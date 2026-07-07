package com.industrial.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrial.agent.config.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * L1 Working Memory — volatile mid-task state (current goal, partial results,
 * pending tool calls). Backed by Redis with a short TTL so abandoned tasks
 * self-expire. Key: {@code wm:{sessionId}:{taskId}}.
 */
@Slf4j
@Component
public class WorkingMemory {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public WorkingMemory(StringRedisTemplate redis, ObjectMapper mapper, MemoryProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(props.getWorkingTtlSeconds());
    }

    private String key(String sessionId, String taskId) {
        return "wm:" + sessionId + ":" + taskId;
    }

    /** Overwrite the working state for a task; refreshes the TTL. */
    public void put(String sessionId, String taskId, Map<String, Object> state) {
        try {
            redis.opsForValue().set(key(sessionId, taskId), mapper.writeValueAsString(state), ttl);
        } catch (Exception e) {
            log.warn("[L1] failed to write working memory {}:{}: {}", sessionId, taskId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String sessionId, String taskId) {
        try {
            String json = redis.opsForValue().get(key(sessionId, taskId));
            if (json == null) return Map.of();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[L1] failed to read working memory {}:{}: {}", sessionId, taskId, e.getMessage());
            return Map.of();
        }
    }

    public void clear(String sessionId, String taskId) {
        redis.delete(key(sessionId, taskId));
    }
}
