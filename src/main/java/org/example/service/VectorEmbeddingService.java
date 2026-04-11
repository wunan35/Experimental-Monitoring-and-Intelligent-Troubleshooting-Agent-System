package org.example.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingOutput;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import org.example.service.cache.EmbeddingCacheService;
import org.example.service.metrics.AgentMetricsService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 向量嵌入服务
 * 使用阿里云 DashScope Text Embedding API
 * 支持缓存和指标收集
 */
@Service
public class VectorEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.embedding.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @Autowired(required = false)
    private EmbeddingCacheService embeddingCacheService;

    @Autowired(required = false)
    private AgentMetricsService metricsService;

    @PostConstruct
    public void init() {
        // 验证 API Key
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.error("API Key 未正确配置！");
            throw new IllegalStateException("请设置环境变量 DASHSCOPE_API_KEY 或在 application.yml 中配置正确的 API Key");
        }

        // 不记录API Key内容，仅记录状态
        logger.info("API Key 已加载");

        // 设置全局 API Key（确保设置成功）
        Constants.apiKey = apiKey;

        // 验证 API Key 是否设置成功
        if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
            logger.error("Constants.apiKey 设置失败！");
            throw new IllegalStateException("API Key 设置到 Constants 失败");
        }

        logger.info("Constants.apiKey 已设置");

        // 创建 TextEmbedding 实例
        textEmbedding = new TextEmbedding();

        logger.info("阿里云 DashScope Embedding 服务初始化完成，模型: {}, 缓存: {}",
            model, embeddingCacheService != null ? "启用" : "禁用");
    }

    /**
     * 生成向量嵌入
     * 调用阿里云 DashScope Text Embedding API
     * 支持缓存
     *
     * @param content 文本内容
     * @return 向量嵌入（浮点数列表）
     */
    public List<Float> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                logger.warn("内容为空，无法生成向量");
                throw new IllegalArgumentException("内容不能为空");
            }

            // 1. 尝试从缓存获取
            if (embeddingCacheService != null) {
                Optional<List<Float>> cached = embeddingCacheService.getEmbedding(content);
                if (cached.isPresent()) {
                    logger.debug("使用缓存的向量嵌入, 内容长度: {} 字符", content.length());
                    if (metricsService != null) {
                        metricsService.recordVectorEmbedding(1);
                    }
                    return cached.get();
                }
            }

            // 2. 缓存未命中，调用API
            long startTime = System.currentTimeMillis();
            logger.debug("开始生成向量嵌入, 内容长度: {} 字符", content.length());

            // 确保 API Key 已设置（防止被其他地方覆盖）
            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                logger.warn("检测到 Constants.apiKey 为空，重新设置");
                Constants.apiKey = apiKey;
            }

            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(model)
                    .texts(Collections.singletonList(content))
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            List<Float> floatEmbedding = getFloats(result);

            // 归一化向量，用于余弦相似度计算
            List<Float> normalizedEmbedding = normalizeVector(floatEmbedding);

            // 3. 缓存结果
            if (embeddingCacheService != null) {
                embeddingCacheService.putEmbedding(content, normalizedEmbedding);
            }

            // 4. 记录指标
            long duration = System.currentTimeMillis() - startTime;
            if (metricsService != null) {
                metricsService.recordVectorEmbedding(1);
                metricsService.recordVectorEmbeddingDuration(Duration.ofMillis(duration), 1);
            }

            logger.info("成功生成向量嵌入, 内容长度: {} 字符, 向量维度: {}, 已归一化, 耗时: {}ms",
                content.length(), normalizedEmbedding.size(), duration);

            return normalizedEmbedding;

        } catch (NoApiKeyException e) {
            logger.error("API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            logger.error("生成向量嵌入失败, 内容长度: {}", content != null ? content.length() : 0, e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    @NotNull
    private static List<Float> getFloats(TextEmbeddingResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new RuntimeException("DashScope API 返回空结果");
        }

        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if (embeddings.isEmpty()) {
            throw new RuntimeException("DashScope API 返回空向量列表");
        }

        // 获取第一个文本的向量
        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        // 转换为 List<Float>
        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for (Double value : embeddingDoubles) {
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }

    /**
     * 批量生成向量嵌入
     * 
     * @param contents 文本内容列表
     * @return 向量嵌入列表
     */
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        try {
            if (contents == null || contents.isEmpty()) {
                logger.warn("内容列表为空，无法生成向量");
                return Collections.emptyList();
            }

            logger.info("开始批量生成向量嵌入, 数量: {}", contents.size());
            
            // 确保 API Key 已设置
            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                logger.warn("检测到 Constants.apiKey 为空，重新设置");
                Constants.apiKey = apiKey;
            }

            // 构建请求参数 - 批量输入
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(model)
                    .texts(contents)
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("批量 DashScope API 返回空结果");
            }

            List<TextEmbeddingResultItem> embeddingItems = result.getOutput().getEmbeddings();
            
            if (embeddingItems.isEmpty()) {
                throw new RuntimeException("批量 DashScope API 返回空向量列表");
            }

            // 转换结果
            List<List<Float>> embeddings = new ArrayList<>();
            for (TextEmbeddingResultItem item : embeddingItems) {
                List<Double> embeddingDoubles = item.getEmbedding();
                List<Float> embedding = new ArrayList<>(embeddingDoubles.size());
                for (Double value : embeddingDoubles) {
                    embedding.add(value.floatValue());
                }
                embeddings.add(embedding);
            }

            // 归一化所有向量
            List<List<Float>> normalizedEmbeddings = normalizeVectors(embeddings);

            logger.info("成功批量生成向量嵌入, 数量: {}, 维度: {}, 已归一化",
                normalizedEmbeddings.size(),
                normalizedEmbeddings.isEmpty() ? 0 : normalizedEmbeddings.get(0).size());

            return normalizedEmbeddings;

        } catch (NoApiKeyException e) {
            logger.error("批量调用时 API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            logger.error("批量生成向量嵌入失败", e);
            throw new RuntimeException("批量生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成查询向量
     * 
     * @param query 查询文本
     * @return 向量嵌入
     */
    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    /**
     * 归一化向量（单位向量）
     *
     * @param vector 输入向量
     * @return 归一化后的向量
     */
    public List<Float> normalizeVector(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("向量不能为空");
        }

        float norm = 0.0f;
        for (Float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);

        if (norm == 0.0f) {
            throw new IllegalArgumentException("向量范数为零");
        }

        List<Float> normalized = new ArrayList<>(vector.size());
        for (Float value : vector) {
            normalized.add(value / norm);
        }

        return normalized;
    }

    /**
     * 批量归一化向量
     *
     * @param vectors 向量列表
     * @return 归一化后的向量列表
     */
    public List<List<Float>> normalizeVectors(List<List<Float>> vectors) {
        List<List<Float>> normalizedVectors = new ArrayList<>(vectors.size());
        for (List<Float> vector : vectors) {
            normalizedVectors.add(normalizeVector(vector));
        }
        return normalizedVectors;
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 余弦相似度 [-1, 1]
     */
    public float calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
