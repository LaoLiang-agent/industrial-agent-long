package com.industrial.agent.agent.router;

import com.industrial.agent.agent.experts.*;
import com.industrial.agent.rag.RagContextHolder;
import com.industrial.agent.runtime.RuntimeContext;
import com.industrial.agent.workflow.WorkflowEngine;
import com.industrial.agent.workflow.WorkflowRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RouterAgent {

    private final IntentClassifier classifier;
    private final AlarmExpert alarmExpert;
    private final DataExpert dataExpert;
    private final DiagnosisExpert diagnosisExpert;
    private final KnowledgeExpert knowledgeExpert;
    private final GeneralExpert generalExpert;
    private final WorkflowEngine workflowEngine;
    private final WorkflowRegistry workflowRegistry;

    public RouterAgent(IntentClassifier classifier, AlarmExpert alarmExpert,
                       DataExpert dataExpert, DiagnosisExpert diagnosisExpert,
                       KnowledgeExpert knowledgeExpert, GeneralExpert generalExpert,
                       WorkflowEngine workflowEngine, WorkflowRegistry workflowRegistry) {
        this.classifier = classifier;
        this.alarmExpert = alarmExpert;
        this.dataExpert = dataExpert;
        this.diagnosisExpert = diagnosisExpert;
        this.knowledgeExpert = knowledgeExpert;
        this.generalExpert = generalExpert;
        this.workflowEngine = workflowEngine;
        this.workflowRegistry = workflowRegistry;
    }

    public RouteResult route(String message) {
        return route(message, null);
    }

    public RouteResult route(String message, RuntimeContext ctx) {
        if (ctx != null) {
            RagContextHolder.set(ctx.getTenantId(), ctx.getUserId());
        }
        try {
            long start = System.currentTimeMillis();
            Intent intent = classifier.classify(message);

            String reply = switch (intent) {
                case ALARM -> alarmExpert.chat(message);
                case DATA -> dataExpert.chat(message);
                case DIAGNOSIS -> diagnosisExpert.chat(message);
                case KNOWLEDGE -> knowledgeExpert.chat(message);
                case WORKFLOW -> executeWorkflow(message, ctx);
                case GENERAL -> generalExpert.chat(message);
            };

            long elapsed = System.currentTimeMillis() - start;
            log.info("[Router] {} → {} ({}ms)", intent, reply.length() > 50 ? reply.substring(0, 50) + "..." : reply, elapsed);

            return new RouteResult(intent, reply, elapsed);
        } finally {
            RagContextHolder.clear();
        }
    }

    public Map<String, Long> getStats() {
        return classifier.getStats();
    }

    private String executeWorkflow(String message, RuntimeContext ctx) {
        var wf = workflowRegistry.findByIntentKeywords(message);
        if (wf.isPresent()) {
            var result = workflowEngine.execute(wf.get(), message, ctx);
            if (result.completed()) {
                return "工作流执行完成 (" + result.nodeExecutions().size() + " 步骤)\n\n" +
                       result.accumulatedContext();
            } else {
                return "工作流已暂停，等待审批。审批ID: " + result.pendingApprovals();
            }
        }
        // Fall back to DiagnosisExpert if no workflow matches
        return diagnosisExpert.chat(message);
    }

    public record RouteResult(Intent intent, String reply, long latencyMs) {}
}
