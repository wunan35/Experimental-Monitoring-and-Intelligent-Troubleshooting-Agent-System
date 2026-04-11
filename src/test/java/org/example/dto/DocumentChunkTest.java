package org.example.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * DocumentChunk 单元测试
 */
@DisplayName("DocumentChunk 测试")
class DocumentChunkTest {

    @Test
    @DisplayName("应该正确创建 DocumentChunk")
    void shouldCreateDocumentChunk() {
        // Arrange & Act
        DocumentChunk chunk = new DocumentChunk("测试内容", 0, 10, 1);

        // Assert
        assertThat(chunk.getContent()).isEqualTo("测试内容");
        assertThat(chunk.getStartIndex()).isEqualTo(0);
        assertThat(chunk.getEndIndex()).isEqualTo(10);
        assertThat(chunk.getChunkIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确设置和获取标题")
    void shouldSetAndGetTitle() {
        // Arrange
        DocumentChunk chunk = new DocumentChunk();

        // Act
        chunk.setTitle("测试标题");

        // Assert
        assertThat(chunk.getTitle()).isEqualTo("测试标题");
    }

    @Test
    @DisplayName("toString 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
        // Arrange
        DocumentChunk chunk = new DocumentChunk("测试内容", 0, 10, 1);
        chunk.setTitle("标题");

        // Act
        String result = chunk.toString();

        // Assert
        assertThat(result).contains("chunkIndex=1");
        assertThat(result).contains("标题");
        assertThat(result).contains("contentLength=4");
    }

    @Test
    @DisplayName("默认构造函数应该创建空对象")
    void defaultConstructorShouldCreateEmptyObject() {
        // Act
        DocumentChunk chunk = new DocumentChunk();

        // Assert
        assertThat(chunk.getContent()).isNull();
        assertThat(chunk.getTitle()).isNull();
        assertThat(chunk.getStartIndex()).isEqualTo(0);
        assertThat(chunk.getEndIndex()).isEqualTo(0);
        assertThat(chunk.getChunkIndex()).isEqualTo(0);
    }
}
