package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Health Profile Request DTO
 * Used for creating and updating user health profiles
 */
@Data
public class HealthProfileRequest {

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate birthDate;

    @Size(min = 1, max = 16, message = "Gender must be between 1 and 16 characters")
    @Pattern(regexp = "^(male|female|other|男|女|其他)?$", message = "Gender must be male, female, other, or empty (or Chinese equivalents)")
    private String gender;

    @DecimalMin(value = "50.0", message = "Height must be at least 50 cm")
    @DecimalMax(value = "300.0", message = "Height must not exceed 300 cm")
    private BigDecimal heightCm;

    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
    @DecimalMax(value = "500.0", message = "Weight must not exceed 500 kg")
    private BigDecimal weightKg;

    @Size(max = 16, message = "Blood type must not exceed 16 characters")
    @Pattern(regexp = "^(A|B|AB|O)[+-]?$|^$", message = "Blood type must be a valid type (A, B, AB, O with optional + or -) or empty")
    private String bloodType;

    @Size(max = 512, message = "Allergy history must not exceed 512 characters")
    private String allergyHistory;

    @Size(max = 512, message = "Family history must not exceed 512 characters")
    private String familyHistory;

    @Size(max = 512, message = "Lifestyle habits must not exceed 512 characters")
    private String lifestyleHabits;
}
