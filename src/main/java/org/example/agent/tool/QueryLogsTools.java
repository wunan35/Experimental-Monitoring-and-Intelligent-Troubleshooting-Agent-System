package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验日志查询工具
 *
 * <p>用于查询土木实验的过程日志、设备状态日志、实验事件日志等。</p>
 * <p>支持 Mock 模式，提供与实验告警关联的模拟日志数据。</p>
 *
 * <h2>日志主题</h2>
 * <ul>
 *   <li><b>experiment-metrics</b> - 实验指标日志（混凝土强度、结构变形、材料性能）</li>
 *   <li><b>experiment-process-logs</b> - 实验过程日志（试件制备、养护、加载、测量）</li>
 *   <li><b>equipment-operation-logs</b> - 设备操作日志（试验机状态、传感器校准）</li>
 *   <li><b>experiment-events</b> - 实验事件日志（试件破坏、设备故障、安全警报）</li>
 * </ul>
 *
 * <h2>查询语法</h2>
 * <p>支持 Lucene 风格的查询语法：</p>
 * <ul>
 *   <li>{@code strength:<30} - 查询强度低于30的日志</li>
 *   <li>{@code deformation:>15} - 查询变形超过15的日志</li>
 *   <li>{@code status:ERROR OR level:WARN} - 组合查询</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <ul>
 *   <li>{@code experiment.logs.mock-enabled} - 是否启用 Mock 模式（默认 true）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 首先获取可用主题
 * String topics = getAvailableExperimentLogTopics();
 *
 * // 查询实验指标日志
 * String logs = queryExperimentLogs(
 *     "结构实验室",           // laboratory
 *     "experiment-metrics",  // logTopic
 *     "strength:<30",        // query
 *     20                     // limit
 * );
 * }</pre>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 * @see QueryMetricsTools
 */
