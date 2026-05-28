package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.ExportJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for export_jobs table.
 */
@Mapper
public interface ExportJobMapper extends BaseMapper<ExportJob> {
}
