package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 土木实验告警查询工具
 *
 * <p>用于查询土木实验中的异常告警信息，支持多种实验类型和参数。</p>
 *
 * <h2>告警类型</h2>
 * <ul>
 *   <li><b>CONCRETE_STRENGTH_ANOMALY</b> - 混凝土强度异常</li>
 *   <li><b>STRUCTURAL_DEFORMATION_EXCEED</b> - 结构变形超限</li>
 *   <li><b>MATERIAL_PROPERTY_FAILURE</b> - 材料性能不合格</li>
 *   <li><b>EXPERIMENT_SYSTEM_FAILURE</b> - 实验系统故障</li>
 *   <li><b>SENSOR_DATA_ABNORMAL</b> - 传感器数据异常</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <ul>
 *   <li>{@code experiment.data-source.url} - 实验监控系统 API 地址</li>
 *   <li>{@code experiment.timeout} - HTTP 请求超时时间（秒）</li>
 *   <li>{@code experiment.mock-enabled} - 是否启用 Mock 模式</li>
 * </ul>
 *
 * <h2>返回数据结构</h2>
 * <pre>{@code
 * {
 *   "success": true,
 *   "alerts": [
 *     {
 *       "alert_id": "alert-concrete-001",
 *       "alert_type": "CONCRETE_STRENGTH_ANOMALY",
 *       "experiment_id": "exp-concrete-20240322-001",
 *       "parameter_name": "抗压强度",
 *       "measured_value": 25.3,
 *       "threshold_value": 30.0,
 *       "unit": "MPa",
 *       "severity": "严重",
 *       "description": "C30混凝土试件28天抗压强度仅达到设计值的84.3%",
 *       "location": "实验室3#压力试验机",
 *       "active_at": "2024-03-22T10:30:00Z",
 *       "duration": "2h15m30s"
 *     }
 *   ],
 *   "message": "成功检索到 5 个活动实验告警"
 * }
 * }</pre>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 * @see QueryLogsTools
 */
