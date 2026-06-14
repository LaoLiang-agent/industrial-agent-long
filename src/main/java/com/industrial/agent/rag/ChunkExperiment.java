package com.industrial.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkExperiment {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentIngestionService ingestionService;

    private static final int[] CHUNK_SIZES = {200, 500, 1000, 2000};

    /**
     * Run hit-rate experiment across different chunk sizes.
     * Clears and re-ingests the knowledge base for each chunk size,
     * then runs test queries to measure retrieval quality.
     */
    public Map<Integer, Map<String, Object>> run(List<String> knowledgeBase,
                                                  List<String> testQueries,
                                                  List<String> expectedKeywords) {
        Map<Integer, Map<String, Object>> results = new LinkedHashMap<>();

        for (int chunkSize : CHUNK_SIZES) {
            log.info("[ChunkExp] Testing chunk size: {}", chunkSize);

            // Re-create collection and ingest with this chunk size
            ingestionService.ingest(knowledgeBase, chunkSize);

            // Run test queries and measure hit rate
            int hits = 0;
            for (int i = 0; i < testQueries.size(); i++) {
                Embedding qe = embeddingModel.embed(testQueries.get(i)).content();
                var res = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(qe).maxResults(3).minScore(0.4).build());

                for (EmbeddingMatch<TextSegment> match : res.matches()) {
                    if (match.embedded().text().contains(expectedKeywords.get(i))) {
                        hits++;
                        break;
                    }
                }
            }

            double hitRate = testQueries.size() > 0 ? hits * 100.0 / testQueries.size() : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chunkSize", chunkSize);
            m.put("hitRate", String.format("%.1f%%", hitRate));
            m.put("hits", hits);
            m.put("total", testQueries.size());
            results.put(chunkSize, m);
        }

        return results;
    }
}
