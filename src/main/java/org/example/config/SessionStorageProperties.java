package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 会话存储配置属性
 * 支持本地内存和Redis两种存储方式
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "session.storage")
public class SessionStorageProperties {

    /**
     * 存储类型: local 或 redis
     * 默认使用本地内存存储
     */
    private String type = "local";

    /**
     * 会话过期时间（秒）
     * 默认30分钟
     */
    private long expireSeconds = 1800L;

    /**
     * Redis key前缀
     * 用于区分不同应用的会话
     */
    private String redisKeyPrefix = "agent:session:";

    /**
     * 最大历史消息对数
     * 超过后自动清理最旧的消息
     */
    private int maxMessagePairs = 6;
}
