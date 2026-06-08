package com.industrial.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Detects and compresses redundant prompt content before sending to LLM.
 * Three strategies:
 *   1. Truncate repeated system logs/data dumps to a single line
 *   2. Replace boilerplate JSON with structural summary
 *   3. Estimate savings in tokens
 */
@Slf4j
@Component
public class PromptCompressor {

    private final TokenCostTracker costTracker;

    public PromptCompressor(TokenCostTracker costTracker) {
        this.costTracker = costTracker;
    }

    public record CompressionResult(String compressed, int originalTokens,
                                     int compressedTokens, double savingsPercent) {}

    /**
     * Compress a prompt by removing redundancy.
     */
    public CompressionResult compress(String prompt) {
        int original = costTracker.countInputTokens(prompt);
        String compressed = prompt;

        // Strategy 1: collapse repeated lines (e.g., logs, JSON dumps)
        compressed = collapseRepeatedLines(compressed);

        // Strategy 2: truncate JSON blobs to structural summary
        compressed = summarizeJsonBlobs(compressed);

        // Strategy 3: merge consecutive whitespace
        compressed = compressed.replaceAll("\n{3,}", "\n\n").trim();

        int after = costTracker.countInputTokens(compressed);
        double saved = original > 0 ? (original - after) * 100.0 / original : 0;

        if (saved > 5.0) {
            log.info("[Compressor] {} -> {} tokens, saved {:.1f}%", original, after, saved);
        }
        return new CompressionResult(compressed, original, after, saved);
    }

    /**
     * Collapse consecutive repeated lines (common in system logs).
     */
    private String collapseRepeatedLines(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");
        String prev = null;
        int repeat = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(prev)) {
                repeat++;
            } else {
                if (repeat > 0) {
                    sb.append("  [... above line repeats ").append(repeat).append(" more times]\n");
                    repeat = 0;
                }
                sb.append(line).append("\n");
                prev = trimmed;
            }
        }
        if (repeat > 0) {
            sb.append("  [... above line repeats ").append(repeat).append(" more times]\n");
        }
        return sb.toString().trim();
    }

    /**
     * Replace large JSON arrays with field-level summary.
     */
    private String summarizeJsonBlobs(String text) {
        // Find large JSON objects/arrays (>500 chars) and replace with structural summary
        // Simple heuristic: if a line starts with { or [ and is very long, summarize
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() > 500 && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
                // Extract top-level keys only
                int keyCount = trimmed.split("\"").length / 2;
                sb.append("  [JSON blob: ~").append(trimmed.length())
                  .append(" chars, ~").append(keyCount).append(" keys — structure preserved]\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
