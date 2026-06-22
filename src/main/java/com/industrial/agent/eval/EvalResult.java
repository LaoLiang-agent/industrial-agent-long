package com.industrial.agent.eval;

import com.industrial.agent.agent.router.Intent;

public record EvalResult(
        String caseId,
        String query,
        Intent expectedIntent,
        Intent actualIntent,
        boolean intentCorrect,
        double keywordHitRate,
        int relevanceScore,
        long latencyMs,
        String replySnippet
) {}
