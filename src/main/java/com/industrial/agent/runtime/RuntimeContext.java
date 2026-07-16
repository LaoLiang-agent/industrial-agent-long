package com.industrial.agent.runtime;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class RuntimeContext {

    private final String traceId;
    private final String sessionId;
    private final String tenantId;
    private final String userId;
    private final long startedAtMs;
    private final long deadlineMs;
    private final List<ToolCallRecord> toolCallHistory = new CopyOnWriteArrayList<>();
    private final Map<String, Object> workingMemory = new LinkedHashMap<>();
    private AgentState currentState;
    private String executionPlan;
    private int llmCallCount;
    private int readToolCount;
    private int writeToolCount;

    public RuntimeContext(String traceId, String sessionId, String tenantId, String userId,
                          long deadlineMs) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.startedAtMs = System.currentTimeMillis();
        this.deadlineMs = deadlineMs;
        this.currentState = AgentState.RECEIVED;
    }

    public RuntimeContext transition(AgentState next) {
        AgentState prev = this.currentState;
        this.currentState = next;
        return this;
    }

    public void recordToolCall(String executionId, String toolName, String params, String result) {
        toolCallHistory.add(new ToolCallRecord(executionId, toolName, params, result));
    }

    public void putWorkingMemory(String key, Object value) {
        workingMemory.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getWorkingMemory(String key) {
        return (T) workingMemory.get(key);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > deadlineMs;
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - startedAtMs;
    }

    // Getters
    public String getTraceId() { return traceId; }
    public String getSessionId() { return sessionId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public long getStartedAtMs() { return startedAtMs; }
    public long getDeadlineMs() { return deadlineMs; }
    public AgentState getCurrentState() { return currentState; }
    public String getExecutionPlan() { return executionPlan; }
    public void setExecutionPlan(String executionPlan) { this.executionPlan = executionPlan; }
    public List<ToolCallRecord> getToolCallHistory() { return List.copyOf(toolCallHistory); }
    public int getToolCallCount() { return toolCallHistory.size(); }

    public int getLlmCallCount() { return llmCallCount; }
    public void incrementLlmCalls() { this.llmCallCount++; }
    public int getReadToolCount() { return readToolCount; }
    public void incrementReadTools() { this.readToolCount++; }
    public int getWriteToolCount() { return writeToolCount; }
    public void incrementWriteTools() { this.writeToolCount++; }
}
