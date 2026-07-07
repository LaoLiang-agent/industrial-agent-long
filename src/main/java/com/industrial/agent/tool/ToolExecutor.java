package com.industrial.agent.tool;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolExecutor {

    private final ToolRegistry registry;
    private final ToolBudget budget;

    private final Map<String, String> idempotencyStore = new ConcurrentHashMap<>();
    private final List<ExecutionAuditLog> auditLogs = Collections.synchronizedList(new ArrayList<>());

    public ToolExecutor(ToolRegistry registry, ToolBudget budget) {
        this.registry = registry;
        this.budget = budget;
    }

    public String generateExecutionId(String sessionId, String toolName, String params) {
        String raw = sessionId + ":" + toolName + ":" + params;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public boolean isIdempotent(String executionId) {
        return idempotencyStore.containsKey(executionId);
    }

    public String getCachedResult(String executionId) {
        return idempotencyStore.get(executionId);
    }

    public void cacheResult(String executionId, String result) {
        idempotencyStore.put(executionId, result);
    }

    public ToolMeta validate(String toolName, int readCount, int writeCount) {
        ToolMeta meta = registry.get(toolName)
                .orElseThrow(() -> new ToolException("Unknown tool: " + toolName));

        if (meta.sideEffect() == SideEffect.WRITE && writeCount >= budget.getMaxWriteToolsPerRequest()) {
            throw new ToolException(
                    "Write budget exceeded: " + writeCount + "/" + budget.getMaxWriteToolsPerRequest());
        }
        if (meta.sideEffect() == SideEffect.READ && readCount >= budget.getMaxReadToolsPerRequest()) {
            throw new ToolException(
                    "Read budget exceeded: " + readCount + "/" + budget.getMaxReadToolsPerRequest());
        }
        return meta;
    }

    public void audit(String executionId, String toolName, SideEffect sideEffect,
                      String params, String result, String status, long durationMs) {
        ExecutionAuditLog entry = new ExecutionAuditLog(
                executionId, toolName, sideEffect, params, result, status, durationMs, Instant.now());
        auditLogs.add(entry);
        log.info("[ToolExecutor] {} | {} | {} | {}ms | {}",
                toolName, executionId, status, durationMs, result.substring(0, Math.min(100, result.length())));
    }

    public List<ExecutionAuditLog> getAuditLogs() {
        return List.copyOf(auditLogs);
    }

    public int getBudgetMaxLlmCalls() { return budget.getMaxLlmCalls(); }
    public int getBudgetTotalLatencyMs() { return budget.getTotalLatencyMs(); }

    @PreDestroy
    public void cleanup() {
        int size = auditLogs.size();
        log.info("[ToolExecutor] Shutting down, {} audit entries recorded", size);
    }

    public static class ToolException extends RuntimeException {
        public ToolException(String message) { super(message); }
    }
}
