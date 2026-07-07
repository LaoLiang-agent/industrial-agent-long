package com.industrial.agent.tool;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private final Map<String, ToolMeta> registry = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        register("queryDeviceAlarms",     SideEffect.READ,  3, false, 5000);
        register("queryDeviceHistory",    SideEffect.READ,  3, false, 5000);
        register("queryRealtimeData",     SideEffect.READ,  3, false, 5000);
        register("searchKnowledgeBase",   SideEffect.READ,  3, false, 5000);
        register("generateDiagnosis",     SideEffect.READ,  3, false, 5000);
        register("createWorkOrder",       SideEffect.WRITE, 1, true,  10000);
        register("startWorkOrder",        SideEffect.WRITE, 1, false, 10000);
        register("completeWorkOrder",     SideEffect.WRITE, 1, false, 10000);
        register("getWorkOrder",          SideEffect.READ,  3, false, 5000);
    }

    public void register(String name, SideEffect sideEffect, int maxCalls, boolean requiresApproval, int timeoutMs) {
        registry.put(name, new ToolMeta(name, sideEffect, maxCalls, requiresApproval, timeoutMs));
    }

    public Optional<ToolMeta> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public Set<String> toolNames() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public List<ToolMeta> allTools() {
        return List.copyOf(registry.values());
    }
}
