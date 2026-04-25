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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vector search service.
 *
 * <p>The public topK remains the final result count. When reranking is enabled,
 * Milvus retrieves a larger candidate set first, then the reranker selects the
 * final topK results.</p>
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

    @Value("${retrieval.candidate-top-k:30}")
    private int candidateTopK;

    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            int searchTopK = resolveSearchTopK(topK);
            logger.info("Start vector search, finalTopK: {}, candidateTopK: {}", topK, searchTopK);

            if (searchCacheService != null) {
                var cached = searchCacheService.getSearchResult(query, searchTopK);
                if (cached.isPresent()) {
                    List<SearchResult> results = convertCachedResults(cached.get());
                    results = applyRerankOrLimit(query, results, topK);
                    if (metricsService != null) {
                        metricsService.recordVectorSearch(topK, results.size());
                    }
                    return results;
                }
            }

            Timer.Sample timer = metricsService != null ? metricsService.startTimer() : null;
            long startTime = System.currentTimeMillis();

            List<Float> queryVector = embeddingService.generateQueryVector(query);
            logger.debug("Query vector generated, dimension: {}", queryVector.size());

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(searchTopK)
                    .withMetricType(io.milvus.param.MetricType.IP)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("Vector search failed: " + searchResponse.getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            if (searchCacheService != null && !results.isEmpty()) {
                List<VectorSearchCacheService.CachedSearchResult> cachedResults = convertToCachedResults(results);
                searchCacheService.putSearchResult(query, searchTopK, cachedResults);
            }

            results = applyRerankOrLimit(query, results, topK);

            long duration = System.currentTimeMillis() - startTime;
            if (metricsService != null) {
                if (timer != null) {
                    metricsService.stopTimer(timer, AgentMetricsService.VECTOR_SEARCH_DURATION);
                }
                metricsService.recordVectorSearch(topK, results.size());
            }

            logger.info("Vector search completed, returned {} results, duration: {}ms", results.size(), duration);
            return results;

        } catch (Exception e) {
            logger.error("Search similar documents failed", e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    private int resolveSearchTopK(int finalTopK) {
        if (rerankerService != null && rerankerService.isEnabled()) {
            return Math.max(finalTopK, candidateTopK);
        }
        return finalTopK;
    }

    private List<SearchResult> applyRerankOrLimit(String query, List<SearchResult> results, int finalTopK) {
        if (rerankerService != null && rerankerService.isEnabled()) {
            try {
                long rerankStart = System.currentTimeMillis();
                List<SearchResult> reranked = rerankerService.rerank(query, results, finalTopK);
                long rerankDuration = System.currentTimeMillis() - rerankStart;
                logger.info("Reranker completed, returned {} results, duration: {}ms", reranked.size(), rerankDuration);
                return reranked;
            } catch (Exception e) {
                logger.warn("Reranker failed, using original vector results: {}", e.getMessage());
            }
        }

        return results.stream()
                .limit(finalTopK)
                .collect(Collectors.toList());
    }

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

    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;
    }
}
