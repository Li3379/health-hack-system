package com.hhs.vo;

import com.hhs.service.push.ChannelType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * VO for push configuration response.
 *
 * <p>Webhook URLs embed secret tokens, so {@code configValue} is masked on read and never
 * echoes a stored secret back to the browser. The mask marker ({@code ****}) is what the
 * frontend tests for before accepting an edited value.
 */
@Data
public class PushConfigVO {

    /** Marker inserted into every masked value; kept in sync with the frontend mask check. */
    public static final String MASK_MARKER = "****";

    private Long id;

    private String channelType;

    private String channelLabel;

    private String configKey;

    private String configValue;

    private Boolean enabled;

    private Boolean offlineSupported;

    private Boolean isConfigured;

    /**
     * Whether the server is capable of delivering on this channel at all.
     *
     * <p>User-side config ({@link #isConfigured}) is necessary but not sufficient: a channel
     * bean gated by {@code @ConditionalOnExpression} (today only EMAIL, on
     * {@code spring.mail.host}) is never registered, so nothing can be sent even with a valid
     * value. Mirrors {@code PushChannelManager.hasChannel(ChannelType)}.</p>
     */
    private Boolean availability;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Create VO from entity
     *
     * @param entity          stored configuration, may be null
     * @param serverAvailable whether the server registered a channel for this type
     */
    public static PushConfigVO fromEntity(com.hhs.entity.UserPushConfig entity, boolean serverAvailable) {
        PushConfigVO vo = new PushConfigVO();
        if (entity != null) {
            vo.setId(entity.getId());
            vo.setChannelType(entity.getChannelType());
            vo.setConfigKey(entity.getConfigKey());
            vo.setConfigValue(maskValue(entity.getConfigValue()));
            vo.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setUpdatedAt(entity.getUpdatedAt());
        }

        // Set channel metadata
        try {
            ChannelType type = ChannelType.valueOf(entity != null ? entity.getChannelType() : "WEBSOCKET");
            vo.setChannelLabel(type.getLabel());
            vo.setOfflineSupported(type.isOfflineSupported());
        } catch (IllegalArgumentException e) {
            vo.setChannelLabel(entity != null ? entity.getChannelType() : "Unknown");
            vo.setOfflineSupported(false);
        }

        // Check if configured (has meaningful config value)
        vo.setIsConfigured(entity != null && entity.getConfigValue() != null
                && !entity.getConfigValue().isBlank());

        vo.setAvailability(serverAvailable);

        return vo;
    }

    /**
     * Mask a stored config value for transport.
     *
     * <p>Keeps a recognisable prefix and the last four characters so the user can tell which
     * webhook is configured without being able to reuse the token.
     *
     * @param value stored value (webhook URL / address)
     * @return masked value, or the original when blank
     */
    static String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int keepEnd = Math.min(4, value.length());
        String tail = value.substring(value.length() - keepEnd);
        if (value.length() <= 8) {
            return MASK_MARKER + tail;
        }
        int keepStart = Math.min(24, value.length() - keepEnd);
        return value.substring(0, keepStart) + MASK_MARKER + tail;
    }

    /**
     * Create default VO for a channel type (not yet configured)
     *
     * @param channelType     the channel
     * @param serverAvailable whether the server registered a channel for this type
     */
    public static PushConfigVO defaultForChannel(ChannelType channelType, boolean serverAvailable) {
        PushConfigVO vo = new PushConfigVO();
        vo.setChannelType(channelType.name());
        vo.setChannelLabel(channelType.getLabel());
        vo.setOfflineSupported(channelType.isOfflineSupported());
        vo.setEnabled(false);
        vo.setIsConfigured(false);
        vo.setAvailability(serverAvailable);
        return vo;
    }
}
