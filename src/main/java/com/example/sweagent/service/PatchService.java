package com.example.sweagent.service;

import com.example.sweagent.dto.PatchTaskRequest;
import com.example.sweagent.dto.PatchTaskResponse;
import com.example.sweagent.dto.ToolInvocationRecord;
import com.example.sweagent.exception.RepoPathNotFoundException;
import com.example.sweagent.tool.RecordingToolCallbackProvider;
import com.example.sweagent.tool.ToolTraceRecorder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class PatchService {

    private static final String SYSTEM_PROMPT = """
            You are an autonomous software engineering agent using Model Context Protocol (MCP) tools.
            You MUST explore the repository first using MCP tools. Do NOT assume file contents or directory structures.

            Required Workflow:
            1. Call list_directory to explore the directory structure of the repository.
            2. Call read_file to inspect the source file(s) related to the issue.
            3. Call write_file to apply the fix directly to the target file inside the repository.
            4. CRITICAL: After calling write_file, you MUST call run_in_sandbox with a command to run the test suite (e.g. "mvn test") to verify the fix before finishing.
            5. If the tests fail, analyze the test failure output, reconsider your fix, call write_file again with a corrected version, and re-run run_in_sandbox ("mvn test").
            6. Repeat this edit-and-test loop if necessary, capping your attempts at 3 attempts total.
            7. Once tests pass (or if maximum 3 attempts are reached), call git_diff to inspect your final changes.
            8. Return a concise summary of the plan, attempts made, and resolution.

            Always pass the target absolute repository path as repoPath in every tool call.
            """;

    private final ChatClient chatClient;
    private final ToolTraceRecorder toolTraceRecorder;
    private final RecordingToolCallbackProvider recordingToolCallbackProvider;

    public PatchService(ChatClient chatClient, ToolTraceRecorder toolTraceRecorder,
                        RecordingToolCallbackProvider recordingToolCallbackProvider) {
        this.chatClient = chatClient;
        this.toolTraceRecorder = toolTraceRecorder;
        this.recordingToolCallbackProvider = recordingToolCallbackProvider;
    }

    public PatchTaskResponse createPatch(PatchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        Path repoPath = validateRepoPath(request.repoPath());
        this.toolTraceRecorder.start();
        try {
            String assistantResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(repoPath, request.issueDescription()))
                    .toolCallbacks(this.recordingToolCallbackProvider)
                    .call()
                    .content();

            List<ToolInvocationRecord> toolTrace = this.toolTraceRecorder.snapshot();

            int attemptsMade = (int) toolTrace.stream()
                    .filter(record -> record.toolName() != null && record.toolName().endsWith("run_in_sandbox"))
                    .count();

            String finalTestResult = this.toolTraceRecorder.findLastOutput("run_in_sandbox")
                    .orElse("No sandbox tests executed");

            String gitDiff = this.toolTraceRecorder.findLastOutput("git_diff").orElse("");
            if (gitDiff.isBlank()) {
                gitDiff = runGitDiff(repoPath);
            }

            return new PatchTaskResponse(
                    assistantResponse == null ? "" : assistantResponse,
                    toolTrace,
                    gitDiff,
                    repoPath.toString(),
                    attemptsMade,
                    finalTestResult
            );
        } finally {
            this.toolTraceRecorder.clear();
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

    private String buildUserPrompt(Path repoPath, String issueDescription) {
        return "Target Repository Root (repoPath): " + repoPath.toString() + "\n"
                + "Issue Description: " + issueDescription + "\n\n"
                + "Please start by listing the directory contents using list_directory with repoPath=\"" + repoPath.toString() + "\".";
    }

    private String runGitDiff(Path repoPath) {
        try {
            Process process = new ProcessBuilder("git", "-C", repoPath.toString(), "diff", "--", ".")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor();
            return output.trim();
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
