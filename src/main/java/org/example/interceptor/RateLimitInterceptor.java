package org.example.interceptor;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * API限流拦截器
 * 基于IP和接口路径进行限流
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter chatRateLimiter;
    private final RateLimiter aiopsRateLimiter;
    private final RateLimiter uploadRateLimiter;

    // 客户端IP级别的限流计数器
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> clientRequestCounts = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitInterceptor(
            @Qualifier("chatRateLimiter") RateLimiter chatRateLimiter,
            @Qualifier("aiopsRateLimiter") RateLimiter aiopsRateLimiter,
            @Qualifier("uploadRateLimiter") RateLimiter uploadRateLimiter) {
        this.chatRateLimiter = chatRateLimiter;
        this.aiopsRateLimiter = aiopsRateLimiter;
        this.uploadRateLimiter = uploadRateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String clientIp = getClientIp(request);

        try {
            RateLimiter rateLimiter = getRateLimiter(requestURI);
            if (rateLimiter != null) {
                // 使用限流器检查权限
                return rateLimiter.executeSupplier(() -> {
                    // 记录客户端请求
                    recordClientRequest(clientIp, requestURI);
                    return true;
                });
            }
        } catch (RequestNotPermitted e) {
            logger.warn("请求被限流 - IP: {}, URI: {}", clientIp, requestURI);
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"请求过于频繁，请稍后重试\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }

        return true;
    }

    /**
     * 根据请求URI获取对应的限流器
     */
    private RateLimiter getRateLimiter(String requestURI) {
        if (requestURI.startsWith("/api/chat") || requestURI.startsWith("/api/v1/chat")) {
            return chatRateLimiter;
        } else if (requestURI.startsWith("/api/ai_ops") || requestURI.startsWith("/api/v1/ai_ops")) {
            return aiopsRateLimiter;
        } else if (requestURI.startsWith("/api/upload") || requestURI.startsWith("/api/v1/upload")) {
            return uploadRateLimiter;
        }
        return null;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 对于多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 记录客户端请求（用于监控）
     */
    private void recordClientRequest(String clientIp, String requestURI) {
        clientRequestCounts.computeIfAbsent(clientIp, k -> new ConcurrentHashMap<>())
                .merge(requestURI, 1, Integer::sum);
    }

    /**
     * 获取客户端请求统计
     */
    public ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> getClientRequestCounts() {
        return clientRequestCounts;
    }
}
