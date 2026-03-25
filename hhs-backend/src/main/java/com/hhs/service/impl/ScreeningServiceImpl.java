package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.domain.event.OcrProcessingEvent;
import com.hhs.entity.ExaminationReport;
import com.hhs.entity.LabResult;
import com.hhs.exception.BusinessException;
import com.hhs.common.constant.ErrorCode;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.mapper.LabResultMapper;
import com.hhs.security.PathValidationUtil;
import com.hhs.service.OcrService;
import com.hhs.service.ReportStatusService;
import com.hhs.service.ScreeningService;
import com.hhs.vo.ExaminationReportVO;
import com.hhs.vo.LabResultVO;
import com.hhs.vo.OcrStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
public class ScreeningServiceImpl implements ScreeningService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".jpg", ".jpeg", ".png");

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private final ExaminationReportMapper examinationReportMapper;
    private final LabResultMapper labResultMapper;
    private final OcrService ocrService;
    private final ReportStatusService reportStatusService;
    private final ApplicationEventPublisher eventPublisher;

    public ScreeningServiceImpl(ExaminationReportMapper examinationReportMapper, 
                                LabResultMapper labResultMapper, 
                                OcrService ocrService, 
                                ReportStatusService reportStatusService,
                                ApplicationEventPublisher eventPublisher) {
        this.examinationReportMapper = examinationReportMapper;
        this.labResultMapper = labResultMapper;
        this.ocrService = ocrService;
        this.reportStatusService = reportStatusService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Upload a new examination report
     *
     * @param userId User ID
     * @param file Report file (PDF, JPG, PNG)
     * @param reportName Optional report name
     * @param reportType Optional report type
     * @param institution Optional institution name
     * @param reportDateStr Optional report date (yyyy-MM-dd format)
     * @return ExaminationReportVO containing the uploaded report
     * @throws BusinessException if file validation fails
     */
    @Override
    @Transactional
    public ExaminationReportVO uploadReport(Long userId, MultipartFile file, String reportName, String reportType, String institution, String reportDateStr) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();

        // Validate filename is safe before processing
        if (!PathValidationUtil.isFilenameSafe(originalFilename)) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "Invalid file path: " + originalFilename);
        }

        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "仅支持 PDF、JPG、PNG 格式");
        }

        // Generate safe UUID-based filename for disk storage
        String safeFilename = PathValidationUtil.generateSafeFilename(originalFilename);

        // Use PathValidationUtil to securely resolve the path
        Path uploadDirPath = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("reports");
        Path target;
        try {
            Files.createDirectories(uploadDirPath);
            target = PathValidationUtil.validateAndResolvePath(uploadDirPath.toString(), safeFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save file for upload: {}", originalFilename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败: " + e.getMessage(), e);
        }

        String fileUrl = "/uploads/reports/" + safeFilename;
        ExaminationReport report = new ExaminationReport();
        report.setUserId(userId);
        report.setReportName(reportName != null && !reportName.isBlank() ? reportName : originalFilename);
        report.setReportType(reportType);
        report.setInstitution(institution);
        if (reportDateStr != null && !reportDateStr.isBlank()) {
            try {
                report.setReportDate(LocalDate.parse(reportDateStr));
            } catch (DateTimeParseException e) {
                log.warn("Invalid report date format for report ID {}: {}", report.getId(), reportDateStr);
                throw new BusinessException(ErrorCode.RESOURCE_FILE_PARSE_ERROR, "报告日期格式错误: " + reportDateStr, e);
            }
        }
        report.setFileUrl(fileUrl);
        report.setOriginalFilename(originalFilename);
        report.setStoredFilename(safeFilename);
        report.setOcrStatus("PENDING");
        examinationReportMapper.insert(report);

        // Auto-trigger OCR processing after transaction commits
        // Using @TransactionalEventListener ensures the async task sees committed data
        final Long reportId = report.getId();
        try {
            // Update status to PROCESSING (in current transaction)
            reportStatusService.updateOcrStatus(reportId, "PROCESSING", null);

            // Publish event that will trigger OCR AFTER transaction commits
            // This fixes the race condition where async tasks couldn't see uncommitted data
            eventPublisher.publishEvent(new OcrProcessingEvent(reportId));

            log.info("Scheduled OCR processing for report {} (will run after transaction commit)", reportId);
        } catch (Exception e) {
            // If event publishing fails, update status to FAILED
            log.error("Failed to schedule OCR for report {}", reportId, e);
            reportStatusService.updateOcrStatus(reportId, "FAILED", "调度OCR处理失败: " + e.getMessage());
        }

        return toReportVO(report); // Returns immediately without waiting for OCR
    }

    /**
     * List all examination reports for a user
     *
     * @param userId User ID
     * @return List of ExaminationReportVO sorted by creation time (descending)
     */
    @Override
    public List<ExaminationReportVO> listReports(Long userId) {
        List<ExaminationReport> list = examinationReportMapper.selectList(
                new LambdaQueryWrapper<ExaminationReport>().eq(ExaminationReport::getUserId, userId).orderByDesc(ExaminationReport::getCreateTime));
        return list.stream().map(this::toReportVO).toList();
    }

    /**
     * Get a specific examination report
     *
     * @param id Report ID
     * @param userId User ID (for authorization)
     * @return ExaminationReportVO
     * @throws BusinessException if report not found or user not authorized
     */
    @Override
    public ExaminationReportVO getReport(Long id, Long userId) {
        ExaminationReport r = examinationReportMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        return toReportVO(r);
    }

    /**
     * Delete an examination report and its associated lab results
     *
     * @param id Report ID
     * @param userId User ID (for authorization)
     * @throws BusinessException if report not found or user not authorized
     */
    @Override
    @Transactional
    public void deleteReport(Long id, Long userId) {
        ExaminationReport r = examinationReportMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        labResultMapper.delete(new LambdaQueryWrapper<LabResult>().eq(LabResult::getReportId, id));
        examinationReportMapper.deleteById(id);
    }

    /**
     * Manually trigger OCR processing for a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @throws BusinessException if report not found or user not authorized
     */
    @Override
    @Transactional
    public void triggerOcr(Long reportId, Long userId) {
        ExaminationReport r = examinationReportMapper.selectById(reportId);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        r.setOcrStatus("PROCESSING");
        examinationReportMapper.updateById(r);
        ocrService.processReport(reportId);
    }

    /**
     * Get OCR processing status for a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @return OcrStatusVO containing status and optional error message
     * @throws BusinessException if report not found or user not authorized
     */
    @Override
    public OcrStatusVO getOcrStatus(Long reportId, Long userId) {
        ExaminationReport r = examinationReportMapper.selectById(reportId);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        // Return status with error message if available
        String errorMessage = null;
        if ("FAILED".equals(r.getOcrStatus()) && r.getAbnormalSummary() != null && r.getAbnormalSummary().startsWith("识别失败")) {
            errorMessage = r.getAbnormalSummary();
        }
        return new OcrStatusVO(r.getOcrStatus(), errorMessage);
    }

    /**
     * Get lab results extracted from a report
     *
     * @param reportId Report ID
     * @param userId User ID (for authorization)
     * @return List of LabResultVO sorted by sort order
     * @throws BusinessException if report not found or user not authorized
     */
    @Override
    public List<LabResultVO> getLabResults(Long reportId, Long userId) {
        ExaminationReport r = examinationReportMapper.selectById(reportId);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        List<LabResult> list = labResultMapper.selectList(
                new LambdaQueryWrapper<LabResult>().eq(LabResult::getReportId, reportId).orderByAsc(LabResult::getSortOrder));
        return list.stream().map(this::toLabResultVO).toList();
    }

    private ExaminationReportVO toReportVO(ExaminationReport e) {
        return new ExaminationReportVO(e.getId(), e.getUserId(), e.getReportName(), e.getReportType(), e.getInstitution(),
                e.getReportDate(), e.getFileUrl(), e.getOcrStatus(), e.getAbnormalSummary(), e.getCreateTime(), e.getUpdateTime());
    }

    private LabResultVO toLabResultVO(LabResult e) {
        return new LabResultVO(e.getId(), e.getReportId(), e.getName(), e.getCategory(), e.getValue(), e.getUnit(),
                e.getReferenceRange(), e.getIsAbnormal() != null && e.getIsAbnormal() == 1, e.getTrend(), e.getSortOrder(), e.getCreateTime());
    }
}