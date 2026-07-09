package com.industrial.agent.memory;

import com.industrial.agent.runtime.RuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the four memory layers.
 *
 * <p>Read path (synchronous): merges L4 profile + L3 latest summary + L2 recent
 * turns into a single context block for prompt assembly.
 *
 * <p>Write path: L1/L2 are written synchronously on the main request path; L3
 * (summary) and L4 (profile) are written off-path — L3 via {@link SummaryMemory}'s
 * {@code @Async} generation, L4 on demand behind the confidence gate.
 */
@Slf4j
@Component
public class MemoryManager {

    private final WorkingMemory working;
    private final ConversationMemory conversation;
    private final SummaryMemory summary;
    private final ProfileMemory profile;

    public MemoryManager(WorkingMemory working, ConversationMemory conversation,
                         SummaryMemory summary, ProfileMemory profile) {
        this.working = working;
        this.conversation = conversation;
        this.summary = summary;
        this.profile = profile;
    }

    /** Assemble the merged memory context (L4 + L3 + L2) for prompt injection. */
    public String buildContextBlock(RuntimeContext ctx) {
        StringBuilder sb = new StringBuilder();

        List<ProfileEntry> userFacts = profile.forSubject("user", ctx.getUserId());
        if (!userFacts.isEmpty()) {
            sb.append("【用户画像】\n");
            for (ProfileEntry e : userFacts) {
                sb.append("- ").append(e.attribute()).append(": ").append(e.value()).append('\n');
            }
        }

        summary.latest(ctx.getSessionId()).ifPresent(s -> {
            sb.append("【历史摘要】\n");
            if (!s.userGoal().isBlank())       sb.append("- 用户目标: ").append(s.userGoal()).append('\n');
            if (!s.confirmedFacts().isBlank())  sb.append("- 已确认事实: ").append(s.confirmedFacts()).append('\n');
            if (!s.pendingActions().isBlank())  sb.append("- 待办动作: ").append(s.pendingActions()).append('\n');
            if (!s.constraints().isBlank())     sb.append("- 约束: ").append(s.constraints()).append('\n');
        });

        List<ConversationTurn> recent = conversation.recent(ctx.getSessionId());
        if (!recent.isEmpty()) {
            sb.append("【最近对话】\n");
            for (ConversationTurn t : recent) {
                sb.append(t.role()).append(": ").append(t.content()).append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Record one completed turn: append user + assistant to L2 (sync), then
     * kick off async L3 summary regeneration over the recent window.
     */
    public void recordTurn(RuntimeContext ctx, String userMessage, String assistantReply) {
        String sessionId = ctx.getSessionId();
        conversation.append(sessionId, "user", userMessage);
        conversation.append(sessionId, "assistant", assistantReply);

        int nextTurn = summary.latest(sessionId).map(s -> s.turnNumber() + 1).orElse(1);
        summary.generateAndStore(sessionId, nextTurn, conversation.recent(sessionId));
    }

    // L1 working state
    public void putWorkingState(RuntimeContext ctx, Map<String, Object> state) {
        working.put(ctx.getSessionId(), ctx.getTraceId(), state);
    }

    public Map<String, Object> getWorkingState(RuntimeContext ctx) {
        return working.get(ctx.getSessionId(), ctx.getTraceId());
    }

    // L4 profile write (behind confidence gate)
    public boolean writeProfile(ProfileEntry entry) {
        return profile.write(entry);
    }

    // Inspection support
    public List<ConversationTurn> recentTurns(String sessionId) {
        return conversation.recent(sessionId);
    }

    public SummaryMemory summaryMemory() { return summary; }
    public ProfileMemory profileMemory() { return profile; }
}
