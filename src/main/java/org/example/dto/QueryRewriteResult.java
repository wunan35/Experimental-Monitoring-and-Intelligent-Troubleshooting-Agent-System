package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写结果
 */
@Getter
@Setter
public class QueryRewriteResult {

    /** 原始查询 */
    private String originalQuery;

    /** 改写后的查询列表（包含扩展后的查询） */
    private List<String> rewrittenQueries = new ArrayList<>();

    /** 查询类型：synonym, decomposition, pseudo_feedback, passthrough */
    private String rewriteType;

    /** 伪相关反馈时使用的Top-K文档内容 */
    private List<String> feedbackDocuments = new ArrayList<>();

    /** 改写元数据（包含决策理由等） */
    private String metadata;

    /** 是否启用了改写 */
    private boolean rewritten;

    /**
     * 创建透传结果（未启用改写时）
     */
    public static QueryRewriteResult passthrough(String query) {
        QueryRewriteResult result = new QueryRewriteResult();
        result.setOriginalQuery(query);
        result.getRewrittenQueries().add(query);
        result.setRewritten(false);
        result.setRewriteType("passthrough");
        return result;
    }
}
