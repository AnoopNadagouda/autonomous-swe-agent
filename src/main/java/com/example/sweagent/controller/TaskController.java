package com.example.sweagent.controller;

import com.example.sweagent.dto.TaskCreateRequest;
import com.example.sweagent.model.TaskRecord;
import com.example.sweagent.service.TaskOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskOrchestratorService taskOrchestratorService;

    @PostMapping
    public ResponseEntity<Map<String, String>> submitTask(@RequestBody TaskCreateRequest request) {
        TaskRecord record = taskOrchestratorService.submitTask(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "taskId", record.getTaskId(),
                        "status", record.getCurrentStatus()
                ));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskRecord> getTask(@PathVariable String taskId) {
        return taskOrchestratorService.getTask(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
