package com.industrial.agent.agent.supervisor;

import com.industrial.agent.agent.experts.*;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SupervisorAgent {

    private final ChatModel chatModel;
    private final TaskPlanner taskPlanner;
    private final ApprovalGate approvalGate;
    private final AlarmExpert alarmExpert;
    private final DataExpert dataExpert;
    private final DiagnosisExpert diagnosisExpert;
    private final KnowledgeExpert knowledgeExpert;
    private final GeneralExpert generalExpert;

    public SupervisorAgent(ChatModel chatModel, TaskPlanner taskPlanner, ApprovalGate approvalGate,
                           AlarmExpert alarmExpert, DataExpert dataExpert,
                           DiagnosisExpert diagnosisExpert, KnowledgeExpert knowledgeExpert,
                           GeneralExpert generalExpert) {
        this.chatModel = chatModel;
        this.taskPlanner = taskPlanner;
        this.approvalGate = approvalGate;
        this.alarmExpert = alarmExpert;
        this.dataExpert = dataExpert;
        this.diagnosisExpert = diagnosisExpert;
        this.knowledgeExpert = knowledgeExpert;
        this.generalExpert = generalExpert;
    }

    public SupervisorResult execute(String message) {
        long start = System.currentTimeMillis();

        List<SubTask> tasks = taskPlanner.plan(message);
        List<SubTaskResult> results = new ArrayList<>();
        List<String> pendingApprovals = new ArrayList<>();

        for (SubTask task : tasks) {
            if (approvalGate.requiresApproval(task)) {
                String approvalId = approvalGate.requestApproval(task);
                pendingApprovals.add(approvalId);
                results.add(new SubTaskResult(task,
                        String.format("需要人工审批（%s），任务已暂停：%s", approvalId, task.task()),
                        "AWAITING_APPROVAL"));
            } else {
                String reply = dispatch(task);
                results.add(new SubTaskResult(task, reply));
            }
        }

        String summary = summarize(message, results);
        long elapsed = System.currentTimeMillis() - start;

        log.info("[Supervisor] Executed {} tasks ({}ms), {} pending approvals",
                tasks.size(), elapsed, pendingApprovals.size());

        return new SupervisorResult(tasks, results, summary, pendingApprovals, elapsed);
    }

    private String dispatch(SubTask task) {
        log.info("[Supervisor] Dispatching to {}: {}", task.expert(), task.task());
        return switch (task.expert()) {
            case "ALARM_EXPERT" -> alarmExpert.chat(task.task());
            case "DATA_EXPERT" -> dataExpert.chat(task.task());
            case "DIAGNOSIS_EXPERT" -> diagnosisExpert.chat(task.task());
            case "KNOWLEDGE_EXPERT" -> knowledgeExpert.chat(task.task());
            default -> generalExpert.chat(task.task());
        };
    }

    private String summarize(String originalMessage, List<SubTaskResult> results) {
        StringBuilder context = new StringBuilder();
        context.append("用户原始请求：").append(originalMessage).append("\n\n");
        for (var r : results) {
            context.append("【").append(r.task().expert()).append("】\n");
            context.append(r.reply(), 0, Math.min(r.reply().length(), 500)).append("\n\n");
        }

        String prompt = "基于以下多个专家的分析结果，生成一份简洁的综合报告。" +
                "去重、突出关键发现、给出最终建议。\n\n" + context;

        try {
            return chatModel.chat(prompt);
        } catch (Exception e) {
            log.warn("[Supervisor] Summary failed: {}", e.getMessage());
            return "综合报告生成失败，请查看各专家的独立分析结果。";
        }
    }

    public record SupervisorResult(List<SubTask> tasks, List<SubTaskResult> results,
                                    String summary, List<String> pendingApprovals, long latencyMs) {}
}
