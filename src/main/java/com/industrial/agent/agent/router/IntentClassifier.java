package com.industrial.agent.agent.router;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IntentClassifier {

    private final ChatModel chatModel;
    private final Map<Intent, AtomicLong> stats = Map.of(
            Intent.ALARM, new AtomicLong(),
            Intent.DATA, new AtomicLong(),
            Intent.DIAGNOSIS, new AtomicLong(),
            Intent.KNOWLEDGE, new AtomicLong(),
            Intent.WORKFLOW, new AtomicLong(),
            Intent.GENERAL, new AtomicLong()
    );

    private static final Pattern ALARM_PATTERN = Pattern.compile("告警|报警|警告|alarm");
    private static final Pattern DATA_PATTERN = Pattern.compile("温度|振动|压力|数据|趋势|实时|遥测|电流|转速|rpm");
    private static final Pattern DIAGNOSIS_PATTERN = Pattern.compile("诊断|故障|排查|检修|异常.*分析");
    private static final Pattern WORKFLOW_PATTERN = Pattern.compile("维修工单|故障处理流程|报修|停机維修|维修流程");
    private static final Pattern KNOWLEDGE_PATTERN = Pattern.compile("知识|手册|规程|怎么修|怎么处理|维修方案|操作指南");

    public IntentClassifier(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Intent classify(String message) {
        Intent intent = classifyByLlm(message);
        if (intent == null) {
            intent = classifyByKeyword(message);
        }
        stats.get(intent).incrementAndGet();
        log.info("[Router] Intent: {} ← \"{}\"", intent, message.length() > 50 ? message.substring(0, 50) + "..." : message);
        return intent;
    }

    private Intent classifyByLlm(String message) {
        try {
            String prompt = "将以下用户消息分类为一个类别，只返回类别名称，不要其他内容。\n" +
                    "类别：ALARM（告警查询）、DATA（数据查询）、DIAGNOSIS（故障诊断）、KNOWLEDGE（知识检索）、WORKFLOW（需要维修或故障处理的完整流程）、GENERAL（一般对话）\n" +
                    "消息：" + message;
            String result = chatModel.chat(prompt).trim().toUpperCase();
            for (Intent i : Intent.values()) {
                if (result.contains(i.name())) return i;
            }
        } catch (Exception e) {
            log.warn("[Router] LLM classification failed, falling back to keywords: {}", e.getMessage());
        }
        return null;
    }

    private Intent classifyByKeyword(String message) {
        if (WORKFLOW_PATTERN.matcher(message).find()) return Intent.WORKFLOW;
        if (DIAGNOSIS_PATTERN.matcher(message).find()) return Intent.DIAGNOSIS;
        if (ALARM_PATTERN.matcher(message).find()) return Intent.ALARM;
        if (DATA_PATTERN.matcher(message).find()) return Intent.DATA;
        if (KNOWLEDGE_PATTERN.matcher(message).find()) return Intent.KNOWLEDGE;
        return Intent.GENERAL;
    }

    public Map<String, Long> getStats() {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        stats.forEach((k, v) -> result.put(k.name(), v.get()));
        return result;
    }
}
