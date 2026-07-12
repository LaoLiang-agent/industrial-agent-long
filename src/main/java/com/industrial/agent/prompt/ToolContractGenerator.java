package com.industrial.agent.prompt;

import com.industrial.agent.tool.SideEffect;
import com.industrial.agent.tool.ToolMeta;
import com.industrial.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * L3 Tool Contract generator. Renders the governance metadata already declared
 * in {@link ToolRegistry} (side effect, per-request call limit, approval
 * requirement) into explicit constraint text for the system prompt — so limits
 * like "WRITE tools at most once" are stated to the model rather than guessed.
 *
 * <p>Parameter-level JSON Schema is intentionally out of scope: LangChain4j
 * derives that from the {@code @Tool} annotations and passes it in the function
 * calling payload. This layer conveys the governance envelope only.
 */
@Component
public class ToolContractGenerator {

    private final ToolRegistry registry;

    public ToolContractGenerator(ToolRegistry registry) {
        this.registry = registry;
    }

    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (ToolMeta m : registry.allTools()) {
            sb.append("- ").append(m.name())
              .append("（").append(m.sideEffect() == SideEffect.WRITE ? "写操作" : "读操作").append("，")
              .append("单轮最多 ").append(m.maxCallsPerRequest()).append(" 次");
            if (m.requiresApproval()) {
                sb.append("，需人工审批");
            }
            sb.append("）\n");
        }
        return sb.toString();
    }
}
