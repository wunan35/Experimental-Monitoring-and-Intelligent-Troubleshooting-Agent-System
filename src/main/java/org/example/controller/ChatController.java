package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.exception.AgentException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.dto.*;
import org.example.exception.SessionException;
import org.example.exception.ToolExecutionException;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.ConversationMemoryService;
import org.example.service.SseHeartbeatService;
import org.example.service.storage.SessionStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import org.example.constants.AppConstants;

/**
 * 统一 API 控制器
 * 适配前端接口需求
 * 支持API版本控制、统一线程池、限流保护
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api")
@Tag(name = "chat", description = "对话接口 - 智能助手多轮对话")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationMemoryService conversationMemoryService;

    @Autowired
    private SessionStorageService sessionStorageService;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private SseHeartbeatService sseHeartbeatService;

    @Autowired
    @Qualifier("sseExecutor")
    private ExecutorService sseExecutor;

    @Autowired
    @Qualifier("chatRateLimiter")
    private RateLimiter chatRateLimiter;

    @Autowired
    @Qualifier("aiopsRateLimiter")
    private RateLimiter aiopsRateLimiter;

    /**
     * 普通对话接口（支持工具调用）
     * 与 /chat_react 逻辑一致，但直接返回完整结果而非流式输出
     */
    @Operation(
            summary = "普通对话接口",
            description = "发送问题并获取完整回答，支持多轮对话和工具调用。适用于不需要实时输出的场景。"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "对话成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ChatResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "请求被限流"
            )
    })
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Parameter(description = "对话请求参数", required = true)
            @Valid @RequestBody ChatRequest request) {
        try {
            // 使用限流器保护接口
            return chatRateLimiter.executeSupplier(() -> doChat(request));
        } catch (RequestNotPermitted e) {
            logger.warn("对话接口被限流 - SessionId: {}", request.getId());
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("请求过于频繁，请稍后重试"));
        }
    }

    /**
     * 执行对话逻辑
     */
    private ResponseEntity<ApiResponse<ChatResponse>> doChat(ChatRequest request) {
        try {
            logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

            // 获取或创建会话 - 使用存储服务
            SessionData session = sessionStorageService.getOrCreateSession(request.getId());

            // 使用新的记忆服务获取历史消息（包含摘要和完整对话）
            List<Map<String, String>> history = conversationMemoryService.buildContext(session);
            logger.info("会话历史消息对数: {}, 摘要数: {}, 当前消息对: {}",
                    session.getMessagePairCount(), session.getSummaries().size(), session.getMessageHistory().size());

            // 创建 DashScope API 和 ChatModel
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            // 记录可用工具
            chatService.logAvailableTools();

            logger.info("开始 ReactAgent 对话（支持自动工具调用）");

            // 构建系统提示词（包含历史消息）
            String systemPrompt = chatService.buildSystemPrompt(history);

            // 创建 ReactAgent
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

            // 执行对话
            String fullAnswer = chatService.executeChat(agent, request.getQuestion());

            // 更新会话历史（使用新的记忆服务，自动处理压缩）
            conversationMemoryService.addMessagePairAndCompress(session, request.getQuestion(), fullAnswer);
            sessionStorageService.saveSession(session);
            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}, 摘要数: {}",
                session.getSessionId(), session.getMessagePairCount(), session.getSummaries().size());

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));

        } catch (AgentException e) {
            logger.error("Agent执行失败", e);
            throw new ToolExecutionException("chat", "Agent执行失败", e);
        } catch (Exception e) {
            logger.error("对话失败", e);
            throw new SessionException(request.getId(), "对话处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空会话历史
     */
    @Operation(
            summary = "清空会话历史",
            description = "清除指定会话ID的所有对话历史记录"
    )
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(
            @Parameter(description = "清空请求参数", required = true)
            @Valid @RequestBody ClearRequest request) {
        try {
            logger.info("收到清空会话历史请求 - SessionId: {}", request.getId());

            if (sessionStorageService.exists(request.getId())) {
                sessionStorageService.clearHistory(request.getId());
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (RuntimeException e) {
            logger.error("清空会话历史失败", e);
            throw new SessionException(request.getId(), "清空会话历史失败", e);
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @Operation(
            summary = "流式对话接口",
            description = "发送问题并通过SSE流式返回回答，支持多轮对话和自动工具调用。" +
                    "返回的SSE事件类型包括：content（内容块）、error（错误）、done（完成）"
    )
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @Parameter(description = "对话请求参数", required = true)
            @Valid @RequestBody ChatRequest request) {
        // 检查限流
        if (!chatRateLimiter.acquirePermission()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(SseMessage.error("请求过于频繁，请稍后重试"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(AppConstants.SSE_TIMEOUT_MS);
        String sessionId = request.getId() != null ? request.getId() : UUID.randomUUID().toString();

        // 注册SSE心跳保活
        sseHeartbeatService.register(sessionId, emitter);

        sseExecutor.execute(() -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}", sessionId, request.getQuestion());

                // 获取或创建会话 - 使用存储服务
                SessionData session = sessionStorageService.getOrCreateSession(sessionId);

                // 使用新的记忆服务获取历史消息（包含摘要和完整对话）
                List<Map<String, String>> history = conversationMemoryService.buildContext(session);
                logger.info("ReactAgent 会话历史消息对数: {}, 摘要数: {}, 当前消息对: {}",
                        session.getMessagePairCount(), session.getSummaries().size(), session.getMessageHistory().size());

                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                // 记录可用工具
                chatService.logAvailableTools();

                logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");

                // 构建系统提示词（包含历史消息）
                String systemPrompt = chatService.buildSystemPrompt(history);

                // 创建 ReactAgent
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();

                // 使用 agent.stream() 进行流式对话
                Flux<NodeOutput> stream;
                try {
                    stream = agent.stream(request.getQuestion());
                } catch (AgentException e) {
                    throw new ToolExecutionException("chat_stream", "Agent流式执行失败", e);
                }

                stream.subscribe(
                    output -> {
                        try {
                            // 检查是否为 StreamingOutput 类型
                            if (output instanceof StreamingOutput streamingOutput) {
                                OutputType type = streamingOutput.getOutputType();

                                // 处理模型推理的流式输出
                                if (type == OutputType.AGENT_MODEL_STREAMING) {
                                    // 流式增量内容，逐步显示
                                    String chunk = streamingOutput.message().getText();
                                    if (chunk != null && !chunk.isEmpty()) {
                                        fullAnswerBuilder.append(chunk);

                                        // 实时发送到前端
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));

                                        logger.info("发送流式内容: {}", chunk);
                                    }
                                } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                    // 模型推理完成
                                    logger.info("模型输出完成");
                                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                    // 工具调用完成
                                    logger.info("工具调用完成: {}", output.node());
                                } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                    // Hook 执行完成
                                    logger.debug("Hook 执行完成: {}", output.node());
                                }
                            }
                        } catch (IOException e) {
                            logger.error("发送流式消息失败", e);
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        // 错误处理
                        logger.error("ReactAgent 流式对话失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                        } catch (IOException ex) {
                            logger.error("发送错误消息失败", ex);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        // 完成处理
                        try {
                            String fullAnswer = fullAnswerBuilder.toString();
                            logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}",
                                session.getSessionId(), fullAnswer.length());

                            // 更新会话历史（使用新的记忆服务，自动处理压缩）
                            conversationMemoryService.addMessagePairAndCompress(session, request.getQuestion(), fullAnswer);
                            sessionStorageService.saveSession(session);
                            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}, 摘要数: {}",
                                session.getSessionId(), session.getMessagePairCount(), session.getSummaries().size());

                            // 发送完成标记
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("发送完成消息失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                );

            } catch (Exception e) {
                logger.error("ReactAgent 对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */
    @Operation(
            summary = "AI智能运维分析",
            description = "自动获取当前活动告警，通过多Agent协作分析并生成运维报告。" +
                    "流程：读取告警 → 任务拆解 → 工具调用 → 生成报告"
    )
    @Tag(name = "aiops")
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        // 检查限流
        if (!aiopsRateLimiter.acquirePermission()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(SseMessage.error("请求过于频繁，请稍后重试"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(AppConstants.AIOPS_TIMEOUT_MS);
        String sessionId = "aiops-" + UUID.randomUUID().toString().substring(0, 8);

        // 注册SSE心跳保活
        sseHeartbeatService.register(sessionId, emitter);

        sseExecutor.execute(() -> {
            try {
                logger.info("收到 AI 智能运维请求 - 启动多 Agent 协作流程");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                emitter.send(SseEmitter.event().name("message").data(SseMessage.content("正在读取告警并拆解任务...\n")));

                // 调用 AiOpsService 执行分析流程
                Optional<OverAllState> overAllStateOptional;
                try {
                    overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);
                } catch (AgentException e) {
                    throw new ToolExecutionException("ai_ops", "多Agent执行失败", e);
                }

                if (overAllStateOptional.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("多 Agent 编排未获取到有效结果"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                logger.info("AI Ops 编排完成，开始提取最终报告...");

                // 提取最终报告
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

                // 输出最终报告
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    logger.info("提取到 Planner 最终报告，长度: {}", finalReportText.length());

                    // 发送分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));

                    // 发送完整的告警分析报告
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("📋 **告警分析报告**\n\n"), MediaType.APPLICATION_JSON));

                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        String chunk = finalReportText.substring(i, end);

                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                    }

                    // 发送结束分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));

                    logger.info("最终报告已完整输出");
                } else {
                    logger.warn("未能提取到 Planner 最终报告");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("⚠️ 多 Agent 流程已完成，但未能生成最终报告。"), MediaType.APPLICATION_JSON));
                }

                emitter.send(SseEmitter.event().name("message").data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();
                logger.info("AI Ops 多 Agent 编排完成");

            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }


    /**
     * 获取会话信息
     */
    @Operation(
            summary = "获取会话信息",
            description = "根据会话ID获取会话的详细信息，包括消息对数量和创建时间"
    )
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(
            @Parameter(description = "会话ID", required = true, example = "session-12345")
            @PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);

            return sessionStorageService.getSession(sessionId)
                .map(session -> {
                    SessionInfoResponse response = new SessionInfoResponse();
                    response.setSessionId(sessionId);
                    response.setMessagePairCount(session.getMessagePairCount());
                    response.setCreateTime(session.getCreateTime());
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElse(ResponseEntity.ok(ApiResponse.error("会话不存在")));

        } catch (RuntimeException e) {
            logger.error("获取会话信息失败", e);
            throw new SessionException(sessionId, "获取会话信息失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将SessionData转换为历史消息格式
     * 保持与现有buildSystemPrompt方法的兼容性
     */
    private List<Map<String, String>> convertToHistoryFormat(SessionData session) {
        List<Map<String, String>> history = new ArrayList<>();
        for (SessionData.MessagePair pair : session.getMessageHistory()) {
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", pair.getUserMessage());
            history.add(userMsg);

            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", pair.getAssistantMessage());
            history.add(assistantMsg);
        }
        return history;
    }
}
