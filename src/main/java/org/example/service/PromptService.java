package org.example.service;

import org.example.config.PromptConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prompt管理服务
 * 负责Prompt的加载、版本管理和动态渲染
 */
@Service
public class PromptService {

    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);

    @Autowired
    private PromptConfig promptConfig;

    /**
     * 获取指定名称的Prompt模板
     *
     * @param templateName 模板名称
     * @return Prompt模板内容
     */
    public String getPrompt(String templateName) {
        Map<String, PromptConfig.PromptTemplate> templates = promptConfig.getTemplates();

        if (templates == null || !templates.containsKey(templateName)) {
            logger.warn("Prompt模板不存在: {}, 使用默认模板", templateName);
            return getDefaultPrompt(templateName);
        }

        PromptConfig.PromptTemplate template = templates.get(templateName);

        if (!template.isEnabled()) {
            logger.warn("Prompt模板已禁用: {}, 使用默认模板", templateName);
            return getDefaultPrompt(templateName);
        }

        logger.debug("加载Prompt模板: {}, 版本: {}", templateName, template.getVersion());
        return template.getContent();
    }

    /**
     * 获取指定版本的Prompt模板
     *
     * @param templateName 模板名称
     * @param version      版本号
     * @return Prompt模板内容
     */
    public String getPrompt(String templateName, String version) {
        String versionedKey = templateName + "_" + version;
        Map<String, PromptConfig.PromptTemplate> templates = promptConfig.getTemplates();

        if (templates != null && templates.containsKey(versionedKey)) {
            return templates.get(versionedKey).getContent();
        }

        // 回退到默认版本
        return getPrompt(templateName);
    }

    /**
     * 渲染Prompt模板（替换变量）
     *
     * @param templateName 模板名称
     * @param variables    变量映射
     * @return 渲染后的Prompt
     */
    public String renderPrompt(String templateName, Map<String, Object> variables) {
        String template = getPrompt(templateName);

        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replace(placeholder, value);
        }

        return rendered;
    }

    /**
     * 获取所有可用的Prompt模板名称
     */
    public List<String> getAvailableTemplates() {
        Map<String, PromptConfig.PromptTemplate> templates = promptConfig.getTemplates();
        if (templates == null) {
            return List.of();
        }
        return templates.entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 获取当前Prompt版本
     */
    public String getCurrentVersion() {
        return promptConfig.getVersion();
    }

    /**
     * 默认Prompt模板（当配置中不存在时使用）
     */
    private String getDefaultPrompt(String templateName) {
        return switch (templateName) {
            case "chat-system" -> getDefaultChatSystemPrompt();
            case "planner" -> getDefaultPlannerPrompt();
            case "executor" -> getDefaultExecutorPrompt();
            case "supervisor" -> getDefaultSupervisorPrompt();
            default -> {
                logger.error("未知的Prompt模板: {}", templateName);
                yield "";
            }
        };
    }

    /**
     * 默认Chat系统Prompt
     */
    private String getDefaultChatSystemPrompt() {
        return """
            你是一个专业的土木实验智能助手，可以获取当前时间、查询土木工程规范文档、搜索实验知识库，以及查询实验异常告警信息。
            当用户询问时间相关问题时，使用 getCurrentDateTime 工具。
            当用户需要查询土木工程规范、标准、技术指南或实验处理方法时，使用 queryInternalDocs 工具。
            当用户需要查询实验异常告警、监测数据或实验状态时，使用 queryExperimentAlerts 工具。
            当用户需要查询实验过程日志、设备操作记录或实验事件时，使用 queryExperimentLogs 工具。
            """;
    }

    /**
     * 默认Planner Prompt
     */
    private String getDefaultPlannerPrompt() {
        return """
            你是土木实验分析规划Agent，同时承担 Replanner 角色，负责：
            1. 读取当前输入任务 {input} 以及 Executor 的最近反馈 {executor_feedback}。
            2. 分析实验告警、实验日志、土木规范文档等信息，制定可执行的下一步步骤。
            3. 在执行阶段，输出 JSON，包含 decision (PLAN|EXECUTE|FINISH)、step 描述、预期要调用的工具、以及必要的上下文。
            4. 调用任何实验监控系统相关工具时，laboratory 参数必须使用正确格式（如 结构实验室、材料实验室），若不确定请省略以使用默认值。
            5. 严格禁止编造数据，只能引用工具返回的真实内容；如果连续 3 次调用同一工具仍失败或返回空结果，需停止该方向并在最终报告的结论部分说明"无法完成"的原因。
            """;
    }

    /**
     * 默认Executor Prompt
     */
    private String getDefaultExecutorPrompt() {
        return """
            你是 Executor Agent，负责读取 Planner 最新输出 {planner_plan}，只执行其中的第一步。
            - 确认步骤所需的工具与参数，尤其是 laboratory 参数要使用正确格式（结构实验室、材料实验室）；若 Planner 未给出则使用默认实验室。
            - 调用相应的工具并收集结果，如工具返回错误或空数据，需要将失败原因、请求参数一并记录，并停止进一步调用该工具（同一工具失败达到 3 次时应直接返回 FAILED）。
            - 将实验日志、监测数据、规范文档等证据整理成结构化摘要，标注对应的实验异常类型或实验编号，方便 Planner 填充"实验异常根因分析 / 处理方案执行"章节。
            - 以 JSON 形式返回执行状态、证据以及给 Planner 的建议，写入 executor_feedback，严禁编造未实际查询到的内容。
            """;
    }

    /**
     * 默认Supervisor Prompt
     */
    private String getDefaultSupervisorPrompt() {
        return """
            你是土木实验监控 Supervisor，负责调度 planner_agent 与 executor_agent：
            1. 当需要拆解任务或重新制定策略时，调用 planner_agent。
            2. 当 planner_agent 输出 decision=EXECUTE 时，调用 executor_agent 执行第一步。
            3. 根据 executor_agent 的反馈，评估是否需要再次调用 planner_agent，直到 decision=FINISH。
            4. FINISH 后，确保向最终用户输出完整的《实验异常分析报告》。
            5. 若步骤涉及实验监控系统相关工具，请确保使用正确的实验室名称。
            6. 如果发现 Planner/Executor 连续失败，必须终止流程，输出失败原因报告。
            """;
    }
}
