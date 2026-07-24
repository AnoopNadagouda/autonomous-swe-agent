package com.example.sweagent.tool;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.List;

public class RecordingToolCallbackProvider implements ToolCallbackProvider {

    private final List<McpSyncClient> mcpSyncClients;
    private final ToolTraceRecorder toolTraceRecorder;

    public RecordingToolCallbackProvider(List<McpSyncClient> mcpSyncClients, ToolTraceRecorder toolTraceRecorder) {
        this.mcpSyncClients = mcpSyncClients;
        this.toolTraceRecorder = toolTraceRecorder;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        SyncMcpToolCallbackProvider delegate = new SyncMcpToolCallbackProvider(this.mcpSyncClients);
        ToolCallback[] callbacks = delegate.getToolCallbacks();
        System.out.println("DEBUG: RecordingToolCallbackProvider fetched callbacks count = " + callbacks.length);
        return Arrays.stream(callbacks)
                .map(toolCallback -> new RecordingToolCallback(toolCallback, this.toolTraceRecorder))
                .toArray(ToolCallback[]::new);
    }
}