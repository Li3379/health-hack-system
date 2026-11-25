package com.hhs.domain.handler;

import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.entity.RealtimeMetric;
import com.hhs.service.HealthScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 缓存失效监听器的行为证明。
 *
 * <p>之前的实现 evict 一个名为 {@code "scores"} 的 Spring Cache，但全仓没有
 * {@code @Cacheable("scores")}，所以那次 evict 是空操作 —— 真正缓存的评分（Redis 键
 * {@code health:score:v2:{userId}}，TTL 5 分钟）在录入指标后仍然陈旧，「缓存」标签也让
 * 用户以为录入没影响评分。这里的用例钉住两件事：录入指标必须真的删除那个评分键；
 * 评分更新事件<strong>不能</strong>顺手把刚刚写好的缓存删掉。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("缓存失效监听器测试")
class CacheInvalidationListenerTest {

    @Mock
    private HealthScoreService healthScoreService;

    @InjectMocks
    private CacheInvalidationListener listener;

    private static final Long USER_ID = 7L;

    private static MetricRecordedEvent metricEvent() {
        RealtimeMetric metric = new RealtimeMetric();
        metric.setUserId(USER_ID);
        metric.setMetricKey("heartRate");
        metric.setValue(new BigDecimal("72"));
        return new MetricRecordedEvent(USER_ID, metric);
    }

    @Test
    @DisplayName("录入指标 -> 调用评分缓存失效（真实 Redis 键，而非不存在的 cache 名）")
    void onMetricRecorded_EvictsScoreCache() {
        listener.onMetricRecorded(metricEvent());

        verify(healthScoreService, times(1)).evictCachedScore(USER_ID);
    }

    @Test
    @DisplayName("评分缓存失效抛异常时被吞掉，不影响录入主流程")
    void onMetricRecorded_SwallowsFailure() {
        doThrow(new RuntimeException("Redis down")).when(healthScoreService).evictCachedScore(anyLong());

        assertDoesNotThrow(() -> listener.onMetricRecorded(metricEvent()));
        verify(healthScoreService, times(1)).evictCachedScore(USER_ID);
    }

    @Test
    @DisplayName("评分更新事件没有监听器（它是在缓存写入之后发布的，删除即自毁）")
    void onScoreUpdated_HasNoHandler() {
        // 这条不再靠 Mockito 断言，而是靠反射：类上只应有 onMetricRecorded 一个 @EventListener。
        // 保留这条测试的理由是防止有人「顺手补一个 onScoreUpdated」——那会把
        // calculateScore 刚写进 Redis 的值删掉，之后每次读都必然 miss。
        long eventListenerCount = java.util.Arrays.stream(CacheInvalidationListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.context.event.EventListener.class))
                .peek(m -> org.junit.jupiter.api.Assertions.assertEquals("onMetricRecorded", m.getName(),
                        "only onMetricRecorded may listen; a ScoreUpdatedEvent listener would delete the fresh cache"))
                .count();

        org.junit.jupiter.api.Assertions.assertEquals(1, eventListenerCount);
    }

    @Test
    @DisplayName("监听器不再依赖 Spring CacheManager（那个 cache 名根本不存在）")
    void listener_TakesScoreServiceNotCacheManager() {
        // 编译期即可保证：构造器只接受 HealthScoreService。
        CacheInvalidationListener fresh = new CacheInvalidationListener(healthScoreService);
        fresh.onMetricRecorded(metricEvent());

        verify(healthScoreService, times(1)).evictCachedScore(USER_ID);
    }
}