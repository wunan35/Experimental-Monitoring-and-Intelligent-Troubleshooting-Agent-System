package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天请求
 * 用于接收前端发送的对话请求
 */
@Getter
@Setter
@Schema(description = "对话请求参数")
public class ChatRequest {

    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    @Schema(description = "会话ID，用于关联多轮对话历史", example = "session-12345")
    private String id;

    @JsonProperty(value = "Question")
    @JsonAlias({"question", "QUESTION"})
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 4000, message = "问题内容不能超过4000个字符")
    @Schema(description = "用户问题内容", example = "当前有哪些实验告警？", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;
}
