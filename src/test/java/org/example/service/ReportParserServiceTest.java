package org.example.service;

import org.example.dto.AgentReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReportParserService 单元测试
 */
class ReportParserServiceTest {

    private ReportParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ReportParserService();
    }

    @Test
    @DisplayName("解析完整报告")
    void testParseReport_FullReport() {
        String rawReport = """
                # 实验异常分析报告

                ## 摘要
                本次分析共发现5个告警，其中紧急1个，严重3个，警告1个。

                ## 告警分析
                检测到以下告警类型：
                - CONCRETE_STRENGTH_ANOMALY: 混凝土强度异常
                - STRUCTURAL_DEFORMATION_EXCEED: 结构变形超限
                - SENSOR_DATA_ABNORMAL: 传感器数据异常

                ## 根因分析
                主要原因是混凝土配合比设计不当，导致强度不足。
                1. 水灰比偏高
                2. 养护条件不达标

                ## 处理建议
                1. 立即更换不合格批次混凝土试件
                2. 检查并调整养护室温度和湿度
                3. 加强原材料质量控制
                """;

        AgentReportResponse response = parserService.parseReport(rawReport);

        assertNotNull(response);
        assertNotNull(response.getReportId());
        assertEquals("EXPERIMENT_ALERT_ANALYSIS", response.getReportType());
        assertEquals(AgentReportResponse.ExecutionStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getSummary());
        assertTrue(response.getSummary().contains("5个告警"));
    }

    @Test
    @DisplayName("解析空报告")
    void testParseReport_EmptyReport() {
        AgentReportResponse response = parserService.parseReport("");

        assertNotNull(response);
        assertEquals("EMPTY", response.getReportType());
        assertEquals(AgentReportResponse.ExecutionStatus.FAILED, response.getStatus());
    }

    @Test
    @DisplayName("解析null报告")
    void testParseReport_NullReport() {
        AgentReportResponse response = parserService.parseReport(null);

        assertNotNull(response);
        assertEquals("EMPTY", response.getReportType());
    }

    @Test
    @DisplayName("提取摘要 - 无明确摘要章节")
    void testParseReport_ExtractSummaryFromBeginning() {
        String report = "这是一段测试报告内容，没有明确的摘要章节标记。";

        AgentReportResponse response = parserService.parseReport(report);

        assertNotNull(response.getSummary());
        assertTrue(response.getSummary().contains("测试报告"));
    }

    @Test
    @DisplayName("解析告警严重程度统计")
    void testParseAlertsAnalysis_SeverityCount() {
        String report = "发现紧急告警1个，严重告警3个，警告告警2个。";

        AgentReportResponse response = parserService.parseReport(report);

        assertNotNull(response.getAlertsAnalysis());
        assertEquals(1, response.getAlertsAnalysis().getBySeverity().getCritical());
        assertEquals(3, response.getAlertsAnalysis().getBySeverity().getSerious());
        assertEquals(2, response.getAlertsAnalysis().getBySeverity().getWarning());
    }

    @Test
    @DisplayName("解析告警类型统计")
    void testParseAlertsAnalysis_AlertTypes() {
        String report = """
                检测到告警类型：
                - CONCRETE_STRENGTH_ANOMALY
                - STRUCTURAL_DEFORMATION_EXCEED
                - CONCRETE_STRENGTH_ANOMALY
                """;

        AgentReportResponse response = parserService.parseReport(report);

        assertNotNull(response.getAlertsAnalysis().getByType());
        // 应该检测到CONCRETE_STRENGTH_ANOMALY出现2次
        boolean found = response.getAlertsAnalysis().getByType().stream()
                .anyMatch(tc -> tc.getType().equals("CONCRETE_STRENGTH_ANOMALY") && tc.getCount() == 2);
        assertTrue(found);
    }

    @Test
    @DisplayName("解析根因分析")
    void testParseRootCause() {
        String report = """
                ## 根因分析
                主要原因是混凝土配合比设计不当，水灰比偏高。
                贡献因素：
                • 养护温度不稳定
                • 原材料质量波动
                """;

        AgentReportResponse response = parserService.parseReport(report);

        assertNotNull(response.getRootCauseAnalysis());
        assertNotNull(response.getRootCauseAnalysis().getPrimaryCause());
        assertTrue(response.getRootCauseAnalysis().getPrimaryCause().contains("混凝土配合比"));
    }

    @Test
    @DisplayName("解析处理建议")
    void testParseRecommendations() {
        String report = """
                ## 处理建议
                1. 立即更换不合格试件
                2. 检查养护条件
                3. 加强质量控制
                """;

        AgentReportResponse response = parserService.parseReport(report);

        assertNotNull(response.getRecommendations());
        assertFalse(response.getRecommendations().isEmpty());
        assertEquals(1, response.getRecommendations().get(0).getPriority());
        assertTrue(response.getRecommendations().get(0).getAction().contains("更换"));
    }

    @Test
    @DisplayName("建议分类 - 检查类")
    void testCategorizeRecommendation_Investigation() {
        String report = "## 建议\n1. 检查传感器连接状态";

        AgentReportResponse response = parserService.parseReport(report);

        if (!response.getRecommendations().isEmpty()) {
            assertEquals("INVESTIGATION", response.getRecommendations().get(0).getCategory());
        }
    }

    @Test
    @DisplayName("建议分类 - 维护类")
    void testCategorizeRecommendation_Maintenance() {
        String report = "## 建议\n1. 更换损坏的传感器";

        AgentReportResponse response = parserService.parseReport(report);

        if (!response.getRecommendations().isEmpty()) {
            assertEquals("MAINTENANCE", response.getRecommendations().get(0).getCategory());
        }
    }

    @Test
    @DisplayName("转换为JSON")
    void testToJson() {
        AgentReportResponse response = AgentReportResponse.builder()
                .reportId("test-001")
                .reportType("TEST")
                .title("测试报告")
                .status(AgentReportResponse.ExecutionStatus.SUCCESS)
                .summary("测试摘要")
                .build();

        String json = parserService.toJson(response);

        assertNotNull(json);
        assertTrue(json.contains("\"report_id\" : \"test-001\""));
        assertTrue(json.contains("\"status\" : \"SUCCESS\""));
    }

    @Test
    @DisplayName("长摘要应该被截断")
    void testParseReport_TruncateLongSummary() {
        StringBuilder longText = new StringBuilder("摘要：");
        for (int i = 0; i < 600; i++) {
            longText.append("测试内容");
        }
        String report = longText.toString();

        AgentReportResponse response = parserService.parseReport(report);

        assertTrue(response.getSummary().length() <= 503); // 500 + "..."
    }
}
