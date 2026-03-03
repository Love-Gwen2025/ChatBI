package com.chatbi.config;

import com.chatbi.agent.memory.RedisChatMemoryStore;
import com.chatbi.agent.service.DataAnalysisAssistant;
import com.chatbi.agent.service.StreamingDataAnalysisAssistant;
import com.chatbi.agent.tool.DataAnalysisTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public DataAnalysisAssistant dataAnalysisAssistant(
            ChatModel chatModel,
            RedisChatMemoryStore chatMemoryStore,
            DataAnalysisTools dataAnalysisTools) {
        return AiServices.builder(DataAnalysisAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(dataAnalysisTools)
                .build();
    }

    @Bean
    public StreamingDataAnalysisAssistant streamingDataAnalysisAssistant(
            StreamingChatModel streamingChatModel,
            RedisChatMemoryStore chatMemoryStore,
            DataAnalysisTools dataAnalysisTools) {
        return AiServices.builder(StreamingDataAnalysisAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(dataAnalysisTools)
                .build();
    }
}
