package com.hhs.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "^(EMAIL|WECOM|FEISHU|WEBSOCKET)$", message = "推送渠道类型无效")
    private String channelType;

    /**
     * Configuration key (e.g., "webhook", "email")
     */
    private String configKey;

    /**
     * Configuration value (webhook URL, email address, etc.).
     *
     * <p>Capped at the {@code push_config.config_value VARCHAR(500)} column width so an
     * over-long paste fails Bean Validation with a Chinese message instead of escaping into a
     * database integrity error.
     */
    @Size(max = 500, message = "配置内容不能超过 500 个字符")
    private String configValue;

    /**
     * Whether this channel is enabled
     */
    private Boolean enabled;
}