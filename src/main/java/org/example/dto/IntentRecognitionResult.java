package org.example.dto;

import java.util.List;

/**
 * 意图识别结果
 */
public class IntentRecognitionResult {

    /**
     * 意图类型
     */
    public enum Intent {
        /**
         * 知识库检索 - 查询静态文档/知识
         */
        KNOWLEDGE_RETRIEVAL,

        /**
         * 工具调用 - 查实时数据/系统操作
         */
        TOOL_CALLING,

        /**
         * 闲聊 - 社交性对话
         */
        CHITCHAT,

        /**
         * 信息不全 - 需要追问用户
         */
        INFO_INCOMPLETE,

        /**
         * 未知 - 降级处理
         */
        UNKNOWN
    }

    private Intent intent;
    private double confidence;
    private String reason;
    private String missingInfo;

    public IntentRecognitionResult() {
    }

    public IntentRecognitionResult(Intent intent, double confidence, String reason) {
        this.intent = intent;
        this.confidence = confidence;
        this.reason = reason;
    }

    public static IntentRecognitionResult knowledgeRetrieval(double confidence, String reason) {
        return new IntentRecognitionResult(Intent.KNOWLEDGE_RETRIEVAL, confidence, reason);
    }

    public static IntentRecognitionResult toolCalling(double confidence, String reason) {
        return new IntentRecognitionResult(Intent.TOOL_CALLING, confidence, reason);
    }

    public static IntentRecognitionResult chitchat(double confidence, String reason) {
        return new IntentRecognitionResult(Intent.CHITCHAT, confidence, reason);
    }

    public static IntentRecognitionResult infoIncomplete(double confidence, String reason, String missingInfo) {
        IntentRecognitionResult result = new IntentRecognitionResult(Intent.INFO_INCOMPLETE, confidence, reason);
        result.setMissingInfo(missingInfo);
        return result;
    }

    public static IntentRecognitionResult unknown(double confidence, String reason) {
        return new IntentRecognitionResult(Intent.UNKNOWN, confidence, reason);
    }

    public static IntentRecognitionResult passthrough(String query) {
        return new IntentRecognitionResult(Intent.UNKNOWN, 0.0, "无法识别意图，默认降级处理");
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMissingInfo() {
        return missingInfo;
    }

    public void setMissingInfo(String missingInfo) {
        this.missingInfo = missingInfo;
    }

    @Override
    public String toString() {
        return "IntentRecognitionResult{" +
                "intent=" + intent +
                ", confidence=" + confidence +
                ", reason='" + reason + '\'' +
                ", missingInfo='" + missingInfo + '\'' +
                '}';
    }
}
