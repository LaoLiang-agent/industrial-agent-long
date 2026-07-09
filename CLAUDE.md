# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> A parent `../CLAUDE.md` governs the whole AIOT monorepo (article publishing, image workflows). This file is specific to the `industrial-agent-long/` Spring Boot codebase.

## Build & Run

Always compile/run with Amazon Corretto 21 (the system default JDK may differ):

```bash
JAVA_HOME=/Users/leo/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home mvn compile -q
```

- Run app: `mvn spring-boot:run` (requires `export DEEPSEEK_API_KEY=sk-...`)
- Package: `mvn package` → runnable jar via `spring-boot-maven-plugin`
- Start all backing services: `docker compose up -d` (EMQX, TDEngine, Milvus + etcd/minio, Prometheus, Grafana)
- After startup, seed the vector store: `curl -X POST http://localhost:8080/api/rag/ingest`
- App on `:8080`, Prometheus `:9090`, Grafana `:3000` (admin/admin), H2 console at `/h2`

**Tests:** there is no `src/test` yet — no unit/integration test suite exists. Verification is done via the REST endpoints and the `/api/eval/run` evaluation framework, not JUnit.

## Configuration

- `application.yml` holds all config; `application-local.yml` is imported optionally (gitignored, put secrets/local overrides there).
- LLM is DeepSeek via the OpenAI-compatible endpoint (`langchain4j-open-ai` pointed at `api.deepseek.com`). `DEEPSEEK_API_KEY` env var is required.
- MCP client (`McpConfig`) is **disabled by default** — only wired when `mcp.enabled=true` (connects to an external MCP server at `mcp.server-url`).
- Hard per-request budget lives under `agent.budget.*` in `application.yml` and is enforced by the tool/runtime layers below.

## Architecture — the big picture

Two data paths, worth understanding before editing:

- **Write path (telemetry ingest):** `DeviceSimulator` publishes MQTT → EMQX → `MqttToTdengineBridge` subscribes and writes to TDEngine (WebSocket JDBC). Tools read from TDEngine, they never touch MQTT directly.
- **Query path (agent):** REST controller → an Agent mode → LangChain4j `AiServices` → `@Tool` methods → DeepSeek LLM → response. Work orders persist to H2; tool calls append to an in-memory audit log.

### How an Agent is assembled (the core pattern)

Agents are **not** subclasses. Each agent class (e.g. `DeviceAgent`) defines a private inner **interface** annotated with `@SystemMessage` (the CoT/ReAct prompt) and builds it at call time with `AiServices.builder(...).chatModel(...).chatMemory(...).tools(...)`. The `@Tool`-annotated methods on the tool beans are what the LLM can call. To change agent behavior you edit the `@SystemMessage` prompt and/or the registered tool set — there is no separate prompt-template file.

### Runtime state machine wraps every request

`DeviceAgent.chat()` delegates to `AgentRuntime.execute(ctx, action)`, which is the productionized control layer (added in the P0 commit, not covered by the README):

- `RuntimeContext` carries `traceId`, session/tenant/user, and a deadline. States flow through `AgentState` (`RECEIVED → SESSION_READY → CONTEXT_READY → MODEL_THINKING → POST_PROCESSING → COMPLETED/FAILED`).
- `AgentRuntime` checks the `CircuitBreaker`, enforces the deadline, records `AgentMetrics`, and emits structured traces via `StructuredLogger`. Any new agent entry point should run inside `runtime.execute(...)` to inherit this.

### Tool governance layer (`tool/` package)

Separate from LangChain4j's `@Tool` wiring, `ToolRegistry` declares metadata for each tool name (`SideEffect` READ/WRITE, max calls, `requiresApproval`, timeout). `ToolExecutor` enforces the read/write budgets, provides SHA-256 idempotency keys + result caching, and writes `ExecutionAuditLog` entries. `createWorkOrder` is the one WRITE tool that `requiresApproval`. When adding a tool, register it in `ToolRegistry.init()` **and** expose the `@Tool` method on a bean passed to the relevant `AiServices` builder.

### Agent modes (each has its own controller/endpoint)

| Mode | Class | Endpoint |
|------|-------|----------|
| Single agent (all 5 tools) | `DeviceAgent` | `/api/agent/chat`, `/chat/stream` (SSE), `/diagnose` |
| Router (intent → 1 of 5 experts) | `RouterAgent` + `agent/experts/*` | `/api/agent/route/chat` |
| Supervisor (plan → multi-expert + HITL approval) | `SupervisorAgent` + `TaskPlanner` + `ApprovalGate` | `/api/agent/supervisor/chat`, `/supervisor/approve|reject/{id}` |
| MCP (tools discovered over MCP protocol) | `McpAgent` | `/api/mcp/chat` |
| Edge (local Ollama first, cloud fallback) | `ModelRouter` | `/api/edge/chat` |

Other subsystems: `rag/` (Milvus + local AllMiniLmL6V2 embeddings, BM25 + RRF hybrid retrieval, query rewriting, `RagEvaluator`), `guardrail/` (`GuardrailChain`: InputGuard → OutputGuard → ActionGuard → CircuitBreaker), `eval/` (`AgentEvaluator` over `EvalDataset`), `llm/` (`TokenCostTracker`, `PromptCompressor`, `TemperatureExperiment`), `observability/` (Micrometer metrics + structured logging).

## Conventions

- Language of prompts, log tags, and many docs is Chinese; keep new `@SystemMessage` prompts consistent with the existing industrial-ops tone.
- Lombok is used (`@Slf4j`, constructors are hand-written for injection in the agent classes).
- Production-grade design decisions are specced in `docs/superpowers/specs/2026-06-28-production-upgrade-design.md` — consult it before large changes to the runtime/tool/guardrail layers.
