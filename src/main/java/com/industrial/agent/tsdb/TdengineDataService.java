package com.industrial.agent.tsdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * TDEngine time-series data service via REST API.
 * Schema: CREATE STABLE devices (ts timestamp, temp float, vibration float,
 *   pressure float, rpm float, current float) TAGS (device_id binary(64),
 *   device_type binary(32), factory binary(64))
 */
@Slf4j
@Service
public class TdengineDataService {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    public TdengineDataService(
            @Value("${tdengine.url:http://localhost:6041}") String baseUrl,
            ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.json = json;
    }

    /** Initialize schema and super table. */
    public void initSchema() {
        execute("CREATE DATABASE IF NOT EXISTS industrial KEEP 365 DURATION 10 BUFFER 16");
        execute("""
                CREATE STABLE IF NOT EXISTS industrial.devices
                (ts TIMESTAMP, temp FLOAT, vibration FLOAT, pressure FLOAT,
                 rpm FLOAT, current FLOAT)
                TAGS (device_id BINARY(64), device_type BINARY(32), factory BINARY(64))
                """);
        log.info("[TDEngine] Schema initialized.");
    }

    /** Insert a data point. */
    public void insert(String deviceId, String deviceType, String factory,
                        double temp, double vibration, double pressure,
                        double rpm, double current) {
        long ts = System.currentTimeMillis();
        String sql = String.format(
                "INSERT INTO industrial.d_%s USING industrial.devices " +
                "TAGS ('%s', '%s', '%s') VALUES (%d, %.1f, %.2f, %.2f, %.0f, %.1f)",
                deviceId.replace("-", "_"), deviceId, deviceType, factory,
                ts, temp, vibration, pressure, rpm, current);
        execute(sql);
    }

    /** Batch insert data points. */
    public void batchInsert(List<DataPoint> points) {
        for (DataPoint p : points) {
            insert(p.deviceId, p.deviceType, p.factory,
                    p.temp, p.vibration, p.pressure, p.rpm, p.current);
        }
    }

    /** Query latest metrics for a device. */
    public Map<String, Object> queryLatest(String deviceId) {
        String table = "industrial.d_" + deviceId.replace("-", "_");
        String sql = String.format(
                "SELECT ts, temp, vibration, pressure, rpm, current " +
                "FROM %s ORDER BY ts DESC LIMIT 1", table);
        return querySingle(sql);
    }

    /** Query metrics in a time window. */
    public List<Map<String, Object>> queryHistory(String deviceId, int minutes) {
        long ago = System.currentTimeMillis() - minutes * 60_000L;
        String table = "industrial.d_" + deviceId.replace("-", "_");
        String sql = String.format(
                "SELECT ts, temp, vibration, pressure, rpm, current " +
                "FROM %s WHERE ts >= %d ORDER BY ts DESC LIMIT 100", table, ago);
        return queryList(sql);
    }

    /** Get aggregated stats (avg, max, min) for a device in a time window. */
    public Map<String, Object> queryStats(String deviceId, int minutes) {
        long ago = System.currentTimeMillis() - minutes * 60_000L;
        String table = "industrial.d_" + deviceId.replace("-", "_");
        String sql = String.format(
                "SELECT AVG(temp) as avg_temp, MAX(temp) as max_temp, " +
                "AVG(vibration) as avg_vib, MAX(vibration) as max_vib, " +
                "AVG(pressure) as avg_pressure, MAX(pressure) as max_pressure, " +
                "AVG(rpm) as avg_rpm, AVG(current) as avg_current " +
                "FROM %s WHERE ts >= %d", table, ago);
        return querySingle(sql);
    }

    private String execute(String sql) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/sql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sql))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("[TDEngine] SQL failed: {}", e.getMessage());
        }
        return "";
    }

    private Map<String, Object> querySingle(String sql) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/sql"))
                    .POST(HttpRequest.BodyPublishers.ofString(sql))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(resp.body());
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0 && data.get(0).isArray()) {
                Map<String, Object> row = new LinkedHashMap<>();
                JsonNode cols = root.path("column_meta");
                JsonNode vals = data.get(0);
                for (int i = 0; i < cols.size() && i < vals.size(); i++) {
                    row.put(cols.get(i).get(0).asText(), vals.get(i).asText());
                }
                return row;
            }
        } catch (Exception e) {
            log.warn("[TDEngine] Query failed: {}", e.getMessage());
        }
        return Map.of();
    }

    private List<Map<String, Object>> queryList(String sql) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/sql"))
                    .POST(HttpRequest.BodyPublishers.ofString(sql))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(resp.body());
            List<Map<String, Object>> rows = new ArrayList<>();
            JsonNode cols = root.path("column_meta");
            for (JsonNode row : root.path("data")) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < cols.size() && i < row.size(); i++) {
                    m.put(cols.get(i).get(0).asText(), row.get(i).asText());
                }
                rows.add(m);
            }
            return rows;
        } catch (Exception e) {
            log.warn("[TDEngine] Query failed: {}", e.getMessage());
        }
        return List.of();
    }

    public record DataPoint(String deviceId, String deviceType, String factory,
                             double temp, double vibration, double pressure,
                             double rpm, double current) {}
}
