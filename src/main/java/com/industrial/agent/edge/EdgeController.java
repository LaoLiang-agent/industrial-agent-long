package com.industrial.agent.edge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/edge")
public class EdgeController {

    private final ModelRouter modelRouter;

    public EdgeController(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        ModelRouter.RouterResult result = modelRouter.chat(message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", result.source());
        response.put("fallback", result.fallback());
        response.put("latencyMs", result.latencyMs());
        response.put("reply", result.reply());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<ModelRouter.RouteStats> stats() {
        return ResponseEntity.ok(modelRouter.getStats());
    }
}
