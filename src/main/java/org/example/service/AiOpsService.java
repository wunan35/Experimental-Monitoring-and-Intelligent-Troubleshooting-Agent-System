package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.example.exception.AgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 土木实验智能监控服务
 *
 * <p>负责多 Agent 协作的实验异常分析流程，实现"规划→执行→再规划"的闭环分析模式。</p>
 *
 * <h2>架构设计</h2>
 * <p>采用 Supervisor 模式编排多个专业 Agent：</p>
 * <pre>
 *                    ┌──────────────────┐
 *                    │ SupervisorAgent  │
 *                    │   (调度控制)      │
 *                    └────────┬─────────┘
 *                             │
 *              ┌──────────────┼──────────────┐
 *              │              │              │
 *              ▼              ▼              ▼
 *       ┌──────────┐   ┌──────────┐   ┌──────────┐
 *       │ Planner  │   │ Executor │   │  Tools   │
 *       │ (规划)   │   │ (执行)   │   │ (工具集) │
 *       └──────────┘   └──────────┘   └──────────┘
 * </pre>
 *
 * <h2>Agent 角色</h2>
 * <ul>
 *   <li><b>SupervisorAgent</b> - 总调度器，协调 Planner 和 Executor 的协作</li>
 *   <li><b>PlannerAgent</b> - 规划器，分析告警、拆解任务、综合生成报告</li>
 *   <li><b>ExecutorAgent</b> - 执行器，执行具体排查步骤并反馈结果</li>
 * </ul>
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>Supervisor 接收任务，调用 Planner 进行初步规划</li>
 *   <li>Planner 分析告警数据，拆解排查步骤</li>
 *   <li>Supervisor 调用 Executor 执行首个排查步骤</li>
 *   <li>Executor 调用工具获取实时数据，反馈执行结果</li>
 *   <li>Planner 根据反馈进行再规划，生成最终报告</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Autowired
 * private AiOpsService aiOpsService;
 *
 * // 执行分析流程
 * Optional<OverAllState> result = aiOpsService.executeAiOpsAnalysis(chatModel, tools);
 *
 * // 提取报告
 * Optional<String> report = aiOpsService.extractFinalReport(result.get());
 * }</pre>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 * @see ChatService
 * @see PromptService
 */
@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private PromptService promptService;

    @Autowired
    @Qualifier("agentExecutor")
    private Executor agentExecutor;

    @Autowired
    private TimeLimiter agentTimeLimiter;

    /**
     * 执行 AI Ops 告警分析流程
     *
     * @param chatModel      大模型实例
     * @param toolCallbacks  工具回调数组
     * @return 分析结果状态
     * @throws GraphRunnerException 如果 Agent 执行失败
     */
    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws AgentException {
        logger.info("开始执行土木实验监控多 Agent 协作流程");

        // 构建 Planner 和 Executor Agent
        ReactAgent plannerAgent = buildPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildExecutorAgent(chatModel, toolCallbacks);

        // 构建 Supervisor Agent
        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("ai_ops_supervisor")
                .description("负责调度 Planner 与 Executor 的多 Agent 实验监控控制器")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = "你是土木实验监控专家，接到了实验异常自动分析任务。请结合工具调用，执行**规划→执行→再规划**的闭环，并最终按照固定模板输出《实验异常分析报告》。禁止编造虚假数据，如连续多次查询失败需诚实反馈无法完成的原因。";

        logger.info("调用土木实验监控 Supervisor Agent 开始编排...");

        // 使用异步执行和超时控制
        CompletableFuture<Optional<OverAllState>> future = CompletableFuture.supplyAsync(
            () -> {
                try {
                    return supervisorAgent.invoke(taskPrompt);
                } catch (Exception e) {
                    throw new RuntimeException("SupervisorAgent invoke failed", e);
                }
            },
            agentExecutor
        );

        try {
            // 应用超时控制
            return future.get(agentTimeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis(),
                             java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("AI Ops 分析超时，超过 {} 毫秒", agentTimeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis());
            future.cancel(true);
            throw new AgentException("AI Ops analysis timed out", e);
        } catch (Exception e) {
            logger.error("AI Ops 分析执行失败", e);
            throw new AgentException("AI Ops analysis failed", e);
        }
    }

    /**
     * 从执行结果中提取最终报告文本
     *
     * @param state 执行状态
     * @return 报告文本（如果存在）
     */
    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("开始提取最终报告...");

        // 提取 Planner 最终输出（包含完整的实验异常分析报告）
        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("成功提取到 Planner 最终报告，长度: {}", reportText.length());
            return Optional.of(reportText);
        } else {
            logger.warn("未能提取到 Planner 最终报告");
            return Optional.empty();
        }
    }

    /**
     * 构建 Planner Agent
     */
    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("负责拆解实验异常、规划与再规划步骤")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(chatService.buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("planner_plan")
                .build();
    }

    /**
     * 构建 Executor Agent
     */
    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("负责执行 Planner 的首个实验排查步骤并及时反馈")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(chatService.buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    /**
     * 构建 Planner Agent 系统提示词
     * 使用 PromptService 获取可配置的提示词模板
     */
    private String buildPlannerPrompt() {
        // 从 PromptService 获取 Planner 提示词（支持版本管理）
        return promptService.getPrompt("planner");
    }

    /**
     * 构建 Executor Agent 系统提示词
     * 使用 PromptService 获取可配置的提示词模板
     */
    private String buildExecutorPrompt() {
        // 从 PromptService 获取 Executor 提示词（支持版本管理）
        return promptService.getPrompt("executor");
    }

    /**
     * 构建 Supervisor Agent 系统提示词
     * 使用 PromptService 获取可配置的提示词模板
     */
    private String buildSupervisorSystemPrompt() {
        // 从 PromptService 获取 Supervisor 提示词（支持版本管理）
        return promptService.getPrompt("supervisor");
    }
}
