package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义分片服务
 * 使用句子相似度计算进行文档分片，保持语义完整性
 */
@Service
public class SemanticChunkService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticChunkService.class);

    @Autowired
    private DocumentChunkConfig chunkConfig;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private DocumentChunkService documentChunkService;

    // 语义相似度阈值，低于此阈值的句子将被分到不同分片
    private static final float SEMANTIC_SIMILARITY_THRESHOLD = 0.7f;

    // 最小分片大小（字符数），避免过小分片
    private static final int MIN_CHUNK_SIZE = 100;

    // 最大分片大小（字符数）
    private static final int MAX_CHUNK_SIZE = 1600;

    /**
     * 语义分片文档
     * 基于句子相似度将文档划分为语义连贯的分片
     *
     * @param content 文档内容
     * @param filePath 文件路径（用于日志）
     * @return 文档分片列表
     */
    public List<DocumentChunk> chunkDocumentSemantically(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        logger.info("开始语义分片文档: {}", filePath);

        try {
            // 1. 分割为句子
            List<String> sentences = splitIntoSentences(content);
            logger.info("文档分割为 {} 个句子", sentences.size());

            if (sentences.isEmpty()) {
                logger.warn("文档没有有效的句子");
                return chunks;
            }

            // 2. 为每个句子生成嵌入向量
            List<List<Float>> sentenceEmbeddings = embeddingService.generateEmbeddings(sentences);
            logger.info("句子嵌入生成完成");

            // 3. 递归分片：基于语义相似度合并句子
            List<TextSegment> segments = createSemanticSegments(sentences, sentenceEmbeddings);

            // 4. 构建文档分片
            int chunkIndex = 0;
            int currentPosition = 0;

            for (TextSegment segment : segments) {
                // 如果分段太大，进一步分割
                if (segment.text.length() > chunkConfig.getMaxSize()) {
                    List<DocumentChunk> subChunks = recursivelySplitLargeSegment(segment, currentPosition, chunkIndex);
                    chunks.addAll(subChunks);
                    chunkIndex += subChunks.size();
                } else {
                    DocumentChunk chunk = new DocumentChunk(
                        segment.text,
                        currentPosition,
                        currentPosition + segment.text.length(),
                        chunkIndex++
                    );
                    chunks.add(chunk);
                }
                currentPosition += segment.text.length();
            }

            logger.info("语义分片完成: {} -> {} 个分片", filePath, chunks.size());
            return chunks;

        } catch (Exception e) {
            logger.error("语义分片失败: {}", filePath, e);
            // 回退到传统分片
            return fallbackToTraditionalChunking(content, filePath);
        }
    }

    /**
     * 分割文本为句子
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // 简单的句子分割：按句号、问号、感叹号分割
        String[] parts = text.split("[。！？；;!?]+");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= 10) { // 过滤太短的句子
                sentences.add(trimmed);
            }
        }

        // 如果没有找到句子，将整个文本作为一个句子
        if (sentences.isEmpty()) {
            sentences.add(text.trim());
        }

        return sentences;
    }

    /**
     * 创建语义分段
     */
    private List<TextSegment> createSemanticSegments(List<String> sentences, List<List<Float>> embeddings) {
        List<TextSegment> segments = new ArrayList<>();

        if (sentences.isEmpty()) {
            return segments;
        }

        StringBuilder currentSegment = new StringBuilder(sentences.get(0));
        List<List<Float>> currentSegmentEmbeddings = new ArrayList<>();
        currentSegmentEmbeddings.add(embeddings.get(0));

        for (int i = 1; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            List<Float> sentenceEmbedding = embeddings.get(i);

            // 计算当前分段与句子的语义相似度
            float similarity = embeddingService.calculateCosineSimilarity(
                calculateAverageEmbedding(currentSegmentEmbeddings),
                sentenceEmbedding
            );

            // 如果相似度低于阈值或分段过大，开始新分段
            if (similarity < SEMANTIC_SIMILARITY_THRESHOLD ||
                currentSegment.length() + sentence.length() > MAX_CHUNK_SIZE) {

                segments.add(new TextSegment(currentSegment.toString()));

                currentSegment = new StringBuilder(sentence);
                currentSegmentEmbeddings = new ArrayList<>();
                currentSegmentEmbeddings.add(sentenceEmbedding);
            } else {
                // 合并到当前分段
                currentSegment.append(" ").append(sentence);
                currentSegmentEmbeddings.add(sentenceEmbedding);
            }
        }

        // 添加最后一个分段
        if (currentSegment.length() > 0) {
            segments.add(new TextSegment(currentSegment.toString()));
        }

        return segments;
    }

    /**
     * 计算平均嵌入
     */
    private List<Float> calculateAverageEmbedding(List<List<Float>> embeddings) {
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("嵌入列表不能为空");
        }

        int dimension = embeddings.get(0).size();
        List<Float> average = new ArrayList<>(dimension);

        // 初始化为零向量
        for (int i = 0; i < dimension; i++) {
            average.add(0.0f);
        }

        // 累加所有嵌入向量
        for (List<Float> embedding : embeddings) {
            for (int i = 0; i < dimension; i++) {
                average.set(i, average.get(i) + embedding.get(i));
            }
        }

        // 计算平均值
        int count = embeddings.size();
        for (int i = 0; i < dimension; i++) {
            average.set(i, average.get(i) / count);
        }

        return average;
    }

    /**
     * 递归分割大分段
     */
    private List<DocumentChunk> recursivelySplitLargeSegment(TextSegment segment, int startPosition, int startChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 如果分段仍然太大，继续递归分割
        if (segment.getText().length() > chunkConfig.getMaxSize()) {
            // 尝试在段落边界分割
            String[] paragraphs = segment.getText().split("\n\n+");

            if (paragraphs.length > 1) {
                // 按段落分割
                int currentPos = startPosition;
                int chunkIdx = startChunkIndex;

                for (String paragraph : paragraphs) {
                    if (paragraph.trim().isEmpty()) continue;

                    if (paragraph.length() > chunkConfig.getMaxSize()) {
                        // 段落仍然太大，递归处理
                        List<DocumentChunk> subChunks = recursivelySplitLargeSegment(
                            new TextSegment(paragraph), currentPos, chunkIdx
                        );
                        chunks.addAll(subChunks);
                        chunkIdx += subChunks.size();
                    } else {
                        DocumentChunk chunk = new DocumentChunk(
                            paragraph,
                            currentPos,
                            currentPos + paragraph.length(),
                            chunkIdx++
                        );
                        chunks.add(chunk);
                    }
                    currentPos += paragraph.length();
                }
            } else {
                // 没有段落边界，按固定大小分割
                int chunkSize = chunkConfig.getMaxSize();
                int overlap = chunkConfig.getOverlap();

                int pos = 0;
                int chunkIdx = startChunkIndex;
                int currentPos = startPosition;

                while (pos < segment.getText().length()) {
                    int endPos = Math.min(pos + chunkSize, segment.getText().length());
                    String chunkText = segment.getText().substring(pos, endPos);

                    DocumentChunk chunk = new DocumentChunk(
                        chunkText,
                        currentPos,
                        currentPos + chunkText.length(),
                        chunkIdx++
                    );
                    chunks.add(chunk);

                    currentPos += chunkText.length();
                    pos = endPos - overlap; // 应用重叠

                    if (pos <= 0) pos = endPos; // 防止无限循环
                }
            }
        } else {
            // 分段大小合适，直接创建分片
            DocumentChunk chunk = new DocumentChunk(
                segment.getText(),
                startPosition,
                startPosition + segment.getText().length(),
                startChunkIndex
            );
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 回退到传统分片
     */
    private List<DocumentChunk> fallbackToTraditionalChunking(String content, String filePath) {
        logger.warn("语义分片失败，回退到传统分片: {}", filePath);
        return documentChunkService.chunkDocument(content, filePath);
    }

    /**
     * 文本分段内部类
     */
    private static class TextSegment {
        String text;

        TextSegment(String text) {
            this.text = text;
        }

        String getText() {
            return text;
        }
    }
}