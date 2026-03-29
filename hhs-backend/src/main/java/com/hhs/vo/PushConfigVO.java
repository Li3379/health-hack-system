package com.hhs.vo;

import com.hhs.service.push.ChannelType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * VO for push configuration response
 */
@Data
public class PushConfigVO {

    private Long id;

    private String channelType;

    private String channelLabel;

    private String configKey;

    private String configValue;

    private Boolean enabled;

    private Boolean offlineSupported;

    private Boolean isConfigured;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Create VO from entity
     */
    public static PushConfigVO fromEntity(com.hhs.entity.UserPushConfig entity) {
        PushConfigVO vo = new PushConfigVO();
        if (entity != null) {
            vo.setId(entity.getId());
            vo.setChannelType(entity.getChannelType());
            vo.setConfigKey(entity.getConfigKey());
            vo.setConfigValue(entity.getConfigValue());
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

        return vo;
    }

    /**
     * Create default VO for a channel type (not yet configured)
     */
    public static PushConfigVO defaultForChannel(ChannelType channelType) {
        PushConfigVO vo = new PushConfigVO();
        vo.setChannelType(channelType.name());
        vo.setChannelLabel(channelType.getLabel());
        vo.setOfflineSupported(channelType.isOfflineSupported());
        vo.setEnabled(false);
        vo.setIsConfigured(false);
        return vo;
    }
}
