package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Agent结构化输出Schema
 * 定义标准化的Agent输出格式，便于前端解析和展示
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentOutputSchema {

    /**
     * 输出类型
     */
    private OutputType type;

    /**
     * 原始文本内容
     */
    private String rawContent;

    /**
     * 结构化报告（当type为REPORT时）
     */
    private StructuredReport report;

    /**
     * 错误信息（当执行失败时）
     */
    private ErrorInfo error;

    /**
     * 执行元数据
     */
    private ExecutionMetadata metadata;

    /**
     * 输出类型枚举
     */
    public enum OutputType {
        REPORT,      // 完整分析报告
        CONVERSATION, // 普通对话
        ERROR,       // 错误响应
        TOOL_RESULT  // 工具调用结果
    }

    /**
     * 结构化报告
     */
    @Data
    public static class StructuredReport {
        /**
         * 报告标题
         */
        private String title;

        /**
         * 活跃异常清单
         */
        @JsonProperty("active_alerts")
        private List<AlertInfo> activeAlerts;

        /**
         * 根因分析列表
         */
        @JsonProperty("root_cause_analyses")
        private List<RootCauseAnalysis> rootCauseAnalyses;

        /**
         * 处理方案列表
         */
        @JsonProperty("treatment_plans")
        private List<TreatmentPlan> treatmentPlans;

        /**
         * 结论
         */
        private Conclusion conclusion;
    }

    /**
     * 告警信息
     */
    @Data
    public static class AlertInfo {
        @JsonProperty("alert_type")
        private String alertType;

        private String severity;

        @JsonProperty("experiment_id")
        private String experimentId;

        @JsonProperty("parameter_name")
        private String parameterName;

        @JsonProperty("measured_value")
        private Double measuredValue;

        @JsonProperty("threshold_value")
        private Double thresholdValue;

        private String unit;

        private String status;

        private String duration;
    }

    /**
     * 根因分析
     */
    @Data
    public static class RootCauseAnalysis {
        /**
         * 异常类型
         */
        @JsonProperty("alert_type")
        private String alertType;

        /**
         * 异常详情
         */
        private AlertDetail detail;

        /**
         * 症状描述
         */
        private String symptoms;

        /**
         * 证据
         */
        private String evidence;

        /**
         * 根因结论
         */
        private String conclusion;
    }

    /**
     * 告警详情
     */
    @Data
    public static class AlertDetail {
        private String severity;

        @JsonProperty("experiment_id")
        private String experimentId;

        @JsonProperty("parameter_name")
        private String parameterName;

        @JsonProperty("measured_threshold")
        private String measuredThreshold;

        private String duration;
    }

    /**
     * 处理方案
     */
    @Data
    public static class TreatmentPlan {
        /**
         * 异常类型
         */
        @JsonProperty("alert_type")
        private String alertType;

        /**
         * 已执行步骤
         */
        @JsonProperty("executed_steps")
        private List<String> executedSteps;

        /**
         * 处理建议
         */
        private String recommendation;

        /**
         * 预期效果
         */
        @JsonProperty("expected_effect")
        private String expectedEffect;
    }

    /**
     * 结论
     */
    @Data
    public static class Conclusion {
        /**
         * 整体评估
         */
        @JsonProperty("overall_assessment")
        private String overallAssessment;

        /**
         * 关键发现
         */
        @JsonProperty("key_findings")
        private List<String> keyFindings;

        /**
         * 后续建议
         */
        private List<String> recommendations;

        /**
         * 安全风险评估
         */
        @JsonProperty("safety_risk_assessment")
        private String safetyRiskAssessment;
    }

    /**
     * 错误信息
     */
    @Data
    public static class ErrorInfo {
        private String code;
        private String message;
        private String details;
    }

    /**
     * 执行元数据
     */
    @Data
    public static class ExecutionMetadata {
        /**
         * 执行时间（毫秒）
         */
        @JsonProperty("execution_time_ms")
        private Long executionTimeMs;

        /**
         * 工具调用次数
         */
        @JsonProperty("tool_invocations")
        private Integer toolInvocations;

        /**
         * Agent类型
         */
        @JsonProperty("agent_type")
        private String agentType;

        /**
         * 会话ID
         */
        @JsonProperty("session_id")
        private String sessionId;
    }
}
