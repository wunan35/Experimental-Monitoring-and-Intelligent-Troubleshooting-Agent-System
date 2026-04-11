package org.example.config.health;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量数据库健康检查
 */
@Component
public class MilvusHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(MilvusHealthIndicator.class);

    @Autowired(required = false)
    private MilvusServiceClient milvusClient;

    @Override
    public Health health() {
        if (milvusClient == null) {
            return Health.down()
                    .withDetail("error", "Milvus 客户端未初始化")
                    .build();
        }

        try {
            // 检查集合是否存在
            HasCollectionParam param = HasCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build();

            R<Boolean> response = milvusClient.hasCollection(param);

            if (response.getStatus() == 0) {
                return Health.up()
                        .withDetail("connected", true)
                        .withDetail("collection", MilvusConstants.MILVUS_COLLECTION_NAME)
                        .withDetail("collectionExists", response.getData())
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Milvus 查询失败: " + response.getMessage())
                        .build();
            }

        } catch (Exception e) {
            logger.error("Milvus 健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("connected", false)
                    .build();
        }
    }
}
