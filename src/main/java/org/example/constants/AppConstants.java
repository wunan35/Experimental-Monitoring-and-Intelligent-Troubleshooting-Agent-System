package org.example.constants;

/**
 * 应用常量类
 * 集中管理所有魔法数字和硬编码值，提高代码可维护性
 */
public final class AppConstants {

    private AppConstants() {
        throw new IllegalStateException("常量工具类，禁止实例化");
    }

    // ==================== 会话配置 ====================

    /** 最大历史消息窗口大小 */
    public static final int MAX_WINDOW_SIZE = 6;

    /** 会话默认过期时间（秒） */
    public static final int SESSION_EXPIRE_SECONDS = 1800;

    // ==================== SSE超时配置 ====================

    /** SSE超时时间：5分钟 */
    public static final long SSE_TIMEOUT_MS = 300_000L;

    /** AI Ops超时时间：10分钟 */
    public static final long AIOPS_TIMEOUT_MS = 600_000L;

    /** SSE心跳间隔：30秒 */
    public static final long SSE_HEARTBEAT_INTERVAL_MS = 30_000L;

    // ==================== 文档分片配置 ====================

    /** 默认分片最大大小 */
    public static final int CHUNK_MAX_SIZE = 800;

    /** 分片重叠大小 */
    public static final int CHUNK_OVERLAP = 100;

    /** 语义分片最小大小 */
    public static final int SEMANTIC_MIN_CHUNK_SIZE = 100;

    /** 语义分片最大大小 */
    public static final int SEMANTIC_MAX_CHUNK_SIZE = 1600;

    /** 语义相似度阈值 */
    public static final float SEMANTIC_SIMILARITY_THRESHOLD = 0.7f;

    /** 最小句子长度 */
    public static final int MIN_SENTENCE_LENGTH = 10;

    // ==================== 线程池配置 ====================

    /** 线程池核心大小 */
    public static final int THREAD_POOL_CORE_SIZE = 5;

    /** 线程池最大大小 */
    public static final int THREAD_POOL_MAX_SIZE = 20;

    /** 线程池保活时间（秒） */
    public static final long THREAD_POOL_KEEP_ALIVE_TIME = 60L;

    /** 线程池队列容量 */
    public static final int THREAD_POOL_QUEUE_CAPACITY = 100;

    /** SSE线程池核心大小 */
    public static final int SSE_THREAD_POOL_CORE_SIZE = 10;

    /** SSE线程池最大大小 */
    public static final int SSE_THREAD_POOL_MAX_SIZE = 50;

    // ==================== HTTP超时配置 ====================

    /** HTTP连接超时（秒） */
    public static final int HTTP_TIMEOUT_SECONDS = 10;

    /** HTTP连接超时（毫秒） */
    public static final int HTTP_TIMEOUT_MS = 10_000;

    // ==================== 缓存配置 ====================

    /** Embedding缓存TTL（小时） */
    public static final long EMBEDDING_CACHE_TTL_HOURS = 24;

    /** 向量搜索缓存TTL（分钟） */
    public static final long VECTOR_SEARCH_CACHE_TTL_MINUTES = 10;

    /** Embedding缓存Key前缀 */
    public static final String EMBEDDING_CACHE_KEY_PREFIX = "embedding:";

    /** 向量搜索缓存Key前缀 */
    public static final String VECTOR_SEARCH_CACHE_KEY_PREFIX = "vector_search:";

    // ==================== RAG配置 ====================

    /** RAG检索TopK */
    public static final int RAG_TOP_K = 3;

    /** RAG模型名称 */
    public static final String RAG_MODEL = "qwen3-max";

    // ==================== 实验监控配置 ====================

    /** 实验数据源URL */
    public static final String EXPERIMENT_DATA_SOURCE_URL = "http://localhost:8080/api/experiment/alerts";

    /** 默认实验室 */
    public static final String DEFAULT_LABORATORY = "结构实验室";

    /** 有效实验室列表 */
    public static final String[] VALID_LABORATORIES = {
            "结构实验室", "材料实验室", "疲劳实验室", "智能监测实验室"
    };

    // ==================== 工具安全配置 ====================

    /** 工具输出最大长度 */
    public static final int TOOL_MAX_OUTPUT_LENGTH = 10_000;

    /** 文件上传最大大小（字节） */
    public static final long FILE_UPLOAD_MAX_SIZE = 10 * 1024 * 1024; // 10MB

    // ==================== 请求头常量 ====================

    /** TraceId请求头 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 会话ID请求头 */
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    // ==================== Agent配置 ====================

    /** Agent执行超时（秒） */
    public static final int AGENT_EXECUTION_TIMEOUT_SECONDS = 300;

    /** Agent最大并发数 */
    public static final int AGENT_MAX_CONCURRENT = 10;

    /** Agent队列容量 */
    public static final int AGENT_QUEUE_CAPACITY = 100;

    // ==================== 限流配置 ====================

    /** 对话限流：每秒请求数 */
    public static final int RATE_LIMIT_CHAT_PER_SECOND = 10;

    /** AI Ops限流：每分钟请求数 */
    public static final int RATE_LIMIT_AIOPS_PER_MINUTE = 3;

    /** 文件上传限流：每分钟请求数 */
    public static final int RATE_LIMIT_UPLOAD_PER_MINUTE = 5;
}