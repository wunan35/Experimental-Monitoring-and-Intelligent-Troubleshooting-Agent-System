package org.example.exception;

/**
 * 模型调用异常
 * 当LLM模型调用失败时抛出
 */
public class ModelInvocationException extends AgentException {

    private final String modelName;

    public ModelInvocationException(String message) {
        super("MODEL_INVOCATION_ERROR", message);
        this.modelName = "unknown";
    }

    public ModelInvocationException(String modelName, String message) {
        super("MODEL_INVOCATION_ERROR",
                String.format("模型 [%s] 调用失败: %s", modelName, message),
                "unknown",
                "model_invocation");
        this.modelName = modelName;
    }

    public ModelInvocationException(String modelName, String message, Throwable cause) {
        super("MODEL_INVOCATION_ERROR",
                String.format("模型 [%s] 调用失败: %s", modelName, message),
                "unknown",
                "model_invocation",
                cause);
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }
}
