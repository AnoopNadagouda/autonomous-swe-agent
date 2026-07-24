package com.example.sweagent.tool;

import com.example.sweagent.dto.ToolInvocationRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ToolTraceRecorder {

    private final ThreadLocal<List<ToolInvocationRecord>> currentTrace = new ThreadLocal<>();

    public void start() {
        this.currentTrace.set(new ArrayList<>());
    }

    public void record(String toolName, String input, String output) {
        List<ToolInvocationRecord> trace = this.currentTrace.get();
        if (trace != null) {
            trace.add(new ToolInvocationRecord(toolName, input, output));
        }
    }

    public List<ToolInvocationRecord> snapshot() {
        List<ToolInvocationRecord> trace = this.currentTrace.get();
        return trace == null ? List.of() : List.copyOf(trace);
    }

    public Optional<String> findLastOutput(String toolName) {
        List<ToolInvocationRecord> trace = this.currentTrace.get();
        if (trace == null) {
            return Optional.empty();
        }
        for (int index = trace.size() - 1; index >= 0; index--) {
            ToolInvocationRecord record = trace.get(index);
            if (toolName.equals(record.toolName())) {
                return Optional.ofNullable(record.output());
            }
        }
        return Optional.empty();
    }

    public void clear() {
        this.currentTrace.remove();
    }
}