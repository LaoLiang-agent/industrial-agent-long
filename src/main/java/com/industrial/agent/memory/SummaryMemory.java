package com.industrial.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * L3 Summary Memory — structured, durable summaries in PostgreSQL. After each
 * turn a small-model call distills the conversation into
 * {user_goal, confirmed_facts, pending_actions, constraints}. Generation runs
 * off the main request path via {@code @Async}.
 */
@Slf4j
@Component
public class SummaryMemory {

    private final JdbcTemplate jdbc;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper mapper;

    public SummaryMemory(@Qualifier("memoryJdbcTemplate") JdbcTemplate memoryJdbcTemplate,
                         OpenAiChatModel chatModel, ObjectMapper mapper) {
        this.jdbc = memoryJdbcTemplate;
        this.chatModel = chatModel;
        this.mapper = mapper;
    }

    @PostConstruct
    void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS conversation_summaries (
                    id             BIGSERIAL PRIMARY KEY,
                    session_id     VARCHAR(128) NOT NULL,
                    turn_number    INT          NOT NULL,
                    user_goal      TEXT,
                    confirmed_facts TEXT,
                    pending_actions TEXT,
                    constraints    TEXT,
                    created_at     TIMESTAMP    NOT NULL DEFAULT now()
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_summaries_session ON conversation_summaries(session_id, turn_number DESC)");
    }

    public void store(ConversationSummary s) {
        jdbc.update("""
                INSERT INTO conversation_summaries
                    (session_id, turn_number, user_goal, confirmed_facts, pending_actions, constraints)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                s.sessionId(), s.turnNumber(), s.userGoal(), s.confirmedFacts(),
                s.pendingActions(), s.constraints());
    }

    public Optional<ConversationSummary> latest(String sessionId) {
        List<ConversationSummary> rows = jdbc.query("""
                SELECT session_id, turn_number, user_goal, confirmed_facts, pending_actions, constraints
                FROM conversation_summaries
                WHERE session_id = ?
                ORDER BY turn_number DESC
                LIMIT 1
                """,
                (rs, i) -> new ConversationSummary(
                        rs.getString("session_id"), rs.getInt("turn_number"),
                        rs.getString("user_goal"), rs.getString("confirmed_facts"),
                        rs.getString("pending_actions"), rs.getString("constraints")),
                sessionId);
        return rows.stream().findFirst();
    }

    /**
     * Asynchronously distill the given turns into a structured summary and store it.
     * Failures are swallowed (logged) — summary generation must never break the
     * user-facing request.
     */
    @Async
    public void generateAndStore(String sessionId, int turnNumber, List<ConversationTurn> turns) {
        try {
            String transcript = turns.stream()
                    .map(t -> t.role() + ": " + t.content())
                    .collect(Collectors.joining("\n"));
            String prompt = """
                    你是对话摘要器。请把下面的多轮对话压缩为结构化 JSON，仅输出 JSON，不要解释。
                    字段：user_goal（用户目标）、confirmed_facts（已确认事实）、
                    pending_actions（待办动作）、constraints（约束/限制）。
                    若某字段无内容，填空字符串。

                    对话：
                    %s
                    """.formatted(transcript);
            String raw = chatModel.chat(prompt);
            JsonNode node = mapper.readTree(extractJson(raw));
            store(new ConversationSummary(
                    sessionId, turnNumber,
                    node.path("user_goal").asText(""),
                    node.path("confirmed_facts").asText(""),
                    node.path("pending_actions").asText(""),
                    node.path("constraints").asText("")));
            log.info("[L3] stored summary session={} turn={}", sessionId, turnNumber);
        } catch (Exception e) {
            log.warn("[L3] summary generation failed session={} turn={}: {}", sessionId, turnNumber, e.getMessage());
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : "{}";
    }
}
