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
                你是一名工业设备专家。根据以下问题，写一段简短的假设性回答（2-3句话），
                就像在阅读维修手册一样。使用专业技术术语。

                问题：%s
                假设性回答：""".formatted(query);

        return chatModel.chat(prompt).trim();
    }

    /**
     * Generate 3 diverse queries from different angles.
     */
    public List<String> multiQueryRewrite(String query) {
        String prompt = """
                根据以下用户问题，生成3个不同的搜索查询，
                以帮助在工业知识库中找到相关信息。
                每个查询应从不同角度切入问题。
                每行输出一个查询，不要编号。

                问题：%s
                查询：""".formatted(query);

        String raw = chatModel.chat(prompt).trim();
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
                根据以下具体的故障排查问题，写一个更通用的背景问题，
                以帮助查找该主题的基础知识。
                从具体设备问题退一步，上升到通用原理层面。

                具体问题：%s
                通用问题：""".formatted(query);

        return chatModel.chat(prompt).trim();
    }
}
