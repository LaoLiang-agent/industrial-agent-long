package com.industrial.agent.observability;

import com.industrial.agent.runtime.AgentState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AgentMetrics {

    private final Counter requestCounter;
    private final Counter toolCallCounter;
    private final Counter llmCallCounter;
    private final Counter failureCounter;
    private final Timer requestTimer;

    public AgentMetrics(MeterRegistry registry) {
        this.requestCounter = Counter.builder("agent.requests.total")
                .description("Total agent requests")
                .register(registry);
        this.toolCallCounter = Counter.builder("agent.tool.calls.total")
                .description("Total tool calls")
                .register(registry);
        this.llmCallCounter = Counter.builder("agent.llm.calls.total")
                .description("Total LLM calls")
                .register(registry);
        this.failureCounter = Counter.builder("agent.requests.failed")
                .description("Failed agent requests")
                .register(registry);
        this.requestTimer = Timer.builder("agent.request.duration")
                .description("Agent request duration")
                .register(registry);
    }

    public void recordRequest(AgentState finalState, long elapsedMs) {
        requestCounter.increment();
        requestTimer.record(elapsedMs, TimeUnit.MILLISECONDS);
        if (finalState == AgentState.FAILED) {
            failureCounter.increment();
        }
    }

    public void recordToolCall() {
        toolCallCounter.increment();
    }

    public void recordLlmCall() {
        llmCallCounter.increment();
    }
}
