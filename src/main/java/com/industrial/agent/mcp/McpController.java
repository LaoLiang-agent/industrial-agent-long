package com.industrial.agent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for MCP-based Agent.
 * Only active when MCP is enabled (mcp.enabled=true).
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@ConditionalOnBean(McpAgent.class)
public class McpController {

    private final McpAgent mcpAgent;

    public McpController(McpAgent mcpAgent) {
        this.mcpAgent = mcpAgent;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        long start = System.currentTimeMillis();
        String reply = mcpAgent.chat(message);
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "source", "mcp",
                "reply", reply,
                "latencyMs", elapsed
        ));
    }
}
