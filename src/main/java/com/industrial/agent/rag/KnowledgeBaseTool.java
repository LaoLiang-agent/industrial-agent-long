package com.industrial.agent.rag;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseTool {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Tool("从工业设备维修知识库中检索相关故障诊断信息。" +
          "当用户询问设备故障原因、维修方法、维护保养知识时使用此工具。" +
          "输入查询问题，返回最相关的维修知识条目。")
    public String searchKnowledgeBase(String query) {
        log.info("[RAG] Searching knowledge base: {}", query);

        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .minScore(0.5)
                        .build()
        );

        if (result.matches().isEmpty()) {
            return "{\"found\":false,\"message\":\"知识库中未找到相关信息\"}";
        }

        String entries = result.matches().stream()
                .map(m -> String.format("{\"score\":%.2f,\"content\":\"%s\"}",
                        m.score(), m.embedded().text().replace("\"", "'")))
                .collect(Collectors.joining(","));

        return String.format("{\"found\":true,\"total\":%d,\"entries\":[%s]}",
                result.matches().size(), entries);
    }
}
