package com.hhs.domain.handler;

import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.RealtimeMetric;
import com.hhs.service.AlertRuleEngine;
import com.hhs.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Async event listener for alert-related events.
 * Decouples alert generation from metric recording.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventListener {

    private final AlertRuleEngine alertRuleEngine;
    private final AlertService alertService;

    @Async("eventExecutor")
    @EventListener
    public void onMetricRecorded(MetricRecordedEvent event) {
        try {
            log.debug("Processing MetricRecordedEvent for user: {}", event.getUserId());

            RealtimeMetric metric = event.getMetric();

            // Check rate limiting
            if (!alertRuleEngine.isWithinRateLimit(event.getUserId())) {
                log.debug("Rate limit exceeded for user: {}", event.getUserId());
                return;
            }

            // Evaluate metric against alert rules
            List<AlertVO> alerts = alertRuleEngine.evaluateMetric(metric);

            if (!alerts.isEmpty()) {
                // Create alerts
                for (AlertVO alertVO : alerts) {
                    // Convert AlertVO to HealthAlert entity
                    HealthAlert alert = new HealthAlert();
                    alert.setUserId(event.getUserId());
                    alert.setAlertType(alertVO.getAlertType());
                    alert.setAlertLevel(alertVO.getAlertLevel());
                    alert.setTitle(alertVO.getTitle());
                    alert.setMessage(alertVO.getMessage());
                    alert.setMetricKey(metric.getMetricKey());
                    alert.setCurrentValue(metric.getValue());
                    alert.setThresholdValue(alertVO.getThresholdValue());
                    alert.setIsRead(false);
                    alert.setIsAcknowledged(false);

                    alertService.createAlert(alert);
                    log.info("Alert generated for user: {}, type: {}", event.getUserId(), alertVO.getAlertType());
                }
            }
        } catch (Exception e) {
            log.error("Error processing MetricRecordedEvent for user: {}", event.getUserId(), e);
            // Don't re-throw - async event failures shouldn't crash the main flow
        }
    }
}
