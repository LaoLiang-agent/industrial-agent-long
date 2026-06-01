package com.industrial.agent.agent;

import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAgent {

    private final OpenAiChatModel chatModel;
    private final ChatMemory chatMemory;
    private final DeviceAlarmTool alarmTool;
    private final DeviceDataTool dataTool;
    private final DiagnosisTool diagnosisTool;

    public String chat(String userMessage) {
        log.info("[Agent] User message: {}", userMessage);

        IndustrialAssistant assistant = AiServices.builder(IndustrialAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(chatMemory)
                .tools(alarmTool, dataTool, diagnosisTool)
                .build();

        return assistant.chat(userMessage);
    }

    /**
     * The AI Service interface — LangChain4j generates the implementation.
     */
    interface IndustrialAssistant {
        String chat(String message);
    }
}
