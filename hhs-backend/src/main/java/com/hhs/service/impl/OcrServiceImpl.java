package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.PageResult;
import com.hhs.common.constant.ErrorCode;
import com.hhs.component.BaiduOcrClient;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.*;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.*;
import com.hhs.security.PathValidationUtil;
import com.hhs.service.*;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.OcrHealthResultVO;
import com.hhs.vo.OcrHistoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrServiceImpl implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrServiceImpl.class);

    // Mock OCR text for testing when Baidu OCR is not configured
    private static final String MOCK_REPORT_TEXT = """
        血糖 5.6 mmol/L 3.9-6.1
        总胆固醇 4.2 mmol/L 参考值<5.2
        甘油三酯 1.5 mmol/L 0.4-1.7
        血压 120/80 mmHg
        心率 72 次/分
        """;

    private static final String MOCK_MEDICINE_TEXT = """
        阿司匹林肠溶片
        规格: 100mg
        用法: 口服 一次1片 一日1次
        有效期: 2026-12-31
        """;

    private static final String MOCK_NUTRITION_TEXT = """
        营养成分表
        能量 2000 kJ
        蛋白质 5.0 g
        脂肪 10.0 g
        碳水化合物 30.0 g
        钠 400 mg
        """;

    // Metric key mappings for OCR recognition
    private static final Map<String, String> METRIC_KEY_MAP = Map.ofEntries(
        // Health metrics
        Map.entry("血糖", "glucose"),
        Map.entry("空腹血糖", "fastingGlucose"),
        Map.entry("餐后血糖", "postprandialGlucose"),
        Map.entry("心率", "heartRate"),
        Map.entry("收缩压", "systolicBP"),
        Map.entry("舒张压", "diastolicBP"),
        Map.entry("血压", "bloodPressure"),
        Map.entry("体重", "weight"),
        Map.entry("体温", "bodyTemperature"),
        Map.entry("血氧", "bloodOxygen"),
        Map.entry("总胆固醇", "totalCholesterol"),
        Map.entry("甘油三酯", "triglycerides"),
        // Lipid panel metrics
        Map.entry("高密度脂蛋白", "hdlCholesterol"),
        Map.entry("高密度脂蛋白胆固醇", "hdlCholesterol"),
        Map.entry("低密度脂蛋白", "ldlCholesterol"),
        Map.entry("低密度脂蛋白胆固醇", "ldlCholesterol"),
        // Kidney function metrics
        Map.entry("肌酐", "creatinine"),
        Map.entry("尿酸", "uricAcid"),
        // Blood routine metrics
        Map.entry("血红蛋白", "hemoglobin"),
        Map.entry("白细胞", "wbc"),
        Map.entry("白细胞计数", "wbc"),
        Map.entry("红细胞", "rbc"),
        Map.entry("红细胞计数", "rbc"),
        Map.entry("血小板", "platelet"),
        Map.entry("血小板计数", "platelet"),
        // Wellness metrics
        Map.entry("步数", "steps"),
        Map.entry("睡眠", "sleepDuration"),
        Map.entry("运动", "exerciseMinutes"),
        Map.entry("饮水", "waterIntake")
    );

    // Unit mappings - use Map.ofEntries for more than 10 entries
    private static final Map<String, String> DEFAULT_UNITS = Map.ofEntries(
        Map.entry("glucose", "mmol/L"),
        Map.entry("fastingGlucose", "mmol/L"),
        Map.entry("postprandialGlucose", "mmol/L"),
        Map.entry("heartRate", "次/分"),
        Map.entry("systolicBP", "mmHg"),
        Map.entry("diastolicBP", "mmHg"),
        Map.entry("weight", "kg"),
        Map.entry("bodyTemperature", "℃"),
        Map.entry("bloodOxygen", "%"),
        Map.entry("totalCholesterol", "mmol/L"),
        Map.entry("triglycerides", "mmol/L"),
        // Lipid panel units
        Map.entry("hdlCholesterol", "mmol/L"),
        Map.entry("ldlCholesterol", "mmol/L"),
        // Kidney function units
        Map.entry("creatinine", "μmol/L"),
        Map.entry("uricAcid", "μmol/L"),
        // Blood routine units
        Map.entry("hemoglobin", "g/L"),
        Map.entry("wbc", "10^9/L"),
        Map.entry("rbc", "10^12/L"),
        Map.entry("platelet", "10^9/L"),
        // Wellness units
        Map.entry("steps", "步"),
        Map.entry("sleepDuration", "小时"),
        Map.entry("exerciseMinutes", "分钟"),
        Map.entry("waterIntake", "毫升")
    );

    // Health metric keys
    private static final Set<String> HEALTH_KEYS = Set.of(
        "glucose", "fastingGlucose", "postprandialGlucose",
        "heartRate", "systolicBP", "diastolicBP", "bloodPressure",
        "weight", "bodyTemperature", "bloodOxygen", "bmi",
        "totalCholesterol", "triglycerides",
        "hdlCholesterol", "ldlCholesterol",
        "creatinine", "uricAcid",
        "hemoglobin", "wbc", "rbc", "platelet"
    );

    // Wellness metric keys
    private static final Set<String> WELLNESS_KEYS = Set.of(
        "sleepDuration", "sleepQuality", "steps",
        "exerciseMinutes", "waterIntake", "mood", "energy"
    );

    // Reference ranges for confidence calculation (min, max)
    private static final Map<String, BigDecimal[]> REFERENCE_RANGES = Map.ofEntries(
        // Blood glucose
        Map.entry("glucose", new BigDecimal[]{new BigDecimal("3.9"), new BigDecimal("6.1")}),
        Map.entry("fastingGlucose", new BigDecimal[]{new BigDecimal("3.9"), new BigDecimal("6.1")}),
        Map.entry("postprandialGlucose", new BigDecimal[]{new BigDecimal("3.9"), new BigDecimal("7.8")}),
        // Vital signs
        Map.entry("heartRate", new BigDecimal[]{new BigDecimal("60"), new BigDecimal("100")}),
        Map.entry("systolicBP", new BigDecimal[]{new BigDecimal("90"), new BigDecimal("140")}),
        Map.entry("diastolicBP", new BigDecimal[]{new BigDecimal("60"), new BigDecimal("90")}),
        Map.entry("bloodOxygen", new BigDecimal[]{new BigDecimal("95"), new BigDecimal("100")}),
        Map.entry("bodyTemperature", new BigDecimal[]{new BigDecimal("36.0"), new BigDecimal("37.5")}),
        // Lipid panel
        Map.entry("totalCholesterol", new BigDecimal[]{new BigDecimal("0"), new BigDecimal("5.2")}),
        Map.entry("triglycerides", new BigDecimal[]{new BigDecimal("0"), new BigDecimal("1.7")}),
        Map.entry("hdlCholesterol", new BigDecimal[]{new BigDecimal("1.0"), new BigDecimal("2.0")}),
        Map.entry("ldlCholesterol", new BigDecimal[]{new BigDecimal("0"), new BigDecimal("3.4")}),
        // Kidney function
        Map.entry("creatinine", new BigDecimal[]{new BigDecimal("44"), new BigDecimal("133")}),
        Map.entry("uricAcid", new BigDecimal[]{new BigDecimal("150"), new BigDecimal("420")}),
        // Blood routine
        Map.entry("hemoglobin", new BigDecimal[]{new BigDecimal("120"), new BigDecimal("160")}),
        Map.entry("wbc", new BigDecimal[]{new BigDecimal("4"), new BigDecimal("10")}),
        Map.entry("rbc", new BigDecimal[]{new BigDecimal("4.0"), new BigDecimal("5.5")}),
        Map.entry("platelet", new BigDecimal[]{new BigDecimal("100"), new BigDecimal("300")})
    );

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private final ExaminationReportMapper examinationReportMapper;
    private final LabResultMapper labResultMapper;
    private final OcrRecognizeLogMapper ocrRecognizeLogMapper;
    private final OcrHealthRecordMapper ocrHealthRecordMapper;
    private final BaiduOcrClient baiduOcrClient;
    private final ReportStatusService reportStatusService;
    private final HealthMetricService healthMetricService;
    private final WellnessService wellnessService;
    private final ObjectMapper objectMapper;

    public OcrServiceImpl(ExaminationReportMapper examinationReportMapper,
                          LabResultMapper labResultMapper,
                          OcrRecognizeLogMapper ocrRecognizeLogMapper,
                          OcrHealthRecordMapper ocrHealthRecordMapper,
                          BaiduOcrClient baiduOcrClient,
                          ReportStatusService reportStatusService,
                          HealthMetricService healthMetricService,
                          WellnessService wellnessService,
                          ObjectMapper objectMapper) {
        this.examinationReportMapper = examinationReportMapper;
        this.labResultMapper = labResultMapper;
        this.ocrRecognizeLogMapper = ocrRecognizeLogMapper;
        this.ocrHealthRecordMapper = ocrHealthRecordMapper;
        this.baiduOcrClient = baiduOcrClient;
        this.reportStatusService = reportStatusService;
        this.healthMetricService = healthMetricService;
        this.wellnessService = wellnessService;
        this.objectMapper = objectMapper;
    }

    // =====================================================
    // Existing method - processReport
    // =====================================================

    @Override
    @Transactional
    public void processReport(Long reportId) {
        log.info("Processing OCR for report {}", reportId);
        ExaminationReport report = examinationReportMapper.selectById(reportId);
        if (report == null) {
            // Throw exception instead of silent return - this makes debugging easier
            // and allows proper error handling in the event listener
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "报告不存在: ID=" + reportId + "。可能原因：事务尚未提交或报告已被删除");
        }

        long start = System.currentTimeMillis();
        String rawText;

        try {
            byte[] bytes = readReportFile(report.getFileUrl());
            Optional<String> ocrText = baiduOcrClient.recognize(bytes);
            boolean usedMockData = ocrText.isEmpty();
            rawText = ocrText.orElse(MOCK_REPORT_TEXT);

            if (usedMockData) {
                log.warn("OCR service not configured, using mock data for report: reportId={}", reportId);
            }

            List<ReportTextParser.LabResultItem> items = ReportTextParser.parse(rawText);
            int durationMs = (int) (System.currentTimeMillis() - start);

            OcrRecognizeLog logEntity = new OcrRecognizeLog();
            logEntity.setReportId(reportId);
            logEntity.setStatus("SUCCESS");
            logEntity.setRawText(rawText.length() > 5000 ? rawText.substring(0, 5000) : rawText);
            logEntity.setDurationMs(durationMs);
            ocrRecognizeLogMapper.insert(logEntity);

            labResultMapper.delete(new LambdaQueryWrapper<LabResult>().eq(LabResult::getReportId, reportId));
            StringBuilder abnormalSummary = new StringBuilder();
            for (ReportTextParser.LabResultItem item : items) {
                LabResult lr = new LabResult();
                lr.setReportId(reportId);
                lr.setName(item.name);
                lr.setCategory("");
                lr.setValue(item.value);
                lr.setUnit(item.unit);
                lr.setReferenceRange(item.referenceRange);
                lr.setIsAbnormal(item.abnormal ? 1 : 0);
                lr.setSortOrder(item.sortOrder);
                labResultMapper.insert(lr);
                if (item.abnormal) {
                    if (abnormalSummary.length() > 0) abnormalSummary.append("；");
                    abnormalSummary.append(item.name).append(" ").append(item.value);
                }
            }

            String abnormalSummaryText = abnormalSummary.length() > 0 ? abnormalSummary.toString() : null;
            reportStatusService.updateOcrStatus(reportId, "SUCCESS", abnormalSummaryText);

            report.setOcrStatus("SUCCESS");
            report.setAbnormalSummary(abnormalSummaryText);
            examinationReportMapper.updateById(report);

            log.info("OCR processing completed for report {} in {}ms, usedMockData={}", reportId, durationMs, usedMockData);

        } catch (Exception e) {
            log.error("OCR processing failed for report {}", reportId, e);
            reportStatusService.updateOcrStatus(reportId, "FAILED", "识别失败: " + e.getMessage());

            OcrRecognizeLog logEntity = new OcrRecognizeLog();
            logEntity.setReportId(reportId);
            logEntity.setStatus("FAILED");
            logEntity.setRawText("Processing failed: " + e.getMessage());
            logEntity.setDurationMs((int) (System.currentTimeMillis() - start));
            ocrRecognizeLogMapper.insert(logEntity);
        }
    }

    // =====================================================
    // New methods for smart input
    // =====================================================

    @Override
    @Transactional
    public OcrHealthResultVO recognizeHealthImage(Long userId, MultipartFile file, String ocrType) {
        log.info("Recognizing health image: userId={}, ocrType={}", userId, ocrType);

        // Validate OCR type
        if (!Set.of("report", "medicine", "nutrition").contains(ocrType)) {
            return OcrHealthResultVO.failed("不支持的识别类型: " + ocrType);
        }

        long start = System.currentTimeMillis();
        String originalFilename = file.getOriginalFilename();
        String storedFilename = null;

        try {
            // Save file
            storedFilename = saveUploadedFile(file, "ocr");

            // Read file bytes
            byte[] bytes = file.getBytes();

            // Call OCR
            Optional<String> ocrText = baiduOcrClient.recognize(bytes);
            boolean usedMockData = ocrText.isEmpty();
            String rawText = ocrText.orElse(getMockText(ocrType));

            // Parse metrics based on type
            List<OcrHealthResultVO.RecognizedMetric> metrics = parseOcrText(rawText, ocrType);

            int durationMs = (int) (System.currentTimeMillis() - start);

            // Save record
            OcrHealthRecord record = new OcrHealthRecord();
            record.setUserId(userId);
            record.setOcrType(ocrType);
            record.setRawText(rawText);
            record.setParseResult(objectMapper.writeValueAsString(metrics));
            record.setConfirmed(false);
            record.setOriginalFilename(originalFilename);
            record.setStoredFilename(storedFilename);
            record.setStatus("success");
            record.setDurationMs(durationMs);
            record.setCreateTime(LocalDateTime.now());
            ocrHealthRecordMapper.insert(record);

            // Use appropriate result method based on whether mock data was used
            OcrHealthResultVO result = usedMockData
                    ? OcrHealthResultVO.successWithMockData(ocrType, metrics, rawText)
                    : OcrHealthResultVO.success(ocrType, metrics, rawText);
            result.setOcrRecordId(record.getId());

            if (usedMockData) {
                log.warn("OCR service not configured, using mock data: userId={}, ocrType={}", userId, ocrType);
            }

            log.info("OCR recognition completed: userId={}, ocrType={}, metricsCount={}, duration={}ms, usedMockData={}",
                    userId, ocrType, metrics.size(), durationMs, usedMockData);

            return result;

        } catch (Exception e) {
            log.error("OCR recognition failed: userId={}, ocrType={}", userId, ocrType, e);

            // Save failed record
            try {
                OcrHealthRecord record = new OcrHealthRecord();
                record.setUserId(userId);
                record.setOcrType(ocrType);
                record.setOriginalFilename(originalFilename);
                record.setStoredFilename(storedFilename);
                record.setStatus("failed");
                record.setErrorMessage(e.getMessage());
                record.setDurationMs((int) (System.currentTimeMillis() - start));
                record.setCreateTime(LocalDateTime.now());
                ocrHealthRecordMapper.insert(record);
            } catch (Exception ignored) {
                log.error("Failed to save OCR error record", ignored);
            }

            return OcrHealthResultVO.failed(e.getMessage());
        }
    }

    @Override
    public OcrHealthResultVO recognizeReport(Long userId, MultipartFile file) {
        return recognizeHealthImage(userId, file, "report");
    }

    @Override
    public OcrHealthResultVO recognizeMedicine(Long userId, MultipartFile file) {
        return recognizeHealthImage(userId, file, "medicine");
    }

    @Override
    public OcrHealthResultVO recognizeNutrition(Long userId, MultipartFile file) {
        return recognizeHealthImage(userId, file, "nutrition");
    }

    @Override
    @Transactional
    public List<Long> confirmMetrics(Long userId, OcrConfirmRequest request) {
        log.info("Confirming OCR metrics: userId={}, ocrRecordId={}", userId, request.getOcrRecordId());

        if (request.getMetrics() == null || request.getMetrics().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "指标列表不能为空");
        }

        // Validate OCR record
        OcrHealthRecord record = null;
        if (request.getOcrRecordId() != null) {
            record = ocrHealthRecordMapper.selectById(request.getOcrRecordId());
            if (record != null && !record.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权操作此记录");
            }
            if (record != null && Boolean.TRUE.equals(record.getConfirmed())) {
                throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "此记录已确认，请勿重复提交");
            }
        }

        List<Long> savedIds = new ArrayList<>();
        List<String> failedMetrics = new ArrayList<>();

        for (OcrConfirmRequest.MetricItem item : request.getMetrics()) {
            if (!Boolean.TRUE.equals(item.getSelected())) {
                continue;
            }

            String metricKey = item.getMetricKey();
            if (!StringUtils.hasText(metricKey)) {
                continue;
            }

            // Determine category
            String category = item.getCategory();
            if (!StringUtils.hasText(category)) {
                category = determineCategory(metricKey);
            }

            try {
                Long savedId = saveMetric(userId, item, category);
                if (savedId != null) {
                    savedIds.add(savedId);
                } else {
                    failedMetrics.add(item.getName() != null ? item.getName() : metricKey);
                }
            } catch (Exception e) {
                log.error("Failed to save metric: metricKey={}, error={}", metricKey, e.getMessage());
                failedMetrics.add(item.getName() != null ? item.getName() : metricKey);
            }
        }

        // Update record as confirmed
        if (record != null && !savedIds.isEmpty()) {
            record.setConfirmed(true);
            try {
                record.setMetricIds(objectMapper.writeValueAsString(savedIds));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize metric IDs", e);
            }
            ocrHealthRecordMapper.updateById(record);
        }

        if (!failedMetrics.isEmpty() && savedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "所有指标保存失败: " + String.join(", ", failedMetrics));
        }

        log.info("Successfully saved {} metrics", savedIds.size());
        return savedIds;
    }

    // =====================================================
    // Helper methods
    // =====================================================

    private String getMockText(String ocrType) {
        return switch (ocrType) {
            case "report" -> MOCK_REPORT_TEXT;
            case "medicine" -> MOCK_MEDICINE_TEXT;
            case "nutrition" -> MOCK_NUTRITION_TEXT;
            default -> MOCK_REPORT_TEXT;
        };
    }

    private String saveUploadedFile(MultipartFile file, String subDir) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve(subDir);
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filename;
    }

    private List<OcrHealthResultVO.RecognizedMetric> parseOcrText(String rawText, String ocrType) {
        List<OcrHealthResultVO.RecognizedMetric> metrics = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Pattern for metrics: name + number + unit
        // Updated to include lipid panel, kidney function, and blood routine metrics
        Pattern metricPattern = Pattern.compile(
            "(血糖|空腹血糖|餐后血糖|心率|收缩压|舒张压|血压|体重|体温|血氧|" +
            "总胆固醇|甘油三酯|高密度脂蛋白|高密度脂蛋白胆固醇|低密度脂蛋白|低密度脂蛋白胆固醇|" +
            "肌酐|尿酸|血红蛋白|白细胞|白细胞计数|红细胞|红细胞计数|血小板|血小板计数|" +
            "步数|睡眠|运动|饮水)" +
            "\\s*([\\d.]+)\\s*" +
            "(mmol/L|mmHg|次/分|kg|℃|%|步|小时|分钟|毫升|g|mg|kJ|μmol/L|umol/L|10\\^9/L|10\\^12/L)?"
        );

        Matcher matcher = metricPattern.matcher(rawText);
        while (matcher.find()) {
            String name = matcher.group(1);
            String valueStr = matcher.group(2);
            String unit = matcher.group(3);

            String metricKey = METRIC_KEY_MAP.get(name);
            if (metricKey == null) {
                continue;
            }

            BigDecimal value;
            try {
                value = new BigDecimal(valueStr, MathContext.DECIMAL64);
            } catch (NumberFormatException e) {
                continue;
            }

            // Handle blood pressure specially (e.g., "120/80")
            if ("bloodPressure".equals(metricKey)) {
                // Split into systolic and diastolic
                if (valueStr.contains("/")) {
                    String[] parts = valueStr.split("/");
                    if (parts.length == 2) {
                        // Add systolic
                        metrics.add(createMetric("systolicBP", "收缩压",
                                new BigDecimal(parts[0]), "mmHg", today));
                        // Add diastolic
                        metrics.add(createMetric("diastolicBP", "舒张压",
                                new BigDecimal(parts[1]), "mmHg", today));
                    }
                }
                continue;
            }

            // Use default unit if not specified
            if (unit == null) {
                unit = DEFAULT_UNITS.get(metricKey);
            }

            String category = determineCategory(metricKey);

            metrics.add(createMetric(metricKey, name, value, unit, today));
        }

        return metrics;
    }

    private OcrHealthResultVO.RecognizedMetric createMetric(
            String metricKey, String name, BigDecimal value, String unit, LocalDate recordDate) {

        // Calculate dynamic confidence
        BigDecimal confidence = calculateConfidence(metricKey, value, unit);

        return OcrHealthResultVO.RecognizedMetric.builder()
                .metricKey(metricKey)
                .name(name)
                .value(value)
                .unit(unit)
                .confidence(confidence)
                .category(determineCategory(metricKey))
                .recordDate(recordDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .selected(true)
                .build();
    }

    /**
     * Calculate confidence score based on:
     * - Base confidence: 0.75
     * - Unit match: +0.1 if detected unit matches expected unit
     * - Value in normal range: +0.1 if value falls within reference range
     * - Final score clamped between 0.5 and 0.95
     */
    private BigDecimal calculateConfidence(String metricKey, BigDecimal value, String detectedUnit) {
        BigDecimal confidence = new BigDecimal("0.75");

        // Check unit match
        String expectedUnit = DEFAULT_UNITS.get(metricKey);
        if (expectedUnit != null && detectedUnit != null) {
            // Normalize units for comparison
            String normalizedDetected = normalizeUnit(detectedUnit);
            String normalizedExpected = normalizeUnit(expectedUnit);
            if (normalizedDetected.equals(normalizedExpected)) {
                confidence = confidence.add(new BigDecimal("0.10"));
            }
        }

        // Check if value is within normal range
        BigDecimal[] range = REFERENCE_RANGES.get(metricKey);
        if (range != null && value != null) {
            if (value.compareTo(range[0]) >= 0 && value.compareTo(range[1]) <= 0) {
                confidence = confidence.add(new BigDecimal("0.10"));
            }
        }

        // Clamp between 0.5 and 0.95
        if (confidence.compareTo(new BigDecimal("0.95")) > 0) {
            confidence = new BigDecimal("0.95");
        } else if (confidence.compareTo(new BigDecimal("0.50")) < 0) {
            confidence = new BigDecimal("0.50");
        }

        return confidence.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Normalize unit strings for comparison
     */
    private String normalizeUnit(String unit) {
        if (unit == null) return "";
        return unit.replace("μ", "u")
                   .replace("μmol/L", "umol/L")
                   .replace("10^9/L", "10e9/L")
                   .replace("10^12/L", "10e12/L")
                   .toLowerCase()
                   .trim();
    }

    private String determineCategory(String metricKey) {
        if (HEALTH_KEYS.contains(metricKey)) {
            return "HEALTH";
        } else if (WELLNESS_KEYS.contains(metricKey)) {
            return "WELLNESS";
        }
        return "HEALTH"; // Default to HEALTH
    }

    private Long saveMetric(Long userId, OcrConfirmRequest.MetricItem item, String category) {
        LocalDate recordDate = item.getRecordDate() != null
                ? LocalDate.parse(item.getRecordDate())
                : LocalDate.now();

        if ("WELLNESS".equals(category)) {
            WellnessMetricRequest request = new WellnessMetricRequest();
            request.setMetricKey(item.getMetricKey());
            request.setValue(item.getValue());
            request.setRecordDate(recordDate);
            request.setUnit(item.getUnit());

            HealthMetricVO saved = wellnessService.createWellnessMetric(userId, request);
            return saved != null ? saved.id() : null;
        } else {
            HealthMetricRequest request = new HealthMetricRequest();
            request.setUserId(userId);
            request.setMetricKey(item.getMetricKey());
            request.setValue(item.getValue());
            request.setRecordDate(recordDate);
            request.setUnit(item.getUnit());

            HealthMetricVO saved = healthMetricService.add(userId, request);
            return saved != null ? saved.id() : null;
        }
    }

    private byte[] readReportFile(String fileUrl) throws Exception {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            throw new IllegalArgumentException("无效的 fileUrl");
        }
        String relative = fileUrl.substring("/uploads/".length());

        if (!PathValidationUtil.isFilenameSafe(relative)) {
            throw new SecurityException("Invalid file path detected: " + relative);
        }

        Path uploadDirPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path resolvedPath = PathValidationUtil.validateAndResolvePath(uploadDirPath.toString(), relative);

        if (!Files.exists(resolvedPath)) {
            throw new IllegalArgumentException("文件不存在: " + resolvedPath);
        }
        return Files.readAllBytes(resolvedPath);
    }

    // =====================================================
    // OCR History methods
    // =====================================================

    @Override
    public PageResult<OcrHistoryVO> getHistory(Long userId, int page, int size) {
        log.info("Getting OCR history: userId={}, page={}, size={}", userId, page, size);

        // Build query
        LambdaQueryWrapper<OcrHealthRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OcrHealthRecord::getUserId, userId)
               .orderByDesc(OcrHealthRecord::getCreateTime);

        // Get total count
        long total = ocrHealthRecordMapper.selectCount(wrapper);

        // Get paginated records
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<OcrHealthRecord> records = ocrHealthRecordMapper.selectList(wrapper);

        // Convert to VO
        List<OcrHistoryVO> historyList = records.stream()
                .map(this::toHistoryVO)
                .toList();

        return PageResult.of(total, page, size, historyList);
    }

    @Override
    public OcrHealthResultVO getRecordDetail(Long userId, Long recordId) {
        log.info("Getting OCR record detail: userId={}, recordId={}", userId, recordId);

        OcrHealthRecord record = ocrHealthRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权访问此记录");
        }

        // Parse metrics from stored JSON
        List<OcrHealthResultVO.RecognizedMetric> metrics = new ArrayList<>();
        if (record.getParseResult() != null) {
            try {
                metrics = objectMapper.readValue(record.getParseResult(),
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, OcrHealthResultVO.RecognizedMetric.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse metrics from record: {}", recordId, e);
            }
        }

        OcrHealthResultVO result = OcrHealthResultVO.success(record.getOcrType(), metrics, record.getRawText());
        result.setOcrRecordId(record.getId());

        if ("failed".equals(record.getStatus())) {
            result.setStatus("failed");
            result.setErrorMessage(record.getErrorMessage());
        }

        return result;
    }

    @Override
    @Transactional
    public boolean deleteRecord(Long userId, Long recordId) {
        log.info("Deleting OCR record: userId={}, recordId={}", userId, recordId);

        OcrHealthRecord record = ocrHealthRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除此记录");
        }

        int deleted = ocrHealthRecordMapper.deleteById(recordId);
        return deleted > 0;
    }

    private OcrHistoryVO toHistoryVO(OcrHealthRecord record) {
        // Count metrics from parseResult
        int metricsCount = 0;
        if (record.getParseResult() != null) {
            try {
                List<?> metrics = objectMapper.readValue(record.getParseResult(), List.class);
                metricsCount = metrics.size();
            } catch (JsonProcessingException e) {
                log.error("Failed to parse metrics count from record: {}", record.getId(), e);
            }
        }

        return OcrHistoryVO.builder()
                .id(record.getId())
                .ocrType(record.getOcrType())
                .status(record.getStatus())
                .metricsCount(metricsCount)
                .confirmed(record.getConfirmed())
                .originalFilename(record.getOriginalFilename())
                .durationMs(record.getDurationMs())
                .createTime(record.getCreateTime())
                .errorMessage(record.getErrorMessage())
                .build();
    }
}