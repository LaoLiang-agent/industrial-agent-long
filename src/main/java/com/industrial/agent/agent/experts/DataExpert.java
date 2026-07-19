package com.industrial.agent.agent.experts;

import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.skill.Skill;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataExpert implements Skill {

    private final ChatModel chatModel;
    private final DeviceDataTool dataTool;

    interface DataAssistant {
        @SystemMessage("""
                你是工业设备数据分析专家。你只负责设备遥测数据的查询和趋势分析。
                可用工具：
                - queryDeviceHistory：查询过去1小时统计摘要和最新值
                - queryRealtimeData：查询最近5分钟原始数据
                回复规范：
                - 用表格呈现关键指标（温度、振动、压力、转速、电流）
                - 标注异常值和趋势变化
                - 不做故障诊断，只呈现数据事实
                """)
        String chat(String message);
    }

    public DataExpert(ChatModel chatModel, DeviceDataTool dataTool) {
        this.chatModel = chatModel;
        this.dataTool = dataTool;
    }

    public String chat(String message) {
        log.info("[DataExpert] Processing: {}", message);
        DataAssistant assistant = AiServices.builder(DataAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(dataTool)
                .build();
        return assistant.chat(message);
    }

    @Override
    public String skillName() { return "DATA_EXPERT"; }

    @Override
    public String description() { return "设备遥测数据查询和趋势分析"; }
}
