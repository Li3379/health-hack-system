package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.OcrHealthRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * OCR健康图片识别记录Mapper
 */
@Mapper
public interface OcrHealthRecordMapper extends BaseMapper<OcrHealthRecord> {
}