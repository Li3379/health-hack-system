package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.DeleteLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 删除日志Mapper
 */
@Mapper
public interface DeleteLogMapper extends BaseMapper<DeleteLog> {
}
