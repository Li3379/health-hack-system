package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.AlertRule;
import com.hhs.mapper.AlertRuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads enabled {@code alert_rule} rows and merges them into {@link HealthMetricMetaRegistry}
 * as the authoritative {@code alertable} + recommended-threshold source (D-03/D-06, THR-01/THR-02).
 *
 * <p>A rule row is "usable" only when it is enabled AND at least one of the four boundary
 * columns is non-null — this is what excludes the {@code weight} seed row (all four columns
 * NULL, RESEARCH §Pitfall 10) and any metric without a seed row such as {@code bloodOxygen}
 * (D-19). Keys are canonicalized via {@link MetricKeyNormalizer#toCanonicalKey(String)} so a
 * legacy {@code glucose} row still maps onto {@code bloodGlucose}; on duplicate keys the
 * first row wins.
 *
 * <p>The registry itself stays dependency-free ({@code new HealthMetricMetaRegistry()} keeps
 * working in tests); this component performs the one-time startup merge.
 */
@Slf4j
@Component
public class MetricRecommendedThresholdProvider {

    private final AlertRuleMapper alertRuleMapper;
    private final HealthMetricMetaRegistry registry;

    public MetricRecommendedThresholdProvider(AlertRuleMapper alertRuleMapper,
                                              HealthMetricMetaRegistry registry) {
        this.alertRuleMapper = alertRuleMapper;
        this.registry = registry;
    }

    /**
     * Startup merge: push the usable seed recommendations into the registry. Failures are
     * logged and swallowed so the metadata endpoint (public reference data) keeps serving
     * with {@code alertable=false} everywhere instead of blocking application startup.
     */
    @PostConstruct
    public void applySeedRecommendations() {
        try {
            registry.applyRecommendations(loadUsable());
        } catch (Exception e) {
            log.warn("启动期读取 alert_rule 推荐阈值失败，指标元数据将全部不可告警", e);
        }
    }

    /**
     * Load usable seed recommendations keyed by canonical metric key.
     *
     * @return canonicalKey -> RecommendedThreshold for enabled rules with at least one non-null boundary
     */
    public Map<String, RecommendedThreshold> loadUsable() {
        List<AlertRule> rules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, true));
        Map<String, RecommendedThreshold> usable = new LinkedHashMap<>();
        for (AlertRule rule : rules) {
            if (!hasAnyBoundary(rule)) {
                // All four boundaries null: metric cannot be evaluated (RESEARCH Pitfall 10)
                continue;
            }
            usable.putIfAbsent(MetricKeyNormalizer.toCanonicalKey(rule.getMetricKey()),
                    new RecommendedThreshold(rule.getWarningLow(), rule.getWarningHigh(),
                            rule.getCriticalLow(), rule.getCriticalHigh()));
        }
        return usable;
    }

    /**
     * Convenience check: whether the metric has a usable seed rule (D-19 definition of alertable).
     *
     * @param key metric key or alias
     * @return true when a usable seed exists for the canonical key
     */
    public boolean isAlertable(String key) {
        return loadUsable().containsKey(MetricKeyNormalizer.toCanonicalKey(key));
    }

    private boolean hasAnyBoundary(AlertRule rule) {
        return rule.getWarningHigh() != null || rule.getCriticalHigh() != null
                || rule.getWarningLow() != null || rule.getCriticalLow() != null;
    }
}
