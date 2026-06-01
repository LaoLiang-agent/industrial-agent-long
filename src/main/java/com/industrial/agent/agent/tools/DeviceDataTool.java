package com.industrial.agent.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Slf4j
@Component
public class DeviceDataTool {

    @Tool("查询指定设备的历史遥测数据，包括温度、振动、压力、转速等关键指标。输入设备ID，返回最近1小时的关键指标数据。")
    public String queryDeviceHistory(String deviceId) {
        log.info("[DataTool] Querying history for device: {}", deviceId);

        Random random = new Random();
        double temperature = 45 + random.nextDouble() * 40;     // 45-85°C
        double vibration = 0.5 + random.nextDouble() * 4.5;     // 0.5-5.0 mm/s
        double pressure = 0.3 + random.nextDouble() * 1.2;      // 0.3-1.5 MPa
        double rpm = 800 + random.nextDouble() * 1200;          // 800-2000 rpm
        double current = 10 + random.nextDouble() * 30;          // 10-40 A

        return String.format(
                "{\"deviceId\":\"%s\",\"queryTime\":\"%s\",\"metrics\":{" +
                "\"temperature\":{\"value\":%.1f,\"unit\":\"°C\",\"normalRange\":\"40-65\"}," +
                "\"vibration\":{\"value\":%.2f,\"unit\":\"mm/s\",\"normalRange\":\"0-2.8\"}," +
                "\"pressure\":{\"value\":%.2f,\"unit\":\"MPa\",\"normalRange\":\"0.3-1.0\"}," +
                "\"rpm\":{\"value\":%.0f,\"unit\":\"rpm\",\"normalRange\":\"800-1800\"}," +
                "\"current\":{\"value\":%.1f,\"unit\":\"A\",\"normalRange\":\"10-25\"}" +
                "}}",
                deviceId, Instant.now().toString(),
                temperature, vibration, pressure, rpm, current
        );
    }
}
