package com.industrial.agent.agent;

import com.industrial.agent.agent.model.DiagnosticResponse;
import com.industrial.agent.llm.TokenCostTracker;
import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import com.industrial.agent.agent.tools.WorkOrderTool;
import com.industrial.agent.memory.MemoryManager;
import com.industrial.agent.prompt.PromptCompiler;
import com.industrial.agent.rag.KnowledgeBaseTool;
import com.industrial.agent.runtime.AgentRuntime;
import com.industrial.agent.runtime.RuntimeContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
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
    private final PromptCompiler promptCompiler;

    public DeviceAgent(OpenAiChatModel chatModel, ChatMemory chatMemory,
                       DeviceAlarmTool alarmTool, DeviceDataTool dataTool,
                       DiagnosisTool diagnosisTool, KnowledgeBaseTool knowledgeBaseTool,
                       WorkOrderTool workOrderTool, TokenCostTracker costTracker,
                       AgentRuntime runtime, MemoryManager memory, PromptCompiler promptCompiler) {
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
        this.promptCompiler = promptCompiler;
    }

    public String chat(RuntimeContext ctx, String userMessage) {
        return runtime.execute(ctx, () -> {
            log.info("[Agent] trace={} userMessage={}", ctx.getTraceId(), userMessage);
            // L1–L5 compiled into the system message; L6 wraps the user turn.
            String reply = buildAssistant(ctx).chat(promptCompiler.compileTask(userMessage));
            costTracker.recordRequest(userMessage, reply);
            memory.recordTurn(ctx, userMessage, reply);
            return reply;
        });
    }

    public TokenStream chatStream(RuntimeContext ctx, String userMessage) {
        ctx.transition(com.industrial.agent.runtime.AgentState.SESSION_READY);
        ctx.transition(com.industrial.agent.runtime.AgentState.CONTEXT_READY);
        log.info("[Agent] trace={} streaming userMessage={}", ctx.getTraceId(), userMessage);
        return buildAssistant(ctx).chatStream(promptCompiler.compileTask(userMessage));
    }

    public DiagnosticResponse diagnose(RuntimeContext ctx, String deviceId) {
        return runtime.execute(ctx, () -> {
            log.info("[Agent] trace={} diagnosis for device={}", ctx.getTraceId(), deviceId);
            DiagnosticAssistant assistant = AiServices.builder(DiagnosticAssistant.class)
                    .chatModel(chatModel)
                    .chatMemory(chatMemory)
                    .systemMessageProvider(id -> promptCompiler.compileSystem(ctx))
                    .tools(alarmTool, dataTool, diagnosisTool, knowledgeBaseTool, workOrderTool)
                    .build();
            return assistant.diagnose(deviceId);
        });
    }

    private IndustrialAssistant buildAssistant(RuntimeContext ctx) {
        return AiServices.builder(IndustrialAssistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .systemMessageProvider(id -> promptCompiler.compileSystem(ctx))
                .tools(alarmTool, dataTool, diagnosisTool, knowledgeBaseTool, workOrderTool)
                .build();
    }

    interface IndustrialAssistant {
        String chat(String message);

        TokenStream chatStream(String message);
    }

    interface DiagnosticAssistant {
        @UserMessage("""
                请对设备 {{deviceId}} 进行全面的故障诊断分析：
                1. 查询设备告警信息
                2. 查询设备历史遥测数据
                3. 基于以上数据生成诊断报告
                """)
        DiagnosticResponse diagnose(@dev.langchain4j.service.V("deviceId") String deviceId);
    }
}
