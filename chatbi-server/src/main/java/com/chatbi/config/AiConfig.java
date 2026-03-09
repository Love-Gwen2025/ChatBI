package com.chatbi.config;

import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.agent.service.DataAnalysisAssistant;
import com.chatbi.agent.service.StreamingDataAnalysisAssistant;
import com.chatbi.chat.service.McpToolTracingBridge;
import com.chatbi.common.constants.AppConstants;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class AiConfig {

    private final McpServerProperties mcpServerProperties;
    private final McpToolTracingBridge mcpToolTracingBridge;

    private McpClient mcpClient;

    public AiConfig(McpServerProperties mcpServerProperties, McpToolTracingBridge mcpToolTracingBridge) {
        this.mcpServerProperties = mcpServerProperties;
        this.mcpToolTracingBridge = mcpToolTracingBridge;
    }

    @Bean
    public McpToolProvider mcpToolProvider() {
        McpTransport transport = createTransport();

        mcpClient = DefaultMcpClient.builder()
                .key("chatbi-mcp")
                .transport(transport)
                .toolExecutionTimeout(mcpServerProperties.getTimeout())
                .listener(mcpToolTracingBridge)
                .build();

        log.info("MCP Client connected: transport={}, url={}", mcpServerProperties.getTransport(), mcpServerProperties.getUrl());

        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }

    @Bean
    public DataAnalysisAssistant dataAnalysisAssistant(
            ChatModel chatModel,
            RedisChatMemoryStore chatMemoryStore,
            McpToolProvider mcpToolProvider) {
        return AiServices.builder(DataAnalysisAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(AppConstants.MAX_CHAT_MEMORY_MESSAGES)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .toolProvider(mcpToolProvider)
                .build();
    }

    @Bean
    public StreamingDataAnalysisAssistant streamingDataAnalysisAssistant(
            StreamingChatModel streamingChatModel,
            RedisChatMemoryStore chatMemoryStore,
            McpToolProvider mcpToolProvider) {
        return AiServices.builder(StreamingDataAnalysisAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(AppConstants.MAX_CHAT_MEMORY_MESSAGES)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .toolProvider(mcpToolProvider)
                .build();
    }

    @PreDestroy
    public void cleanup() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
                log.info("MCP Client closed");
            } catch (Exception e) {
                log.warn("Failed to close MCP Client: {}", e.getMessage());
            }
        }
    }

    private McpTransport createTransport() {
        String transport = mcpServerProperties.getTransport() == null
                ? "streamable-http"
                : mcpServerProperties.getTransport().trim().toLowerCase();

        if ("sse".equals(transport)) {
            return HttpMcpTransport.builder()
                    .sseUrl(mcpServerProperties.getUrl())
                    .customHeaders(mcpToolTracingBridge)
                    .timeout(mcpServerProperties.getTimeout())
                    .logRequests(mcpServerProperties.isLogRequests())
                    .logResponses(mcpServerProperties.isLogResponses())
                    .build();
        }

        return StreamableHttpMcpTransport.builder()
                .url(mcpServerProperties.getUrl())
                .customHeaders(mcpToolTracingBridge)
                .timeout(mcpServerProperties.getTimeout())
                .logRequests(mcpServerProperties.isLogRequests())
                .logResponses(mcpServerProperties.isLogResponses())
                .build();
    }
}
