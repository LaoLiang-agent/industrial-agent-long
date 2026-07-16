package com.industrial.agent.schedule;

import com.industrial.agent.runtime.RuntimeContext;
import com.industrial.agent.tool.SideEffect;
import com.industrial.agent.tool.ToolBudget;
import com.industrial.agent.tool.ToolMeta;
import com.industrial.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class BudgetManager {

    private final ToolBudget budget;
    private final ToolRegistry registry;
    private final StringRedisTemplate redis;
    private final ExecutorService readExecutor;

    public BudgetManager(ToolBudget budget, ToolRegistry registry, StringRedisTemplate redis) {
        this.budget = budget;
        this.registry = registry;
        this.redis = redis;
        this.readExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "tool-read-");
            t.setDaemon(true);
            return t;
        });
    }

    /** Check LLM call budget before each reasoning round. */
    public void checkLlmBudget(RuntimeContext ctx) {
        int count = ctx.getLlmCallCount();
        int max = budget.getMaxLlmCalls();
        if (count >= max) {
            throw new BudgetExceededException(
                    "LLM call budget exceeded: " + count + "/" + max);
        }
    }

    /** Record an LLM call after it completes. */
    public void recordLlmCall(RuntimeContext ctx) {
        ctx.incrementLlmCalls();
    }

    /** Check deadline, throw if expired. */
    public void checkDeadline(RuntimeContext ctx) {
        if (ctx.isExpired()) {
            throw new BudgetExceededException(
                    "Deadline exceeded: " + ctx.elapsedMs() + "ms / " + budget.getTotalLatencyMs() + "ms");
        }
    }

    /** Check write budget, acquire Redis lock for WRITE tools. */
    public void checkWriteBudget(RuntimeContext ctx, String toolName) {
        ToolMeta meta = registry.get(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
        if (meta.sideEffect() != SideEffect.WRITE) return;

        int count = ctx.getWriteToolCount();
        if (count >= budget.getMaxWriteToolsPerRequest()) {
            throw new BudgetExceededException(
                    "Write budget exceeded: " + count + "/" + budget.getMaxWriteToolsPerRequest());
        }
    }

    /** Check read budget. */
    public void checkReadBudget(RuntimeContext ctx) {
        int count = ctx.getReadToolCount();
        if (count >= budget.getMaxReadToolsPerRequest()) {
            throw new BudgetExceededException(
                    "Read budget exceeded: " + count + "/" + budget.getMaxReadToolsPerRequest());
        }
    }

    /** Record a tool call (READ or WRITE). */
    public void recordToolCall(RuntimeContext ctx, String toolName) {
        ToolMeta meta = registry.get(toolName).orElse(null);
        if (meta == null) return;
        if (meta.sideEffect() == SideEffect.WRITE) {
            ctx.incrementWriteTools();
        } else {
            ctx.incrementReadTools();
        }
    }

    /**
     * Execute multiple READ tools in parallel and return results.
     * Used by SupervisorAgent for multi-tool planning scenarios.
     */
    public List<ToolResult> executeReadsInParallel(List<ToolTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return List.of();
        List<CompletableFuture<ToolResult>> futures = tasks.stream()
                .map(t -> CompletableFuture.supplyAsync(() -> executeOne(t), readExecutor))
                .toList();
        List<ToolResult> results = new ArrayList<>();
        for (CompletableFuture<ToolResult> f : futures) {
            try {
                results.add(f.get(10, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                results.add(ToolResult.timeout(tasks.get(results.size()).toolName()));
            } catch (Exception e) {
                results.add(ToolResult.error(tasks.get(results.size()).toolName(), e.getMessage()));
            }
        }
        return results;
    }

    private ToolResult executeOne(ToolTask task) {
        try {
            Object result = task.action().call();
            return ToolResult.ok(task.toolName(), result);
        } catch (Exception e) {
            return ToolResult.error(task.toolName(), e.getMessage());
        }
    }

    /**
     * Acquire a Redis-based distributed lock for WRITE tool execution.
     * Returns true if lock acquired, false otherwise.
     */
    public boolean acquireWriteLock(String sessionId, int timeoutSeconds) {
        String key = "lock:write:" + sessionId;
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, "locked", Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(ok);
    }

    /** Release the write lock. */
    public void releaseWriteLock(String sessionId) {
        redis.delete("lock:write:" + sessionId);
    }

    /** Exposed budget values for diagnostics. */
    public int maxLlmCalls() { return budget.getMaxLlmCalls(); }
    public int maxReadTools() { return budget.getMaxReadToolsPerRequest(); }
    public int maxWriteTools() { return budget.getMaxWriteToolsPerRequest(); }
    public int totalLatencyMs() { return budget.getTotalLatencyMs(); }

    // ---- inner types ----

    public record ToolTask(String toolName, java.util.concurrent.Callable<Object> action) {}

    public record ToolResult(String toolName, Object data, String error, boolean timeout, boolean ok) {
        public static ToolResult ok(String name, Object data) {
            return new ToolResult(name, data, null, false, true);
        }

        public static ToolResult error(String name, String error) {
            return new ToolResult(name, null, error, false, false);
        }

        public static ToolResult timeout(String name) {
            return new ToolResult(name, null, "Timeout after 10s", true, false);
        }
    }

    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String message) { super(message); }
    }
}
