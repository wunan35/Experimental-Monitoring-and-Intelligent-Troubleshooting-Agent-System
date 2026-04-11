package org.example.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 向量搜索结果缓存服务
 * 缓存相似的查询结果，减少Milvus查询
 */
@Service
public class VectorSearchCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchCacheService.class);

    private static final String CACHE_KEY_PREFIX = "vector_search:";
    private static final long CACHE_TTL_MINUTES = 10;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 搜索结果缓存条目
     */
    public static class CachedSearchResult {
        private String id;
        private String content;
        private double score;
        private String metadata;

        public CachedSearchResult() {}

        public CachedSearchResult(String id, String content, double score, String metadata) {
            this.id = id;
            this.content = content;
            this.score = score;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }

    /**
     * 获取缓存的搜索结果
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 搜索结果列表（如果存在）
     */
    @SuppressWarnings("unchecked")
    public Optional<List<CachedSearchResult>> getSearchResult(String query, int topK) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        String key = buildCacheKey(query, topK);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                logger.debug("Cache hit for vector search: query length={}, topK={}",
                    query.length(), topK);
                return Optional.of((List<CachedSearchResult>) cached);
            }
        } catch (Exception e) {
            logger.warn("Failed to get search result from cache: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 缓存搜索结果
     *
     * @param query   查询文本
     * @param topK    返回数量
     * @param results 搜索结果
     */
    public void putSearchResult(String query, int topK, List<CachedSearchResult> results) {
        if (redisTemplate == null || results == null || results.isEmpty()) {
            return;
        }

        String key = buildCacheKey(query, topK);
        try {
            redisTemplate.opsForValue().set(
                key,
                results,
                CACHE_TTL_MINUTES,
                TimeUnit.MINUTES
            );
            logger.debug("Cached vector search result: query length={}, topK={}, resultCount={}",
                query.length(), topK, results.size());
        } catch (Exception e) {
            logger.warn("Failed to cache search result: {}", e.getMessage());
        }
    }

    /**
     * 构建缓存key
     */
    private String buildCacheKey(String query, int topK) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String cacheInput = query + ":" + topK;
            byte[] hash = digest.digest(cacheInput.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return CACHE_KEY_PREFIX + hexString;
        } catch (Exception e) {
            return CACHE_KEY_PREFIX + Math.abs((query + ":" + topK).hashCode());
        }
    }

    /**
     * 清除所有搜索缓存
     */
    public void clearAll() {
        if (redisTemplate == null) {
            return;
        }

        try {
            var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} vector search cache entries", keys.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to clear vector search cache: {}", e.getMessage());
        }
    }
}
