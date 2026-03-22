package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.HealthMetric;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthMetricMapper extends BaseMapper<HealthMetric> {
}
