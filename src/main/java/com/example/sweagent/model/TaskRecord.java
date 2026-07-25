package com.example.sweagent.model;

import com.example.sweagent.dto.StatusTransition;
import com.example.sweagent.dto.ToolInvocationRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tasks")
public class TaskRecord {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    private String taskId;

    private String repoPath;

    @Column(columnDefinition = "TEXT")
    private String issueDescription;

    private String currentStatus;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String gitDiff;

    @Column(columnDefinition = "TEXT")
    private String finalTestResult;

    private int attemptsMade;

    private long createdAt;

    private long updatedAt;

    @Column(columnDefinition = "TEXT")
    private String toolTraceJson;

    @Column(columnDefinition = "TEXT")
    private String historyJson;

    @Transient
    private List<ToolInvocationRecord> toolTrace = new ArrayList<>();

    @Transient
    private List<StatusTransition> history = new ArrayList<>();

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
        serializeLists();
    }

    @PrePersist
    @PreUpdate
    public void serializeLists() {
        try {
            if (toolTrace != null) {
                this.toolTraceJson = MAPPER.writeValueAsString(toolTrace);
            }
            if (history != null) {
                this.historyJson = MAPPER.writeValueAsString(history);
            }
        } catch (Exception e) {
            // fallback
        }
    }

    @PostLoad
    public void deserializeLists() {
        try {
            if (toolTraceJson != null && !toolTraceJson.isBlank()) {
                this.toolTrace = MAPPER.readValue(toolTraceJson, new TypeReference<List<ToolInvocationRecord>>() {});
            }
            if (historyJson != null && !historyJson.isBlank()) {
                this.history = MAPPER.readValue(historyJson, new TypeReference<List<StatusTransition>>() {});
            }
        } catch (Exception e) {
            // fallback
        }
    }
}
