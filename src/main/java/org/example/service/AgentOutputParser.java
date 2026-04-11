package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.AgentOutputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent输出解析服务
 * 将Agent的文本输出解析为结构化格式
 */
@Service
public class AgentOutputParser {

    private static final Logger logger = LoggerFactory.getLogger(AgentOutputParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 报告标题模式
    private static final Pattern TITLE_PATTERN = Pattern.compile("#\\s*(.+?)(?:\\n|$)");

    // 表格行模式
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("\\|([^|]+\\|)+");

    // 章节标题模式
    private static final Pattern SECTION_PATTERN = Pattern.compile("^##+\\s*(.+)$", Pattern.MULTILINE);

    /**
     * 解析Agent输出
     *
     * @param rawOutput 原始输出文本
     * @return 结构化输出
     */
    public AgentOutputSchema parse(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return createEmptyOutput();
        }

        logger.debug("开始解析Agent输出, 长度: {}", rawOutput.length());

        AgentOutputSchema output = new AgentOutputSchema();
        output.setRawContent(rawOutput);

        // 检测输出类型
        if (isReportFormat(rawOutput)) {
            output.setType(AgentOutputSchema.OutputType.REPORT);
            output.setReport(parseReport(rawOutput));
        } else if (isErrorResponse(rawOutput)) {
            output.setType(AgentOutputSchema.OutputType.ERROR);
            output.setError(parseError(rawOutput));
        } else {
            output.setType(AgentOutputSchema.OutputType.CONVERSATION);
        }

        return output;
    }

    /**
     * 检测是否为报告格式
     */
    private boolean isReportFormat(String text) {
        return text.contains("# 实验异常分析报告") ||
               text.contains("活跃实验异常清单") ||
               text.contains("根因分析");
    }

    /**
     * 检测是否为错误响应
     */
    private boolean isErrorResponse(String text) {
        return text.contains("错误") && text.contains("失败") ||
               text.contains("无法完成") ||
               text.startsWith("{\"error") ||
               text.startsWith("{\"success\":false");
    }

    /**
     * 解析报告
     */
    private AgentOutputSchema.StructuredReport parseReport(String text) {
        AgentOutputSchema.StructuredReport report = new AgentOutputSchema.StructuredReport();

        try {
            // 解析标题
            Matcher titleMatcher = TITLE_PATTERN.matcher(text);
            if (titleMatcher.find()) {
                report.setTitle(titleMatcher.group(1).trim());
            }

            // 解析活跃异常清单
            report.setActiveAlerts(parseActiveAlerts(text));

            // 解析根因分析
            report.setRootCauseAnalyses(parseRootCauseAnalyses(text));

            // 解析处理方案
            report.setTreatmentPlans(parseTreatmentPlans(text));

            // 解析结论
            report.setConclusion(parseConclusion(text));

        } catch (Exception e) {
            logger.error("解析报告失败", e);
        }

        return report;
    }

    /**
     * 解析活跃异常清单
     */
    private List<AgentOutputSchema.AlertInfo> parseActiveAlerts(String text) {
        List<AgentOutputSchema.AlertInfo> alerts = new ArrayList<>();

        // 查找表格部分
        int tableStart = text.indexOf("活跃实验异常清单");
        if (tableStart == -1) {
            return alerts;
        }

        String tableSection = text.substring(tableStart, Math.min(tableStart + 3000, text.length()));

        // 解析表格行
        String[] lines = tableSection.split("\n");
        for (String line : lines) {
            if (line.startsWith("|") && !line.contains("---") && !line.contains("异常类型")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 8) {
                    AgentOutputSchema.AlertInfo alert = new AgentOutputSchema.AlertInfo();
                    alert.setAlertType(cells[1].trim());
                    alert.setSeverity(cells[2].trim());
                    alert.setExperimentId(cells[3].trim());
                    alert.setParameterName(cells[4].trim());
                    try {
                        alert.setMeasuredValue(Double.parseDouble(cells[5].trim()));
                    } catch (NumberFormatException e) {
                        // 忽略解析错误
                    }
                    try {
                        alert.setThresholdValue(Double.parseDouble(cells[6].trim()));
                    } catch (NumberFormatException e) {
                        // 忽略解析错误
                    }
                    if (cells.length > 7) {
                        alert.setUnit(cells[7].trim());
                    }
                    if (cells.length > 8) {
                        alert.setStatus(cells[8].trim());
                    }
                    alerts.add(alert);
                }
            }
        }

