package com.chatbi.common.security;

import com.chatbi.common.constants.CacheKeyConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;


    public void saveSession(SessionInfo session) {
        try {
            String key = CacheKeyConstants.SESSION_PREFIX + session.getUserId();
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, CacheKeyConstants.SESSION_TTL);
            log.debug("保存 session: userId={}", session.getUserId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 SessionInfo 失败", e);
        }
    }

    public SessionInfo getSession(Long userId) {
        String key = CacheKeyConstants.SESSION_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SessionInfo.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化 SessionInfo 失败: userId={}", userId, e);
            return null;
        }
    }

    public void deleteSession(Long userId) {
        String key = CacheKeyConstants.SESSION_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("删除 session: userId={}", userId);
    }

    public void updateProjectInSession(Long userId, Long projectId, String projectName, String projectRole) {
        SessionInfo session = getSession(userId);
        if (session == null) {
            log.warn("更新项目失败：session 不存在, userId={}", userId);
            return;
        }
        session.setLastLoginProjectId(projectId);
        session.setLastLoginProjectName(projectName);
        session.setLastLoginProjectRole(projectRole);
        saveSession(session);
    }
}
