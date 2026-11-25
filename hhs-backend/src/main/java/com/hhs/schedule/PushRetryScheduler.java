package com.hhs.schedule;

import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.PushHistory;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannelManager;
import com.hhs.service.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 推送失败的补偿任务。
 *
 * <p>背景：真实告警投递失败后原来没有任何补偿 —— {@code AlertPushListener} 吞掉异常只打日志，
 * {@code push_history} 里 FAILED 的行无人扫描重发。凭据或 webhook 短暂故障期间产生的告警
 * 就此永久丢失，用户不开推送设置页就完全不知道漏了几条。
 *
 * <p>本任务每 10 分钟扫一次「到期待重试的真实告警失败行」，按指数退避重试有限次：
 * <pre>
 *   第 1 次重试：失败后 2 分钟
 *   第 2 次重试：再 4 分钟
 *   第 3 次重试：再 8 分钟（默认上限 3 次）
 * </pre>
 *
 * <p><b>为什么不无限重试</b>：最常见的失败原因是凭据本身错误（QQ SMTP 535、webhook 被删）。
 * 这类故障重试永远不会成功，而服务商对反复失败的认证请求会回频率限制 —— 无限重试会把
 * 「配置错了」升级成「被限流了」。上限 + 退避让它在有限次后停下来，把结论留给历史记录和
 * 设置页上的「最近投递失败」标记（见 PUSH-6）。
 *
 * <p><b>重试的是告警本身</b>，不是那一行历史：任务按 {@code alert_id} 重新取告警内容，
 * 走同一个 {@link PushChannelManager#push}，因此成功后会写入一条新的 SUCCESS 行 ——
 * 失败与最终成功的记录都在，用户能看出「这条告警晚到但到了」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushRetryScheduler {

    private final PushHistoryService pushHistoryService;
    private final PushChannelManager pushChannelManager;
    private final HealthAlertMapper healthAlertMapper;

    /** 单次扫描最多处理多少行，避免一次积压把执行器占满。 */
    @Value("${push.retry.batch-size:50}")
    private int batchSize;

    /** 每条历史最多重试几次（不含首次投递）。 */
    @Value("${push.retry.max-attempts:3}")
    private int maxAttempts;

    /** 首次重试的延迟；后续按 2 的幂退避。 */
    @Value("${push.retry.base-delay-minutes:2}")
    private long baseDelayMinutes;

    /**
     * 每 10 分钟补偿一次。
     *
     * <p>选 10 分钟而不是 1 分钟：告警不是秒级时效的业务，而扫描频率直接决定了重试会打出
     * 多少额外请求 —— 对一个刚坏掉凭据的邮箱来说，扫得越勤越是加压。
     */
    @Scheduled(fixedDelayString = "${push.retry.interval-ms:600000}")
    public void retryFailedPushes() {
        List<PushHistory> due;
        try {
            due = pushHistoryService.findDueForRetry(maxAttempts, batchSize);
        } catch (Exception e) {
            log.error("Failed to load due push retries: {}", e.getMessage());
            return;
        }
        if (due.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed push(es)", due.size());
        for (PushHistory history : due) {
            try {
                retryOne(history);
            } catch (Exception e) {
                // 单行失败不能中断整批：其余告警仍有机会送达
                log.error("Retry failed for push history {}: {}", history.getId(), e.getMessage());
            }
        }
    }

    private void retryOne(PushHistory history) {
        ChannelType channelType;
        try {
            channelType = ChannelType.valueOf(history.getChannelType());
        } catch (IllegalArgumentException | NullPointerException e) {
            // 渠道类型无法识别时重试也只会再次失败，直接停止调度
            log.warn("Stopping retries for push history {}: unknown channel {}",
                    history.getId(), history.getChannelType());
            pushHistoryService.scheduleRetry(history.getId(), maxAttempts, null);
            return;
        }

        AlertVO alert = loadAlert(history.getAlertId());
        if (alert == null) {
            // 告警已被删除（或用户注销连带删除）——重试没有内容可发
            log.info("Stopping retries for push history {}: alert {} no longer exists",
                    history.getId(), history.getAlertId());
            pushHistoryService.scheduleRetry(history.getId(), maxAttempts, null);
            return;
        }

        PushResult result = pushChannelManager.push(history.getUserId(), alert, channelType);
        int attempts = (history.getRetryCount() == null ? 0 : history.getRetryCount()) + 1;

        if (result.isSuccess()) {
            // push() 已写入一条 SUCCESS 历史；把这一行标记为「不再调度」，避免下轮重复重试。
            pushHistoryService.scheduleRetry(history.getId(), attempts, null);
            log.info("Retry succeeded for push history {} (channel {}, attempt {})",
                    history.getId(), channelType, attempts);
            return;
        }

        LocalDateTime next = attempts >= maxAttempts ? null : nextAttemptAt(attempts);
        pushHistoryService.scheduleRetry(history.getId(), attempts, next);
        if (next == null) {
            log.warn("Giving up on push history {} after {} attempt(s): {}",
                    history.getId(), attempts, result.getMessage());
        } else {
            log.info("Retry {} for push history {} failed, next at {}: {}",
                    attempts, history.getId(), next, result.getMessage());
        }
    }

    /**
     * 指数退避：第 n 次失败后等 {@code base * 2^(n-1)} 分钟。
     *
     * @param attempts 已尝试次数（含刚失败这次）
     * @return 下次重试时间
     */
    LocalDateTime nextAttemptAt(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 10);
        return LocalDateTime.now().plus(Duration.ofMinutes(baseDelayMinutes * multiplier));
    }

    private AlertVO loadAlert(Long alertId) {
        if (alertId == null) {
            return null;
        }
        HealthAlert alert = healthAlertMapper.selectById(alertId);
        return alert == null ? null : toVO(alert);
    }

    /**
     * 与 {@code AlertServiceImpl.convertToVO} 同形。
     *
     * <p>复制而不是复用：{@code convertToVO} 是 private，而把它提到接口上只为了让定时任务
     * 能取一条告警，会让 AlertService 的公开面为一个内部需求变大。字段清单很短且稳定
     * （新增字段时两边都不会静默丢失——推送只读标题/正文/建议等少数几个）。
     */
    private AlertVO toVO(HealthAlert alert) {
        AlertVO vo = new AlertVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setTitle(alert.getTitle());
        vo.setMessage(alert.getMessage());
        vo.setMetricKey(alert.getMetricKey());
        vo.setCurrentValue(alert.getCurrentValue());
        vo.setThresholdValue(alert.getThresholdValue());
        vo.setSuggestion(alert.getSuggestion());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }
}