package com.chatbi.chat.service;

import com.chatbi.chat.entity.ChatMessageRecord;
import com.chatbi.chat.entity.ChatToolRecord;
import com.chatbi.chat.mapper.ChatMessageRecordMapper;
import com.chatbi.chat.mapper.ChatToolRecordMapper;
import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAuditService {

    private final ChatMessageRecordMapper chatMessageRecordMapper;
    private final ChatToolRecordMapper chatToolRecordMapper;
    private final ObjectMapper objectMapper;

    public void recordUserMessage(String conversationId, String content) {
        recordMessage(conversationId, "user", content, null);
    }

    public void recordAssistantMessage(String conversationId, String content, Map<String, Object> metadata) {
        recordMessage(conversationId, "assistant", content, metadata);
    }

    public void recordAssistantError(String conversationId, String errorMessage) {
        recordMessage(conversationId, "assistant_error", errorMessage, null);
    }

    public void recordToolCall(String toolName, String status, String inputText, String outputText, long durationMs) {
        ChatTraceContext.TraceState traceState = ChatTraceContext.current();
        ChatToolRecord record = new ChatToolRecord();
        record.setTraceId(traceState != null ? traceState.getTraceId() : null);
        record.setConversationId(parseConversationId(traceState != null ? traceState.getConversationId() : null));
        record.setProjectId(traceState != null ? traceState.getProjectId() : null);
        record.setUserId(traceState != null ? traceState.getUserId() : null);
        record.setToolName(toolName);
        record.setStatus(status);
        record.setInputText(StringUtils.truncate(inputText, AppConstants.TRUNCATE_LONG * 4));
        record.setOutputText(StringUtils.truncate(outputText, AppConstants.TRUNCATE_LONG * 6));
        record.setDurationMs(durationMs);
        record.setCreatedAt(LocalDateTime.now());
        chatToolRecordMapper.insert(record);
    }

    private void recordMessage(String conversationId, String role, String content, Map<String, Object> metadata) {
        ChatTraceContext.TraceState traceState = ChatTraceContext.current();
        ChatMessageRecord record = new ChatMessageRecord();
        record.setTraceId(traceState != null ? traceState.getTraceId() : null);
        record.setConversationId(parseConversationId(conversationId != null ? conversationId : traceState != null ? traceState.getConversationId() : null));
        record.setProjectId(traceState != null ? traceState.getProjectId() : null);
        record.setUserId(traceState != null ? traceState.getUserId() : null);
        record.setRole(role);
        record.setContent(content);
        record.setMetadataJson(toJson(metadata));
        record.setCreatedAt(LocalDateTime.now());
        chatMessageRecordMapper.insert(record);
    }

    private Long parseConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            log.debug("忽略非法 conversationId: {}", conversationId);
            return null;
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("序列化聊天审计元数据失败", e);
            return null;
        }
    }
}
