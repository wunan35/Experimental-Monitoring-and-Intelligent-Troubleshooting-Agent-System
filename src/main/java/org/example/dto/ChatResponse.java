package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一聊天响应格式
 * 适用于所有普通返回模式的对话接口
 */
@Getter
@Setter
@Schema(description = "对话响应结果")
public class ChatResponse {

    @Schema(description = "是否成功", example = "true")
    private boolean success;

    @Schema(description = "AI回答内容", example = "当前有3个活动告警...")
    private String answer;

    @Schema(description = "错误消息（失败时返回）", example = "对话处理失败")
    private String errorMessage;

    /**
     * 创建成功响应
     *
     * @param answer AI 回答内容
     * @return 成功的 ChatResponse
     */
    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    /**
     * 创建错误响应
     *
     * @param errorMessage 错误消息
     * @return 错误的 ChatResponse
     */
    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
