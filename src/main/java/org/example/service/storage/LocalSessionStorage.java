package org.example.service.storage;

import org.example.config.SessionStorageProperties;
import org.example.dto.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地内存会话存储实现
 * 默认实现，适用于单机开发环境
 */
@Service
@ConditionalOnProperty(name = "session.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalSessionStorage implements SessionStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalSessionStorage.class);

    private final Map<String, SessionWrapper> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;
    private final SessionStorageProperties properties;

    public LocalSessionStorage(SessionStorageProperties properties) {
        this.properties = properties;

        // 启动定时清理任务
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleaner.scheduleAtFixedRate(
            this::cleanExpiredSessions,
            5, 5, TimeUnit.MINUTES
        );

        logger.info("LocalSessionStorage initialized, expireSeconds={}, maxPairs={}",
            properties.getExpireSeconds(), properties.getMaxMessagePairs());
    }

    @Override
    public SessionData getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        final String finalSessionId = sessionId;
        SessionWrapper wrapper = sessions.computeIfAbsent(sessionId, id -> {
            SessionData data = new SessionData(id);
            SessionWrapper w = new SessionWrapper(data);
            logger.debug("Created new session: {}", id);
            return w;
        });

        wrapper.touch();
        trimHistory(wrapper.getData());
        return wrapper.getData();
    }

    @Override
    public void saveSession(SessionData sessionData) {
        if (sessionData == null || sessionData.getSessionId() == null) {
            return;
        }

        SessionWrapper wrapper = sessions.get(sessionData.getSessionId());
        if (wrapper != null) {
            wrapper.touch();
            trimHistory(sessionData);
        }
    }

    @Override
    public Optional<SessionData> getSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        SessionWrapper wrapper = sessions.get(sessionId);
        if (wrapper == null) {
            return Optional.empty();
        }

        if (wrapper.isExpired(properties.getExpireSeconds())) {
            sessions.remove(sessionId);
            logger.debug("Session expired and removed: {}", sessionId);
            return Optional.empty();
        }

        wrapper.touch();
        return Optional.of(wrapper.getData());
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        logger.debug("Session deleted: {}", sessionId);
    }

    @Override
    public void clearHistory(String sessionId) {
        SessionWrapper wrapper = sessions.get(sessionId);
        if (wrapper != null) {
            wrapper.getData().clearHistory();
            wrapper.touch();
            logger.info("Session history cleared: {}", sessionId);
        }
    }

    @Override
    public boolean exists(String sessionId) {
        SessionWrapper wrapper = sessions.get(sessionId);
        if (wrapper == null) {
            return false;
        }
        return !wrapper.isExpired(properties.getExpireSeconds());
    }

    @Override
    public void refreshTtl(String sessionId) {
        SessionWrapper wrapper = sessions.get(sessionId);
        if (wrapper != null) {
            wrapper.touch();
        }
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    /**
     * 清理过期会话
     */
    private void cleanExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, SessionWrapper> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(properties.getExpireSeconds())) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned {} expired sessions, remaining: {}", removed, sessions.size());
        }
    }

    /**
     * 裁剪历史消息，保持最大窗口大小
     */
    private void trimHistory(SessionData data) {
        while (data.getMessageHistory().size() > properties.getMaxMessagePairs()) {
            data.getMessageHistory().remove(0);
        }
    }

    /**
     * 会话包装器，包含最后访问时间和锁
     */
    private static class SessionWrapper {
        private final SessionData data;
        private volatile long lastAccessTime;
        private final ReentrantLock lock = new ReentrantLock();

        public SessionWrapper(SessionData data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void touch() {
            this.lastAccessTime = System.currentTimeMillis();
            data.touch();
        }

        public boolean isExpired(long expireSeconds) {
            return System.currentTimeMillis() - lastAccessTime > expireSeconds * 1000;
        }

        public SessionData getData() {
            return data;
        }
    }
}
