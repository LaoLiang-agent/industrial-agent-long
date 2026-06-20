package com.industrial.agent.agent.experts;

import com.industrial.agent.agent.tools.DeviceAlarmTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlarmExpert {

    private final ChatModel chatModel;
    private final DeviceAlarmTool alarmTool;

    interface AlarmAssistant {
        @SystemMessage("""
                你是工业设备告警分析专家。你只负责设备告警的查询和解读。
                使用 queryDeviceAlarms 工具查询设备当前活跃告警。
                回复规范：
                - 列出所有告警，标注严重级别（HIGH/MEDIUM/LOW）
                - 对每条告警给出简要风险评估
                - 如果无告警，明确说明设备告警状态正常
                """)
        String chat(String message);
    }

    public AlarmExpert(ChatModel chatModel, DeviceAlarmTool alarmTool) {
        this.chatModel = chatModel;
        this.alarmTool = alarmTool;
    }

    public String chat(String message) {
        log.info("[AlarmExpert] Processing: {}", message);
        AlarmAssistant assistant = AiServices.builder(AlarmAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(alarmTool)
                .build();
        return assistant.chat(message);
    }
}
