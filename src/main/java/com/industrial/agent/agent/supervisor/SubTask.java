package com.industrial.agent.agent.supervisor;

public record SubTask(String expert, String task, String riskLevel) {
    public SubTask(String expert, String task) {
        this(expert, task, "L0");
    }
}
