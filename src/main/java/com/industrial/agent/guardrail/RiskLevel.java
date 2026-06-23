package com.industrial.agent.guardrail;

public enum RiskLevel {
    L0("无风险", "查询操作"),
    L1("低风险", "分析操作"),
    L2("中风险", "创建/修改操作"),
    L3("高风险", "停机/参数修改"),
    L4("极高风险", "紧急停机/安全联锁");

    private final String label;
    private final String description;

    RiskLevel(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
