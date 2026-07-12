package com.industrial.agent.prompt;

import com.industrial.agent.config.AgentPromptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * L2 Policy registry. Holds the safety/compliance rules that get compiled into
 * the system prompt as explicit constraints, rather than left for the model to
 * infer. Backed by {@code agent.prompt.policies}; {@link #reload()} re-reads the
 * bound properties so policies can change without recompiling the prompt engine.
 */
@Slf4j
@Component
public class PolicyRegistry {

    private final AgentPromptProperties properties;
    private final List<String> policies = new CopyOnWriteArrayList<>();

    public PolicyRegistry(AgentPromptProperties properties) {
        this.properties = properties;
        reload();
    }

    public List<String> policies() {
        return List.copyOf(policies);
    }

    /** Re-read policies from the bound configuration. */
    public void reload() {
        policies.clear();
        policies.addAll(properties.getPolicies());
        log.info("[Prompt] PolicyRegistry loaded {} policies", policies.size());
    }
}
