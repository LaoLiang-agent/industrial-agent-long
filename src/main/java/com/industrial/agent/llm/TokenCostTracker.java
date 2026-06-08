package com.industrial.agent.llm;

import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks token usage and estimates cost across all LLM calls.
 * DeepSeek pricing (as of 2026): input ~$0.14/1M tokens, output ~$0.28/1M tokens.
 */
@Slf4j
@Component
public class TokenCostTracker {

    // DeepSeek pricing per 1M tokens
    private static final double INPUT_PRICE_PER_1M = 0.14;
    private static final double OUTPUT_PRICE_PER_1M = 0.28;

    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    private final OpenAiTokenizer tokenizer = new OpenAiTokenizer();

    public int countInputTokens(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public int countOutputTokens(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public void recordRequest(String input, String output) {
        int in = countInputTokens(input);
        int out = countOutputTokens(output);
        totalInputTokens.addAndGet(in);
        totalOutputTokens.addAndGet(out);
        long req = totalRequests.incrementAndGet();

        double cost = (in / 1_000_000.0 * INPUT_PRICE_PER_1M)
                    + (out / 1_000_000.0 * OUTPUT_PRICE_PER_1M);

        log.info("[TokenCost] req #{}: in={}, out={}, cost=${:.6f}, total=${:.4f}",
                req, in, out, cost, getTotalCost());
    }

    public long getTotalInputTokens() { return totalInputTokens.get(); }
    public long getTotalOutputTokens() { return totalOutputTokens.get(); }
    public long getTotalRequests() { return totalRequests.get(); }

    public double getTotalCost() {
        return (totalInputTokens.get() / 1_000_000.0 * INPUT_PRICE_PER_1M)
             + (totalOutputTokens.get() / 1_000_000.0 * OUTPUT_PRICE_PER_1M);
    }
}
