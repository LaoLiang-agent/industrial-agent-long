package com.industrial.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** Query rewrite strategy: HYDE, MULTI_QUERY, or NONE */
    private RewriteStrategy rewriteStrategy = RewriteStrategy.HYDE;

    private Rerank rerank = new Rerank();

    private int denseTopK = 10;
    private int sparseTopK = 10;
    private int fusionTopK = 5;
    private int finalTopK = 3;
    private double minScore = 0.3;

    @Data
    public static class Rerank {
        private boolean enabled = true;
    }

    public enum RewriteStrategy {
        HYDE, MULTI_QUERY, NONE
    }
}
