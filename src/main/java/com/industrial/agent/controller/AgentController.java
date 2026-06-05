package com.industrial.agent.controller;

import com.industrial.agent.agent.DeviceAgent;
import com.industrial.agent.agent.MemoryComparisonService;
import com.industrial.agent.agent.model.DiagnosticResponse;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final DeviceAgent deviceAgent;
    private final MemoryComparisonService memoryComparison;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "agent", "industrial-agent-long"));
    }
}
