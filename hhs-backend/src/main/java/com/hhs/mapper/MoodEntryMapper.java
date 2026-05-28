package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.MoodEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for mood_entries table
 */
@Mapper
public interface MoodEntryMapper extends BaseMapper<MoodEntry> {
}
