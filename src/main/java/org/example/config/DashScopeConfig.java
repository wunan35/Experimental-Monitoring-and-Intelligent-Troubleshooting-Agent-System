package org.example.config;

import jakarta.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DashScope API 配置
 * 用于配置 API Key、超时时间等参数
 */
@Configuration
public class DashScopeConfig {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeConfig.class);

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.options.timeout:180000}")
    private long timeout;

    /**
     * 启动时验证配置
     */
    @PostConstruct
    public void validateConfig() {
        // 验证 API Key
        if (!StringUtils.hasText(apiKey)) {
            logger.error("=================================================");
            logger.error("DASHSCOPE_API_KEY 环境变量未设置！");
            logger.error("请在启动前设置环境变量: export DASHSCOPE_API_KEY=your-api-key");
            logger.error("或者在 application-local.yml 中配置（仅开发环境）");
            logger.error("=================================================");
            throw new IllegalStateException(
                    "DashScope API Key 未配置。请设置 DASHSCOPE_API_KEY 环境变量。"
            );
        }

        // 验证 API Key 格式（DashScope Key 通常以 sk- 开头）
        if (!apiKey.startsWith("sk-")) {
            logger.warn("API Key 格式可能不正确，DashScope API Key 通常以 'sk-' 开头");
        }

        // 打印脱敏的 API Key 用于调试
        String maskedKey = apiKey.length() > 8
                ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
                : "***";
        logger.info("DashScope API Key 已加载: {}", maskedKey);
    }

    /**
     * 配置 RestClient.Builder，设置超时时间
     * Spring AI 会自动使用这个 Bean
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // 创建自定义的 OkHttpClient，设置超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .readTimeout(Duration.ofMillis(timeout))
                .writeTimeout(Duration.ofMillis(timeout))
                .callTimeout(Duration.ofMillis(timeout))
                .build();

        // 创建 RestClient.Builder 并配置 OkHttpClient
        return RestClient.builder()
                .requestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient));
    }
}
