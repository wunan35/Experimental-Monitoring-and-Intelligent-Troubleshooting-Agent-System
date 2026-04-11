package org.example.exception;

/**
 * 会话异常
 * 当会话操作失败时抛出
 */
public class SessionException extends AgentException {

    private final String sessionId;

    public SessionException(String sessionId, String message) {
        super("SESSION_ERROR",
                String.format("会话 [%s] 操作失败: %s", sessionId, message),
                "unknown",
                "session");
        this.sessionId = sessionId;
    }

    public SessionException(String sessionId, String message, Throwable cause) {
        super("SESSION_ERROR",
                String.format("会话 [%s] 操作失败: %s", sessionId, message),
                "unknown",
                "session",
                cause);
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
