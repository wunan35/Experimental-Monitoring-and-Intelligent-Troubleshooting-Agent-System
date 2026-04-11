package org.example.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全审计服务
 * 记录和监控安全相关事件
 */
@Service
public class SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");

    /**
     * 安全事件统计
     */
    private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();

    /**
     * 记录安全事件
     *
     * @param eventType    事件类型
     * @param toolName     工具名称
     * @param details      详细信息
     * @param severity     严重程度 (INFO, WARN, ERROR, CRITICAL)
     */
    public void logSecurityEvent(String eventType, String toolName, String details, String severity) {
        // 更新统计
        String countKey = eventType + ":" + toolName;
        eventCounts.computeIfAbsent(countKey, k -> new AtomicLong(0)).incrementAndGet();

        // 构建审计日志
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setTimestamp(Instant.now().toString());
        event.setEventType(eventType);
        event.setToolName(toolName);
        event.setDetails(details);
        event.setSeverity(severity);

        // 根据严重程度选择日志级别
        switch (severity) {
            case "CRITICAL":
                securityLogger.error("SECURITY_EVENT: {}", event.toJson());
                break;
            case "ERROR":
                securityLogger.error("SECURITY_EVENT: {}", event.toJson());
                break;
            case "WARN":
                securityLogger.warn("SECURITY_EVENT: {}", event.toJson());
                break;
            default:
                securityLogger.info("SECURITY_EVENT: {}", event.toJson());
        }

        // 同时输出到普通日志
        logger.info("安全审计事件: type={}, tool={}, severity={}", eventType, toolName, severity);
    }

    /**
     * 记录Prompt注入检测事件
     */
    public void logPromptInjectionDetected(String toolName, String patternType, String matchedText) {
        String details = String.format("检测到Prompt注入模式: pattern=%s, matched=%s",
                patternType,
                matchedText.length() > 50 ? matchedText.substring(0, 50) + "..." : matchedText);

        logSecurityEvent("PROMPT_INJECTION_DETECTED", toolName, details, "WARN");
    }

    /**
     * 记录工具输入校验失败事件
     */
    public void logInputValidationFailed(String toolName, String paramName, String reason) {
        String details = String.format("参数校验失败: param=%s, reason=%s", paramName, reason);
        logSecurityEvent("INPUT_VALIDATION_FAILED", toolName, details, "WARN");
    }

    /**
     * 记录输出清洗事件
     */
    public void logOutputSanitized(String toolName, int originalLength, int sanitizedLength) {
        String details = String.format("输出已清洗: original=%d, sanitized=%d, reduced=%d",
                originalLength, sanitizedLength, originalLength - sanitizedLength);
        logSecurityEvent("OUTPUT_SANITIZED", toolName, details, "INFO");
    }

    /**
     * 记录异常访问事件
     */
    public void logAnomalousAccess(String sessionId, String ipAddress, String userAgent, String reason) {
        String details = String.format("异常访问: ip=%s, ua=%s, reason=%s", ipAddress, userAgent, reason);
        logSecurityEvent("ANOMALOUS_ACCESS", sessionId, details, "WARN");
    }

    /**
     * 获取安全事件统计
     */
    public Map<String, Long> getEventStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        eventCounts.forEach((key, count) -> stats.put(key, count.get()));
        return stats;
    }

    /**
     * 重置统计
     */
    public void resetStatistics() {
        eventCounts.clear();
    }

    /**
     * 安全审计事件
     */
    public static class SecurityAuditEvent {
        private String timestamp;
        private String eventType;
        private String toolName;
        private String details;
        private String severity;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String toJson() {
            return String.format(
                    "{\"timestamp\":\"%s\",\"eventType\":\"%s\",\"toolName\":\"%s\",\"details\":\"%s\",\"severity\":\"%s\"}",
                    escapeJson(timestamp), escapeJson(eventType), escapeJson(toolName),
                    escapeJson(details), escapeJson(severity));
        }

        private String escapeJson(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
