package com.example.sweagent.dto;

import java.util.List;

public record TaskStatusEvent(
        String taskId,
        String status,
        String summary,
        List<ToolInvocationRecord> toolTrace,
        String gitDiff,
        String testResult,
        int attemptsMade,
        long timestamp
) {
}
