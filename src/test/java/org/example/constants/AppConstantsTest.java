package org.example.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppConstants 常量类测试
 * 验证常量值的合理性和一致性
 */
class AppConstantsTest {

    @Test
    @DisplayName("验证会话配置常量")
    void testSessionConstants() {
        assertEquals(6, AppConstants.MAX_WINDOW_SIZE);
        assertEquals(1800, AppConstants.SESSION_EXPIRE_SECONDS);
    }

    @Test
    @DisplayName("验证SSE超时配置")
    void testSseTimeoutConstants() {
        assertEquals(300_000L, AppConstants.SSE_TIMEOUT_MS);
        assertEquals(600_000L, AppConstants.AIOPS_TIMEOUT_MS);
        assertEquals(30_000L, AppConstants.SSE_HEARTBEAT_INTERVAL_MS);

        // 验证超时时间的合理性：AI Ops应该比普通SSE更长
        assertTrue(AppConstants.AIOPS_TIMEOUT_MS > AppConstants.SSE_TIMEOUT_MS);
    }

    @Test
    @DisplayName("验证文档分片配置")
    void testDocumentChunkConstants() {
        assertEquals(800, AppConstants.CHUNK_MAX_SIZE);
        assertEquals(100, AppConstants.CHUNK_OVERLAP);
        assertEquals(100, AppConstants.SEMANTIC_MIN_CHUNK_SIZE);
        assertEquals(1600, AppConstants.SEMANTIC_MAX_CHUNK_SIZE);

        // 重叠应该小于分片大小
        assertTrue(AppConstants.CHUNK_OVERLAP < AppConstants.CHUNK_MAX_SIZE);

        // 语义分片最大应该大于等于基础分片
        assertTrue(AppConstants.SEMANTIC_MAX_CHUNK_SIZE >= AppConstants.CHUNK_MAX_SIZE);
    }

    @Test
    @DisplayName("验证线程池配置")
    void testThreadPoolConstants() {
        assertEquals(5, AppConstants.THREAD_POOL_CORE_SIZE);
        assertEquals(20, AppConstants.THREAD_POOL_MAX_SIZE);
        assertEquals(60L, AppConstants.THREAD_POOL_KEEP_ALIVE_TIME);
        assertEquals(100, AppConstants.THREAD_POOL_QUEUE_CAPACITY);

        // 核心大小应该小于等于最大大小
        assertTrue(AppConstants.THREAD_POOL_CORE_SIZE <= AppConstants.THREAD_POOL_MAX_SIZE);
    }

    @Test
    @DisplayName("验证缓存配置")
    void testCacheConstants() {
        assertEquals(24L, AppConstants.EMBEDDING_CACHE_TTL_HOURS);
        assertEquals(10L, AppConstants.VECTOR_SEARCH_CACHE_TTL_MINUTES);
        assertEquals("embedding:", AppConstants.EMBEDDING_CACHE_KEY_PREFIX);
        assertEquals("vector_search:", AppConstants.VECTOR_SEARCH_CACHE_KEY_PREFIX);
    }

    @Test
    @DisplayName("验证RAG配置")
    void testRagConstants() {
        assertEquals(3, AppConstants.RAG_TOP_K);
        assertEquals("qwen3-max", AppConstants.RAG_MODEL);
    }

    @Test
    @DisplayName("验证实验室配置")
    void testLaboratoryConstants() {
        assertEquals("结构实验室", AppConstants.DEFAULT_LABORATORY);
        assertEquals(4, AppConstants.VALID_LABORATORIES.length);

        // 默认实验室应该在有效列表中
        boolean found = false;
        for (String lab : AppConstants.VALID_LABORATORIES) {
            if (lab.equals(AppConstants.DEFAULT_LABORATORY)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "默认实验室应该在有效实验室列表中");
    }

    @Test
    @DisplayName("验证安全配置")
    void testSecurityConstants() {
        assertEquals(10_000, AppConstants.TOOL_MAX_OUTPUT_LENGTH);
        assertEquals(10 * 1024 * 1024, AppConstants.FILE_UPLOAD_MAX_SIZE);
    }

    @Test
    @DisplayName("验证请求头常量")
    void testHeaderConstants() {
        assertEquals("X-Trace-Id", AppConstants.TRACE_ID_HEADER);
        assertEquals("X-Session-Id", AppConstants.SESSION_ID_HEADER);
    }

    @Test
    @DisplayName("验证Agent配置")
    void testAgentConstants() {
        assertEquals(300, AppConstants.AGENT_EXECUTION_TIMEOUT_SECONDS);
        assertEquals(10, AppConstants.AGENT_MAX_CONCURRENT);
        assertEquals(100, AppConstants.AGENT_QUEUE_CAPACITY);
    }

    @Test
    @DisplayName("验证限流配置")
    void testRateLimitConstants() {
        assertEquals(10, AppConstants.RATE_LIMIT_CHAT_PER_SECOND);
        assertEquals(3, AppConstants.RATE_LIMIT_AIOPS_PER_MINUTE);
        assertEquals(5, AppConstants.RATE_LIMIT_UPLOAD_PER_MINUTE);

        // AI Ops限流应该比对话限流更严格
        assertTrue(AppConstants.RATE_LIMIT_AIOPS_PER_MINUTE < AppConstants.RATE_LIMIT_CHAT_PER_SECOND);
    }

    @Test
    @DisplayName("验证私有构造函数")
    void testPrivateConstructor() {
        assertThrows(IllegalStateException.class, () -> {
            // 尝试通过反射创建实例
            var constructor = AppConstants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }
}
