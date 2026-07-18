package com.industrial.agent.rag;

import com.industrial.agent.config.RagProperties;
import com.industrial.agent.config.RagProperties.RewriteStrategy;
import com.industrial.agent.rag.advanced.Bm25Retriever;
import com.industrial.agent.rag.advanced.LlmReranker;
import com.industrial.agent.rag.advanced.QueryRewriter;
import com.industrial.agent.rag.advanced.RrfFusion;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Component
public class KnowledgeBaseTool {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final QueryRewriter queryRewriter;
    private final Bm25Retriever bm25Retriever;
    private final RrfFusion rrfFusion;
    private final LlmReranker llmReranker;
    private final RagProperties ragProperties;

    public KnowledgeBaseTool(EmbeddingModel embeddingModel,
                             EmbeddingStore<TextSegment> embeddingStore,
                             QueryRewriter queryRewriter,
                             Bm25Retriever bm25Retriever,
                             RrfFusion rrfFusion,
                             LlmReranker llmReranker,
                             RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.queryRewriter = queryRewriter;
        this.bm25Retriever = bm25Retriever;
        this.rrfFusion = rrfFusion;
        this.llmReranker = llmReranker;
        this.ragProperties = ragProperties;
    }

    @Tool("从工业设备维修知识库中检索相关故障诊断信息。" +
          "当用户询问设备故障原因、维修方法、维护保养知识时使用此工具。" +
          "输入查询问题，返回最相关的维修知识条目。")
    public String searchKnowledgeBase(String query) {
        log.info("[RAG] Hybrid search: {}", query.length() > 80 ? query.substring(0, 80) + "..." : query);

        // Step 1: Query rewrite
        String searchQuery = rewriteQuery(query);

        // Step 2: Dense retrieval with tenant filter
        List<EmbeddingMatch<TextSegment>> denseResults = denseSearch(searchQuery);

        // Step 3: Sparse BM25 retrieval
        List<Bm25Retriever.ScoredDoc> sparseResults = bm25Retriever.search(searchQuery, ragProperties.getSparseTopK());

        // Step 4: RRF fusion
        List<String> fused = rrfFusion.fuse(denseResults, sparseResults, ragProperties.getFusionTopK());

        if (fused.isEmpty()) {
            return "{\"found\":false,\"message\":\"知识库中未找到相关信息\"}";
        }

        // Step 5: Rerank
        List<String> finalResults;
        if (ragProperties.getRerank().isEnabled()) {
            finalResults = llmReranker.rerank(query, fused, ragProperties.getFinalTopK());
        } else {
            finalResults = fused.size() > ragProperties.getFinalTopK()
                    ? fused.subList(0, ragProperties.getFinalTopK()) : fused;
        }

        // Step 6: Format JSON response (expiry filter not applicable — static KB)
        String entries = finalResults.stream()
                .map(text -> String.format("{\"content\":\"%s\"}", text.replace("\"", "'").replace("\\", "\\\\")))
                .collect(Collectors.joining(","));

        return String.format("{\"found\":true,\"total\":%d,\"entries\":[%s]}",
                finalResults.size(), entries);
    }

    private String rewriteQuery(String query) {
        RewriteStrategy strategy = ragProperties.getRewriteStrategy();
        if (strategy == RewriteStrategy.NONE) {
            return query;
        }
        try {
            if (strategy == RewriteStrategy.MULTI_QUERY) {
                List<String> queries = queryRewriter.multiQueryRewrite(query);
                return queries.isEmpty() ? query : String.join(" ", queries);
            }
            // Default: HYDE
            return queryRewriter.hydeRewrite(query);
        } catch (Exception e) {
            log.warn("[RAG] Query rewrite failed, using original query: {}", e.getMessage());
            return query;
        }
    }

    private List<EmbeddingMatch<TextSegment>> denseSearch(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(ragProperties.getDenseTopK())
                .minScore(ragProperties.getMinScore());

        String tenantId = RagContextHolder.getTenantId();
        if (tenantId != null) {
            Filter tenantFilter = metadataKey("tenant_id").isEqualTo(tenantId);
            requestBuilder.filter(tenantFilter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(requestBuilder.build());
        log.info("[RAG] Dense search: {} matches (tenant={})", result.matches().size(),
                tenantId != null ? tenantId : "none");
        return result.matches();
    }

    // Preserved: simple search for backward compatibility and RagEvaluator
    public List<String> simpleDenseSearch(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .minScore(0.3)
                        .build()
        ).matches().stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.toList());
    }
}
