package com.industrial.agent.agent.experts;

import com.industrial.agent.rag.KnowledgeBaseTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KnowledgeExpert {

    private final ChatModel chatModel;
    private final KnowledgeBaseTool knowledgeBaseTool;

    interface KnowledgeAssistant {
        @SystemMessage("""
                你是工业设备维修知识专家。你负责从知识库中检索维修方案和操作指南。
                使用 searchKnowledgeBase 工具检索相关知识。
                回复规范：
                - 列出检索到的相关维修知识和操作步骤
                - 标注知识来源的相关度
                - 如知识库中无相关内容，如实说明
                """)
        String chat(String message);
    }

    public KnowledgeExpert(ChatModel chatModel, KnowledgeBaseTool knowledgeBaseTool) {
        this.chatModel = chatModel;
        this.knowledgeBaseTool = knowledgeBaseTool;
    }

    public String chat(String message) {
        log.info("[KnowledgeExpert] Processing: {}", message);
        KnowledgeAssistant assistant = AiServices.builder(KnowledgeAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(knowledgeBaseTool)
                .build();
        return assistant.chat(message);
    }
}
