package com.example.sweagent.service;

import com.example.sweagent.dto.StatusTransition;
import com.example.sweagent.dto.TaskCreateRequest;
import com.example.sweagent.dto.TaskStatusEvent;
import com.example.sweagent.dto.TaskSubmittedEvent;
import com.example.sweagent.exception.RepoPathNotFoundException;
import com.example.sweagent.model.TaskRecord;
import com.example.sweagent.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskOrchestratorService {

    private static final String TOPIC_TASK_SUBMITTED = "swe-agent.task-submitted";

    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, TaskSubmittedEvent> kafkaTemplate;

    @Transactional
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
        taskRecord = taskRepository.save(taskRecord);

        TaskSubmittedEvent submittedEvent = new TaskSubmittedEvent(taskId, repoPath.toString(), request.issueDescription());
        kafkaTemplate.send(TOPIC_TASK_SUBMITTED, taskId, submittedEvent);
        log.info("Task submitted successfully: taskId={}, repoPath={}", taskId, repoPath);

        return taskRecord;
    }

    @Transactional(readOnly = true)
    public Optional<TaskRecord> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    @Transactional
    @KafkaListener(topics = "swe-agent.task-status", groupId = "swe-agent-orchestrator-group")
    public void handleTaskStatusEvent(TaskStatusEvent event) {

        if (event == null || event.taskId() == null) {
            return;
        }
        log.info("Received TaskStatusEvent: taskId={}, status={}, toolTraceSize={}",
                event.taskId(), event.status(), event.toolTrace() == null ? 0 : event.toolTrace().size());
        Optional<TaskRecord> optionalTask = taskRepository.findById(event.taskId());
        if (optionalTask.isPresent()) {
            TaskRecord taskRecord = optionalTask.get();
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
            taskRepository.save(taskRecord);
        } else {
            log.warn("Received TaskStatusEvent for unknown taskId={}", event.taskId());
        }
    }

    private Path validateRepoPath(String repoPathValue) {
        if (repoPathValue == null || repoPathValue.isBlank()) {
            throw new RepoPathNotFoundException("repoPath is required");
        }
        boolean isWindowsAbsolute = repoPathValue.matches("^[a-zA-Z]:[\\\\/].*");
        Path repoPath = Path.of(repoPathValue);
        if (!repoPath.isAbsolute() && !isWindowsAbsolute) {
            throw new RepoPathNotFoundException("repoPath must be an absolute path: " + repoPath);
        }
        if (!isWindowsAbsolute && !Files.exists(repoPath)) {
            throw new RepoPathNotFoundException("repoPath does not exist: " + repoPath);
        }
        return repoPath;
    }
}
