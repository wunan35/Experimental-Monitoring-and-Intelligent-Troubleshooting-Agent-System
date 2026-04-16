package org.example.service;

import io.micrometer.core.instrument.Timer;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Getter;
import lombok.Setter;
import org.example.constant.MilvusConstants;
import org.example.service.cache.VectorSearchCacheService;
import org.example.service.metrics.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量搜索服务
 * 负责从 Milvus 中搜索相似向量
 * 支持缓存和指标收集
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired(required = false)
    private VectorSearchCacheService searchCacheService;

    @Autowired(required = false)
    private AgentMetricsService metricsService;

    @Autowired(required = false)
    private RerankerService rerankerService;

    /**
     * 搜索相似文档
     *
     * @param query 查询文本
     * @param topK  返回最相似的K个结果
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("开始搜索相似文档, 查询: {}, topK: {}", query, topK);

            // 1. 尝试从缓存获取
            if (searchCacheService != null) {
                var cached = searchCacheService.getSearchResult(query, topK);
                if (cached.isPresent()) {
                    logger.info("使用缓存的搜索结果, 查询: {}, topK: {}", query, topK);
                    List<SearchResult> results = convertCachedResults(cached.get());
                    if (metricsService != null) {
                        metricsService.recordVectorSearch(topK, results.size());
                    }
                    return results;
                }
            }

            // 2. 缓存未命中，执行搜索
            Timer.Sample timer = metricsService != null ? metricsService.startTimer() : null;
            long startTime = System.currentTimeMillis();

            // 生成查询向量
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            logger.debug("查询向量生成成功, 维度: {}", queryVector.size());

            // 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.IP)  // 使用内积实现余弦相似度
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();

            // 执行搜索
            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败: " + searchResponse.getMessage());
            }

            // 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                // 解析 metadata
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            // 3. 缓存结果
            if (searchCacheService != null && !results.isEmpty()) {
                List<VectorSearchCacheService.CachedSearchResult> cachedResults = convertToCachedResults(results);
                searchCacheService.putSearchResult(query, topK, cachedResults);
            }

            // 4. 调用Reranker重排序
            if (rerankerService != null && rerankerService.isEnabled()) {
                try {
                    long rerankStart = System.currentTimeMillis();
                    results = rerankerService.rerank(query, results);
                    long rerankDuration = System.currentTimeMillis() - rerankStart;
                    logger.info("Reranker重排序完成, 返回top{}个结果, 耗时: {}ms", results.size(), rerankDuration);
                } catch (Exception e) {
                    logger.warn("Reranker重排序失败，使用原始结果: {}", e.getMessage());
                    // 降级：使用原始搜索结果，不中断流程
                }
            }

            // 5. 记录指标
            long duration = System.currentTimeMillis() - startTime;
            if (metricsService != null) {
                if (timer != null) {
                    metricsService.stopTimer(timer, AgentMetricsService.VECTOR_SEARCH_DURATION);
                }
                metricsService.recordVectorSearch(topK, results.size());
            }

            logger.info("搜索完成, 找到 {} 个相似文档, 耗时: {}ms", results.size(), duration);
            return results;

        } catch (Exception e) {
            logger.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将缓存结果转换为SearchResult列表
     */
    private List<SearchResult> convertCachedResults(List<VectorSearchCacheService.CachedSearchResult> cachedResults) {
        List<SearchResult> results = new ArrayList<>();
        for (VectorSearchCacheService.CachedSearchResult cached : cachedResults) {
            SearchResult result = new SearchResult();
            result.setId(cached.getId());
            result.setContent(cached.getContent());
            result.setScore((float) cached.getScore());
            result.setMetadata(cached.getMetadata());
            results.add(result);
        }
        return results;
    }

    /**
     * 将SearchResult列表转换为缓存格式
     */
    private List<VectorSearchCacheService.CachedSearchResult> convertToCachedResults(List<SearchResult> results) {
        List<VectorSearchCacheService.CachedSearchResult> cachedResults = new ArrayList<>();
        for (SearchResult result : results) {
            cachedResults.add(new VectorSearchCacheService.CachedSearchResult(
                result.getId(),
                result.getContent(),
                result.getScore(),
                result.getMetadata()
            ));
        }
        return cachedResults;
    }

    /**
     * 搜索结果类
     */
    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;
    }
}
