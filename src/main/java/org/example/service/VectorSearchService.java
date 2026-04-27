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
import org.example.dto.QueryRewriteResult;
import org.example.service.cache.VectorSearchCacheService;
import org.example.service.metrics.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector search service with hybrid retrieval (semantic + keyword).
 *
 * <p>Hybrid search combines:
 * <ul>
 *   <li>Semantic search: Milvus vector similarity (IP metric)</li>
 *   <li>Keyword search: BM25 on full corpus</li>
 * </ul>
 * Results are fused using Reciprocal Rank Fusion (RRF), then reranked
 * to produce the final topK results.</p>
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    // RRF constant (standard value)
    private static final int RRF_K = 60;

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

    @Autowired(required = false)
    private QueryRewriteService queryRewriteService;

    @Autowired
    private BM25Service bm25Service;

    @Value("${query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    @Value("${retrieval.candidate-top-k:20}")
    private int candidateTopK;

    @Value("${retrieval.hybrid-search:true}")
    private boolean hybridSearchEnabled;

    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("Start hybrid search, query: {}, finalTopK: {}, hybridEnabled: {}",
                    query, topK, hybridSearchEnabled);

            // Check cache first
            if (searchCacheService != null) {
                var cached = searchCacheService.getSearchResult(query, topK);
                if (cached.isPresent()) {
                    List<SearchResult> results = convertCachedResults(cached.get());
                    if (metricsService != null) {
                        metricsService.recordVectorSearch(topK, results.size());
                    }
                    return results;
                }
            }

            Timer.Sample timer = metricsService != null ? metricsService.startTimer() : null;
            long startTime = System.currentTimeMillis();

            // Query rewrite (semantic enhancement)
            String processedQuery = query;
            List<String> rewrittenQueriesList = null;
            if (queryRewriteEnabled && queryRewriteService != null) {
                QueryRewriteResult rewriteResult = queryRewriteService.rewrite(query);
                if (rewriteResult.isRewritten()) {
                    logger.info("Query rewritten: {} -> {}", query, rewriteResult.getRewrittenQueries());
                    processedQuery = rewriteResult.getRewrittenQueries().get(0);
                    // Store all rewritten queries for multi-query search
                    if (rewriteResult.getRewrittenQueries().size() > 1) {
                        rewrittenQueriesList = rewriteResult.getRewrittenQueries();
                    }
                }
            }

            // Perform hybrid search
            List<SearchResult> results;
            if (hybridSearchEnabled) {
                // Rebuild BM25 index if empty (on first search or after restart)
                rebuildBm25IndexIfNeeded();
                results = hybridSearch(processedQuery, candidateTopK);
            } else {
                results = semanticSearch(processedQuery, candidateTopK);
            }

            // Multi-query search: if we have multiple rewritten queries, search each and merge
            if (rewrittenQueriesList != null && rewrittenQueriesList.size() > 1) {
                logger.info("Performing multi-query search with {} queries", rewrittenQueriesList.size());
                results = multiQuerySearch(rewrittenQueriesList, candidateTopK);
            }

            // Apply reranking (if enabled) to get final topK
            results = applyRerankOrLimit(query, results, topK);

            // Cache the results
            if (searchCacheService != null && !results.isEmpty()) {
                List<VectorSearchCacheService.CachedSearchResult> cachedResults = convertToCachedResults(results);
                searchCacheService.putSearchResult(query, topK, cachedResults);
            }

            long duration = System.currentTimeMillis() - startTime;
            if (metricsService != null) {
                if (timer != null) {
                    metricsService.stopTimer(timer, AgentMetricsService.VECTOR_SEARCH_DURATION);
                }
                metricsService.recordVectorSearch(topK, results.size());
            }

            logger.info("Hybrid search completed, returned {} results, duration: {}ms", results.size(), duration);
            return results;

        } catch (Exception e) {
            logger.error("Search similar documents failed", e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Multi-query search: search with multiple queries and merge results.
     */
    private List<SearchResult> multiQuerySearch(List<String> queries, int topK) {
        Map<String, SearchResult> allResults = new LinkedHashMap<>();

        for (String q : queries) {
            logger.debug("Multi-query search: {}", q);
            List<SearchResult> queryResults;
            if (hybridSearchEnabled) {
                queryResults = hybridSearch(q, topK);
            } else {
                queryResults = semanticSearch(q, topK);
            }

            // Merge results, deduplicate by id
            for (SearchResult r : queryResults) {
                if (!allResults.containsKey(r.getId())) {
                    allResults.put(r.getId(), r);
                }
            }
        }

        return new ArrayList<>(allResults.values());
    }

    /**
     * Hybrid search: combines semantic search (Milvus) with keyword search (BM25)
     * using Reciprocal Rank Fusion (RRF).
     */
    private List<SearchResult> hybridSearch(String query, int topK) {
        long hybridStart = System.currentTimeMillis();

        // 1. Semantic search (vector similarity) - get top candidates from Milvus
        List<SearchResult> semanticResults = semanticSearch(query, topK);
        logger.info("Semantic search returned {} results", semanticResults.size());

        // 2. Keyword search (BM25) - search full corpus
        List<SearchResult> bm25Results = bm25Service.search(query, topK);
        logger.info("BM25 keyword search returned {} results", bm25Results.size());

        // 3. RRF fusion to combine semantic and BM25 rankings
        List<SearchResult> fusedResults = rrfFusion(semanticResults, bm25Results, topK);
        logger.info("RRF fusion completed, {} fused results", fusedResults.size());

        long hybridDuration = System.currentTimeMillis() - hybridStart;
        logger.info("Hybrid search (semantic + BM25) completed in {}ms", hybridDuration);

        return fusedResults;
    }

    /**
     * Pure semantic search using Milvus vector similarity.
     */
    private List<SearchResult> semanticSearch(String query, int topK) {
        try {
            // Generate query vector
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            logger.debug("Query vector generated, dimension: {}", queryVector.size());

            // Search Milvus
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.IP)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("Vector search failed: " + searchResponse.getMessage());
            }

            // Parse results
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

            return results;

        } catch (Exception e) {
            logger.error("Semantic search failed", e);
            throw new RuntimeException("Semantic search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Rebuild BM25 index from Milvus if it's empty.
     * Called on first hybrid search to warm up the index.
     */
    private void rebuildBm25IndexIfNeeded() {
        if (bm25Service.getTotalDocs() > 0) {
            return; // Index already populated
        }

        logger.info("BM25 index is empty, skipping auto-rebuild (use /api/rebuild-bm25 to manually trigger)");
        // Note: For production, consider implementing auto-rebuild from Milvus here
        // For now, BM25 index will be populated as new documents are indexed
    }

    /**
     * Reciprocal Rank Fusion (RRF) to combine multiple ranked lists.
     *
     * RRF score = sum of 1 / (rank + k) for each list the document appears in
     *
     * @param semanticResults Results from semantic search, ordered by relevance
     * @param bm25Results Results from BM25 keyword search, ordered by relevance
     * @param topK Number of results to return
     */
    private List<SearchResult> rrfFusion(List<SearchResult> semanticResults,
                                         List<SearchResult> bm25Results,
                                         int topK) {
        if (semanticResults.isEmpty() && bm25Results.isEmpty()) {
            return Collections.emptyList();
        }

        if (semanticResults.isEmpty()) {
            return bm25Results.stream().limit(topK).collect(Collectors.toList());
        }

        if (bm25Results.isEmpty()) {
            return semanticResults.stream().limit(topK).collect(Collectors.toList());
        }

        // Build maps for quick lookup
        Map<String, SearchResult> allResults = new LinkedHashMap<>();

        // Add semantic results with their rank (1-based)
        for (int i = 0; i < semanticResults.size(); i++) {
            SearchResult r = semanticResults.get(i);
            r = copyResult(r);
            r.setScore((float) (1.0 / (i + RRF_K))); // RRF score for semantic
            allResults.put(r.getId(), r);
        }

        // Add/enhance BM25 results with their rank
        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult r = bm25Results.get(i);
            SearchResult existing = allResults.get(r.getId());
            if (existing != null) {
                // Document appears in both lists - add RRF scores
                existing.setScore(existing.getScore() + (float) (1.0 / (i + RRF_K)));
            } else {
                // Document only in BM25 results
                r = copyResult(r);
                r.setScore((float) (1.0 / (i + RRF_K)));
                allResults.put(r.getId(), r);
            }
        }

        // Sort by fused RRF score and return topK
        return allResults.values().stream()
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Create a copy of SearchResult to avoid modifying cached objects.
     */
    private SearchResult copyResult(SearchResult original) {
        SearchResult copy = new SearchResult();
        copy.setId(original.getId());
        copy.setContent(original.getContent());
        copy.setScore(original.getScore());
        copy.setMetadata(original.getMetadata());
        return copy;
    }

    /**
     * Apply reranking if enabled, otherwise just limit to topK.
     */
    private List<SearchResult> applyRerankOrLimit(String query, List<SearchResult> results, int finalTopK) {
        if (rerankerService != null && rerankerService.isEnabled()) {
            try {
                long rerankStart = System.currentTimeMillis();
                List<SearchResult> reranked = rerankerService.rerank(query, results, finalTopK);
                long rerankDuration = System.currentTimeMillis() - rerankStart;
                logger.info("Reranker completed, returned {} results, duration: {}ms", reranked.size(), rerankDuration);
                return reranked;
            } catch (Exception e) {
                logger.warn("Reranker failed, using hybrid results: {}", e.getMessage());
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
