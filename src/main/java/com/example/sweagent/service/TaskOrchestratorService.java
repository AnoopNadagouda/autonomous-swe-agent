package com.example.sweagent.service;

import com.example.sweagent.dto.StatusTransition;
import com.example.sweagent.dto.TaskCreateRequest;
import com.example.sweagent.dto.TaskStatusEvent;
import com.example.sweagent.dto.TaskSubmittedEvent;
import com.example.sweagent.exception.RepoPathNotFoundException;
import com.example.sweagent.model.TaskRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskOrchestratorService {

    private static final String TOPIC_TASK_SUBMITTED = "swe-agent.task-submitted";

    private final ConcurrentHashMap<String, TaskRecord> taskStore = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, TaskSubmittedEvent> kafkaTemplate;

    public TaskRecord submitTask(TaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        Path repoPath = validateRepoPath(request.repoPath());

        String taskId = UUID.randomUUID().toString();
        TaskRecord taskRecord = new TaskRecord(taskId, repoPath.toString(), request.issueDescription());
        taskStore.put(taskId, taskRecord);

        TaskSubmittedEvent submittedEvent = new TaskSubmittedEvent(taskId, repoPath.toString(), request.issueDescription());
        kafkaTemplate.send(TOPIC_TASK_SUBMITTED, taskId, submittedEvent);
        log.info("Task submitted successfully: taskId={}, repoPath={}", taskId, repoPath);

        return taskRecord;
    }

    public Optional<TaskRecord> getTask(String taskId) {
        return Optional.ofNullable(taskStore.get(taskId));
    }

    @KafkaListener(topics = "swe-agent.task-status", groupId = "swe-agent-orchestrator-group")
    public void handleTaskStatusEvent(TaskStatusEvent event) {
        if (event == null || event.taskId() == null) {
            return;
        }
        log.info("Received TaskStatusEvent: taskId={}, status={}", event.taskId(), event.status());
        TaskRecord taskRecord = taskStore.get(event.taskId());
        if (taskRecord != null) {
            taskRecord.setCurrentStatus(event.status());
            if (event.summary() != null && !event.summary().isBlank()) {
                taskRecord.setSummary(event.summary());
            }
            if (event.toolTrace() != null && !event.toolTrace().isEmpty()) {
                taskRecord.setToolTrace(event.toolTrace());
            }
            if (event.gitDiff() != null && !event.gitDiff().isBlank()) {
                taskRecord.setGitDiff(event.gitDiff());
            }
            if (event.testResult() != null && !event.testResult().isBlank()) {
                taskRecord.setFinalTestResult(event.testResult());
            }
            if (event.attemptsMade() > 0) {
                taskRecord.setAttemptsMade(event.attemptsMade());
            }
            taskRecord.setUpdatedAt(System.currentTimeMillis());
            taskRecord.getHistory().add(new StatusTransition(
                    event.status(),
                    event.summary(),
                    event.timestamp() > 0 ? event.timestamp() : System.currentTimeMillis()
            ));
        } else {
            log.warn("Received TaskStatusEvent for unknown taskId={}", event.taskId());
        }
    }

    private Path validateRepoPath(String repoPathValue) {
        Path repoPath = Path.of(repoPathValue);
        if (!repoPath.isAbsolute()) {
            throw new RepoPathNotFoundException("repoPath must be an absolute path: " + repoPath);
        }
        if (!Files.exists(repoPath)) {
            throw new RepoPathNotFoundException("repoPath does not exist: " + repoPath);
        }
        if (!Files.isDirectory(repoPath)) {
            throw new RepoPathNotFoundException("repoPath is not a directory: " + repoPath);
        }
        return repoPath;
    }
}
