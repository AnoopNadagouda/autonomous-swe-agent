package com.example.sweagent.service;

import com.example.sweagent.dto.TaskCreateRequest;
import com.example.sweagent.dto.TaskStatusEvent;
import com.example.sweagent.dto.TaskSubmittedEvent;
import com.example.sweagent.exception.RepoPathNotFoundException;
import com.example.sweagent.model.TaskRecord;
import com.example.sweagent.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrchestratorServiceTest {

    private TaskRepository taskRepository;
    @SuppressWarnings("unchecked")
    private org.springframework.kafka.core.KafkaTemplate<String, TaskSubmittedEvent> kafkaTemplate = mock(org.springframework.kafka.core.KafkaTemplate.class);
    private TaskOrchestratorService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        service = new TaskOrchestratorService(taskRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("Verify valid task submission generates TaskRecord and sends Kafka event")
    void testSubmitTaskSuccess(@TempDir Path tempDir) {
        when(taskRepository.save(any(TaskRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskCreateRequest request = new TaskCreateRequest(tempDir.toString(), "Fix bug in Calculator");
        TaskRecord record = service.submitTask(request);

        assertThat(record).isNotNull();
        assertThat(record.getTaskId()).isNotNull();
        assertThat(record.getCurrentStatus()).isEqualTo("PENDING");
        assertThat(record.getIssueDescription()).isEqualTo("Fix bug in Calculator");

        verify(kafkaTemplate).send(eq("swe-agent.task-submitted"), eq(record.getTaskId()), any(TaskSubmittedEvent.class));
    }

    @Test
    @DisplayName("Verify relative repo path throws RepoPathNotFoundException and empty repoPath throws IllegalArgumentException")
    void testSubmitTaskInvalidRepoPath() {
        TaskCreateRequest requestRelative = new TaskCreateRequest("relative/path/repo", "Fix bug");
        assertThatThrownBy(() -> service.submitTask(requestRelative))
                .isInstanceOf(RepoPathNotFoundException.class);

        TaskCreateRequest requestEmpty = new TaskCreateRequest("", "Fix bug");
        assertThatThrownBy(() -> service.submitTask(requestEmpty))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Verify TaskStatusEvent handler updates TaskRecord history and fields")
    void testHandleTaskStatusEvent() {
        TaskRecord record = new TaskRecord("task-123", "C:\\temp", "Fix issue");
        when(taskRepository.findById("task-123")).thenReturn(Optional.of(record));

        TaskStatusEvent statusEvent = new TaskStatusEvent(
                "task-123", "CODING", "Writing fix", List.of(), "diff content", "test output", 1, System.currentTimeMillis()
        );

        service.handleTaskStatusEvent(statusEvent);

        assertThat(record.getCurrentStatus()).isEqualTo("CODING");
        assertThat(record.getSummary()).isEqualTo("Writing fix");
        assertThat(record.getGitDiff()).isEqualTo("diff content");
        verify(taskRepository).save(record);
    }
}
