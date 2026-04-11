package org.example.exception;

/**
 * 参数校验异常
 * 用于表示输入参数验证失败
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, cause);
    }

    /**
     * 支持自定义错误码的构造函数
     * @param code 错误码
     * @param message 错误消息
     */
    public ValidationException(String code, String message) {
        super(code, message);
    }

    /**
     * 支持自定义错误码和原因的构造函数
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因
     */
    public ValidationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
