package com.industrial.agent.agent.supervisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TaskPlanner {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    public TaskPlanner(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<SubTask> plan(String message) {
        String prompt = """
                将以下用户请求拆解为子任务列表。每个子任务指定一个执行者。
                执行者只能是：ALARM_EXPERT, DATA_EXPERT, DIAGNOSIS_EXPERT, KNOWLEDGE_EXPERT, GENERAL_EXPERT
                风险级别：L0（查询）、L1（分析）、L2（创建工单）、L3（停机/修改参数，需人工审批）
                返回 JSON 数组，格式：[{"expert":"ALARM_EXPERT","task":"查询 CNC-001 告警","riskLevel":"L0"}]
                只返回 JSON 数组，不要其他内容。
                用户请求：""" + message;

        try {
            String raw = chatModel.chat(prompt);
            Matcher m = JSON_ARRAY.matcher(raw);
            if (m.find()) {
                String json = m.group();
                List<TaskDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
                List<SubTask> tasks = dtos.stream()
                        .map(d -> new SubTask(d.expert(), d.task(), d.riskLevel() != null ? d.riskLevel() : "L0"))
                        .toList();
                log.info("[TaskPlanner] Planned {} sub-tasks for: {}", tasks.size(), message);
                return tasks;
            }
        } catch (Exception e) {
            log.warn("[TaskPlanner] LLM planning failed: {}", e.getMessage());
        }
        return List.of(new SubTask("DIAGNOSIS_EXPERT", message, "L1"));
    }

    private record TaskDto(String expert, String task, String riskLevel) {}
}
