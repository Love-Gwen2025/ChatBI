package com.chatbi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.chat.entity.ChatMessageRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageRecordMapper extends BaseMapper<ChatMessageRecord> {
}
