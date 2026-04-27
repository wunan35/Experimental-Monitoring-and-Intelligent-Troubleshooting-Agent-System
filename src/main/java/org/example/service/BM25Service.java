package org.example.service;

import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索服务
 * 使用改进的 BM25 算法对文档内容进行关键词匹配评分
 */
@Service
public class BM25Service {

    private static final Logger logger = LoggerFactory.getLogger(BM25Service.class);

    // BM25 标准参数
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double AVG_DOC_LEN = 0; // 初始化时计算

    // 倒排索引: term -> list of (docId, termFreq)
    private final Map<String, List<DocTermInfo>> invertedIndex = new ConcurrentHashMap<>();

    // 文档信息: docId -> content
    private final Map<String, String> idToContent = new ConcurrentHashMap<>();

    // 文档信息: docId -> metadata
    private final Map<String, String> idToMetadata = new ConcurrentHashMap<>();

    // 文档长度
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();

    // 文档总数
    private int totalDocs = 0;

    // 平均文档长度
    private double avgDocLen = 0;

    // 文档频率: term -> docFreq
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();

    // 分词器正则（简单中文分词 + 英文）
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\u4e00-\u9fff]+|[a-zA-Z0-9]+");

    /**
     * 添加文档到索引
     */
    public synchronized void addDocument(String id, String content, String metadata) {
        if (id == null || content == null || content.isEmpty()) {
            return;
        }

        // 移除旧文档（如果存在）
        if (idToContent.containsKey(id)) {
            removeDocument(id);
        }

        // 存储文档信息
        idToContent.put(id, content);
        idToMetadata.put(id, metadata);
        int docLen = content.length();
        docLengths.put(id, docLen);
        totalDocs++;

        // 更新平均文档长度
        avgDocLen = (avgDocLen * (totalDocs - 1) + docLen) / totalDocs;

        // 分词并构建倒排索引
        List<String> terms = tokenize(content);
        Map<String, Integer> termFreqMap = new HashMap<>();
        for (String term : terms) {
            termFreqMap.merge(term, 1, Integer::sum);
        }

        // 更新倒排索引和文档频率
        for (Map.Entry<String, Integer> entry : termFreqMap.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            invertedIndex.computeIfAbsent(term, k -> new ArrayList<>())
                    .add(new DocTermInfo(id, tf));

            docFreq.merge(term, 1, Integer::sum);
        }

        logger.debug("添加文档到BM25索引: id={}, 长度={}, term数={}", id, docLen, termFreqMap.size());
    }

    /**
     * 从索引中移除文档
     */
    public synchronized void removeDocument(String id) {
        if (!idToContent.containsKey(id)) {
            return;
        }

        String content = idToContent.get(id);
        List<String> terms = tokenize(content);

        // 从倒排索引中移除
        for (String term : terms) {
            List<DocTermInfo> list = invertedIndex.get(term);
            if (list != null) {
                list.removeIf(info -> info.docId.equals(id));
                if (list.isEmpty()) {
                    invertedIndex.remove(term);
                }
            }
        }

        // 更新文档频率
        Map<String, Integer> termFreqMap = new HashMap<>();
        for (String term : terms) {
            termFreqMap.merge(term, 1, Integer::sum);
        }
        for (String term : termFreqMap.keySet()) {
            Integer freq = docFreq.get(term);
            if (freq != null && freq > 0) {
                docFreq.put(term, freq - 1);
            }
        }

        // 移除文档信息
        idToContent.remove(id);
        idToMetadata.remove(id);
        docLengths.remove(id);
        totalDocs--;

        // 重新计算平均文档长度
        if (totalDocs > 0) {
            avgDocLen = (avgDocLen * (totalDocs + 1) - content.length()) / totalDocs;
        } else {
            avgDocLen = 0;
        }

        logger.debug("从BM25索引移除文档: id={}", id);
    }

    /**
     * 根据源路径前缀移除所有匹配的文档
     * 用于在重新索引时删除旧文档
     */
    public synchronized void removeBySourcePrefix(String sourcePrefix) {
        // 找出所有匹配前缀的文档 ID
        List<String> toRemove = idToContent.keySet().stream()
                .filter(id -> id.startsWith(sourcePrefix))
                .collect(Collectors.toList());

        if (toRemove.isEmpty()) {
            logger.debug("BM25索引中没有找到前缀匹配的文档: {}", sourcePrefix);
            return;
        }

        logger.info("从BM25索引移除 {} 个匹配前缀的文档: {}", toRemove.size(), sourcePrefix);

        for (String id : toRemove) {
            removeDocument(id);
        }
    }

    /**
     * 搜索查询并返回 BM25 评分结果
     */
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isEmpty() || totalDocs == 0) {
            return Collections.emptyList();
        }

        logger.debug("BM25搜索: query={}, topK={}, 总文档数={}", query, topK, totalDocs);

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算每个文档的 BM25 分数
        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            List<DocTermInfo> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            // IDF: log((N - n + 0.5) / (n + 0.5))
            int df = postings.size();
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1);

            for (DocTermInfo info : postings) {
                int docLen = docLengths.getOrDefault(info.docId, 1);
                double tf = info.termFreq;

                // BM25 公式: IDF * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * docLen / avgDocLen))
                double score = idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / Math.max(avgDocLen, 1)));

                scores.merge(info.docId, score, Double::sum);
            }
        }

        // 排序并返回 topK
        List<SearchResult> results = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    SearchResult r = new SearchResult();
                    r.setId(entry.getKey());
                    r.setContent(idToContent.get(entry.getKey()));
                    r.setMetadata(idToMetadata.get(entry.getKey()));
                    r.setScore(entry.getValue().floatValue());
                    return r;
                })
                .collect(Collectors.toList());

        logger.debug("BM25搜索完成: 返回 {} 条结果", results.size());
        return results;
    }

    /**
     * 获取索引中的文档总数
     */
    public int getTotalDocs() {
        return totalDocs;
    }

    /**
     * 清空索引
     */
    public synchronized void clear() {
        invertedIndex.clear();
        idToContent.clear();
        idToMetadata.clear();
        docLengths.clear();
        docFreq.clear();
        totalDocs = 0;
        avgDocLen = 0;
        logger.info("BM25索引已清空");
    }

    /**
     * 简单的中文分词 + 英文分词
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }

        // 转为小写
        text = text.toLowerCase();

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            // 过滤短词（小于2个字符的英文单词）
            if (token.length() >= 2 || isChinese(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private boolean isChinese(String str) {
        return str.length() > 0 && str.charAt(0) >= 0x4e00 && str.charAt(0) <= 0x9fff;
    }

    /**
     * 文档词项信息
     */
    private static class DocTermInfo {
        String docId;
        int termFreq;

        DocTermInfo(String docId, int termFreq) {
            this.docId = docId;
            this.termFreq = termFreq;
        }
    }
}
