package com.industrial.agent.agent.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Work order generated from device diagnosis.
 */
public class WorkOrder {

    private String workOrderId;
    private String deviceId;
    private String type;       // 维修/检查/保养
    private String priority;   // HIGH/MEDIUM/LOW
    private String description;
    private String assignee;
    private Instant createdTime;
    private String status;     // PENDING/IN_PROGRESS/DONE
    private List<String> suggestedActions;

    public WorkOrder() {}

    public WorkOrder(String deviceId, String type, String priority, String description) {
        this.workOrderId = "WO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.deviceId = deviceId;
        this.type = type;
        this.priority = priority;
        this.description = description;
        this.assignee = "值班工程师";
        this.createdTime = Instant.now();
        this.status = "PENDING";
    }

    public String getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<String> suggestedActions) { this.suggestedActions = suggestedActions; }

    @Override
    public String toString() {
        return String.format(
                "{\"workOrderId\":\"%s\",\"deviceId\":\"%s\",\"type\":\"%s\",\"priority\":\"%s\"," +
                "\"description\":\"%s\",\"assignee\":\"%s\",\"status\":\"%s\",\"createdTime\":\"%s\"}",
                workOrderId, deviceId, type, priority, description, assignee, status, createdTime);
    }
}
