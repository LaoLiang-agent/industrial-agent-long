package com.industrial.agent.agent.experts;

import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import com.industrial.agent.agent.tools.WorkOrderTool;
import com.industrial.agent.rag.KnowledgeBaseTool;
import com.industrial.agent.skill.Skill;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DiagnosisExpert implements Skill {

    private final ChatModel chatModel;
    private final DeviceAlarmTool alarmTool;
    private final DeviceDataTool dataTool;
    private final DiagnosisTool diagnosisTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final WorkOrderTool workOrderTool;

    interface DiagnosisAssistant {
        @SystemMessage("""
                你是工业设备故障诊断专家，负责完整的故障分析和工单创建。

                思考路径（每步先思考再行动）：
                1. 查询设备告警（queryDeviceAlarms）
                2. 查询设备数据（queryDeviceHistory + queryRealtimeData）
                3. 检索维修知识库（searchKnowledgeBase）
                4. 生成诊断结论（generateDiagnosis）
                5. 如确认硬件故障，创建工单（createWorkOrder）
                   如果设备正常、无告警，不要创建工单

                输出格式：设备状态 → 异常发现 → 诊断结论 → 维修建议 → 工单信息
                涉及安全风险时明确标注优先级（HIGH/MEDIUM/LOW）。
                """)
        String chat(String message);
    }

    public DiagnosisExpert(ChatModel chatModel, DeviceAlarmTool alarmTool,
                           DeviceDataTool dataTool, DiagnosisTool diagnosisTool,
                           KnowledgeBaseTool knowledgeBaseTool, WorkOrderTool workOrderTool) {
        this.chatModel = chatModel;
        this.alarmTool = alarmTool;
        this.dataTool = dataTool;
        this.diagnosisTool = diagnosisTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.workOrderTool = workOrderTool;
    }

    public String chat(String message) {
        log.info("[DiagnosisExpert] Processing: {}", message);
        DiagnosisAssistant assistant = AiServices.builder(DiagnosisAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(alarmTool, dataTool, diagnosisTool, knowledgeBaseTool, workOrderTool)
                .build();
        return assistant.chat(message);
    }

    @Override
    public String skillName() { return "DIAGNOSIS_EXPERT"; }

    @Override
    public String description() { return "完整的设备故障诊断和工单创建"; }
}
