package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Prompt配置类
 * 支持Prompt版本管理和外部配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "prompt")
public class PromptConfig {

    /**
     * 当前使用的Prompt版本
     */
    private String version = "v1";

    /**
     * Prompt模板配置
     */
    private Map<String, PromptTemplate> templates;

    /**
     * Prompt模板结构
     */
    @Data
    public static class PromptTemplate {
        /**
         * 模板版本
         */
        private String version;

        /**
         * 模板描述
         */
        private String description;

        /**
         * 模板内容
         */
        private String content;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 创建时间
         */
        private String createdAt;

        /**
         * 最后更新时间
         */
        private String updatedAt;
    }
}
