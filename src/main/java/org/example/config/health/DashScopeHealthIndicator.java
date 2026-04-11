package org.example.config.health;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * DashScope API 健康检查
 */
@Component
public class DashScopeHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeHealthIndicator.class);

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Autowired(required = false)
    private DashScopeApi dashScopeApi;

    @Override
    public Health health() {
        try {
            // 检查 API Key 是否配置
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return Health.down()
                        .withDetail("error", "API Key 未配置")
                        .build();
            }

            // 如果 DashScopeApi 实例存在，认为配置正确
            if (dashScopeApi != null) {
                return Health.up()
                        .withDetail("status", "API 已配置")
                        .withDetail("apiKeyConfigured", true)
                        .build();
            }

            return Health.unknown()
                    .withDetail("status", "DashScopeApi 实例不可用")
                    .build();

        } catch (Exception e) {
            logger.error("DashScope 健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
