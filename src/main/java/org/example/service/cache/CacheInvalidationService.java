package org.example.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 缓存失效服务
 * 处理缓存失效的场景
 */
@Service
public class CacheInvalidationService {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationService.class);

    @Autowired(required = false)
    private EmbeddingCacheService embeddingCacheService;

    @Autowired(required = false)
    private VectorSearchCacheService searchCacheService;

    /**
     * 当知识库更新时，清除相关缓存
     * 通常在文档上传/删除后调用
     */
    public void invalidateOnKnowledgeBaseUpdate() {
        logger.info("Knowledge base updated, invalidating caches...");

        if (searchCacheService != null) {
            searchCacheService.clearAll();
        }

        logger.info("Cache invalidation completed");
    }

    /**
     * 当特定文档更新时，清除相关搜索缓存
     *
     * @param documentId 文档ID
     */
    public void invalidateOnDocumentUpdate(String documentId) {
        logger.info("Document {} updated, search cache will be invalidated on next query", documentId);
        // 搜索缓存使用TTL自动过期，无需主动清除
        // 如果需要精确控制，可以维护 documentId -> cacheKeys 的映射
    }

    /**
     * 清除所有缓存
     */
    public void invalidateAll() {
        logger.info("Invalidating all caches...");

        if (embeddingCacheService != null) {
            embeddingCacheService.clearAll();
        }

        if (searchCacheService != null) {
            searchCacheService.clearAll();
        }

        logger.info("All caches invalidated");
    }
}
