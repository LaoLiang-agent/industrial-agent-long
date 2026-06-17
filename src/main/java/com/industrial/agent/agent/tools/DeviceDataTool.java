package com.industrial.agent.agent.tools;

import com.industrial.agent.tsdb.TdengineDataService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDataTool {

    private final TdengineDataService tdengine;

    @Tool("查询指定设备的历史遥测数据，包括温度、振动、压力、转速、电流等关键指标。" +
          "输入设备ID，返回最近1小时的关键指标统计和最新数据点。")
    public String queryDeviceHistory(String deviceId) {
        log.info("[DataTool] TDEngine query for device: {}", deviceId);

        Map<String, Object> stats = tdengine.queryStats(deviceId, 60);
        Map<String, Object> latest = tdengine.queryLatest(deviceId);

        if (stats.isEmpty() && latest.isEmpty()) {
            return String.format("{\"deviceId\":\"%s\",\"error\":false," +
                    "\"message\":\"该设备暂无历史数据，请检查数据采集是否正常\"}", deviceId);
        }

        return String.format(
                "{\"deviceId\":\"%s\",\"latest\":%s,\"statsLast60Min\":%s}",
                deviceId, mapToJson(latest), mapToJson(stats));
    }

    @Tool("查询指定设备最近5分钟的实时遥测趋势。" +
          "输入设备ID，返回最新的原始数据点列表。")
    public String queryRealtimeData(String deviceId) {
        log.info("[DataTool] Realtime query for device: {}", deviceId);
        List<Map<String, Object>> history = tdengine.queryHistory(deviceId, 5);
        return String.format("{\"deviceId\":\"%s\",\"dataPoints\":%d,\"entries\":%s}",
                deviceId, history.size(), listToJson(history));
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(list.size(), 10); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
