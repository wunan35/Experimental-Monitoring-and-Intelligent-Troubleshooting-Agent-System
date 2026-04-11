package org.example.config;

import jakarta.annotation.PreDestroy;
import org.example.service.AgentExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 优雅关闭配置
 * 确保正在执行的Agent任务能够完成
 */
@Configuration
public class GracefulShutdownConfig {

    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdownConfig.class);

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    @Autowired(required = false)
    private AgentExecutorService agentExecutorService;

    /**
     * 检查是否正在关闭
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }

    /**
     * 优雅关闭处理
     * 在Spring容器销毁前调用
     */
    @PreDestroy
    public void onShutdown() {
        logger.info("应用开始关闭，等待正在执行的Agent任务完成...");
        isShuttingDown.set(true);

        if (agentExecutorService != null) {
            try {
                // 等待最多60秒让任务完成
                agentExecutorService.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("等待Agent任务完成被中断");
                Thread.currentThread().interrupt();
            }
        }

        logger.info("优雅关闭完成");
    }
}
