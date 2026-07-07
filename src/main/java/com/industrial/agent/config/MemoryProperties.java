package com.industrial.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    private long workingTtlSeconds = 600;
    private int conversationMaxEntries = 5;
    private double profileConfidenceThreshold = 0.9;

    public long getWorkingTtlSeconds() { return workingTtlSeconds; }
    public void setWorkingTtlSeconds(long workingTtlSeconds) { this.workingTtlSeconds = workingTtlSeconds; }

    public int getConversationMaxEntries() { return conversationMaxEntries; }
    public void setConversationMaxEntries(int conversationMaxEntries) { this.conversationMaxEntries = conversationMaxEntries; }

    public double getProfileConfidenceThreshold() { return profileConfidenceThreshold; }
    public void setProfileConfidenceThreshold(double profileConfidenceThreshold) { this.profileConfidenceThreshold = profileConfidenceThreshold; }
}
