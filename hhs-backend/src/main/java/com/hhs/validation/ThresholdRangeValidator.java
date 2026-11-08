package com.hhs.validation;

import com.hhs.dto.UserThresholdRequest;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricDefinition;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Jakarta Validation validator for {@link ValidThresholdRange} (THR-05 / D-09).
 * Authoritative server-side gate for user threshold requests:
 * <ol>
 *   <li>Adjacent boundary ordering: criticalLow &lt; warningLow &lt; warningHigh &lt; criticalHigh.
 *       A pair is only compared when both sides are non-null (null = unbounded, D-10).</li>
 *   <li>Physiological hard limits: every non-null boundary must lie within the metric's
 *       metadata hardMin/hardMax (D-11 — registry-derived limits, not PANIC_LIMITS).</li>
 * </ol>
 * Each violation is emitted as TWO separate constraint violations — one per involved field —
 * so Phase 11's fieldErrors projection lights up both inputs. Never chain two
 * {@code addPropertyNode} calls on one builder: SpringValidatorAdapter joins PROPERTY nodes
 * with a dot, producing a field name no frontend form prop can match (RESEARCH §Pitfall 5).
 */
@Component
public class ThresholdRangeValidator implements ConstraintValidator<ValidThresholdRange, UserThresholdRequest> {

    private static final String CRITICAL_LOW_FIELD = "criticalLow";
    private static final String WARNING_LOW_FIELD = "warningLow";
    private static final String WARNING_HIGH_FIELD = "warningHigh";
    private static final String CRITICAL_HIGH_FIELD = "criticalHigh";

    private static final String CRITICAL_LOW_VS_WARNING_LOW_MESSAGE = "严重低值必须小于警告低值，请调整其中一个";
    private static final String WARNING_LOW_VS_WARNING_HIGH_MESSAGE = "警告低值必须小于警告高值，请调整其中一个";
    private static final String WARNING_HIGH_VS_CRITICAL_HIGH_MESSAGE = "警告高值必须小于严重高值，请调整其中一个";

    private final HealthMetricMetaRegistry registry;

    public ThresholdRangeValidator() {
        this(new HealthMetricMetaRegistry());
    }

    @Autowired
    public ThresholdRangeValidator(HealthMetricMetaRegistry registry) {
        this.registry = registry != null ? registry : new HealthMetricMetaRegistry();
    }

    @Override
    public boolean isValid(UserThresholdRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            // Null requests are handled by @Valid on the controller parameter itself
            return true;
        }
        context.disableDefaultConstraintViolation();

        boolean ok = true;
        ok &= checkOrderPair(context, request.getCriticalLow(), request.getWarningLow(),
                CRITICAL_LOW_FIELD, WARNING_LOW_FIELD, CRITICAL_LOW_VS_WARNING_LOW_MESSAGE);
        ok &= checkOrderPair(context, request.getWarningLow(), request.getWarningHigh(),
                WARNING_LOW_FIELD, WARNING_HIGH_FIELD, WARNING_LOW_VS_WARNING_HIGH_MESSAGE);
        ok &= checkOrderPair(context, request.getWarningHigh(), request.getCriticalHigh(),
                WARNING_HIGH_FIELD, CRITICAL_HIGH_FIELD, WARNING_HIGH_VS_CRITICAL_HIGH_MESSAGE);
        ok &= checkHardLimits(context, request);
        return ok;
    }

    /**
     * D-10: null = unbounded, so a pair with either side null is skipped; otherwise the low
     * side must compare strictly less than the high side. One broken pair emits TWO violations.
     */
    private boolean checkOrderPair(ConstraintValidatorContext context, BigDecimal low, BigDecimal high,
                                   String lowField, String highField, String message) {
        if (low == null || high == null || low.compareTo(high) < 0) {
            return true;
        }
        // One violation per field — two independent builders, never one chained builder
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(lowField)
                .addConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(highField)
                .addConstraintViolation();
        return false;
    }

    /**
     * D-11: boundaries must lie within the metric's metadata hardMin/hardMax. Unknown or
     * custom metric keys pass through, consistent with MetricRecordValidator.
     */
    private boolean checkHardLimits(ConstraintValidatorContext context, UserThresholdRequest request) {
        Optional<MetricDefinition> definition = registry.getDefinition(request.getMetricKey());
        if (definition.isEmpty()) {
            return true;
        }
        MetricDefinition def = definition.get();
        boolean ok = true;
        ok &= checkBoundary(context, def, CRITICAL_LOW_FIELD, request.getCriticalLow());
        ok &= checkBoundary(context, def, WARNING_LOW_FIELD, request.getWarningLow());
        ok &= checkBoundary(context, def, WARNING_HIGH_FIELD, request.getWarningHigh());
        ok &= checkBoundary(context, def, CRITICAL_HIGH_FIELD, request.getCriticalHigh());
        return ok;
    }

    private boolean checkBoundary(ConstraintValidatorContext context, MetricDefinition definition,
                                  String fieldName, BigDecimal value) {
        if (value == null) {
            return true;
        }
        boolean underMin = definition.hardMin() != null && value.compareTo(definition.hardMin()) < 0;
        boolean overMax = definition.hardMax() != null && value.compareTo(definition.hardMax()) > 0;
        if (!underMin && !overMax) {
            return true;
        }
        // Static Chinese template only: display name and bounds come from the registry,
        // never from the request body (T-12-13)
        context.buildConstraintViolationWithTemplate(buildHardLimitMessage(definition))
                .addPropertyNode(fieldName)
                .addConstraintViolation();
        return false;
    }

    private String buildHardLimitMessage(MetricDefinition definition) {
        String unitStr = definition.unit() != null ? definition.unit() : "";
        return definition.displayName()
                + "有效生理区间为 "
                + formatBigDecimal(definition.hardMin())
                + " ~ "
                + formatBigDecimal(definition.hardMax())
                + " "
                + unitStr
                + "，请核对后重新输入";
    }

    private String formatBigDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
