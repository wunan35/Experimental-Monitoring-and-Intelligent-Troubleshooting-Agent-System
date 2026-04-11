package org.example.service.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 追踪上下文
 * 管理请求级别的TraceId，用于链路追踪
 */
public class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SESSION_ID_KEY = "sessionId";

    private static final ThreadLocal<String> traceIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();

    /**
     * 初始化追踪上下文
     * 自动生成新的TraceId
     */
    public static String init() {
        String traceId = generateTraceId();
        traceIdHolder.set(traceId);
        MDC.put(TRACE_ID_KEY, traceId);
        return traceId;
    }

    /**
     * 初始化追踪上下文（带sessionId）
     */
    public static String init(String sessionId) {
        String traceId = init();
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionIdHolder.set(sessionId);
            MDC.put(SESSION_ID_KEY, maskSessionId(sessionId));
        }
        return traceId;
    }

    /**
     * 生成16位TraceId
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取当前TraceId
     */
    public static String getTraceId() {
        String traceId = traceIdHolder.get();
        if (traceId == null) {
            traceId = MDC.get(TRACE_ID_KEY);
        }
        return traceId;
    }

    /**
     * 获取当前SessionId
     */
    public static String getSessionId() {
        return sessionIdHolder.get();
    }

    /**
     * 设置TraceId（用于异步线程传递）
     */
    public static void setTraceId(String traceId) {
        traceIdHolder.set(traceId);
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 设置SessionId（用于异步线程传递）
     */
    public static void setSessionId(String sessionId) {
        sessionIdHolder.set(sessionId);
        if (sessionId != null) {
            MDC.put(SESSION_ID_KEY, maskSessionId(sessionId));
        }
    }

    /**
     * 清理追踪上下文
     * 必须在请求结束时调用，防止内存泄漏
     */
    public static void clear() {
        traceIdHolder.remove();
        sessionIdHolder.remove();
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SESSION_ID_KEY);
    }

    /**
     * 脱敏sessionId
     */
    private static String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) {
            return sessionId;
        }
        return sessionId.substring(0, 4) + "..." + sessionId.substring(sessionId.length() - 4);
    }
}
