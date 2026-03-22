package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.DeviceConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备连接配置 Mapper
 */
@Mapper
public interface DeviceConnectionMapper extends BaseMapper<DeviceConnection> {
}