package com.industrial.agent.eval;

import java.util.List;

public record EvalReport(
        int totalCases,
        double intentAccuracy,
        double avgKeywordHitRate,
        double avgRelevanceScore,
        long avgLatencyMs,
        List<EvalResult> results
) {
    public static EvalReport from(List<EvalResult> results) {
        int total = results.size();
        long correctIntents = results.stream().filter(EvalResult::intentCorrect).count();
        double intentAcc = (double) correctIntents / total;
        double avgKeyword = results.stream().mapToDouble(EvalResult::keywordHitRate).average().orElse(0);
        double avgRelevance = results.stream().mapToDouble(EvalResult::relevanceScore).average().orElse(0);
        long avgLatency = (long) results.stream().mapToLong(EvalResult::latencyMs).average().orElse(0);
        return new EvalReport(total, intentAcc, avgKeyword, avgRelevance, avgLatency, results);
    }
}
