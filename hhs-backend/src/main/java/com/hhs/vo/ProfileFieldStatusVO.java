package com.hhs.vo;

/**
 * Payload for a single health-profile field in the completeness response.
 *
 * <p>{@code reason} explains why the field matters and names its real backend consumer.
 * {@code assumption} is the substitution sentence the risk engine applies when this field
 * is absent; the calculator sets it to {@code null} whenever {@code filled} is true, because
 * a filled field is never rendered as an assumption.</p>
 *
 * @param key        stable machine-readable field identifier, in catalog order
 * @param label      user-facing Chinese field name
 * @param importance one of {@code required}, {@code recommended} or {@code optional}
 * @param filled     whether the user supplied a usable value for this field
 * @param reason     why the field matters, anchored to its real consumer
 * @param assumption the absent-case substitution sentence, or {@code null}
 */
public record ProfileFieldStatusVO(
        String key,
        String label,
        String importance,
        boolean filled,
        String reason,
        String assumption
) {
}