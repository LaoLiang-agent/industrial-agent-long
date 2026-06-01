package com.industrial.agent.simulator;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulates industrial device data publishing via MQTT.
 * In production, this would be replaced by real device gateways.
 */
@Slf4j
@Component
public class DeviceSimulator {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MqttClient client;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            client = new MqttClient(brokerUrl, clientId + "-simulator", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.connect(options);
            log.info("[Simulator] Connected to MQTT broker at {}", brokerUrl);

            // Publish device data every 5 seconds
            scheduler.scheduleAtFixedRate(this::publishDeviceData, 0, 5, TimeUnit.SECONDS);
        } catch (MqttException e) {
            log.warn("[Simulator] MQTT broker not available — running without device simulation. Error: {}",
                    e.getMessage());
        }
    }

    private void publishDeviceData() {
        String[] devices = {"CNC-001", "CNC-002", "ROBOT-ARM-A1"};
        for (String device : devices) {
            try {
                String topic = String.format("industrial/devices/%s/data", device);
                String payload = generatePayload(device);
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                client.publish(topic, message);
            } catch (MqttException e) {
                log.warn("[Simulator] Publish failed for {}: {}", device, e.getMessage());
            }
        }
    }

    private String generatePayload(String deviceId) {
        double temp = 45 + random.nextDouble() * 40;
        double vibration = 0.5 + random.nextDouble() * 5.0;
        double pressure = 0.3 + random.nextDouble() * 1.2;
        double rpm = 800 + random.nextDouble() * 1200;
        double current = 10 + random.nextDouble() * 30;

        return String.format(
                "{\"deviceId\":\"%s\",\"ts\":%d,\"temp\":%.1f,\"vibration\":%.2f,\"pressure\":%.2f,\"rpm\":%.0f,\"current\":%.1f}",
                deviceId, System.currentTimeMillis(), temp, vibration, pressure, rpm, current
        );
    }
}
