package com.industrial.agent.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class ActionGuard {

    private static final Pattern STOP_MACHINE = Pattern.compile("停机|关机|shutdown|emergency.?stop");
    private static final Pattern MODIFY_PARAMS = Pattern.compile("修改参数|调整阈值|change.*parameter|set.*threshold");
    private static final Pattern CREATE_ORDER = Pattern.compile("创建工单|create.*order|生成工单");

    public RiskLevel classifyRisk(String action) {
        if (STOP_MACHINE.matcher(action).find()) return RiskLevel.L3;
        if (MODIFY_PARAMS.matcher(action).find()) return RiskLevel.L3;
        if (CREATE_ORDER.matcher(action).find()) return RiskLevel.L2;
        if (action.contains("诊断") || action.contains("分析")) return RiskLevel.L1;
        return RiskLevel.L0;
    }

    public GuardResult check(String action) {
        RiskLevel risk = classifyRisk(action);
        if (risk == RiskLevel.L3 || risk == RiskLevel.L4) {
            log.warn("[ActionGuard] HIGH RISK action blocked ({}): {}", risk, action);
            return GuardResult.blocked(String.format("高风险操作（%s: %s），需人工审批", risk.name(), risk.getDescription()));
        }
        return GuardResult.passed(risk);
    }
}
