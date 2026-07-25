package com.example.sweagent.service;

import com.example.sweagent.dto.PatchTaskRequest;
import com.example.sweagent.dto.PatchTaskResponse;
import com.example.sweagent.dto.TaskCreateRequest;
import com.example.sweagent.model.TaskRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatchService {

    private final TaskOrchestratorService taskOrchestratorService;

    public PatchTaskResponse createPatch(PatchTaskRequest request) {
        TaskCreateRequest createRequest = new TaskCreateRequest(request.repoPath(), request.issueDescription());
        TaskRecord record = taskOrchestratorService.submitTask(createRequest);
        return new PatchTaskResponse(
                record.getSummary(),
                record.getToolTrace(),
                record.getGitDiff(),
                record.getRepoPath(),
                record.getAttemptsMade(),
                record.getFinalTestResult()
        );
    }
}
