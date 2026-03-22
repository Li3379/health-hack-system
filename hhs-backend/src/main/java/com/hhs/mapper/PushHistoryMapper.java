package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.PushHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Push history mapper
 */
@Mapper
public interface PushHistoryMapper extends BaseMapper<PushHistory> {

    /**
     * Count push attempts for a user on a specific channel since a given time
     */
    @Select("SELECT COUNT(*) FROM push_history WHERE user_id = #{userId} " +
            "AND channel_type = #{channelType} AND pushed_at >= #{since}")
    int countByUserAndChannelSince(@Param("userId") Long userId,
                                    @Param("channelType") String channelType,
                                    @Param("since") LocalDateTime since);

    /**
     * Count push attempts for a user since a given time (all channels)
     */
    @Select("SELECT COUNT(*) FROM push_history WHERE user_id = #{userId} AND pushed_at >= #{since}")
    int countByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Get recent push history for a user
     */
    @Select("SELECT * FROM push_history WHERE user_id = #{userId} " +
            "ORDER BY pushed_at DESC LIMIT #{limit}")
    List<PushHistory> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Get push statistics grouped by channel for a user
     */
    @Select("SELECT channel_type AS channelType, status, COUNT(*) AS count " +
            "FROM push_history WHERE user_id = #{userId} " +
            "AND pushed_at >= #{since} " +
            "GROUP BY channel_type, status")
    List<Map<String, Object>> getChannelStatsByUserSince(@Param("userId") Long userId,
                                                          @Param("since") LocalDateTime since);
}