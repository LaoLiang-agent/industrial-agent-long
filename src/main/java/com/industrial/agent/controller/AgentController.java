package com.industrial.agent.controller;

import com.industrial.agent.agent.DeviceAgent;
import com.industrial.agent.agent.MemoryComparisonService;
import com.industrial.agent.agent.model.DiagnosticResponse;
import com.industrial.agent.agent.model.WorkOrder;
import com.industrial.agent.agent.router.RouterAgent;
import com.industrial.agent.agent.supervisor.ApprovalGate;
import com.industrial.agent.agent.supervisor.SupervisorAgent;
import com.industrial.agent.agent.tools.WorkOrderTool;
import com.industrial.agent.workflow.WorkflowEngine;
import com.industrial.agent.workflow.WorkflowRegistry;
import com.industrial.agent.llm.TemperatureExperiment;
import com.industrial.agent.llm.TokenCostTracker;
import com.industrial.agent.memory.MemoryManager;
import com.industrial.agent.memory.ProfileEntry;
import com.industrial.agent.runtime.AgentRuntime;
import com.industrial.agent.runtime.RuntimeContext;
import dev.langchain4j.service.TokenStream;
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
public class AgentController {

    private final DeviceAgent deviceAgent;
    private final MemoryComparisonService memoryComparison;
    private final TokenCostTracker costTracker;
    private final TemperatureExperiment tempExperiment;
    private final WorkOrderTool workOrderTool;
    private final RouterAgent routerAgent;
    private final SupervisorAgent supervisorAgent;
    private final ApprovalGate approvalGate;
    private final AgentRuntime runtime;
    private final MemoryManager memory;
    private final WorkflowEngine workflowEngine;
    private final WorkflowRegistry workflowRegistry;

