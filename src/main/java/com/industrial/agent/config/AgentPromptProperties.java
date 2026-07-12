package com.industrial.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent.prompt")
public class AgentPromptProperties {

    private String role = "";
    private List<String> policies = new ArrayList<>();

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<String> getPolicies() { return policies; }
    public void setPolicies(List<String> policies) { this.policies = policies; }
}
