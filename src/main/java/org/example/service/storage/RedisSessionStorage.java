package org.example.service.storage;

import org.example.config.SessionStorageProperties;
import org.example.dto.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis会话存储实现
 * 适用于分布式环境，支持会话持久化
 */
@Service
@ConditionalOnProperty(name = "session.storage.type", havingValue = "redis")
public class RedisSessionStorage implements SessionStorageService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionStorage.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionStorageProperties properties;

    @Autowired
    public RedisSessionStorage(RedisTemplate<String, Object> redisTemplate,
                               SessionStorageProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        logger.info("RedisSessionStorage initialized, expireSeconds={}, keyPrefix={}",
            properties.getExpireSeconds(), properties.getRedisKeyPrefix());
    }

    /**
     * 构建Redis key
     */
    private String buildKey(String sessionId) {
        return properties.getRedisKeyPrefix() + sessionId;
    }

    @Override
    public SessionData getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        String key = buildKey(sessionId);

        @SuppressWarnings("unchecked")
        SessionData data = (SessionData) redisTemplate.opsForValue().get(key);

        if (data == null) {
            data = new SessionData(sessionId);
            saveSession(data);
            logger.debug("Created new session in Redis: {}", sessionId);
        } else {
            trimHistory(data);
            refreshTtl(sessionId);
        }

        return data;
    }

    @Override
    public void saveSession(SessionData sessionData) {
        if (sessionData == null || sessionData.getSessionId() == null) {
            return;
        }

        trimHistory(sessionData);
        String key = buildKey(sessionData.getSessionId());
        redisTemplate.opsForValue().set(
            key,
            sessionData,
            properties.getExpireSeconds(),
            TimeUnit.SECONDS
        );
        logger.debug("Session saved to Redis: {}", sessionData.getSessionId());
    }

    @Override
    public Optional<SessionData> getSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        String key = buildKey(sessionId);
        @SuppressWarnings("unchecked")
        SessionData data = (SessionData) redisTemplate.opsForValue().get(key);

        if (data != null) {
            refreshTtl(sessionId);
        }

        return Optional.ofNullable(data);
    }

    @Override
    public void deleteSession(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.delete(key);
        logger.debug("Session deleted from Redis: {}", sessionId);
    }

    @Override
    public void clearHistory(String sessionId) {
        getSession(sessionId).ifPresent(data -> {
            data.clearHistory();
            saveSession(data);
            logger.info("Session history cleared in Redis: {}", sessionId);
        });
    }

    @Override
    public boolean exists(String sessionId) {
        String key = buildKey(sessionId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void refreshTtl(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.expire(key, properties.getExpireSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public String getStorageType() {
        return "redis";
    }

    /**
     * 裁剪历史消息，保持最大窗口大小
     * 注意：当使用混合记忆策略时，压缩逻辑由 ConversationMemoryService 处理，
     * 此方法仅作为兜底保护，不应干扰正常的压缩流程
     */
    private void trimHistory(SessionData data) {
        // 如果开启了摘要压缩功能，不再裁剪 messageHistory
        // 因为 ConversationMemoryService 会自动管理 messageHistory 的大小
        if (data.getSummaries() != null && !data.getSummaries().isEmpty()) {
            // 已有摘要，说明使用了混合记忆策略，跳过裁剪
            return;
        }
        while (data.getMessageHistory().size() > properties.getMaxMessagePairs()) {
            data.getMessageHistory().remove(0);
        }
    }
}
