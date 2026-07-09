package com.industrial.agent.agent;

import com.industrial.agent.agent.model.DiagnosticResponse;
import com.industrial.agent.llm.TokenCostTracker;
import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import com.industrial.agent.agent.tools.WorkOrderTool;
import com.industrial.agent.memory.MemoryManager;
import com.industrial.agent.rag.KnowledgeBaseTool;
import com.industrial.agent.runtime.AgentRuntime;
import com.industrial.agent.runtime.RuntimeContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceAgent {

    private final OpenAiChatModel chatModel;
    private final ChatMemory chatMemory;
    private final DeviceAlarmTool alarmTool;
    private final DeviceDataTool dataTool;
    private final DiagnosisTool diagnosisTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final WorkOrderTool workOrderTool;
    private final TokenCostTracker costTracker;
    private final AgentRuntime runtime;
    private final MemoryManager memory;

    public DeviceAgent(OpenAiChatModel chatModel, ChatMemory chatMemory,
                       DeviceAlarmTool alarmTool, DeviceDataTool dataTool,
                       DiagnosisTool diagnosisTool, KnowledgeBaseTool knowledgeBaseTool,
                       WorkOrderTool workOrderTool, TokenCostTracker costTracker,
                       AgentRuntime runtime, MemoryManager memory) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.alarmTool = alarmTool;
        this.dataTool = dataTool;
        this.diagnosisTool = diagnosisTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.workOrderTool = workOrderTool;
        this.costTracker = costTracker;
        this.runtime = runtime;
        this.memory = memory;
    }

    public String chat(RuntimeContext ctx, String userMessage) {
        return runtime.execute(ctx, () -> {
            log.info("[Agent] trace={} userMessage={}", ctx.getTraceId(), userMessage);
            // L4+L3+L2 memory context injected ahead of the user message.
            // (P1-2 PromptCompiler will formalize this as the L4 Memory layer.)
            String contextBlock = memory.buildContextBlock(ctx);
            String augmented = contextBlock.isBlank()
                    ? userMessage
                    : "已知上下文（供参考，勿复述）：\n" + contextBlock + "\n---\n用户问题：" + userMessage;
            String reply = buildAssistant().chat(augmented);
            costTracker.recordRequest(userMessage, reply);
            memory.recordTurn(ctx, userMessage, reply);
            return reply;
        });
    }

    public TokenStream chatStream(RuntimeContext ctx, String userMessage) {
        ctx.transition(com.industrial.agent.runtime.AgentState.SESSION_READY);
        ctx.transition(com.industrial.agent.runtime.AgentState.CONTEXT_READY);
        log.info("[Agent] trace={} streaming userMessage={}", ctx.getTraceId(), userMessage);
        return buildAssistant().chatStream(userMessage);
    }

    public DiagnosticResponse diagnose(RuntimeContext ctx, String deviceId) {
        return runtime.execute(ctx, () -> {
            log.info("[Agent] trace={} diagnosis for device={}", ctx.getTraceId(), deviceId);
            DiagnosticAssistant assistant = AiServices.builder(DiagnosticAssistant.class)
                    .chatModel(chatModel)
                    .chatMemory(chatMemory)
                    .tools(alarmTool, dataTool, diagnosisTool, knowledgeBaseTool, workOrderTool)
                    .build();
            return assistant.diagnose(deviceId);
        });
    }

    private IndustrialAssistant buildAssistant() {
        return AiServices.builder(IndustrialAssistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(alarmTool, dataTool, diagnosisTool, knowledgeBaseTool, workOrderTool)
                .build();
    }

    interface IndustrialAssistant {
        @SystemMessage("""
                你是一个工业设备运维专家，服务于智能工厂的设备监控与故障诊断。
                你的知识有限，无法访问实时设备数据。
                当用户询问设备状态、告警、历史数据、故障原因时，你必须使用提供的工具查询，不要凭猜测回答。

                可用工具：
                - queryDeviceAlarms：查询设备当前告警
                - queryDeviceHistory：查询设备历史遥测数据（含统计）
                - queryRealtimeData：查询设备最近5分钟原始数据
                - searchKnowledgeBase：检索维修知识库
                - generateDiagnosis：基于告警类型生成诊断结论
                - createWorkOrder：创建维修工单（仅在确认硬件故障时使用）

                思考路径（ReAct），每步先思考再行动：
                1. 数据采集：先用 queryDeviceAlarms 和 queryDeviceHistory 获取实时数据
                2. 知识检索：如有异常，用 searchKnowledgeBase 检索相关维修知识和历史案例
                3. 诊断分析：用 generateDiagnosis 分析根因
                4. 工单决策：确认是硬件故障需要人工介入时，用 createWorkOrder 创建工单。
                   如果设备状态正常、无告警，不要创建工单
                5. 最终输出：按「设备状态 → 异常发现 → 诊断结论 → 维修建议 → 工单信息」结构呈现

                回复规范：
                - 用结构化方式呈现结果（表格优先于纯文本）
                - 涉及安全风险时明确标注优先级（HIGH/MEDIUM/LOW）
                - 不确定时如实说明，不要编造数据
                """)
        String chat(String message);

        TokenStream chatStream(String message);
    }

    interface DiagnosticAssistant {
        @SystemMessage("""
                你是一个工业设备故障诊断专家。你必须使用提供的工具查询设备数据，
                然后基于工具返回的结构化数据进行诊断分析，不要凭猜测回答。

                诊断流程：
                1. 查询设备告警（queryDeviceAlarms）
                2. 查询历史遥测数据（queryDeviceHistory）
                3. 如有异常，检索知识库（searchKnowledgeBase）
                4. 生成诊断结论（generateDiagnosis）
                5. 若确认为硬件故障，创建工单（createWorkOrder）
                """)
        @UserMessage("""
                请对设备 {{deviceId}} 进行全面的故障诊断分析：
                1. 查询设备告警信息
                2. 查询设备历史遥测数据
                3. 基于以上数据生成诊断报告
                """)
        DiagnosticResponse diagnose(@dev.langchain4j.service.V("deviceId") String deviceId);
    }
}
