package com.industrial.agent.rag.advanced;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple in-memory BM25 (TF-IDF variant) sparse retriever.
 * Indexes documents and retrieves top-k by keyword relevance.
 */
@Slf4j
@Component
public class Bm25Retriever {

    private final Map<Integer, String> docIndex = new HashMap<>();
    private final Map<String, Map<Integer, Double>> invertedIndex = new HashMap<>();
    private final Map<Integer, Integer> docLengths = new HashMap<>();
    private double avgDocLength = 0;

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public void index(List<String> documents) {
        docIndex.clear(); invertedIndex.clear(); docLengths.clear();
        int totalLen = 0;

        for (int i = 0; i < documents.size(); i++) {
            String doc = documents.get(i);
            docIndex.put(i, doc);
            String[] words = tokenize(doc);
            docLengths.put(i, words.length);
            totalLen += words.length;

            for (String word : words) {
                invertedIndex.computeIfAbsent(word, k -> new HashMap<>())
                        .merge(i, 1.0, Double::sum);
            }
        }

        avgDocLength = documents.isEmpty() ? 0 : (double) totalLen / documents.size();
        log.info("[BM25] Indexed {} docs, {} terms, avgLen={:.0f}",
                documents.size(), invertedIndex.size(), avgDocLength);
    }

    public List<ScoredDoc> search(String query, int topK) {
        String[] queryTerms = tokenize(query);
        int totalDocs = docIndex.size();

        Map<Integer, Double> scores = new HashMap<>();
        for (String term : queryTerms) {
            Map<Integer, Double> postings = invertedIndex.getOrDefault(term, Map.of());
            int df = postings.size();
            double idf = Math.log(1 + (totalDocs - df + 0.5) / (df + 0.5));

            for (var entry : postings.entrySet()) {
                int docId = entry.getKey();
                double tf = entry.getValue();
                int len = docLengths.get(docId);
                double score = idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * len / avgDocLength));
                scores.merge(docId, score, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new ScoredDoc(e.getKey(), docIndex.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    private String[] tokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-z0-9]", " ")
                .trim()
                .split("\\s+");
    }

    public record ScoredDoc(int id, String text, double score) {}
}
