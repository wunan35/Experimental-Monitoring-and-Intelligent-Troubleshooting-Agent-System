package org.example.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.example.service.metrics.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能指标监控接口
 * 提供实时的性能监控数据，用于排查性能问题
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);

    @Autowired
    private AgentMetricsService metricsService;

    @Autowired
    private MeterRegistry meterRegistry;

    // 实时性能计数器
    private final AtomicInteger activeAgentExecutions = new AtomicInteger(0);
    private final AtomicInteger activeToolCalls = new AtomicInteger(0);
    private final AtomicInteger slowQueries = new AtomicInteger(0);

    /**
     * 获取实时性能概览
     */
    @GetMapping("/overview")
    public PerformanceOverview getPerformanceOverview() {
        PerformanceOverview overview = new PerformanceOverview();

        // 从metricsService获取统计数据
        overview.setActiveAgentExecutions(activeAgentExecutions.get());
        overview.setActiveToolCalls(activeToolCalls.get());
        overview.setSlowQueries(slowQueries.get());

        // 计算平均响应时间
        Timer chatTimer = metricsService.getMeterRegistry().find("agent.chat.duration").timer();
        if (chatTimer != null) {
            overview.setAverageChatDuration(chatTimer.mean(TimeUnit.MILLISECONDS));
        }

        Timer toolTimer = metricsService.getMeterRegistry().find("agent.tool.duration").timer();
        if (toolTimer != null) {
            overview.setAverageToolDuration(toolTimer.mean(TimeUnit.MILLISECONDS));
        }

        overview.setTimestamp(System.currentTimeMillis());

        logger.info("性能概览查询: 活跃Agent={}, 活跃工具={}, 慢查询={}",
            activeAgentExecutions.get(), activeToolCalls.get(), slowQueries.get());

        return overview;
    }

    /**
     * 获取详细的性能指标
     */
    @GetMapping("/detailed")
    public DetailedMetrics getDetailedMetrics() {
        DetailedMetrics metrics = new DetailedMetrics();

        // 添加聊天相关指标
        metrics.setChatRequests((long) metricsService.getMeterRegistry().counter("agent.chat.requests.total").count());
        metrics.setChatErrors((long) metricsService.getMeterRegistry().counter("agent.chat.errors.total").count());

        // 添加工具调用指标
        metrics.setToolInvocations((long) metricsService.getMeterRegistry().counter("agent.tool.invocations.total").count());
        metrics.setToolErrors((long) metricsService.getMeterRegistry().counter("agent.tool.errors.total").count());

        // 添加向量检索指标
        metrics.setVectorSearches((long) metricsService.getMeterRegistry().counter("agent.vector.search.total").count());

        // 添加Reranker指标
        metrics.setRerankerCalls((long) metricsService.getMeterRegistry().counter("agent.reranker.total").count());

        metrics.setTimestamp(System.currentTimeMillis());

        return metrics;
    }

    /**
     * 手动触发慢查询计数
     */
    @GetMapping("/slow-query")
    public void incrementSlowQuery() {
        slowQueries.incrementAndGet();
        logger.warn("慢查询计数已增加: {}", slowQueries.get());
    }

    /**
     * 增加活跃Agent执行数
     */
    public void incrementActiveAgent() {
        activeAgentExecutions.incrementAndGet();
    }

    /**
     * 减少活跃Agent执行数
     */
    public void decrementActiveAgent() {
        activeAgentExecutions.decrementAndGet();
    }

    /**
     * 增加活跃工具调用数
     */
    public void incrementActiveTool() {
        activeToolCalls.incrementAndGet();
    }

    /**
     * 减少活跃工具调用数
     */
    public void decrementActiveTool() {
        activeToolCalls.decrementAndGet();
    }

    // ==================== 内部类 ====================

    /**
     * 性能概览数据结构
     */
    public static class PerformanceOverview {
        private int activeAgentExecutions;
        private int activeToolCalls;
        private int slowQueries;
        private double averageChatDuration;
        private double averageToolDuration;
        private long timestamp;

        // Getters and Setters
        public int getActiveAgentExecutions() { return activeAgentExecutions; }
        public void setActiveAgentExecutions(int activeAgentExecutions) { this.activeAgentExecutions = activeAgentExecutions; }

        public int getActiveToolCalls() { return activeToolCalls; }
        public void setActiveToolCalls(int activeToolCalls) { this.activeToolCalls = activeToolCalls; }

        public int getSlowQueries() { return slowQueries; }
        public void setSlowQueries(int slowQueries) { this.slowQueries = slowQueries; }

        public double getAverageChatDuration() { return averageChatDuration; }
        public void setAverageChatDuration(double averageChatDuration) { this.averageChatDuration = averageChatDuration; }

        public double getAverageToolDuration() { return averageToolDuration; }
        public void setAverageToolDuration(double averageToolDuration) { this.averageToolDuration = averageToolDuration; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 详细性能指标数据结构
     */
    public static class DetailedMetrics {
        private long chatRequests;
        private long chatErrors;
        private long toolInvocations;
        private long toolErrors;
        private long vectorSearches;
        private long rerankerCalls;
        private long timestamp;

        // Getters and Setters
        public long getChatRequests() { return chatRequests; }
        public void setChatRequests(long chatRequests) { this.chatRequests = chatRequests; }

        public long getChatErrors() { return chatErrors; }
        public void setChatErrors(long chatErrors) { this.chatErrors = chatErrors; }

        public long getToolInvocations() { return toolInvocations; }
        public void setToolInvocations(long toolInvocations) { this.toolInvocations = toolInvocations; }

        public long getToolErrors() { return toolErrors; }
        public void setToolErrors(long toolErrors) { this.toolErrors = toolErrors; }

        public long getVectorSearches() { return vectorSearches; }
        public void setVectorSearches(long vectorSearches) { this.vectorSearches = vectorSearches; }

        public long getRerankerCalls() { return rerankerCalls; }
        public void setRerankerCalls(long rerankerCalls) { this.rerankerCalls = rerankerCalls; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}