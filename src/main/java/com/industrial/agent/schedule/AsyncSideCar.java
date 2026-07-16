package com.industrial.agent.schedule;

import com.industrial.agent.llm.TokenCostTracker;
import com.industrial.agent.memory.MemoryManager;
import com.industrial.agent.observability.AgentMetrics;
import com.industrial.agent.runtime.AgentState;
import com.industrial.agent.runtime.RuntimeContext;
import com.industrial.agent.tool.ExecutionAuditLog;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Async sidecar that offloads non-critical work from the hot response path.
 * <p>
 * The main path only does LLM inference + tool execution. Everything else —
 * cost tracking, audit persistence, memory recording, metrics reporting —
 * is pushed to this sidecar and runs asynchronously on a dedicated thread pool.
 */
@Slf4j
@Component
public class AsyncSideCar {

    private final TokenCostTracker costTracker;
    private final MemoryManager memory;
    private final AgentMetrics metrics;
    private final JdbcTemplate pg;

    public AsyncSideCar(TokenCostTracker costTracker, MemoryManager memory, AgentMetrics metrics,
                        @Qualifier("memoryJdbcTemplate") JdbcTemplate pg) {
        this.costTracker = costTracker;
        this.memory = memory;
        this.metrics = metrics;
        this.pg = pg;
    }

    @PostConstruct
    void initTable() {
        pg.execute("""
                CREATE TABLE IF NOT EXISTS tool_audit_logs (
                    execution_id VARCHAR(16) PRIMARY KEY,
                    tool_name    VARCHAR(64)  NOT NULL,
                    side_effect  VARCHAR(8)   NOT NULL,
                    params       TEXT,
                    result       TEXT,
                    status       VARCHAR(16)  NOT NULL,
                    duration_ms  BIGINT,
                    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Async("sidecarExecutor")
    public void recordCost(String userMessage, String reply) {
        try {
            costTracker.recordRequest(userMessage, reply);
        } catch (Exception e) {
            log.warn("[AsyncSideCar] cost recording failed: {}", e.getMessage());
        }
    }

    @Async("sidecarExecutor")
    public void recordTurn(RuntimeContext ctx, String userMessage, String reply) {
        try {
            memory.recordTurn(ctx, userMessage, reply);
        } catch (Exception e) {
            log.warn("[AsyncSideCar] memory recording failed: {}", e.getMessage());
        }
    }

    @Async("sidecarExecutor")
    public void reportMetrics(AgentState state, long elapsedMs) {
        try {
            metrics.recordRequest(state, elapsedMs);
        } catch (Exception e) {
            log.warn("[AsyncSideCar] metrics reporting failed: {}", e.getMessage());
        }
    }

    @Async("sidecarExecutor")
    public void persistAudit(ExecutionAuditLog entry) {
        try {
            pg.update(
                    "INSERT INTO tool_audit_logs (execution_id, tool_name, side_effect, params, result, status, duration_ms, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (execution_id) DO NOTHING",
                    entry.executionId(), entry.toolName(), entry.sideEffect().name(),
                    entry.params(), entry.result(), entry.status(),
                    entry.durationMs(), Timestamp.from(entry.timestamp()));
        } catch (Exception e) {
            log.warn("[AsyncSideCar] audit persistence failed for {}: {}", entry.executionId(), e.getMessage());
        }
    }
}
