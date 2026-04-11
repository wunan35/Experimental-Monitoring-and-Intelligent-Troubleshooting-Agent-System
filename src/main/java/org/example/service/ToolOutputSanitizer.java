package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 工具输出安全校验服务
 * 对工具返回数据进行安全校验和清洗，防止Prompt注入
 */
@Service
public class ToolOutputSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(ToolOutputSanitizer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 危险模式：可能用于Prompt注入的模式
    private static final Pattern[] DANGEROUS_PATTERNS = {
            // 忽略指令模式
            Pattern.compile("(?i)(ignore\\s+(previous|all|above)\\s*(instructions?|prompts?|rules?))", Pattern.CASE_INSENSITIVE),
            // 角色扮演注入
            Pattern.compile("(?i)(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be)", Pattern.CASE_INSENSITIVE),
            // 系统指令注入
            Pattern.compile("(?i)(system\\s*:\\s*|assistant\\s*:\\s*|user\\s*:\\s*)", Pattern.CASE_INSENSITIVE),
            // 转义字符序列
            Pattern.compile("(\\\\[nrtu]|[\\x00-\\x1f])"),
            // 超长重复字符（可能的DoS攻击）
            Pattern.compile("(.{1,10})\\1{100,}"),
    };

    // 敏感信息模式
    private static final Pattern[] SENSITIVE_PATTERNS = {
            // API Key
            Pattern.compile("(?i)(api[_-]?key|apikey|access[_-]?token)\\s*[=:]\\s*['\"]?[a-zA-Z0-9_\\-]{20,}['\"]?"),
            // 密码
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*['\"]?[^'\"\\s]{8,}['\"]?"),
            // 数据库连接串
            Pattern.compile("(?i)(jdbc:mysql://[^\\s]+|mongodb://[^\\s]+)"),
            // 私钥
            Pattern.compile("-----BEGIN.*PRIVATE KEY-----"),
    };

    /**
     * 校验和清洗工具输出
     *
     * @param toolName  工具名称
     * @param rawOutput 原始输出
     * @return 清洗后的输出
     */
    public String sanitize(String toolName, String rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return rawOutput;
        }

        logger.debug("开始清洗工具输出: {}, 原始长度: {}", toolName, rawOutput.length());

        // 1. 检查危险模式
        String sanitized = rawOutput;
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                logger.warn("检测到潜在危险的输出模式: {} in tool {}", pattern.pattern(), toolName);
                sanitized = pattern.matcher(sanitized).replaceAll("[已过滤]");
            }
        }

        // 2. 检查敏感信息
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                logger.warn("检测到敏感信息模式: {} in tool {}", pattern.pattern(), toolName);
                sanitized = pattern.matcher(sanitized).replaceAll("[敏感信息已脱敏]");
            }
        }

        // 3. 限制输出长度（防止上下文溢出）
        int maxLength = 50000;
        if (sanitized.length() > maxLength) {
            logger.warn("工具输出过长，进行截断: {} -> {}", toolName, sanitized.length());
            sanitized = sanitized.substring(0, maxLength) + "\n... [输出已截断]";
        }

        // 4. 移除控制字符（保留换行和制表符）
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", "");

        // 5. 检测JSON格式是否合法（如果看起来像JSON）
        if (sanitized.trim().startsWith("{") || sanitized.trim().startsWith("[")) {
            sanitized = validateJson(sanitized);
        }

        if (!sanitized.equals(rawOutput)) {
            logger.info("工具输出已清洗: {}", toolName);
        }

        return sanitized;
    }

    /**
     * 验证JSON格式
     */
    private String validateJson(String json) {
        try {
            // 尝试解析JSON以验证格式
            objectMapper.readTree(json);
            return json;
        } catch (Exception e) {
            logger.warn("JSON格式验证失败，尝试修复: {}", e.getMessage());
            // 如果解析失败，返回原始字符串（不包含无效JSON）
            return json;
        }
    }

    /**
     * 检查输出是否安全
     *
     * @param output 输出内容
     * @return 是否安全
     */
    public boolean isSafe(String output) {
        if (output == null || output.isEmpty()) {
            return true;
        }

        // 检查所有危险模式
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return false;
            }
        }

        // 检查敏感信息模式
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 转义特殊字符（用于安全地嵌入Prompt）
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    public String escapeForPrompt(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
