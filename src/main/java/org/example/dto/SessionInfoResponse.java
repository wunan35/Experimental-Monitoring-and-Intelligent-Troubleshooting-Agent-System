package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 会话信息响应
 * 用于返回会话的状态信息
 */
@Getter
@Setter
public class SessionInfoResponse {
    private String sessionId;
    private int messagePairCount;
    private long createTime;
}
