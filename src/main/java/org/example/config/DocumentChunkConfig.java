package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档分片配置
 * 支持传统分片、语义分片和混合分片策略
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {

    /**
     * 每个分片的最大字符数
     */
    private int maxSize = 800;

    /**
     * 分片之间的重叠字符数
     */
    private int overlap = 100;

    /**
     * 最小分片大小（字符数）
     */
    private int minSize = 100;

    /**
     * 分片策略: semantic(语义分片), traditional(传统分片), hybrid(混合分片)
     */
    private String strategy = "traditional";

    /**
     * 语义相似度阈值（用于语义分片）
     */
    private float semanticThreshold = 0.7f;

    /**
     * 是否保留标题
     */
    private boolean preserveHeadings = true;

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setSemanticThreshold(float semanticThreshold) {
        this.semanticThreshold = semanticThreshold;
    }

    public void setPreserveHeadings(boolean preserveHeadings) {
        this.preserveHeadings = preserveHeadings;
    }
}
