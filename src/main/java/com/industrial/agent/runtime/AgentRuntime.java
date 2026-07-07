package com.industrial.agent.runtime;

import com.industrial.agent.guardrail.CircuitBreaker;
import com.industrial.agent.observability.AgentMetrics;
import com.industrial.agent.observability.StructuredLogger;
import com.industrial.agent.tool.ToolBudget;
import com.industrial.agent.tool.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AgentRuntime {

    private final ToolExecutor toolExecutor;
    private final ToolBudget budget;
    private final CircuitBreaker circuitBreaker;
    private final StructuredLogger tracer;
    private final AgentMetrics metrics;

    public AgentRuntime(ToolExecutor toolExecutor, ToolBudget budget, CircuitBreaker circuitBreaker,
                        StructuredLogger tracer, AgentMetrics metrics) {
        this.toolExecutor = toolExecutor;
        this.budget = budget;
        this.circuitBreaker = circuitBreaker;
        this.tracer = tracer;
        this.metrics = metrics;
    }

    public RuntimeContext createContext(String sessionId, String tenantId, String userId) {
        String traceId = UUID.randomUUID().toString().substring(0, 12);
        long deadline = System.currentTimeMillis() + budget.getTotalLatencyMs();
        return new RuntimeContext(traceId, sessionId, tenantId, userId, deadline);
    }

    public <T> T execute(RuntimeContext ctx, Supplier<T> action) {
        tracer.attachTrace(ctx);

        if (!circuitBreaker.isAllowed()) {
            tracer.requestFailed(ctx, "Circuit breaker open");
            ctx.transition(AgentState.FAILED);
            metrics.recordRequest(AgentState.FAILED, ctx.elapsedMs());
            throw new RuntimeExceededException("Circuit breaker open");
        }

        AgentState prev = ctx.getCurrentState();
        ctx.transition(AgentState.SESSION_READY);
        tracer.stateTransition(ctx, prev, AgentState.SESSION_READY);

        prev = ctx.getCurrentState();
        ctx.transition(AgentState.CONTEXT_READY);
        tracer.stateTransition(ctx, prev, AgentState.CONTEXT_READY);

        if (ctx.isExpired()) {
            ctx.transition(AgentState.FAILED);
            circuitBreaker.recordFailure();
            metrics.recordRequest(AgentState.FAILED, ctx.elapsedMs());
            tracer.requestFailed(ctx, "Deadline exceeded");
            throw new RuntimeExceededException("Deadline exceeded before execution");
        }

        try {
            prev = ctx.getCurrentState();
            ctx.transition(AgentState.MODEL_THINKING);
            tracer.stateTransition(ctx, prev, AgentState.MODEL_THINKING);

            T result = action.get();

            prev = ctx.getCurrentState();
            ctx.transition(AgentState.POST_PROCESSING);
            tracer.stateTransition(ctx, prev, AgentState.POST_PROCESSING);

            ctx.transition(AgentState.COMPLETED);
            circuitBreaker.recordSuccess();
            metrics.recordRequest(AgentState.COMPLETED, ctx.elapsedMs());
            tracer.requestCompleted(ctx, result != null ? result.toString() : null);
            return result;
        } catch (ToolExecutor.ToolException e) {
            tracer.requestFailed(ctx, e.getMessage());
            ctx.transition(AgentState.FAILED);
            circuitBreaker.recordFailure();
            metrics.recordRequest(AgentState.FAILED, ctx.elapsedMs());
            throw e;
        } catch (RuntimeExceededException e) {
            throw e;
        } catch (Exception e) {
            tracer.requestFailed(ctx, e.getMessage());
            ctx.transition(AgentState.FAILED);
            circuitBreaker.recordFailure();
            metrics.recordRequest(AgentState.FAILED, ctx.elapsedMs());
            throw new RuntimeException("Agent execution failed", e);
        } finally {
            tracer.detachTrace();
        }
    }

    public static class RuntimeExceededException extends RuntimeException {
        public RuntimeExceededException(String message) { super(message); }
    }
}
