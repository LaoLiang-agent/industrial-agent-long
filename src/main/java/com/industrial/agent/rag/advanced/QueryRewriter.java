package com.industrial.agent.rag.advanced;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Three query rewriting strategies for better retrieval:
 *   1. HyDE — LLM generates hypothetical answer, use that for retrieval
 *   2. Multi-Query — generate 3 angle-diverse queries from original
 *   3. Step-back — abstract to a broader question, then retrieve
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private final OpenAiChatModel chatModel;

    /**
     * HyDE: Generate a hypothetical answer, then use it as the retrieval query.
     * Hypothesis answers share vocabulary with actual documents.
     */
    public String hydeRewrite(String query) {
        String prompt = """
                You are an industrial equipment expert. Given the question below,
                write a short hypothetical answer (2-3 sentences) as if you were
                reading from a maintenance manual. Use technical terminology.

                Question: %s
                Hypothetical answer:""".formatted(query);

        return chatModel.generate(prompt).trim();
    }

    /**
     * Generate 3 diverse queries from different angles.
     */
    public List<String> multiQueryRewrite(String query) {
        String prompt = """
                Given the user question below, generate 3 different search queries
                that could help find relevant information in an industrial knowledge base.
                Each query should approach the problem from a different angle.
                Output one query per line, no numbers.

                Question: %s
                Queries:""".formatted(query);

        String raw = chatModel.generate(prompt).trim();
        List<String> queries = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String q = line.replaceAll("^[\\d\\.\\- ]+", "").trim();
            if (!q.isBlank() && q.length() > 3) {
                queries.add(q);
            }
        }
        return queries.isEmpty() ? List.of(query) : queries;
    }

    /**
     * Step-back: abstract to a broader/background question.
     * E.g., "CNC-001 bearing temp high" → "What are common causes of bearing overheating?"
     */
    public String stepBackRewrite(String query) {
        String prompt = """
                Given the specific troubleshooting question below, write a more
                general background question that would help find foundational
                knowledge about the topic. Step back from the specific device
                to the general principle.

                Specific question: %s
                General question:""".formatted(query);

        return chatModel.generate(prompt).trim();
    }
}
