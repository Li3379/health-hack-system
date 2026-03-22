package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.UserPushConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * User push config mapper
 */
@Mapper
public interface UserPushConfigMapper extends BaseMapper<UserPushConfig> {

    @Select("SELECT * FROM user_push_config WHERE user_id = #{userId} AND enabled = 1")
    List<UserPushConfig> findEnabledByUserId(Long userId);

    @Select("SELECT * FROM user_push_config WHERE user_id = #{userId} AND channel_type = #{channelType}")
    UserPushConfig findByUserIdAndChannelType(Long userId, String channelType);
}