package com.industrial.agent.controller;

import com.industrial.agent.guardrail.GuardrailChain;
import com.industrial.agent.guardrail.GuardResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/guardrail")
@RequiredArgsConstructor
public class GuardrailController {

    private final GuardrailChain guardrailChain;

    @PostMapping("/check-input")
    public ResponseEntity<Map<String, Object>> checkInput(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "");
        GuardResult result = guardrailChain.checkInput(input);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("passed", result.isPassed());
        response.put("reason", result.reason());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-action")
    public ResponseEntity<Map<String, Object>> checkAction(@RequestBody Map<String, String> request) {
        String action = request.getOrDefault("action", "");
        GuardResult result = guardrailChain.checkAction(action);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("passed", result.isPassed());
        response.put("riskLevel", result.riskLevel() != null ? result.riskLevel().name() : null);
        response.put("reason", result.reason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "circuitBreaker", guardrailChain.getCircuitBreakerState().name(),
                "auditLogSize", guardrailChain.getAuditLog().size()
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<?> auditLog() {
        return ResponseEntity.ok(guardrailChain.getAuditLog());
    }
}
