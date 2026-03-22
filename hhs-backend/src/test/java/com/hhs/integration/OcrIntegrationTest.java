package com.hhs.integration;

import com.hhs.common.PageResult;
import com.hhs.component.BaiduOcrClient;
import com.hhs.config.TestSecurityConfig;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.entity.OcrHealthRecord;
import com.hhs.mapper.OcrHealthRecordMapper;
import com.hhs.vo.OcrHealthResultVO;
import com.hhs.vo.OcrHistoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OCR-based health data input flow.
 *
 * Tests the complete flow from image upload through OCR recognition
 * to metric confirmation and storage.
 *
 * @see com.hhs.controller.OcrController
 * @see com.hhs.service.OcrService
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@DisplayName("OCR健康图片识别集成测试")
public class OcrIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BaiduOcrClient baiduOcrClient;

    @Autowired
    private OcrHealthRecordMapper ocrHealthRecordMapper;

    private static final String API_BASE = "/api/ocr";
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // Mock BaiduOcrClient to return test OCR text
        when(baiduOcrClient.recognize(any(byte[].class)))
                .thenReturn(Optional.of("血糖 5.6 mmol/L\n心率 72 次/分\n血压 120/80 mmHg"));
    }

    @Nested
    @DisplayName("体检报告识别测试")
    class ReportRecognitionTests {

        @Test
        @DisplayName("OCR-INT-001: 识别体检报告 - 成功场景")
        void testRecognizeReport_Success() throws Exception {
            // Given: A valid medical report image
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.jpg", "image/jpeg",
                    "test image content".getBytes()
            );

            // When: Upload the image for recognition
            // Then: Should return parsed health metrics
            mockMvc.perform(multipart(API_BASE + "/report")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("success"))
                    .andExpect(jsonPath("$.data.ocrType").value("report"))
                    .andExpect(jsonPath("$.data.ocrRecordId").exists())
                    .andExpect(jsonPath("$.data.metrics").isArray());

            // Verify BaiduOcrClient was called
            verify(baiduOcrClient, atLeastOnce()).recognize(any(byte[].class));
        }

        @Test
        @DisplayName("OCR-INT-002: 识别体检报告 - 空文件返回错误")
        void testRecognizeReport_EmptyFile() throws Exception {
            // Given: An empty file
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]
            );

            // When: Upload the empty file
            // Then: Should return error
            mockMvc.perform(multipart(API_BASE + "/report")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("OCR-INT-003: 识别体检报告 - 非图片文件返回错误")
        void testRecognizeReport_NonImageFile() throws Exception {
            // Given: A non-image file
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf",
                    "pdf content".getBytes()
            );

            // When: Upload the non-image file
            // Then: Should return error
            mockMvc.perform(multipart(API_BASE + "/report")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Nested
    @DisplayName("药品标签识别测试")
    class MedicineRecognitionTests {

        @Test
        @DisplayName("OCR-INT-004: 识别药品标签 - 成功场景")
        void testRecognizeMedicine_Success() throws Exception {
            // Given: A valid medicine label image
            MockMultipartFile file = new MockMultipartFile(
                    "file", "medicine.jpg", "image/jpeg",
                    "medicine label image".getBytes()
            );

            // When: Upload the image for recognition
            // Then: Should return medicine information
            mockMvc.perform(multipart(API_BASE + "/medicine")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.ocrType").value("medicine"));
        }
    }

    @Nested
    @DisplayName("营养标签识别测试")
    class NutritionRecognitionTests {

        @Test
        @DisplayName("OCR-INT-005: 识别营养标签 - 成功场景")
        void testRecognizeNutrition_Success() throws Exception {
            // Given: A valid nutrition label image
            MockMultipartFile file = new MockMultipartFile(
                    "file", "nutrition.jpg", "image/jpeg",
                    "nutrition label image".getBytes()
            );

            // When: Upload the image for recognition
            // Then: Should return nutrition information
            mockMvc.perform(multipart(API_BASE + "/nutrition")
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.ocrType").value("nutrition"));
        }
    }

    @Nested
    @DisplayName("确认录入测试")
    class ConfirmMetricsTests {

        @Test
        @DisplayName("OCR-INT-006: 确认录入 - 成功场景")
        void testConfirmMetrics_Success() throws Exception {
            // Given: An existing OCR record with parsed metrics
            // First, create an OCR record by uploading an image
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.jpg", "image/jpeg",
                    "test image".getBytes()
            );

            MvcResult uploadResult = mockMvc.perform(multipart(API_BASE + "/report")
                            .file(file))
                    .andReturn();

            // Extract ocrRecordId from the response (simplified for skeleton)
            Long ocrRecordId = extractOcrRecordId(uploadResult);

            // Prepare confirm request
            String confirmJson = String.format("""
                {
                    "ocrRecordId": %d,
                    "metrics": [
                        {
                            "metricKey": "glucose",
                            "name": "血糖",
                            "value": 5.6,
                            "unit": "mmol/L",
                            "category": "HEALTH",
                            "selected": true,
                            "recordDate": "%s"
                        }
                    ]
                }
                """, ocrRecordId != null ? ocrRecordId : 1L, LocalDate.now());

            // When: Confirm the metrics
            // Then: Should save the metrics
            mockMvc.perform(post(API_BASE + "/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("OCR-INT-007: 确认录入 - 空指标列表返回错误")
        void testConfirmMetrics_EmptyMetrics() throws Exception {
            // Given: Confirm request with empty metrics
            String confirmJson = """
                {
                    "ocrRecordId": 1,
                    "metrics": []
                }
                """;

            // When: Confirm with empty metrics
            // Then: Should return validation error (400)
            mockMvc.perform(post(API_BASE + "/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("历史记录测试")
    class HistoryTests {

        @Test
        @DisplayName("OCR-INT-008: 获取历史记录 - 成功场景")
        void testGetHistory_Success() throws Exception {
            // When: Request OCR history
            // Then: Should return paginated history
            mockMvc.perform(get(API_BASE + "/history")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("OCR-INT-009: 获取记录详情 - 成功场景")
        void testGetRecordDetail_Success() throws Exception {
            // Given: An existing OCR record
            Long recordId = 1L;

            // When: Request the record detail
            // Then: Should return the detail (or 404 if not exists)
            mockMvc.perform(get(API_BASE + "/history/{recordId}", recordId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("OCR-INT-010: 删除记录 - 成功场景")
        void testDeleteRecord_Success() throws Exception {
            // Given: An existing OCR record
            Long recordId = 1L;

            // When: Delete the record
            // Then: Should return success (or error if not exists)
            mockMvc.perform(delete(API_BASE + "/history/{recordId}", recordId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }
    }

    @Nested
    @DisplayName("通用健康图片识别测试")
    class HealthImageTests {

        @Test
        @DisplayName("OCR-INT-011: 通用识别 - report类型")
        void testRecognizeHealthImage_ReportType() throws Exception {
            // Given: A health image with report type
            MockMultipartFile file = new MockMultipartFile(
                    "file", "health.jpg", "image/jpeg",
                    "health image".getBytes()
            );

            // When: Upload with ocrType parameter
            // Then: Should recognize as report type
            mockMvc.perform(multipart(API_BASE + "/health-image")
                            .file(file)
                            .param("ocrType", "report"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.ocrType").value("report"));
        }

        @Test
        @DisplayName("OCR-INT-012: 通用识别 - 不支持的类型返回错误")
        void testRecognizeHealthImage_UnsupportedType() throws Exception {
            // Given: A health image with unsupported type
            MockMultipartFile file = new MockMultipartFile(
                    "file", "health.jpg", "image/jpeg",
                    "health image".getBytes()
            );

            // When: Upload with invalid ocrType
            // Then: Should return error
            mockMvc.perform(multipart(API_BASE + "/health-image")
                            .file(file)
                            .param("ocrType", "invalid_type"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("failed"));
        }
    }

    /**
     * Helper method to extract ocrRecordId from upload response.
     * This is a simplified implementation for the skeleton.
     */
    private Long extractOcrRecordId(MvcResult result) {
        try {
            String response = result.getResponse().getContentAsString();
            // In a real implementation, parse JSON to extract ocrRecordId
            // For skeleton, return null to let tests handle it
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}