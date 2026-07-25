package com.example.sweagent.dto;

public record TaskSubmittedEvent(String taskId, String repoPath, String issueDescription) {
}
