package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent执行结果结构化输出
 * 定义标准的Agent响应格式，便于前端解析和展示
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentReportResponse {

    /**
     * 报告ID
     */
    @JsonProperty("report_id")
    private String reportId;

    /**
     * 报告类型
     */
    @JsonProperty("report_type")
    private String reportType;

    /**
     * 报告标题
     */
    @JsonProperty("title")
    private String title;

    /**
     * 执行时间戳
     */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * 执行状态
     */
    @JsonProperty("status")
    private ExecutionStatus status;

    /**
     * 总体摘要
     */
    @JsonProperty("summary")
    private String summary;

    /**
     * 告警分析章节
     */
    @JsonProperty("alerts_analysis")
    private AlertsAnalysis alertsAnalysis;

    /**
     * 根因分析章节
     */
    @JsonProperty("root_cause_analysis")
    private RootCauseAnalysis rootCauseAnalysis;

    /**
     * 处理建议章节
     */
    @JsonProperty("recommendations")
    private List<Recommendation> recommendations;

    /**
     * 执行步骤记录
     */
    @JsonProperty("execution_steps")
    private List<ExecutionStep> executionSteps;

    /**
     * 使用的工具列表
     */
    @JsonProperty("tools_used")
    private List<String> toolsUsed;

    /**
     * 错误信息（如果有）
     */
    @JsonProperty("error")
    private ErrorInfo error;

    /**
     * 元数据
     */
    @JsonProperty("metadata")
    private Metadata metadata;

    // ==================== 内部类定义 ====================

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        @JsonProperty("success")
        SUCCESS,
        @JsonProperty("partial_success")
        PARTIAL_SUCCESS,
        @JsonProperty("failed")
        FAILED,
        @JsonProperty("timeout")
        TIMEOUT
    }

    /**
     * 告警分析
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlertsAnalysis {
        @JsonProperty("total_alerts")
        private int totalAlerts;

        @JsonProperty("by_severity")
        private SeverityCount bySeverity;

        @JsonProperty("by_type")
        private List<TypeCount> byType;

        @JsonProperty("alert_details")
        private List<AlertDetail> alertDetails;

        // Getters and Setters
        public int getTotalAlerts() {
            return totalAlerts;
        }

        public void setTotalAlerts(int totalAlerts) {
            this.totalAlerts = totalAlerts;
        }

        public SeverityCount getBySeverity() {
            return bySeverity;
        }

        public void setBySeverity(SeverityCount bySeverity) {
            this.bySeverity = bySeverity;
        }

        public List<TypeCount> getByType() {
            return byType;
        }

        public void setByType(List<TypeCount> byType) {
            this.byType = byType;
        }

        public List<AlertDetail> getAlertDetails() {
            return alertDetails;
        }

        public void setAlertDetails(List<AlertDetail> alertDetails) {
            this.alertDetails = alertDetails;
        }
    }

    /**
     * 严重程度统计
     */
    public static class SeverityCount {
        @JsonProperty("critical")
        private int critical;
        @JsonProperty("serious")
        private int serious;
        @JsonProperty("warning")
        private int warning;

        public int getCritical() {
            return critical;
        }

        public void setCritical(int critical) {
            this.critical = critical;
        }

        public int getSerious() {
            return serious;
        }

        public void setSerious(int serious) {
            this.serious = serious;
        }

        public int getWarning() {
            return warning;
        }

        public void setWarning(int warning) {
            this.warning = warning;
        }
    }

    /**
     * 类型统计
     */
    public static class TypeCount {
        @JsonProperty("type")
        private String type;
        @JsonProperty("count")
        private int count;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    /**
     * 告警详情
     */
    public static class AlertDetail {
        @JsonProperty("alert_id")
        private String alertId;
        @JsonProperty("alert_type")
        private String alertType;
        @JsonProperty("experiment_id")
        private String experimentId;
        @JsonProperty("severity")
        private String severity;
        @JsonProperty("description")
        private String description;
        @JsonProperty("location")
        private String location;
        @JsonProperty("active_at")
        private String activeAt;
        @JsonProperty("duration")
        private String duration;

        // Getters and Setters
        public String getAlertId() {
            return alertId;
        }

        public void setAlertId(String alertId) {
            this.alertId = alertId;
        }

        public String getAlertType() {
            return alertType;
        }

        public void setAlertType(String alertType) {
            this.alertType = alertType;
        }

        public String getExperimentId() {
            return experimentId;
        }

        public void setExperimentId(String experimentId) {
            this.experimentId = experimentId;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getActiveAt() {
            return activeAt;
        }

        public void setActiveAt(String activeAt) {
            this.activeAt = activeAt;
        }

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }
    }

    /**
     * 根因分析
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RootCauseAnalysis {
        @JsonProperty("primary_cause")
        private String primaryCause;

        @JsonProperty("contributing_factors")
        private List<String> contributingFactors;

        @JsonProperty("evidence")
        private List<Evidence> evidence;

        @JsonProperty("analysis_process")
        private String analysisProcess;

        // Getters and Setters
        public String getPrimaryCause() {
            return primaryCause;
        }

        public void setPrimaryCause(String primaryCause) {
            this.primaryCause = primaryCause;
        }

        public List<String> getContributingFactors() {
            return contributingFactors;
        }

        public void setContributingFactors(List<String> contributingFactors) {
            this.contributingFactors = contributingFactors;
        }

        public List<Evidence> getEvidence() {
            return evidence;
        }

        public void setEvidence(List<Evidence> evidence) {
            this.evidence = evidence;
        }

        public String getAnalysisProcess() {
            return analysisProcess;
        }

        public void setAnalysisProcess(String analysisProcess) {
            this.analysisProcess = analysisProcess;
        }
    }

    /**
     * 证据
     */
    public static class Evidence {
        @JsonProperty("source")
        private String source;
        @JsonProperty("type")
        private String type;
        @JsonProperty("content")
        private String content;
        @JsonProperty("relevance")
        private String relevance;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getRelevance() {
            return relevance;
        }

        public void setRelevance(String relevance) {
            this.relevance = relevance;
        }
    }

    /**
     * 处理建议
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Recommendation {
        @JsonProperty("priority")
        private int priority;
        @JsonProperty("category")
        private String category;
        @JsonProperty("action")
        private String action;
        @JsonProperty("rationale")
        private String rationale;
        @JsonProperty("estimated_effort")
        private String estimatedEffort;

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getRationale() {
            return rationale;
        }

        public void setRationale(String rationale) {
            this.rationale = rationale;
        }

        public String getEstimatedEffort() {
            return estimatedEffort;
        }

        public void setEstimatedEffort(String estimatedEffort) {
            this.estimatedEffort = estimatedEffort;
        }
    }

    /**
     * 执行步骤
     */
    public static class ExecutionStep {
        @JsonProperty("step_number")
        private int stepNumber;
        @JsonProperty("action")
        private String action;
        @JsonProperty("tool_used")
        private String toolUsed;
        @JsonProperty("result")
        private String result;
        @JsonProperty("status")
        private String status;
        @JsonProperty("duration_ms")
        private long durationMs;

        public int getStepNumber() {
            return stepNumber;
        }

        public void setStepNumber(int stepNumber) {
            this.stepNumber = stepNumber;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getToolUsed() {
            return toolUsed;
        }

        public void setToolUsed(String toolUsed) {
            this.toolUsed = toolUsed;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    /**
     * 错误信息
     */
    public static class ErrorInfo {
        @JsonProperty("code")
        private String code;
        @JsonProperty("message")
        private String message;
        @JsonProperty("phase")
        private String phase;
        @JsonProperty("retryable")
        private boolean retryable;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public void setRetryable(boolean retryable) {
            this.retryable = retryable;
        }
    }

    /**
     * 元数据
     */
    public static class Metadata {
        @JsonProperty("agent_version")
        private String agentVersion;
        @JsonProperty("model_name")
        private String modelName;
        @JsonProperty("execution_time_ms")
        private long executionTimeMs;
        @JsonProperty("token_usage")
        private TokenUsage tokenUsage;

        public String getAgentVersion() {
            return agentVersion;
        }

        public void setAgentVersion(String agentVersion) {
            this.agentVersion = agentVersion;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public void setExecutionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
        }

        public TokenUsage getTokenUsage() {
            return tokenUsage;
        }

        public void setTokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
        }
    }

    /**
     * Token使用量
     */
    public static class TokenUsage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }

    // ==================== Builder模式 ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentReportResponse response = new AgentReportResponse();

        public Builder() {
            response.timestamp = Instant.now().toString();
            response.executionSteps = new ArrayList<>();
            response.toolsUsed = new ArrayList<>();
            response.recommendations = new ArrayList<>();
        }

        public Builder reportId(String reportId) {
            response.reportId = reportId;
            return this;
        }

        public Builder reportType(String reportType) {
            response.reportType = reportType;
            return this;
        }

        public Builder title(String title) {
            response.title = title;
            return this;
        }

        public Builder status(ExecutionStatus status) {
            response.status = status;
            return this;
        }

        public Builder summary(String summary) {
            response.summary = summary;
            return this;
        }

        public Builder alertsAnalysis(AlertsAnalysis alertsAnalysis) {
            response.alertsAnalysis = alertsAnalysis;
            return this;
        }

        public Builder rootCauseAnalysis(RootCauseAnalysis rootCauseAnalysis) {
            response.rootCauseAnalysis = rootCauseAnalysis;
            return this;
        }

        public Builder addRecommendation(Recommendation recommendation) {
            response.recommendations.add(recommendation);
            return this;
        }

        public Builder addExecutionStep(ExecutionStep step) {
            response.executionSteps.add(step);
            return this;
        }

        public Builder addToolUsed(String toolName) {
            response.toolsUsed.add(toolName);
            return this;
        }

        public Builder error(ErrorInfo error) {
            response.error = error;
            return this;
        }

        public Builder metadata(Metadata metadata) {
            response.metadata = metadata;
            return this;
        }

        public AgentReportResponse build() {
            return response;
        }
    }

    // ==================== Getters and Setters ====================

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public AlertsAnalysis getAlertsAnalysis() {
        return alertsAnalysis;
    }

    public void setAlertsAnalysis(AlertsAnalysis alertsAnalysis) {
        this.alertsAnalysis = alertsAnalysis;
    }

    public RootCauseAnalysis getRootCauseAnalysis() {
        return rootCauseAnalysis;
    }

    public void setRootCauseAnalysis(RootCauseAnalysis rootCauseAnalysis) {
        this.rootCauseAnalysis = rootCauseAnalysis;
    }

    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }

    public List<ExecutionStep> getExecutionSteps() {
        return executionSteps;
    }

    public void setExecutionSteps(List<ExecutionStep> executionSteps) {
        this.executionSteps = executionSteps;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
