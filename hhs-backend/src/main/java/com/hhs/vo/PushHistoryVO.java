package com.hhs.vo;

import com.hhs.entity.PushHistory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * VO for push history response
 */
@Data
public class PushHistoryVO {

    private Long id;

    private Long alertId;

    private String channelType;

    private String channelLabel;

    private String status;

    private String message;

    private LocalDateTime pushedAt;

    /**
     * Create VO from entity
     */
    public static PushHistoryVO fromEntity(PushHistory entity) {
        if (entity == null) {
            return null;
        }

        PushHistoryVO vo = new PushHistoryVO();
        vo.setId(entity.getId());
        vo.setAlertId(entity.getAlertId());
        vo.setChannelType(entity.getChannelType());
        vo.setStatus(entity.getStatus());
        vo.setMessage(entity.getMessage());
        vo.setPushedAt(entity.getPushedAt());

        // Set channel label
        try {
            com.hhs.service.push.ChannelType type =
                    com.hhs.service.push.ChannelType.valueOf(entity.getChannelType());
            vo.setChannelLabel(type.getLabel());
        } catch (IllegalArgumentException e) {
            vo.setChannelLabel(entity.getChannelType());
        }

        return vo;
    }
}