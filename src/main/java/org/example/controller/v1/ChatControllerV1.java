package org.example.controller.v1;

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
import jakarta.validation.Valid;
import org.example.constants.AppConstants;
import org.example.controller.BaseApiController;
import org.example.dto.*;
import org.example.exception.SessionException;
import org.example.exception.ToolExecutionException;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.ConversationMemoryService;
import org.example.service.IntentRoutingService;
import org.example.service.SseHeartbeatService;
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

/**
 * API V1 版本控制器
 * 所有接口带版本前缀 /api/v1，便于后续版本升级
 */
@RestController
@RequestMapping("/api/v1")
public class ChatControllerV1 extends BaseApiController {

    private static final Logger logger = LoggerFactory.getLogger(ChatControllerV1.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationMemoryService conversationMemoryService;

    @Autowired
    private IntentRoutingService intentRoutingService;

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
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        try {
            return chatRateLimiter.executeSupplier(() -> doChat(request));
        } catch (RequestNotPermitted e) {
            logger.warn("对话接口被限流 - SessionId: {}", request.getId());
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("请求过于频繁，请稍后重试"));
        }
    }

    private ResponseEntity<ApiResponse<ChatResponse>> doChat(ChatRequest request) {
        try {
            logger.info("[V1] 收到对话请求 - SessionId: {}", request.getId());

            SessionData session = sessionStorageService.getOrCreateSession(request.getId());
            List<Map<String, String>> history = conversationMemoryService.buildContext(session);

            // 意图路由
            IntentRoutingService.RouteResult routeResult = intentRoutingService.route(request.getQuestion(), history);

            String fullAnswer = routeResult.getAnswer();
            if (!routeResult.isSuccess()) {
                logger.warn("路由返回失败: {}", routeResult.getErrorMessage());
            }

            conversationMemoryService.addMessagePairAndCompress(session, request.getQuestion(), fullAnswer);
            sessionStorageService.saveSession(session);

            return success(ChatResponse.success(fullAnswer));

        } catch (AgentException e) {
            logger.error("Agent执行失败", e);
            throw new ToolExecutionException("chat", "Agent执行失败", e);
        } catch (Exception e) {
            logger.error("对话失败", e);
            throw new SessionException(request.getId(), "对话处理失败", e);
        }
    }

    /**
     * 流式对话接口（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        if (!chatRateLimiter.acquirePermission()) {
            SseEmitter emitter = new SseEmitter();
            sendSseError(emitter, "请求过于频繁，请稍后重试");
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(AppConstants.SSE_TIMEOUT_MS);
        String sessionId = request.getId() != null ? request.getId() : UUID.randomUUID().toString();

        sseHeartbeatService.register(sessionId, emitter);

        sseExecutor.execute(() -> {
            try {
                logger.info("[V1] 收到流式对话请求 - SessionId: {}", sessionId);

                SessionData session = sessionStorageService.getOrCreateSession(sessionId);
                List<Map<String, String>> history = conversationMemoryService.buildContext(session);

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
                chatService.logAvailableTools();

                String systemPrompt = chatService.buildSystemPrompt(history);
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                StringBuilder fullAnswerBuilder = new StringBuilder();
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                stream.subscribe(
                    output -> {
                        try {
                            if (output instanceof StreamingOutput streamingOutput) {
                                OutputType type = streamingOutput.getOutputType();
                                if (type == OutputType.AGENT_MODEL_STREAMING) {
                                    String chunk = streamingOutput.message().getText();
                                    if (chunk != null && !chunk.isEmpty()) {
                                        fullAnswerBuilder.append(chunk);
                                        sendSseContent(emitter, chunk);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        logger.error("流式对话失败", error);
                        sendSseError(emitter, error.getMessage());
                    },
                    () -> {
                        try {
                            String fullAnswer = fullAnswerBuilder.toString();
                            conversationMemoryService.addMessagePairAndCompress(session, request.getQuestion(), fullAnswer);
                            sessionStorageService.saveSession(session);
                            sendSseDone(emitter);
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                );
            } catch (Exception e) {
                logger.error("流式对话初始化失败", e);
                sendSseError(emitter, e.getMessage());
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）
     */
    @PostMapping(value = "/aiops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        if (!aiopsRateLimiter.acquirePermission()) {
            SseEmitter emitter = new SseEmitter();
            sendSseError(emitter, "请求过于频繁，请稍后重试");
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(AppConstants.AIOPS_TIMEOUT_MS);
        String sessionId = "aiops-" + UUID.randomUUID().toString().substring(0, 8);

        sseHeartbeatService.register(sessionId, emitter);

        sseExecutor.execute(() -> {
            try {
                logger.info("[V1] 收到 AI 智能运维请求");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .model(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .temperature(0.3)
                                .maxToken(8000)
                                .topP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                sendSseContent(emitter, "正在读取告警并拆解任务...\n");

                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);

                if (overAllStateOptional.isEmpty()) {
                    sendSseError(emitter, "多 Agent 编排未获取到有效结果");
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    logger.info("[V1] 提取到最终报告，长度: {}", finalReportText.length());

                    sendSseContent(emitter, "\n\n" + "=".repeat(60) + "\n");
                    sendSseContent(emitter, "📋 **告警分析报告**\n\n");

                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        sendSseContent(emitter, finalReportText.substring(i, end));
                    }

                    sendSseContent(emitter, "\n" + "=".repeat(60) + "\n\n");
                } else {
                    sendSseContent(emitter, "⚠️ 多 Agent 流程已完成，但未能生成最终报告。");
                }

                sendSseDone(emitter);
            } catch (Exception e) {
                logger.error("AI Ops 执行失败", e);
                sendSseError(emitter, "AI Ops 流程失败: " + e.getMessage());
            }
        });

        return emitter;
    }

    /**
     * 清空会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@Valid @RequestBody ClearRequest request) {
        try {
            logger.info("[V1] 清空会话历史 - SessionId: {}", request.getId());

            if (sessionStorageService.exists(request.getId())) {
                sessionStorageService.clearHistory(request.getId());
                return success("会话历史已清空");
            } else {
                return error("会话不存在");
            }
        } catch (RuntimeException e) {
            throw new SessionException(request.getId(), "清空会话历史失败", e);
        }
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            return sessionStorageService.getSession(sessionId)
                .map(session -> {
                    SessionInfoResponse response = new SessionInfoResponse();
                    response.setSessionId(sessionId);
                    response.setMessagePairCount(session.getMessagePairCount());
                    response.setCreateTime(session.getCreateTime());
                    return success(response);
                })
                .orElse(error("会话不存在"));
        } catch (RuntimeException e) {
            throw new SessionException(sessionId, "获取会话信息失败", e);
        }
    }
}
