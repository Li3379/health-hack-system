package com.hhs.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View Object for realtime metrics
 */
@Data
public class RealtimeMetricVO {

    private Long id;

    private String metricKey;

    private String metricDisplayName;

    private BigDecimal value;

    private String unit;

    private String source;

    @JsonProperty("recordedAt")
    private LocalDateTime createdAt;
}
