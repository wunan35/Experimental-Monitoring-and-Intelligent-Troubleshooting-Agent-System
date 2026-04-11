package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSE心跳保活服务
 * 防止SSE连接因超时而断开
 */
@Service
public class SseHeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(SseHeartbeatService.class);

    @Value("${sse.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    // 存储活跃的SSE连接
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    // 心跳调度器
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    /**
     * 注册SSE连接
     * @param sessionId 会话ID
     * @param emitter SSE发射器
     */
    public void register(String sessionId, SseEmitter emitter) {
        activeEmitters.put(sessionId, emitter);
        logger.info("SSE连接注册成功 - SessionId: {}, 当前活跃连接数: {}", sessionId, activeEmitters.size());

        // 启动心跳
        ScheduledFuture<?> heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                SseEmitter activeEmitter = activeEmitters.get(sessionId);
                if (activeEmitter != null) {
                    activeEmitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("timestamp", System.currentTimeMillis())));
                    logger.debug("SSE心跳发送成功 - SessionId: {}", sessionId);
                }
            } catch (IOException e) {
                logger.warn("SSE心跳发送失败 - SessionId: {}, 错误: {}", sessionId, e.getMessage());
                unregister(sessionId);
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

        // 连接完成或超时时清理
        emitter.onCompletion(() -> {
            heartbeatFuture.cancel(false);
            unregister(sessionId);
        });
        emitter.onTimeout(() -> {
            heartbeatFuture.cancel(false);
            unregister(sessionId);
        });
        emitter.onError(e -> {
            heartbeatFuture.cancel(false);
            unregister(sessionId);
        });
    }

    /**
     * 注销SSE连接
     * @param sessionId 会话ID
     */
    public void unregister(String sessionId) {
        SseEmitter removed = activeEmitters.remove(sessionId);
        if (removed != null) {
            logger.info("SSE连接注销 - SessionId: {}, 当前活跃连接数: {}", sessionId, activeEmitters.size());
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeEmitters.size();
    }

    /**
     * 关闭所有连接
     */
    public void shutdown() {
        logger.info("关闭所有SSE连接，当前连接数: {}", activeEmitters.size());
        activeEmitters.forEach((id, emitter) -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.warn("关闭SSE连接失败 - SessionId: {}", id, e);
            }
        });
        activeEmitters.clear();
        heartbeatScheduler.shutdown();
    }
}
