package com.industrial.agent.observability;

import com.industrial.agent.runtime.AgentState;
import com.industrial.agent.runtime.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger("agent.trace");

    public void attachTrace(RuntimeContext ctx) {
        MDC.put("trace_id", ctx.getTraceId());
        MDC.put("session_id", ctx.getSessionId());
        MDC.put("tenant_id", ctx.getTenantId());
    }

    public void detachTrace() {
        MDC.remove("trace_id");
        MDC.remove("session_id");
        MDC.remove("tenant_id");
    }

    public void stateTransition(RuntimeContext ctx, AgentState from, AgentState to) {
        attachTrace(ctx);
        log.info("state_transition from={} to={} elapsed_ms={}", from, to, ctx.elapsedMs());
    }

    public void llmCall(RuntimeContext ctx, String promptHash, int inputTokens, int outputTokens, long latencyMs, String model) {
        attachTrace(ctx);
        log.info("llm_call prompt_hash={} input_tokens={} output_tokens={} latency_ms={} model={}",
                promptHash, inputTokens, outputTokens, latencyMs, model);
    }

    public void toolCall(RuntimeContext ctx, String executionId, String toolName,
                         String params, String resultPreview, String status, long durationMs) {
        attachTrace(ctx);
        int previewLen = Math.min(resultPreview != null ? resultPreview.length() : 0, 100);
        log.info("tool_call execution_id={} tool_name={} params={} result_preview={} status={} duration_ms={}",
                executionId, toolName, params,
                resultPreview != null ? resultPreview.substring(0, previewLen) : "null",
                status, durationMs);
    }

    public void requestCompleted(RuntimeContext ctx, String resultPreview) {
        attachTrace(ctx);
        int previewLen = Math.min(resultPreview != null ? resultPreview.length() : 0, 150);
        log.info("request_completed state={} elapsed_ms={} result_preview={}",
                ctx.getCurrentState(), ctx.elapsedMs(),
                resultPreview != null ? resultPreview.substring(0, previewLen) : "null");
    }

    public void requestFailed(RuntimeContext ctx, String error) {
        attachTrace(ctx);
        log.error("request_failed state={} elapsed_ms={} error={}",
                ctx.getCurrentState(), ctx.elapsedMs(), error);
    }
}
