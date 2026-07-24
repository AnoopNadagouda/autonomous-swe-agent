package com.example.sweagent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public final class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolTraceRecorder toolTraceRecorder;

    public RecordingToolCallback(ToolCallback delegate, ToolTraceRecorder toolTraceRecorder) {
        this.delegate = delegate;
        this.toolTraceRecorder = toolTraceRecorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return this.delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        try {
            String output = this.delegate.call(toolInput);
            this.toolTraceRecorder.record(getToolDefinition().name(), toolInput, output);
            return output;
        } catch (RuntimeException exception) {
            this.toolTraceRecorder.record(getToolDefinition().name(), toolInput,
                    "ERROR: " + exception.getMessage());
            throw exception;
        }
    }
}