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

    /**
     * Result of the most recent delivery attempt on this channel, or {@code null} when unknown.
     *
     * <p>{@link #availability} only says "the server registered a channel bean" — for EMAIL that
     * is just {@code spring.mail.host} being non-blank, and says nothing about whether the
     * credentials work. The settings page used to render "支持离线推送" off that alone, promising
     * delivery the backend could not perform. This field carries the evidence: {@code "SUCCESS"}
     * or {@code "FAILED"} from the newest {@code push_history} row (manual test pushes included),
     * {@code null} when the channel has not been attempted in the lookback window.
     *
     * <p>Derived from history rather than a live probe on purpose — an SMTP probe is a real AUTH
     * and providers rate-limit repeated failures, so probing on every page read would cause the
     * outage it reports.
     */
    private String deliveryStatus;

    /** When that most recent attempt happened; null when {@link #deliveryStatus} is null. */
    private LocalDateTime lastDeliveryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Attach the most recent delivery outcome to this VO.
     *
     * @param status   newest push_history status for the channel (SUCCESS/FAILED/SKIPPED), may be null
     * @param pushedAt when that attempt happened, may be null
     * @return this VO, for chaining at call sites
     */
    public PushConfigVO withDeliveryOutcome(String status, LocalDateTime pushedAt) {
        this.deliveryStatus = status;
        this.lastDeliveryAt = pushedAt;
        return this;
    }

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
     * <p>Email addresses and webhook URLs are masked differently because they carry different
     * secrets and are read differently by the user:</p>
     *
     * <ul>
     *   <li><b>Email</b> — the whole address <em>is</em> the user's identity, not a secret. A
     *       user must be able to confirm "this is my mailbox" at a glance, so only the local
     *       part is shortened and the domain is kept intact. Masking across the domain (the
     *       previous behaviour, which produced {@code 3379044054@qq****.com}) made a correct
     *       address look corrupt and left the user unable to tell which mailbox was set.</li>
     *   <li><b>Webhook</b> — the URL embeds a token, so a long prefix is withheld and only the
     *       last four characters survive: enough to distinguish two bots, not enough to reuse.</li>
     * </ul>
     *
     * @param value stored value (webhook URL / email address)
     * @return masked value, or the original when blank
     */
    static String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int at = value.indexOf('@');
        // More than one '@' is not a valid address; fall through to the URL rule.
        if (at > 0 && at == value.lastIndexOf('@')) {
            return maskEmail(value, at);
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
     * Mask the local part of an email address, keeping the domain readable.
     *
     * <p>The purpose here is only to stop the full address from being echoed back over the
     * wire, not to make it unrecognisable: the user is looking at their own mailbox. So a long
     * local part keeps its first three and last two characters, a short one keeps just its
     * first character, and the domain ({@code @qq.com}) is never split — that is the part the
     * user reads to confirm which account is configured.</p>
     *
     * @param value the full address
     * @param at    index of the (single) {@code @}
     * @return masked address such as {@code 337****54@qq.com}
     */
    private static String maskEmail(String value, int at) {
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) {
            // Too short to keep any inner characters without revealing all of them.
            return local.charAt(0) + MASK_MARKER + domain;
        }
        if (local.length() <= 5) {
            return local.charAt(0) + MASK_MARKER + domain;
        }
        return local.substring(0, 3) + MASK_MARKER + local.substring(local.length() - 2) + domain;
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
