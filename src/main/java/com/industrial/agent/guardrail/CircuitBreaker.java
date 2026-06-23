package com.industrial.agent.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final int FAILURE_THRESHOLD = 3;
    private static final long RECOVERY_MS = 60_000;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile long openedAt = 0;

    public boolean isAllowed() {
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAt > RECOVERY_MS) {
                state.set(State.HALF_OPEN);
                log.info("[CircuitBreaker] HALF_OPEN — allowing one test request");
                return true;
            }
            return false;
        }
        return true; // HALF_OPEN allows one request
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            log.info("[CircuitBreaker] CLOSED — recovered");
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && state.get() == State.CLOSED) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
            log.warn("[CircuitBreaker] OPEN — {} consecutive failures, blocking for {}s",
                    failures, RECOVERY_MS / 1000);
        }
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
            log.warn("[CircuitBreaker] OPEN again — half-open test failed");
        }
    }

    public State getState() { return state.get(); }
    public int getFailureCount() { return consecutiveFailures.get(); }
}
