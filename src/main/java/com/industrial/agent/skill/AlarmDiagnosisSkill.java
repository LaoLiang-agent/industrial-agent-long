package com.industrial.agent.skill;

import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import com.industrial.agent.prompt.PromptCompiler;
import com.industrial.agent.rag.KnowledgeBaseTool;
import com.industrial.agent.runtime.RuntimeContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Combined alarm analysis + diagnosis skill using PromptCompiler for dynamic system messages.
 * Demonstrates the SkillTemplate pattern for skills that need L1-L6 prompt compilation.
 */
@Component
public class AlarmDiagnosisSkill extends SkillTemplate {

    interface Assistant {
        String chat(String message);
    }

    public AlarmDiagnosisSkill(PromptCompiler promptCompiler, ChatModel chatModel,
                               DeviceAlarmTool alarmTool, DiagnosisTool diagnosisTool,
                               KnowledgeBaseTool knowledgeBaseTool) {
        super(promptCompiler, chatModel, List.of(alarmTool, diagnosisTool, knowledgeBaseTool));
    }

    @Override
    public String chat(String message) {
        return buildAssistant(Assistant.class, null).chat(message);
    }

    @Override
    public String chat(String message, RuntimeContext ctx) {
        return buildAssistant(Assistant.class, ctx).chat(message);
    }

    @Override
    public String skillName() { return "ALARM_DIAGNOSIS_SKILL"; }

    @Override
    public String description() { return "告警分析+故障诊断联合技能"; }
}
