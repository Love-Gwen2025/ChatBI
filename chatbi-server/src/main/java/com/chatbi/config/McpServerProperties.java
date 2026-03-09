package com.chatbi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    private String url = "http://localhost:8000/mcp";
    private String transport = "streamable-http";
    private Duration timeout = Duration.ofSeconds(30);
    private boolean logRequests = true;
    private boolean logResponses = true;
}
