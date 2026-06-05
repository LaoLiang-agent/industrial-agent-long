package com.industrial.agent.agent;

import com.industrial.agent.agent.tools.DeviceAlarmTool;
import com.industrial.agent.agent.tools.DeviceDataTool;
import com.industrial.agent.agent.tools.DiagnosisTool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryComparisonService {

    private final OpenAiChatModel chatModel;
    private final DeviceAlarmTool alarmTool;
    private final DeviceDataTool dataTool;
    private final DiagnosisTool diagnosisTool;

    interface Assistant {
        String chat(String message);
    }

    /**
     * Run the same multi-turn conversation against three memory strategies.
     * Creates fresh ChatMemory instances per call to ensure isolation.
     */
    public Map<String, List<String>> compare(List<String> conversation) {
        Map<String, List<String>> results = new LinkedHashMap<>();

        // Strategy 1: Message window — keep last 20 messages (standard)
        ChatMemory msg20 = MessageWindowChatMemory.withMaxMessages(20);
        results.put("messageWindow(20)", runConversation(msg20, conversation));

        // Strategy 2: Message window — keep only last 4 messages (short memory)
        ChatMemory msg4 = MessageWindowChatMemory.withMaxMessages(4);
        results.put("messageWindow(4)", runConversation(msg4, conversation));

        // Strategy 3: Token window — keep messages up to 2000 tokens
        ChatMemory token2k = TokenWindowChatMemory.withMaxTokens(2000, new OpenAiTokenizer());
        results.put("tokenWindow(2000t)", runConversation(token2k, conversation));

        return results;
    }

    private List<String> runConversation(ChatMemory memory, List<String> messages) {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(memory)
                .tools(alarmTool, dataTool, diagnosisTool)
                .build();

        List<String> replies = new ArrayList<>();
        for (String msg : messages) {
            log.info("[MemoryCompare] Turn {}: {}", replies.size() + 1, msg);
            String reply = assistant.chat(msg);
            replies.add(reply);
        }
        return replies;
    }
}
