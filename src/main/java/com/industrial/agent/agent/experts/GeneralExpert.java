package com.industrial.agent.agent.experts;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeneralExpert {

    private final ChatModel chatModel;

    interface GeneralAssistant {
        @SystemMessage("""
                你是一个工业设备运维领域的助手。
                你没有工具可用，只能基于自身知识回答一般性问题。
                如果用户询问具体设备数据、告警或诊断，建议他们使用更具体的指令。
                """)
        String chat(String message);
    }

    public GeneralExpert(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String message) {
        log.info("[GeneralExpert] Processing: {}", message);
        GeneralAssistant assistant = AiServices.builder(GeneralAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        return assistant.chat(message);
    }
}
