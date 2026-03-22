package com.hhs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request DTO for push configuration
 */
@Data
public class PushConfigRequest {

    /**
     * Channel type: EMAIL, WECOM, FEISHU, WEBSOCKET
     * Optional - typically provided via URL path variable
     */
    @Pattern(regexp = "^(EMAIL|WECOM|FEISHU|WEBSOCKET)$", message = "Invalid channel type")
    private String channelType;

    /**
     * Configuration key (e.g., "webhook", "email")
     */
    private String configKey;

    /**
     * Configuration value (webhook URL, email address, etc.)
     */
    private String configValue;

    /**
     * Whether this channel is enabled
     */
    private Boolean enabled;
}