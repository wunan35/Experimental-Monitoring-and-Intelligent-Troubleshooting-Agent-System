package org.example.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reranks vector-search candidates with BGE-Reranker-large.
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

    @Value("${reranker.max-sequence-length:512}")
    private int maxSequenceLength;

    @Value("${reranker.max-query-tokens:128}")
    private int maxQueryTokens;

    @Value("${reranker.max-document-tokens:384}")
    private int maxDocumentTokens;

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
            logger.info("Reranker disabled, skipping model load");
            return;
        }

        try {
            logger.info("Loading reranker tokenizer: {}", modelPath);
            try {
                tokenizer = HuggingFaceTokenizer.newInstance(modelPath);
            } catch (Exception e) {
                logger.warn("Failed to load tokenizer from {}, falling back to BAAI/bge-reranker-large: {}", modelPath, e.getMessage());
                tokenizer = HuggingFaceTokenizer.newInstance("BAAI/bge-reranker-large");
            }

            Path modelFilePath = Paths.get(modelPath, "model.onnx");
            if (!Files.exists(modelFilePath)) {
                String envPath = System.getenv("RERANKER_MODEL_PATH");
                if (envPath != null) {
                    modelFilePath = Paths.get(envPath, "model.onnx");
                }
                if (!Files.exists(modelFilePath)) {
                    throw new IOException("Reranker model.onnx not found. Configure reranker.model-path or RERANKER_MODEL_PATH.");
                }
            }

            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);

            session = ortEnvironment.createSession(modelFilePath.toString(), options);
            initialized = true;
            logger.info(
                    "BGE reranker initialized. topK={}, maxResults={}, maxSequenceLength={}",
                    topK, maxResultsToRerank, maxSequenceLength);

        } catch (Exception e) {
            logger.error("Failed to load BGE reranker, reranker will be disabled", e);
            initialized = false;
        }
    }

    public boolean isEnabled() {
        return enabled && initialized;
    }

    public List<VectorSearchService.SearchResult> rerank(
            String query,
            List<VectorSearchService.SearchResult> results) {
        return rerank(query, results, topK);
    }

    public List<VectorSearchService.SearchResult> rerank(
            String query,
            List<VectorSearchService.SearchResult> results,
            int finalTopK) {

        if (!isEnabled()) {
            return limit(results, finalTopK);
        }

        if (results == null || results.isEmpty()) {
            return results;
        }

        if (cacheEnabled && cacheService != null) {
            Optional<List<RerankerCacheService.CachedRankResult>> cached =
                    cacheService.getRerankResult(query, results.size(), finalTopK);
            if (cached.isPresent()) {
                return convertFromCached(cached.get());
            }
        }

        long startTime = System.currentTimeMillis();

        try {
            List<VectorSearchService.SearchResult> toRerank =
                    results.stream().limit(maxResultsToRerank).collect(Collectors.toList());

            List<long[]> inputIdsList = new ArrayList<>();
            List<long[]> attentionMaskList = new ArrayList<>();
            int maxLen = 0;

            for (VectorSearchService.SearchResult result : toRerank) {
                EncodedPair encodedPair = encodePair(query, result.getContent());
                inputIdsList.add(encodedPair.inputIds);
                attentionMaskList.add(encodedPair.attentionMask);
                maxLen = Math.max(maxLen, encodedPair.inputIds.length);
            }

            long[][] inputIds = new long[toRerank.size()][maxLen];
            long[][] attentionMask = new long[toRerank.size()][maxLen];

            for (int i = 0; i < toRerank.size(); i++) {
                long[] ids = inputIdsList.get(i);
                long[] mask = attentionMaskList.get(i);
                System.arraycopy(ids, 0, inputIds[i], 0, ids.length);
                System.arraycopy(mask, 0, attentionMask[i], 0, mask.length);
            }

            Map<String, OnnxTensor> inputs = new HashMap<>();
            try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds);
                 OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMask)) {

                inputs.put("input_ids", inputIdsTensor);
                inputs.put("attention_mask", attentionMaskTensor);

                try (OrtSession.Result ortResult = session.run(inputs)) {
                    float[] scores = extractScores(ortResult.get(0), toRerank.size());
                    List<IndexedResult> indexedResults = new ArrayList<>();
                    for (int i = 0; i < toRerank.size(); i++) {
                        indexedResults.add(new IndexedResult(toRerank.get(i), scores[i]));
                    }

                    indexedResults.sort((a, b) -> Float.compare(b.score, a.score));

                    List<VectorSearchService.SearchResult> reranked = indexedResults.stream()
                            .limit(finalTopK)
                            .map(ir -> {
                                VectorSearchService.SearchResult result = ir.result;
                                result.setScore(ir.score);
                                return result;
                            })
                            .collect(Collectors.toList());

                    if (cacheEnabled && cacheService != null) {
                        cacheService.putRerankResult(query, results.size(), finalTopK, convertToCached(reranked));
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    if (metricsService != null) {
                        metricsService.recordRerank(1, reranked.size());
                        metricsService.recordRerankDuration(Duration.ofMillis(duration), 1);
                    }

                    logger.info("Reranker completed, input={}, returned={}, duration={}ms", toRerank.size(), reranked.size(), duration);
                    return reranked;
                }
            }

        } catch (Exception e) {
            logger.error("Reranker failed, falling back to vector order", e);
            return limit(results, finalTopK);
        }
    }

    private EncodedPair encodePair(String query, String document) {
        String limitedQuery = limitTextByTokens(query, maxQueryTokens);
        String limitedDocument = limitTextByTokens(document, maxDocumentTokens);

        var encoding = tokenizer.encode(limitedQuery, limitedDocument);
        long[] ids = truncate(encoding.getIds(), maxSequenceLength);
        long[] mask = truncate(encoding.getAttentionMask(), ids.length);
        return new EncodedPair(ids, mask);
    }

    private String limitTextByTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty() || maxTokens <= 0) {
            return "";
        }

        try {
            List<String> tokens = tokenizer.tokenize(text);
            if (tokens.size() <= maxTokens) {
                return text;
            }
            return tokenizer.buildSentence(tokens.subList(0, maxTokens));
        } catch (Exception e) {
            int maxChars = Math.min(text.length(), Math.max(1, maxTokens) * 4);
            return text.substring(0, maxChars);
        }
    }

    private long[] truncate(long[] values, int maxLength) {
        if (values.length <= maxLength) {
            return values;
        }
        return Arrays.copyOf(values, maxLength);
    }

    private float[] extractScores(OnnxValue outputValue, int expectedCount) throws Exception {
        Object output = outputValue.getValue();

        if (output instanceof float[]) {
            float[] scores = (float[]) output;
            validateScoreCount(scores.length, expectedCount);
            return scores;
        }

        if (output instanceof float[][]) {
            float[][] matrix = (float[][]) output;
            float[] scores = new float[expectedCount];

            if (matrix.length == expectedCount) {
                for (int i = 0; i < expectedCount; i++) {
                    if (matrix[i].length == 0) {
                        throw new IllegalStateException("Empty reranker score row at index " + i);
                    }
                    scores[i] = matrix[i][0];
                }
                return scores;
            }

            if (matrix.length == 1 && matrix[0].length == expectedCount) {
                return matrix[0];
            }

            throw new IllegalStateException(
                    "Unexpected reranker output shape: [" + matrix.length + ", "
                            + (matrix.length == 0 ? 0 : matrix[0].length) + "]");
        }

        throw new IllegalStateException("Unsupported reranker output type: " + output.getClass());
    }

    private void validateScoreCount(int scoreCount, int expectedCount) {
        if (scoreCount < expectedCount) {
            throw new IllegalStateException(
                    "Reranker returned too few scores: expected " + expectedCount + ", got " + scoreCount);
        }
    }

    private List<VectorSearchService.SearchResult> limit(
            List<VectorSearchService.SearchResult> results,
            int finalTopK) {
        if (results == null) {
            return List.of();
        }
        return results.stream().limit(finalTopK).collect(Collectors.toList());
    }

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

    private static class EncodedPair {
        private final long[] inputIds;
        private final long[] attentionMask;

        private EncodedPair(long[] inputIds, long[] attentionMask) {
            this.inputIds = inputIds;
            this.attentionMask = attentionMask;
        }
    }

    private static class IndexedResult {
        private final VectorSearchService.SearchResult result;
        private final float score;

        private IndexedResult(VectorSearchService.SearchResult result, float score) {
            this.result = result;
            this.score = score;
        }
    }
}
