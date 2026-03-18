package com.hhs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth Token Response DTO.
 * Used for deserializing token responses from Huawei Health OAuth API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    Long expiresIn,

    @JsonProperty("refresh_token")
    String refreshToken,

    @JsonProperty("scope")
    String scope,

    @JsonProperty("error")
    String error,

    @JsonProperty("error_description")
    String errorDescription
) {
    /**
     * Check if the token response contains an error.
     */
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    /**
     * Get a human-readable error message.
     */
    public String getErrorMessage() {
        if (errorDescription != null && !errorDescription.isEmpty()) {
            return errorDescription;
        }
        return error;
    }
}