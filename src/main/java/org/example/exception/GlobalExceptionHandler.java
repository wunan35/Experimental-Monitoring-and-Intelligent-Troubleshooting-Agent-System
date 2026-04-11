package org.example.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用程序中的异常，返回标准格式的错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== Agent相关异常 ====================

    /**
     * 处理Agent异常基类
     */
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String, Object>> handleAgentException(AgentException e) {
        logger.error("Agent异常 - Code: {}, Agent: {}, Phase: {}, Message: {}",
                e.getErrorCode(), e.getAgentName(), e.getPhase(), e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getErrorCode());
        response.put("message", e.getMessage());
        response.put("agent", e.getAgentName());
        response.put("phase", e.getPhase());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理Agent超时异常
     */
    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAgentTimeoutException(AgentTimeoutException e) {
        logger.error("Agent超时 - Agent: {}, Message: {}", e.getAgentName(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getErrorCode());
        response.put("message", "Agent执行超时，请稍后重试或简化请求");
        response.put("agent", e.getAgentName());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    /**
     * 处理工具调用异常
     */
    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleToolExecutionException(ToolExecutionException e) {
        logger.error("工具调用异常 - Tool: {}, Agent: {}, Message: {}",
                e.getToolName(), e.getAgentName(), e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getErrorCode());
        response.put("message", String.format("工具 [%s] 执行失败，请检查参数或稍后重试", e.getToolName()));
        response.put("tool", e.getToolName());
        response.put("agent", e.getAgentName());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理模型调用异常
     */
    @ExceptionHandler(ModelInvocationException.class)
    public ResponseEntity<Map<String, Object>> handleModelInvocationException(ModelInvocationException e) {
        logger.error("模型调用异常 - Model: {}, Message: {}", e.getModelName(), e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getErrorCode());
        response.put("message", "AI模型调用失败，请稍后重试");
        response.put("model", e.getModelName());
        response.put("timestamp", System.currentTimeMillis());

        // 模型调用失败可能是服务不可用
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * 处理会话异常
     */
    @ExceptionHandler(SessionException.class)
    public ResponseEntity<Map<String, Object>> handleSessionException(SessionException e) {
        logger.error("会话异常 - SessionId: {}, Message: {}", e.getSessionId(), e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getErrorCode());
        response.put("message", e.getMessage());
        response.put("sessionId", e.getSessionId());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== 业务异常 ====================

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getCode());
        response.put("message", e.getMessage());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException e) {
        logger.warn("参数校验异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", e.getCode());
        response.put("message", e.getMessage());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== JSR-303 校验异常 ====================

    /**
     * 处理 @Valid 校验失败异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        logger.warn("参数校验失败: {}", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "VALIDATION_ERROR");
        response.put("message", "参数校验失败: " + errors);
        response.put("errors", errors);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException e) {
        String errors = e.getConstraintViolations().stream()
                .map(violation -> String.format("%s: %s",
                        violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));

        logger.warn("约束违反: {}", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "CONSTRAINT_VIOLATION");
        response.put("message", "约束违反: " + errors);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数 '%s' 类型不正确，期望类型: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");

        logger.warn("参数类型不匹配: {}", message);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "TYPE_MISMATCH");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== 通用异常 ====================

    /**
     * 处理IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "ILLEGAL_ARGUMENT");
        response.put("message", e.getMessage());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "RUNTIME_ERROR");
        response.put("message", "系统内部错误，请稍后重试");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理IOException
     */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(java.io.IOException e) {
        logger.error("IO异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "IO_ERROR");
        response.put("message", "系统IO错误，请稍后重试");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        logger.error("未处理的异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", "UNKNOWN_ERROR");
        response.put("message", "系统内部错误，请稍后重试");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
