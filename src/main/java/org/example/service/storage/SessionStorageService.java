package org.example.service.storage;

import org.example.dto.SessionData;

import java.util.Optional;

/**
 * 会话存储服务接口
 * 支持本地内存和Redis两种实现
 */
public interface SessionStorageService {

    /**
     * 获取或创建会话
     *
     * @param sessionId 会话ID，如果为空则创建新会话
     * @return 会话数据
     */
    SessionData getOrCreateSession(String sessionId);

    /**
     * 保存会话
     *
     * @param sessionData 会话数据
     */
    void saveSession(SessionData sessionData);

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话数据（可能为空）
     */
    Optional<SessionData> getSession(String sessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void deleteSession(String sessionId);

    /**
     * 清空会话历史
     *
     * @param sessionId 会话ID
     */
    void clearHistory(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean exists(String sessionId);

    /**
     * 刷新会话过期时间
     *
     * @param sessionId 会话ID
     */
    void refreshTtl(String sessionId);

    /**
     * 获取当前存储类型
     *
     * @return 存储类型名称
     */
    String getStorageType();
}
