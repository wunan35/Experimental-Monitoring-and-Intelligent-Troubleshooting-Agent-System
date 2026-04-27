package org.example.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.QueryRewriteConfig;
import org.example.dto.QueryRewriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询改写和语义增强服务
 *
 * <p>使用大模型对用户查询进行改写：
 * <ul>
 *   <li>补全代词和省略的上下文信息</li>
 *   <li>将口语化表达转化为更正式、更适合检索的表达</li>
 *   <li>多意图查询拆分为多个子查询</li>
 *   <li>单意图查询保持原样</li>
 * </ul>
 */
@Service
public class QueryRewriteService {

    private static final Logger logger = LoggerFactory.getLogger(QueryRewriteService.class);

    private static final String PROMPT_TEMPLATE = """
            你是一个查询改写助手。根据对话历史和用户的最新问题，将问题改写为适合检索的查询。

            要求：
            1. 补全代词和省略的上下文信息
            2. 将口语化表达转化为更正式、更适合检索的表达
            3. 如果问题包含多个独立意图，拆分为多个子查询
            4. 如果问题已经完整清晰且只有一个意图，只输出一个查询
            5. 以 JSON 格式输出，格式为: {"queries": ["查询1", "查询2"]}
            6. 不要输出 JSON 以外的任何内容

            对话历史：
            %s

            用户最新问题: %s
            """;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private QueryRewriteConfig rewriteConfig;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${query-rewrite.model:qwen3-max}")
    private String model;

    private final Generation generation = new Generation();

    /**
     * 执行查询改写
     *
     * @param query 原始用户查询
     * @return 改写结果
     */
    public QueryRewriteResult rewrite(String query) {
        return rewrite(query, new ArrayList<>());
    }

    /**
     * 执行查询改写（带对话历史）
     *
     * @param query 原始用户查询
     * @param history 对话历史，格式：[{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
     * @return 改写结果
     */
    public QueryRewriteResult rewrite(String query, List<Map<String, String>> history) {
        if (query == null || query.isEmpty()) {
            return QueryRewriteResult.passthrough(query);
        }

        if (!rewriteConfig.isEnabled()) {
            logger.debug("查询改写未启用，返回原始查询");
            return QueryRewriteResult.passthrough(query);
        }

        logger.info("开始查询改写，原始查询: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            // 构建对话历史字符串
            String historyStr = buildHistoryString(history);

            // 构建 prompt
            String prompt = String.format(PROMPT_TEMPLATE, historyStr, query);
            logger.debug("查询改写 prompt: {}", prompt);

            // 调用 LLM
            String response = callLLM(prompt);
            logger.debug("查询改写响应: {}", response);

            // 解析 JSON 响应
            List<String> rewrittenQueries = parseQueriesFromJson(response);

            // 如果解析失败或为空，使用原始查询
            if (rewrittenQueries.isEmpty()) {
                logger.warn("解析改写结果失败，使用原始查询");
                rewrittenQueries.add(query);
            }

            QueryRewriteResult result = new QueryRewriteResult();
            result.setOriginalQuery(query);
            result.setRewrittenQueries(rewrittenQueries);
            result.setRewritten(!rewrittenQueries.equals(List.of(query)));
            result.setRewriteType("query_rewrite");

            long duration = System.currentTimeMillis() - startTime;
            logger.info("查询改写完成，耗时 {}ms，生成 {} 个改写查询: {}",
                    duration, rewrittenQueries.size(), rewrittenQueries);

            return result;

        } catch (Exception e) {
            logger.error("查询改写失败，使用原始查询", e);
            return QueryRewriteResult.passthrough(query);
        }
    }

