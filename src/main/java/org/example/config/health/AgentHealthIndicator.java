package org.example.config.health;

import org.example.service.AgentExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Agent 执行服务健康检查
 */
@Component
public class AgentHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(AgentHealthIndicator.class);

    @Autowired(required = false)
    private AgentExecutorService agentExecutorService;

    @Override
    public Health health() {
        if (agentExecutorService == null) {
            return Health.unknown()
                    .withDetail("status", "Agent 执行服务未初始化")
                    .build();
        }

        try {
            AgentExecutorService.AgentExecutionStatus status = agentExecutorService.getStatus();

            // 如果利用率超过90%，标记为警告
            double utilizationRate = status.getUtilizationRate();
            if (utilizationRate >= 0.9) {
                return Health.status("WARNING")
                        .withDetail("maxConcurrent", status.maxConcurrent())
                        .withDetail("activeCount", status.activeCount())
                        .withDetail("queuedCount", status.queuedCount())
                        .withDetail("availablePermits", status.availablePermits())
                        .withDetail("utilizationRate", String.format("%.2f%%", utilizationRate * 100))
                        .withDetail("totalExecuted", status.totalExecuted())
                        .withDetail("totalRejected", status.totalRejected())
                        .withDetail("warning", "Agent 执行队列接近饱和")
                        .build();
            }

            return Health.up()
                    .withDetail("maxConcurrent", status.maxConcurrent())
                    .withDetail("activeCount", status.activeCount())
                    .withDetail("queuedCount", status.queuedCount())
                    .withDetail("availablePermits", status.availablePermits())
                    .withDetail("utilizationRate", String.format("%.2f%%", utilizationRate * 100))
                    .withDetail("totalExecuted", status.totalExecuted())
                    .withDetail("totalRejected", status.totalRejected())
                    .build();

        } catch (Exception e) {
            logger.error("Agent 健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
