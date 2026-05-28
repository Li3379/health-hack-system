package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.UserPoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分记录 Mapper
 */
@Mapper
public interface UserPointMapper extends BaseMapper<UserPoint> {
}
