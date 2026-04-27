package org.example.service.storage;

import org.example.config.SessionStorageProperties;
import org.example.dto.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 双存储会话实现
 * 同时使用本地内存和Redis存储，会话数据双写，各自有独立的过期时间
 * - 本地存储：快速访问，默认4小时
 * - Redis存储：持久化，默认3天
 */
@Service
@ConditionalOnProperty(name = "session.storage.type", havingValue = "dual")
public class DualSessionStorage implements SessionStorageService {

    private static final Logger logger = LoggerFactory.getLogger(DualSessionStorage.class);

    private final LocalSessionStorage localStorage;
    private final RedisSessionStorage redisStorage;
    private final SessionStorageProperties properties;

    public DualSessionStorage(LocalSessionStorage localStorage,
                               RedisSessionStorage redisStorage,
                               SessionStorageProperties properties) {
        this.localStorage = localStorage;
        this.redisStorage = redisStorage;
        this.properties = properties;
        logger.info("DualSessionStorage initialized, localExpire={}s, redisExpire={}s",
                properties.getLocalExpireSeconds(), properties.getRedisExpireSeconds());
    }

    @Override
    public SessionData getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 1. 优先从本地读取
        SessionData localData = localStorage.getSession(sessionId).orElse(null);

        if (localData != null) {
            // 本地命中 - 刷新双方 TTL
            refreshTtl(sessionId);
            trimHistory(localData);
            logger.debug("Session {} found in local cache", sessionId);
            return localData;
        }

        // 2. 本地未命中 - 尝试从 Redis 读取
        Optional<SessionData> redisData = redisStorage.getSession(sessionId);

        if (redisData.isPresent()) {
            // Redis 命中 - 回填本地
            SessionData data = redisData.get();
            saveToLocal(data);
            trimHistory(data);
            refreshTtl(sessionId);
            logger.debug("Session {} loaded from Redis and cached locally", sessionId);
            return data;
        }

        // 3. 双方都没有 - 创建新会话，双写
        SessionData newData = new SessionData(sessionId);
        saveSession(newData);
        logger.info("Created new session in dual storage: {}", sessionId);
        return newData;
    }

    @Override
    public void saveSession(SessionData sessionData) {
        if (sessionData == null || sessionData.getSessionId() == null) {
            return;
        }

        trimHistory(sessionData);

        // 同时写入本地和 Redis（各自使用自己的 TTL）
        saveToLocal(sessionData);
        saveToRedis(sessionData);
    }

    @Override
    public Optional<SessionData> getSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        // 1. 优先本地
        Optional<SessionData> localData = localStorage.getSession(sessionId);
        if (localData.isPresent()) {
            // 异步刷新 Redis TTL
            refreshTtlAsync(sessionId);
            return localData;
        }

        // 2. 本地没有则查 Redis
        Optional<SessionData> redisData = redisStorage.getSession(sessionId);
        if (redisData.isPresent()) {
            // 回填本地
            SessionData data = redisData.get();
            try {
                saveToLocal(data);
            } catch (Exception e) {
                logger.warn("Failed to backfill session to local: {}", sessionId, e);
            }
        }

        return redisData;
    }

    @Override
    public void deleteSession(String sessionId) {
        localStorage.deleteSession(sessionId);
        redisStorage.deleteSession(sessionId);
        logger.debug("Session {} deleted from both storages", sessionId);
    }

    @Override
    public void clearHistory(String sessionId) {
        localStorage.clearHistory(sessionId);
        redisStorage.clearHistory(sessionId);
        logger.info("Session history cleared in both storages: {}", sessionId);
    }

    @Override
    public boolean exists(String sessionId) {
        return localStorage.exists(sessionId) || redisStorage.exists(sessionId);
    }

    @Override
    public void refreshTtl(String sessionId) {
        localStorage.refreshTtl(sessionId);
        redisStorage.refreshTtl(sessionId);
    }

    @Override
    public String getStorageType() {
        return "dual";
    }

    /**
     * 保存到本地存储
     */
    private void saveToLocal(SessionData sessionData) {
        try {
            localStorage.saveSession(sessionData);
        } catch (Exception e) {
            logger.warn("Failed to save session to local storage: {}", sessionData.getSessionId(), e);
        }
    }

    /**
     * 保存到 Redis（使用 Redis 过期时间）
     */
    private void saveToRedis(SessionData sessionData) {
        try {
            redisStorage.saveSession(sessionData);
        } catch (Exception e) {
            logger.warn("Failed to save session to Redis: {}", sessionData.getSessionId(), e);
        }
    }

    /**
     * 异步刷新 Redis TTL
     */
    private void refreshTtlAsync(String sessionId) {
        try {
            redisStorage.refreshTtl(sessionId);
        } catch (Exception e) {
            logger.warn("Failed to refresh Redis TTL: {}", sessionId, e);
        }
    }

    /**
     * 裁剪历史消息
     */
    private void trimHistory(SessionData data) {
        // 如果有摘要，说明使用了混合记忆策略，跳过
        if (data.getSummaries() != null && !data.getSummaries().isEmpty()) {
            return;
        }
        // 兜底裁剪（正常情况下 ConversationMemoryService 会管理大小）
        while (data.getMessageHistory().size() > properties.getMaxMessagePairs()) {
            data.getMessageHistory().remove(0);
        }
    }
}
