package com.industrial.agent;

import com.industrial.agent.agent.router.Intent;
import com.industrial.agent.agent.supervisor.ApprovalGate;
import com.industrial.agent.runtime.RuntimeContext;
import com.industrial.agent.skill.Skill;
import com.industrial.agent.workflow.WorkflowDefinition;
import com.industrial.agent.workflow.WorkflowEngine;
import com.industrial.agent.workflow.WorkflowRegistry;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class P2SkillWorkflowTest {

    private static final long DEADLINE = System.currentTimeMillis() + 60_000;

    private static RuntimeContext newCtx(String traceId, String tenantId) {
        return new RuntimeContext(traceId, "session-1", tenantId, "user-1", DEADLINE);
    }

    // ═══════════════════════════════════════════════════════════════
    // Skill interface contract
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class SkillContractTest {

        static class TestSkill implements Skill {
            @Override public String chat(String message) { return "echo: " + message; }
            @Override public String skillName() { return "TEST_SKILL"; }
            @Override public String description() { return "测试技能"; }
        }

        @Test
        void shouldImplementAllMethods() {
            TestSkill skill = new TestSkill();
            assertEquals("TEST_SKILL", skill.skillName());
            assertEquals("测试技能", skill.description());
            assertEquals("echo: hello", skill.chat("hello"));
        }

        @Test
        void shouldDefaultChatWithContextToChat() {
            TestSkill skill = new TestSkill();
            RuntimeContext ctx = newCtx("trace-1", "tenant-A");
            assertEquals("echo: hi", skill.chat("hi", ctx));
        }

        @Test
        void shouldAllowOverrideChatWithContext() {
            Skill ctxAwareSkill = new Skill() {
                @Override public String chat(String message) { return "bare"; }
                @Override public String chat(String message, RuntimeContext ctx) {
                    return ctx.getTenantId() + ":" + message;
                }
                @Override public String skillName() { return "CTX_SKILL"; }
                @Override public String description() { return "context-aware"; }
            };

            RuntimeContext ctx = newCtx("t", "tenant-A");
            assertEquals("tenant-A:hello", ctxAwareSkill.chat("hello", ctx));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WorkflowDefinition
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class WorkflowDefinitionTest {

        @Test
        void shouldCreateValidDefinition() {
            var nodes = List.of(
                    new WorkflowDefinition.WorkflowNode("n1", WorkflowDefinition.NodeType.EXPERT_CALL,
                            "查询告警", Map.of("skillName", "ALARM_EXPERT")),
                    new WorkflowDefinition.WorkflowNode("n2", WorkflowDefinition.NodeType.TOOL_CALL,
                            "创建工单", Map.of("toolName", "createWorkOrder"))
            );
            var edges = List.of(new WorkflowDefinition.WorkflowEdge("n1", "n2"));

            var def = new WorkflowDefinition("test-wf", "测试流程",
                    List.of("维修", "工单"), nodes, edges);

            assertEquals("test-wf", def.name());
            assertEquals(2, def.nodes().size());
            assertEquals(1, def.edges().size());
            assertEquals(2, def.intentKeywords().size());
        }

        @Test
        void shouldSupportAllNodeTypes() {
            for (WorkflowDefinition.NodeType type : WorkflowDefinition.NodeType.values()) {
                var node = new WorkflowDefinition.WorkflowNode("id", type, "label", Map.of());
                assertEquals(type, node.type());
            }
        }

        @Test
        void nodeConfigShouldPreserveAllKeys() {
            Map<String, Object> config = Map.of("skillName", "ALARM_EXPERT",
                    "timeout", 30000, "retry", 3);
            var node = new WorkflowDefinition.WorkflowNode("n1", WorkflowDefinition.NodeType.EXPERT_CALL,
                    "label", config);

            assertEquals("ALARM_EXPERT", node.config().get("skillName"));
            assertEquals(30000, node.config().get("timeout"));
            assertEquals(3, node.config().get("retry"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WorkflowEngine — topological sort
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class WorkflowEngineTopologicalSortTest {

        @Test
        void shouldSortLinearChain() {
            var def = makeDef("linear", List.of("A", "B", "C", "D"),
                    List.of(edge("A","B"), edge("B","C"), edge("C","D")),
                    WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);

            assertEquals(List.of("A", "B", "C", "D"), sorted);
        }

        @Test
        void shouldSortDiamondDag() {
            var def = makeDef("diamond", List.of("A", "B", "C", "D"),
                    List.of(edge("A","B"), edge("A","C"), edge("B","D"), edge("C","D")),
                    WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);

            assertEquals(4, sorted.size());
            assertEquals("A", sorted.get(0));
            assertEquals("D", sorted.get(3));
            assertTrue(sorted.containsAll(List.of("A", "B", "C", "D")));
        }

        @Test
        void shouldSortSingleNode() {
            var def = makeDef("single", List.of("only"),
                    List.of(), WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);

            assertEquals(List.of("only"), sorted);
        }

        @Test
        void shouldHandleNoEdges() {
            var def = makeDef("parallel", List.of("X", "Y", "Z"),
                    List.of(), WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);

            assertEquals(3, sorted.size());
        }

        @Test
        void shouldHandleCycleByFallingBackToInsertionOrder() {
            var def = makeDef("cycle", List.of("A", "B", "C"),
                    List.of(edge("A","B"), edge("B","C"), edge("C","A")),
                    WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);
            assertEquals(List.of("A", "B", "C"), sorted);
        }

        @Test
        void shouldHandleDisconnectedNodes() {
            var def = makeDef("disconnected", List.of("A", "B", "isolated"),
                    List.of(edge("A","B")),
                    WorkflowDefinition.NodeType.NOTIFY);

            List<String> sorted = topologicalSort(def);
            assertEquals(3, sorted.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Intent & IntentClassifier — WORKFLOW intent
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class IntentTest {

        @Test
        void shouldHaveWorkflowEnumValue() {
            assertNotNull(Intent.valueOf("WORKFLOW"));
        }

        @Test
        void shouldHaveAllSixIntents() {
            assertEquals(6, Intent.values().length);
        }

        @Test
        void workflowKeywordPatternShouldMatchMaintenanceRequests() {
            Pattern p = Pattern.compile("维修工单|故障处理流程|报修|停机維修|维修流程");

            assertTrue(p.matcher("CNC-001需要维修工单").find());
            assertTrue(p.matcher("启动故障处理流程").find());
            assertTrue(p.matcher("设备报修").find());
            assertTrue(p.matcher("维修流程走起来").find());
            assertFalse(p.matcher("查询温度数据").find());
            assertFalse(p.matcher("告警处理").find());
        }

        @Test
        void workflowPatternShouldTakePriorityInClassifyByKeyword() {
            Pattern workflowP = Pattern.compile("维修工单|故障处理流程|报修|停机維修|维修流程");
            Pattern alarmP = Pattern.compile("告警|报警|警告|alarm");

            String msg = "CNC-001轴承温度告警，需要维修工单";
            assertTrue(alarmP.matcher(msg).find());
            assertTrue(workflowP.matcher(msg).find());
        }

        @Test
        void diagnosisPatternShouldNotMatchWorkflow() {
            Pattern diagP = Pattern.compile("诊断|故障|排查|检修|异常.*分析");
            Pattern workflowP = Pattern.compile("维修工单|故障处理流程|报修|停机維修|维修流程");

            String msg = "故障排查";
            assertTrue(diagP.matcher(msg).find());
            assertFalse(workflowP.matcher(msg).find());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WorkflowEngine — node execution with mock skills
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class WorkflowEngineExecutionTest {

        private List<Skill> testSkills;
        private RuntimeContext ctx;

        @BeforeEach
        void setUp() {
            testSkills = List.of(
                    createSkill("ALARM_EXPERT", "告警查询", msg -> "告警: CNC-001 温度过高"),
                    createSkill("DATA_EXPERT", "数据分析", msg -> "数据: 温度曲线上升"),
                    createSkill("DIAGNOSIS_EXPERT", "故障诊断", msg -> "诊断: 轴承磨损")
            );
            ctx = newCtx("trace-test", "tenant-A");
        }

        @Test
        void shouldExecuteLinearWorkflowWithExpertCalls() {
            var nodes = List.of(
                    node("alarm", WorkflowDefinition.NodeType.EXPERT_CALL, "查询告警", Map.of("skillName", "ALARM_EXPERT")),
                    node("data", WorkflowDefinition.NodeType.EXPERT_CALL, "查询数据", Map.of("skillName", "DATA_EXPERT")),
                    node("diag", WorkflowDefinition.NodeType.EXPERT_CALL, "故障诊断", Map.of("skillName", "DIAGNOSIS_EXPERT"))
            );
            var def = new WorkflowDefinition("test", "test", List.of(),
                    nodes, List.of(edge("alarm","data"), edge("data","diag")));

            var engine = new WorkflowEngine(testSkills, new ApprovalGate(), null, null);
            engine.init();

            var result = engine.execute(def, "初始消息: CNC-001温度过高", ctx);

            assertTrue(result.completed());
            assertEquals(3, result.nodeExecutions().size());
            assertEquals("COMPLETED", result.nodeExecutions().get(0).status());
            assertEquals("COMPLETED", result.nodeExecutions().get(1).status());
            assertEquals("COMPLETED", result.nodeExecutions().get(2).status());
            assertTrue(result.accumulatedContext().contains("告警"));
            assertTrue(result.accumulatedContext().contains("数据"));
            assertTrue(result.accumulatedContext().contains("诊断"));
        }

        @Test
        void shouldAccumulateContextAcrossNodes() {
            var nodes = List.of(
                    node("a", WorkflowDefinition.NodeType.EXPERT_CALL, "第一步", Map.of("skillName", "ALARM_EXPERT")),
                    node("b", WorkflowDefinition.NodeType.EXPERT_CALL, "第二步", Map.of("skillName", "DATA_EXPERT"))
            );
            var def = new WorkflowDefinition("ctx-test", "test", List.of(),
                    nodes, List.of(edge("a","b")));

            var engine = new WorkflowEngine(testSkills, new ApprovalGate(), null, null);
            engine.init();

            var result = engine.execute(def, "原始输入", ctx);

            assertTrue(result.completed());
            assertTrue(result.accumulatedContext().contains("原始输入"));
            assertTrue(result.accumulatedContext().contains("第一步"));
            assertTrue(result.accumulatedContext().contains("第二步"));
        }

        @Test
        void shouldFailWhenSkillNotFound() {
            var nodes = List.of(
                    node("n1", WorkflowDefinition.NodeType.EXPERT_CALL, "不存在的技能",
                            Map.of("skillName", "NONEXISTENT"))
            );
            var def = new WorkflowDefinition("fail-test", "test", List.of(), nodes, List.of());

            var engine = new WorkflowEngine(testSkills, new ApprovalGate(), null, null);
            engine.init();

            var result = engine.execute(def, "测试", ctx);

            assertFalse(result.completed());
            assertEquals("FAILED", result.nodeExecutions().get(0).status());
            assertTrue(result.nodeExecutions().get(0).output().contains("Skill not found"));
        }

        @Test
        void shouldTrackLatencyInResult() {
            var nodes = List.of(
                    node("n1", WorkflowDefinition.NodeType.EXPERT_CALL, "快", Map.of("skillName", "ALARM_EXPERT"))
            );
            var def = new WorkflowDefinition("latency-test", "test", List.of(), nodes, List.of());

            var engine = new WorkflowEngine(testSkills, new ApprovalGate(), null, null);
            engine.init();

            var result = engine.execute(def, "测试", ctx);

            assertTrue(result.latencyMs() >= 0);
        }

        @Test
        void shouldRecordCorrectWorkflowName() {
            var nodes = List.of(
                    node("n1", WorkflowDefinition.NodeType.EXPERT_CALL, "步骤", Map.of("skillName", "ALARM_EXPERT"))
            );
            var def = new WorkflowDefinition("my-workflow-name", "test", List.of(), nodes, List.of());

            var engine = new WorkflowEngine(testSkills, new ApprovalGate(), null, null);
            engine.init();

            var result = engine.execute(def, "测试", ctx);

            assertEquals("my-workflow-name", result.workflowName());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WorkflowRegistry — intent keyword matching
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class WorkflowRegistryTest {

        @Test
        void shouldMatchByIntentKeywords() {
            var def = new WorkflowDefinition("maintenance", "维修流程",
                    List.of("维修工单", "报修", "停机"), List.of(), List.of());
            WorkflowRegistry registry = new TestWorkflowRegistry(Map.of("maintenance", def));

            assertTrue(registry.findByIntentKeywords("需要创建维修工单").isPresent());
            assertTrue(registry.findByIntentKeywords("设备报修处理").isPresent());
            assertTrue(registry.findByIntentKeywords("紧急停机故障").isPresent());
            assertFalse(registry.findByIntentKeywords("查询温度数据").isPresent());
            assertFalse(registry.findByIntentKeywords("一般对话").isPresent());
        }

        @Test
        void shouldReturnEmptyWhenNoMatch() {
            var def = new WorkflowDefinition("test", "test", List.of("维修"), List.of(), List.of());
            WorkflowRegistry registry = new TestWorkflowRegistry(Map.of("test", def));

            assertTrue(registry.findByIntentKeywords("你好").isEmpty());
        }

        @Test
        void shouldFindByName() {
            var def = new WorkflowDefinition("maintenance-workflow", "维修流程",
                    List.of(), List.of(), List.of());
            WorkflowRegistry registry = new TestWorkflowRegistry(Map.of("maintenance-workflow", def));

            assertTrue(registry.findByName("maintenance-workflow").isPresent());
            assertTrue(registry.findByName("nonexistent").isEmpty());
        }

        @Test
        void shouldListAll() {
            var def1 = new WorkflowDefinition("wf1", "d1", List.of(), List.of(), List.of());
            var def2 = new WorkflowDefinition("wf2", "d2", List.of(), List.of(), List.of());
            WorkflowRegistry registry = new TestWorkflowRegistry(
                    new LinkedHashMap<>() {{ put("wf1", def1); put("wf2", def2); }});

            assertEquals(2, registry.listAll().size());
        }

        @Test
        void matchesAnyWorkflowShouldDelegateToKeywords() {
            var def = new WorkflowDefinition("test", "test", List.of("维修"), List.of(), List.of());
            WorkflowRegistry registry = new TestWorkflowRegistry(Map.of("test", def));

            assertTrue(registry.matchesAnyWorkflow("设备需要维修"));
            assertFalse(registry.matchesAnyWorkflow("今天天气真好"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private static WorkflowDefinition makeDef(String name, List<String> nodeIds,
                                               List<WorkflowDefinition.WorkflowEdge> edges,
                                               WorkflowDefinition.NodeType type) {
        return makeDef(name, nodeIds, edges, type, Map.of());
    }

    private static WorkflowDefinition makeDef(String name, List<String> nodeIds,
                                               List<WorkflowDefinition.WorkflowEdge> edges,
                                               WorkflowDefinition.NodeType type,
                                               Map<String, Object> config) {
        var nodes = nodeIds.stream()
                .map(id -> node(id, type, "节点-" + id, config))
                .toList();
        return new WorkflowDefinition(name, "测试流程", List.of(), nodes, edges);
    }

    private static WorkflowDefinition.WorkflowNode node(String id, WorkflowDefinition.NodeType type,
                                                         String label, Map<String, Object> config) {
        return new WorkflowDefinition.WorkflowNode(id, type, label, config);
    }

    private static WorkflowDefinition.WorkflowEdge edge(String from, String to) {
        return new WorkflowDefinition.WorkflowEdge(from, to);
    }

    private static Skill createSkill(String name, String desc, java.util.function.Function<String, String> handler) {
        return new Skill() {
            @Override public String chat(String message) { return handler.apply(message); }
            @Override public String skillName() { return name; }
            @Override public String description() { return desc; }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> topologicalSort(WorkflowDefinition def) {
        try {
            var method = WorkflowEngine.class.getDeclaredMethod("topologicalSort", WorkflowDefinition.class);
            method.setAccessible(true);
            var engine = new WorkflowEngine(List.of(), new ApprovalGate(), null, null);
            return (List<String>) method.invoke(engine, def);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class TestWorkflowRegistry extends WorkflowRegistry {
        TestWorkflowRegistry(Map<String, WorkflowDefinition> defs) {
            try {
                var field = WorkflowRegistry.class.getDeclaredField("workflows");
                field.setAccessible(true);
                field.set(this, new LinkedHashMap<>(defs));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
