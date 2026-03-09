package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message_record")
public class ChatMessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long conversationId;

    private Long projectId;

    private Long userId;

    private String role;

    private String content;

    private String metadataJson;

    private LocalDateTime createdAt;
}
