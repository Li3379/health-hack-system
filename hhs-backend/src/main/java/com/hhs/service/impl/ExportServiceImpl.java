package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.entity.ExportJob;
import com.hhs.entity.ExaminationReport;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.entity.LabResult;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.ExportJobMapper;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthScoreHistoryMapper;
import com.hhs.mapper.LabResultMapper;
import com.hhs.service.ExportService;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Export service implementation.
 * Processes data exports asynchronously and stores files on disk.
 */
@Slf4j
@Service
public class ExportServiceImpl implements ExportService {

    private final ExportJobMapper exportJobMapper;
    private final HealthMetricMapper healthMetricMapper;
    private final HealthScoreHistoryMapper healthScoreHistoryMapper;
    private final ExaminationReportMapper examinationReportMapper;
    private final LabResultMapper labResultMapper;
    private final ExportServiceImpl self;

    public ExportServiceImpl(ExportJobMapper exportJobMapper,
                             HealthMetricMapper healthMetricMapper,
                             HealthScoreHistoryMapper healthScoreHistoryMapper,
                             ExaminationReportMapper examinationReportMapper,
                             LabResultMapper labResultMapper,
                             @Lazy ExportServiceImpl self) {
        this.exportJobMapper = exportJobMapper;
        this.healthMetricMapper = healthMetricMapper;
        this.healthScoreHistoryMapper = healthScoreHistoryMapper;
        this.examinationReportMapper = examinationReportMapper;
        this.labResultMapper = labResultMapper;
        this.self = self;
    }

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ExportJob createExport(Long userId, String exportType, String contentType,
                                  LocalDate startDate, LocalDate endDate) {
        ExportJob job = new ExportJob();
        job.setUserId(userId);
        job.setExportType(exportType);
        job.setContentType(contentType);
        job.setStatus("pending");
        job.setStartDate(startDate);
        job.setEndDate(endDate);
        job.setCreatedAt(LocalDateTime.now());
        exportJobMapper.insert(job);

        log.info("Created export job {} for user {}: type={}, content={}",
                job.getId(), userId, exportType, contentType);

        self.processExportAsync(job.getId());
        return job;
    }

