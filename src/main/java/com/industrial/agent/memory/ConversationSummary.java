package com.industrial.agent.memory;

/**
 * L3 structured summary of a conversation, generated asynchronously after each turn.
 * The four content fields mirror the design's summary contract:
 * {user_goal, confirmed_facts, pending_actions, constraints}.
 */
public record ConversationSummary(
        String sessionId,
        int turnNumber,
        String userGoal,
        String confirmedFacts,
        String pendingActions,
        String constraints
) {}
