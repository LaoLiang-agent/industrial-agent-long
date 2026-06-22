package com.industrial.agent.eval;

import com.industrial.agent.agent.router.Intent;
import com.industrial.agent.agent.router.RouterAgent;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AgentEvaluator {

    private final RouterAgent routerAgent;
    private final ChatModel chatModel;

    public AgentEvaluator(RouterAgent routerAgent, ChatModel chatModel) {
        this.routerAgent = routerAgent;
        this.chatModel = chatModel;
    }

    public EvalReport evaluate(List<EvalCase> cases) {
        log.info("[Eval] Starting evaluation with {} cases", cases.size());
        List<EvalResult> results = new ArrayList<>();

        for (EvalCase ec : cases) {
            try {
                EvalResult result = evaluateOne(ec);
                results.add(result);
                log.info("[Eval] {} {} intent={} keyword={:.0f}% relevance={}/5 {}ms",
                        result.intentCorrect() ? "PASS" : "FAIL",
                        ec.id(), result.actualIntent(),
                        result.keywordHitRate() * 100, result.relevanceScore(), result.latencyMs());
            } catch (Exception e) {
                log.warn("[Eval] {} ERROR: {}", ec.id(), e.getMessage());
                results.add(new EvalResult(ec.id(), ec.query(), ec.expectedIntent(),
                        Intent.GENERAL, false, 0, 0, 0, "ERROR: " + e.getMessage()));
            }
        }

        EvalReport report = EvalReport.from(results);
        log.info("[Eval] Done: accuracy={:.1f}% keyword={:.1f}% relevance={:.1f}/5 avgLatency={}ms",
                report.intentAccuracy() * 100, report.avgKeywordHitRate() * 100,
                report.avgRelevanceScore(), report.avgLatencyMs());
        return report;
    }

    private EvalResult evaluateOne(EvalCase ec) {
        long start = System.currentTimeMillis();
        RouterAgent.RouteResult route = routerAgent.route(ec.query());
        long elapsed = System.currentTimeMillis() - start;

        boolean intentCorrect = route.intent() == ec.expectedIntent();
        double keywordHit = calcKeywordHitRate(route.reply(), ec.expectedKeywords());
        int relevance = judgeRelevance(ec.query(), route.reply());
        String snippet = route.reply().length() > 100 ? route.reply().substring(0, 100) + "..." : route.reply();

        return new EvalResult(ec.id(), ec.query(), ec.expectedIntent(), route.intent(),
                intentCorrect, keywordHit, relevance, elapsed, snippet);
    }

    private double calcKeywordHitRate(String reply, List<String> keywords) {
        if (keywords.isEmpty()) return 1.0;
        long hits = keywords.stream().filter(reply::contains).count();
        return (double) hits / keywords.size();
    }

    private int judgeRelevance(String query, String reply) {
        try {
            String prompt = "评估以下AI回复对用户问题的相关性和质量，只返回1-5的整数评分。" +
                    "\n5=完美回答 4=良好 3=基本相关 2=部分相关 1=完全无关" +
                    "\n用户问题：" + query +
                    "\nAI回复：" + reply.substring(0, Math.min(reply.length(), 500)) +
                    "\n评分（只返回数字）：";
            String score = chatModel.chat(prompt).trim();
            return Integer.parseInt(score.replaceAll("[^1-5]", "").substring(0, 1));
        } catch (Exception e) {
            return 3;
        }
    }
}
