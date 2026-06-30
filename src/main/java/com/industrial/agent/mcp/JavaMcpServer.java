package com.industrial.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java MCP Server — exposes industrial tools via MCP SSE protocol.
 * Replaces the Node.js MCP server with native Java/Spring implementation.
 *
 * Activated with: mcp.server.enabled=true
 * SSE endpoint: GET /mcp/sse
 * Messages: POST /mcp/messages?sessionId=xxx
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@ConditionalOnProperty(name = "mcp.server.enabled", havingValue = "true", matchIfMissing = false)
public class JavaMcpServer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    /**
     * SSE endpoint — establishes long-lived connection for MCP JSON-RPC.
     */
    @GetMapping("/sse")
    public SseEmitter sseConnect() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        String sessionId = UUID.randomUUID().toString();

        sessions.put(sessionId, emitter);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(e -> sessions.remove(sessionId));

        try {
            // MCP SSE spec: first event announces the message endpoint
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/messages?sessionId=" + sessionId));
        } catch (IOException e) {
            sessions.remove(sessionId);
        }

        log.info("[JavaMcpServer] SSE session started: {}", sessionId);
        return emitter;
    }

    /**
     * Message endpoint — receives JSON-RPC requests, sends responses via SSE.
     */
    @PostMapping("/messages")
    public void handleMessage(@RequestParam String sessionId,
                              @RequestBody JsonNode request,
                              HttpServletResponse resp) throws IOException {
        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            resp.sendError(400, "Unknown session: " + sessionId);
            return;
        }

        String method = request.has("method") ? request.get("method").asText() : "";
        int id = request.has("id") ? request.get("id").asInt() : 0;
        String requestJson = request.toString().length() > 120
                ? request.toString().substring(0, 120) + "..." : request.toString();
        log.info("[JavaMcpServer] ← {} ({})", method, requestJson);

        try {
            JsonNode response = handleJsonRpc(id, method, request);
            String responseJson = mapper.writeValueAsString(response);
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(responseJson, MediaType.APPLICATION_JSON));
            log.info("[JavaMcpServer] → response ({} bytes)", responseJson.length());
        } catch (Exception e) {
            log.error("[JavaMcpServer] Error handling {}: {}", method, e.getMessage());
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(errorResponse(id, e.getMessage()), MediaType.APPLICATION_JSON));
        }
    }

    private JsonNode handleJsonRpc(int id, String method, JsonNode request) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        switch (method) {
            case "initialize" -> response.set("result", buildInitResult());
            case "notifications/initialized" -> response.set("result", mapper.createObjectNode());
            case "tools/list" -> response.set("result", buildToolsList());
            case "tools/call" -> response.set("result", buildToolCall(request.get("params")));
            default -> response.set("result", mapper.createObjectNode().put("ok", true));
        }
        return response;
    }

    private ObjectNode buildInitResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2025-11-25");
        result.set("capabilities", mapper.createObjectNode()
                .set("tools", mapper.createObjectNode().put("listChanged", true)));
        result.set("serverInfo", mapper.createObjectNode()
                .put("name", "industrial-tools").put("version", "1.0.0"));
        return result;
    }

    private ObjectNode buildToolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = mapper.createArrayNode();

        addTool(tools, "queryDeviceAlarms", "查询指定设备的当前活跃告警",
                newParam("deviceId", "string", "设备ID"));
        addTool(tools, "queryDeviceHistory", "查询设备过去1小时的遥测数据统计和最新值",
                newParam("deviceId", "string", "设备ID"));
        addTool(tools, "searchKnowledgeBase", "搜索工业设备维修知识库",
                newParam("query", "string", "查询内容"));
        addTool(tools, "generateDiagnosis", "基于告警类型和异常指标生成诊断结论",
                newParam("alarmType", "string", "告警类型"),
                newParam("abnormalMetrics", "string", "异常指标"));

        result.set("tools", tools);
        return result;
    }

    private ObjectNode buildToolCall(JsonNode params) {
        String name = params.get("name").asText();
        JsonNode args = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();
        ObjectNode result = mapper.createObjectNode();

        String content = switch (name) {
            case "queryDeviceAlarms" -> String.format(
                    "{\"deviceId\":\"%s\",\"status\":\"warning\"," +
                            "\"alarms\":[{\"type\":\"振动异常\",\"severity\":\"MEDIUM\",\"value\":4.8}," +
                            "{\"type\":\"温度过高\",\"severity\":\"HIGH\",\"value\":82.5}]}",
                    args.has("deviceId") ? args.get("deviceId").asText() : "unknown");
            case "queryDeviceHistory" -> String.format(
                    "{\"deviceId\":\"%s\",\"statsLast60Min\":{\"avg_temp\":68.3,\"max_temp\":82.5}," +
                            "\"latest\":{\"temp\":72.5,\"vibration\":4.8}}",
                    args.has("deviceId") ? args.get("deviceId").asText() : "unknown");
            case "searchKnowledgeBase" ->
                    "{\"results\":[{\"title\":\"轴承温度过高处理方案\",\"score\":0.92}," +
                            "{\"title\":\"振动异常排查流程\",\"score\":0.87}]}";
            case "generateDiagnosis" ->
                    "{\"diagnosis\":\"主轴轴承磨损导致振动超标和温度升高\"," +
                            "\"confidence\":0.85,\"priority\":\"HIGH\"}";
            default -> "{}";
        };

        ArrayNode contents = mapper.createArrayNode();
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", content);
        contents.add(textContent);
        result.set("content", contents);
        return result;
    }

    private JsonNode errorResponse(int id, String error) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        ObjectNode err = mapper.createObjectNode();
        err.put("code", -32603);
        err.put("message", error);
        response.set("error", err);
        return response;
    }

    private void addTool(ArrayNode tools, String name, String description, ObjectNode... params) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();
        for (ObjectNode p : params) {
            properties.set(p.get("name").asText(), p);
            required.add(p.get("name").asText());
        }
        inputSchema.set("properties", properties);
        inputSchema.set("required", required);
        tool.set("inputSchema", inputSchema);
        tools.add(tool);
    }

    private ObjectNode newParam(String name, String type, String description) {
        ObjectNode p = mapper.createObjectNode();
        p.put("name", name);
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
