package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * VectorEmbeddingService 单元测试
 * 测试向量嵌入服务的核心功能
 */
@DisplayName("VectorEmbeddingService 测试")
class VectorEmbeddingServiceTest {

    private VectorEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new VectorEmbeddingService();
    }

    @Nested
    @DisplayName("normalizeVector 方法测试")
    class NormalizeVectorTest {

        @Test
        @DisplayName("应该正确归一化非零向量")
        void shouldNormalizeNonZeroVector() {
            // Arrange
            List<Float> vector = Arrays.asList(3.0f, 4.0f);

            // Act
            List<Float> normalized = embeddingService.normalizeVector(vector);

            // Assert
            // 3,4 向量的范数是 5，归一化后应该是 (0.6, 0.8)
            assertThat(normalized).hasSize(2);
            assertThat(normalized.get(0)).isCloseTo(0.6f, within(0.0001f));
            assertThat(normalized.get(1)).isCloseTo(0.8f, within(0.0001f));
        }

        @Test
        @DisplayName("归一化后的向量范数应该为1")
        void normalizedVectorShouldHaveUnitNorm() {
            // Arrange
            List<Float> vector = Arrays.asList(1.0f, 2.0f, 3.0f, 4.0f, 5.0f);

            // Act
            List<Float> normalized = embeddingService.normalizeVector(vector);

            // Assert - 计算范数
            float norm = 0.0f;
            for (Float value : normalized) {
                norm += value * value;
            }
            norm = (float) Math.sqrt(norm);

            assertThat(norm).isCloseTo(1.0f, within(0.0001f));
        }

        @Test
        @DisplayName("单位向量归一化后应该保持不变")
        void unitVectorShouldRemainUnchanged() {
            // Arrange - 单位向量
            List<Float> unitVector = Arrays.asList(1.0f, 0.0f, 0.0f);

            // Act
            List<Float> normalized = embeddingService.normalizeVector(unitVector);

            // Assert
            assertThat(normalized.get(0)).isCloseTo(1.0f, within(0.0001f));
            assertThat(normalized.get(1)).isCloseTo(0.0f, within(0.0001f));
            assertThat(normalized.get(2)).isCloseTo(0.0f, within(0.0001f));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("空向量或null应该抛出异常")
        void nullOrEmptyVectorShouldThrowException(List<Float> invalidVector) {
            assertThatThrownBy(() -> embeddingService.normalizeVector(invalidVector))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("向量不能为空");
        }

        @Test
        @DisplayName("零向量应该抛出异常")
        void zeroVectorShouldThrowException() {
            // Arrange
            List<Float> zeroVector = Arrays.asList(0.0f, 0.0f, 0.0f);

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.normalizeVector(zeroVector))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("向量范数为零");
        }

        @Test
        @DisplayName("负值向量应该正确归一化")
        void negativeValuesShouldBeNormalized() {
            // Arrange
            List<Float> vector = Arrays.asList(-3.0f, -4.0f);

            // Act
            List<Float> normalized = embeddingService.normalizeVector(vector);

            // Assert
            assertThat(normalized.get(0)).isCloseTo(-0.6f, within(0.0001f));
            assertThat(normalized.get(1)).isCloseTo(-0.8f, within(0.0001f));
        }
    }

    @Nested
    @DisplayName("normalizeVectors 批量归一化测试")
    class NormalizeVectorsTest {

        @Test
        @DisplayName("应该正确批量归一化向量列表")
        void shouldNormalizeMultipleVectors() {
            // Arrange
            List<List<Float>> vectors = Arrays.asList(
                    Arrays.asList(3.0f, 4.0f),
                    Arrays.asList(1.0f, 0.0f),
                    Arrays.asList(0.0f, 5.0f)
            );

            // Act
            List<List<Float>> normalized = embeddingService.normalizeVectors(vectors);

            // Assert
            assertThat(normalized).hasSize(3);

            // 第一个向量 (3,4) -> (0.6, 0.8)
            assertThat(normalized.get(0).get(0)).isCloseTo(0.6f, within(0.0001f));
            assertThat(normalized.get(0).get(1)).isCloseTo(0.8f, within(0.0001f));

            // 第二个向量 (1,0) -> (1, 0)
            assertThat(normalized.get(1).get(0)).isCloseTo(1.0f, within(0.0001f));
            assertThat(normalized.get(1).get(1)).isCloseTo(0.0f, within(0.0001f));

            // 第三个向量 (0,5) -> (0, 1)
            assertThat(normalized.get(2).get(0)).isCloseTo(0.0f, within(0.0001f));
            assertThat(normalized.get(2).get(1)).isCloseTo(1.0f, within(0.0001f));
        }

        @Test
        @DisplayName("空列表应该返回空列表")
        void emptyListShouldReturnEmptyList() {
            // Arrange
            List<List<Float>> vectors = Collections.emptyList();

            // Act
            List<List<Float>> normalized = embeddingService.normalizeVectors(vectors);

            // Assert
            assertThat(normalized).isEmpty();
        }
    }

    @Nested
    @DisplayName("calculateCosineSimilarity 余弦相似度测试")
    class CosineSimilarityTest {

        @Test
        @DisplayName("相同向量的余弦相似度应该为1")
        void identicalVectorsShouldHaveSimilarityOne() {
            // Arrange
            List<Float> vector = Arrays.asList(1.0f, 2.0f, 3.0f);

            // Act
            float similarity = embeddingService.calculateCosineSimilarity(vector, vector);

            // Assert
            assertThat(similarity).isCloseTo(1.0f, within(0.0001f));
        }

        @Test
        @DisplayName("正交向量的余弦相似度应该为0")
        void orthogonalVectorsShouldHaveSimilarityZero() {
            // Arrange
            List<Float> vector1 = Arrays.asList(1.0f, 0.0f);
            List<Float> vector2 = Arrays.asList(0.0f, 1.0f);

            // Act
            float similarity = embeddingService.calculateCosineSimilarity(vector1, vector2);

            // Assert
            assertThat(similarity).isCloseTo(0.0f, within(0.0001f));
        }

        @Test
        @DisplayName("相反向量的余弦相似度应该为-1")
        void oppositeVectorsShouldHaveSimilarityMinusOne() {
            // Arrange
            List<Float> vector1 = Arrays.asList(1.0f, 2.0f);
            List<Float> vector2 = Arrays.asList(-1.0f, -2.0f);

            // Act
            float similarity = embeddingService.calculateCosineSimilarity(vector1, vector2);

            // Assert
            assertThat(similarity).isCloseTo(-1.0f, within(0.0001f));
        }

        @Test
        @DisplayName("相似向量的余弦相似度应该在(0,1)之间")
        void similarVectorsShouldHavePositiveSimilarity() {
            // Arrange
            List<Float> vector1 = Arrays.asList(1.0f, 1.0f, 1.0f);
            List<Float> vector2 = Arrays.asList(1.0f, 1.0f, 2.0f);

            // Act
            float similarity = embeddingService.calculateCosineSimilarity(vector1, vector2);

            // Assert
            assertThat(similarity).isGreaterThan(0.0f);
            assertThat(similarity).isLessThan(1.0f);
        }

        @Test
        @DisplayName("不同维度的向量应该抛出异常")
        void differentDimensionVectorsShouldThrowException() {
            // Arrange
            List<Float> vector1 = Arrays.asList(1.0f, 2.0f, 3.0f);
            List<Float> vector2 = Arrays.asList(1.0f, 2.0f);

            // Act & Assert
            assertThatThrownBy(() -> embeddingService.calculateCosineSimilarity(vector1, vector2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("向量维度不匹配");
        }
    }
}
