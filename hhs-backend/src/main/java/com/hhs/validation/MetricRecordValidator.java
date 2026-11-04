package com.hhs.validation;

import com.hhs.dto.HealthMetricRequest;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricKeyNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Jakarta Validation validator for {@link ValidMetricRecord}.
 * Validates that health metric values fall within physically possible physiological boundaries.
 * Clinical abnormal values (e.g., severe hypertension, tachycardia) pass validation,
 * while physically impossible extreme values are intercepted with user-friendly guidance.
 */
@Component
public class MetricRecordValidator implements ConstraintValidator<ValidMetricRecord, HealthMetricRequest> {

    private final HealthMetricMetaRegistry registry;

    public MetricRecordValidator() {
        this(new HealthMetricMetaRegistry());
    }

    @Autowired
    public MetricRecordValidator(HealthMetricMetaRegistry registry) {
        this.registry = registry != null ? registry : new HealthMetricMetaRegistry();
    }

    @Override
    public boolean isValid(HealthMetricRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getValue() == null || request.getMetricKey() == null) {
            // Null values are validated by @NotNull on the respective fields
            return true;
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(request.getMetricKey());
        Optional<MetricDefinition> defOpt = registry.getDefinition(canonicalKey);
        if (defOpt.isEmpty()) {
            // Unregistered / custom metric: lenient passthrough
            return true;
        }

        MetricDefinition def = defOpt.get();
        BigDecimal hardMin = def.hardMin();
        BigDecimal hardMax = def.hardMax();
        BigDecimal value = request.getValue();

        boolean underMin = hardMin != null && value.compareTo(hardMin) < 0;
        boolean overMax = hardMax != null && value.compareTo(hardMax) > 0;

        if (underMin || overMax) {
            context.disableDefaultConstraintViolation();

            String minStr = formatBigDecimal(hardMin);
            String maxStr = formatBigDecimal(hardMax);
            String unitStr = def.unit() != null ? def.unit() : "";

            String errorMessage = def.displayName()
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
            return false;
        }

        return true;
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
