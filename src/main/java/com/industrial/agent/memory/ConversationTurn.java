package com.industrial.agent.memory;

/** A single conversation turn stored in L2 Conversation Memory. */
public record ConversationTurn(String role, String content, long timestamp) {}
