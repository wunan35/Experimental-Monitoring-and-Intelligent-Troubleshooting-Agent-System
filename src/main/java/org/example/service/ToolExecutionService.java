package org.example.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.service.metrics.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 工具执行服务
 * 包装工具调用，提供限流、熔断、重试能力
 */
@Service
public class ToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionService.class);

    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final Retry retry;

    @Autowired(required = false)
    private AgentMetricsService metricsService;

    public ToolExecutionService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            RetryRegistry retryRegistry) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tool-execution");
        this.rateLimiter = rateLimiterRegistry.rateLimiter("tool-execution");
        this.retry = retryRegistry.retry("tool-execution");
    }

    /**
     * 执行工具调用（带治理能力）
     *
     * @param toolName    工具名称
     * @param toolCall    工具调用逻辑
     * @param <T>         返回类型
     * @return 工具调用结果
     */
    public <T> T execute(String toolName, Supplier<T> toolCall) {
        logger.info("执行工具调用: {}", toolName);
        long startTime = System.currentTimeMillis();
        Timer.Sample timer = metricsService != null ? metricsService.startTimer() : null;

        try {
            // 组合装饰器：限流 -> 熔断 -> 重试 -> 执行
            Supplier<T> decoratedSupplier =
                    RateLimiter.decorateSupplier(rateLimiter,
                            CircuitBreaker.decorateSupplier(circuitBreaker,
                                    Retry.decorateSupplier(retry, toolCall)));

            T result = decoratedSupplier.get();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("工具调用成功: {}, 耗时: {}ms", toolName, duration);

            if (metricsService != null) {
                if (timer != null) {
                    metricsService.stopTimer(timer, AgentMetricsService.TOOL_DURATION);
                }
                metricsService.recordToolInvocation(toolName, true);
            }

            return result;

        } catch (CallNotPermittedException e) {
            logger.error("工具调用被熔断: {}", toolName);
            recordFailure(toolName, startTime, "circuit_breaker_open");
            throw new ToolExecutionException("工具调用熔断器已打开，请稍后重试", e);

        } catch (io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
            logger.error("工具调用被限流: {}", toolName);
            recordFailure(toolName, startTime, "rate_limited");
            throw new ToolExecutionException("工具调用频率过高，请稍后重试", e);

        } catch (Exception e) {
            logger.error("工具调用失败: {}, 错误: {}", toolName, e.getMessage());
            recordFailure(toolName, startTime, "execution_error");
            throw new ToolExecutionException("工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行工具调用（带治理能力和默认值）
     *
     * @param toolName    工具名称
     * @param toolCall    工具调用逻辑
     * @param fallback    失败时的降级处理
     * @param <T>         返回类型
     * @return 工具调用结果
     */
    public <T> T executeWithFallback(String toolName, Supplier<T> toolCall, Supplier<T> fallback) {
        try {
            return execute(toolName, toolCall);
        } catch (ToolExecutionException e) {
            logger.warn("工具调用失败，使用降级处理: {}", toolName);
            return fallback.get();
        }
    }

    /**
     * 记录失败指标
     */
    private void recordFailure(String toolName, long startTime, String errorType) {
        long duration = System.currentTimeMillis() - startTime;
        if (metricsService != null) {
            metricsService.recordToolInvocation(toolName, false);
        }
    }

    /**
     * 获取熔断器状态
     */
    public String getCircuitBreakerStatus() {
        return String.format("状态: %s, 失败率: %.2f%%, 成功调用: %d, 失败调用: %d",
                circuitBreaker.getState(),
                circuitBreaker.getMetrics().getFailureRate(),
                circuitBreaker.getMetrics().getNumberOfSuccessfulCalls(),
                circuitBreaker.getMetrics().getNumberOfFailedCalls());
    }

    /**
     * 获取限流器状态
     */
    public String getRateLimiterStatus() {
        return String.format("可用许可: %d, 等待线程数: %d",
                rateLimiter.getMetrics().getAvailablePermissions(),
                rateLimiter.getMetrics().getNumberOfWaitingThreads());
    }

    /**
     * 工具执行异常
     */
    public static class ToolExecutionException extends RuntimeException {
        public ToolExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
