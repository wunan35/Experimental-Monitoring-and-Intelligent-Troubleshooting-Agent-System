package org.example.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(RedisHealthIndicator.class);

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        if (redisTemplate == null) {
            return Health.unknown()
                    .withDetail("status", "Redis 未配置（使用本地会话存储）")
                    .build();
        }

        try {
            // 执行 PING 命令
            String result = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();

            if ("PONG".equalsIgnoreCase(result)) {
                return Health.up()
                        .withDetail("connected", true)
                        .withDetail("response", result)
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Redis PING 响应异常: " + result)
                        .build();
            }

        } catch (Exception e) {
            logger.error("Redis 健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("connected", false)
                    .build();
        }
    }
}
