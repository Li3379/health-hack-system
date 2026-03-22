package com.hhs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hhs.entity.AIConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话历史Mapper
 */
@Mapper
public interface AIConversationMapper extends BaseMapper<AIConversation> {
}
