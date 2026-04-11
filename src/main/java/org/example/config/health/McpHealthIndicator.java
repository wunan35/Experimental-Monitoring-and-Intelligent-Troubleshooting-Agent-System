package org.example.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MCP 服务健康检查
 */
@Component
public class McpHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(McpHealthIndicator.class);

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean mcpEnabled;

    @Value("${spring.ai.mcp.client.sse.connections.tencent-cls.url:}")
    private String mcpUrl;

    @Value("${spring.ai.mcp.client.sse.connections.tencent-cls.sse-endpoint:}")
    private String mcpEndpoint;

    @Override
    public Health health() {
        if (!mcpEnabled) {
            return Health.unknown()
                    .withDetail("status", "MCP 客户端未启用")
                    .build();
        }

        try {
            // 检查 MCP 配置
            if (mcpUrl == null || mcpUrl.isEmpty()) {
                return Health.down()
                        .withDetail("error", "MCP URL 未配置")
                        .build();
            }

            if (mcpEndpoint == null || mcpEndpoint.isEmpty()) {
                return Health.down()
                        .withDetail("error", "MCP SSE 端点未配置")
                        .withDetail("hint", "请设置环境变量 MCP_SSE_ENDPOINT")
                        .build();
            }

            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("url", mcpUrl)
                    .withDetail("endpointConfigured", true)
                    .build();

        } catch (Exception e) {
            logger.error("MCP 健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
