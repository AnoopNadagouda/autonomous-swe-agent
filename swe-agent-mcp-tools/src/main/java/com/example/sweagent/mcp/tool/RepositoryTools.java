package com.example.sweagent.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.concurrent.TimeUnit;

@Component
public class RepositoryTools {

    private static final Set<String> IGNORED_NAMES = Set.of(".git", "node_modules", "target", "build");

    @Tool(name = "list_directory", description = "Lists files and directories inside the given repository path. Call this tool first to explore repository contents before reading or writing files.")
    public String listDirectory(
            @ToolParam(description = "Absolute path to the git repository root", required = true) String repoPath,
            @ToolParam(description = "Relative path within the repository to list (use '.' or empty string for root)", required = false) String relativePath) {
        Path repoRoot = validateAndNormalizeRepoPath(repoPath);
        Path targetPath;
        if (relativePath == null || relativePath.isBlank() || relativePath.trim().equals(".")) {
            targetPath = repoRoot;
        } else {
            targetPath = repoRoot.resolve(relativePath).normalize();
        }

        enforcePathEscapeProtection(repoRoot, targetPath);

        if (!Files.exists(targetPath)) {
            throw new IllegalArgumentException("Path does not exist: " + relativePath);
        }
        if (!Files.isDirectory(targetPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + relativePath);
        }

        try (Stream<Path> stream = Files.list(targetPath)) {
            List<String> items = stream
                    .filter(path -> !IGNORED_NAMES.contains(path.getFileName().toString()))
                    .map(path -> {
                        String name = path.getFileName().toString();
                        return Files.isDirectory(path) ? name + "/" : name;
                    })
                    .sorted()
                    .collect(Collectors.toList());

            if (items.isEmpty()) {
                return "(empty directory)";
            }
            return String.join("\n", items);
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory: " + e.getMessage(), e);
        }
    }

    @Tool(name = "read_file", description = "Reads text file content inside the repository. You must call list_directory first to locate the relative path of the file.")
    public String readFile(
            @ToolParam(description = "Absolute path to the git repository root", required = true) String repoPath,
            @ToolParam(description = "Relative path of the file to read within the repository", required = true) String relativePath) {
        Path repoRoot = validateAndNormalizeRepoPath(repoPath);
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        Path targetPath = repoRoot.resolve(relativePath).normalize();
        enforcePathEscapeProtection(repoRoot, targetPath);

        if (!Files.exists(targetPath)) {
            throw new IllegalArgumentException("File does not exist: " + relativePath);
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new IllegalArgumentException("Path is not a regular file: " + relativePath);
        }

        try {
            return Files.readString(targetPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Tool(name = "write_file", description = "Writes or overwrites text content into a file inside the repository. Call list_directory and read_file before calling write_file.")
    public String writeFile(
            @ToolParam(description = "Absolute path to the git repository root", required = true) String repoPath,
            @ToolParam(description = "Relative path of the file to write within the repository", required = true) String relativePath,
            @ToolParam(description = "Full text content to write into the file", required = true) String content) {
        Path repoRoot = validateAndNormalizeRepoPath(repoPath);
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        Path targetPath = repoRoot.resolve(relativePath).normalize();
        enforcePathEscapeProtection(repoRoot, targetPath);

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.writeString(targetPath, content == null ? "" : content, StandardCharsets.UTF_8);
            return "Successfully wrote to " + relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }
    }

    @Tool(name = "git_diff", description = "Runs git diff inside the repository to view uncommitted changes made so far. Call this tool after applying changes with write_file.")
    public String gitDiff(
            @ToolParam(description = "Absolute path to the git repository root", required = true) String repoPath) {
        Path repoRoot = validateAndNormalizeRepoPath(repoPath);

        try {
            Process process = new ProcessBuilder("git", "-C", repoRoot.toString(), "diff", "--", ".")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor();
            return output.trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute git diff: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("git diff process interrupted", e);
        }
    }

    @Tool(name = "run_in_sandbox", description = "Runs a shell command inside an isolated, ephemeral Docker container mounted to the repo. Use this to run the test suite (e.g. 'mvn test') after making a code change, to verify the fix actually works before finishing.")
    public String runInSandbox(
            @ToolParam(description = "Absolute path to the git repository root", required = true) String repoPath,
            @ToolParam(description = "Shell command to run inside the container", required = true) String command) {
        Path repoRoot = validateAndNormalizeRepoPath(repoPath);

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", repoRoot.toAbsolutePath().toString() + ":/workspace",
                    "-w", "/workspace",
                    "maven:3.9-eclipse-temurin-17",
                    "sh", "-c", command
            );

            Process process = processBuilder.start();

            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdoutBuf));
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderrBuf));

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(2, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                stdoutThread.join(1000);
                stderrThread.join(1000);
                return "Exit Code: -1\nStdout:\n" + stdoutBuf.toString().trim() + "\nStderr:\n" + stderrBuf.toString().trim()
                        + "\nError: Command execution timed out after 2 minutes";
            }

            stdoutThread.join();
            stderrThread.join();

            int exitCode = process.exitValue();
            String cleanStdout = sanitizeAndTruncateOutput(stdoutBuf.toString());
            String cleanStderr = sanitizeAndTruncateOutput(stderrBuf.toString());

            return "Exit Code: " + exitCode + "\nStdout:\n" + cleanStdout + "\nStderr:\n" + cleanStderr;

        } catch (IOException e) {
            return "Error: Docker command failed to start or Docker is not running: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Command execution interrupted: " + e.getMessage();
        }
    }

    private String sanitizeAndTruncateOutput(String output) {
        if (output == null || output.isBlank()) {
            return "(none)";
        }
        String filtered = output.lines()
                .filter(line -> !line.startsWith("Downloading") && !line.startsWith("Downloaded") && !line.contains(" Progress ("))
                .collect(Collectors.joining("\n"));

        if (filtered.length() > 3000) {
            int length = filtered.length();
            filtered = "... [truncated " + (length - 3000) + " characters] ...\n" + filtered.substring(length - 3000);
        }
        return filtered.trim();
    }

    private void readStream(InputStream inputStream, StringBuilder buffer) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            buffer.append(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ignore stream read error
        }
    }

    private Path validateAndNormalizeRepoPath(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        Path root = Path.of(repoPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("repoPath does not exist: " + repoPath);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("repoPath is not a directory: " + repoPath);
        }
        return root;
    }

    private void enforcePathEscapeProtection(Path repoRoot, Path targetPath) {
        if (!targetPath.startsWith(repoRoot)) {
            throw new IllegalArgumentException("Security violation: path resolves outside repoPath: " + targetPath);
        }
    }
}
