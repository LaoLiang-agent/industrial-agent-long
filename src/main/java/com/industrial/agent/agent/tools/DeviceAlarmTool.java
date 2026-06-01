package com.industrial.agent.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class DeviceAlarmTool {

    private static final List<String> ALARM_TYPES = List.of(
            "温度过高", "振动异常", "压力超标", "电流过载",
            "润滑不足", "轴承磨损", "通讯中断", "电源异常"
    );

    private static final List<String> DEVICES = List.of(
            "CNC-001", "CNC-002", "ROBOT-ARM-A1",
            "PRESS-001", "INJECTION-M01", "CONVEYOR-B3"
    );

    @Tool("查询指定设备的当前告警信息。输入设备ID，返回该设备的所有活跃告警。")
    public String queryDeviceAlarms(String deviceId) {
        log.info("[AlarmTool] Querying alarms for device: {}", deviceId);

        // Simulate alarm data — in production, this queries TDEngine/InfluxDB
        Random random = new Random();
        int alarmCount = random.nextInt(3); // 0-2 alarms

        if (alarmCount == 0) {
            return String.format("{\"deviceId\":\"%s\",\"status\":\"normal\",\"alarms\":[]}", deviceId);
        }

        StringBuilder alarms = new StringBuilder("[");
        for (int i = 0; i < alarmCount; i++) {
            String alarmType = ALARM_TYPES.get(random.nextInt(ALARM_TYPES.size()));
            if (i > 0) alarms.append(",");
            alarms.append(String.format(
                    "{\"type\":\"%s\",\"severity\":\"%s\",\"timestamp\":\"%s\",\"value\":%.2f}",
                    alarmType,
                    random.nextDouble() > 0.5 ? "HIGH" : "MEDIUM",
                    Instant.now().minusSeconds(random.nextInt(3600)).toString(),
                    50 + random.nextDouble() * 100
            ));
        }
        alarms.append("]");

        return String.format(
                "{\"deviceId\":\"%s\",\"status\":\"warning\",\"activeAlarms\":%s,\"totalCount\":%d}",
                deviceId, alarms, alarmCount
        );
    }
}
