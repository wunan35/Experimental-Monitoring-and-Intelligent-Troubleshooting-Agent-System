package org.example.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.IntentRecognitionConfig;
import org.example.dto.IntentRecognitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 意图识别服务
 *
 * <p>混合策略：先用规则层快速过滤，再用大模型兜底判断。
 * 四种意图：知识库检索、工具调用、闲聊、信息不全需要补充
 */
@Service
public class IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionService.class);

    private static final String LLM_PROMPT_TEMPLATE = """
            你是一个意图分类助手。用户输入可能是以下意图之一：
            - KNOWLEDGE_RETRIEVAL: 知识库检索（查询静态文档、手册、规定、SOP等）
            - TOOL_CALLING: 工具调用（查实时指标、监控、日志，执行系统操作）
            - CHITCHAT: 闲聊（社交性对话，如问候、感谢等）
            - INFO_INCOMPLETE: 信息不足需要补充（问句不完整、缺少关键信息）

            请根据用户输入判断其意图，输出JSON格式：
            {"intent": "意图类型", "confidence": 0.95, "reason": "判断理由", "missingInfo": "缺失信息描述（仅INFO_INCOMPLETE时填写）"}

            注意：
            1. 如果用户输入是问文档、手册、规定等静态知识，返回 KNOWLEDGE_RETRIEVAL
            2. 如果用户输入是查实时数据、执行操作，返回 TOOL_CALLING
            3. 如果用户输入是问候、感谢等社交对话，返回 CHITCHAT
            4. 如果用户输入模糊、不完整或无法判断，返回 INFO_INCOMPLETE

            用户输入：%s
            """;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IntentRecognitionConfig config;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    private final Generation generation = new Generation();

    /**
     * 识别用户意图
     *
     * @param query  用户输入
     * @param history 对话历史
     * @return 意图识别结果
     */
    public IntentRecognitionResult recognize(String query, List<Map<String, String>> history) {
        if (query == null || query.trim().isEmpty()) {
            return IntentRecognitionResult.unknown(0.0, "输入为空");
        }

        if (!config.isEnabled()) {
            logger.debug("意图识别未启用，默认降级处理");
            return IntentRecognitionResult.unknown(0.0, "意图识别未启用");
        }

        String trimmedQuery = query.trim();
        logger.info("开始意图识别，输入: {}", trimmedQuery);

        // 1. 规则层快速过滤
        IntentRecognitionResult ruleResult = tryRuleMatch(trimmedQuery);
        if (ruleResult != null) {
            logger.info("规则层匹配成功: {}", ruleResult);
            return ruleResult;
        }

        // 2. 信息完整性检测
        IntentRecognitionResult completenessResult = checkCompleteness(trimmedQuery);
        if (completenessResult != null) {
            logger.info("信息完整性检测: {}", completenessResult);
            return completenessResult;
        }

        // 3. 大模型兜底
        if (config.isLlmFallback()) {
            try {
                IntentRecognitionResult llmResult = tryLlmMatch(trimmedQuery);
                if (llmResult != null && llmResult.getConfidence() >= config.getConfidenceThreshold()) {
                    logger.info("LLM识别成功: {}", llmResult);
                    return llmResult;
                }
            } catch (Exception e) {
                logger.warn("LLM意图识别失败: {}", e.getMessage());
            }
        }

        // 4. 降级处理
        logger.warn("意图识别降级处理，输入: {}", trimmedQuery);
        return IntentRecognitionResult.unknown(0.0, "无法明确识别意图");
    }

    /**
     * 规则层快速匹配
     */
    private IntentRecognitionResult tryRuleMatch(String query) {
        IntentRecognitionConfig.Rules rules = config.getRules();

        // 优先级1: 闲聊检测（最快短路）
        for (String keyword : rules.getChitchat()) {
            if (containsKeyword(query, keyword)) {
                return IntentRecognitionResult.chitchat(0.95, "规则匹配-闲聊关键词: " + keyword);
            }
        }

        // 优先级2: 工具调用检测
        for (String keyword : rules.getToolCalling()) {
            if (containsKeyword(query, keyword)) {
                return IntentRecognitionResult.toolCalling(0.95, "规则匹配-工具调用关键词: " + keyword);
            }
        }

        // 优先级3: 知识库检索检测
        for (String keyword : rules.getKnowledgeRetrieval()) {
            if (containsKeyword(query, keyword)) {
                return IntentRecognitionResult.knowledgeRetrieval(0.95, "规则匹配-知识库检索关键词: " + keyword);
            }
        }

        return null;
    }

    /**
     * 检测信息完整性
     */
    private IntentRecognitionResult checkCompleteness(String query) {
        // 移除标点后的查询
        String cleaned = query.replaceAll("[，。！？、：；]+", "").trim();

        // 检测空输入
        if (cleaned.isEmpty()) {
            return IntentRecognitionResult.infoIncomplete(0.8, "输入为空或只有标点", "请提供有效的查询内容");
        }

        // 检测是否只有助词或代词（问句不完整）
        if (cleaned.matches("^[的么呢啊呀哦呀呗啦嘻哈嘿]$")) {
            return IntentRecognitionResult.infoIncomplete(0.9, "输入无实际意义", "请明确您的问题");
        }

        // 检测问号后面为空或很短
        if (query.contains("?") || query.contains("？")) {
            String afterQuestionMark = query.replaceAll("^[^？?]*[？?]", "").trim();
            if (afterQuestionMark.length() < 2) {
                return IntentRecognitionResult.infoIncomplete(0.85, "问句不完整，疑问词后无内容",
                        "请完整描述您的问题");
            }
        }

        return null;
    }

    /**
     * LLM兜底识别
     */
    private IntentRecognitionResult tryLlmMatch(String query) throws Exception {
        logger.debug("开始LLM意图识别，输入: {}", query);

        // 设置 API Key 和 Base URL
        Constants.apiKey = apiKey;
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

        String prompt = String.format(LLM_PROMPT_TEMPLATE, query);

        List<Message> messages = List.of(
                Message.builder()
                        .role(Role.USER.getValue())
                        .content(prompt)
                        .build()
        );

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(config.getModel())
                .resultFormat("message")
                .messages(messages)
                .build();

        GenerationResult result = generation.call(param);

        if (result.getOutput() == null || result.getOutput().getChoices() == null || result.getOutput().getChoices().isEmpty()) {
            return null;
        }

        String response = result.getOutput().getChoices().get(0).getMessage().getContent();
        logger.debug("LLM意图识别响应: {}", response);

        return parseLlmResponse(response);
    }

    /**
     * 解析LLM响应
     */
    private IntentRecognitionResult parseLlmResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        try {
            // 清理可能的markdown代码块
            response = response.trim();
            if (response.startsWith("```")) {
                int jsonStart = response.indexOf("{");
                int jsonEnd = response.lastIndexOf("}");
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    response = response.substring(jsonStart, jsonEnd + 1);
                }
            }

            JsonNode root = objectMapper.readTree(response);
            String intentStr = root.get("intent").asText();
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5;
            String reason = root.has("reason") ? root.get("reason").asText() : "";
            String missingInfo = root.has("missingInfo") ? root.get("missingInfo").asText() : null;

            IntentRecognitionResult.Intent intent = parseIntent(intentStr);
            if (intent == IntentRecognitionResult.Intent.INFO_INCOMPLETE && missingInfo != null) {
                return IntentRecognitionResult.infoIncomplete(confidence, reason, missingInfo);
            }

            return new IntentRecognitionResult(intent, confidence, reason);

        } catch (Exception e) {
            logger.warn("解析LLM响应失败: {}, response: {}", e.getMessage(), response);
            return null;
        }
    }

    /**
     * 解析意图字符串
     */
    private IntentRecognitionResult.Intent parseIntent(String intentStr) {
        if (intentStr == null) {
            return IntentRecognitionResult.Intent.UNKNOWN;
        }
        return switch (intentStr.toUpperCase()) {
            case "KNOWLEDGE_RETRIEVAL" -> IntentRecognitionResult.Intent.KNOWLEDGE_RETRIEVAL;
            case "TOOL_CALLING" -> IntentRecognitionResult.Intent.TOOL_CALLING;
            case "CHITCHAT" -> IntentRecognitionResult.Intent.CHITCHAT;
            case "INFO_INCOMPLETE" -> IntentRecognitionResult.Intent.INFO_INCOMPLETE;
            default -> IntentRecognitionResult.Intent.UNKNOWN;
        };
    }

    /**
     * 判断查询是否包含关键词（支持简短匹配）
     */
    private boolean containsKeyword(String query, String keyword) {
        return query.contains(keyword);
    }
}
