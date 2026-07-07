package com.industrial.agent.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.budget")
public class ToolBudget {

    private int maxLlmCalls = 5;
    private int maxReadToolsPerRequest = 3;
    private int maxWriteToolsPerRequest = 1;
    private int totalLatencyMs = 10000;

    public int getMaxLlmCalls() { return maxLlmCalls; }
    public void setMaxLlmCalls(int maxLlmCalls) { this.maxLlmCalls = maxLlmCalls; }

    public int getMaxReadToolsPerRequest() { return maxReadToolsPerRequest; }
    public void setMaxReadToolsPerRequest(int maxReadToolsPerRequest) { this.maxReadToolsPerRequest = maxReadToolsPerRequest; }

    public int getMaxWriteToolsPerRequest() { return maxWriteToolsPerRequest; }
    public void setMaxWriteToolsPerRequest(int maxWriteToolsPerRequest) { this.maxWriteToolsPerRequest = maxWriteToolsPerRequest; }

    public int getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(int totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
}
