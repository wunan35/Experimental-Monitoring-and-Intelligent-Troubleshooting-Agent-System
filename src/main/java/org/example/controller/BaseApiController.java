package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.SessionData;
import org.example.dto.SseMessage;
import org.example.service.storage.SessionStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API控制器基类
 * 提供公共方法，减少重复代码
 */
public abstract class BaseApiController {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected SessionStorageService sessionStorageService;

    /**
     * 将SessionData转换为历史消息格式
     * 保持与现有buildSystemPrompt方法的兼容性
     */
    protected List<Map<String, String>> convertToHistoryFormat(SessionData session) {
        List<Map<String, String>> history = new ArrayList<>();
        for (SessionData.MessagePair pair : session.getMessageHistory()) {
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", pair.getUserMessage());
            history.add(userMsg);

            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", pair.getAssistantMessage());
            history.add(assistantMsg);
        }
        return history;
    }

    /**
     * 发送SSE错误消息
     */
    protected void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(SseMessage.error(message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            logger.error("发送SSE错误消息失败", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 发送SSE内容消息
     */
    protected void sendSseContent(SseEmitter emitter, String content) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(SseMessage.content(content), MediaType.APPLICATION_JSON));
    }

    /**
     * 发送SSE完成消息
     */
    protected void sendSseDone(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(SseMessage.done(), MediaType.APPLICATION_JSON));
        emitter.complete();
    }

    /**
     * 构建成功响应
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 构建错误响应
     */
    protected <T> ResponseEntity<ApiResponse<T>> error(String message) {
        return ResponseEntity.ok(ApiResponse.error(message));
    }
}
