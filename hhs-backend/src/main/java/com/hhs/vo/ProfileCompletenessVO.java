package com.hhs.vo;

import java.util.List;

/**
 * Authoritative health-profile completeness payload.
 *
 * <p>{@code hasProfile} separates "no profile row yet" from "profile row present but sparse",
 * so consumers never infer that state from the score. {@code assumptions} lists the
 * substitution sentences the risk engine will actually apply, produced by the single shared
 * {@code ProfileFieldCatalog.assumptionsFor} function.</p>
 *
 * @param score      0-100 weighted completeness
 * @param level      one of {@code 基础档案}, {@code 标准档案} or {@code 完整档案}
 * @param filledCount number of catalog fields the user supplied
 * @param totalCount  number of catalog fields, always derived from the catalog size
 * @param hasProfile  whether a health_profile row exists at all
 * @param assumptions substitution sentences disclosed for this profile state
 * @param fields      per-field status in catalog order
 */
public record ProfileCompletenessVO(
        Integer score,
        String level,
        Integer filledCount,
        Integer totalCount,
        boolean hasProfile,
        List<String> assumptions,
        List<ProfileFieldStatusVO> fields
) {
}