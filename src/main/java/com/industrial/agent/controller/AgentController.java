package com.industrial.agent.controller;

import com.industrial.agent.agent.DeviceAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final DeviceAgent deviceAgent;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        String reply = deviceAgent.chat(message);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clear() {
        return ResponseEntity.ok(Map.of("status", "ok", "message", "memory will reset on next restart"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "agent", "industrial-agent-long"));
    }
}