    /**
     * 基于检索结果进行伪相关反馈增强
     *
     * @param query 原始查询
     * @param initialResults 初始检索结果（Top-K文档）
     * @return 增强后的查询列表
     */
    public List<String> enrichWithPseudoFeedback(String query,
                                                 List<VectorSearchService.SearchResult> initialResults) {
        if (!rewriteConfig.isEnabled() || !rewriteConfig.isPseudoFeedbackEnabled()) {
            return List.of(query);
        }

        if (initialResults == null || initialResults.isEmpty()) {
            return List.of(query);
        }

        try {
            logger.info("开始伪相关反馈增强，初始结果数: {}", initialResults.size());

            // 构建文档摘要
            StringBuilder docContext = new StringBuilder();
            int maxDocs = Math.min(rewriteConfig.getPseudoFeedback().getInitialTopK(), initialResults.size());
            for (int i = 0; i < maxDocs; i++) {
                VectorSearchService.SearchResult r = initialResults.get(i);
                String content = r.getContent();
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                docContext.append("【文档").append(i + 1).append("】\n")
                        .append(content)
                        .append("\n\n");
            }

            // 构建 prompt
            String prompt = buildPseudoFeedbackPrompt(query, docContext.toString());
            String response = callLLM(prompt);

            // 解析返回结果
            List<String> queries = parseQueriesFromJson(response);
            if (queries.isEmpty()) {
                queries.add(query);
            }

            logger.info("伪相关反馈增强完成，生成 {} 个扩展查询: {}", queries.size(), queries);
            return queries;

        } catch (Exception e) {
            logger.error("伪相关反馈增强失败", e);
            return List.of(query);
        }
    }

    /**
     * 构建对话历史字符串
     */
    private String buildHistoryString(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return "(无对话历史)";
        }

        return history.stream()
                .map(msg -> {
                    String role = msg.getOrDefault("role", "user");
                    String content = msg.getOrDefault("content", "");
                    return role + ": " + content;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建伪相关反馈 Prompt
     */
    private String buildPseudoFeedbackPrompt(String query, String documents) {
        return """
                你是一个查询优化专家，基于检索到的文档扩展用户查询。

                ## 原始查询
                """ + query + """

                ## 检索到的相关文档
                """ + documents + """

                ## 任务
                分析以上文档，提取与原始查询相关的关键词和概念，生成扩展查询。

                ## 输出要求
                1. 从文档中提取重要的术语和概念
                2. 生成2-3个扩展查询，结合原始查询和文档内容
                3. 扩展查询应该更具体、更精准
                4. 以 JSON 格式输出: {"queries": ["扩展查询1", "扩展查询2", "扩展查询3"]}
                5. 不要输出 JSON 以外的任何内容
                """;
    }

    /**
     * 调用 LLM
     */
    private String callLLM(String prompt) throws NoApiKeyException, ApiException, InputRequiredException {
        // 设置 API Key 和 Base URL
        Constants.apiKey = apiKey;
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

        List<Message> messages = List.of(
                Message.builder()
                        .role(Role.USER.getValue())
                        .content(prompt)
                        .build()
        );

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .resultFormat("message")
                .messages(messages)
                .build();

        GenerationResult result = generation.call(param);

        if (result.getOutput() != null &&
            result.getOutput().getChoices() != null &&
            !result.getOutput().getChoices().isEmpty()) {
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        }

        return "";
    }

    /**
     * 从 JSON 响应中解析查询列表
     */
    private List<String> parseQueriesFromJson(String response) {
        if (response == null || response.isEmpty()) {
            return List.of();
        }

        try {
            // 清理可能的 markdown 代码块
            response = response.trim();
            if (response.startsWith("```")) {
                int jsonStart = response.indexOf("{");
                int jsonEnd = response.lastIndexOf("}");
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    response = response.substring(jsonStart, jsonEnd + 1);
                }
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode queriesNode = root.get("queries");

            if (queriesNode != null && queriesNode.isArray()) {
                List<String> queries = new ArrayList<>();
                for (JsonNode node : queriesNode) {
                    String q = node.asText();
                    if (q != null && !q.isEmpty()) {
                        queries.add(q.trim());
                    }
                }
                return queries;
            }
        } catch (Exception e) {
            logger.warn("解析 JSON 失败: {}, response: {}", e.getMessage(), response);
        }

        return List.of();
    }
}
