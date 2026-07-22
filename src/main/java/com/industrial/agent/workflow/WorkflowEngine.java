package com.industrial.agent.workflow;

import com.industrial.agent.agent.supervisor.ApprovalGate;
import com.industrial.agent.agent.tools.WorkOrderTool;
import com.industrial.agent.rag.KnowledgeBaseTool;
import com.industrial.agent.runtime.RuntimeContext;
import com.industrial.agent.skill.Skill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkflowEngine {

    private final Map<String, Skill> skillMap = new LinkedHashMap<>();
    private final List<Skill> skills;
    private final ApprovalGate approvalGate;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final WorkOrderTool workOrderTool;

    public WorkflowEngine(List<Skill> skills, ApprovalGate approvalGate,
                          KnowledgeBaseTool knowledgeBaseTool, WorkOrderTool workOrderTool) {
        this.skills = skills;
        this.approvalGate = approvalGate;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.workOrderTool = workOrderTool;
    }

    @PostConstruct
    public void init() {
        skills.forEach(s -> skillMap.put(s.skillName(), s));
        log.info("[WorkflowEngine] Registered {} skills: {}", skillMap.size(), skillMap.keySet());
    }

    public WorkflowResult execute(WorkflowDefinition def, String input, RuntimeContext ctx) {
        long start = System.currentTimeMillis();
        List<String> sortedIds = topologicalSort(def);
        List<NodeExecution> executions = new ArrayList<>();
        List<String> pendingApprovals = new ArrayList<>();

        // Build accumulated context from prior nodes
        StringBuilder context = new StringBuilder(input);

        for (String nodeId : sortedIds) {
            WorkflowDefinition.WorkflowNode node = def.nodes().stream()
                    .filter(n -> n.id().equals(nodeId))
                    .findFirst().orElse(null);
            if (node == null) continue;

            log.info("[WorkflowEngine] Executing node: {} ({})", node.id(), node.type());
            NodeExecution execution = executeNode(node, context.toString(), ctx);
            executions.add(execution);

            if ("AWAITING_APPROVAL".equals(execution.status())) {
                pendingApprovals.add(node.id());
                break; // Stop at approval gate, wait for human
            }
            if ("FAILED".equals(execution.status())) {
                break;
            }

            // Append output to context for next nodes
            if (execution.output() != null && !execution.output().isBlank()) {
                context.append("\n\n[").append(node.label()).append("]: ")
                        .append(execution.output().length() > 500
                                ? execution.output().substring(0, 500) + "..."
                                : execution.output());
            }
        }

        boolean completed = pendingApprovals.isEmpty()
                && executions.stream().noneMatch(e -> "FAILED".equals(e.status()));
        long elapsed = System.currentTimeMillis() - start;

        log.info("[WorkflowEngine] {} completed={} ({}ms, {} nodes)",
                def.name(), completed, elapsed, executions.size());

        return new WorkflowResult(def.name(), executions, context.toString(), completed,
                pendingApprovals, elapsed);
    }

    private List<String> topologicalSort(WorkflowDefinition def) {
        // Build adjacency and in-degree
        Map<String, List<String>> adj = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (var node : def.nodes()) {
            adj.putIfAbsent(node.id(), new ArrayList<>());
            inDegree.putIfAbsent(node.id(), 0);
        }
        for (var edge : def.edges()) {
            adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String neighbor : adj.getOrDefault(node, List.of())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                if (inDegree.get(neighbor) == 0) queue.add(neighbor);
            }
        }

        if (sorted.size() != def.nodes().size()) {
            log.warn("[WorkflowEngine] Graph has cycle or disconnected nodes, using insertion order");
            return def.nodes().stream().map(WorkflowDefinition.WorkflowNode::id).collect(Collectors.toList());
        }
        return sorted;
    }

    private NodeExecution executeNode(WorkflowDefinition.WorkflowNode node, String context,
                                       RuntimeContext ctx) {
        try {
            return switch (node.type()) {
                case EXPERT_CALL -> executeExpert(node, context, ctx);
                case TOOL_CALL -> executeTool(node, context);
                case APPROVAL -> executeApproval(node, context);
                case NOTIFY -> executeNotify(node, context);
            };
        } catch (Exception e) {
            log.error("[WorkflowEngine] Node {} failed: {}", node.id(), e.getMessage());
            return new NodeExecution(node.id(), node.label(), e.getMessage(), "FAILED");
        }
    }

    private NodeExecution executeExpert(WorkflowDefinition.WorkflowNode node, String context,
                                         RuntimeContext ctx) {
        String skillName = (String) node.config().get("skillName");
        Skill skill = skillMap.get(skillName);
        if (skill == null) {
            return new NodeExecution(node.id(), node.label(),
                    "Skill not found: " + skillName, "FAILED");
        }
        String output = skill.chat(context, ctx);
        return new NodeExecution(node.id(), node.label(), output, "COMPLETED");
    }

    private NodeExecution executeTool(WorkflowDefinition.WorkflowNode node, String context) {
        String toolName = (String) node.config().get("toolName");
        return switch (toolName) {
            case "searchKnowledgeBase" -> {
                String result = knowledgeBaseTool.searchKnowledgeBase(context);
                yield new NodeExecution(node.id(), node.label(), result, "COMPLETED");
            }
            case "createWorkOrder" -> {
                // Extract device ID and issue from context (simple approach)
                String result = workOrderTool.createWorkOrder(
                        extractDeviceId(context),
                        "故障处理流程自动创建",
                        "MEDIUM",
                        "workflow-engine");
                yield new NodeExecution(node.id(), node.label(), result, "COMPLETED");
            }
            default -> new NodeExecution(node.id(), node.label(),
                    "Unknown tool: " + toolName, "FAILED");
        };
    }

    private NodeExecution executeApproval(WorkflowDefinition.WorkflowNode node, String context) {
        // Create a Subtask for approval
        var task = new com.industrial.agent.agent.supervisor.SubTask(
                "APPROVAL_GATE", context, "L3");
        if (approvalGate.requiresApproval(task)) {
            String approvalId = approvalGate.requestApproval(task);
            return new NodeExecution(node.id(), node.label(),
                    "需要人工审批（" + approvalId + "）", "AWAITING_APPROVAL");
        }
        return new NodeExecution(node.id(), node.label(), "无需审批，自动通过", "COMPLETED");
    }

    private NodeExecution executeNotify(WorkflowDefinition.WorkflowNode node, String context) {
        String msg = (String) node.config().getOrDefault("message", "流程完成");
        log.info("[WorkflowEngine] Notification: {}", msg);
        return new NodeExecution(node.id(), node.label(), msg, "COMPLETED");
    }

    private String extractDeviceId(String context) {
        // Simple regex to find device ID patterns like CNC-001, DEV-123
        var m = java.util.regex.Pattern.compile("[A-Z]{2,5}-\\d{3,5}")
                .matcher(context);
        return m.find() ? m.group() : "UNKNOWN-DEVICE";
    }

    public record WorkflowResult(
            String workflowName,
            List<NodeExecution> nodeExecutions,
            String accumulatedContext,
            boolean completed,
            List<String> pendingApprovals,
            long latencyMs
    ) {}

    public record NodeExecution(String nodeId, String label, String output, String status) {}
}
