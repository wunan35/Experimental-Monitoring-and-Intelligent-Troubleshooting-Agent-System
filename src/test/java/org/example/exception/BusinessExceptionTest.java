package org.example.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * BusinessException 单元测试
 * 测试业务异常类的功能
 */
@DisplayName("BusinessException 测试")
class BusinessExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("使用消息构造异常")
        void constructWithMessage() {
            // Arrange & Act
            BusinessException exception = new BusinessException("测试错误消息");

            // Assert
            assertThat(exception.getMessage()).isEqualTo("测试错误消息");
            assertThat(exception.getCode()).isEqualTo("BUSINESS_ERROR");
        }

        @Test
        @DisplayName("使用错误码和消息构造异常")
        void constructWithCodeAndMessage() {
            // Arrange & Act
            BusinessException exception = new BusinessException("INVALID_PARAM", "参数无效");

            // Assert
            assertThat(exception.getMessage()).isEqualTo("参数无效");
            assertThat(exception.getCode()).isEqualTo("INVALID_PARAM");
        }

        @Test
        @DisplayName("使用消息和原因构造异常")
        void constructWithMessageAndCause() {
            // Arrange
            Throwable cause = new RuntimeException("原始异常");

            // Act
            BusinessException exception = new BusinessException("业务错误", cause);

            // Assert
            assertThat(exception.getMessage()).isEqualTo("业务错误");
            assertThat(exception.getCause()).isEqualTo(cause);
            assertThat(exception.getCode()).isEqualTo("BUSINESS_ERROR");
        }

        @Test
        @DisplayName("使用错误码、消息和原因构造异常")
        void constructWithCodeMessageAndCause() {
            // Arrange
            Throwable cause = new RuntimeException("原始异常");

            // Act
            BusinessException exception = new BusinessException("DB_ERROR", "数据库错误", cause);

            // Assert
            assertThat(exception.getMessage()).isEqualTo("数据库错误");
            assertThat(exception.getCode()).isEqualTo("DB_ERROR");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("异常继承测试")
    class InheritanceTest {

        @Test
        @DisplayName("BusinessException 应该是 RuntimeException 的子类")
        void shouldBeRuntimeException() {
            // Arrange
            BusinessException exception = new BusinessException("测试");

            // Assert
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("可以正常抛出和捕获")
        void canBeThrownAndCaught() {
            // Act & Assert
            assertThatThrownBy(() -> {
                throw new BusinessException("测试异常");
            }).isInstanceOf(BusinessException.class)
              .hasMessage("测试异常");
        }
    }

    @Nested
    @DisplayName("getCode 方法测试")
    class GetCodeTest {

        @Test
        @DisplayName("默认错误码应该是 BUSINESS_ERROR")
        void defaultCodeShouldBeBusinessError() {
            // Arrange
            BusinessException exception = new BusinessException("消息");

            // Act & Assert
            assertThat(exception.getCode()).isEqualTo("BUSINESS_ERROR");
        }

        @Test
        @DisplayName("自定义错误码应该正确返回")
        void customCodeShouldBeReturned() {
            // Arrange
            String customCode = "CUSTOM_ERROR_001";

            // Act
            BusinessException exception = new BusinessException(customCode, "自定义错误");

            // Assert
            assertThat(exception.getCode()).isEqualTo(customCode);
        }
    }
}
