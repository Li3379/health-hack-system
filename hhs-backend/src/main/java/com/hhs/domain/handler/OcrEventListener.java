package com.hhs.domain.handler;

import com.hhs.domain.event.OcrProcessingEvent;
import com.hhs.service.OcrService;
import com.hhs.service.ReportStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener for OCR processing.
 * 
 * Uses @TransactionalEventListener to ensure OCR processing happens
 * AFTER the database transaction commits. This prevents the race condition
 * where async tasks started inside a transaction cannot see uncommitted data.
 * 
 * The listener is triggered AFTER_COMMIT which guarantees:
 * 1. The report record is fully committed and visible to other transactions
 * 2. The file has been saved to disk
 * 3. The OCR status has been set to PROCESSING
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrEventListener {

    private final OcrService ocrService;
    private final ReportStatusService reportStatusService;

    /**
     * Process OCR asynchronously after the transaction commits.
     * This ensures the report exists in the database when OCR tries to read it.
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOcrProcessingRequested(OcrProcessingEvent event) {
        Long reportId = event.getReportId();
        log.info("Processing OCR for report {} (triggered after transaction commit)", reportId);
        
        try {
            ocrService.processReport(reportId);
            log.info("OCR processing completed for report {}", reportId);
        } catch (Exception e) {
            log.error("OCR processing failed for report {}: {}", reportId, e.getMessage(), e);
            // Update status to FAILED - this runs in a new transaction
            reportStatusService.updateOcrStatus(reportId, "FAILED", "OCR处理失败: " + e.getMessage());
        }
    }
}