package org.example.exception;

/**
 * 工具调用异常
 * 当Agent工具调用失败时抛出
 */
public class ToolExecutionException extends AgentException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super("TOOL_EXECUTION_ERROR",
                String.format("工具 [%s] 执行失败: %s", toolName, message),
                "unknown",
                "tool_execution");
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super("TOOL_EXECUTION_ERROR",
                String.format("工具 [%s] 执行失败: %s", toolName, message),
                "unknown",
                "tool_execution",
                cause);
        this.toolName = toolName;
    }

    public ToolExecutionException(String agentName, String toolName, String message, Throwable cause) {
        super("TOOL_EXECUTION_ERROR",
                String.format("Agent [%s] 调用工具 [%s] 失败: %s", agentName, toolName, message),
                agentName,
                "tool_execution",
                cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
