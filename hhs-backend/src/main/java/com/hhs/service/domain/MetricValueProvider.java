package com.hhs.service.domain;

import java.math.BigDecimal;

/**
 * Universal interface for metric data providers across all data ingestion channels.
 * Exposes canonical key and numerical value for unified physiological limit validation.
 */
public interface MetricValueProvider {

    /**
     * Get the metric key (canonical key or raw alias).
     *
     * @return metric key string
     */
    String getMetricKey();

    /**
     * Get the numerical value of the metric.
     *
     * @return metric value as BigDecimal
     */
    BigDecimal getValue();
}
