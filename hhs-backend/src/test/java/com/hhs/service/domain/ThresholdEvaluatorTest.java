package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertRule;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.AlertRuleMapper;
import com.hhs.mapper.UserThresholdMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Threshold Evaluator Unit Tests
 *
 * Pins FIX-03 (D-17) alias-aware threshold reads: a row stored under a legacy
 * alias (e.g. "glucose") must be found when the engine queries the canonical
 * key ("bloodGlucose"), the deterministic first-element pick must not throw on
 * a coexisting alias/canonical window, and the alert_rule fallback must be
 * alias-aware too. Case 5 cross-checks that plan 01's classify() fix and the
 * alias read compose into a real WARNING alert.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("阈值评估器测试")
class ThresholdEvaluatorTest {

    @Mock
    private UserThresholdMapper userThresholdMapper;

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<UserThreshold>> queryWrapperCaptor;

    private ThresholdEvaluator evaluator;

    private Long userId = 1L;

    /**
     * Lambda column cache must be initialized for plain unit tests so the captured
     * wrappers can be materialized (getSqlSegment) for assertion — column resolution
     * is lazy in MyBatis-Plus and would otherwise throw outside a Spring/mybatis boot.
     */
    @BeforeAll
    static void initLambdaColumnCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserThreshold.class);
        TableInfoHelper.initTableInfo(assistant, AlertRule.class);
    }

    @BeforeEach
    void setUp() {
        evaluator = new ThresholdEvaluator(
                userThresholdMapper,
                alertRuleMapper,
                new ThresholdComparator(),
                new ThresholdRangeChecker(),
                new MetricDisplayFormatter(new HealthMetricMetaRegistry()));
    }

    @Test
    @DisplayName("测试1：存量别名行 glucose 可被 bloodGlucose 查询命中")
    void getThresholdForUser_aliasRow_hits() {
        // Given: a legacy row stored under the alias key. The stub only answers when
        // the captured wrapper actually carries the full alias set — an exact-match
        // .eq("bloodGlucose") wrapper would find nothing and this test would fail
        // with "expected not null" (the FIX-03 pre-fix failure signal).
        UserThreshold aliasRow = thresholdRow(3L, "glucose");
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<UserThreshold> wrapper = inv.getArgument(0);
            return wrapperQueriesAliasSet(wrapper, "bloodGlucose", "glucose")
                    ? List.of(aliasRow)
                    : Collections.emptyList();
        });

        // When: the engine queries the canonical key
        UserThreshold result = evaluator.getThresholdForUser(userId, "bloodGlucose");

        // Then: the alias row the exact-match query would have missed is found
        assertNotNull(result);
        assertEquals("glucose", result.getMetricKey());
    }

    @Test
    @DisplayName("测试2：别名与标准行并存窗口 - 确定取首条且不抛 TooManyResultsException")
    void getThresholdForUser_aliasWindow_returnsFirstDeterministically() {
        // Given: a legacy alias row and the canonical row coexist
        UserThreshold aliasRow = thresholdRow(3L, "glucose");
        UserThreshold canonicalRow = thresholdRow(4L, "bloodGlucose");
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(aliasRow, canonicalRow));

        // When & Then: exactly the first supplied element is returned, no exception
        UserThreshold result = assertDoesNotThrow(() ->
                evaluator.getThresholdForUser(userId, "bloodGlucose"));

        assertSame(aliasRow, result);
    }

    @Test
    @DisplayName("测试3：alert_rule 回退查询同样按别名集合命中")
    void getThresholdForUser_ruleFallback_isAliasAware() {
        // Given: no personalized threshold; the seed rule is still stored under a legacy key
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        AlertRule rule = new AlertRule();
        rule.setId(7L);
        rule.setMetricKey("glucose");
        rule.setWarningHigh(new BigDecimal("7.0"));
        rule.setCriticalHigh(new BigDecimal("11.1"));
        rule.setWarningLow(new BigDecimal("3.9"));
        rule.setCriticalLow(new BigDecimal("3.0"));
        rule.setEnabled(true);

        when(alertRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<AlertRule> wrapper = inv.getArgument(0);
            return wrapperQueriesAliasSet(wrapper, "bloodGlucose", "glucose")
                    ? List.of(rule)
                    : Collections.emptyList();
        });

        // When: queried by the canonical key
        UserThreshold result = evaluator.getThresholdForUser(userId, "bloodGlucose");

        // Then: a converted threshold carrying the rule's boundary values is returned
        assertNotNull(result);
        assertEquals("glucose", result.getMetricKey());
        assertEquals(new BigDecimal("7.0"), result.getWarningHigh());
        assertEquals(new BigDecimal("11.1"), result.getCriticalHigh());
        assertEquals(new BigDecimal("3.9"), result.getWarningLow());
        assertEquals(new BigDecimal("3.0"), result.getCriticalLow());
    }

    @Test
    @DisplayName("测试4：用户阈值与规则均为空时返回 null")
    void getThresholdForUser_nothingConfigured_returnsNull() {
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(alertRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        assertNull(evaluator.getThresholdForUser(userId, "bloodGlucose"));
    }

    @Test
    @DisplayName("测试5：evaluate(systolicBP, 145) 警告带产生 WARNING/MEDIUM AlertVO")
    void evaluate_systolic145_warningBand_returnsMediumWarningAlert() {
        // Given: warningHigh 140, criticalHigh 160 — 145 sits in the warning band
        UserThreshold row = thresholdRow(5L, "systolicBP");
        row.setWarningHigh(new BigDecimal("140"));
        row.setCriticalHigh(new BigDecimal("160"));
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(row));

        // When: evaluate — the cross-check that plan 01's classify() fix and this
        // plan's alias read compose into a real alert
        AlertVO alert = evaluator.evaluate(userId, "systolicBP", new BigDecimal("145"));

        // Then: the alert actually materializes with WARNING type and MEDIUM level
        assertNotNull(alert);
        assertEquals("WARNING", alert.getAlertType());
        assertEquals("MEDIUM", alert.getAlertLevel());
    }

    @Test
    @DisplayName("测试6：wrapper 断言 - metric_key 条件是别名集合 IN 而非标量 eq")
    void getThresholdForUser_wrapperCarriesAliasIn_notScalarEq() {
        when(userThresholdMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(alertRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        evaluator.getThresholdForUser(userId, "bloodGlucose");

        verify(userThresholdMapper).selectList(queryWrapperCaptor.capture());
        LambdaQueryWrapper<UserThreshold> wrapper = queryWrapperCaptor.getValue();

        // Materialize the lazy segments, then assert the metric-key condition is
        // list-filtered: MyBatis-Plus 3.5.5 registers one param per IN element
        // (bytecode-verified inExpression), so the param map must span the whole
        // alias set rather than hold a single scalar key.
        String sqlSegment = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sqlSegment.contains(" IN "));
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        assertTrue(params.contains("bloodGlucose"));
        assertTrue(params.contains("glucose"));

        // Ownership filter survived the rewrite (T-12-04, ASVS V4)
        assertTrue(params.contains(userId));
    }

    /**
     * True only when the wrapper's materialized parameter map carries both the
     * canonical key and the legacy alias — i.e. the query is an alias-set IN,
     * not a scalar eq. This is what makes the alias-hit stubs in this class
     * sensitive to the real query condition.
     */
    private boolean wrapperQueriesAliasSet(LambdaQueryWrapper<?> wrapper,
                                           String canonicalKey, String legacyAlias) {
        wrapper.getSqlSegment(); // materialize lazy segments and register params
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        return params.contains(canonicalKey) && params.contains(legacyAlias);
    }

    private UserThreshold thresholdRow(Long id, String metricKey) {
        UserThreshold row = new UserThreshold();
        row.setId(id);
        row.setUserId(userId);
        row.setMetricKey(metricKey);
        return row;
    }
}
