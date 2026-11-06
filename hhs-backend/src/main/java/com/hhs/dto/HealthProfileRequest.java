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

    @NotNull(message = "出生日期不能为空")
    @Past(message = "出生日期必须早于今天")
    private LocalDate birthDate;

    @Size(min = 1, max = 16, message = "性别长度需在 1 至 16 个字符之间")
    @Pattern(regexp = "^(male|female|other|男|女|其他)?$", message = "性别只能是男、女、其他或留空")
    private String gender;

    @DecimalMin(value = "50.0", message = "身高不能低于 50 厘米")
    @DecimalMax(value = "300.0", message = "身高不能超过 300 厘米")
    private BigDecimal heightCm;

    @DecimalMin(value = "20.0", message = "体重不能低于 20 千克")
    @DecimalMax(value = "500.0", message = "体重不能超过 500 千克")
    private BigDecimal weightKg;

    @Size(max = 16, message = "血型长度不能超过 16 个字符")
    @Pattern(regexp = "^(A|B|AB|O)[+-]?$|^$", message = "血型格式不正确，请输入 A、B、AB、O（可带正负号）或留空")
    private String bloodType;

    @Size(max = 512, message = "过敏史不能超过 512 个字符")
    private String allergyHistory;

    @Size(max = 512, message = "家族病史不能超过 512 个字符")
    private String familyHistory;

    @Size(max = 512, message = "生活习惯不能超过 512 个字符")
    private String lifestyleHabits;
}
