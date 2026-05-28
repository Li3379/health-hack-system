package com.hhs.domain.handler;

import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.service.GamificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 游戏化事件监听器
 * 监听健康数据记录事件，更新连续记录和检查成就解锁
 */
@Slf4j
@Component
public class GamificationEventListener {

    private final GamificationService gamificationService;
    private final GamificationEventListener self;

    public GamificationEventListener(GamificationService gamificationService,
                                     @Lazy GamificationEventListener self) {
        this.gamificationService = gamificationService;
        this.self = self;
    }

    @Async("eventExecutor")
    @EventListener
    public void onMetricRecorded(MetricRecordedEvent event) {
        self.processAsync(event);
    }

    /**
     * 异步处理游戏化逻辑，使用 @Lazy self 调用以确保 @Async 代理生效
     */
    public void processAsync(MetricRecordedEvent event) {
        try {
            Long userId = event.getUserId();
            log.debug("Processing gamification for MetricRecordedEvent: userId={}", userId);

            gamificationService.updateStreak(userId);
            gamificationService.checkAndUnlockAchievements(userId);

            log.debug("Gamification processing completed for user: {}", userId);
        } catch (Exception e) {
            log.error("Error processing gamification for MetricRecordedEvent: userId={}",
                    event.getUserId(), e);
        }
    }
}
