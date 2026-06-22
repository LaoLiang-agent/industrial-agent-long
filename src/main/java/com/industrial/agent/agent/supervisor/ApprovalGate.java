package com.industrial.agent.agent.supervisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ApprovalGate {

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }

    private final Map<String, ApprovalStatus> approvals = new ConcurrentHashMap<>();
    private final Map<String, SubTask> pendingTasks = new ConcurrentHashMap<>();

    public boolean requiresApproval(SubTask task) {
        return "L3".equals(task.riskLevel());
    }

    public String requestApproval(SubTask task) {
        String approvalId = "APR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        approvals.put(approvalId, ApprovalStatus.PENDING);
        pendingTasks.put(approvalId, task);
        log.info("[ApprovalGate] Approval requested: {} for task: {}", approvalId, task.task());
        return approvalId;
    }

    public ApprovalStatus approve(String approvalId) {
        if (!approvals.containsKey(approvalId)) return null;
        approvals.put(approvalId, ApprovalStatus.APPROVED);
        log.info("[ApprovalGate] {} APPROVED", approvalId);
        return ApprovalStatus.APPROVED;
    }

    public ApprovalStatus reject(String approvalId) {
        if (!approvals.containsKey(approvalId)) return null;
        approvals.put(approvalId, ApprovalStatus.REJECTED);
        log.info("[ApprovalGate] {} REJECTED", approvalId);
        return ApprovalStatus.REJECTED;
    }

    public ApprovalStatus getStatus(String approvalId) {
        return approvals.get(approvalId);
    }

    public SubTask getPendingTask(String approvalId) {
        return pendingTasks.get(approvalId);
    }
}
