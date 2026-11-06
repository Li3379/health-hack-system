package com.hhs.vo;

/**
 * Payload for a single field-level validation failure.
 *
 * <p>Replaces the legacy {@code "field: message"} string concatenation used by the old
 * {@code Result.errors} array. The field order ({@code field}, {@code message}, {@code code})
 * matches the frontend {@code FieldError} contract.
 *
 * @param field   the DTO property the violation belongs to, or {@code "_global"} for
 *                class-level violations that carry no property path
 * @param message the user-facing Chinese message for this violation
 * @param code    an optional stable machine-readable code; usually {@code null} for
 *                Bean Validation violations, which carry messages only
 */
public record FieldErrorVO(String field, String message, String code) {
}