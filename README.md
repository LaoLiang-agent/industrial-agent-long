# Industrial Agent Long（龍）

> 工业 AI Agent 实战项目 — 从 MQTT 设备数据到 Agent 智能诊断 + 维修工单的全链路闭环
>
> 版本演进：`mvp`（基础 Agent 闭环）→ `P0`（运行时+工具治理）→ `P1`（记忆+Prompt+调度）→ `P2`（RAG 增强+Skill+Workflow）

## 概览

基于 **LangChain4j + DeepSeek + Spring Boot** 的工业设备智能运维 Agent 系统，已完成 MVP 到 P2 的全部升级。

### 核心能力

| 能力 | 实现 |
|------|------|
| 设备告警查询 | DeviceAlarmTool — MQTT 实时告警 |
| 时序数据查询 | DeviceDataTool — TDEngine 聚合查询 |
| 故障诊断 | DiagnosisTool + CoT 推理链 |
| RAG 知识检索 | Milvus + BM25 + RRF 混合检索 + LLM Reranker + 租户过滤 `[P2]` |
| 维修工单 | WorkOrderTool — H2 持久化 + 生命周期 |
| 多 Agent 路由 | Router（5 个 Expert）+ Supervisor + HITL |
| 边缘推理 | Ollama 本地模型 + 云端兜底 |
| 安全护栏 | InputGuard + OutputGuard + ActionGuard + CircuitBreaker |
| MCP 协议 | McpToolProvider + Node.js MCP Server |
| 可观测性 | Prometheus + Grafana + Micrometer 指标 `[P0]` |
| 运行时状态机 | AgentRuntime + RuntimeContext（trace/tenant/deadline）`[P0]` |
| 工具治理 | ToolRegistry + ToolBudget + ToolExecutor（SHA-256 幂等 + 审计）`[P0]` |
| 四层记忆 | L1 Working / L2 Conversation / L3 Summary / L4 Profile `[P1]` |
| 六层 Prompt | PromptCompiler（角色→策略→契约→记忆→知识→任务）`[P1]` |
| 预算调度 | BudgetManager + 并行 READ + 分布式写锁 + AsyncSideCar `[P1]` |
| Skill | Skill 接口 + SkillTemplate + AlarmDiagnosisSkill `[P2]` |
| Workflow | WorkflowEngine — DAG 拓扑排序 + EXPERT/TOOL/APPROVAL/NOTIFY `[P2]` |

### API 端点速览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/chat` | 单 Agent 对话 |
| POST | `/api/agent/route/chat` | Router 多 Agent 路由 |
| POST | `/api/agent/supervisor/chat` | Supervisor 任务编排 + HITL 审批 |
| POST | `/api/mcp/chat` | MCP 协议 Agent |
| POST | `/api/edge/chat` | 边缘推理路由 |
| POST | `/api/eval/run` | 评估框架运行 |
| POST | `/api/guardrail/check-action` | 操作风险检查 |
| GET  | `/actuator/prometheus` | 指标导出 |

## 架构

```
写入流: DeviceSimulator → MQTT/EMQX → Bridge → TDEngine（WebSocket JDBC）
查询流: User → Agent(Router/Supervisor/MCP) → AgentRuntime → Tools → LLM → Response
                                                    ↓                ↓
                                              ToolExecutor    WorkOrder(H2)
                                              (幂等+审计)     + AuditLog(PG)

安全层:  InputGuard → Agent → OutputGuard → ActionGuard → CircuitBreaker
预算层:  BudgetManager（LLM/READ/WRITE 预算 + Redis 分布式锁）
旁路层:  AsyncSideCar（成本记录 / 审计持久化 / 记忆写入 / 指标上报）
监控层:  AgentMetrics + StructuredLogger → Prometheus(9090) → Grafana(3000)
记忆层:  L1 Working(Redis) → L2 Conversation(Redis Stream) → L3 Summary(PG+LLM) → L4 Profile(PG)
```

## 技术栈

| 组件 | 选型 |
|------|------|
| LLM | DeepSeek Chat + Ollama（本地） |
| Agent 框架 | LangChain4j 1.16.3 |
| 应用框架 | Spring Boot 3.3 |
| 时序数据库 | TDEngine（WebSocket JDBC） |
| 向量数据库 | Milvus Standalone |
| Embedding | AllMiniLmL6V2（ONNX 本地） |
| 消息队列 | EMQX 5.7（MQTT） |
| 缓存/流 | Redis（L1/L2 记忆 + 分布式锁） |
| 关系数据库 | H2（工单）+ PostgreSQL（L3/L4 记忆 + 审计日志） |
| 监控 | Prometheus + Grafana + Micrometer |
| 容器化 | Docker 多阶段构建 + Compose |
| 语言 | Java 21 |
| 测试 | JUnit 5 |

