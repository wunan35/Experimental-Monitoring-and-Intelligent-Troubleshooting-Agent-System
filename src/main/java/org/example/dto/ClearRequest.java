package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 清空会话请求
 * 用于清空指定会话的历史记录
 */
@Getter
@Setter
public class ClearRequest {

    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    @NotBlank(message = "会话ID不能为空")
    private String id;
}
