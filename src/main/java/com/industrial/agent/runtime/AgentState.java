package com.industrial.agent.runtime;

public enum AgentState {
    RECEIVED,
    SESSION_READY,
    CONTEXT_READY,
    MODEL_THINKING,
    TOOL_RUNNING,
    AWAITING_APPROVAL,
    POST_PROCESSING,
    COMPLETED,
    FAILED
}
