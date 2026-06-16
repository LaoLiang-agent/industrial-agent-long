package com.industrial.agent.tsdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Subscribes to MQTT device telemetry topics and batch-writes to TDEngine.
 * MQTT topics: industrial/devices/+/data
 * Payload: {"deviceId":"CNC-001","temp":72.5,"vibration":4.8,...}
 */
@Slf4j
@Component
public class MqttToTdengineBridge {

    private final TdengineDataService tdengine;
    private final ObjectMapper json;
    private final String brokerUrl;
    private final String clientId;
    private final String topicPattern;
    private final ConcurrentLinkedQueue<TdengineDataService.DataPoint> buffer = new ConcurrentLinkedQueue<>();
    private MqttClient client;

    public MqttToTdengineBridge(TdengineDataService tdengine, ObjectMapper json,
            @Value("${mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
            @Value("${mqtt.client-id:industrial-agent-long}") String clientId,
            @Value("${mqtt.topic-device-data:industrial/devices/+/data}") String topicPattern) {
        this.tdengine = tdengine;
        this.json = json;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId + "-bridge";
        this.topicPattern = topicPattern;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(brokerUrl, clientId);
            client.connect();
            client.subscribe(topicPattern, this::onMessage);
            log.info("[MQTT→TD] Bridge started, subscribing to {}", topicPattern);
        } catch (Exception e) {
            log.warn("[MQTT→TD] Bridge start failed (MQTT broker not running?): {}", e.getMessage());
            return;
        }

        // Batch flush every 5 seconds
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tdengine-flush");
            t.setDaemon(true);
            return t;
        }).scheduleWithFixedDelay(this::flush, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        flush();
        if (client != null && client.isConnected()) {
            try { client.disconnect(); } catch (Exception ignored) {}
        }
    }

    private void onMessage(String topic, MqttMessage msg) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = json.readValue(msg.getPayload(), Map.class);
            String deviceId = (String) data.getOrDefault("deviceId", "unknown");
            String deviceType = (String) data.getOrDefault("deviceType", "machine");
            String factory = (String) data.getOrDefault("factory", "factory-A");

            double temp = toDouble(data.get("temp"));
            double vibration = toDouble(data.get("vibration"));
            double pressure = toDouble(data.get("pressure"));
            double rpm = toDouble(data.get("rpm"));
            double current = toDouble(data.get("current"));

            buffer.add(new TdengineDataService.DataPoint(
                    deviceId, deviceType, factory, temp, vibration, pressure, rpm, current));
        } catch (Exception e) {
            log.warn("[MQTT→TD] Parse failed: {}", e.getMessage());
        }
    }

    private void flush() {
        int count = 0;
        TdengineDataService.DataPoint p;
        while ((p = buffer.poll()) != null && count < 100) {
            tdengine.insert(p.deviceId(), p.deviceType(), p.factory(),
                    p.temp(), p.vibration(), p.pressure(), p.rpm(), p.current());
            count++;
        }
        if (count > 0) {
            log.info("[MQTT→TD] Flushed {} data points", count);
        }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return 0.0;
    }
}
