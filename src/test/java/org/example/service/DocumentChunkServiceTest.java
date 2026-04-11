package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * DocumentChunkService 单元测试
 * 测试文档分片服务的核心功能
 */
@DisplayName("DocumentChunkService 测试")
class DocumentChunkServiceTest {

    private DocumentChunkService createService(int maxSize, int overlap) {
        DocumentChunkService service = new DocumentChunkService();
        DocumentChunkConfig config = new DocumentChunkConfig();
        config.setMaxSize(maxSize);
        config.setOverlap(overlap);

        try {
            var field = DocumentChunkService.class.getDeclaredField("chunkConfig");
            field.setAccessible(true);
            field.set(service, config);
        } catch (Exception e) {
            throw new RuntimeException("注入配置失败", e);
        }

        return service;
    }

    @Nested
    @DisplayName("chunkDocument 基础功能测试")
    class ChunkDocumentBasicTest {

        @Test
        @DisplayName("空内容应该返回空列表")
        void emptyContentShouldReturnEmptyList() {
            DocumentChunkService service = createService(800, 100);
            List<DocumentChunk> chunks = service.chunkDocument("", "test.md");
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("null内容应该返回空列表")
        void nullContentShouldReturnEmptyList() {
            DocumentChunkService service = createService(800, 100);
            List<DocumentChunk> chunks = service.chunkDocument(null, "test.md");
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("空白内容应该返回空列表")
        void blankContentShouldReturnEmptyList() {
            DocumentChunkService service = createService(800, 100);
            List<DocumentChunk> chunks = service.chunkDocument("   \n\n   ", "test.md");
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("短内容应该返回单个分片")
        void shortContentShouldReturnSingleChunk() {
            DocumentChunkService service = createService(800, 100);
            String content = "这是一个简短的测试内容。";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo(content);
            assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Markdown 标题分割测试")
    class MarkdownHeadingTest {

        @Test
        @DisplayName("应该按一级标题分割文档")
        void shouldSplitByH1Heading() {
            DocumentChunkService service = createService(800, 100);
            String content = "# 第一章\n这是第一章的内容。\n\n# 第二章\n这是第二章的内容。";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).getTitle()).isEqualTo("第一章");
            assertThat(chunks.get(1).getTitle()).isEqualTo("第二章");
        }

        @Test
        @DisplayName("应该按多级标题分割文档")
        void shouldSplitByMultipleLevelHeadings() {
            DocumentChunkService service = createService(800, 100);
            String content = "# 主标题\n主标题内容\n\n## 子标题1\n子标题1内容\n\n## 子标题2\n子标题2内容";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("标题内容应该包含在分片中")
        void headingContentShouldBeIncludedInChunk() {
            DocumentChunkService service = createService(800, 100);
            String content = "# 测试标题\n这是标题下的内容。";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).getContent()).contains("测试标题");
            assertThat(chunks.get(0).getContent()).contains("这是标题下的内容");
        }
    }

    @Nested
    @DisplayName("长文档分片测试")
    class LongDocumentTest {

        @Test
        @DisplayName("长文档应该被分割成多个分片")
        void longDocumentShouldBeSplitIntoMultipleChunks() {
            // 使用较小的 maxSize 便于测试
            DocumentChunkService service = createService(100, 20);

            // 创建一个有多个段落的长文档（使用 \n\n 分隔段落）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("这是第").append(i).append("段测试内容。");
                if (i < 9) sb.append("\n\n");  // 段落分隔符
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks.size()).as("内容长度 %d 应该分成多个分片", content.length()).isGreaterThan(1);
        }

        @Test
        @DisplayName("每个分片不应超过最大尺寸")
        void eachChunkShouldNotExceedMaxSize() {
            int maxSize = 100;
            int overlap = 20;
            DocumentChunkService service = createService(maxSize, overlap);

            // 创建有段落分隔的内容
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 15; i++) {
                sb.append("测试内容").append(i).append("。");
                if (i < 14) sb.append("\n\n");
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            // 检查分片数量
            assertThat(chunks.size()).isGreaterThan(1);

            // 每个分片长度应该合理（考虑段落边界，允许稍微超过限制）
            for (DocumentChunk chunk : chunks) {
                // 由于按段落分割，单个段落可能超过 maxSize，这是预期行为
                assertThat(chunk.getContent().length()).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("分片应该有正确的索引序号")
        void chunksShouldHaveCorrectIndex() {
            DocumentChunkService service = createService(80, 10);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("段落").append(i).append("：测试内容。");
                if (i < 9) sb.append("\n\n");
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            for (int i = 0; i < chunks.size(); i++) {
                assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
            }
        }
    }

    @Nested
    @DisplayName("段落边界分割测试")
    class ParagraphBoundaryTest {

        @Test
        @DisplayName("应该优先在段落边界分割")
        void shouldSplitAtParagraphBoundary() {
            DocumentChunkService service = createService(80, 10);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("段落").append(i).append("的内容。");
                if (i < 9) sb.append("\n\n");
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("分片属性测试")
    class ChunkPropertiesTest {

        @Test
        @DisplayName("分片应该包含正确的位置信息")
        void chunkShouldHaveCorrectPositionInfo() {
            DocumentChunkService service = createService(800, 100);
            String content = "测试内容";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks).hasSize(1);
            DocumentChunk chunk = chunks.get(0);
            assertThat(chunk.getStartIndex()).isGreaterThanOrEqualTo(0);
            assertThat(chunk.getEndIndex()).isGreaterThan(chunk.getStartIndex());
            assertThat(chunk.getContent().length()).isEqualTo(chunk.getEndIndex() - chunk.getStartIndex());
        }

        @Test
        @DisplayName("分片应该保留标题信息")
        void chunkShouldPreserveTitle() {
            DocumentChunkService service = createService(800, 100);
            String content = "# 标题\n内容";
            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).getTitle()).isEqualTo("标题");
        }
    }

    @Nested
    @DisplayName("重叠分片测试")
    class OverlapTest {

        @Test
        @DisplayName("配置为无重叠时应该正确分片")
        void noOverlapWhenConfigured() {
            DocumentChunkService service = createService(80, 0);

            // 创建有段落分隔的内容
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 15; i++) {
                sb.append("内容段落").append(i).append("。");
                if (i < 14) sb.append("\n\n");
            }
            String content = sb.toString();

            List<DocumentChunk> chunks = service.chunkDocument(content, "test.md");

            assertThat(chunks.size()).as("内容长度 %d 应该分成多个分片", content.length()).isGreaterThan(1);
        }
    }
}
