package org.example.service;

import ai.onnxruntime.*;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.example.service.cache.RerankerCacheService;
import org.example.service.metrics.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reranker重排序服务
 * 使用 BGE-Reranker-large 模型对搜索结果进行重排序
 */
@Service
public class RerankerService {

    private static final Logger logger = LoggerFactory.getLogger(RerankerService.class);

    @Value("${reranker.enabled:true}")
    private boolean enabled;

    @Value("${reranker.model-path:onnx/BAAI/bge-reranker-large}")
    private String modelPath;

    @Value("${reranker.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${reranker.top-k:3}")
    private int topK;

    @Value("${reranker.max-results-to-rerank:50}")
    private int maxResultsToRerank;

    @Autowired(required = false)
    private RerankerCacheService cacheService;

    @Autowired(required = false)
    private AgentMetricsService metricsService;

    private OrtEnvironment ortEnvironment;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Reranker已禁用，跳过模型加载");
            return;
        }

        try {
            // 1. 加载Tokenizer
            logger.info("开始加载Tokenizer: {}", modelPath);
            try {
                tokenizer = HuggingFaceTokenizer.newInstance(modelPath);
                logger.info("Tokenizer加载成功");
            } catch (Exception e) {
                logger.warn("加载Tokenizer失败，使用默认配置: {}", e.getMessage());
                // 使用默认tokenizer
                tokenizer = HuggingFaceTokenizer.newInstance("BAAI/bge-reranker-large");
            }

            // 2. 加载ONNX模型
            logger.info("开始加载ONNX模型: {}", modelPath);
            Path modelFilePath = Paths.get(modelPath, "model.onnx");

            // 检查本地模型是否存在
            if (!Files.exists(modelFilePath)) {
                logger.warn("本地模型文件不存在: {}, 使用内置配置", modelFilePath);
                // 尝试使用环境变量或默认路径
                String envPath = System.getenv("RERANKER_MODEL_PATH");
                if (envPath != null) {
                    modelFilePath = Paths.get(envPath, "model.onnx");
                    if (!Files.exists(modelFilePath)) {
                        logger.warn("环境变量指定的模型路径也不存在: {}", envPath);
                        throw new IOException("模型文件不存在，请配置正确的reranker.model-path或设置RERANKER_MODEL_PATH环境变量");
                    }
                }
            }

            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);

            session = ortEnvironment.createSession(modelFilePath.toString(), options);

            // 输入输出信息
            logger.info("ONNX模型加载成功");
            logger.info("模型输入: {}", session.getInputNames());
            logger.info("模型输出: {}", session.getOutputNames());

            initialized = true;
            logger.info("BGE-Reranker-large 模型初始化完成，topK: {}, maxResults: {}", topK, maxResultsToRerank);

        } catch (Exception e) {
            logger.error("BGE-Reranker-large 模型加载失败，reranker将被禁用", e);
            initialized = false;
        }
    }

    /**
     * 检查reranker是否可用
     */
    public boolean isEnabled() {
        return enabled && initialized;
    }

    /**
     * 对搜索结果进行重排序
     * 返回topK个最相关的结果
     *
     * @param query 查询文本
     * @param results 原始搜索结果
     * @return 重排序后的结果（仅返回topK个）
     */
    public List<VectorSearchService.SearchResult> rerank(
            String query,
            List<VectorSearchService.SearchResult> results) {

        // 1. 检查模型是否可用
        if (!isEnabled()) {
            logger.debug("Reranker未启用，返回原始结果");
            return results;
        }

        // 2. 检查缓存
        if (cacheEnabled && cacheService != null) {
            Optional<List<RerankerCacheService.CachedRankResult>> cached =
                cacheService.getRerankResult(query, results.size());
            if (cached.isPresent()) {
                logger.debug("使用缓存的重排序结果");
                return convertFromCached(cached.get());
            }
        }

        long startTime = System.currentTimeMillis();

        try {
            // 3. 限制重排序数量（性能优化）
            List<VectorSearchService.SearchResult> toRerank =
                results.stream().limit(maxResultsToRerank).collect(Collectors.toList());

            if (toRerank.isEmpty()) {
                logger.warn("没有结果需要重排序");
                return results;
            }

            // 4. Tokenize：构建query-doc pairs
            List<String> pairs = new ArrayList<>();
            for (VectorSearchService.SearchResult result : toRerank) {
                // BGE-Reranker-large 格式: "query [SEP] doc"
                pairs.add(query + " [SEP] " + result.getContent());
            }

            logger.debug("开始对 {} 个结果进行tokenization", pairs.size());

            // 使用tokenizer编码 - 单个编码处理
            List<long[]> inputIdsList = new ArrayList<>();
            List<long[]> attentionMaskList = new ArrayList<>();
            int maxLen = 0;

            for (String pair : pairs) {
                var encoding = tokenizer.encode(pair);

                // 提取 input_ids 和 attention_mask
                long[] ids = encoding.getIds();
                long[] mask = encoding.getAttentionMask();

                inputIdsList.add(ids);
                attentionMaskList.add(mask);

                // 记录最大长度
                if (ids.length > maxLen) {
                    maxLen = ids.length;
                }
            }

            // 创建固定长度的数组用于padding
            long[][] inputIds = new long[pairs.size()][maxLen];
            long[][] attentionMask = new long[pairs.size()][maxLen];

            // 填充数组，不足长度用0填充
            for (int i = 0; i < pairs.size(); i++) {
                long[] ids = inputIdsList.get(i);
                long[] mask = attentionMaskList.get(i);

                System.arraycopy(ids, 0, inputIds[i], 0, ids.length);
                System.arraycopy(mask, 0, attentionMask[i], 0, mask.length);

                // padding部分已经是0
            }

            logger.debug("Tokenization完成，最大序列长度: {}", maxLen);

            // 5. 调用ONNX模型计算相关性分数
            Map<String, OnnxTensor> inputs = new HashMap<>();

            // 创建 OnnxTensor 需要指定正确的类型和形状
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                inputIds
            );
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                ortEnvironment,
                attentionMask
            );

            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            logger.debug("开始调用ONNX模型推理...");
            OrtSession.Result ortResult = session.run(inputs);

            // 获取输出分数 - BGE模型输出是 [batch_size, 1] 或 [batch_size]
            OnnxValue outputValue = ortResult.get(0);
            Object output = outputValue.getValue();

            float[] scores;
            if (output instanceof float[][]) {
                scores = ((float[][]) output)[0];
            } else if (output instanceof float[]) {
                scores = (float[]) output;
            } else {
                throw new RuntimeException("不支持的输出类型: " + output.getClass());
            }

            logger.debug("ONNX模型推理完成，输出分数数量: {}", scores.length);

            // 6. 根据分数重新排序
            List<IndexedResult> indexedResults = new ArrayList<>();
            for (int i = 0; i < toRerank.size(); i++) {
                indexedResults.add(new IndexedResult(toRerank.get(i), i, scores[i]));
            }

            // 按分数降序排序
            indexedResults.sort((a, b) -> Float.compare(b.score, a.score));

            // 7. 只返回topK个结果
            List<VectorSearchService.SearchResult> reranked = indexedResults.stream()
                .limit(topK)
                .map(ir -> {
                    VectorSearchService.SearchResult result = ir.result;
                    result.setScore(ir.score);
                    return result;
                })
                .collect(Collectors.toList());

            // 8. 缓存结果
            if (cacheEnabled && cacheService != null) {
                List<RerankerCacheService.CachedRankResult> cachedResults = convertToCached(reranked);
                cacheService.putRerankResult(query, results.size(), cachedResults);
            }

            long duration = System.currentTimeMillis() - startTime;

            // 9. 记录指标
            if (metricsService != null) {
                metricsService.recordRerank(1, reranked.size());
                metricsService.recordRerankDuration(Duration.ofMillis(duration), 1);
            }

            logger.info("Reranker重排序完成，输入{}个，返回top{}个，耗时{}ms",
                toRerank.size(), topK, duration);

            return reranked;

        } catch (Exception e) {
            logger.error("Reranker重排序失败，使用原始结果", e);
            // 降级：返回原始结果的前topK个
            return results.stream().limit(topK).collect(Collectors.toList());
        }
    }

    /**
     * 将缓存结果转换为SearchResult列表
     */
    private List<VectorSearchService.SearchResult> convertFromCached(
            List<RerankerCacheService.CachedRankResult> cachedResults) {
        List<VectorSearchService.SearchResult> results = new ArrayList<>();
        for (RerankerCacheService.CachedRankResult cached : cachedResults) {
            VectorSearchService.SearchResult result = new VectorSearchService.SearchResult();
            result.setId(cached.getId());
            result.setContent(cached.getContent());
            result.setScore(cached.getScore());
            result.setMetadata(cached.getMetadata());
            results.add(result);
        }
        return results;
    }

    /**
     * 将SearchResult列表转换为缓存格式
     */
    private List<RerankerCacheService.CachedRankResult> convertToCached(
            List<VectorSearchService.SearchResult> results) {
        List<RerankerCacheService.CachedRankResult> cachedResults = new ArrayList<>();
        for (VectorSearchService.SearchResult result : results) {
            cachedResults.add(new RerankerCacheService.CachedRankResult(
                result.getId(),
                result.getContent(),
                result.getScore(),
                result.getMetadata()
            ));
        }
        return cachedResults;
    }

    /**
     * 辅助类：带索引的结果
     */
    private static class IndexedResult {
        VectorSearchService.SearchResult result;
        int index;
        float score;

        IndexedResult(VectorSearchService.SearchResult result, int index, float score) {
            this.result = result;
            this.index = index;
            this.score = score;
        }
    }
}
