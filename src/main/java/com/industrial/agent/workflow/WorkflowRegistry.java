package com.industrial.agent.workflow;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Slf4j
@Component
public class WorkflowRegistry {

    private final Map<String, WorkflowDefinition> workflows = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:workflows/*.yml");
            Yaml yaml = new Yaml();

            for (Resource r : resources) {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = yaml.load(r.getInputStream());

                String name = (String) raw.get("name");
                String description = (String) raw.get("description");

                @SuppressWarnings("unchecked")
                List<String> intentKeywords = (List<String>) raw.getOrDefault("intentKeywords", List.of());

                List<WorkflowDefinition.WorkflowNode> nodes = parseNodes(
                        (List<Map<String, Object>>) raw.get("nodes"));
                List<WorkflowDefinition.WorkflowEdge> edges = parseEdges(
                        (List<Map<String, String>>) raw.get("edges"));

                var def = new WorkflowDefinition(name, description, intentKeywords, nodes, edges);
                workflows.put(name, def);
                log.info("[WorkflowRegistry] Loaded workflow: {} ({} nodes)", name, nodes.size());
            }
        } catch (Exception e) {
            log.warn("[WorkflowRegistry] Failed to load workflows: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowDefinition.WorkflowNode> parseNodes(List<Map<String, Object>> rawNodes) {
        if (rawNodes == null) return List.of();
        List<WorkflowDefinition.WorkflowNode> nodes = new ArrayList<>();
        for (Map<String, Object> n : rawNodes) {
            nodes.add(new WorkflowDefinition.WorkflowNode(
                    (String) n.get("id"),
                    WorkflowDefinition.NodeType.valueOf((String) n.get("type")),
                    (String) n.get("label"),
                    (Map<String, Object>) n.getOrDefault("config", Map.of())
            ));
        }
        return nodes;
    }

    private List<WorkflowDefinition.WorkflowEdge> parseEdges(List<Map<String, String>> rawEdges) {
        if (rawEdges == null) return List.of();
        List<WorkflowDefinition.WorkflowEdge> edges = new ArrayList<>();
        for (Map<String, String> e : rawEdges) {
            edges.add(new WorkflowDefinition.WorkflowEdge(e.get("from"), e.get("to")));
        }
        return edges;
    }

    public Optional<WorkflowDefinition> findByName(String name) {
        return Optional.ofNullable(workflows.get(name));
    }

    public Optional<WorkflowDefinition> findByIntentKeywords(String message) {
        return workflows.values().stream()
                .filter(w -> w.intentKeywords().stream()
                        .anyMatch(message::contains))
                .findFirst();
    }

    public boolean matchesAnyWorkflow(String message) {
        return findByIntentKeywords(message).isPresent();
    }

    public Collection<WorkflowDefinition> listAll() {
        return workflows.values();
    }
}
