package com.hhs.validation;

import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.LabRoutineMetricRegistry;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.service.domain.MetricValueProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Jakarta Validation validator for {@link ValidMetricRecord}.
 * Validates that health metric values fall within physically possible physiological boundaries
 * across all ingestion DTOs implementing {@link MetricValueProvider}.
 * Clinical abnormal values (e.g., severe hypertension, tachycardia) pass validation,
 * while physically impossible extreme values are intercepted with user-friendly guidance.
 */
@Component
public class MetricRecordValidator implements ConstraintValidator<ValidMetricRecord, MetricValueProvider> {

    private final HealthMetricMetaRegistry registry;
    private final LabRoutineMetricRegistry labRegistry;

    public MetricRecordValidator() {
        this(new HealthMetricMetaRegistry(), new LabRoutineMetricRegistry());
    }

    @Autowired
    public MetricRecordValidator(HealthMetricMetaRegistry registry, LabRoutineMetricRegistry labRegistry) {
        this.registry = registry != null ? registry : new HealthMetricMetaRegistry();
        this.labRegistry = labRegistry != null ? labRegistry : new LabRoutineMetricRegistry();
    }

    @Override
    public boolean isValid(MetricValueProvider provider, ConstraintValidatorContext context) {
        if (provider == null || provider.getValue() == null || provider.getMetricKey() == null) {
            // Null values are validated by @NotNull on the respective fields
            return true;
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(provider.getMetricKey());
        BigDecimal value = provider.getValue();

        // 1. 优先查核心健康/保健指标注册表
        Optional<MetricDefinition> defOpt = registry.getDefinition(canonicalKey);
        if (defOpt.isPresent()) {
            MetricDefinition def = defOpt.get();
            BigDecimal hardMin = def.hardMin();
            BigDecimal hardMax = def.hardMax();

            boolean underMin = hardMin != null && value.compareTo(hardMin) < 0;
            boolean overMax = hardMax != null && value.compareTo(hardMax) > 0;

            if (underMin || overMax) {
                buildViolation(context, def.displayName(), hardMin, hardMax, def.unit());
                return false;
            }
            return true;
        }

        // 2. 次查化验单扩展注册表
        Optional<LabRoutineMetricRegistry.LabDefinition> labOpt = labRegistry.getDefinition(canonicalKey);
        if (labOpt.isPresent()) {
            var lab = labOpt.get();
            BigDecimal hardMin = lab.hardMin();
            BigDecimal hardMax = lab.hardMax();

            boolean underMin = hardMin != null && value.compareTo(hardMin) < 0;
            boolean overMax = hardMax != null && value.compareTo(hardMax) > 0;

            if (underMin || overMax) {
                buildViolation(context, lab.displayName(), hardMin, hardMax, lab.unit());
                return false;
            }
            return true;
        }

        // 3. 未知自定义指标放行
        return true;
    }

    private void buildViolation(ConstraintValidatorContext context, String displayName,
                                BigDecimal hardMin, BigDecimal hardMax, String unit) {
        context.disableDefaultConstraintViolation();

        String minStr = formatBigDecimal(hardMin);
        String maxStr = formatBigDecimal(hardMax);
        String unitStr = unit != null ? unit : "";

        String errorMessage = displayName
                + "有效生理区间为 "
                + minStr
                + " ~ "
                + maxStr
                + " "
                + unitStr
                + "，请核对后重新输入";

        context.buildConstraintViolationWithTemplate(errorMessage)
                .addPropertyNode("value")
                .addConstraintViolation();
    }

    private String formatBigDecimal(BigDecimal val) {
        if (val == null) {
            return "";
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return val.stripTrailingZeros().toPlainString();
    }
}
