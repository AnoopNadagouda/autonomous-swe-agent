package com.example.sweagent.dto;

import java.util.List;

public record PatchTaskResponse(
        String summary,
        List<ToolInvocationRecord> toolTrace,
        String gitDiff,
        String repoPath,
        int attemptsMade,
        String finalTestResult
) {
}
