package org.example.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Agent指标收集服务
 * 收集对话、工具调用、向量检索等关键指标
 */
@Service
public class AgentMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(AgentMetricsService.class);

    private final MeterRegistry meterRegistry;

    // 指标名称常量
    public static final String CHAT_REQUESTS_TOTAL = "agent.chat.requests.total";
    public static final String CHAT_ERRORS_TOTAL = "agent.chat.errors.total";
    public static final String CHAT_DURATION = "agent.chat.duration";
    public static final String TOOL_INVOCATIONS_TOTAL = "agent.tool.invocations.total";
    public static final String TOOL_ERRORS_TOTAL = "agent.tool.errors.total";
    public static final String TOOL_DURATION = "agent.tool.duration";
    public static final String VECTOR_SEARCH_TOTAL = "agent.vector.search.total";
    public static final String VECTOR_SEARCH_DURATION = "agent.vector.search.duration";
    public static final String VECTOR_EMBEDDING_TOTAL = "agent.vector.embedding.total";
    public static final String VECTOR_EMBEDDING_DURATION = "agent.vector.embedding.duration";
    public static final String SESSION_ACTIVE_COUNT = "agent.session.active.count";

    public AgentMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        logger.info("AgentMetricsService initialized");
    }

    // ==================== 对话指标 ====================

    /**
     * 记录对话请求
     */
    public void recordChatRequest(String sessionId, String model) {
        Counter.builder(CHAT_REQUESTS_TOTAL)
            .tag("model", model != null ? model : "unknown")
            .description("Total number of chat requests")
            .register(meterRegistry)
            .increment();
        logger.debug("Recorded chat request for session: {}", maskSessionId(sessionId));
    }

    /**
     * 记录对话错误
     */
    public void recordChatError(String sessionId, String errorType) {
        Counter.builder(CHAT_ERRORS_TOTAL)
            .tag("error_type", errorType != null ? errorType : "unknown")
            .description("Total number of chat errors")
            .register(meterRegistry)
            .increment();
        logger.warn("Recorded chat error: {} for session: {}", errorType, maskSessionId(sessionId));
    }

    /**
     * 记录对话耗时
     */
    public void recordChatDuration(String sessionId, Duration duration) {
        Timer.builder(CHAT_DURATION)
            .description("Chat request duration")
            .register(meterRegistry)
            .record(duration);
        logger.debug("Recorded chat duration: {}ms for session: {}", duration.toMillis(), maskSessionId(sessionId));
    }

    // ==================== 工具调用指标 ====================

    /**
     * 记录工具调用
     */
    public void recordToolInvocation(String toolName, boolean success) {
        Counter.builder(TOOL_INVOCATIONS_TOTAL)
            .tag("tool", toolName != null ? toolName : "unknown")
            .tag("success", String.valueOf(success))
            .description("Total number of tool invocations")
            .register(meterRegistry)
            .increment();
        logger.info("Recorded tool invocation: {} (success={})", toolName, success);
    }

    /**
     * 记录工具调用错误
     */
    public void recordToolError(String toolName, String errorType) {
        Counter.builder(TOOL_ERRORS_TOTAL)
            .tag("tool", toolName != null ? toolName : "unknown")
            .tag("error_type", errorType != null ? errorType : "unknown")
            .description("Total number of tool errors")
            .register(meterRegistry)
            .increment();
        logger.warn("Recorded tool error: {} for tool: {}", errorType, toolName);
    }

    /**
     * 记录工具调用耗时
     */
    public void recordToolDuration(String toolName, Duration duration) {
        Timer.builder(TOOL_DURATION)
            .tag("tool", toolName != null ? toolName : "unknown")
            .description("Tool invocation duration")
            .register(meterRegistry)
            .record(duration);
        logger.debug("Recorded tool duration: {}ms for {}", duration.toMillis(), toolName);
    }

    // ==================== 向量检索指标 ====================

    /**
     * 记录向量搜索请求
     */
    public void recordVectorSearch(int topK, int resultCount) {
        Counter.builder(VECTOR_SEARCH_TOTAL)
            .tag("top_k", String.valueOf(topK))
            .tag("result_count_range", getResultCountRange(resultCount))
            .description("Total number of vector searches")
            .register(meterRegistry)
            .increment();
        logger.debug("Recorded vector search: topK={}, results={}", topK, resultCount);
    }

    /**
     * 记录向量搜索耗时
     */
    public void recordVectorSearchDuration(Duration duration) {
        Timer.builder(VECTOR_SEARCH_DURATION)
            .description("Vector search duration")
            .register(meterRegistry)
            .record(duration);
        logger.debug("Recorded vector search duration: {}ms", duration.toMillis());
    }

    /**
     * 记录向量嵌入请求
     */
    public void recordVectorEmbedding(int textCount) {
        Counter.builder(VECTOR_EMBEDDING_TOTAL)
            .tag("batch", textCount > 1 ? "true" : "false")
            .description("Total number of vector embedding requests")
            .register(meterRegistry)
            .increment();
        logger.debug("Recorded vector embedding: {} texts", textCount);
    }

    /**
     * 记录向量嵌入耗时
     */
    public void recordVectorEmbeddingDuration(Duration duration, int textCount) {
        Timer.builder(VECTOR_EMBEDDING_DURATION)
            .tag("batch", textCount > 1 ? "true" : "false")
            .description("Vector embedding duration")
            .register(meterRegistry)
            .record(duration);
        logger.debug("Recorded vector embedding duration: {}ms for {} texts", duration.toMillis(), textCount);
    }

    // ==================== 会话指标 ====================

    /**
     * 更新活跃会话数
     */
    public void updateActiveSessionCount(int count) {
        meterRegistry.gauge(SESSION_ACTIVE_COUNT, count);
    }

    // ==================== 计时器辅助方法 ====================

    /**
     * 创建计时器采样
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 停止计时器并记录耗时
     */
    public long stopTimer(Timer.Sample sample, String metricName) {
        Timer timer = Timer.builder(metricName)
            .register(meterRegistry);
        return sample.stop(timer);
    }

    /**
     * 停止计时器并返回Duration
     */
    public Duration stopAndGetDuration(Timer.Sample sample, String metricName) {
        long nanos = stopTimer(sample, metricName);
        return Duration.ofNanos(nanos);
    }

    // ==================== 辅助方法 ====================

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) {
            return "unknown";
        }
        return sessionId.substring(0, 4) + "..." + sessionId.substring(sessionId.length() - 4);
    }

    private String getResultCountRange(int count) {
        if (count == 0) return "0";
        if (count <= 3) return "1-3";
        if (count <= 5) return "4-5";
        return "6+";
    }

    /**
     * 获取MeterRegistry（供外部使用）
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
