package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 查询改写配置
 */
@Configuration
@ConfigurationProperties(prefix = "query-rewrite")
@Getter
@Setter
public class QueryRewriteConfig {

    private static final Logger logger = LoggerFactory.getLogger(QueryRewriteConfig.class);

    /** 是否启用查询改写 */
    private boolean enabled = false;

    /** 模型配置 */
    private String model = "qwen3-max";

    /** 是否启用同义词扩展 */
    private boolean synonymExpansionEnabled = true;

    /** 是否启用查询分解 */
    private boolean decompositionEnabled = true;

    /** 是否启用伪相关反馈 */
    private boolean pseudoFeedbackEnabled = true;

    /** 同义词扩展配置 */
    private SynonymConfig synonym = new SynonymConfig();

    /** 查询分解配置 */
    private DecompositionConfig decomposition = new DecompositionConfig();

    /** 伪相关反馈配置 */
    private PseudoFeedbackConfig pseudoFeedback = new PseudoFeedbackConfig();

    @Getter
    @Setter
    public static class SynonymConfig {
        /** 扩展查询数量 */
        private int maxExpansions = 5;
    }

    @Getter
    @Setter
    public static class DecompositionConfig {
        /** 最大子查询数量 */
        private int maxSubQueries = 4;
    }

    @Getter
    @Setter
    public static class PseudoFeedbackConfig {
        /** 用于反馈的初始检索文档数 */
        private int initialTopK = 5;
    }
}
