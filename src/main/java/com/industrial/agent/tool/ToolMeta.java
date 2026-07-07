package com.industrial.agent.tool;

public record ToolMeta(
    String name,
    SideEffect sideEffect,
    int maxCallsPerRequest,
    boolean requiresApproval,
    int timeoutMs
) {}
