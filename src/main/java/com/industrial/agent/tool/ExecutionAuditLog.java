package com.industrial.agent.tool;

import java.time.Instant;

public record ExecutionAuditLog(
    String executionId,
    String toolName,
    SideEffect sideEffect,
    String params,
    String result,
    String status,
    long durationMs,
    Instant timestamp
) {}
