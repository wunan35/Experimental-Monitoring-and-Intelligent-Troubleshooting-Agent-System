package org.example.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * DocumentChunkConfig 单元测试
 */
@DisplayName("DocumentChunkConfig 测试")
class DocumentChunkConfigTest {

    @Test
    @DisplayName("默认配置应该正确")
    void defaultConfigShouldBeCorrect() {
        // Act
        DocumentChunkConfig config = new DocumentChunkConfig();

        // Assert
        assertThat(config.getMaxSize()).isEqualTo(800);
        assertThat(config.getOverlap()).isEqualTo(100);
        assertThat(config.getStrategy()).isEqualTo("traditional");
    }

    @Test
    @DisplayName("应该能够设置 maxSize")
    void shouldSetMaxSize() {
        // Arrange
        DocumentChunkConfig config = new DocumentChunkConfig();

        // Act
        config.setMaxSize(500);

        // Assert
        assertThat(config.getMaxSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该能够设置 overlap")
    void shouldSetOverlap() {
        // Arrange
        DocumentChunkConfig config = new DocumentChunkConfig();

        // Act
        config.setOverlap(50);

        // Assert
        assertThat(config.getOverlap()).isEqualTo(50);
    }

    @Test
    @DisplayName("应该能够设置 strategy")
    void shouldSetStrategy() {
        // Arrange
        DocumentChunkConfig config = new DocumentChunkConfig();

        // Act
        config.setStrategy("semantic");

        // Assert
        assertThat(config.getStrategy()).isEqualTo("semantic");
    }
}
