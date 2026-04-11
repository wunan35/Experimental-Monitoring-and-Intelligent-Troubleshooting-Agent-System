package org.example.exception;

/**
 * Agent异常基类
 * 所有Agent相关异常的父类
 */
public class AgentException extends RuntimeException {
    private final String errorCode;
    private final String agentName;
    private final String phase;

    public AgentException(String message) {
        super(message);
        this.errorCode = "AGENT_ERROR";
        this.agentName = "unknown";
        this.phase = "unknown";
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AGENT_ERROR";
        this.agentName = "unknown";
        this.phase = "unknown";
    }

    public AgentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.agentName = "unknown";
        this.phase = "unknown";
    }

    public AgentException(String errorCode, String message, String agentName, String phase) {
        super(message);
        this.errorCode = errorCode;
        this.agentName = agentName;
        this.phase = phase;
    }

    public AgentException(String errorCode, String message, String agentName, String phase, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.agentName = agentName;
        this.phase = phase;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getPhase() {
        return phase;
    }
}
