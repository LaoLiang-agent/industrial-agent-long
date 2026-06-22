package com.industrial.agent.mcp;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Agent that discovers and uses tools via MCP protocol.
 * Tools are NOT hardcoded — they are dynamically discovered from the MCP Server.
 */
@Slf4j
@Service
@ConditionalOnBean(McpToolProvider.class)
public class McpAgent {

    private final ChatModel chatModel;
    private final McpToolProvider mcpToolProvider;

    interface McpAssistant {
        @SystemMessage("""
                你是一个工业设备运维专家。
                你的工具通过 MCP 协议动态发现，请根据可用工具处理用户请求。
                涉及安全风险时明确标注优先级。
                """)
        String chat(String message);
    }

    public McpAgent(ChatModel chatModel, McpToolProvider mcpToolProvider) {
        this.chatModel = chatModel;
        this.mcpToolProvider = mcpToolProvider;
    }

    public String chat(String message) {
        log.info("[McpAgent] Processing: {}", message);
        McpAssistant assistant = AiServices.builder(McpAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .toolProvider(mcpToolProvider)
                .build();
        return assistant.chat(message);
    }
}
