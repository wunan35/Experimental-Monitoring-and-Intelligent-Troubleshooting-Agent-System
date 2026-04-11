package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一线程池配置
 * 集中管理所有异步任务的线程池，避免资源竞争
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolConfig.class);

    @Value("${thread-pool.core-size:5}")
    private int coreSize;

    @Value("${thread-pool.max-size:20}")
    private int maxSize;

    @Value("${thread-pool.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    @Value("${thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${thread-pool.sse-core-size:10}")
    private int sseCoreSize;

    @Value("${thread-pool.sse-max-size:50}")
    private int sseMaxSize;

    /**
     * SSE专用线程池
     * 用于处理SSE流式响应
     */
    @Bean(name = "sseExecutor")
    public ExecutorService sseExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                sseCoreSize,
                sseMaxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("sse-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("SSE线程池初始化完成 - 核心线程: {}, 最大线程: {}, 队列容量: {}",
                sseCoreSize, sseMaxSize, queueCapacity);

        return executor;
    }

    /**
     * Agent执行线程池
     * 用于Agent任务的异步执行
     */
    @Bean(name = "agentExecutor")
    public ExecutorService agentExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("agent-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("Agent线程池初始化完成 - 核心线程: {}, 最大线程: {}, 队列容量: {}",
                coreSize, maxSize, queueCapacity);

        return executor;
    }

    /**
     * 自定义线程工厂，为线程设置有意义的名称
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);
        private final String prefix;

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
