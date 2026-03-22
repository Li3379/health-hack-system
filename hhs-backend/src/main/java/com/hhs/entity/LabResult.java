package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_result")
public class LabResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private String name;
    private String category;
    private String value;
    private String unit;
    private String referenceRange;
    private Integer isAbnormal;
    private String trend;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
