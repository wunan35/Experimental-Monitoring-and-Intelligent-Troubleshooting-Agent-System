package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.example.dto.IntentRecognitionResult;
import org.example.dto.QueryRewriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.tool.ToolCallback;

/**
 * 意图路由服务
 *
 * <p>根据意图识别结果，分发到不同的处理流程：
 * <ul>
 *   <li>知识库检索 -> 查询改写 + RAG 检索</li>
 *   <li>工具调用 -> ReactAgent 执行</li>
 *   <li>闲聊 -> 直接 LLM 对话</li>
 *   <li>信息不全 -> 追问用户</li>
 *   <li>未知 -> 降级处理</li>
 * </ul>
 */
@Service
public class IntentRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRoutingService.class);

    @Autowired
    private IntentRecognitionService intentRecognitionService;

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AiOpsService aiOpsService;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    /**
     * 处理用户输入的路由分发
     *
     * @param query  用户输入
     * @param history 对话历史
     * @return 处理结果
     */
    public RouteResult route(String query, List<Map<String, String>> history) {
        logger.info("开始路由分发，输入: {}", query);

        // 1. 意图识别
        IntentRecognitionResult intentResult = intentRecognitionService.recognize(query, history);
        logger.info("意图识别结果: {}", intentResult);

        // 2. 根据意图路由
        return switch (intentResult.getIntent()) {
            case KNOWLEDGE_RETRIEVAL -> handleKnowledgeRetrieval(query, history, intentResult);
            case TOOL_CALLING -> handleToolCalling(query, history, intentResult);
            case CHITCHAT -> handleChitchat(query, history, intentResult);
            case INFO_INCOMPLETE -> handleInfoIncomplete(intentResult);
            case UNKNOWN -> handleUnknown(intentResult);
        };
    }

    /**
     * 知识库检索处理
     */
    private RouteResult handleKnowledgeRetrieval(String query, List<Map<String, String>> history,
                                                   IntentRecognitionResult intentResult) {
        logger.info("路由到知识库检索流程");

        try {
            // 1. 查询改写
            QueryRewriteResult rewriteResult = queryRewriteService.rewrite(query, history);
            logger.info("查询改写结果: {}", rewriteResult.getRewrittenQueries());

            // 2. 使用改写后的第一个查询进行向量检索
            List<String> rewrittenQueries = rewriteResult.getRewrittenQueries();
            if (rewrittenQueries.isEmpty()) {
                rewrittenQueries = List.of(query);
            }

            // 3. 向量检索
            List<VectorSearchService.SearchResult> searchResults =
                vectorSearchService.searchSimilarDocuments(rewrittenQueries.get(0), 5);

            if (searchResults.isEmpty()) {
                return RouteResult.success("抱歉，我在知识库中没有找到与您问题相关的信息。", intentResult);
            }

            // 4. 构建上下文
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < searchResults.size(); i++) {
                VectorSearchService.SearchResult result = searchResults.get(i);
                contextBuilder.append("【参考资料 ").append(i + 1).append("】\n")
                        .append(result.getContent()).append("\n\n");
            }
            String context = contextBuilder.toString();

            // 5. 调用 LLM 生成答案
            String answer = generateRagAnswer(query, context);

            return RouteResult.success(answer, intentResult);

        } catch (Exception e) {
            logger.error("知识库检索失败", e);
            return RouteResult.fallback("知识库检索失败，请稍后重试。错误: " + e.getMessage(), intentResult);
        }
    }

    @Autowired
    private VectorSearchService vectorSearchService;

    /**
     * 生成 RAG 答案
     */
    private String generateRagAnswer(String question, String context) throws Exception {
        String prompt = String.format(
            "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n" +
            "参考资料：\n%s\n\n" +
            "用户问题：%s\n\n" +
            "回答要求：\n" +
            "1. **只基于上述参考资料回答**，不要添加参考资料之外的信息。\n" +
            "2. **标注引用来源**：在每个论点或事实后用方括号标注来源，如 [参考资料1]。\n" +
            "3. **诚实反馈**：如果参考资料中没有相关信息能回答问题，必须明确说明。\n" +
            "4. **严谨表述**：避免使用\"根据我的知识\"等模糊表述。",
            context, question
        );

        com.alibaba.dashscope.common.Message message = com.alibaba.dashscope.common.Message.builder()
                .role(com.alibaba.dashscope.common.Role.USER.getValue())
                .content(prompt)
                .build();

        com.alibaba.dashscope.aigc.generation.GenerationParam param = com.alibaba.dashscope.aigc.generation.GenerationParam.builder()
                .apiKey(dashScopeApiKey)
                .model("qwen3-max")
                .resultFormat(com.alibaba.dashscope.aigc.generation.GenerationParam.ResultFormat.MESSAGE)
                .messages(List.of(message))
                .build();

        com.alibaba.dashscope.aigc.generation.GenerationResult result = generation.call(param);
        if (result.getOutput() != null && !result.getOutput().getChoices().isEmpty()) {
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        }
        return "抱歉，生成答案时出现问题。";
    }

    private final com.alibaba.dashscope.aigc.generation.Generation generation = new com.alibaba.dashscope.aigc.generation.Generation();

    /**
     * 工具调用处理
     * 简单查询 -> ReactAgent
     * 复杂分析 -> AiOps 多Agent协作
     */
    private RouteResult handleToolCalling(String query, List<Map<String, String>> history,
                                          IntentRecognitionResult intentResult) {
        logger.info("路由到工具调用流程");

        // 判断是简单查询还是复杂分析
        if (isComplexAnalysis(query)) {
            logger.info("检测到复杂分析任务，路由到 AiOps 多Agent流程");
            return handleComplexAnalysis(query, intentResult);
        } else {
            logger.info("检测到简单查询，路由到 ReactAgent");
            return handleSimpleQuery(query, history, intentResult);
        }
    }

    /**
     * 判断是否为复杂分析任务
     */
    private boolean isComplexAnalysis(String query) {
        String[] complexKeywords = {"分析", "排查", "诊断", "为什么", "原因", "根因", "报告"};
        String lowerQuery = query.toLowerCase();
        for (String keyword : complexKeywords) {
            if (lowerQuery.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单查询 - ReactAgent
     */
    private RouteResult handleSimpleQuery(String query, List<Map<String, String>> history,
                                          IntentRecognitionResult intentResult) {
        try {
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
            chatService.logAvailableTools();

            String systemPrompt = chatService.buildSystemPrompt(history);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            String answer = chatService.executeChat(agent, query);

            return RouteResult.success(answer, intentResult);

        } catch (Exception e) {
            logger.error("简单查询失败", e);
            return RouteResult.fallback("查询失败，请稍后重试。错误: " + e.getMessage(), intentResult);
        }
    }

    /**
     * 复杂分析 - AiOps 多Agent协作
     */
    private RouteResult handleComplexAnalysis(String query, IntentRecognitionResult intentResult) {
        try {
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .defaultOptions(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                            .model(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel.DEFAULT_MODEL_NAME)
                            .temperature(0.3)
                            .maxToken(8000)
                            .topP(0.9)
                            .build())
                    .build();

            ToolCallback[] toolCallbacks = chatService.getToolCallbacks();

            Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);

            if (overAllStateOptional.isEmpty()) {
                return RouteResult.fallback("多Agent编排未获取到有效结果", intentResult);
            }

            OverAllState state = overAllStateOptional.get();
            Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

            if (finalReportOptional.isPresent()) {
                String finalReportText = finalReportOptional.get();
                return RouteResult.success(finalReportText, intentResult);
            } else {
                return RouteResult.fallback("多Agent流程已完成，但未能生成最终报告", intentResult);
            }

        } catch (Exception e) {
            logger.error("复杂分析失败", e);
            return RouteResult.fallback("分析失败，请稍后重试。错误: " + e.getMessage(), intentResult);
        }
    }

    /**
     * 闲聊处理
     */
    private RouteResult handleChitchat(String query, List<Map<String, String>> history,
                                        IntentRecognitionResult intentResult) {
        logger.info("路由到闲聊流程");

        try {
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            String systemPrompt = chatService.buildSystemPrompt(history);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            String answer = chatService.executeChat(agent, query);

            return RouteResult.success(answer, intentResult);

        } catch (Exception e) {
            logger.error("闲聊处理失败", e);
            return RouteResult.fallback("抱歉，聊天出现问题，请稍后重试。", intentResult);
        }
    }

    /**
     * 信息不全处理
     */
    private RouteResult handleInfoIncomplete(IntentRecognitionResult intentResult) {
        logger.info("路由到信息不全处理");

        String missingInfo = intentResult.getMissingInfo();
        if (missingInfo == null || missingInfo.isEmpty()) {
            missingInfo = "您提供的信息不完整，请补充更多细节以便我更好地帮助您";
        }

        String response = "为了更好地帮助您，" + missingInfo + "。";

        return RouteResult.success(response, intentResult);
    }

    /**
     * 未知/降级处理
     */
    private RouteResult handleUnknown(IntentRecognitionResult intentResult) {
        logger.info("路由到降级处理");

        String response = "抱歉，我没有理解您的意思，能否详细描述一下您的问题？比如您是想查询文档、了解操作流程，还是需要我帮您查询某些数据？";

        return RouteResult.success(response, intentResult);
    }

    /**
     * 路由结果
     */
    public static class RouteResult {
        private boolean success;
        private String answer;
        private String errorMessage;
        private IntentRecognitionResult intentResult;

        public static RouteResult success(String answer, IntentRecognitionResult intentResult) {
            RouteResult result = new RouteResult();
            result.success = true;
            result.answer = answer;
            result.intentResult = intentResult;
            return result;
        }

        public static RouteResult fallback(String errorMessage, IntentRecognitionResult intentResult) {
            RouteResult result = new RouteResult();
            result.success = false;
            result.errorMessage = errorMessage;
            result.intentResult = intentResult;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public IntentRecognitionResult getIntentResult() {
            return intentResult;
        }

        public void setIntentResult(IntentRecognitionResult intentResult) {
            this.intentResult = intentResult;
        }

        @Override
        public String toString() {
            return "RouteResult{" +
                    "success=" + success +
                    ", answer='" + (answer != null ? answer.substring(0, Math.min(50, answer.length())) + "..." : null) + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", intentResult=" + intentResult +
                    '}';
        }
    }
}
