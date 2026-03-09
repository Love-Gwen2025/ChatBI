package com.chatbi.chat.service;

import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.agent.service.DataAnalysisAssistant;
import com.chatbi.agent.service.StreamingDataAnalysisAssistant;
import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.chatbi.common.security.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final DataAnalysisAssistant assistant;
    private final StreamingDataAnalysisAssistant streamingAssistant;
    private final RedisChatMemoryStore chatMemoryStore;
    private final ConversationService conversationService;

    public String chat(String conversationId, String message) {
        conversationService.verifyConversationAccess(conversationId);
        log.info("会话[{}] 用户消息: {}", conversationId, message);
        String response = assistant.chat(conversationId, message, buildInvocationParameters(conversationId, "sync"));
        log.info("会话[{}] AI 回复长度: {}", conversationId, response != null ? response.length() : 0);
        return response;
    }

    public TokenStream chatStream(String conversationId, String message) {
        conversationService.verifyConversationAccess(conversationId);
        log.info("会话[{}] 用户消息(stream): {}", conversationId, message);
        return streamingAssistant.chat(conversationId, message, buildInvocationParameters(conversationId, "stream"));
    }

    public List<Map<String, Object>> getMessages(String conversationId) {
        conversationService.verifyConversationAccess(conversationId);

        List<ChatMessage> messages = chatMemoryStore.getMessages(conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, String>> pendingToolCalls = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage um) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("role", "user");
                map.put("content", um.singleText());
                result.add(map);
            } else if (msg instanceof AiMessage am) {
                if (am.hasToolExecutionRequests()) {
                    for (var req : am.toolExecutionRequests()) {
                        Map<String, String> tcInfo = new LinkedHashMap<>();
                        tcInfo.put("id", req.id());
                        tcInfo.put("name", req.name());
                        tcInfo.put("input", req.arguments());
                        tcInfo.put("status", "done");
                        pendingToolCalls.add(tcInfo);
                    }
                } else {
                    String text = am.text();
                    if (text != null && !text.isBlank()) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("role", "assistant");
                        map.put("content", text);
                        if (!pendingToolCalls.isEmpty()) {
                            map.put("toolCalls", new ArrayList<>(pendingToolCalls));
                            pendingToolCalls.clear();
                        }
                        result.add(map);
                    }
                }
            } else if (msg instanceof ToolExecutionResultMessage term) {
                for (Map<String, String> tc : pendingToolCalls) {
                    if (term.id().equals(tc.get("id"))) {
                        tc.put("output", StringUtils.truncate(term.text(), AppConstants.TRUNCATE_SHORT));
                        break;
                    }
                }
            }
        }

        return result;
    }

    private InvocationParameters buildInvocationParameters(String conversationId, String chatMode) {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put(ChatTraceKeys.PARAM_CONVERSATION_ID, conversationId);
        parameters.put(ChatTraceKeys.PARAM_CHAT_MODE, chatMode);

        Long projectId = UserContext.getProjectId();
        if (projectId != null) {
            parameters.put(ChatTraceKeys.PARAM_PROJECT_ID, projectId);
        }

        Long userId = UserContext.getUserId();
        if (userId != null) {
            parameters.put(ChatTraceKeys.PARAM_USER_ID, userId);
        }

        ChatTraceContext.TraceState traceState = ChatTraceContext.current();
        if (traceState != null) {
            parameters.put(ChatTraceKeys.PARAM_TRACE_ID, traceState.getTraceId());
            parameters.put(ChatTraceKeys.PARAM_PARENT_SPAN_ID, traceState.currentParentSpanId());
            return parameters;
        }

        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            parameters.put(ChatTraceKeys.PARAM_TRACE_ID, spanContext.getTraceId());
            parameters.put(ChatTraceKeys.PARAM_PARENT_SPAN_ID, spanContext.getSpanId());
        }
        return parameters;
    }
}
