package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data export job entity.
 * Tracks async export tasks for metrics, scores, and screenings.
 */
@Data
@TableName("export_jobs")
public class ExportJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String exportType;
    private String contentType;
    private String status;
    private String filePath;
    private String fileName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