@Component
public class QueryMetricsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryMetricsTools.class);

    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_EXPERIMENT_ALERTS = "queryExperimentAlerts";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${experiment.data-source.url:http://localhost:8080/api/experiment/alerts}")
    private String experimentDataSourceUrl;

    @Value("${experiment.timeout:10}")
    private int timeout;

    @Value("${experiment.mock-enabled:true}")
    private boolean mockEnabled;

    private OkHttpClient httpClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .readTimeout(Duration.ofSeconds(timeout))
                .build();
        logger.info("✅ QueryMetricsTools 初始化成功, 实验数据源: {}, Mock模式: {}", experimentDataSourceUrl, mockEnabled);
    }

    /**
     * 查询土木实验活动告警
     * 该工具从实验监控系统检索所有当前活动/触发的实验异常告警
     */
    @Tool(description = "Query active alerts from civil engineering experiment monitoring system. " +
            "This tool retrieves all currently active/firing experiment alerts including their type, severity, " +
            "measured values, thresholds, and affected experiment parameters. " +
            "Use this tool when you need to check what experiment anomalies are currently detected, " +
            "investigate alert conditions, or monitor experiment status.")
    public String queryExperimentAlerts() {
        logger.info("开始查询土木实验活动告警, Mock模式: {}", mockEnabled);

        try {
            List<SimplifiedExperimentAlert> simplifiedAlerts;

            if (mockEnabled) {
                // Mock 模式：返回与土木实验文档关联的模拟告警数据
                simplifiedAlerts = buildMockExperimentAlerts();
                logger.info("使用 Mock 数据，返回 {} 个模拟实验告警", simplifiedAlerts.size());
            } else {
                // 真实模式：调用实验监控系统 API
                ExperimentAlertsResult result = fetchExperimentAlerts();

                if (!"success".equals(result.getStatus())) {
                    return buildErrorResponse("实验监控系统 API 返回非成功状态: " + result.getStatus(), result.getError());
                }

                // 转换为简化格式，对于相同的 alertId，只保留最新的
                Set<String> seenAlertIds = new HashSet<>();
                simplifiedAlerts = new ArrayList<>();

                for (ExperimentAlert alert : result.getData().getAlerts()) {
                    String alertId = alert.getAlertId();

                    // 如果这个 alertId 已经存在，跳过
                    if (seenAlertIds.contains(alertId)) {
                        continue;
                    }

                    // 标记为已见过
                    seenAlertIds.add(alertId);

                    SimplifiedExperimentAlert simplified = new SimplifiedExperimentAlert();
                    simplified.setAlertId(alertId);
                    simplified.setAlertType(alert.getAlertType());
                    simplified.setExperimentId(alert.getExperimentId());
                    simplified.setParameterName(alert.getParameterName());
                    simplified.setMeasuredValue(alert.getMeasuredValue());
                    simplified.setThresholdValue(alert.getThresholdValue());
                    simplified.setUnit(alert.getUnit());
                    simplified.setSeverity(alert.getSeverity());
                    simplified.setDescription(alert.getDescription());
                    simplified.setLocation(alert.getLocation());
                    simplified.setActiveAt(alert.getActiveAt());
                    simplified.setDuration(calculateDuration(alert.getActiveAt()));

                    simplifiedAlerts.add(simplified);
                }
            }

            // 构建成功响应
            ExperimentAlertsOutput output = new ExperimentAlertsOutput();
            output.setSuccess(true);
            output.setAlerts(simplifiedAlerts);
            output.setMessage(String.format("成功检索到 %d 个活动实验告警", simplifiedAlerts.size()));

            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.info("实验告警查询完成: 找到 {} 个告警", simplifiedAlerts.size());

            return jsonResult;

        } catch (RuntimeException | java.io.IOException e) {
            logger.error("查询实验告警失败", e);
            return buildErrorResponse("查询失败", e.getMessage());
        }
    }

    /**
     * 构建 Mock 实验告警数据
     * 与 aiops-docs 文档中的实验异常类型对应：
     * - CONCRETE_STRENGTH_ANOMALY: 混凝土强度异常
     * - STRUCTURAL_DEFORMATION_EXCEED: 结构变形超限
     * - MATERIAL_PROPERTY_FAILURE: 材料性能不合格
     * - EXPERIMENT_SYSTEM_FAILURE: 实验系统故障
     * - SENSOR_DATA_ABNORMAL: 传感器数据异常
     */
    private List<SimplifiedExperimentAlert> buildMockExperimentAlerts() {
        List<SimplifiedExperimentAlert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        // 告警1: 混凝土强度异常 - 持续约2小时
        SimplifiedExperimentAlert concreteAlert = new SimplifiedExperimentAlert();
        concreteAlert.setAlertId("alert-concrete-001");
        concreteAlert.setAlertType("CONCRETE_STRENGTH_ANOMALY");
        concreteAlert.setExperimentId("exp-concrete-20240322-001");
        concreteAlert.setParameterName("抗压强度");
        concreteAlert.setMeasuredValue(25.3);
        concreteAlert.setThresholdValue(30.0);
        concreteAlert.setUnit("MPa");
        concreteAlert.setSeverity("严重");
        concreteAlert.setDescription("C30混凝土试件28天抗压强度仅达到设计值的84.3%，低于规范要求的85%下限。试件编号: CS-20240322-01~03");
        concreteAlert.setLocation("实验室3#压力试验机");
        Instant concreteActiveAt = now.minus(2, ChronoUnit.HOURS);
        concreteAlert.setActiveAt(concreteActiveAt.toString());
        concreteAlert.setDuration(calculateDuration(concreteActiveAt.toString()));
        alerts.add(concreteAlert);

        // 告警2: 结构变形超限 - 持续约45分钟
        SimplifiedExperimentAlert deformationAlert = new SimplifiedExperimentAlert();
        deformationAlert.setAlertId("alert-deformation-001");
        deformationAlert.setAlertType("STRUCTURAL_DEFORMATION_EXCEED");
        deformationAlert.setExperimentId("exp-steelbeam-20240322-001");
        deformationAlert.setParameterName("跨中挠度");
        deformationAlert.setMeasuredValue(18.5);
        deformationAlert.setThresholdValue(15.0);
        deformationAlert.setUnit("mm");
        deformationAlert.setSeverity("严重");
        deformationAlert.setDescription("钢梁在80%设计荷载下跨中挠度达到18.5mm，超过规范限值15mm。荷载级: 80% Fu，监测点位: L/2");
        deformationAlert.setLocation("结构实验室反力墙区域");
        Instant deformationActiveAt = now.minus(45, ChronoUnit.MINUTES);
        deformationAlert.setActiveAt(deformationActiveAt.toString());
        deformationAlert.setDuration(calculateDuration(deformationActiveAt.toString()));
        alerts.add(deformationAlert);

        // 告警3: 传感器数据异常 - 持续约30分钟
        SimplifiedExperimentAlert sensorAlert = new SimplifiedExperimentAlert();
        sensorAlert.setAlertId("alert-sensor-001");
        sensorAlert.setAlertType("SENSOR_DATA_ABNORMAL");
        sensorAlert.setExperimentId("exp-monitoring-20240322-001");
        sensorAlert.setParameterName("应变值");
        sensorAlert.setMeasuredValue(0.0);
        sensorAlert.setThresholdValue(5000.0);
        sensorAlert.setUnit("με");
        sensorAlert.setSeverity("警告");
        sensorAlert.setDescription("光纤光栅传感器FBG-03信号丢失，持续输出零值。传感器位置: 梁底部受拉区，通道: CH-03");
        sensorAlert.setLocation("智能结构实验室");
        Instant sensorActiveAt = now.minus(30, ChronoUnit.MINUTES);
        sensorAlert.setActiveAt(sensorActiveAt.toString());
        sensorAlert.setDuration(calculateDuration(sensorActiveAt.toString()));
        alerts.add(sensorAlert);

        // 告警4: 材料性能不合格 - 持续约3小时
        SimplifiedExperimentAlert materialAlert = new SimplifiedExperimentAlert();
        materialAlert.setAlertId("alert-material-001");
        materialAlert.setAlertType("MATERIAL_PROPERTY_FAILURE");
        materialAlert.setExperimentId("exp-steel-20240322-001");
        materialAlert.setParameterName("屈服强度");
        materialAlert.setMeasuredValue(325.0);
        materialAlert.setThresholdValue(345.0);
        materialAlert.setUnit("MPa");
        materialAlert.setSeverity("严重");
        materialAlert.setDescription("Q345钢材拉伸试验屈服强度仅325MPa，低于标准值345MPa。试件编号: ST-20240322-01，批次: 20240315-A");
        materialAlert.setLocation("材料力学实验室");
        Instant materialActiveAt = now.minus(3, ChronoUnit.HOURS);
        materialAlert.setActiveAt(materialActiveAt.toString());
        materialAlert.setDuration(calculateDuration(materialActiveAt.toString()));
        alerts.add(materialAlert);

        // 告警5: 实验系统故障 - 持续约15分钟
        SimplifiedExperimentAlert systemAlert = new SimplifiedExperimentAlert();
        systemAlert.setAlertId("alert-system-001");
        systemAlert.setAlertType("EXPERIMENT_SYSTEM_FAILURE");
        systemAlert.setExperimentId("exp-fatigue-20240322-001");
        systemAlert.setParameterName("系统状态");
        systemAlert.setMeasuredValue(0.0);
        systemAlert.setThresholdValue(1.0);
        systemAlert.setUnit("状态码");
        systemAlert.setSeverity("紧急");
        systemAlert.setDescription("MTS疲劳试验机液压系统压力异常，错误代码: E-1023，已自动停机。设备编号: MTS-370.02");
        systemAlert.setLocation("疲劳实验室");
        Instant systemActiveAt = now.minus(15, ChronoUnit.MINUTES);
        systemAlert.setActiveAt(systemActiveAt.toString());
        systemAlert.setDuration(calculateDuration(systemActiveAt.toString()));
        alerts.add(systemAlert);

        return alerts;
    }

    /**
     * 从实验监控系统 API 获取告警数据
     */
    private ExperimentAlertsResult fetchExperimentAlerts() throws java.io.IOException {
        String apiUrl = experimentDataSourceUrl;
        logger.debug("请求实验监控系统 API: {}", apiUrl);

        Request request = new Request.Builder()
                .url(apiUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP 请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, ExperimentAlertsResult.class);
        }
    }

    /**
     * 计算从 activeAt 到现在的持续时间
     */
    private String calculateDuration(String activeAtStr) {
        try {
            Instant activeAt = Instant.parse(activeAtStr);
            Duration duration = Duration.between(activeAt, Instant.now());

            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            if (hours > 0) {
                return String.format("%dh%dm%ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm%ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", activeAtStr, e);
            return "unknown";
        }
    }

    /**
     * 构建错误响应
     */
    private String buildErrorResponse(String message, String error) {
        try {
            ExperimentAlertsOutput output = new ExperimentAlertsOutput();
            output.setSuccess(false);
            output.setMessage(message);
            output.setError(error);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return String.format("{\"success\":false,\"message\":\"%s\",\"error\":\"%s\"}", message, error);
        }
    }

    // ==================== 数据模型 ====================

    /**
     * 实验告警信息结构
     */
    @Data
    public static class ExperimentAlert {
        private String alertId;
        private String alertType;
        private String experimentId;
        private String parameterName;
        private Double measuredValue;
        private Double thresholdValue;
        private String unit;
        private String severity;
        private String description;
        private String location;
        private String activeAt;
    }

    /**
     * 实验告警查询结果
     */
    @Data
    public static class ExperimentAlertsResult {
        private String status;
        private ExperimentAlertsData data;
        private String error;
        private String errorType;
    }

    @Data
    public static class ExperimentAlertsData {
        private List<ExperimentAlert> alerts = new ArrayList<>();
    }

    /**
     * 简化的实验告警信息
     */
    @Data
    public static class SimplifiedExperimentAlert {
        @JsonProperty("alert_id")
        private String alertId;

        @JsonProperty("alert_type")
        private String alertType;

        @JsonProperty("experiment_id")
        private String experimentId;

        @JsonProperty("parameter_name")
        private String parameterName;

        @JsonProperty("measured_value")
        private Double measuredValue;

        @JsonProperty("threshold_value")
        private Double thresholdValue;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("severity")
        private String severity;

        @JsonProperty("description")
        private String description;

        @JsonProperty("location")
        private String location;

        @JsonProperty("active_at")
        private String activeAt;

        @JsonProperty("duration")
        private String duration;
    }

    /**
     * 实验告警查询输出
     */
    @Data
    public static class ExperimentAlertsOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("alerts")
        private List<SimplifiedExperimentAlert> alerts;

        @JsonProperty("message")
        private String message;

        @JsonProperty("error")
        private String error;
    }
}