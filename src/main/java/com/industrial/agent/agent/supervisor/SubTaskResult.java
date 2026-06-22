package com.industrial.agent.agent.supervisor;

public record SubTaskResult(SubTask task, String reply, String status) {
    public SubTaskResult(SubTask task, String reply) {
        this(task, reply, "COMPLETED");
    }
}
