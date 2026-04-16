package org.example.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RerankerService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RerankerServiceTest {

    @Mock
    private HuggingFaceTokenizer mockTokenizer;

    @Mock
    private OrtEnvironment mockOrtEnvironment;

    @Mock
    private OrtSession mockOrtSession;

    private RerankerService rerankerService;

    @BeforeEach
    void setUp() {
        rerankerService = new RerankerService();
    }

    /**
     * 测试 tokenizer encode 方法的返回值
     */
    @Test
    void testTokenizerEncode() throws Exception {
        // 创建临时tokenizer用于测试
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance("bert-base-cased");

        // 测试文本
        String text = "This is a test sentence [SEP] This is a document";

        // encode 返回的对象应该包含 getIds() 和 getAttentionMask() 方法
        var encoding = tokenizer.encode(text);

        assertNotNull(encoding);
        assertNotNull(encoding.getIds());
        assertNotNull(encoding.getAttentionMask());

        System.out.println("IDs length: " + encoding.getIds().length);
        System.out.println("Mask length: " + encoding.getAttentionMask().length);
    }

    /**
     * 测试多个文本的批量处理
     */
    @Test
    void testBatchProcessing() throws Exception {
        // 创建临时tokenizer
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance("bert-base-cased");

        // 测试多个对
        List<String> pairs = Arrays.asList(
            "What is machine learning? [SEP] Machine learning is a subset of AI.",
            "How deep learning works? [SEP] Deep learning uses neural networks."
        );

        List<long[]> inputIdsList = new ArrayList<>();
        List<long[]> attentionMaskList = new ArrayList<>();

        int maxLen = 0;

        for (String pair : pairs) {
            var encoding = tokenizer.encode(pair);

            long[] ids = encoding.getIds();
            long[] mask = encoding.getAttentionMask();

            inputIdsList.add(ids);
            attentionMaskList.add(mask);

            if (ids.length > maxLen) {
                maxLen = ids.length;
            }
        }

        // 测试填充
        long[][] inputIds = new long[pairs.size()][maxLen];
        long[][] attentionMask = new long[pairs.size()][maxLen];

        for (int i = 0; i < pairs.size(); i++) {
            long[] ids = inputIdsList.get(i);
            long[] mask = attentionMaskList.get(i);

            System.arraycopy(ids, 0, inputIds[i], 0, ids.length);
            System.arraycopy(mask, 0, attentionMask[i], 0, mask.length);
        }

        // 验证填充结果
        assertEquals(maxLen, inputIds[0].length);
        assertEquals(maxLen, inputIds[1].length);

        System.out.println("Max length: " + maxLen);
        System.out.println("First sample IDs: " + Arrays.toString(inputIds[0]));
    }
}