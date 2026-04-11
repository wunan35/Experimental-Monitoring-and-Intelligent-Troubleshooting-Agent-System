package org.example.config;

import org.example.service.AgentExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 优雅关闭控制器
 * 提供手动触发优雅关闭的接口
 */
@RestController
@RequestMapping("/api/admin")
public class AgentShutdownEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(AgentShutdownEndpoint.class);

    @Autowired(required = false)
    private AgentExecutorService agentExecutorService;

    /**
     * 获取当前执行状态
     */
    @RequestMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        if (agentExecutorService != null) {
            AgentExecutorService.AgentExecutionStatus execStatus = agentExecutorService.getStatus();
            status.put("agentExecutor", Map.of(
                    "maxConcurrent", execStatus.maxConcurrent(),
                    "activeCount", execStatus.activeCount(),
                    "queuedCount", execStatus.queuedCount(),
                    "availablePermits", execStatus.availablePermits(),
                    "utilizationRate", String.format("%.2f%%", execStatus.getUtilizationRate() * 100),
                    "totalExecuted", execStatus.totalExecuted(),
                    "totalRejected", execStatus.totalRejected(),
                    "hasAvailableSlots", agentExecutorService.hasAvailableSlots()
            ));
        }

        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    /**
     * 准备关闭（停止接收新任务）
     */
    @PostMapping("/prepare-shutdown")
    public Map<String, Object> prepareShutdown() {
        logger.info("收到准备关闭请求");

        Map<String, Object> result = new HashMap<>();

        if (agentExecutorService != null) {
            AgentExecutorService.AgentExecutionStatus status = agentExecutorService.getStatus();

            if (status.activeCount() > 0) {
                result.put("status", "WAITING");
                result.put("message", String.format("等待 %d 个活跃任务完成", status.activeCount()));
                result.put("activeCount", status.activeCount());
            } else {
                result.put("status", "READY");
                result.put("message", "没有活跃任务，可以安全关闭");
            }
        } else {
            result.put("status", "READY");
            result.put("message", "Agent执行服务未初始化");
        }

        return result;
    }
}
