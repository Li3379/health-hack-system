package com.hhs.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HealthProfileVO(
        Long id,
        Long userId,
        String gender,
        LocalDate birthDate,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal bmi,
        String bloodType,
        String allergyHistory,
        String familyHistory,
        String lifestyleHabits,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
