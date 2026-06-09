package com.industrial.agent.controller;

import com.industrial.agent.agent.DeviceAgent;
import com.industrial.agent.agent.MemoryComparisonService;
import com.industrial.agent.agent.model.DiagnosticResponse;
import com.industrial.agent.llm.TemperatureExperiment;
import com.industrial.agent.llm.TokenCostTracker;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final DeviceAgent deviceAgent;
    private final MemoryComparisonService memoryComparison;
    private final TokenCostTracker costTracker;
    private final TemperatureExperiment tempExperiment;
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        String reply = deviceAgent.chat(message);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        if (message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("message is required"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        TokenStream tokenStream = deviceAgent.chatStream(message);
        tokenStream.onNext(token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    log.info("[SSE] Stream completed, total tokens used: {}", response.tokenUsage());
                    emitter.complete();
                })
                .onError(error -> {
                    log.error("[SSE] Stream error: {}", error.getMessage());
                    emitter.completeWithError(error);
                })
                .start();

        return emitter;
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clear() {
        return ResponseEntity.ok(Map.of("status", "ok", "message", "memory will reset on next restart"));
    }

    @PostMapping("/diagnose")
    public ResponseEntity<DiagnosticResponse> diagnose(@RequestBody Map<String, String> request) {
        String deviceId = request.getOrDefault("deviceId", "");
        if (deviceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        DiagnosticResponse result = deviceAgent.diagnose(deviceId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/memory/compare")
    public ResponseEntity<Map<String, List<String>>> compareMemory(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> conversation = (List<String>) request.getOrDefault("conversation", List.of(
                "你好，我叫张三，是CNC-001的运维工程师。",
                "CNC-001现在有什么告警吗？",
                "我之前说我是谁？我叫什么名字？我负责哪台设备？"
        ));
        Map<String, List<String>> results = memoryComparison.compare(conversation);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalRequests", costTracker.getTotalRequests(),
                "totalInputTokens", costTracker.getTotalInputTokens(),
                "totalOutputTokens", costTracker.getTotalOutputTokens(),
                "estimatedCost", String.format("$%.4f", costTracker.getTotalCost())
        ));
    }

    @PostMapping("/experiment/temperature")
    public ResponseEntity<Map<String, Object>> runTemperatureExperiment() {
        Map<Double, TemperatureExperiment.TempResult> results = tempExperiment.run();
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            var r = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("consistency", String.format("%.2f", r.consistencyScore()));
            item.put("totalTokens", r.totalTokens());
            item.put("sampleResponse", r.responses().get(0).substring(0,
                    Math.min(200, r.responses().get(0).length())));
            summary.put(String.valueOf(r.temperature()), item);
        }
        response.put("experiment", "temperature vs consistency");
        response.put("prompt", "CNC-001 vibration 4.8mm/s diagnosis");
        response.put("runsPerTemperature", 10);
        response.put("results", summary);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "agent", "industrial-agent-long"));
    }
}
