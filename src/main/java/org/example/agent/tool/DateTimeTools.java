package org.example.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日期时间工具
 *
 * <p>提供获取当前日期时间的工具方法，Agent 可用于时间相关推理。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>计算告警持续时间</li>
 *   <li>判断日志时间范围</li>
 *   <li>生成带时间戳的报告</li>
 * </ul>
 *
 * <h2>返回格式</h2>
 * <p>返回 ISO 8601 格式的日期时间字符串，包含时区信息：</p>
 * <pre>{@code
 * 2024-03-22T14:30:00+08:00[Asia/Shanghai]
 * }</pre>
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@Component
public class DateTimeTools {

    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_GET_CURRENT_DATETIME = "getCurrentDateTime";

    /**
     * 获取当前日期时间
     *
     * @return 当前日期时间字符串，格式为 ISO 8601，包含用户时区
     */
    @Tool(description = "Get the current date and time in the user's timezone")
    public String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
