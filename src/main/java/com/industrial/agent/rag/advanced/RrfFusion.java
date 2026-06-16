package com.industrial.agent.rag.advanced;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion — combines dense (vector) and sparse (BM25) results.
 * RRF formula: score(d) = sum over rankings of 1 / (k + rank(d))
 */
@Slf4j
@Component
public class RrfFusion {

    private static final double K = 60.0;

    /**
     * Fuse dense and sparse results using RRF.
     * @param denseResults  vector search results (already sorted by score)
     * @param sparseResults BM25 search results (already sorted by score)
     * @param topK          number of fused results to return
     */
    public List<String> fuse(
            List<EmbeddingMatch<TextSegment>> denseResults,
            List<Bm25Retriever.ScoredDoc> sparseResults,
            int topK) {

        Map<String, Double> scores = new LinkedHashMap<>();

        // Dense contribution
        for (int i = 0; i < denseResults.size(); i++) {
            String text = denseResults.get(i).embedded().text();
            scores.merge(text, 1.0 / (K + i + 1), Double::sum);
        }

        // Sparse contribution
        for (int i = 0; i < sparseResults.size(); i++) {
            String text = sparseResults.get(i).text();
            scores.merge(text, 1.0 / (K + i + 1), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
