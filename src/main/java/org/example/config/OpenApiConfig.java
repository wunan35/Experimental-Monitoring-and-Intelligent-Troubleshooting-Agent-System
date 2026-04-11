package org.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAPI 配置类
 * 配置 Swagger UI 文档页面信息
 *
 * <p>访问地址：</p>
 * <ul>
 *   <li>Swagger UI: http://localhost:8080/swagger-ui.html</li>
 *   <li>OpenAPI JSON: http://localhost:8080/v3/api-docs</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 配置 OpenAPI 基本信息
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags());
    }

    /**
     * API 基本信息
     */
    private Info apiInfo() {
        return new Info()
                .title("土木实验智能监控 Agent API")
                .description("""
                        ## 概述

                        本系统是基于 Spring AI Alibaba 框架构建的智能运维 Agent 系统，
                        用于土木实验监控和告警分析。

                        ## 核心功能

                        - **对话接口**: 支持多轮对话和工具调用的智能助手
                        - **AI运维**: 自动分析实验告警并生成运维报告
                        - **文件上传**: 支持上传知识库文档并自动构建向量索引
                        - **健康检查**: 系统组件健康状态监控

                        ## 工具能力

                        Agent 可调用以下工具获取实时数据：
                        - 查询实验告警（queryExperimentAlerts）
                        - 查询实验日志（queryLogs）
                        - 查询内部文档（queryInternalDocs）
                        - 获取当前时间（getCurrentTime）

                        ## 流式响应

                        `/api/chat_stream` 和 `/api/ai_ops` 接口使用 SSE (Server-Sent Events)
                        返回流式响应，支持实时输出和超时处理。
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("OnCall Agent Team")
                        .email("support@example.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    /**
     * 服务器配置
     */
    private List<Server> servers() {
        return Arrays.asList(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("本地开发服务器"),
                new Server()
                        .url("http://production-server:" + serverPort)
                        .description("生产环境服务器")
        );
    }

    /**
     * API 标签分组
     */
    private List<Tag> tags() {
        return Arrays.asList(
                new Tag().name("chat").description("对话接口 - 智能助手多轮对话"),
                new Tag().name("aiops").description("智能运维 - 自动告警分析"),
                new Tag().name("upload").description("文件上传 - 知识库文档管理"),
                new Tag().name("health").description("健康检查 - 系统状态监控")
        );
    }
}
