package com.industrial.agent.tsdb;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * TDEngine time-series data service via WebSocket JDBC connection.
 * Schema: CREATE STABLE devices (ts timestamp, temp float, vibration float,
 *   pressure float, rpm float, current float) TAGS (device_id binary(64),
 *   device_type binary(32), factory binary(64))
 */
@Slf4j
@Service
public class TdengineDataService {

    private final String jdbcUrl;
    private Connection conn;

    public TdengineDataService(
            @Value("${tdengine.jdbc-url:jdbc:TAOS-WS://localhost:6041/industrial}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @PostConstruct
    public void connect() {
        try {
            conn = DriverManager.getConnection(jdbcUrl, "root", "taosdata");
            log.info("[TDEngine] WebSocket connected: {}", jdbcUrl);
            initSchema();
        } catch (SQLException e) {
            log.warn("[TDEngine] WebSocket connect failed (TDEngine not running?): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private void ensureConnected() {
        try {
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(jdbcUrl, "root", "taosdata");
            }
        } catch (SQLException e) {
            log.warn("[TDEngine] Reconnect failed: {}", e.getMessage());
        }
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
        ensureConnected();
        long ts = System.currentTimeMillis();
        String table = "industrial.d_" + deviceId.replace("-", "_");
        String sql = String.format(
                "INSERT INTO %s USING industrial.devices " +
                "TAGS ('%s', '%s', '%s') VALUES (%d, %.1f, %.2f, %.2f, %.0f, %.1f)",
                table, deviceId, deviceType, factory,
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

    /** Get aggregated stats (avg, max) for a device in a time window. */
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

    private void execute(String sql) {
        ensureConnected();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.warn("[TDEngine] SQL failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> querySingle(String sql) {
        ensureConnected();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            }
        } catch (SQLException e) {
            log.warn("[TDEngine] Query failed: {}", e.getMessage());
        }
        return Map.of();
    }

    private List<Map<String, Object>> queryList(String sql) {
        ensureConnected();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    m.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(m);
            }
            return rows;
        } catch (SQLException e) {
            log.warn("[TDEngine] Query failed: {}", e.getMessage());
        }
        return List.of();
    }

    public record DataPoint(String deviceId, String deviceType, String factory,
                             double temp, double vibration, double pressure,
                             double rpm, double current) {}
}
