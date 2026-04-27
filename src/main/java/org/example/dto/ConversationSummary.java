package org.example.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 对话摘要
 * 用于存储被压缩的历史对话的摘要信息
 */
@Data
public class ConversationSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 摘要ID
     */
    private String summaryId;

    /**
     * 摘要内容
     */
    private String summaryContent;

    /**
     * 对应起始轮数（包含）
     */
    private int startRound;

    /**
     * 对应结束轮数（包含）
     */
    private int endRound;

    /**
     * 摘要创建时间戳
     */
    private long createdAt;

    /**
     * 涉及的关键词列表（用于快速检索）
     */
    private String[] keywords;

    /**
     * 摘要版本
     */
    private int version;

    public ConversationSummary() {
    }

    public ConversationSummary(int startRound, int endRound, String summaryContent) {
        this.summaryId = generateId(startRound, endRound);
        this.startRound = startRound;
        this.endRound = endRound;
        this.summaryContent = summaryContent;
        this.createdAt = System.currentTimeMillis();
        this.version = 1;
    }

    private String generateId(int startRound, int endRound) {
        return String.format("summary_%d_%d_%d", startRound, endRound, System.currentTimeMillis());
    }

    /**
     * 获取摘要范围描述
     */
    public String getRangeDescription() {
        return String.format("第%d-%d轮对话", startRound, endRound);
    }

    /**
     * 获取摘要轮数
     */
    public int getRounds() {
        return endRound - startRound + 1;
    }
}
