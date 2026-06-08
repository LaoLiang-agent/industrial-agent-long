package com.industrial.agent.llm;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TemperatureExperiment {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    private final TokenCostTracker costTracker;

    private static final String PROMPT = """
            你是一个工业设备诊断专家。请根据以下信息进行诊断：
            设备 CNC-001 当前振动值为 4.8mm/s（正常范围 0-2.8mm/s），
            轴承温度为 72°C（正常范围 40-65°C）。
            请给出：1) 可能的故障原因 2) 建议的维修措施 3) 是否需要立即停机。
            """;

    private static final double[] TEMPERATURES = {0.0, 0.1, 0.3, 0.7, 1.0};
    private static final int RUNS_PER_TEMP = 10;

    public TemperatureExperiment(TokenCostTracker costTracker) {
        this.costTracker = costTracker;
    }

    public record TempResult(double temperature, List<String> responses,
                              double consistencyScore, long totalTokens) {}

    public Map<Double, TempResult> run() {
        Map<Double, TempResult> results = new LinkedHashMap<>();

        for (double temp : TEMPERATURES) {
            log.info("[TempExperiment] temp={}, {} runs...", temp, RUNS_PER_TEMP);

            OpenAiChatModel model = OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(temp)
                    .maxTokens(1024)
                    .logRequests(false)
                    .logResponses(false)
                    .build();

            List<String> responses = new ArrayList<>();
            List<Set<String>> keywordsPerResponse = new ArrayList<>();
            long totalTokens = 0;

            for (int i = 0; i < RUNS_PER_TEMP; i++) {
                String reply = model.generate(PROMPT);
                responses.add(reply);
                totalTokens += costTracker.countInputTokens(PROMPT) + costTracker.countOutputTokens(reply);
                keywordsPerResponse.add(extractKeywords(reply));
            }

            double consistency = calculateConsistency(keywordsPerResponse);
            results.put(temp, new TempResult(temp, responses, consistency, totalTokens));
            log.info("[TempExperiment] temp={}: consistency={:.2f}, tokens={}", temp, consistency, totalTokens);
        }
        return results;
    }

    private Set<String> extractKeywords(String response) {
        Set<String> keywords = new HashSet<>();
        String[] patterns = {"轴承磨损", "转子不平衡", "联轴器对中", "润滑不足",
                "立即停机", "继续运行", "减负荷运行", "检查轴承", "更换轴承",
                "振动频谱分析", "对中校验"};
        for (String p : patterns) {
            if (response.contains(p)) keywords.add(p);
        }
        return keywords;
    }

    private double calculateConsistency(List<Set<String>> keywordSets) {
        if (keywordSets.size() < 2) return 1.0;
        double total = 0;
        int pairs = 0;
        for (int i = 0; i < keywordSets.size(); i++) {
            for (int j = i + 1; j < keywordSets.size(); j++) {
                Set<String> a = keywordSets.get(i);
                Set<String> b = keywordSets.get(j);
                if (a.isEmpty() && b.isEmpty()) { total += 1.0; }
                else if (!a.isEmpty() && !b.isEmpty()) {
                    Set<String> union = new HashSet<>(a); union.addAll(b);
                    Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
                    total += (double) intersection.size() / union.size();
                }
                pairs++;
            }
        }
        return pairs > 0 ? total / pairs : 0.0;
    }
}
