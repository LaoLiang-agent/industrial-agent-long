package com.industrial.agent.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(
        String name,
        String description,
        List<String> intentKeywords,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges
) {
    public record WorkflowNode(
            String id,
            NodeType type,
            String label,
            Map<String, Object> config
    ) {}

    public enum NodeType {
        EXPERT_CALL,
        TOOL_CALL,
        APPROVAL,
        NOTIFY
    }

    public record WorkflowEdge(String from, String to) {}
}
