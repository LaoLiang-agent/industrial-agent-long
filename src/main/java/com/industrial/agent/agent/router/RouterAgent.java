package com.industrial.agent.agent.router;

import com.industrial.agent.agent.experts.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RouterAgent {

    private final IntentClassifier classifier;
    private final AlarmExpert alarmExpert;
    private final DataExpert dataExpert;
    private final DiagnosisExpert diagnosisExpert;
    private final KnowledgeExpert knowledgeExpert;
    private final GeneralExpert generalExpert;

    public RouterAgent(IntentClassifier classifier, AlarmExpert alarmExpert,
                       DataExpert dataExpert, DiagnosisExpert diagnosisExpert,
                       KnowledgeExpert knowledgeExpert, GeneralExpert generalExpert) {
        this.classifier = classifier;
        this.alarmExpert = alarmExpert;
        this.dataExpert = dataExpert;
        this.diagnosisExpert = diagnosisExpert;
        this.knowledgeExpert = knowledgeExpert;
        this.generalExpert = generalExpert;
    }

    public RouteResult route(String message) {
        long start = System.currentTimeMillis();
        Intent intent = classifier.classify(message);

        String reply = switch (intent) {
            case ALARM -> alarmExpert.chat(message);
            case DATA -> dataExpert.chat(message);
            case DIAGNOSIS -> diagnosisExpert.chat(message);
            case KNOWLEDGE -> knowledgeExpert.chat(message);
            case GENERAL -> generalExpert.chat(message);
        };

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Router] {} → {} ({}ms)", intent, reply.length() > 50 ? reply.substring(0, 50) + "..." : reply, elapsed);

        return new RouteResult(intent, reply, elapsed);
    }

    public Map<String, Long> getStats() {
        return classifier.getStats();
    }

    public record RouteResult(Intent intent, String reply, long latencyMs) {}
}
