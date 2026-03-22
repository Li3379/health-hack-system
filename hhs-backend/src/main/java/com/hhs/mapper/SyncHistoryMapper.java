package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.SyncHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备同步历史 Mapper
 */
@Mapper
public interface SyncHistoryMapper extends BaseMapper<SyncHistory> {
}