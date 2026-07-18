package com.industrial.agent.rag.advanced;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG evaluation metrics: Hit Rate, MRR, NDCG.
 * Works with small test sets (50 queries is enough).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvaluator {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Bm25Retriever bm25;
    private final RrfFusion fusion;

    public record QueryResult(String query, String expectedKeyword,
                               boolean denseHit, boolean bm25Hit, boolean fusedHit,
                               int denseRank, int bm25Rank, int fusedRank) {}

    public enum Strategy { DENSE, BM25, FUSED }

    /**
     * Run evaluation across dense-only, BM25-only, and RRF-fused strategies.
     */
    public Map<String, Object> evaluate(Map<String, String> testQueries, // query → expected keyword
                                         List<String> allDocuments) {

        bm25.index(allDocuments);
        List<QueryResult> results = new ArrayList<>();

        for (var entry : testQueries.entrySet()) {
            String query = entry.getKey();
            String expected = entry.getValue();

            // Dense
            Embedding qe = embeddingModel.embed(query).content();
            var denseRes = embeddingStore.search(
                    EmbeddingSearchRequest.builder().queryEmbedding(qe).maxResults(10).minScore(0.3).build());

            // BM25
            var bm25Res = bm25.search(query, 10);

            // Fused
            var fusedRes = fusion.fuse(denseRes.matches(), bm25Res, 5);

            results.add(new QueryResult(
                    query, expected,
                    containsKeyword(denseRes.matches(), expected),
                    containsKeywordBm25(bm25Res, expected),
                    containsKeywordText(fusedRes, expected, allDocuments),
                    findFirstRank(denseRes.matches(), expected),
                    findFirstRankBm25(bm25Res, expected),
                    findFirstRankText(fusedRes, expected, allDocuments)
            ));
        }

        int total = results.size();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalQueries", total);

        for (Strategy s : Strategy.values()) {
            double hitRate = results.stream().filter(r -> hitByStrategy(r, s)).count() * 100.0 / total;
            double mrr = calculateMRR(results, s);
            double ndcg = calculateNDCG(results, s);
            metrics.put(s.name() + "_hitRate", String.format("%.1f%%", hitRate));
            metrics.put(s.name() + "_MRR", String.format("%.3f", mrr));
            metrics.put(s.name() + "_NDCG", String.format("%.3f", ndcg));
        }

        return metrics;
    }

    private boolean hitByStrategy(QueryResult r, Strategy s) {
        return switch (s) {
            case DENSE -> r.denseHit();
            case BM25 -> r.bm25Hit();
            case FUSED -> r.fusedHit();
        };
    }

    private double calculateMRR(List<QueryResult> results, Strategy s) {
        double sum = 0;
        for (QueryResult r : results) {
            int rank = switch (s) {
                case DENSE -> r.denseRank();
                case BM25 -> r.bm25Rank();
                case FUSED -> r.fusedRank();
            };
            sum += rank > 0 ? 1.0 / rank : 0;
        }
        return sum / results.size();
    }

    private double calculateNDCG(List<QueryResult> results, Strategy s) {
        // Binary relevance: 1 if hit, 0 otherwise. NDCG@10.
        int k = 10;
        double idcg = 0;
        for (int i = 0; i < Math.min(k, results.size()); i++) idcg += 1.0 / (Math.log(i + 2) / Math.log(2));

        double sum = 0;
        for (QueryResult r : results) {
            boolean hit = hitByStrategy(r, s);
            int rank = switch (s) {
                case DENSE -> r.denseRank();
                case BM25 -> r.bm25Rank();
                case FUSED -> r.fusedRank();
            };
            if (hit && rank > 0 && rank <= k) {
                sum += 1.0 / (Math.log(rank + 1) / Math.log(2));
            }
        }
        return idcg > 0 ? sum / (results.size() * idcg) : 0;
    }

    private boolean containsKeyword(List<EmbeddingMatch<TextSegment>> matches, String keyword) {
        return matches.stream().anyMatch(m -> m.embedded().text().contains(keyword));
    }

    private boolean containsKeywordBm25(List<Bm25Retriever.ScoredDoc> docs, String keyword) {
        return docs.stream().anyMatch(d -> d.text().contains(keyword));
    }

    private boolean containsKeywordText(List<String> texts, String keyword, List<String> allDocs) {
        return texts.stream().anyMatch(t -> t.contains(keyword));
    }

    private int findFirstRank(List<EmbeddingMatch<TextSegment>> matches, String keyword) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).embedded().text().contains(keyword)) return i + 1;
        }
        return -1;
    }

    private int findFirstRankBm25(List<Bm25Retriever.ScoredDoc> docs, String keyword) {
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i).text().contains(keyword)) return i + 1;
        }
        return -1;
    }

    private int findFirstRankText(List<String> texts, String keyword, List<String> allDocs) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(keyword)) return i + 1;
        }
        return -1;
    }
}
