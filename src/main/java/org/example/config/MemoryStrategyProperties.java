package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆策略配置属性
 * 控制多轮对话记忆的混合存储策略
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "memory.strategy")
public class MemoryStrategyProperties {

    /**
     * 完整对话保留轮数
     * 最近 N 轮保持完整对话，不压缩
     */
    private int fullHistoryRounds = 3;

    /**
     * 本地缓存轮数
     * 本地内存中保存的对话轮数，超过后触发压缩
     */
    private int localCacheRounds = 5;

    /**
     * Redis存储轮数
     * Redis中保存的最大对话轮数，超过后清理旧摘要
     */
    private int redisHistoryRounds = 20;

    /**
     * 压缩触发方式
     * - "full": 本地缓存存满时触发
     * - "periodic": 定期压缩
     * - "manual": 手动触发
     */
    private String compressTrigger = "full";

    /**
     * 是否启用摘要压缩
     */
    private boolean summaryEnabled = true;

    /**
     * 摘要生成模型
     * 用于生成对话摘要的模型名称
     */
    private String summaryModel = "qwen3-max";

    /**
     * 单次压缩的对话轮数
     * 每次压缩将多少轮对话压缩成一个摘要
     */
    private int compressRounds = 2;
}
