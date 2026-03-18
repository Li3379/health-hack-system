package com.hhs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Huawei Health Data API Response DTO.
 * Used for deserializing health data responses from Huawei Health Kit API.
 *
 * <p>Huawei Health Kit API returns data in the following format:
 * <pre>
 * {
 *   "dataPoints": [
 *     {
 *       "startTime": "2024-01-01T00:00:00Z",
 *       "endTime": "2024-01-01T00:01:00Z",
 *       "dataType": "heart_rate",
 *       "value": 75.0,
 *       "unit": "bpm"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see <a href="https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/health-kit-overview">Huawei Health Kit</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HuaweiHealthDataResponse(
    @JsonProperty("dataPoints")
    List<HuaweiDataPoint> dataPoints,

    @JsonProperty("hasMore")
    Boolean hasMore,

    @JsonProperty("nextPageToken")
    String nextPageToken,

    @JsonProperty("error")
    HuaweiError error
) {
    /**
     * Check if the response contains an error.
     */
    public boolean hasError() {
        return error != null && error.errorCode() != null && !error.errorCode().isEmpty();
    }

    /**
     * Get a human-readable error message.
     */
    public String getErrorMessage() {
        if (error != null && error.errorMessage() != null) {
            return error.errorMessage();
        }
        return "Unknown error";
    }

    /**
     * Check if there are data points in the response.
     */
    public boolean hasData() {
        return dataPoints != null && !dataPoints.isEmpty();
    }

    /**
     * Huawei Health data point.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HuaweiDataPoint(
        @JsonProperty("startTime")
        String startTime,

        @JsonProperty("endTime")
        String endTime,

        @JsonProperty("dataType")
        String dataType,

        @JsonProperty("value")
        Double value,

        @JsonProperty("unit")
        String unit,

        @JsonProperty("values")
        List<HuaweiValue> values
    ) {
        /**
         * Get the primary value for this data point.
         * Some data points (like blood pressure) have multiple values.
         */
        public Double getPrimaryValue() {
            if (value != null) {
                return value;
            }
            if (values != null && !values.isEmpty()) {
                return values.get(0).value();
            }
            return null;
        }

        /**
         * Get a secondary value for this data point (e.g., diastolic BP).
         */
        public Double getSecondaryValue() {
            if (values != null && values.size() > 1) {
                return values.get(1).value();
            }
            return null;
        }
    }

    /**
     * Multi-value entry for data points with multiple measurements.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HuaweiValue(
        @JsonProperty("key")
        String key,

        @JsonProperty("value")
        Double value
    ) {}

    /**
     * Error response from Huawei Health API.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HuaweiError(
        @JsonProperty("errorCode")
        String errorCode,

        @JsonProperty("errorMessage")
        String errorMessage,

        @JsonProperty("errorDetail")
        String errorDetail
    ) {}
}