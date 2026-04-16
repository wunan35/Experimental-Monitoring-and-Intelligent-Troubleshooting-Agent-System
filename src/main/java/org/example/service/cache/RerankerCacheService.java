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
 * Reranker重排序结果缓存服务
 * 缓存查询+重排序结果，减少重复计算
 */
@Service
public class RerankerCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RerankerCacheService.class);

    private static final String CACHE_KEY_PREFIX = "reranker:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存的重排序结果条目
     */
    public static class CachedRankResult {
        private String id;
        private String content;
        private float score;
        private String metadata;

        public CachedRankResult() {}

        public CachedRankResult(String id, String content, float score, String metadata) {
            this.id = id;
            this.content = content;
            this.score = score;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }

    /**
     * 获取缓存的重排序结果
     *
     * @param query 查询文本
     * @param resultCount 结果数量
     * @return 重排序结果列表（如果存在）
     */
    @SuppressWarnings("unchecked")
    public Optional<List<CachedRankResult>> getRerankResult(String query, int resultCount) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        String key = buildCacheKey(query, resultCount);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                logger.debug("Cache hit for reranker: query length={}, resultCount={}",
                    query.length(), resultCount);
                return Optional.of((List<CachedRankResult>) cached);
            }
        } catch (Exception e) {
            logger.warn("Failed to get rerank result from cache: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 缓存重排序结果
     *
     * @param query 查询文本
     * @param resultCount 结果数量
     * @param results 重排序结果
     */
    public void putRerankResult(String query, int resultCount, List<CachedRankResult> results) {
        if (redisTemplate == null || results == null || results.isEmpty()) {
            return;
        }

        String key = buildCacheKey(query, resultCount);
        try {
            redisTemplate.opsForValue().set(
                key,
                results,
                CACHE_TTL_HOURS,
                TimeUnit.HOURS
            );
            logger.debug("Cached reranker result: query length={}, resultCount={}, resultCount={}",
                query.length(), resultCount, results.size());
        } catch (Exception e) {
            logger.warn("Failed to cache rerank result: {}", e.getMessage());
        }
    }

    /**
     * 构建缓存key
     * 使用 query + resultCount 的 SHA256 哈希
     * 必须包含 resultCount，因为相同query但不同数量的结果，rerank后排序可能不同
     */
    private String buildCacheKey(String query, int resultCount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String cacheInput = query + ":" + resultCount;
            byte[] hash = digest.digest(cacheInput.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return CACHE_KEY_PREFIX + hexString;
        } catch (Exception e) {
            // 降级：使用哈希码
            return CACHE_KEY_PREFIX + Math.abs((query + ":" + resultCount).hashCode());
        }
    }

    /**
     * 清除所有reranker缓存
     */
    public void clearAll() {
        if (redisTemplate == null) {
            return;
        }

        try {
            var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} reranker cache entries", keys.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to clear reranker cache: {}", e.getMessage());
        }
    }
}
