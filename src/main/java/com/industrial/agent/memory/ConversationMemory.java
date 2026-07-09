package com.industrial.agent.memory;

import com.industrial.agent.config.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L2 Conversation Memory — the last few raw turns, backed by a capped Redis
 * Stream ({@code conv:{sessionId}}, MAXLEN = conversationMaxEntries). Used for
 * immediate context; older turns roll off and survive only as L3 summaries.
 */
@Slf4j
@Component
public class ConversationMemory {

    private final StringRedisTemplate redis;
    private final int maxEntries;

    public ConversationMemory(StringRedisTemplate redis, MemoryProperties props) {
        this.redis = redis;
        this.maxEntries = props.getConversationMaxEntries();
    }

    private String key(String sessionId) {
        return "conv:" + sessionId;
    }

    public void append(String sessionId, String role, String content) {
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("role", role);
            fields.put("content", content);
            fields.put("timestamp", String.valueOf(System.currentTimeMillis()));
            MapRecord<String, String, String> record =
                    StreamRecords.mapBacked(fields).withStreamKey(key(sessionId));
            redis.opsForStream().add(record);
            redis.opsForStream().trim(key(sessionId), maxEntries);
        } catch (Exception e) {
            log.warn("[L2] failed to append conversation turn for {}: {}", sessionId, e.getMessage());
        }
    }

    /** Returns up to {@code maxEntries} most recent turns in chronological order. */
    public List<ConversationTurn> recent(String sessionId) {
        List<ConversationTurn> turns = new ArrayList<>();
        try {
            List<MapRecord<String, Object, Object>> records =
                    redis.opsForStream().range(key(sessionId), Range.unbounded());
            if (records == null) return turns;
            for (MapRecord<String, Object, Object> r : records) {
                Map<Object, Object> v = r.getValue();
                turns.add(new ConversationTurn(
                        String.valueOf(v.get("role")),
                        String.valueOf(v.get("content")),
                        parseTs(v.get("timestamp"))));
            }
        } catch (Exception e) {
            log.warn("[L2] failed to read conversation for {}: {}", sessionId, e.getMessage());
        }
        return turns;
    }

    private long parseTs(Object ts) {
        try {
            return Long.parseLong(String.valueOf(ts));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public void clear(String sessionId) {
        redis.delete(key(sessionId));
    }
}
