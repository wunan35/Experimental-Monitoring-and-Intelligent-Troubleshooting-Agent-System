package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一 SSE 流式消息格式
 * 适用于所有 SSE 流式返回模式的对话接口
 */
@Getter
@Setter
@Schema(description = "SSE流式消息格式")
public class SseMessage {

    /**
     * 消息类型
     * content: 内容块
     * error: 错误
     * done: 完成
     */
    @Schema(description = "消息类型", example = "content", allowableValues = {"content", "error", "done"})
    private String type;

    @Schema(description = "消息数据", example = "当前有3个活动告警...")
    private String data;

    /**
     * 创建内容消息
     *
     * @param data 内容数据
     * @return 内容类型的 SseMessage
     */
    public static SseMessage content(String data) {
        SseMessage message = new SseMessage();
        message.setType("content");
        message.setData(data);
        return message;
    }

    /**
     * 创建错误消息
     *
     * @param errorMessage 错误消息
     * @return 错误类型的 SseMessage
     */
    public static SseMessage error(String errorMessage) {
        SseMessage message = new SseMessage();
        message.setType("error");
        message.setData(errorMessage);
        return message;
    }

    /**
     * 创建完成消息
     *
     * @return 完成类型的 SseMessage
     */
    public static SseMessage done() {
        SseMessage message = new SseMessage();
        message.setType("done");
        message.setData(null);
        return message;
    }
}
