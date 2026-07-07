package com.industrial.agent.agent.tools;

import com.industrial.agent.agent.model.WorkOrder;
import com.industrial.agent.tool.SideEffect;
import com.industrial.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Creates and manages maintenance work orders with H2 persistence.
 * Lifecycle: PENDING → IN_PROGRESS → COMPLETED → CLOSED
 */
@Slf4j
@Component
public class WorkOrderTool {

    private final JdbcTemplate jdbc;
    private final ToolExecutor toolExecutor;

    public WorkOrderTool(JdbcTemplate jdbc, ToolExecutor toolExecutor) {
        this.jdbc = jdbc;
        this.toolExecutor = toolExecutor;
    }

    @Tool("""
            创建维修工单。仅当确认设备存在硬件故障需要人工介入时调用。
            设备状态正常、无告警时不要调用。
            参数：
            - deviceId: 设备编号
            - type: 工单类型（维修/检查/保养）
            - priority: 优先级 HIGH/MEDIUM/LOW
            - description: 故障描述和诊断结论
            """)
    public String createWorkOrder(
            @P("设备ID") String deviceId,
            @P("工单类型：维修/检查/保养") String type,
            @P("优先级：HIGH/MEDIUM/LOW") String priority,
            @P("故障描述和诊断结论") String description) {

        String executionId = toolExecutor.generateExecutionId(
                "session", "createWorkOrder", deviceId + type + priority + description);
        long start = System.currentTimeMillis();

        // Idempotency check
        if (toolExecutor.isIdempotent(executionId)) {
            String cached = toolExecutor.getCachedResult(executionId);
            log.info("[WorkOrderTool] Duplicate call detected, returning cached result: {}", executionId);
            toolExecutor.audit(executionId, "createWorkOrder", SideEffect.WRITE,
                    deviceId + "," + type + "," + priority, cached, "DUPLICATE", System.currentTimeMillis() - start);
            return cached;
        }

        WorkOrder wo = new WorkOrder(deviceId, type, priority, description);
        jdbc.update(
                "INSERT INTO work_orders (id, device_id, type, priority, description) VALUES (?,?,?,?,?)",
                wo.getWorkOrderId(), wo.getDeviceId(), wo.getType(), wo.getPriority(), wo.getDescription()
        );
        String result = wo.toString();
        toolExecutor.cacheResult(executionId, result);
        long duration = System.currentTimeMillis() - start;
        toolExecutor.audit(executionId, "createWorkOrder", SideEffect.WRITE,
                deviceId + "," + type + "," + priority, result, "OK", duration);
        log.info("[WorkOrderTool] Created {} for {} (priority={})", wo.getWorkOrderId(), deviceId, priority);
        return result;
    }

    @Tool("将工单状态更新为 IN_PROGRESS（工程师开始处理）")
    public String startWorkOrder(@P("工单编号") String workOrderId) {
        int updated = jdbc.update(
                "UPDATE work_orders SET status='IN_PROGRESS', updated_at=NOW() WHERE id=? AND status='PENDING'",
                workOrderId);
        if (updated == 0) {
            return String.format("工单 %s 不存在或状态不可更改（只能从 PENDING 开始）", workOrderId);
        }
        log.info("[WorkOrderTool] {} → IN_PROGRESS", workOrderId);
        return String.format("工单 %s 已开始处理（IN_PROGRESS）", workOrderId);
    }

    @Tool("将工单状态更新为 COMPLETED（维修已完成）")
    public String completeWorkOrder(@P("工单编号") String workOrderId) {
        int updated = jdbc.update(
                "UPDATE work_orders SET status='COMPLETED', completed_at=NOW(), updated_at=NOW() WHERE id=? AND status='IN_PROGRESS'",
                workOrderId);
        if (updated == 0) {
            return String.format("工单 %s 不存在或状态不可更改（只能从 IN_PROGRESS 完成）", workOrderId);
        }
        log.info("[WorkOrderTool] {} → COMPLETED", workOrderId);
        return String.format("工单 %s 已标记完成（COMPLETED）", workOrderId);
    }

    @Tool("查询指定工单的详细信息")
    public String getWorkOrder(@P("工单编号") String workOrderId) {
        WorkOrder wo = findById(workOrderId);
        if (wo == null) {
            return String.format("{\"error\":\"工单 %s 不存在\"}", workOrderId);
        }
        return wo.toString();
    }

    /** Programmatic lookup (not exposed to LLM). */
    public WorkOrder findById(String workOrderId) {
        List<WorkOrder> results = jdbc.query(
                "SELECT * FROM work_orders WHERE id=?",
                (rs, rowNum) -> new WorkOrder(
                        rs.getString("device_id"),
                        rs.getString("type"),
                        rs.getString("priority"),
                        rs.getString("description")
                ) {{
                    setWorkOrderId(rs.getString("id"));
                    setAssignee(rs.getString("assignee"));
                    setStatus(rs.getString("status"));
                    setCreatedTime(rs.getTimestamp("created_at").toInstant());
                    Timestamp ua = rs.getTimestamp("updated_at");
                    if (ua != null) setUpdatedAt(ua.toInstant());
                    Timestamp ca = rs.getTimestamp("completed_at");
                    if (ca != null) setCompletedAt(ca.toInstant());
                    Timestamp cl = rs.getTimestamp("closed_at");
                    if (cl != null) setClosedAt(cl.toInstant());
                }},
                workOrderId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    /** List work orders for a device. */
    public List<WorkOrder> findByDevice(String deviceId) {
        return jdbc.query(
                "SELECT * FROM work_orders WHERE device_id=? ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    WorkOrder wo = new WorkOrder(
                            rs.getString("device_id"),
                            rs.getString("type"),
                            rs.getString("priority"),
                            rs.getString("description")
                    );
                    wo.setWorkOrderId(rs.getString("id"));
                    wo.setAssignee(rs.getString("assignee"));
                    wo.setStatus(rs.getString("status"));
                    wo.setCreatedTime(rs.getTimestamp("created_at").toInstant());
                    Timestamp ua = rs.getTimestamp("updated_at");
                    if (ua != null) wo.setUpdatedAt(ua.toInstant());
                    Timestamp ca = rs.getTimestamp("completed_at");
                    if (ca != null) wo.setCompletedAt(ca.toInstant());
                    return wo;
                },
                deviceId
        );
    }

    /** Stats: total, pending, inProgress, completed. */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Integer.class));
        stats.put("pending", jdbc.queryForObject("SELECT COUNT(*) FROM work_orders WHERE status='PENDING'", Integer.class));
        stats.put("inProgress", jdbc.queryForObject("SELECT COUNT(*) FROM work_orders WHERE status='IN_PROGRESS'", Integer.class));
        stats.put("completed", jdbc.queryForObject("SELECT COUNT(*) FROM work_orders WHERE status='COMPLETED'", Integer.class));
        return stats;
    }
}
