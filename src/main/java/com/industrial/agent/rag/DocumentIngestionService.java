package com.industrial.agent.rag;

import com.industrial.agent.rag.advanced.Bm25Retriever;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.document.Metadata.metadata;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Bm25Retriever bm25Retriever;

    /**
     * Ingest knowledge entries into Milvus with tenant/time metadata,
     * then rebuild the BM25 index.
     */
    public int ingest(List<String> knowledgeEntries, int maxSegmentChars) {
        DocumentSplitter splitter = DocumentSplitters.recursive(maxSegmentChars, 50);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(splitter)
                .build();

        List<String> allChunks = new ArrayList<>();
        long now = Instant.now().getEpochSecond();
        long expires = Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS).getEpochSecond();

        for (int i = 0; i < knowledgeEntries.size(); i++) {
            Document doc = Document.from(knowledgeEntries.get(i),
                    metadata("id", String.valueOf(i))
                            .put("tenant_id", "default")
                            .put("effective_time", String.valueOf(now))
                            .put("expire_time", String.valueOf(expires)));
            ingestor.ingest(doc);
            allChunks.add(knowledgeEntries.get(i));
        }

        bm25Retriever.rebuild(allChunks);

        log.info("[RAG] Ingested {} entries (chunk={} chars, tenant=default), BM25 index={} docs",
                knowledgeEntries.size(), maxSegmentChars, bm25Retriever.size());
        return knowledgeEntries.size();
    }

    /**
     * Ingest with default chunk size (500 chars).
     */
    public int ingest(List<String> knowledgeEntries) {
        return ingest(knowledgeEntries, 500);
    }
}
