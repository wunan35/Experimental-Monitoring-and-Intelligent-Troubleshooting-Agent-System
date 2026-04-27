package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图识别配置
 */
@Component
@ConfigurationProperties(prefix = "intent-recognition")
public class IntentRecognitionConfig {

    private boolean enabled = true;
    private boolean ruleFirst = true;
    private boolean llmFallback = true;
    private String model = "qwen3-max";
    private double confidenceThreshold = 0.7;

    private Rules rules = new Rules();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRuleFirst() {
        return ruleFirst;
    }

    public void setRuleFirst(boolean ruleFirst) {
        this.ruleFirst = ruleFirst;
    }

    public boolean isLlmFallback() {
        return llmFallback;
    }

    public void setLlmFallback(boolean llmFallback) {
        this.llmFallback = llmFallback;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public Rules getRules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }

    public static class Rules {
        private List<String> knowledgeRetrieval = List.of(
                "文档", "知识库", "规定", "手册", "操作手册", "流程是什么",
                "怎么操作", "如何处理", "解决方案", "故障排查", "SOP"
        );
        private List<String> toolCalling = List.of(
                "查指标", "查监控", "查日志", "查告警", "查询数据", "获取状态",
                "执行", "重启", "关闭", "打开", "启动", "停止"
        );
        private List<String> chitchat = List.of(
                "你好", "谢谢", "在吗", "辛苦了", "聊聊", "随便问问", "天气", "吐槽", "抱怨"
        );

        public List<String> getKnowledgeRetrieval() {
            return knowledgeRetrieval;
        }

        public void setKnowledgeRetrieval(List<String> knowledgeRetrieval) {
            this.knowledgeRetrieval = knowledgeRetrieval;
        }

        public List<String> getToolCalling() {
            return toolCalling;
        }

        public void setToolCalling(List<String> toolCalling) {
            this.toolCalling = toolCalling;
        }

        public List<String> getChitchat() {
            return chitchat;
        }

        public void setChitchat(List<String> chitchat) {
            this.chitchat = chitchat;
        }
    }
}
