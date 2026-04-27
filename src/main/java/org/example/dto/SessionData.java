package org.example.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 可序列化的会话数据对象
 * 用于Redis存储和本地内存存储
 */
@Data
public class SessionData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话唯一标识
     */
    private String sessionId;

    /**
     * 历史消息列表
     * 每个元素是一对用户消息和AI回复
     */
    private List<MessagePair> messageHistory = new ArrayList<>();

    /**
     * 会话创建时间戳
     */
    private long createTime;

    /**
     * 最后访问时间戳
     */
    private long lastAccessTime;

    /**
     * 历史对话摘要列表
     * 存储被压缩的早期对话的摘要
     */
    private List<ConversationSummary> summaries = new ArrayList<>();

    /**
     * 累计对话轮数
     * 用于追踪总对话轮数，便于生成摘要时确定轮数范围
     */
    private int totalRounds = 0;

    /**
     * 消息对 - 用户消息和AI回复的组合
     */
    @Data
    public static class MessagePair implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 用户消息内容
         */
        private String userMessage;

        /**
         * AI回复内容
         */
        private String assistantMessage;

        /**
         * 消息时间戳
         */
        private long timestamp;

        public MessagePair() {
        }

        public MessagePair(String userMessage, String assistantMessage) {
            this.userMessage = userMessage;
            this.assistantMessage = assistantMessage;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public SessionData() {
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = this.createTime;
    }

    public SessionData(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    /**
     * 添加一对消息（用户问题 + AI回复）
     */
    public void addMessagePair(String userMessage, String assistantMessage) {
        messageHistory.add(new MessagePair(userMessage, assistantMessage));
        this.totalRounds++;
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 更新最后访问时间
     */
    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 清空历史消息
     * 同时清除摘要和重置总轮数
     */
    public void clearHistory() {
        messageHistory.clear();
        summaries.clear();
        totalRounds = 0;
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 获取消息对数量
     */
    public int getMessagePairCount() {
        return messageHistory.size();
    }

    /**
     * 获取总对话轮数
     */
    public int getTotalRounds() {
        return totalRounds;
    }
}
