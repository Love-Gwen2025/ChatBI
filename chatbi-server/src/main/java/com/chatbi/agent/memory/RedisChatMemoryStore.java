package com.chatbi.agent.memory;

import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.chatbi.common.constants.CacheKeyConstants;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * 基于 Redis 的 ChatMemoryStore 实现（LangChain4j）
 *
 * 数据结构：
 * - chatbi:memory:{memoryId} -> Redis String (JSON 数组)
 * - chatbi:memory:summary:{memoryId} -> Redis String (摘要文本)
 * - chatbi:memory:ids -> Redis Set (所有 memoryId)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String SUMMARY_PREFIX = "历史摘要:\n";

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = CacheKeyConstants.MEMORY_PREFIX + memoryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return messagesFromJson(json);
        } catch (Exception e) {
            log.error("消息反序列化失败, memoryId={}", memoryId, e);
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = CacheKeyConstants.MEMORY_PREFIX + memoryId;
        try {
            List<ChatMessage> compressedMessages = compressMessages(memoryId, messages);
            String json = messagesToJson(compressedMessages);
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.opsForSet().add(CacheKeyConstants.MEMORY_IDS_KEY, String.valueOf(memoryId));
        } catch (Exception e) {
            log.error("消息序列化失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(CacheKeyConstants.MEMORY_PREFIX + memoryId);
        redisTemplate.delete(CacheKeyConstants.MEMORY_SUMMARY_PREFIX + memoryId);
        redisTemplate.opsForSet().remove(CacheKeyConstants.MEMORY_IDS_KEY, String.valueOf(memoryId));
    }

    private List<ChatMessage> compressMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages.size() <= AppConstants.MEMORY_SUMMARY_TRIGGER_MESSAGES) {
            return messages;
        }

        int preserveRecent = Math.min(AppConstants.MEMORY_SUMMARY_RECENT_MESSAGES, messages.size());
        int cutoffIndex = messages.size() - preserveRecent;
        List<ChatMessage> olderMessages = new ArrayList<>(messages.subList(0, cutoffIndex));
        List<ChatMessage> recentMessages = new ArrayList<>(messages.subList(cutoffIndex, messages.size()));

        String existingSummary = redisTemplate.opsForValue().get(CacheKeyConstants.MEMORY_SUMMARY_PREFIX + memoryId);
        if (!olderMessages.isEmpty() && olderMessages.get(0) instanceof SystemMessage systemMessage
                && systemMessage.text().startsWith(SUMMARY_PREFIX)) {
            olderMessages.remove(0);
            existingSummary = systemMessage.text().substring(SUMMARY_PREFIX.length()).trim();
        }

        String mergedSummary = buildSummary(existingSummary, olderMessages);
        redisTemplate.opsForValue().set(CacheKeyConstants.MEMORY_SUMMARY_PREFIX + memoryId, mergedSummary);

        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(SystemMessage.from(SUMMARY_PREFIX + mergedSummary));
        compressed.addAll(recentMessages);
        return compressed;
    }

    private String buildSummary(String existingSummary, List<ChatMessage> olderMessages) {
        List<String> lines = new ArrayList<>();
        if (existingSummary != null && !existingSummary.isBlank()) {
            lines.add(existingSummary.trim());
        }
        for (ChatMessage message : olderMessages) {
            if (message instanceof UserMessage userMessage) {
                lines.add("- 用户问题: " + StringUtils.truncate(userMessage.singleText(), AppConstants.TRUNCATE_LONG));
            } else if (message instanceof AiMessage aiMessage) {
                if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                    lines.add("- 助手结论: " + StringUtils.truncate(aiMessage.text(), AppConstants.TRUNCATE_LONG));
                }
            } else if (message instanceof ToolExecutionResultMessage toolMessage) {
                lines.add("- 工具结果: " + StringUtils.truncate(toolMessage.text(), AppConstants.TRUNCATE_SHORT));
            }
        }
        if (lines.isEmpty()) {
            return "暂无可压缩的历史上下文";
        }
        int fromIndex = Math.max(0, lines.size() - 12);
        return String.join("\n", lines.subList(fromIndex, lines.size()));
    }
}
