package com.chatbi.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.chat.mapper.ConversationMapper;
import com.chatbi.chat.model.Conversation;
import com.chatbi.common.PageResponse;
import com.chatbi.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final RedisChatMemoryStore chatMemoryStore;

    public Conversation verifyConversationAccess(String conversationId) {
        Conversation conv = conversationMapper.selectById(Long.parseLong(conversationId));
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        Long userId = UserContext.getUserId();
        if (userId == null || conv.getUserId() == null || !userId.equals(conv.getUserId())) {
            throw new SecurityException("无权访问该会话");
        }
        Long projectId = UserContext.getProjectId();
        if (projectId != null && !projectId.equals(conv.getProjectId())) {
            throw new SecurityException("无权访问该会话");
        }
        return conv;
    }

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
        verifyConversationAccess(String.valueOf(id));
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

    public void deleteByProjectId(Long projectId) {
        conversationMapper.delete(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getProjectId, projectId));
    }
}
