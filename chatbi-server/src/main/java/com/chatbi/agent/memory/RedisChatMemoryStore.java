package com.chatbi.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * 基于 Redis 的 ChatMemoryStore 实现（LangChain4j）
 * <p>
 * 使用 LangChain4j 内置的序列化器，自动保存所有消息类型（包括工具调用）。
 * <p>
 * 数据结构：
 * - chatbi:memory:{memoryId} → Redis String (JSON 数组)
 * - chatbi:memory:ids → Redis Set (所有 memoryId)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "chatbi:memory:";
    private static final String IDS_KEY = "chatbi:memory:ids";

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
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
        String key = KEY_PREFIX + memoryId;
        try {
            String json = messagesToJson(messages);
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.opsForSet().add(IDS_KEY, String.valueOf(memoryId));
        } catch (Exception e) {
            log.error("消息序列化失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(KEY_PREFIX + memoryId);
        redisTemplate.opsForSet().remove(IDS_KEY, String.valueOf(memoryId));
    }
}
