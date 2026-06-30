package com.industrial.agent.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * MCP Client configuration — connects to an external MCP Server
 * that exposes industrial tools via the Model Context Protocol.
 *
 * Enable with: mcp.enabled=true and mcp.server-url=http://localhost:3001
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mcp.enabled", havingValue = "true", matchIfMissing = false)
public class McpConfig {

    @Value("${mcp.server-url:http://localhost:3001}")
    private String mcpServerUrl;

    @Bean
    public McpClient mcpClient() {
        HttpMcpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(mcpServerUrl + "/mcp/sse")
                .timeout(Duration.ofSeconds(30))
                .build();

        McpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();

        log.info("[MCP] Client connected to {}", mcpServerUrl);
        return client;
    }

    @Bean
    public McpToolProvider mcpToolProvider(McpClient mcpClient) {
        McpToolProvider provider = McpToolProvider.builder()
                .mcpClients(List.of(mcpClient))
                .build();
        log.info("[MCP] ToolProvider initialized, discovering tools...");
        return provider;
    }
}
