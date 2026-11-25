package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.common.constant.ErrorCode;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.exception.BusinessException;
import com.hhs.security.SecurityUtils;
import com.hhs.service.HealthMetricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `/api/metrics/{id}` 的 IDOR 护栏（IN-11）。
 *
 * <p>DELETE 曾经调用无归属校验的 {@code delete(Long id)}；PUT 则把 JWT 的 userId 塞进请求体、
 * service 却从不读 —— 任一登录用户都能按自增 id 改/删他人指标。这两个方法现在都接收显式的
 * userId，这里从控制器一侧钉住「JWT 里的 userId 被真的传下去」：任何人把它改回
 * {@code delete(id)} / {@code update(id, request)}，这些用例都会编译失败或捕获到错误的参数。
 *
 * <p>刻意用直接构造控制器 + {@code MockedStatic} 而不是 {@code @WebMvcTest}：
 * {@code @Valid} 解析需要在 Spring 上下文里装配 MetricRecordValidator 的注册表依赖，
 * 而那与「控制器是否把 userId 传下去」这件事无关，只会让护栏测到别的失败。
 */
@DisplayName("健康指标控制器归属校验测试")
class HealthMetricControllerOwnershipTest {

    private HealthMetricService healthMetricService;
    private HealthMetricController controller;
    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    private static final Long CALLER_ID = 1L;

    @BeforeEach
    void setUp() {
        healthMetricService = mock(HealthMetricService.class);
        controller = new HealthMetricController(healthMetricService);
        mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(CALLER_ID);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("DELETE 把 JWT 的 userId 传给 service，而不是只传 id")
    void deletePassesCallerId() {
        when(healthMetricService.delete(any(), any())).thenReturn(true);

        Result<Void> result = controller.delete(42L);

        assertEquals(200, result.getCode());
        // 无校验重载已从接口移除：这里能且只能观察到带 userId 的那一个
        verify(healthMetricService).delete(CALLER_ID, 42L);
    }

    @Test
    @DisplayName("PUT 把 JWT 的 userId 作为独立参数传给 service")
    void updatePassesCallerId() {
        HealthMetric updated = new HealthMetric();
        updated.setId(42L);
        updated.setUserId(CALLER_ID);
        when(healthMetricService.update(any(), any(), any())).thenReturn(updated);

        HealthMetricRequest body = new HealthMetricRequest();
        body.setMetricKey("heartRate");
        body.setValue(new BigDecimal("72"));
        body.setRecordDate(LocalDate.now());

        Result<HealthMetric> result = controller.update(42L, body);

        assertEquals(200, result.getCode());

        ArgumentCaptor<Long> caller = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> metricId = ArgumentCaptor.forClass(Long.class);
        verify(healthMetricService).update(caller.capture(), metricId.capture(), any());
        assertEquals(CALLER_ID, caller.getValue());
        assertEquals(42L, metricId.getValue());
    }

    @Test
    @DisplayName("越权删除时 service 的 403 原样抛出，不被控制器吞掉")
    void forbiddenDeleteSurfaces() {
        when(healthMetricService.delete(eq(CALLER_ID), eq(42L)))
                .thenThrow(new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除他人记录"));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.delete(42L));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
        assertTrue(exception.getDetail().contains("无权"));
        verify(healthMetricService).delete(CALLER_ID, 42L);
    }

    @Test
    @DisplayName("越权修改时 service 的 403 原样抛出，且没有走无校验路径")
    void forbiddenUpdateSurfaces() {
        when(healthMetricService.update(eq(CALLER_ID), eq(42L), any()))
                .thenThrow(new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权修改他人记录"));

        HealthMetricRequest body = new HealthMetricRequest();
        body.setMetricKey("heartRate");
        body.setValue(new BigDecimal("72"));
        body.setRecordDate(LocalDate.now());

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.update(42L, body));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
        verify(healthMetricService, never()).delete(any(), any());
    }
}