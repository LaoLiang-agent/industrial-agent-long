package com.industrial.agent.skill;

import com.industrial.agent.runtime.RuntimeContext;

/**
 * Shared contract for all expert skills.
 * Each Skill is a specialized AI capability with its own prompt and tool set.
 */
public interface Skill {

    /** Execute the skill on the given message. */
    String chat(String message);

    /** Execute with runtime context for observability and tenant propagation. */
    default String chat(String message, RuntimeContext ctx) {
        return chat(message);
    }

    /** Unique identifier for this skill (e.g. "ALARM_EXPERT"). */
    String skillName();

    /** Human-readable description for routing and discovery. */
    String description();
}
