package com.industrial.agent.guardrail;

public class GuardResult {
    private final boolean passed;
    private final String reason;
    private final RiskLevel riskLevel;

    private GuardResult(boolean passed, String reason, RiskLevel riskLevel) {
        this.passed = passed;
        this.reason = reason;
        this.riskLevel = riskLevel;
    }

    public static GuardResult passed() { return new GuardResult(true, null, RiskLevel.L0); }
    public static GuardResult passed(RiskLevel level) { return new GuardResult(true, null, level); }
    public static GuardResult blocked(String reason) { return new GuardResult(false, reason, null); }

    public boolean isPassed() { return passed; }
    public String reason() { return reason; }
    public RiskLevel riskLevel() { return riskLevel; }
}
