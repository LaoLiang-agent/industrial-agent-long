package com.industrial.agent.edge;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "edge.enabled", havingValue = "true", matchIfMissing = false)
public class EdgeConfig {

    @Bean("edgeModel")
    public ChatModel edgeModel(
            @Value("${edge.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${edge.ollama.model:qwen2.5:7b}") String modelName) {
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("ollama")
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .build();
        log.info("[Edge] Ollama model configured: {} at {}", modelName, baseUrl);
        return model;
    }
}
