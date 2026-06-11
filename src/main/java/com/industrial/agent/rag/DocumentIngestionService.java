package com.industrial.agent.rag;

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

import java.util.List;

import static dev.langchain4j.data.document.Metadata.metadata;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Ingest knowledge entries into Milvus with configurable chunk size.
     */
    public int ingest(List<String> knowledgeEntries, int maxSegmentChars) {
        DocumentSplitter splitter = DocumentSplitters.recursive(maxSegmentChars, 50);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(splitter)
                .build();

        for (int i = 0; i < knowledgeEntries.size(); i++) {
            Document doc = Document.from(knowledgeEntries.get(i),
                    metadata("id", String.valueOf(i)));
            ingestor.ingest(doc);
        }

        log.info("[RAG] Ingested {} knowledge entries (chunk={} chars)", knowledgeEntries.size(), maxSegmentChars);
        return knowledgeEntries.size();
    }

    /**
     * Ingest with default chunk size (500 chars).
     */
    public int ingest(List<String> knowledgeEntries) {
        return ingest(knowledgeEntries, 500);
    }
}
