package org.example.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 配置类
 * 用于工具调用的限流、熔断、重试
 */
@Configuration
public class Resilience4jConfig {

    private static final Logger logger = LoggerFactory.getLogger(Resilience4jConfig.class);

    /**
     * 工具调用熔断器
     */
    @Bean
    public CircuitBreaker toolCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .slowCallRateThreshold(80)
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("tool-execution", config);

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.warn("工具调用熔断器状态变更: {}", event))
                .onError(event -> logger.error("工具调用熔断器错误: {}", event.getThrowable().getMessage()))
                .onSuccess(event -> logger.debug("工具调用成功, 耗时: {}ms", event.getElapsedDuration().toMillis()));

        logger.info("工具调用熔断器初始化完成");
        return circuitBreaker;
    }

    /**
     * 工具调用限流器
     */
    @Bean
    public RateLimiter toolRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        RateLimiter rateLimiter = registry.rateLimiter("tool-execution", config);

        rateLimiter.getEventPublisher()
                .onSuccess(event -> logger.debug("工具调用限流通过"))
                .onFailure(event -> logger.warn("工具调用被限流: {}", event));

        logger.info("工具调用限流器初始化完成，限制: {}/秒", config.getLimitForPeriod());
        return rateLimiter;
    }

    /**
     * 工具调用重试器
     */
    @Bean
    public Retry toolRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(1, 2))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        Retry retry = registry.retry("tool-execution", config);

        retry.getEventPublisher()
                .onRetry(event -> logger.warn("工具调用重试, 第{}次, 原因: {}",
                        event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
                .onSuccess(event -> logger.debug("工具调用重试成功"))
                .onError(event -> logger.error("工具调用重试失败, 共重试{}次",
                        event.getNumberOfRetryAttempts()));

        logger.info("工具调用重试器初始化完成，最大重试次数: {}", config.getMaxAttempts());
        return retry;
    }
}
