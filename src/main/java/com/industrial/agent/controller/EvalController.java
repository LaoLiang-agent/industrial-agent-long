package com.industrial.agent.controller;

import com.industrial.agent.eval.AgentEvaluator;
import com.industrial.agent.eval.EvalDataset;
import com.industrial.agent.eval.EvalReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final AgentEvaluator evaluator;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runEvaluation() {
        EvalReport report = evaluator.evaluate(EvalDataset.load());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCases", report.totalCases());
        response.put("intentAccuracy", String.format("%.1f%%", report.intentAccuracy() * 100));
        response.put("avgKeywordHitRate", String.format("%.1f%%", report.avgKeywordHitRate() * 100));
        response.put("avgRelevanceScore", String.format("%.1f/5", report.avgRelevanceScore()));
        response.put("avgLatencyMs", report.avgLatencyMs());
        response.put("results", report.results());
        return ResponseEntity.ok(response);
    }
}
