package com.industrial.agent.eval;

import com.industrial.agent.agent.router.Intent;
import java.util.List;

public record EvalCase(
        String id,
        String query,
        Intent expectedIntent,
        List<String> expectedKeywords
) {}