    public AgentController(DeviceAgent deviceAgent, MemoryComparisonService memoryComparison,
                           TokenCostTracker costTracker, TemperatureExperiment tempExperiment,
                           WorkOrderTool workOrderTool, RouterAgent routerAgent,
                           SupervisorAgent supervisorAgent, ApprovalGate approvalGate,
                           AgentRuntime runtime, MemoryManager memory,
                           WorkflowEngine workflowEngine, WorkflowRegistry workflowRegistry) {
        this.deviceAgent = deviceAgent;
        this.memoryComparison = memoryComparison;
        this.costTracker = costTracker;
        this.tempExperiment = tempExperiment;
        this.workOrderTool = workOrderTool;
        this.routerAgent = routerAgent;
        this.supervisorAgent = supervisorAgent;
        this.approvalGate = approvalGate;
        this.runtime = runtime;
        this.memory = memory;
        this.workflowEngine = workflowEngine;
        this.workflowRegistry = workflowRegistry;
    }
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        RuntimeContext ctx = runtime.createContext(
                request.getOrDefault("sessionId", "default"),
                request.getOrDefault("tenantId", "default"),
                request.getOrDefault("userId", "anonymous"));
        String reply = deviceAgent.chat(ctx, message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", reply);
        response.put("traceId", ctx.getTraceId());
        response.put("elapsedMs", ctx.elapsedMs());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        RuntimeContext ctx = runtime.createContext(
                request.getOrDefault("sessionId", "default"),
                request.getOrDefault("tenantId", "default"),
                request.getOrDefault("userId", "anonymous"));
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

        TokenStream tokenStream = deviceAgent.chatStream(ctx, message);
        tokenStream.onPartialResponse(token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
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
        RuntimeContext ctx = runtime.createContext(
                request.getOrDefault("sessionId", "default"),
                request.getOrDefault("tenantId", "default"),
                request.getOrDefault("userId", "anonymous"));
        DiagnosticResponse result = deviceAgent.diagnose(ctx, deviceId);
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

    @GetMapping("/workorder/{id}")
    public ResponseEntity<Map<String, Object>> getWorkOrder(@PathVariable String id) {
        WorkOrder wo = workOrderTool.findById(id);
        if (wo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "workOrderId", wo.getWorkOrderId(),
                "deviceId", wo.getDeviceId(),
                "type", wo.getType(),
                "priority", wo.getPriority(),
                "description", wo.getDescription(),
                "assignee", wo.getAssignee(),
                "status", wo.getStatus(),
                "createdTime", wo.getCreatedTime().toString(),
                "suggestedActions", wo.getSuggestedActions() != null ? wo.getSuggestedActions() : List.of()
        ));
    }

    @PostMapping("/diagnose-and-order")
    public ResponseEntity<Map<String, Object>> diagnoseAndOrder(@RequestBody Map<String, String> request) {
        String deviceId = request.getOrDefault("deviceId", "");
        if (deviceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "deviceId is required"));
        }
        RuntimeContext ctx = runtime.createContext(
                request.getOrDefault("sessionId", "default"),
                request.getOrDefault("tenantId", "default"),
                request.getOrDefault("userId", "anonymous"));
        DiagnosticResponse diagnosis = deviceAgent.diagnose(ctx, deviceId);
        String chatResult = deviceAgent.chat(ctx,
                String.format("设备 %s 诊断结果如下：%s。如果需要维修，请创建工单。",
                        deviceId, diagnosis.getAnalysis()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deviceId", diagnosis.getDeviceId());
        response.put("status", diagnosis.getStatus());
        response.put("analysis", diagnosis.getAnalysis());
        response.put("possibleCauses", diagnosis.getPossibleCauses());
        response.put("suggestedActions", diagnosis.getSuggestedActions());
        response.put("priority", diagnosis.getPriority());
        response.put("requiresImmediateAction", diagnosis.getRequiresImmediateAction());
        response.put("confidence", diagnosis.getConfidence());
        response.put("chatReply", chatResult);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workorder/device/{deviceId}")
    public ResponseEntity<List<WorkOrder>> listWorkOrdersByDevice(@PathVariable String deviceId) {
        return ResponseEntity.ok(workOrderTool.findByDevice(deviceId));
    }

    @GetMapping("/workorder/stats")
    public ResponseEntity<Map<String, Object>> workOrderStats() {
        return ResponseEntity.ok(workOrderTool.stats());
    }

    @PostMapping("/route/chat")
    public ResponseEntity<Map<String, Object>> routeChat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        RouterAgent.RouteResult result = routerAgent.route(message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("intent", result.intent().name());
        response.put("latencyMs", result.latencyMs());
        response.put("reply", result.reply());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/route/stats")
    public ResponseEntity<Map<String, Long>> routeStats() {
        return ResponseEntity.ok(routerAgent.getStats());
    }

    @PostMapping("/supervisor/chat")
    public ResponseEntity<Map<String, Object>> supervisorChat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        SupervisorAgent.SupervisorResult result = supervisorAgent.execute(message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tasks", result.tasks());
        response.put("pendingApprovals", result.pendingApprovals());
        response.put("summary", result.summary());
        response.put("latencyMs", result.latencyMs());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/supervisor/approve/{id}")
    public ResponseEntity<Map<String, String>> approveTask(@PathVariable String id) {
        ApprovalGate.ApprovalStatus status = approvalGate.approve(id);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("approvalId", id, "status", status.name()));
    }

    @PostMapping("/supervisor/reject/{id}")
    public ResponseEntity<Map<String, String>> rejectTask(@PathVariable String id) {
        ApprovalGate.ApprovalStatus status = approvalGate.reject(id);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("approvalId", id, "status", status.name()));
    }

    @GetMapping("/memory/{sessionId}")
    public ResponseEntity<Map<String, Object>> inspectMemory(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "anonymous") String userId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("recentTurns", memory.recentTurns(sessionId));
        response.put("latestSummary", memory.summaryMemory().latest(sessionId).orElse(null));
        response.put("userProfile", memory.profileMemory().forSubject("user", userId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/memory/profile")
    public ResponseEntity<Map<String, Object>> writeProfile(@RequestBody Map<String, Object> request) {
        ProfileEntry entry = new ProfileEntry(
                String.valueOf(request.getOrDefault("subjectType", "user")),
                String.valueOf(request.getOrDefault("subjectId", "anonymous")),
                String.valueOf(request.getOrDefault("attribute", "")),
                String.valueOf(request.getOrDefault("value", "")),
                request.get("confidence") instanceof Number n ? n.doubleValue() : 0.0,
                String.valueOf(request.getOrDefault("sourceEvidence", "")));
        boolean written = memory.writeProfile(entry);
        return ResponseEntity.ok(Map.of(
                "written", written,
                "reason", written ? "passed confidence gate" : "rejected: confidence below threshold"));
    }

    @GetMapping("/workflows")
    public ResponseEntity<Map<String, Object>> listWorkflows() {
        var workflows = workflowRegistry.listAll().stream()
                .map(w -> Map.of(
                        "name", w.name(),
                        "description", w.description(),
                        "nodes", w.nodes().size()))
                .toList();
        return ResponseEntity.ok(Map.of("workflows", workflows));
    }

    @PostMapping("/workflow/execute")
    public ResponseEntity<Map<String, Object>> executeWorkflow(@RequestBody Map<String, String> request) {
        String name = request.getOrDefault("workflow", "");
        String message = request.getOrDefault("message", "");
        if (name.isBlank() || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workflow and message are required"));
        }
        var wf = workflowRegistry.findByName(name);
        if (wf.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workflow not found: " + name));
        }
        var result = workflowEngine.execute(wf.get(), message, null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowName", result.workflowName());
        response.put("completed", result.completed());
        response.put("nodeExecutions", result.nodeExecutions());
        response.put("pendingApprovals", result.pendingApprovals());
        response.put("latencyMs", result.latencyMs());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "agent", "industrial-agent-long"));
    }
}