## 快速开始

```bash
# 1. 启动所有服务（EMQX + TDEngine + Milvus + Redis + PostgreSQL + Prometheus + Grafana）
docker compose up -d

# 2. 设置 DeepSeek API Key
export DEEPSEEK_API_KEY=sk-your-key

# 3. 启动应用
JAVA_HOME=/Users/leo/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home mvn spring-boot:run

# 4. 注入知识库
curl -X POST http://localhost:8080/api/rag/ingest

# 5. 测试 Agent
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"CNC-001 有什么告警？"}'
```

## 项目结构

```
src/main/java/com/industrial/agent/
├── AgentApplication.java
├── config/                        # AgentConfig, MemoryProperties, AgentPromptProperties, RagProperties
├── agent/
│   ├── DeviceAgent.java           # 单 Agent（@Tool + AgentRuntime 包裹）
│   ├── model/                     # WorkOrder, DiagnosticResponse
│   ├── router/                    # IntentClassifier + RouterAgent
│   ├── experts/                   # 5 Expert Agents
│   ├── supervisor/                # TaskPlanner + SupervisorAgent + ApprovalGate
│   └── tools/                     # Alarm, Data, Diagnosis, WorkOrder
├── controller/                    # Agent, Rag, Eval, Guardrail, Edge Controllers
├── runtime/                       # AgentRuntime（状态机）+ RuntimeContext `[P0]`
├── tool/                          # ToolRegistry + ToolBudget + ToolExecutor（幂等+审计）`[P0]`
├── schedule/                      # BudgetManager + AsyncSideCar `[P1]`
├── memory/                        # L1 Working + L2 Conversation + L3 Summary + L4 Profile + MemoryManager `[P1]`
├── prompt/                        # PromptCompiler（六层）+ PolicyRegistry + ToolContractGenerator `[P1]`
├── skill/                         # Skill 接口 + SkillTemplate + AlarmDiagnosisSkill `[P2]`
├── workflow/                      # WorkflowEngine（DAG + 多节点类型）`[P2]`
├── rag/
│   ├── KnowledgeBaseTool.java     # 混合检索入口
│   └── advanced/                  # Bm25Retriever, RrfFusion, LlmReranker, RagEvaluator `[P2]`
├── tsdb/                          # TdengineDataService + MqttToTdengineBridge
├── simulator/DeviceSimulator.java # MQTT 设备模拟器
├── llm/                           # TokenCostTracker + PromptCompressor + TemperatureExperiment
├── eval/                          # AgentEvaluator + 测试数据集
├── mcp/                           # McpConfig + McpAgent
├── edge/                          # EdgeConfig + ModelRouter
├── guardrail/                     # InputGuard + OutputGuard + ActionGuard + CircuitBreaker
└── observability/                 # AgentMetrics + StructuredLogger `[P0]`

src/test/                          # P2RagTest + P2SkillWorkflowTest `[P2]`
```

## 模块详解

### Agent 模式

| 模式 | 类 | 说明 |
|------|------|------|
| 单 Agent | `DeviceAgent` | 5 个 @Tool 全部注册，AgentRuntime 状态机包裹 |
| Router | `RouterAgent` | 5 个 Expert 按意图分发 |
| Supervisor | `SupervisorAgent` | 任务拆解 + 多 Expert 协作 + HITL 审批 |
| MCP | `McpAgent` | 通过 MCP 协议动态发现工具 |
| 边缘 | `ModelRouter` | 边缘 Ollama 优先 + 云端兜底 |

### 运行时状态机 `[P0]`

每个请求经过 `AgentRuntime.execute(ctx, action)` 包裹，状态流转：

```
RECEIVED → SESSION_READY → CONTEXT_READY → MODEL_THINKING → POST_PROCESSING → COMPLETED
                                                                              ↘ FAILED
```

- `RuntimeContext` 携带 traceId、session/tenant/user、deadline、工具调用历史
- `CircuitBreaker` 前置检查 → `BudgetManager` 预算校验 → 执行 → 指标记录
- `ToolExecutor` 提供 SHA-256 幂等键 + 结果缓存 + `ExecutionAuditLog` 审计

### 四层记忆 `[P1]`

| 层 | 组件 | 存储 | 说明 |
|------|------|------|------|
| L1 Working | `WorkingMemory` | Redis（短 TTL） | 单任务中途状态，任务结束自动过期 |
| L2 Conversation | `ConversationMemory` | Redis Stream（capped） | 最近 N 轮原始对话，旧轮次滚动淘汰 |
| L3 Summary | `SummaryMemory` | PostgreSQL + LLM 异步蒸馏 | 结构化摘要（目标/事实/待办/约束） |
| L4 Profile | `ProfileMemory` | PostgreSQL（置信度门控） | 用户/设备长期画像，低于阈值拒绝写入 |

