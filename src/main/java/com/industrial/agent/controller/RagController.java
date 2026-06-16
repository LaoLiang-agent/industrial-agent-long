package com.industrial.agent.controller;

import com.industrial.agent.rag.*;
import com.industrial.agent.rag.advanced.QueryRewriter;
import com.industrial.agent.rag.advanced.RagEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentIngestionService ingestionService;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final ChunkExperiment chunkExperiment;
    private final IndustrialKnowledgeBase knowledgeBase;
    private final QueryRewriter queryRewriter;
    private final RagEvaluator ragEvaluator;

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest() {
        int count = ingestionService.ingest(knowledgeBase.getAllEntries());
        return ResponseEntity.ok(Map.of("status", "ok", "entriesIngested", count, "chunkSize", 500));
    }

    @PostMapping("/ingest/{chunkSize}")
    public ResponseEntity<Map<String, Object>> ingestWithChunk(@PathVariable int chunkSize) {
        int count = ingestionService.ingest(knowledgeBase.getAllEntries(), chunkSize);
        return ResponseEntity.ok(Map.of("status", "ok", "entriesIngested", count, "chunkSize", chunkSize));
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", "");
        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }
        String result = knowledgeBaseTool.searchKnowledgeBase(query);
        return ResponseEntity.ok(Map.of("query", query, "result", result));
    }

    @PostMapping("/experiment/chunk")
    public ResponseEntity<Map<String, Object>> runChunkExperiment() {
        List<String> testQueries = List.of(
                "轴承温度过高是什么原因？",
                "电机振动超标怎么排查？",
                "传感器信号漂移怎么处理？"
        );
        List<String> expectedKeywords = List.of("润滑", "振动", "漂移");

        var results = chunkExperiment.run(knowledgeBase.getAllEntries(), testQueries, expectedKeywords);
        return ResponseEntity.ok(Map.of(
                "experiment", "chunk_size_hit_rate",
                "chunkSizes", List.of(200, 500, 1000, 2000),
                "testQueries", testQueries,
                "results", results
        ));
    }

    @PostMapping("/rewrite/hyde")
    public ResponseEntity<Map<String, String>> hydeRewrite(@RequestBody Map<String, String> req) {
        String query = req.getOrDefault("query", "轴承温度过高是什么原因");
        return ResponseEntity.ok(Map.of("original", query, "hyde", queryRewriter.hydeRewrite(query)));
    }

    @PostMapping("/rewrite/multi-query")
    public ResponseEntity<Map<String, Object>> multiQuery(@RequestBody Map<String, String> req) {
        String query = req.getOrDefault("query", "CNC-001 振动异常怎么排查");
        return ResponseEntity.ok(Map.of("original", query, "queries", queryRewriter.multiQueryRewrite(query)));
    }

    @PostMapping("/rewrite/step-back")
    public ResponseEntity<Map<String, String>> stepBack(@RequestBody Map<String, String> req) {
        String query = req.getOrDefault("query", "CNC-001 轴承温度72度太高了什么原因");
        return ResponseEntity.ok(Map.of("original", query, "stepBack", queryRewriter.stepBackRewrite(query)));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        Map<String, String> testQueries = new LinkedHashMap<>();
        testQueries.put("轴承温度过高是什么原因？", "润滑");
        testQueries.put("电机振动超标怎么排查？", "振动");
        testQueries.put("传感器信号漂移怎么处理？", "漂移");
        testQueries.put("CNC主轴异响是什么问题？", "主轴");
        testQueries.put("气缸动作缓慢怎么修？", "气缸");
        testQueries.put("传送带跑偏怎么调整？", "传送带");
        testQueries.put("液压系统压力波动怎么解决？", "液压");
        testQueries.put("空压机高温停机怎么办？", "空压机");
        testQueries.put("水泵不出水怎么排查？", "水泵");
        testQueries.put("减速机漏油怎么处理？", "减速机");

        var results = ragEvaluator.evaluate(testQueries, knowledgeBase.getAllEntries());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalEntries", knowledgeBase.getAllEntries().size(),
                "chunkSizes", List.of(200, 500, 1000, 2000)
        ));
    }
}