@Component
public class QueryLogsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryLogsTools.class);

    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_EXPERIMENT_LOGS = "queryExperimentLogs";
    public static final String TOOL_GET_AVAILABLE_LOG_TOPICS = "getAvailableExperimentLogTopics";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${experiment.logs.mock-enabled:true}")
    private boolean mockEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("✅ QueryLogsTools 初始化成功, Mock模式: {}", mockEnabled);
    }

    /**
     * 获取可用的实验日志主题列表
     * 用于查询前先了解有哪些实验日志主题可供查询
     */
    @Tool(description = "Get all available experiment log topics and their descriptions. " +
            "Call this tool first before querying logs to understand what experiment log topics are available. " +
            "Returns a list of log topics with their names, descriptions, and example queries.")
    public String getAvailableExperimentLogTopics() {
        logger.info("获取可用的实验日志主题列表");

        try {
            List<ExperimentLogTopicInfo> topics = new ArrayList<>();

            // 实验指标日志
            ExperimentLogTopicInfo experimentMetrics = new ExperimentLogTopicInfo();
            experimentMetrics.setTopicName("experiment-metrics");
            experimentMetrics.setDescription("实验指标日志，包含混凝土强度、结构变形、材料性能等实验参数监测数据");
            experimentMetrics.setExampleQueries(List.of(
                    "strength:<threshold",
                    "deformation:>limit",
                    "parameter:strain AND value:>5000",
                    "status:ABNORMAL"
            ));
            experimentMetrics.setRelatedAlerts(List.of("CONCRETE_STRENGTH_ANOMALY", "STRUCTURAL_DEFORMATION_EXCEED", "MATERIAL_PROPERTY_FAILURE"));
            topics.add(experimentMetrics);

            // 实验过程日志
            ExperimentLogTopicInfo experimentProcessLogs = new ExperimentLogTopicInfo();
            experimentProcessLogs.setTopicName("experiment-process-logs");
            experimentProcessLogs.setDescription("实验过程日志，包含试件制备、养护、加载、测量等实验操作记录和状态信息");
            experimentProcessLogs.setExampleQueries(List.of(
                    "operation:casting",
                    "operation:loading",
                    "step:curing",
                    "status:COMPLETED",
                    "level:WARN OR level:ERROR"
            ));
            experimentProcessLogs.setRelatedAlerts(List.of("EXPERIMENT_SYSTEM_FAILURE", "SENSOR_DATA_ABNORMAL"));
            topics.add(experimentProcessLogs);

            // 设备操作日志
            ExperimentLogTopicInfo equipmentOperationLogs = new ExperimentLogTopicInfo();
            equipmentOperationLogs.setTopicName("equipment-operation-logs");
            equipmentOperationLogs.setDescription("设备操作日志，包含试验机、传感器、数据采集系统等设备的运行状态和操作记录");
            equipmentOperationLogs.setExampleQueries(List.of(
                    "equipment:MTS",
                    "status:ERROR",
                    "calibration:due",
                    "maintenance:required",
                    "*"  // 查询所有设备日志
            ));
            equipmentOperationLogs.setRelatedAlerts(List.of("EXPERIMENT_SYSTEM_FAILURE", "SENSOR_DATA_ABNORMAL"));
            topics.add(equipmentOperationLogs);

            // 实验事件日志
            ExperimentLogTopicInfo experimentEvents = new ExperimentLogTopicInfo();
            experimentEvents.setTopicName("experiment-events");
            experimentEvents.setDescription("实验事件日志，包含试件破坏、设备故障、安全警报、环境异常等关键事件记录");
            experimentEvents.setExampleQueries(List.of(
                    "event:specimen_failure",
                    "event:equipment_failure",
                    "event:safety_alert",
                    "severity:HIGH",
                    "type:CRITICAL"
            ));
            experimentEvents.setRelatedAlerts(List.of("EXPERIMENT_SYSTEM_FAILURE", "MATERIAL_PROPERTY_FAILURE"));
            topics.add(experimentEvents);

            // 构建输出
            ExperimentLogTopicsOutput output = new ExperimentLogTopicsOutput();
            output.setSuccess(true);
            output.setTopics(topics);
            output.setAvailableLaboratories(List.of("结构实验室", "材料实验室", "疲劳实验室", "智能监测实验室"));
            output.setDefaultLaboratory("结构实验室");

            output.setMessage(String.format("共有 %d 个可用的实验日志主题。建议使用默认实验室 '结构实验室'", topics.size()));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);

        } catch (Exception e) {
            logger.error("获取实验日志主题列表失败", e);
            return "{\"success\":false,\"message\":\"获取实验日志主题列表失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 查询实验日志
     * 从实验日志系统查询指定条件的日志
     *
     * @param laboratory 实验室名称，如 结构实验室、材料实验室
     * @param logTopic 日志主题，如 experiment-metrics, experiment-process-logs
     * @param query 查询条件，如 strength:<30 OR deformation:>15
     * @param limit 返回的日志条数，默认20条
     */
    // 有效实验室列表
    private static final List<String> VALID_LABORATORIES = List.of(
            "结构实验室", "材料实验室", "疲劳实验室", "智能监测实验室"
    );

    private static final String DEFAULT_LABORATORY = "结构实验室";

    @Tool(description = "Query logs from experiment logging system. " +
            "Use this tool to search experiment process logs, equipment operation logs, and other experiment-related log data. " +
            "IMPORTANT: Before calling this tool, you should call getAvailableExperimentLogTopics to understand what log topics are available. " +
            "Available log topics: " +
            "1) 'experiment-metrics' - Experiment metrics logs (concrete strength, structural deformation, material properties. Related to CONCRETE_STRENGTH_ANOMALY, STRUCTURAL_DEFORMATION_EXCEED, MATERIAL_PROPERTY_FAILURE alerts); " +
            "2) 'experiment-process-logs' - Experiment process logs (specimen preparation, curing, loading, measurement operations. Related to EXPERIMENT_SYSTEM_FAILURE, SENSOR_DATA_ABNORMAL alerts); " +
            "3) 'equipment-operation-logs' - Equipment operation logs (testing machine status, sensor calibration, data acquisition system. Related to EXPERIMENT_SYSTEM_FAILURE, SENSOR_DATA_ABNORMAL alerts); " +
            "4) 'experiment-events' - Experiment event logs (specimen failure, equipment failure, safety alerts. Related to EXPERIMENT_SYSTEM_FAILURE, MATERIAL_PROPERTY_FAILURE alerts). " +
            "logTopic (required, one of the above topics), " +
            "query (optional, defaults to a curated search if empty), " +
            "limit (optional, default 20, max 100).")
    public String queryExperimentLogs(
            @ToolParam(description = "实验室名称，可选值: 结构实验室, 材料实验室, 疲劳实验室, 智能监测实验室。默认 结构实验室") String laboratory,
            @ToolParam(description = "日志主题，如 experiment-metrics, experiment-process-logs, equipment-operation-logs, experiment-events") String logTopic,
            @ToolParam(description = "查询条件，支持 Lucene 语法，如 strength:<30 OR deformation:>15；为空时返回该主题近 5 条核心日志") String query,
            @ToolParam(description = "返回日志条数，默认20，最大100") Integer limit) {

        int actualLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);

        String safeQuery = query == null ? "" : query;

        try {
            List<ExperimentLogEntry> logEntries;

            if (mockEnabled) {
                // Mock 模式：返回与实验告警关联的模拟日志数据
                logEntries = buildMockExperimentLogs(laboratory, logTopic, safeQuery, actualLimit);
                logger.info("使用 Mock 数据，返回 {} 条实验日志", logEntries.size());
            } else {
                // 真实模式：调用实验日志系统 API（这里预留接口，后续实现）
                return buildErrorResponse("实验日志系统真实查询尚未实现，请启用 mock 模式进行测试");
            }

            // 构建成功响应
            QueryExperimentLogsOutput output = new QueryExperimentLogsOutput();
            output.setSuccess(!logEntries.isEmpty());
            output.setLaboratory(laboratory);
            output.setLogTopic(logTopic);
            output.setQuery(safeQuery.isBlank() ? "DEFAULT_QUERY" : safeQuery);
            output.setLogs(logEntries);
            output.setTotal(logEntries.size());
            output.setMessage(logEntries.isEmpty() ? "未找到匹配的实验日志" : String.format("成功查询到 %d 条实验日志", logEntries.size()));

            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.info("实验日志查询完成: 找到 {} 条日志", logEntries.size());

            return jsonResult;

        } catch (Exception e) {
            logger.error("查询实验日志失败", e);
            return buildErrorResponse("查询失败: " + e.getMessage());
        }
    }

    /**
     * 构建 Mock 实验日志数据
     * 根据日志主题和查询条件返回与实验告警关联的模拟数据
     */
    private List<ExperimentLogEntry> buildMockExperimentLogs(String laboratory, String logTopic, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();
        Instant now = Instant.now();

        String safeTopic = logTopic == null ? "experiment-metrics" : logTopic.toLowerCase();
        String normalizedQuery = query == null ? "" : query.toLowerCase();

        // 根据日志主题和查询条件生成对应的 mock 数据
        switch (safeTopic) {
            case "experiment-metrics":
                logs.addAll(buildExperimentMetricsLogs(now, normalizedQuery, limit));
                break;
            case "experiment-process-logs":
                logs.addAll(buildExperimentProcessLogs(now, normalizedQuery, limit));
                break;
            case "equipment-operation-logs":
                logs.addAll(buildEquipmentOperationLogs(now, normalizedQuery, limit));
                break;
            case "experiment-events":
                logs.addAll(buildExperimentEventsLogs(now, normalizedQuery, limit));
                break;
            default:
                logs.addAll(buildGenericExperimentLogs(now, normalizedQuery, limit));
        }

        if (logs.isEmpty()) {
            logs.addAll(buildGenericExperimentLogs(now, normalizedQuery, limit));
        }

        // 限制返回条数
        if (logs.size() > limit) {
            logs = logs.subList(0, limit);
        }

        return logs;
    }

    /**
     * 构建实验指标日志（与混凝土强度、结构变形、材料性能告警关联）
     */
    private List<ExperimentLogEntry> buildExperimentMetricsLogs(Instant now, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();

        // 混凝土强度相关日志
        if (query.contains("strength") || query.contains("concrete") || query.contains("<30")) {
            for (int i = 0; i < 5; i++) {
                ExperimentLogEntry log = new ExperimentLogEntry();
                log.setTimestamp(FORMATTER.format(now.minus(i * 30, ChronoUnit.MINUTES)));
                log.setLevel("WARN");
                log.setExperimentId("exp-concrete-20240322-00" + (i + 1));
                log.setOperator("technician-0" + (i % 3 + 1));
                log.setMessage(String.format("混凝土试件抗压强度偏低: %.1fMPa, 设计强度: 30.0MPa, 达标率: %.1f%%",
                        25.3 + i * 0.5, (25.3 + i * 0.5) / 30.0 * 100));
                log.setMetrics(Map.of(
                        "parameter", "抗压强度",
                        "value", String.format("%.1f", 25.3 + i * 0.5),
                        "unit", "MPa",
                        "design_value", "30.0",
                        "specimen_id", "CS-20240322-0" + (i + 1),
                        "test_machine", "压力试验机-0" + (i % 2 + 1)
                ));
                logs.add(log);
            }
        }

        // 结构变形相关日志
        if (query.contains("deformation") || query.contains("displacement") || query.contains(">15")) {
            for (int i = 0; i < 5; i++) {
                ExperimentLogEntry log = new ExperimentLogEntry();
                log.setTimestamp(FORMATTER.format(now.minus(i * 20, ChronoUnit.MINUTES)));
                log.setLevel("WARN");
                log.setExperimentId("exp-steelbeam-20240322-00" + (i + 1));
                log.setOperator("engineer-0" + (i % 2 + 1));
                log.setMessage(String.format("钢梁跨中挠度超限: %.1fmm, 允许限值: 15.0mm, 超限比例: %.1f%%",
                        16.5 + i * 0.4, (16.5 + i * 0.4) / 15.0 * 100));
                log.setMetrics(Map.of(
                        "parameter", "跨中挠度",
                        "value", String.format("%.1f", 16.5 + i * 0.4),
                        "unit", "mm",
                        "limit_value", "15.0",
                        "load_level", String.format("%.0f%%", 70 + i * 3),
                        "monitoring_point", "L/2"
                ));
                logs.add(log);
            }
        }

        // 材料性能相关日志
        if (query.contains("material") || query.contains("yield") || query.contains("<345")) {
            for (int i = 0; i < 4; i++) {
                ExperimentLogEntry log = new ExperimentLogEntry();
                log.setTimestamp(FORMATTER.format(now.minus(i * 45, ChronoUnit.MINUTES)));
                log.setLevel("ERROR");
                log.setExperimentId("exp-steel-20240322-00" + (i + 1));
                log.setOperator("material-tech-0" + (i % 2 + 1));
                log.setMessage(String.format("钢材屈服强度不合格: %.1fMPa, 标准值: 345MPa, 偏差: -%.1fMPa",
                        325.0 + i * 2.0, 345.0 - (325.0 + i * 2.0)));
                log.setMetrics(Map.of(
                        "parameter", "屈服强度",
                        "value", String.format("%.1f", 325.0 + i * 2.0),
                        "unit", "MPa",
                        "standard_value", "345.0",
                        "specimen_id", "ST-20240322-0" + (i + 1),
                        "batch_no", "20240315-A"
                ));
                logs.add(log);
            }
        }

        // 传感器数据相关日志
        if (query.contains("sensor") || query.contains("strain") || query.contains("fbg")) {
            ExperimentLogEntry sensorLog = new ExperimentLogEntry();
            sensorLog.setTimestamp(FORMATTER.format(now.minus(15, ChronoUnit.MINUTES)));
            sensorLog.setLevel("ERROR");
            sensorLog.setExperimentId("exp-monitoring-20240322-001");
            sensorLog.setOperator("monitoring-system");
            sensorLog.setMessage("光纤光栅传感器 FBG-03 信号异常: 输出持续为零值, 位置: 梁底部受拉区, 通道: CH-03");
            sensorLog.setMetrics(Map.of(
                    "sensor_id", "FBG-03",
                    "sensor_type", "光纤光栅",
                    "location", "梁底部受拉区",
                    "channel", "CH-03",
                    "status", "FAULT",
                    "last_normal_value", "1250με"
            ));
            logs.add(sensorLog);
        }

        return logs;
    }

    /**
     * 构建实验过程日志（与实验系统故障、传感器异常告警关联）
     */
    private List<ExperimentLogEntry> buildExperimentProcessLogs(Instant now, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();

        // 试件制备过程日志
        if (query.contains("casting") || query.contains("preparation") || query.contains("curing")) {
            ExperimentLogEntry castingLog = new ExperimentLogEntry();
            castingLog.setTimestamp(FORMATTER.format(now.minus(2, ChronoUnit.HOURS)));
            castingLog.setLevel("INFO");
            castingLog.setExperimentId("exp-concrete-20240322-001");
            castingLog.setOperator("technician-01");
            castingLog.setMessage("混凝土试件制备完成: 配合比 C30, 水灰比 0.45, 试件数量 3组, 开始标准养护");
            castingLog.setMetrics(Map.of(
                    "operation", "casting",
                    "mix_design", "C30",
                    "water_cement_ratio", "0.45",
                    "specimen_count", "9",
                    "curing_start", FORMATTER.format(now.minus(2, ChronoUnit.HOURS))
            ));
            logs.add(castingLog);

            ExperimentLogEntry curingLog = new ExperimentLogEntry();
            curingLog.setTimestamp(FORMATTER.format(now.minus(1, ChronoUnit.HOURS)));
            curingLog.setLevel("INFO");
            curingLog.setExperimentId("exp-concrete-20240322-001");
            curingLog.setOperator("automation-system");
            curingLog.setMessage("养护室环境监控: 温度 20.3°C, 湿度 95%, 符合标准养护条件");
            curingLog.setMetrics(Map.of(
                    "operation", "curing",
                    "temperature", "20.3",
                    "humidity", "95",
                    "status", "NORMAL",
                    "duration", "1h"
            ));
            logs.add(curingLog);
        }

        // 加载过程日志
        if (query.contains("loading") || query.contains("load") || query.contains("step")) {
            for (int i = 0; i < 3; i++) {
                ExperimentLogEntry loadingLog = new ExperimentLogEntry();
                loadingLog.setTimestamp(FORMATTER.format(now.minus(i * 10, ChronoUnit.MINUTES)));
                loadingLog.setLevel("INFO");
                loadingLog.setExperimentId("exp-steelbeam-20240322-001");
                loadingLog.setOperator("engineer-01");
                loadingLog.setMessage(String.format("加载步骤 %d: 荷载 %.1fkN, 挠度 %.1fmm, 应变 %dμε",
                        i + 1, 50.0 + i * 10.0, 5.2 + i * 3.1, 800 + i * 250));
                loadingLog.setMetrics(Map.of(
                        "operation", "loading",
                        "load_step", String.valueOf(i + 1),
                        "load_value", String.format("%.1f", 50.0 + i * 10.0),
                        "deformation", String.format("%.1f", 5.2 + i * 3.1),
                        "strain", String.valueOf(800 + i * 250),
                        "status", "IN_PROGRESS"
                ));
                logs.add(loadingLog);
            }
        }

        // 测量过程日志
        if (query.contains("measurement") || query.contains("test") || query.contains("data")) {
            ExperimentLogEntry measurementLog = new ExperimentLogEntry();
            measurementLog.setTimestamp(FORMATTER.format(now.minus(25, ChronoUnit.MINUTES)));
            measurementLog.setLevel("WARN");
            measurementLog.setExperimentId("exp-steel-20240322-001");
            measurementLog.setOperator("technician-02");
            measurementLog.setMessage("拉伸试验数据异常: 力-位移曲线出现平台段, 疑似试件夹持滑移");
            measurementLog.setMetrics(Map.of(
                    "operation", "measurement",
                    "test_type", "tensile",
                    "issue", "grip_slip",
                    "action", "retightened_grips",
                    "retest", "yes"
            ));
            logs.add(measurementLog);
        }

        return logs;
    }

    /**
     * 构建设备操作日志（与实验系统故障告警关联）
     */
    private List<ExperimentLogEntry> buildEquipmentOperationLogs(Instant now, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();

        // 试验机状态日志
        if (query.contains("mts") || query.contains("equipment") || query.contains("failure")) {
            ExperimentLogEntry mtsLog = new ExperimentLogEntry();
            mtsLog.setTimestamp(FORMATTER.format(now.minus(15, ChronoUnit.MINUTES)));
            mtsLog.setLevel("ERROR");
            mtsLog.setExperimentId("exp-fatigue-20240322-001");
            mtsLog.setOperator("MTS-control-system");
            mtsLog.setMessage("MTS试验机液压系统故障: 压力异常下降, 错误代码 E-1023, 已自动紧急停机");
            mtsLog.setMetrics(Map.of(
                    "equipment", "MTS-370.02",
                    "error_code", "E-1023",
                    "subsystem", "hydraulic",
                    "pressure", "8.2MPa",
                    "expected_pressure", "21.0MPa",
                    "action", "emergency_shutdown"
            ));
            logs.add(mtsLog);
        }

        // 传感器校准日志
        if (query.contains("calibration") || query.contains("sensor") || query.contains("due")) {
            ExperimentLogEntry calibrationLog = new ExperimentLogEntry();
            calibrationLog.setTimestamp(FORMATTER.format(now.minus(3, ChronoUnit.DAYS)));
            calibrationLog.setLevel("WARN");
            calibrationLog.setExperimentId("equipment-maintenance");
            calibrationLog.setOperator("calibration-tech-01");
            calibrationLog.setMessage("传感器校准到期提醒: LVDT-05 位移传感器校准有效期剩余 7天, 建议安排校准");
            calibrationLog.setMetrics(Map.of(
                    "equipment", "LVDT-05",
                    "type", "displacement_sensor",
                    "last_calibration", "2024-03-01",
                    "next_due", "2024-03-29",
                    "status", "calibration_due"
            ));
            logs.add(calibrationLog);
        }

        // 数据采集系统日志
        if (query.contains("daq") || query.contains("acquisition") || query.contains("system")) {
            ExperimentLogEntry daqLog = new ExperimentLogEntry();
            daqLog.setTimestamp(FORMATTER.format(now.minus(30, ChronoUnit.MINUTES)));
            daqLog.setLevel("ERROR");
            daqLog.setExperimentId("exp-monitoring-20240322-001");
            daqLog.setOperator("NI-DAQ-system");
            daqLog.setMessage("数据采集系统通信中断: 通道 CH-03, CH-07 信号丢失, 可能原因: 光纤接头松动");
            daqLog.setMetrics(Map.of(
                    "system", "NI-PXIe-1073",
                    "failed_channels", "CH-03, CH-07",
                    "error_type", "communication_loss",
                    "suspected_cause", "fiber_connector_loose",
                    "action", "check_connections"
            ));
            logs.add(daqLog);
        }

        return logs;
    }

    /**
     * 构建实验事件日志（与实验系统故障、材料性能不合格告警关联）
     */
    private List<ExperimentLogEntry> buildExperimentEventsLogs(Instant now, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();

        // 试件破坏事件
        if (query.contains("specimen") || query.contains("failure") || query.contains("destruction")) {
            ExperimentLogEntry failureLog = new ExperimentLogEntry();
            failureLog.setTimestamp(FORMATTER.format(now.minus(40, ChronoUnit.MINUTES)));
            failureLog.setLevel("INFO");
            failureLog.setExperimentId("exp-concrete-20240322-001");
            failureLog.setOperator("pressure-test-machine");
            failureLog.setMessage("混凝土试件破坏事件: 试件 CS-20240322-01 达到极限荷载 425.3kN, 破坏形态为锥形破坏");
            failureLog.setMetrics(Map.of(
                    "event_type", "specimen_failure",
                    "specimen_id", "CS-20240322-01",
                    "peak_load", "425.3kN",
                    "failure_mode", "cone_failure",
                    "strength", "28.4MPa",
                    "photos", "captured"
            ));
            logs.add(failureLog);
        }

        // 安全警报事件
        if (query.contains("safety") || query.contains("alert") || query.contains("emergency")) {
            ExperimentLogEntry safetyLog = new ExperimentLogEntry();
            safetyLog.setTimestamp(FORMATTER.format(now.minus(15, ChronoUnit.MINUTES)));
            safetyLog.setLevel("CRITICAL");
            safetyLog.setExperimentId("exp-fatigue-20240322-001");
            safetyLog.setOperator("safety-system");
            safetyLog.setMessage("安全警报: MTS试验机区域检测到异常振动, 触发急停保护, 人员已疏散");
            safetyLog.setMetrics(Map.of(
                    "event_type", "safety_alert",
                    "alert_level", "CRITICAL",
                    "trigger", "abnormal_vibration",
                    "action", "emergency_stop",
                    "evacuation", "completed",
                    "area", "fatigue_lab"
            ));
            logs.add(safetyLog);
        }

        // 环境异常事件
        if (query.contains("environment") || query.contains("temperature") || query.contains("humidity")) {
            ExperimentLogEntry envLog = new ExperimentLogEntry();
            envLog.setTimestamp(FORMATTER.format(now.minus(2, ChronoUnit.HOURS)));
            envLog.setLevel("WARN");
            envLog.setExperimentId("environment-monitoring");
            envLog.setOperator("environment-control");
            envLog.setMessage("养护室环境异常: 温度波动超出允许范围 ±2°C, 当前 23.5°C, 设定 20.0°C");
            envLog.setMetrics(Map.of(
                    "event_type", "environment_anomaly",
                    "parameter", "temperature",
                    "current_value", "23.5",
                    "set_point", "20.0",
                    "allowed_range", "±2.0",
                    "corrective_action", "AC_adjusted"
            ));
            logs.add(envLog);
        }

        return logs;
    }

    /**
     * 构建通用实验日志
     */
    private List<ExperimentLogEntry> buildGenericExperimentLogs(Instant now, String query, int limit) {
        List<ExperimentLogEntry> logs = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, 10); i++) {
            ExperimentLogEntry log = new ExperimentLogEntry();
            log.setTimestamp(FORMATTER.format(now.minus(i, ChronoUnit.MINUTES)));
            log.setLevel(i % 3 == 0 ? "ERROR" : (i % 3 == 1 ? "WARN" : "INFO"));
            log.setExperimentId("exp-generic-20240322-00" + (i + 1));
            log.setOperator("operator-" + (i % 5 + 1));
            log.setMessage("实验日志消息 #" + (i + 1) + ", 查询条件: " + query);
            log.setMetrics(new HashMap<>());
            logs.add(log);
        }

        return logs;
    }

    /**
     * 构建错误响应
     */
    private String buildErrorResponse(String message) {
        try {
            QueryExperimentLogsOutput output = new QueryExperimentLogsOutput();
            output.setSuccess(false);
            output.setMessage(message);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return String.format("{\"success\":false,\"message\":\"%s\"}", message);
        }
    }

    // ==================== 数据模型 ====================

    /**
     * 实验日志条目
     */
    @Data
    public static class ExperimentLogEntry {
        @JsonProperty("timestamp")
        private String timestamp;

        @JsonProperty("level")
        private String level;

        @JsonProperty("experiment_id")
        private String experimentId;

        @JsonProperty("operator")
        private String operator;

        @JsonProperty("message")
        private String message;

        @JsonProperty("metrics")
        private Map<String, String> metrics;
    }

    /**
     * 实验日志查询输出
     */
    @Data
    public static class QueryExperimentLogsOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("laboratory")
        private String laboratory;

        @JsonProperty("log_topic")
        private String logTopic;

        @JsonProperty("query")
        private String query;

        @JsonProperty("logs")
        private List<ExperimentLogEntry> logs;

        @JsonProperty("total")
        private int total;

        @JsonProperty("message")
        private String message;
    }

    /**
     * 实验日志主题信息
     */
    @Data
    public static class ExperimentLogTopicInfo {
        @JsonProperty("topic_name")
        private String topicName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("example_queries")
        private List<String> exampleQueries;

        @JsonProperty("related_alerts")
        private List<String> relatedAlerts;
    }

    /**
     * 实验日志主题列表输出
     */
    @Data
    public static class ExperimentLogTopicsOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("topics")
        private List<ExperimentLogTopicInfo> topics;

        @JsonProperty("available_laboratories")
        private List<String> availableLaboratories;

        @JsonProperty("default_laboratory")
        private String defaultLaboratory;

        @JsonProperty("message")
        private String message;
    }
}