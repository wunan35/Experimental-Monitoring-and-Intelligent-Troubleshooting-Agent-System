package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.service.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 追踪拦截器
 * 为每个请求生成TraceId，实现链路追踪
 */
@Component
public class TraceInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TraceInterceptor.class);

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 尝试从请求头获取TraceId，否则生成新的
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.init();
        } else {
            TraceContext.setTraceId(traceId);
        }

        // 设置响应头
        response.setHeader(TRACE_ID_HEADER, traceId);

        logger.debug("Request started: {} {} [traceId={}]",
            request.getMethod(), request.getRequestURI(), traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        logger.debug("Request completed: {} {} - status {} [traceId={}]",
            request.getMethod(), request.getRequestURI(), response.getStatus(), TraceContext.getTraceId());
        TraceContext.clear();
    }
}
