package com.industrial.agent.controller;

import com.industrial.agent.rag.ChunkExperiment;
import com.industrial.agent.rag.DocumentIngestionService;
import com.industrial.agent.rag.IndustrialKnowledgeBase;
import com.industrial.agent.rag.KnowledgeBaseTool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalEntries", knowledgeBase.getAllEntries().size(),
                "chunkSizes", List.of(200, 500, 1000, 2000)
        ));
    }
}
