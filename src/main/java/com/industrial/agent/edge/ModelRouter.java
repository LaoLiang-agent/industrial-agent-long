package com.industrial.agent.edge;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ModelRouter {

    private final ChatModel edgeModel;
    private final ChatModel cloudModel;
    private final AtomicLong edgeHits = new AtomicLong();
    private final AtomicLong cloudFallbacks = new AtomicLong();
    private final AtomicLong edgeErrors = new AtomicLong();

    public ModelRouter(@Qualifier("edgeModel") ObjectProvider<ChatModel> edgeModelProvider, ChatModel cloudModel) {
        this.edgeModel = edgeModelProvider.getIfAvailable();
        this.cloudModel = cloudModel;
        if (this.edgeModel == null) {
            log.info("[ModelRouter] edgeModel not configured, all requests go directly to cloud");
        }
    }

    public RouterResult chat(String message) {
        long start = System.currentTimeMillis();

        if (edgeModel != null) {
            try {
                String reply = edgeModel.chat(message);
                long elapsed = System.currentTimeMillis() - start;

                if (isQualityAcceptable(reply)) {
                    edgeHits.incrementAndGet();
                    log.info("[ModelRouter] EDGE ({}ms): {}", elapsed, snippet(reply));
                    return new RouterResult("edge", reply, elapsed, false);
                }

                log.warn("[ModelRouter] Edge quality check failed, falling back to cloud");
            } catch (Exception e) {
                edgeErrors.incrementAndGet();
                log.warn("[ModelRouter] Edge failed ({}), falling back to cloud", e.getMessage());
            }
        }

        // Cloud (or cloud fallback)
        long cloudStart = System.currentTimeMillis();
        String reply = cloudModel.chat(message);
        long elapsed = System.currentTimeMillis() - cloudStart;
        cloudFallbacks.incrementAndGet();
        log.info("[ModelRouter] CLOUD ({}ms): {}", elapsed, snippet(reply));
        return new RouterResult("cloud", reply, elapsed, edgeModel != null);
    }

    private boolean isQualityAcceptable(String reply) {
        if (reply == null || reply.isBlank()) return false;
        if (reply.length() < 20) return false;
        // Reject obvious hallucination markers
        if (reply.contains("我无法") && reply.length() < 50) return false;
        return true;
    }

    private String snippet(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    public RouteStats getStats() {
        return new RouteStats(edgeHits.get(), cloudFallbacks.get(), edgeErrors.get());
    }

    public record RouterResult(String source, String reply, long latencyMs, boolean fallback) {}
    public record RouteStats(long edgeHits, long cloudFallbacks, long edgeErrors) {}
}
