package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 土木规范文档查询工具
 *
 * <p>使用 RAG (Retrieval-Augmented Generation) 从土木工程规范知识库检索相关文档。</p>
 *
 * <h2>知识库内容</h2>
 * <ul>
 *   <li>混凝土结构设计规范 (GB 50010)</li>
 *   <li>钢结构设计标准 (GB 50017)</li>
 *   <li>建筑结构荷载规范 (GB 50009)</li>
 *   <li>土木实验方法标准</li>
 *   <li>材料性能测试规程</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <ul>
 *   <li>{@code rag.top-k} - 返回的相关文档数量（默认 3）</li>
 * </ul>
 *
 * <h2>返回数据结构</h2>
 * <pre>{@code
 * [
 *   {
 *     "content": "混凝土强度等级应按立方体抗压强度标准值确定...",
 *     "score": 0.85,
 *     "metadata": {
 *       "source": "GB50010-2010.pdf",
 *       "page": 15
 *     }
 *   }
 * ]
 * }</pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 查询混凝土强度相关规范
 * String docs = queryInternalDocs("混凝土抗压强度标准值");
 * }</pre>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 * @see VectorSearchService
 */
@Component
public class InternalDocsTools {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    
    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";
    
    private final VectorSearchService vectorSearchService;
    
    @Value("${rag.top-k:3}")
    private int topK = 3; // 默认值
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数注入依赖
     * Spring 会自动注入 VectorSearchService
     */
    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    /**
     * 查询土木规范文档工具
     *
     * @param query 搜索查询，描述您要查找的规范、标准或处理方法
     * @return JSON 格式的搜索结果，包含相关规范文档内容、相似度分数和元数据
     */
    @Tool(description = "Use this tool to search civil engineering specifications, standards, and technical documents for relevant information. " +
            "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract technical guidelines. " +
            "This is useful when you need to understand engineering specifications, material standards, structural design codes, " +
            "or experiment procedures stored in the civil engineering knowledge base.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what specifications, standards, or technical information you are looking for")
            String query) {
        

        try {
            // 使用向量搜索服务检索相关文档
            List<VectorSearchService.SearchResult> searchResults = 
                    vectorSearchService.searchSimilarDocuments(query, topK);
            
            if (searchResults.isEmpty()) {
                return "{\"status\": \"no_results\", \"message\": \"No relevant civil engineering specifications found in the knowledge base.\"}";
            }
            
            // 将搜索结果转换为 JSON 格式
            String resultJson = objectMapper.writeValueAsString(searchResults);
            

            return resultJson;
            
        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocs 执行失败", e);
            return String.format("{\"status\": \"error\", \"message\": \"Failed to query civil engineering specifications: %s\"}",
                    e.getMessage());
        }
    }
}
