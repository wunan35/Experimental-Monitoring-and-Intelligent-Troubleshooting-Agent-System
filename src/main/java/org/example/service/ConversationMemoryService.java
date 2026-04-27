package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.MemoryStrategyProperties;
import org.example.dto.ConversationSummary;
import org.example.dto.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆管理服务
 * 实现混合记忆策略：早期对话压缩成摘要 + 最近N轮完整对话
 */
@Service
public class ConversationMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationMemoryService.class);

    @Autowired
    private MemoryStrategyProperties properties;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Autowired
    private PromptService promptService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 添加消息对并检查是否需要压缩
     *
     * @param session 会话数据
     * @param userMessage 用户消息
     * @param assistantMessage 助手回复
     */
    public void addMessagePairAndCompress(SessionData session, String userMessage, String assistantMessage) {
        session.addMessagePair(userMessage, assistantMessage);
        logger.debug("添加消息对，当前 messageHistory.size()={}, totalRounds={}",
                session.getMessageHistory().size(), session.getTotalRounds());

        // 检查是否需要压缩
        if (shouldCompress(session)) {
            compress(session);
        }
    }

    /**
     * 判断是否需要触发压缩
     */
    private boolean shouldCompress(SessionData session) {
        if (!properties.isSummaryEnabled()) {
            return false;
        }
        // 当本地缓存超过配置阈值时触发压缩
        return session.getMessageHistory().size() > properties.getLocalCacheRounds();
    }

    /**
     * 执行压缩：将早期对话压缩为摘要
     */
    private void compress(SessionData session) {
        int localCache = properties.getLocalCacheRounds();
        int fullHistory = properties.getFullHistoryRounds();

        // 计算需要压缩的对话数量
        int toCompressCount = localCache - fullHistory;

        if (toCompressCount <= 0 || session.getMessageHistory().size() < localCache) {
            return;
        }

        // 获取需要压缩的对话
        List<SessionData.MessagePair> messagesToCompress = new ArrayList<>();
        for (int i = 0; i < toCompressCount && i < session.getMessageHistory().size(); i++) {
            messagesToCompress.add(session.getMessageHistory().get(i));
        }

        if (messagesToCompress.isEmpty()) {
            return;
        }

        // 计算压缩的轮数范围
        int startRound = session.getTotalRounds() - session.getMessageHistory().size() + 1;
        int endRound = startRound + messagesToCompress.size() - 1;

        // 调用 LLM 生成摘要
        String summaryContent = generateSummary(messagesToCompress);
        if (summaryContent == null || summaryContent.isEmpty()) {
            logger.warn("生成摘要失败，使用空摘要");
            summaryContent = "[对话摘要生成失败]";
        }

        // 创建摘要对象
        ConversationSummary summary = new ConversationSummary(startRound, endRound, summaryContent);

        // 添加到摘要列表
        session.getSummaries().add(summary);
        logger.info("生成摘要: rounds={}-{}, contentLength={}", startRound, endRound, summaryContent.length());

        // 移除已压缩的对话（保留最近的完整对话）
        for (int i = 0; i < toCompressCount; i++) {
            if (!session.getMessageHistory().isEmpty()) {
                session.getMessageHistory().remove(0);
            }
        }

        // 检查 Redis 存储限制，清理旧摘要
        trimOldSummaries(session);
    }

    /**
     * 调用 LLM 生成对话摘要
     */
    private String generateSummary(List<SessionData.MessagePair> messagesToCompress) {
        try {
            // 构建摘要提示词
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请将以下对话内容压缩成一个简洁的摘要，保留关键信息和意图：\n\n");

            for (int i = 0; i < messagesToCompress.size(); i++) {
                SessionData.MessagePair pair = messagesToCompress.get(i);
                promptBuilder.append(String.format("【对话 %d】\n", i + 1));
                promptBuilder.append("用户: ").append(pair.getUserMessage()).append("\n");
                promptBuilder.append("助手: ").append(pair.getAssistantMessage()).append("\n\n");
            }

            promptBuilder.append("请生成一个不超过200字的摘要，概括这段对话的核心内容和目的。");

            // 调用模型
            DashScopeApi api = DashScopeApi.builder()
                    .apiKey(dashScopeApiKey)
                    .build();

            DashScopeChatModel model = DashScopeChatModel.builder()
                    .dashScopeApi(api)
                    .defaultOptions(DashScopeChatOptions.builder()
                            .withModel(properties.getSummaryModel())
                            .withTemperature(0.3)
                            .withMaxToken(500)
                            .build())
                    .build();

            // 直接使用 call 返回的结果
            String summary = model.call(promptBuilder.toString());

            logger.debug("摘要生成成功，长度={}", summary.length());
            return summary;

        } catch (Exception e) {
            logger.error("生成摘要时发生错误", e);
            return null;
        }
    }

    /**
     * 清理过旧的摘要，确保 Redis 存储不超过限制
     */
    private void trimOldSummaries(SessionData session) {
        int redisLimit = properties.getRedisHistoryRounds();
        List<ConversationSummary> summaries = session.getSummaries();

        // 计算当前摘要覆盖的对话轮数
        int summaryRounds = 0;
        for (ConversationSummary s : summaries) {
            summaryRounds += s.getRounds();
        }

        int totalRounds = session.getTotalRounds();
        int recentRounds = session.getMessageHistory().size();

        // 如果总轮数超过 Redis 限制，删除最早的摘要
        while (summaryRounds + recentRounds > redisLimit && !summaries.isEmpty()) {
            ConversationSummary oldest = summaries.remove(0);
            summaryRounds -= oldest.getRounds();
            logger.info("清理旧摘要: rounds={}-{}, 剩余摘要数={}",
                    oldest.getStartRound(), oldest.getEndRound(), summaries.size());
        }
    }

    /**
     * 构建对话上下文：包含摘要和完整对话
     *
     * @param session 会话数据
     * @return 用于构建 prompt 的历史消息列表
     */
    public List<Map<String, String>> buildContext(SessionData session) {
        List<Map<String, String>> context = new ArrayList<>();

        // 1. 添加历史摘要（如果有）
        List<ConversationSummary> summaries = session.getSummaries();
        if (!summaries.isEmpty()) {
            context.add(createSummaryMessage(summaries));
        }

        // 2. 添加最近的完整对话
        List<SessionData.MessagePair> recentMessages = session.getMessageHistory();
        for (SessionData.MessagePair pair : recentMessages) {
            // 用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", pair.getUserMessage());
            context.add(userMsg);

            // 助手回复
            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", pair.getAssistantMessage());
            context.add(assistantMsg);
        }

        logger.debug("构建上下文: 摘要数={}, 完整消息对={}, 总消息数={}",
                summaries.size(), recentMessages.size(), context.size());

        return context;
    }

    /**
     * 创建摘要消息
     */
    private Map<String, String> createSummaryMessage(List<ConversationSummary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("【对话摘要】以下是早期对话的简要总结：\n\n");

        for (ConversationSummary summary : summaries) {
            sb.append(String.format("■ 第%d-%d轮: %s\n",
                    summary.getStartRound(), summary.getEndRound(), summary.getSummaryContent()));
        }

        Map<String, String> msg = new HashMap<>();
        msg.put("role", "system");
        msg.put("content", sb.toString());
        return msg;
    }

    /**
     * 获取记忆策略配置
     */
    public MemoryStrategyProperties getProperties() {
        return properties;
    }
}
