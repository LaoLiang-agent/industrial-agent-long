package com.industrial.agent.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GuardrailChain {

    private final InputGuard inputGuard;
    private final OutputGuard outputGuard;
    private final ActionGuard actionGuard;
    private final CircuitBreaker circuitBreaker;
    private final List<AuditEntry> auditLog = new ArrayList<>();

    public GuardrailChain(InputGuard inputGuard, OutputGuard outputGuard,
                          ActionGuard actionGuard, CircuitBreaker circuitBreaker) {
        this.inputGuard = inputGuard;
        this.outputGuard = outputGuard;
        this.actionGuard = actionGuard;
        this.circuitBreaker = circuitBreaker;
    }

    public GuardResult checkInput(String input) {
        if (!circuitBreaker.isAllowed()) {
            audit("INPUT", input, "CIRCUIT_BREAKER_OPEN");
            return GuardResult.blocked("熔断器开启，Agent 暂时不可用，请稍后重试");
        }
        GuardResult result = inputGuard.check(input);
        if (!result.isPassed()) {
            audit("INPUT", input, "BLOCKED: " + result.reason());
        }
        return result;
    }

    public GuardResult checkOutput(String output) {
        GuardResult result = outputGuard.check(output);
        if (!result.isPassed()) {
            audit("OUTPUT", output.substring(0, Math.min(100, output.length())), "BLOCKED: " + result.reason());
        }
        return result;
    }

    public GuardResult checkAction(String action) {
        GuardResult result = actionGuard.check(action);
        if (!result.isPassed()) {
            audit("ACTION", action, "BLOCKED: " + result.reason());
        }
        return result;
    }

    public void recordSuccess() { circuitBreaker.recordSuccess(); }
    public void recordFailure() { circuitBreaker.recordFailure(); }

    private void audit(String type, String content, String result) {
        AuditEntry entry = new AuditEntry(Instant.now(), type, content, result);
        auditLog.add(entry);
        log.info("[Audit] {} | {} | {}", type, result, content.substring(0, Math.min(50, content.length())));
    }

    public List<AuditEntry> getAuditLog() { return List.copyOf(auditLog); }
    public CircuitBreaker.State getCircuitBreakerState() { return circuitBreaker.getState(); }

    public record AuditEntry(Instant timestamp, String type, String content, String result) {}
}
