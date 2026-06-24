# Industrial Agent Long（龍）

> 工业 AI Agent 实战项目 — 从 MQTT 设备数据到 Agent 智能诊断 + 维修工单的全链路闭环

## 概览

基于 **LangChain4j + DeepSeek + Spring Boot** 的工业设备智能运维 Agent 系统。

### 核心能力

| 能力 | 实现 |
|------|------|
| 设备告警查询 | DeviceAlarmTool — MQTT 实时告警 |
| 时序数据查询 | DeviceDataTool — TDEngine 聚合查询 |
| 故障诊断 | DiagnosisTool + CoT 推理链 |
| RAG 知识检索 | Milvus + BM25 + RRF 混合检索 |
| 维修工单 | WorkOrderTool — H2 持久化 + 生命周期 |
| 多 Agent 路由 | Router（5 个 Expert）+ Supervisor + HITL |
| 边缘推理 | Ollama 本地模型 + 云端兜底 |
| 安全护栏 | InputGuard + OutputGuard + ActionGuard + 熔断器 |
| MCP 协议 | McpToolProvider + Node.js MCP Server |
| 可观测性 | Prometheus + Grafana + Docker 健康检查 |

### API 端点速览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/chat` | 单 Agent 对话 |
| POST | `/api/agent/route/chat` | Router 多 Agent 路由 |
| POST | `/api/agent/supervisor/chat` | Supervisor 任务编排 |
| POST | `/api/mcp/chat` | MCP 协议 Agent |
| POST | `/api/edge/chat` | 边缘推理路由 |
| POST | `/api/eval/run` | 评估框架运行 |
| POST | `/api/guardrail/check-action` | 操作风险检查 |
| GET  | `/actuator/prometheus` | 指标导出 |

## 架构

```
写入流: DeviceSimulator → MQTT/EMQX → Bridge → TDEngine（WebSocket JDBC）
查询流: User → Agent(Router/Supervisor/MCP) → Tools → LLM → Response
                                                      ↓
                                                WorkOrder(H2) + AuditLog

安全层: InputGuard → Agent → OutputGuard → ActionGuard → CircuitBreaker
监控层: Micrometer → Prometheus(9090) → Grafana(3000)
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
| 数据库 | H2（工单持久化） |
| 监控 | Prometheus + Grafana |
| 容器化 | Docker 多阶段构建 + Compose |
| 语言 | Java 21 |

## 快速开始

```bash
# 1. 启动所有服务（EMQX + TDEngine + Milvus + Prometheus + Grafana）
docker compose up -d

# 2. 设置 DeepSeek API Key
export DEEPSEEK_API_KEY=sk-your-key

# 3. 启动应用
mvn spring-boot:run

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
├── config/AgentConfig.java
├── agent/
│   ├── DeviceAgent.java           # 单 Agent（@Tool 模式）
│   ├── MemoryComparisonService.java
│   ├── model/                     # WorkOrder, DiagnosticResponse
│   ├── router/                    # IntentClassifier + RouterAgent
│   ├── experts/                   # 5 Expert Agents
│   ├── supervisor/                # TaskPlanner + SupervisorAgent + ApprovalGate
│   └── tools/                     # Alarm, Data, Diagnosis, WorkOrder
├── controller/                    # Agent, Rag, Eval, Guardrail, Edge Controllers
├── rag/                           # MilvusConfig + KnowledgeBase + BM25 + RRF
├── tsdb/                          # TdengineDataService + MqttToTdengineBridge
├── simulator/DeviceSimulator.java # MQTT 设备模拟器
├── llm/                           # TokenCostTracker + TemperatureExperiment
├── eval/                          # AgentEvaluator + 测试数据集
├── mcp/                           # McpConfig + McpAgent
├── edge/                          # EdgeConfig + ModelRouter
└── guardrail/                     # InputGuard + OutputGuard + ActionGuard + CircuitBreaker
```

## 模块详解

### Agent 模式

| 模式 | 类 | 说明 |
|------|------|------|
| 单 Agent | `DeviceAgent` | 5 个 @Tool 全部注册，CoT 系统消息 |
| Router | `RouterAgent` | 5 个 Expert 按意图分发 |
| Supervisor | `SupervisorAgent` | 任务拆解 + 多 Expert 协作 + 审批 |
| MCP | `McpAgent` | 通过 MCP 协议动态发现工具 |
| 边缘 | `ModelRouter` | 边缘 Ollama 优先 + 云端兜底 |

### RAG 管线

Ingestion → Embedding(384d) → Milvus → BM25 + RRF 融合 → Query Rewriting → 评估(Hit Rate/MRR)

### 工单生命周期

PENDING → IN_PROGRESS → COMPLETED / CANCELLED → CLOSED（H2 持久化）

### 安全护栏

InputGuard（危险指令拦截）→ OutputGuard（敏感信息过滤）→ ActionGuard（L3 审批）→ CircuitBreaker（熔断）

## 监控

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000（admin/admin）

## 作者

LaoLiang — 工业 AI Agent 实战派，从设备到决策的全链路。

> *代码仓库：[github.com/LaoLiang-agent/industrial-agent-long](https://github.com/LaoLiang-agent/industrial-agent-long)*
