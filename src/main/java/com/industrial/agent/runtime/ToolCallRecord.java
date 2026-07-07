package com.industrial.agent.runtime;

public record ToolCallRecord(
    String executionId,
    String toolName,
    String params,
    String result
) {}
