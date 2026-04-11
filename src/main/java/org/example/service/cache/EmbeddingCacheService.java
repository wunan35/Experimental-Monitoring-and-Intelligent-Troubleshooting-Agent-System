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
 * Embedding缓存服务
 * 缓存文本的向量嵌入结果，减少API调用
 */
@Service
public class EmbeddingCacheService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingCacheService.class);

    private static final String CACHE_KEY_PREFIX = "embedding:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取缓存的向量
     *
     * @param text 原始文本
     * @return 向量（如果存在）
     */
    @SuppressWarnings("unchecked")
    public Optional<List<Float>> getEmbedding(String text) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        String key = buildCacheKey(text);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                logger.debug("Cache hit for embedding: {}", key.substring(0, Math.min(20, key.length())) + "...");
                return Optional.of((List<Float>) cached);
            }
        } catch (Exception e) {
            logger.warn("Failed to get embedding from cache: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 缓存向量
     *
     * @param text      原始文本
     * @param embedding 向量
     */
    public void putEmbedding(String text, List<Float> embedding) {
        if (redisTemplate == null || embedding == null) {
            return;
        }

        String key = buildCacheKey(text);
        try {
            redisTemplate.opsForValue().set(
                key,
                embedding,
                CACHE_TTL_HOURS,
                TimeUnit.HOURS
            );
            logger.debug("Cached embedding: {}", key.substring(0, Math.min(20, key.length())) + "...");
        } catch (Exception e) {
            logger.warn("Failed to cache embedding: {}", e.getMessage());
        }
    }

    /**
     * 构建缓存key
     * 使用文本内容的SHA256哈希
     */
    private String buildCacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return CACHE_KEY_PREFIX + hexString;
        } catch (Exception e) {
            // 降级：使用文本哈希码
            return CACHE_KEY_PREFIX + Math.abs(text.hashCode());
        }
    }

    /**
     * 清除所有embedding缓存
     */
    public void clearAll() {
        if (redisTemplate == null) {
            return;
        }

        try {
            var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} embedding cache entries", keys.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to clear embedding cache: {}", e.getMessage());
        }
    }
}
