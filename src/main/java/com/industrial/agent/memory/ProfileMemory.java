package com.industrial.agent.memory;

import com.industrial.agent.config.MemoryProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L4 Profile Memory — long-term facts about users and devices in PostgreSQL.
 * Guarded by a confidence gate to prevent memory pollution: a fact is written
 * only when it is explicitly stated (confidence 1.0) or computed above the
 * configured threshold. Every write records its source evidence.
 */
@Slf4j
@Component
public class ProfileMemory {

    private final JdbcTemplate jdbc;
    private final double threshold;

    public ProfileMemory(@Qualifier("memoryJdbcTemplate") JdbcTemplate memoryJdbcTemplate,
                         MemoryProperties props) {
        this.jdbc = memoryJdbcTemplate;
        this.threshold = props.getProfileConfidenceThreshold();
    }

    @PostConstruct
    void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS profiles (
                    id             BIGSERIAL PRIMARY KEY,
                    subject_type   VARCHAR(16)  NOT NULL,
                    subject_id     VARCHAR(128) NOT NULL,
                    attribute      VARCHAR(128) NOT NULL,
                    value          TEXT         NOT NULL,
                    confidence     DOUBLE PRECISION NOT NULL,
                    source_evidence TEXT,
                    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
                    UNIQUE (subject_type, subject_id, attribute)
                )
                """);
    }

    /**
     * Upsert a profile fact if it clears the confidence gate.
     * @return true if written, false if rejected by the gate.
     */
    public boolean write(ProfileEntry e) {
        if (e.confidence() < threshold) {
            log.info("[L4] rejected {}:{} {}={} (confidence {} < {})",
                    e.subjectType(), e.subjectId(), e.attribute(), e.value(), e.confidence(), threshold);
            return false;
        }
        jdbc.update("""
                INSERT INTO profiles (subject_type, subject_id, attribute, value, confidence, source_evidence)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (subject_type, subject_id, attribute)
                DO UPDATE SET value = EXCLUDED.value,
                              confidence = EXCLUDED.confidence,
                              source_evidence = EXCLUDED.source_evidence,
                              updated_at = now()
                """,
                e.subjectType(), e.subjectId(), e.attribute(), e.value(),
                e.confidence(), e.sourceEvidence());
        log.info("[L4] wrote {}:{} {}={}", e.subjectType(), e.subjectId(), e.attribute(), e.value());
        return true;
    }

    public List<ProfileEntry> forSubject(String subjectType, String subjectId) {
        return jdbc.query("""
                SELECT subject_type, subject_id, attribute, value, confidence, source_evidence
                FROM profiles
                WHERE subject_type = ? AND subject_id = ?
                ORDER BY attribute
                """,
                (rs, i) -> new ProfileEntry(
                        rs.getString("subject_type"), rs.getString("subject_id"),
                        rs.getString("attribute"), rs.getString("value"),
                        rs.getDouble("confidence"), rs.getString("source_evidence")),
                subjectType, subjectId);
    }
}