`MemoryManager` 统一编排：同步合并 L4+L3+L2 为 Prompt 上下文块，L3/L4 异步生成。

### 六层 Prompt 编译 `[P1]`

`PromptCompiler` 替代硬编码 `@SystemMessage`，每次请求动态组装：

```
L1 角色 → L2 安全策略 → L3 工具契约 → L4 记忆上下文 → L5 知识库 → L6 ReAct 任务指令
```

- `PolicyRegistry` 管理安全/合规规则
- `ToolContractGenerator` 生成工具调用约束说明
- `PromptCompressor` 自动压缩冗余内容（重复行折叠 + JSON 摘要），节省 token

### 预算与调度 `[P1]`

- `ToolBudget`（`agent.budget.*`）定义 LLM 调用上限、READ/WRITE 工具上限、总延迟 deadline
- `BudgetManager` 执行预算检查 + Redis 分布式写锁 + 并行 READ 工具执行
- `AsyncSideCar` 将成本记录、审计持久化、记忆写入、指标上报从热路径剥离到 `@Async` 线程池

### RAG 混合检索管线 `[P2]`

```
用户查询 → Query Rewriting（Multi-Query / HyDE）
        → Dense 检索（Milvus + 租户过滤）
        → Sparse 检索（BM25 内存索引）
        → RRF 融合（Reciprocal Rank Fusion）
        → LLM Reranker（DeepSeek 打分重排）
        → JSON 结果
```

- **租户过滤**：通过 `RagContextHolder` 注入 `tenant_id`，Milvus 查询自动带 Filter
- **LLM Reranker**：一次 LLM 调用批量打分（0-10），按相关性重排候选项
- 可配置：`rag.rewrite-strategy`（NONE / HYDE / MULTI_QUERY）、`rag.rerank.enabled`

### Skill 接口 `[P2]`

`Skill` 接口 + `SkillTemplate` 抽象基类为可复用的 Agent 能力单元：

- `AlarmDiagnosisSkill` — 告警分析 + 故障诊断联合技能，注册 AlarmTool + DiagnosisTool + KnowledgeBaseTool 子集
- `PromptCompiler` 驱动 system message，`AiServices` 构建 assistant
- `WorkflowEngine` 通过 `skillName` 查找并执行 Skill

### Workflow 引擎 `[P2]`

`WorkflowEngine` 基于 DAG 的工作流执行器：

- 节点类型：`EXPERT_CALL`（Skill）| `TOOL_CALL`（searchKnowledgeBase / createWorkOrder）| `APPROVAL`（HITL 审批门）| `NOTIFY`（通知）
- 拓扑排序保证执行顺序，上下文在节点间累积传递
- 遇到 `AWAITING_APPROVAL` 暂停等待人工审批

### 工单生命周期

PENDING → IN_PROGRESS → COMPLETED / CANCELLED → CLOSED（H2 持久化）

### 安全护栏

InputGuard（危险指令拦截）→ OutputGuard（敏感信息过滤）→ ActionGuard（L3 审批）→ CircuitBreaker（熔断）

## 监控

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000（admin/admin）
- Micrometer 指标：`agent.requests.total`、`agent.tool.calls.total`、`agent.llm.calls.total`、`agent.requests.failed`、`agent.request.duration`

## 测试

```bash
mvn test  # P2RagTest + P2SkillWorkflowTest
```

## 版本演进

| 版本 | 关键 Commit | 核心变更 |
|------|-----------|---------|
| **MVP** | `mvp` tag | Agent 闭环（5 工具 + 5 模式）、RAG 基础检索（Milvus）、安全护栏、Edge/MCP、Prometheus+Grafana、Eval 评估框架 |
| **P0** | `afe222f` | AgentRuntime 状态机、ToolRegistry + ToolBudget + ToolExecutor（SHA-256 幂等+审计）、AgentMetrics + StructuredLogger |
| **P1** | `38f5cc9` `25e62e6` `e443a75` | 四层记忆（L1 Redis / L2 Stream / L3 PG+LLM / L4 PG 置信度门控）、六层 PromptCompiler、BudgetManager（LLM/工具预算+分布式锁）、AsyncSideCar |
| **P2** | `38b2c97` `f1d7299` `ee375a7` | RAG BM25 稀疏检索 + RRF 融合 + LLM Reranker + 租户过滤、Skill 接口 + SkillTemplate + AlarmDiagnosisSkill、WorkflowEngine（DAG 拓扑排序 + HITL）、JUnit 5 单元测试 |

## 作者

LaoLiang — 工业 AI Agent 实战派，从设备到决策的全链路。

> *代码仓库：[github.com/LaoLiang-agent/industrial-agent-long](https://github.com/LaoLiang-agent/industrial-agent-long)*
