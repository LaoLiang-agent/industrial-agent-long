package com.industrial.agent.memory;

/**
 * L4 long-term profile fact about a user or device. Written only past the
 * confidence gate, and always carries source evidence to prevent memory pollution.
 */
public record ProfileEntry(
        String subjectType,   // "user" | "device"
        String subjectId,
        String attribute,
        String value,
        double confidence,
        String sourceEvidence
) {}
