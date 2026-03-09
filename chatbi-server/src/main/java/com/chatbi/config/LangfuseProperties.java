package com.chatbi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "observability.langfuse")
public class LangfuseProperties {

    private boolean enabled = false;
    private String host = "https://cloud.langfuse.com";
    private String publicKey;
    private String secretKey;
    private String otlpEndpoint;
    private String environment = "development";
    private String serviceName = "chatbi-server";
    private String serviceVersion = "local";
}
