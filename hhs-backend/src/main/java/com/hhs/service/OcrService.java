package com.hhs.service;

import com.hhs.common.PageResult;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.vo.OcrHealthResultVO;
import com.hhs.vo.OcrHistoryVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OCR Service Interface
 * Provides OCR recognition for health-related images
 */
public interface OcrService {

    /**
     * Process OCR for an examination report (existing method)
     *
     * @param reportId Report ID to process
     * @throws Exception if file read fails or OCR processing fails
     */
    void processReport(Long reportId);

    /**
     * Recognize health metrics from an image
     * Supports multiple recognition types: report, medicine, nutrition
     *
     * @param userId User ID
     * @param file Image file to recognize
     * @param ocrType Recognition type: report, medicine, nutrition
     * @return OCR recognition result
     */
    OcrHealthResultVO recognizeHealthImage(Long userId, MultipartFile file, String ocrType);

    /**
     * Recognize medical examination report image
     *
     * @param userId User ID
     * @param file Report image file
     * @return OCR recognition result
     */
    OcrHealthResultVO recognizeReport(Long userId, MultipartFile file);

    /**
     * Recognize medicine label image
     *
     * @param userId User ID
     * @param file Medicine label image file
     * @return OCR recognition result
     */
    OcrHealthResultVO recognizeMedicine(Long userId, MultipartFile file);

    /**
     * Recognize nutrition label image
     *
     * @param userId User ID
     * @param file Nutrition label image file
     * @return OCR recognition result
     */
    OcrHealthResultVO recognizeNutrition(Long userId, MultipartFile file);

    /**
     * Confirm and save recognized metrics
     *
     * @param userId User ID
     * @param request Confirmation request with selected metrics
     * @return List of saved metric IDs
     */
    List<Long> confirmMetrics(Long userId, OcrConfirmRequest request);

    /**
     * Get OCR history for a user
     *
     * @param userId User ID
     * @param page Page number (1-based)
     * @param size Page size
     * @return Paginated OCR history records
     */
    PageResult<OcrHistoryVO> getHistory(Long userId, int page, int size);

    /**
     * Get OCR record details by ID
     *
     * @param userId User ID (for authorization)
     * @param recordId OCR record ID
     * @return OCR recognition result
     */
    OcrHealthResultVO getRecordDetail(Long userId, Long recordId);

    /**
     * Delete an OCR record
     *
     * @param userId User ID (for authorization)
     * @param recordId OCR record ID to delete
     * @return true if deleted successfully
     */
    boolean deleteRecord(Long userId, Long recordId);
}