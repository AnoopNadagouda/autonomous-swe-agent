package com.example.sweagent.model;

import com.example.sweagent.dto.StatusTransition;
import com.example.sweagent.dto.ToolInvocationRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class TaskRecord {
    private String taskId;
    private String repoPath;
    private String issueDescription;
    private String currentStatus;
    private String summary;
    private List<ToolInvocationRecord> toolTrace = new CopyOnWriteArrayList<>();
    private String gitDiff;
    private String finalTestResult;
    private int attemptsMade;
    private long createdAt;
    private long updatedAt;
    private List<StatusTransition> history = new CopyOnWriteArrayList<>();

    public TaskRecord(String taskId, String repoPath, String issueDescription) {
        this.taskId = taskId;
        this.repoPath = repoPath;
        this.issueDescription = issueDescription;
        this.currentStatus = "PENDING";
        this.summary = "Task submitted";
        this.gitDiff = "";
        this.finalTestResult = "";
        this.attemptsMade = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.history.add(new StatusTransition("PENDING", "Task submitted", this.createdAt));
    }
}
