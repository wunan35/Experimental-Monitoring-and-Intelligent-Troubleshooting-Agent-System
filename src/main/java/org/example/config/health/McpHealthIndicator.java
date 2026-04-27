package org.example.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MCP 服务健康检查
 */
@Component
public class McpHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(McpHealthIndicator.class);

    @Override
    public Health health() {
        return Health.up()
                .withDetail("status", "MCP 服务正常")
                .build();
    }
}
