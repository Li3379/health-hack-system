package com.hhs.service.alert;

import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertTemplate;
import com.hhs.mapper.AlertTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert template service for matching and generating alert messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTemplateService {

    private final AlertTemplateMapper alertTemplateMapper;

    /**
     * Find the best matching template for the given alert context
     *
     * @param metricKey    the metric type
     * @param value        the current value
     * @param thresholdHigh the high threshold
     * @param thresholdLow  the low threshold
     * @return the matched template, or empty if no match
     */
    public Optional<AlertTemplate> findMatchingTemplate(String metricKey, BigDecimal value,
                                                         BigDecimal thresholdHigh, BigDecimal thresholdLow) {
        if (value == null) {
            return Optional.empty();
        }

        // Determine severity based on value vs thresholds
        String severityLevel = determineSeverityLevel(value, thresholdHigh, thresholdLow);

        // Find template by metric key and severity
        Optional<AlertTemplate> template = alertTemplateMapper.findByMetricKeyAndSeverity(metricKey, severityLevel);

        if (template.isEmpty()) {
            // Try to find any template for this metric
            List<AlertTemplate> templates = alertTemplateMapper.findByMetricKey(metricKey);
            if (!templates.isEmpty()) {
                // Return the highest priority template
                template = templates.stream().findFirst();
            }
        }

        return template;
    }

    /**
     * Determine severity level based on value and thresholds
     */
    private String determineSeverityLevel(BigDecimal value, BigDecimal thresholdHigh, BigDecimal thresholdLow) {
        if (thresholdHigh != null && value.compareTo(thresholdHigh) >= 0) {
            return "CRITICAL";
        }
        if (thresholdLow != null && value.compareTo(thresholdLow) <= 0) {
            return "CRITICAL";
        }

        // Check for warning level (within 20% of threshold)
        if (thresholdHigh != null) {
            BigDecimal warningThreshold = thresholdHigh.multiply(new BigDecimal("0.8"));
            if (value.compareTo(warningThreshold) >= 0) {
                return "WARNING";
            }
        }
        if (thresholdLow != null) {
            BigDecimal warningThreshold = thresholdLow.multiply(new BigDecimal("1.2"));
            if (value.compareTo(warningThreshold) <= 0) {
                return "WARNING";
            }
        }

        return "INFO";
    }

    /**
     * Generate alert message from template
     *
     * @param template       the template to use
     * @param currentValue   the current value
     * @param thresholdValue the threshold value
     * @return AlertVO with generated content
     */
    public AlertVO generateAlertFromTemplate(AlertTemplate template, BigDecimal currentValue, BigDecimal thresholdValue) {
        AlertVO alert = new AlertVO();

        // Replace placeholders in templates
        String title = replacePlaceholders(template.getTitleTemplate(), currentValue, thresholdValue);
        String message = replacePlaceholders(template.getMessageTemplate(), currentValue, thresholdValue);
        String suggestion = template.getSuggestionTemplate() != null
                ? replacePlaceholders(template.getSuggestionTemplate(), currentValue, thresholdValue)
                : null;

        alert.setTitle(title);
        alert.setMessage(message);
        alert.setSuggestion(suggestion);
        alert.setCurrentValue(currentValue);
        alert.setThresholdValue(thresholdValue);

        // Map severity level to alert type and level
        switch (template.getSeverityLevel()) {
            case "CRITICAL" -> {
                alert.setAlertType("CRITICAL");
                alert.setAlertLevel("HIGH");
            }
            case "WARNING" -> {
                alert.setAlertType("WARNING");
                alert.setAlertLevel("MEDIUM");
            }
            default -> {
                alert.setAlertType("INFO");
                alert.setAlertLevel("LOW");
            }
        }

        return alert;
    }

    /**
     * Replace placeholders in template string
     * Supported placeholders: {value}, {threshold}
     */
    private String replacePlaceholders(String template, BigDecimal value, BigDecimal threshold) {
        if (template == null) {
            return "";
        }

        String result = template;
        if (value != null) {
            result = result.replace("{value}", value.stripTrailingZeros().toPlainString());
        }
        if (threshold != null) {
            result = result.replace("{threshold}", threshold.stripTrailingZeros().toPlainString());
        }

        return result;
    }

    /**
     * Check if the alert context needs AI analysis
     *
     * @param userId       the user ID
     * @param metricKey    the metric type
     * @param alertLevel   the alert level
     * @param recentAlertCount recent alert count for this metric
     * @return true if AI analysis is needed
     */
    public boolean needsAIAnalysis(Long userId, String metricKey, String alertLevel, int recentAlertCount) {
        // CRITICAL alerts always get AI analysis
        if ("HIGH".equals(alertLevel)) {
            return true;
        }

        // Frequent alerts (>3 in last hour) need AI analysis
        if (recentAlertCount > 3) {
            return true;
        }

        // Multi-metric scenarios would be handled by the calling service

        return false;
    }

    /**
     * Get all enabled templates
     */
    @Cacheable(value = "alertTemplates", unless = "#result == null")
    public List<AlertTemplate> getAllTemplates() {
        return alertTemplateMapper.findAllEnabled();
    }
}