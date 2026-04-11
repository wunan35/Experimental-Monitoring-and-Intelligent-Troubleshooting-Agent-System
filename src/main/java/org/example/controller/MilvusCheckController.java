package org.example.controller;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Milvus 测试控制器
 * 用于测试数据库连接和数据读取
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/milvus")
@Tag(name = "health", description = "健康检查 - 系统状态监控")
public class MilvusCheckController {

    @Autowired
    private MilvusServiceClient milvusClient;

    /**
     * 简单的健康检查
     */
    @Operation(
            summary = "Milvus连接检查",
            description = "检查Milvus向量数据库连接状态，返回可用的集合列表"
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> simpleHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(
                ShowCollectionsParam.newBuilder().build()
            );
            
            if (response.getStatus() == 0) {
                result.put("message", "ok");
                result.put("collections", response.getData().getCollectionNamesList());
                return ResponseEntity.ok(result);
            } else {
                result.put("message", response.getMessage());
                return ResponseEntity.status(503).body(result);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(503).body(result);
        }
    }
}
