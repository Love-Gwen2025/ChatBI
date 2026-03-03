package com.chatbi.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.agent.service.DataAnalysisAssistant;
import com.chatbi.agent.service.StreamingDataAnalysisAssistant;
import com.chatbi.chat.mapper.ConversationMapper;
import com.chatbi.chat.model.Conversation;
import com.chatbi.common.PageResponse;
import com.chatbi.common.security.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final ConversationMapper conversationMapper;

    /**
     * 校验会话属于当前项目，返回会话实体；不属于则抛异常
     */
    private Conversation verifyConversationAccess(String conversationId) {
        Conversation conv = conversationMapper.selectById(Long.parseLong(conversationId));
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        Long projectId = UserContext.getProjectId();
        if (projectId != null && !projectId.equals(conv.getProjectId())) {
            throw new SecurityException("无权访问该会话");
        }
        return conv;
    }

    public String chat(String conversationId, String message) {
        verifyConversationAccess(conversationId);
        log.info("会话[{}] 用户消息: {}", conversationId, message);
        InvocationParameters params = buildInvocationParameters();
        String response = assistant.chat(conversationId, message, params);
        log.info("会话[{}] AI 回复长度: {}", conversationId, response != null ? response.length() : 0);
        return response;
    }

    public TokenStream chatStream(String conversationId, String message) {
        verifyConversationAccess(conversationId);
        log.info("会话[{}] 用户消息(stream): {}", conversationId, message);
        InvocationParameters params = buildInvocationParameters();
        return streamingAssistant.chat(conversationId, message, params);
    }

    private InvocationParameters buildInvocationParameters() {
        Long projectId = UserContext.getProjectId();
        return InvocationParameters.from(Map.of("projectId", projectId != null ? projectId : 0L));
    }

    public List<Map<String, Object>> getMessages(String conversationId) {
        verifyConversationAccess(conversationId);

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
                        tc.put("output", truncate(term.text(), 150));
                        break;
                    }
                }
            }
        }

        return result;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ---- 会话管理 ----

    public Conversation createConversation(String title) {
        Conversation conv = new Conversation();
        conv.setTitle(title != null ? title : "新对话");
        conv.setProjectId(UserContext.getProjectId());
        conv.setUserId(UserContext.getUserId());
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    public PageResponse<Conversation> listConversations(int page, int size) {
        Long projectId = UserContext.getProjectId();
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(Conversation::getProjectId, projectId);
        } else {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                wrapper.eq(Conversation::getUserId, userId);
            } else {
                return PageResponse.of(List.of(), 0, page, size);
            }
        }
        wrapper.orderByDesc(Conversation::getUpdatedAt);
        IPage<Conversation> result = conversationMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result);
    }

    public void deleteConversation(Long id) {
        Conversation conv = verifyConversationAccess(String.valueOf(id));
        // 清理 Redis 中的对话记忆
        chatMemoryStore.deleteMessages(String.valueOf(id));
        conversationMapper.deleteById(id);
        log.info("删除会话[{}]及其 Redis 记忆", id);
    }

    public void renameConversation(Long id, String title) {
        Conversation conv = verifyConversationAccess(String.valueOf(id));
        conv.setTitle(title);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }
}