        return alerts;
    }

    /**
     * 解析根因分析
     */
    private List<AgentOutputSchema.RootCauseAnalysis> parseRootCauseAnalyses(String text) {
        List<AgentOutputSchema.RootCauseAnalysis> analyses = new ArrayList<>();

        // 查找所有根因分析章节
        Pattern pattern = Pattern.compile("##+\\s*实验异常根因分析.*?(?=##+|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String section = matcher.group();
            AgentOutputSchema.RootCauseAnalysis analysis = new AgentOutputSchema.RootCauseAnalysis();

            // 提取异常类型
            Pattern typePattern = Pattern.compile("实验异常根因分析\\s*-\\s*(.+?)(?:\\n|$)");
            Matcher typeMatcher = typePattern.matcher(section);
            if (typeMatcher.find()) {
                analysis.setAlertType(typeMatcher.group(1).trim());
            }

            // 提取详情
            analysis.setDetail(parseAlertDetail(section));

            // 提取症状
            analysis.setSymptoms(extractSection(section, "症状描述"));

            // 提取证据
            analysis.setEvidence(extractSection(section, "实验过程证据"));

            // 提取结论
            analysis.setConclusion(extractSection(section, "根因结论"));

            analyses.add(analysis);
        }

        return analyses;
    }

    /**
     * 解析告警详情
     */
    private AgentOutputSchema.AlertDetail parseAlertDetail(String section) {
        AgentOutputSchema.AlertDetail detail = new AgentOutputSchema.AlertDetail();

        // 提取各个字段
        detail.setSeverity(extractField(section, "异常级别"));
        detail.setExperimentId(extractField(section, "实验编号"));
        detail.setParameterName(extractField(section, "监测参数"));
        detail.setMeasuredThreshold(extractField(section, "实测值/阈值"));
        detail.setDuration(extractField(section, "持续时间"));

        return detail;
    }

    /**
     * 解析处理方案
     */
    private List<AgentOutputSchema.TreatmentPlan> parseTreatmentPlans(String text) {
        List<AgentOutputSchema.TreatmentPlan> plans = new ArrayList<>();

        // 查找所有处理方案章节
        Pattern pattern = Pattern.compile("##+\\s*处理方案执行.*?(?=##+|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String section = matcher.group();
            AgentOutputSchema.TreatmentPlan plan = new AgentOutputSchema.TreatmentPlan();

            // 提取异常类型
            Pattern typePattern = Pattern.compile("处理方案执行\\s*-\\s*(.+?)(?:\\n|$)");
            Matcher typeMatcher = typePattern.matcher(section);
            if (typeMatcher.find()) {
                plan.setAlertType(typeMatcher.group(1).trim());
            }

            // 提取已执行步骤
            plan.setExecutedSteps(extractListSection(section, "已执行的排查步骤"));

            // 提取处理建议
            plan.setRecommendation(extractSection(section, "处理建议"));

            // 提取预期效果
            plan.setExpectedEffect(extractSection(section, "预期效果"));

            plans.add(plan);
        }

        return plans;
    }

    /**
     * 解析结论
     */
    private AgentOutputSchema.Conclusion parseConclusion(String text) {
        AgentOutputSchema.Conclusion conclusion = new AgentOutputSchema.Conclusion();

        int conclusionStart = text.indexOf("## 📊 结论");
        if (conclusionStart == -1) {
            conclusionStart = text.indexOf("## 结论");
        }

        if (conclusionStart != -1) {
            String conclusionSection = text.substring(conclusionStart);

            conclusion.setOverallAssessment(extractSection(conclusionSection, "整体评估"));
            conclusion.setKeyFindings(extractListSection(conclusionSection, "关键发现"));
            conclusion.setRecommendations(extractListSection(conclusionSection, "后续建议"));
            conclusion.setSafetyRiskAssessment(extractSection(conclusionSection, "安全风险评估"));
        }

        return conclusion;
    }

    /**
     * 解析错误
     */
    private AgentOutputSchema.ErrorInfo parseError(String text) {
        AgentOutputSchema.ErrorInfo error = new AgentOutputSchema.ErrorInfo();

        // 尝试解析JSON错误
        if (text.startsWith("{")) {
            try {
                var node = objectMapper.readTree(text);
                if (node.has("error")) {
                    error.setMessage(node.get("error").asText());
                }
                if (node.has("message")) {
                    error.setMessage(node.get("message").asText());
                }
                error.setCode("EXECUTION_ERROR");
            } catch (Exception e) {
                error.setMessage(text);
            }
        } else {
            error.setCode("EXECUTION_ERROR");
            error.setMessage(text);
        }

        return error;
    }

    /**
     * 提取字段值
     */
    private String extractField(String text, String fieldName) {
        Pattern pattern = Pattern.compile("\\*\\*" + fieldName + "\\*\\*\\s*:\\s*([^\\n]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 提取章节内容
     */
    private String extractSection(String text, String sectionName) {
        Pattern pattern = Pattern.compile("###\\s*" + sectionName + "\\s*\\n+(.+?)(?=###|##|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 提取列表章节
     */
    private List<String> extractListSection(String text, String sectionName) {
        List<String> items = new ArrayList<>();

        Pattern pattern = Pattern.compile("###\\s*" + sectionName + "\\s*\\n+((?:\\d+\\..+\\n?)+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String listText = matcher.group(1);
            Pattern itemPattern = Pattern.compile("\\d+\\.\\s*(.+)");
            Matcher itemMatcher = itemPattern.matcher(listText);

            while (itemMatcher.find()) {
                items.add(itemMatcher.group(1).trim());
            }
        }

        return items;
    }

    /**
     * 创建空输出
     */
    private AgentOutputSchema createEmptyOutput() {
        AgentOutputSchema output = new AgentOutputSchema();
        output.setType(AgentOutputSchema.OutputType.CONVERSATION);
        return output;
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson(AgentOutputSchema output) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("序列化输出失败", e);
            return "{\"type\":\"ERROR\",\"message\":\"序列化失败\"}";
        }
    }
}
