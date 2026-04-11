package org.example.exception;

/**
 * Agent超时异常
 * 当Agent执行超过允许的最大时间时抛出
 */
public class AgentTimeoutException extends AgentException {

    public AgentTimeoutException(String agentName, long timeoutMs) {
        super("AGENT_TIMEOUT",
                String.format("Agent [%s] 执行超时，超过 %d ms", agentName, timeoutMs),
                agentName,
                "execution");
    }

    public AgentTimeoutException(String agentName, long timeoutMs, Throwable cause) {
        super("AGENT_TIMEOUT",
                String.format("Agent [%s] 执行超时，超过 %d ms", agentName, timeoutMs),
                agentName,
                "execution",
                cause);
    }
}
