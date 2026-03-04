package com.chatbi.config;

import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.agent.service.DataAnalysisAssistant;
import com.chatbi.agent.service.StreamingDataAnalysisAssistant;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.McpToolProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${mcp.server.url}")
    private String mcpServerUrl;

    private McpClient mcpClient;

    @Bean
    public McpToolProvider mcpToolProvider() {
        McpTransport transport = HttpMcpTransport.builder()
                .sseUrl(mcpServerUrl)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        mcpClient = DefaultMcpClient.builder()
                .key("chatbi-mcp")
                .transport(transport)
                .build();

        log.info("MCP Client 已连接: {}", mcpServerUrl);

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
                        .maxMessages(20)
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
                        .maxMessages(20)
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
                log.info("MCP Client 已关闭");
            } catch (Exception e) {
                log.warn("关闭 MCP Client 失败: {}", e.getMessage());
            }
        }
    }
}
