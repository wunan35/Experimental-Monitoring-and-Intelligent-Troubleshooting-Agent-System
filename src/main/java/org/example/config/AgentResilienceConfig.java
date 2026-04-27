package org.example.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Agent执行超时和重试配置
 *
 * <h2>配置策略</h2>
 * <ul>
 *   <li><b>总超时</b> - 整个Agent流程不超过2分钟</li>
 *   <li><b>单步超时</b> - 每个工具调用不超过30秒</li>
 *   <li><b>重试策略</b> - 网络错误时最多重试2次</li>
 *   <li><b>退避策略</b> - 指数退避，避免雪崩</li>
 * </ul>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@Configuration
public class AgentResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentResilienceConfig.class);

    /**
     * Agent总超时配置
     */
    @Bean("agentTimeLimiter")
    public TimeLimiter agentTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMinutes(2))  // 总超时2分钟
                .build();

        TimeLimiter timeLimiter = TimeLimiter.of("agentTimeLimiter", config);
        logger.info("Agent总超时配置: 2分钟");
        return timeLimiter;
    }

    /**
     * 工具调用超时配置
     */
    @Bean("toolTimeLimiter")
    public TimeLimiter toolTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))  // 单个工具调用30秒
                .build();

        TimeLimiter timeLimiter = TimeLimiter.of("toolTimeLimiter", config);
        logger.info("工具调用超时配置: 30秒");
        return timeLimiter;
    }

    /**
     * Agent执行重试配置
     */
    @Bean("agentRetry")
    public Retry agentRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)  // 最多重试2次
                .waitDuration(Duration.ofSeconds(1))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2))  // 指数退避
                .retryOnException(e -> isRetriableException(e))
                .build();

        Retry retry = Retry.of("agentRetry", config);
        logger.info("Agent重试配置: 最大2次，指数退避");
        return retry;
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetriableException(Throwable e) {
        // 网络相关异常
        if (e instanceof java.net.ConnectException ||
            e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.SocketException) {
            return true;
        }

        // HTTP 5xx 错误
        if (e instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }

        // 超时异常
        if (e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }

        return false;
    }
}