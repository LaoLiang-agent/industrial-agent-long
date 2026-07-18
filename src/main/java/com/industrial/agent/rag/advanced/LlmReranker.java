package com.industrial.agent.rag.advanced;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * LLM-based lightweight reranker — scores candidate documents for relevance to a query.
 * Uses the existing DeepSeek LLM, no extra model server needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmReranker {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Rerank candidates by LLM-judged relevance to the query.
     * Batches all candidates into one LLM call.
     */
    public List<String> rerank(String query, List<String> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() <= topK) {
            return new ArrayList<>(candidates);
        }

        try {
            String scores = callLlm(query, candidates);
            List<Integer> parsed = parseScores(scores, candidates.size());
            return reorder(candidates, parsed, topK);
        } catch (Exception e) {
            log.warn("[Reranker] LLM rerank failed, returning top-K by original order: {}", e.getMessage());
            return candidates.size() > topK ? candidates.subList(0, topK) : candidates;
        }
    }

    private String callLlm(String query, List<String> candidates) {
        var sb = new StringBuilder();
        sb.append("Rate the relevance of each document to the query on a scale of 0-10.\n");
        sb.append("Query: ").append(query).append("\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            String doc = candidates.get(i).length() > 300
                    ? candidates.get(i).substring(0, 300) + "..."
                    : candidates.get(i);
            sb.append("[").append(i).append("] ").append(doc).append("\n");
        }
        sb.append("\nReturn ONLY a JSON array of scores, e.g. [8,3,6]. No other text.");
        return chatModel.chat(sb.toString());
    }

    private List<Integer> parseScores(String llmOutput, int expectedSize) {
        String json = llmOutput.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            List<Integer> scores = objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
            while (scores.size() < expectedSize) {
                scores.add(5);
            }
            if (scores.size() > expectedSize) {
                scores = scores.subList(0, expectedSize);
            }
            return scores;
        } catch (Exception e) {
            log.warn("[Reranker] Failed to parse LLM scores: {}", e.getMessage());
            List<Integer> defaults = new java.util.ArrayList<>();
            for (int i = 0; i < expectedSize; i++) defaults.add(5);
            return defaults;
        }
    }

    private List<String> reorder(List<String> candidates, List<Integer> scores, int topK) {
        record IndexedScore(int index, int score) {}
        List<IndexedScore> indexed = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            indexed.add(new IndexedScore(i, scores.get(i)));
        }
        indexed.sort(Comparator.comparingInt(IndexedScore::score).reversed());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, indexed.size()); i++) {
            result.add(candidates.get(indexed.get(i).index()));
        }
        log.info("[Reranker] Reranked {} candidates → top {}", candidates.size(), result.size());
        return result;
    }
}
