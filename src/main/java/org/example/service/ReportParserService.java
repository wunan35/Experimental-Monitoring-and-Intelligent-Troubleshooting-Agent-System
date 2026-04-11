package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.AgentReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent报告解析服务
 * 将Agent的文本输出解析为结构化的JSON格式
 */
@Service
public class ReportParserService {

    private static final Logger logger = LoggerFactory.getLogger(ReportParserService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析Agent文本报告为结构化格式
     *
     * @param rawReport 原始报告文本
     * @return 结构化报告对象
     */
    public AgentReportResponse parseReport(String rawReport) {
        if (rawReport == null || rawReport.isEmpty()) {
            return createEmptyReport();
        }

        logger.info("开始解析Agent报告，长度: {}", rawReport.length());

        AgentReportResponse.Builder builder = AgentReportResponse.builder()
                .reportId(UUID.randomUUID().toString().substring(0, 8))
                .reportType("EXPERIMENT_ALERT_ANALYSIS")
                .title("实验异常分析报告");

        try {
            // 解析各个章节
            parseSummary(rawReport, builder);
            parseAlertsAnalysis(rawReport, builder);
            parseRootCause(rawReport, builder);
            parseRecommendations(rawReport, builder);

            // 设置执行状态
            builder.status(AgentReportResponse.ExecutionStatus.SUCCESS);

        } catch (Exception e) {
            logger.error("解析报告失败", e);
            builder.status(AgentReportResponse.ExecutionStatus.PARTIAL_SUCCESS);
        }

        return builder.build();
    }

    /**
     * 解析摘要
     */
    private void parseSummary(String report, AgentReportResponse.Builder builder) {
        // 尝试匹配摘要章节
        Pattern summaryPattern = Pattern.compile(
                "(?:摘要|Summary|概述|结论)[：:：]?\\s*([\\s\\S]*?)(?=\\n\\n|\\n#|\\n\\*|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = summaryPattern.matcher(report);
        if (matcher.find()) {
            String summary = matcher.group(1).trim();
            // 限制摘要长度
            if (summary.length() > 500) {
                summary = summary.substring(0, 500) + "...";
            }
            builder.summary(summary);
        } else {
            // 如果没有找到明确的摘要，提取前200字符作为摘要
            String summary = report.length() > 200 ? report.substring(0, 200) + "..." : report;
            builder.summary(summary.trim());
        }
    }

    /**
     * 解析告警分析
     */
    private void parseAlertsAnalysis(String report, AgentReportResponse.Builder builder) {
        AgentReportResponse.AlertsAnalysis analysis = new AgentReportResponse.AlertsAnalysis();

        // 统计告警数量
        Pattern alertCountPattern = Pattern.compile("(\\d+)\\s*(个|条)?告警");
        Matcher countMatcher = alertCountPattern.matcher(report);
        if (countMatcher.find()) {
            analysis.setTotalAlerts(Integer.parseInt(countMatcher.group(1)));
        }

        // 解析严重程度
        AgentReportResponse.SeverityCount severityCount = new AgentReportResponse.SeverityCount();
        severityCount.setCritical(countOccurrences(report, "紧急", "CRITICAL", "critical"));
        severityCount.setSerious(countOccurrences(report, "严重", "SERIOUS", "serious"));
        severityCount.setWarning(countOccurrences(report, "警告", "WARNING", "warning"));
        analysis.setBySeverity(severityCount);

        // 解析告警类型
        List<AgentReportResponse.TypeCount> typeCounts = new ArrayList<>();
        String[] alertTypes = {"CONCRETE_STRENGTH_ANOMALY", "STRUCTURAL_DEFORMATION_EXCEED",
                "MATERIAL_PROPERTY_FAILURE", "SENSOR_DATA_ABNORMAL", "EXPERIMENT_SYSTEM_FAILURE"};

        for (String type : alertTypes) {
            int count = countOccurrences(report, type);
            if (count > 0) {
                AgentReportResponse.TypeCount tc = new AgentReportResponse.TypeCount();
                tc.setType(type);
                tc.setCount(count);
                typeCounts.add(tc);
            }
        }
        analysis.setByType(typeCounts);

        builder.alertsAnalysis(analysis);
    }

    /**
     * 解析根因分析
     */
    private void parseRootCause(String report, AgentReportResponse.Builder builder) {
        AgentReportResponse.RootCauseAnalysis rootCause = new AgentReportResponse.RootCauseAnalysis();

        // 尝试匹配根因章节
        Pattern rootCausePattern = Pattern.compile(
                "(?:根因分析|Root Cause|主要原因|根本原因)[：:：]?\\s*([\\s\\S]*?)(?=\\n\\n|\\n#|\\n\\*|建议|处理|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = rootCausePattern.matcher(report);
        if (matcher.find()) {
            String cause = matcher.group(1).trim();
            // 提取第一句作为主要原因
            int dotIndex = cause.indexOf("。");
            if (dotIndex > 0 && dotIndex < cause.length()) {
                rootCause.setPrimaryCause(cause.substring(0, dotIndex + 1));
                // 其余内容作为分析过程
                rootCause.setAnalysisProcess(cause);
            } else {
                rootCause.setPrimaryCause(cause.length() > 200 ? cause.substring(0, 200) + "..." : cause);
            }
        }

        // 提取贡献因素
        List<String> factors = new ArrayList<>();
        Pattern factorPattern = Pattern.compile("[•\\-\\*]\\s*(.+?)(?=\\n|$)");
        Matcher factorMatcher = factorPattern.matcher(report);
        while (factorMatcher.find() && factors.size() < 5) {
            String factor = factorMatcher.group(1).trim();
            if (factor.length() > 10 && factor.length() < 200) {
                factors.add(factor);
            }
        }
        rootCause.setContributingFactors(factors);

        builder.rootCauseAnalysis(rootCause);
    }

    /**
     * 解析处理建议
     */
    private void parseRecommendations(String report, AgentReportResponse.Builder builder) {
        // 匹配建议章节
        Pattern recommendPattern = Pattern.compile(
                "(?:建议|处理建议|Recommendation|处理方案)[：:：]?\\s*([\\s\\S]*?)(?=\\n\\n|\\n#|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = recommendPattern.matcher(report);
        if (matcher.find()) {
            String recommendSection = matcher.group(1);

            // 提取建议条目
            Pattern itemPattern = Pattern.compile("\\d+[.、]\\s*(.+?)(?=\\n\\d|$)");
            Matcher itemMatcher = itemPattern.matcher(recommendSection);

            int priority = 1;
            while (itemMatcher.find() && priority <= 10) {
                String action = itemMatcher.group(1).trim();
                if (action.length() > 10) {
                    AgentReportResponse.Recommendation rec = new AgentReportResponse.Recommendation();
                    rec.setPriority(priority++);
                    rec.setAction(action);
                    rec.setCategory(categorizeRecommendation(action));
                    builder.addRecommendation(rec);
                }
            }
        }
    }

    /**
     * 对建议进行分类
     */
    private String categorizeRecommendation(String action) {
        String lowerAction = action.toLowerCase();

        if (lowerAction.contains("检查") || lowerAction.contains("排查") || lowerAction.contains("验证")) {
            return "INVESTIGATION";
        } else if (lowerAction.contains("更换") || lowerAction.contains("修复") || lowerAction.contains("维护")) {
            return "MAINTENANCE";
        } else if (lowerAction.contains("监控") || lowerAction.contains("观察") || lowerAction.contains("跟踪")) {
            return "MONITORING";
        } else if (lowerAction.contains("通知") || lowerAction.contains("报告") || lowerAction.contains("联系")) {
            return "COMMUNICATION";
        } else if (lowerAction.contains("校准") || lowerAction.contains("调试") || lowerAction.contains("配置")) {
            return "CALIBRATION";
        } else {
            return "OTHER";
        }
    }

    /**
     * 统计关键词出现次数
     */
    private int countOccurrences(String text, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            int index = 0;
            while ((index = text.indexOf(keyword, index)) != -1) {
                count++;
                index += keyword.length();
            }
        }
        return count;
    }

    /**
     * 创建空报告
     */
    private AgentReportResponse createEmptyReport() {
        return AgentReportResponse.builder()
                .reportId(UUID.randomUUID().toString().substring(0, 8))
                .reportType("EMPTY")
                .title("无报告内容")
                .status(AgentReportResponse.ExecutionStatus.FAILED)
                .summary("未能生成报告内容")
                .build();
    }

    /**
     * 将结构化报告转换为JSON字符串
     */
    public String toJson(AgentReportResponse report) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (Exception e) {
            logger.error("序列化报告失败", e);
            return "{}";
        }
    }
}