    @Override
    public ExportJob getExportStatus(Long jobId, Long userId) {
        ExportJob job = exportJobMapper.selectById(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        return job;
    }

    @Override
    public byte[] getExportFile(Long jobId, Long userId) {
        ExportJob job = getExportStatus(jobId, userId);
        if (!"completed".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "导出任务尚未完成");
        }
        Path filePath = Paths.get(job.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.RESOURCE_FILE_NOT_FOUND, "导出文件不存在");
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read export file: {}", job.getFilePath(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取导出文件失败", e);
        }
    }

    @Async("taskExecutor")
    protected void processExportAsync(Long jobId) {
        ExportJob job = exportJobMapper.selectById(jobId);
        if (job == null) {
            log.error("Export job {} not found", jobId);
            return;
        }
        job.setStatus("processing");
        exportJobMapper.updateById(job);

        try {
            byte[] fileBytes;
            String fileName;
            if ("csv".equals(job.getExportType())) {
                fileBytes = generateCsv(job);
                fileName = buildFileName(job, "csv");
            } else {
                fileBytes = generateExcel(job);
                fileName = buildFileName(job, "xlsx");
            }

            Path exportDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("exports");
            Files.createDirectories(exportDir);
            String safeName = job.getId() + "_" + System.currentTimeMillis() + "_" + fileName;
            Path filePath = exportDir.resolve(safeName);
            Files.write(filePath, fileBytes);

            job.setFilePath(filePath.toString());
            job.setFileName(fileName);
            job.setStatus("completed");
            job.setCompletedAt(LocalDateTime.now());
            exportJobMapper.updateById(job);
            log.info("Export job {} completed, file: {}", jobId, fileName);
        } catch (Exception e) {
            log.error("Export job {} failed", jobId, e);
            job.setStatus("failed");
            job.setErrorMessage(truncate(e.getMessage(), 500));
            job.setCompletedAt(LocalDateTime.now());
            exportJobMapper.updateById(job);
        }
    }

    // ========================================================================
    // CSV generation
    // ========================================================================

    private byte[] generateCsv(ExportJob job) throws IOException {
        StringWriter sw = new StringWriter();
        // BOM for Excel-compatible Chinese encoding
        sw.write('\uFEFF');
        try (CSVWriter writer = new CSVWriter(sw)) {
            switch (job.getContentType()) {
                case "metrics" -> writeMetricsCsv(writer, job);
                case "scores" -> writeScoresCsv(writer, job);
                case "screenings" -> writeScreeningsCsv(writer, job);
                default -> throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                        "不支持的内容类型: " + job.getContentType());
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeMetricsCsv(CSVWriter writer, ExportJob job) {
        writer.writeNext(new String[]{"ID", "指标类型", "数值", "单位", "记录日期", "趋势", "分类", "创建时间"});
        List<HealthMetric> list = queryMetrics(job);
        for (HealthMetric m : list) {
            writer.writeNext(new String[]{
                    String.valueOf(m.getId()),
                    m.getMetricKey(),
                    m.getValue() != null ? m.getValue().toPlainString() : "",
                    m.getUnit(),
                    m.getRecordDate() != null ? m.getRecordDate().format(DATE_FMT) : "",
                    m.getTrend(),
                    m.getCategory() != null ? m.getCategory().name() : "",
                    m.getCreateTime() != null ? m.getCreateTime().format(DATETIME_FMT) : ""
            });
        }
    }

    private void writeScoresCsv(CSVWriter writer, ExportJob job) {
        writer.writeNext(new String[]{
                "ID", "记录日期", "综合评分", "心血管评分", "代谢评分", "体重评分", "生活方式评分", "创建时间"
        });
        List<HealthScoreHistory> list = queryScores(job);
        for (HealthScoreHistory s : list) {
            writer.writeNext(new String[]{
                    String.valueOf(s.getId()),
                    s.getScoreDate() != null ? s.getScoreDate().format(DATE_FMT) : "",
                    s.getOverallScore() != null ? s.getOverallScore().toPlainString() : "",
                    s.getCardiovascularScore() != null ? s.getCardiovascularScore().toPlainString() : "",
                    s.getMetabolicScore() != null ? s.getMetabolicScore().toPlainString() : "",
                    s.getWeightScore() != null ? s.getWeightScore().toPlainString() : "",
                    s.getLifestyleScore() != null ? s.getLifestyleScore().toPlainString() : "",
                    s.getCreatedAt() != null ? s.getCreatedAt().format(DATETIME_FMT) : ""
            });
        }
    }

    private void writeScreeningsCsv(CSVWriter writer, ExportJob job) {
        writer.writeNext(new String[]{
                "报告ID", "报告名称", "报告类型", "机构", "报告日期",
                "指标名称", "类别", "数值", "单位", "参考范围", "是否异常", "创建时间"
        });
        List<ExaminationReport> reports = queryScreenings(job);
        for (ExaminationReport r : reports) {
            List<LabResult> results = labResultMapper.selectList(
                    new LambdaQueryWrapper<LabResult>()
                            .eq(LabResult::getReportId, r.getId())
                            .orderByAsc(LabResult::getSortOrder));
            if (results.isEmpty()) {
                writer.writeNext(new String[]{
                        String.valueOf(r.getId()),
                        r.getReportName(),
                        r.getReportType(),
                        r.getInstitution(),
                        r.getReportDate() != null ? r.getReportDate().format(DATE_FMT) : "",
                        "", "", "", "", "", "",
                        r.getCreateTime() != null ? r.getCreateTime().format(DATETIME_FMT) : ""
                });
            } else {
                for (LabResult lr : results) {
                    writer.writeNext(new String[]{
                            String.valueOf(r.getId()),
                            r.getReportName(),
                            r.getReportType(),
                            r.getInstitution(),
                            r.getReportDate() != null ? r.getReportDate().format(DATE_FMT) : "",
                            lr.getName(),
                            lr.getCategory(),
                            lr.getValue(),
                            lr.getUnit(),
                            lr.getReferenceRange(),
                            lr.getIsAbnormal() != null && lr.getIsAbnormal() == 1 ? "是" : "否",
                            r.getCreateTime() != null ? r.getCreateTime().format(DATETIME_FMT) : ""
                    });
                }
            }
        }
    }

    // ========================================================================
    // Excel generation (streaming)
    // ========================================================================

    private byte[] generateExcel(ExportJob job) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            switch (job.getContentType()) {
                case "metrics" -> writeMetricsExcel(wb, job);
                case "scores" -> writeScoresExcel(wb, job);
                case "screenings" -> writeScreeningsExcel(wb, job);
                default -> throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                        "不支持的内容类型: " + job.getContentType());
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeMetricsExcel(SXSSFWorkbook wb, ExportJob job) {
        Sheet sheet = wb.createSheet("健康指标");
        String[] headers = {"ID", "指标类型", "数值", "单位", "记录日期", "趋势", "分类", "创建时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        List<HealthMetric> list = queryMetrics(job);
        int rowIdx = 1;
        for (HealthMetric m : list) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(m.getId());
            row.createCell(1).setCellValue(m.getMetricKey());
            row.createCell(2).setCellValue(m.getValue() != null ? m.getValue().doubleValue() : 0);
            row.createCell(3).setCellValue(m.getUnit());
            row.createCell(4).setCellValue(
                    m.getRecordDate() != null ? m.getRecordDate().format(DATE_FMT) : "");
            row.createCell(5).setCellValue(m.getTrend());
            row.createCell(6).setCellValue(
                    m.getCategory() != null ? m.getCategory().name() : "");
            row.createCell(7).setCellValue(
                    m.getCreateTime() != null ? m.getCreateTime().format(DATETIME_FMT) : "");
        }
    }

    private void writeScoresExcel(SXSSFWorkbook wb, ExportJob job) {
        Sheet sheet = wb.createSheet("健康评分");
        String[] headers = {
                "ID", "记录日期", "综合评分", "心血管评分", "代谢评分",
                "体重评分", "生活方式评分", "创建时间"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        List<HealthScoreHistory> list = queryScores(job);
        int rowIdx = 1;
        for (HealthScoreHistory s : list) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(s.getId());
            row.createCell(1).setCellValue(
                    s.getScoreDate() != null ? s.getScoreDate().format(DATE_FMT) : "");
            setNumericCell(row, 2, s.getOverallScore());
            setNumericCell(row, 3, s.getCardiovascularScore());
            setNumericCell(row, 4, s.getMetabolicScore());
            setNumericCell(row, 5, s.getWeightScore());
            setNumericCell(row, 6, s.getLifestyleScore());
            row.createCell(7).setCellValue(
                    s.getCreatedAt() != null ? s.getCreatedAt().format(DATETIME_FMT) : "");
        }
    }

    private void writeScreeningsExcel(SXSSFWorkbook wb, ExportJob job) {
        Sheet sheet = wb.createSheet("筛查报告");
        String[] headers = {
                "报告ID", "报告名称", "报告类型", "机构", "报告日期",
                "指标名称", "类别", "数值", "单位", "参考范围", "是否异常", "创建时间"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        List<ExaminationReport> reports = queryScreenings(job);
        int rowIdx = 1;
        for (ExaminationReport r : reports) {
            List<LabResult> results = labResultMapper.selectList(
                    new LambdaQueryWrapper<LabResult>()
                            .eq(LabResult::getReportId, r.getId())
                            .orderByAsc(LabResult::getSortOrder));
            if (results.isEmpty()) {
                Row row = sheet.createRow(rowIdx++);
                writeReportCells(row, r);
                row.createCell(5).setCellValue("");
                row.createCell(6).setCellValue("");
                row.createCell(7).setCellValue("");
                row.createCell(8).setCellValue("");
                row.createCell(9).setCellValue("");
                row.createCell(10).setCellValue("");
            } else {
                for (LabResult lr : results) {
                    Row row = sheet.createRow(rowIdx++);
                    writeReportCells(row, r);
                    row.createCell(5).setCellValue(lr.getName());
                    row.createCell(6).setCellValue(lr.getCategory());
                    row.createCell(7).setCellValue(lr.getValue());
                    row.createCell(8).setCellValue(lr.getUnit());
                    row.createCell(9).setCellValue(lr.getReferenceRange());
                    row.createCell(10).setCellValue(
                            lr.getIsAbnormal() != null && lr.getIsAbnormal() == 1 ? "是" : "否");
                }
            }
        }
    }

    // ========================================================================
    // Data queries
    // ========================================================================

    private List<HealthMetric> queryMetrics(ExportJob job) {
        LambdaQueryWrapper<HealthMetric> qw = new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, job.getUserId());
        if (job.getStartDate() != null) {
            qw.ge(HealthMetric::getRecordDate, job.getStartDate());
        }
        if (job.getEndDate() != null) {
            qw.le(HealthMetric::getRecordDate, job.getEndDate());
        }
        qw.orderByAsc(HealthMetric::getRecordDate);
        return healthMetricMapper.selectList(qw);
    }

    private List<HealthScoreHistory> queryScores(ExportJob job) {
        LambdaQueryWrapper<HealthScoreHistory> qw = new LambdaQueryWrapper<HealthScoreHistory>()
                .eq(HealthScoreHistory::getUserId, job.getUserId());
        if (job.getStartDate() != null) {
            qw.ge(HealthScoreHistory::getScoreDate, job.getStartDate());
        }
        if (job.getEndDate() != null) {
            qw.le(HealthScoreHistory::getScoreDate, job.getEndDate());
        }
        qw.orderByAsc(HealthScoreHistory::getScoreDate);
        return healthScoreHistoryMapper.selectList(qw);
    }

    private List<ExaminationReport> queryScreenings(ExportJob job) {
        LambdaQueryWrapper<ExaminationReport> qw = new LambdaQueryWrapper<ExaminationReport>()
                .eq(ExaminationReport::getUserId, job.getUserId());
        if (job.getStartDate() != null) {
            qw.ge(ExaminationReport::getReportDate, job.getStartDate());
        }
        if (job.getEndDate() != null) {
            qw.le(ExaminationReport::getReportDate, job.getEndDate());
        }
        qw.orderByAsc(ExaminationReport::getCreateTime);
        return examinationReportMapper.selectList(qw);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String buildFileName(ExportJob job, String ext) {
        String prefix = switch (job.getContentType()) {
            case "metrics" -> "健康指标";
            case "scores" -> "健康评分";
            case "screenings" -> "筛查报告";
            default -> "导出数据";
        };
        return prefix + "_" + LocalDate.now().format(DATE_FMT) + "." + ext;
    }

    private void writeReportCells(Row row, ExaminationReport r) {
        row.createCell(0).setCellValue(r.getId());
        row.createCell(1).setCellValue(r.getReportName());
        row.createCell(2).setCellValue(r.getReportType());
        row.createCell(3).setCellValue(r.getInstitution());
        row.createCell(4).setCellValue(
                r.getReportDate() != null ? r.getReportDate().format(DATE_FMT) : "");
        row.createCell(11).setCellValue(
                r.getCreateTime() != null ? r.getCreateTime().format(DATETIME_FMT) : "");
    }

    private void setNumericCell(Row row, int col, java.math.BigDecimal value) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("");
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
