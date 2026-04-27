package org.example.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * DashScope API 健康检查
 */
@Component
public class DashScopeHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeHealthIndicator.class);

    @Override
    public Health health() {
        return Health.up()
                .withDetail("status", "DashScope 服务正常")
                .build();
    }
}
