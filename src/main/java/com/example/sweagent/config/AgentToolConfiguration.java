package com.example.sweagent.config;

import com.example.sweagent.tool.RecordingToolCallbackProvider;
import com.example.sweagent.tool.ToolTraceRecorder;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AgentToolConfiguration {

    @Bean
    public RecordingToolCallbackProvider recordingToolCallbackProvider(
            List<McpSyncClient> mcpSyncClients, ToolTraceRecorder toolTraceRecorder) {
        return new RecordingToolCallbackProvider(mcpSyncClients, toolTraceRecorder);
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}