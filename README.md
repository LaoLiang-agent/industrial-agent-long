# Industrial Agent Long(龍)

> 工业 AI Agent 实战项目 — 从 MQTT 设备数据到 Agent 智能诊断的全链路

## 概览

一个基于 **LangChain4j + DeepSeek + Spring Boot** 的工业设备智能运维 Agent 系统。

Agent 可以自主调用工具完成：
- **设备告警查询** — 查看指定设备的活跃告警
- **历史数据查询** — 拉取设备关键指标（温度、振动、压力、转速、电流）
- **故障诊断** — 基于告警类型和数据异常，生成诊断结论和维修建议

## 架构

```
User (Chat / REST API)
        │
        ▼
┌───────────────────────┐
│  DeviceAgent          │  ← LangChain4j AI Service
│  (Agent 决策层)        │
│                       │
│  Tools:               │
│  ├─ DeviceAlarmTool   │  ← 查询设备告警
│  ├─ DeviceDataTool    │  ← 查询设备时序数据
│  └─ DiagnosisTool     │  ← 生成故障诊断
└───────┬───────────────┘
        │
        ▼
┌───────────────────────┐
│  DeepSeek API         │  ← LLM (OpenAI-compatible)
└───────────────────────┘

Device Simulator: MQTT → EMQX
  (每5秒模拟 3 台设备发布温度/振动/压力/转速/电流数据)
```

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- Docker（用于启动 EMQX）

### 1. 启动 MQTT Broker

```bash
docker compose up -d
```

### 2. 设置 DeepSeek API Key

```bash
export DEEPSEEK_API_KEY=sk-your-key-here
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

### 4. 测试 Agent

```bash
# 健康检查
curl http://localhost:8080/api/agent/health

# 与 Agent 对话
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "查询 CNC-001 的告警情况"}'

# 复杂诊断请求
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "CNC-001 出现振动异常告警，帮我查一下数据并诊断"}'

# 清除对话记忆
curl -X POST http://localhost:8080/api/agent/clear
```

## 技术栈

| 组件 | 技术选型 |
|------|---------|
| LLM | DeepSeek (via OpenAI-compatible API) |
| Agent 框架 | LangChain4j 0.35.0 |
| 应用框架 | Spring Boot 3.3 |
| 消息队列 | EMQX 5.7 (MQTT) |
| 设备模拟 | Eclipse Paho MQTT Client |
| 语言 | Java 17 |

## 后续计划

- [ ] 接入真实时序数据库（TDEngine）
- [ ] 集成 RAG 知识库（Milvus + 设备维修手册）
- [ ] 多 Agent 协作（诊断 Agent + 数据查询 Agent + 工单 Agent）
- [ ] MCP 协议标准化工具接口
- [ ] 钉钉/飞书工单推送

## 作者
(Leo Liang) — 10 年工业 IoT 架构经验，专注 AI Agent 在工业场景的落地。

> 工业 AI Agent 实战派 — 从设备到决策的全链路
