package org.example.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * API限流配置
 * 防止接口被滥用，保护系统稳定性
 */
@Configuration
public class ApiRateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimitConfig.class);

    @Value("${rate-limit.chat.limit-for-period:10}")
    private int chatLimitForPeriod;

    @Value("${rate-limit.chat.limit-refresh-period:1s}")
    private Duration chatLimitRefreshPeriod;

    @Value("${rate-limit.chat.timeout-duration:5s}")
    private Duration chatTimeoutDuration;

    @Value("${rate-limit.aiops.limit-for-period:3}")
    private int aiopsLimitForPeriod;

    @Value("${rate-limit.aiops.limit-refresh-period:1m}")
    private Duration aiopsLimitRefreshPeriod;

    @Value("${rate-limit.aiops.timeout-duration:10s}")
    private Duration aiopsTimeoutDuration;

    @Value("${rate-limit.upload.limit-for-period:5}")
    private int uploadLimitForPeriod;

    @Value("${rate-limit.upload.limit-refresh-period:1m}")
    private Duration uploadLimitRefreshPeriod;

    /**
     * 对话接口限流器
     * 默认: 每秒10个请求
     */
    @Bean(name = "chatRateLimiter")
    public RateLimiter chatRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(chatLimitForPeriod)
                .limitRefreshPeriod(chatLimitRefreshPeriod)
                .timeoutDuration(chatTimeoutDuration)
                .build();

        RateLimiter rateLimiter = RateLimiterRegistry.of(config).rateLimiter("chat", config);
        logger.info("对话接口限流器初始化完成 - 每周期限制: {}, 刷新周期: {}, 超时时间: {}",
                chatLimitForPeriod, chatLimitRefreshPeriod, chatTimeoutDuration);

        return rateLimiter;
    }

    /**
     * AI Ops接口限流器
     * 默认: 每分钟3个请求（AI分析较耗资源）
     */
    @Bean(name = "aiopsRateLimiter")
    public RateLimiter aiopsRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(aiopsLimitForPeriod)
                .limitRefreshPeriod(aiopsLimitRefreshPeriod)
                .timeoutDuration(aiopsTimeoutDuration)
                .build();

        RateLimiter rateLimiter = RateLimiterRegistry.of(config).rateLimiter("aiops", config);
        logger.info("AI Ops接口限流器初始化完成 - 每周期限制: {}, 刷新周期: {}, 超时时间: {}",
                aiopsLimitForPeriod, aiopsLimitRefreshPeriod, aiopsTimeoutDuration);

        return rateLimiter;
    }

    /**
     * 文件上传接口限流器
     * 默认: 每分钟5个请求
     */
    @Bean(name = "uploadRateLimiter")
    public RateLimiter uploadRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(uploadLimitForPeriod)
                .limitRefreshPeriod(uploadLimitRefreshPeriod)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        RateLimiter rateLimiter = RateLimiterRegistry.of(config).rateLimiter("upload", config);
        logger.info("文件上传接口限流器初始化完成 - 每周期限制: {}, 刷新周期: {}",
                uploadLimitForPeriod, uploadLimitRefreshPeriod);

        return rateLimiter;
    }
}
