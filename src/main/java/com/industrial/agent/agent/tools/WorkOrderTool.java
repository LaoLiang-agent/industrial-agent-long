package com.industrial.agent.agent.tools;

import com.industrial.agent.agent.model.WorkOrder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates and manages maintenance work orders.
 * In-memory storage — Week 9+ can persist to database.
 */
@Slf4j
@Component
public class WorkOrderTool {

    private final ConcurrentMap<String, WorkOrder> store = new ConcurrentHashMap<>();

    @Tool("""
            创建维修工单。当设备诊断确认需要维修、检查或保养时调用。
            参数：
            - deviceId: 设备编号
            - faultType: 故障类型（如 温度过高、振动异常、电流过载）
            - priority: 优先级 HIGH/MEDIUM/LOW
            - diagnosis: 诊断结论摘要
            - suggestedActions: 建议措施，逗号分隔
            返回创建的工单信息。
            """)
    public String createWorkOrder(String deviceId, String faultType, String priority,
                                  String diagnosis, String suggestedActions) {
        String description = String.format("[%s] %s", faultType, diagnosis);
        WorkOrder wo = new WorkOrder(deviceId, faultType.startsWith("检查") ? "检查" :
                faultType.startsWith("保养") ? "保养" : "维修", priority, description);

        if (suggestedActions != null && !suggestedActions.isBlank()) {
            wo.setSuggestedActions(java.util.List.of(suggestedActions.split("[,，]")));
        }

        store.put(wo.getWorkOrderId(), wo);
        log.info("[WorkOrderTool] Created {} for {} (priority={})", wo.getWorkOrderId(), deviceId, priority);
        return wo.toString();
    }

    @Tool("查询指定工单的详细信息。输入工单ID，返回工单详情。")
    public String getWorkOrder(String workOrderId) {
        WorkOrder wo = store.get(workOrderId);
        if (wo == null) {
            return String.format("{\"error\":\"工单 %s 不存在\"}", workOrderId);
        }
        return wo.toString();
    }

    /** Programmatic lookup (not exposed to LLM). */
    public WorkOrder findById(String workOrderId) {
        return store.get(workOrderId);
    }
}
