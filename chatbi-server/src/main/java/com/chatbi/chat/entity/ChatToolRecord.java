package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_tool_record")
public class ChatToolRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long conversationId;

    private Long projectId;

    private Long userId;

    private String toolName;

    private String status;

    private String inputText;

    private String outputText;

    private Long durationMs;

    private LocalDateTime createdAt;
}
