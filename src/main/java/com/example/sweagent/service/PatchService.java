package com.example.sweagent.service;

import com.example.sweagent.dto.LlmPatchResponse;
import com.example.sweagent.dto.PatchTaskRequest;
import com.example.sweagent.dto.PatchTaskResponse;
import com.example.sweagent.dto.ProposedFile;
import com.example.sweagent.exception.LlmResponseParseException;
import com.example.sweagent.exception.RepoPathNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PatchService {

    private static final Set<String> INCLUDED_EXTENSIONS = Set.of("java", "py", "js", "ts");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("node_modules", "target", ".git", "build");
    private static final int MAX_CODE_CONTEXT_CHARACTERS = 48_000;
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[A-Za-z0-9]{4,}");
    private static final String SYSTEM_PROMPT = "You are an autonomous software engineer. Given a codebase and an issue description, propose the minimal code changes to fix the issue. Respond ONLY with valid JSON matching this schema: { \"plan\": \"short explanation of the fix\", \"proposedFiles\": [ { \"path\": \"relative/path.java\", \"newContent\": \"full new file content\" } ] }. Do not include markdown code fences or any text outside the JSON.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PatchService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public PatchTaskResponse createPatch(PatchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        Path repoPath = validateRepoPath(request.repoPath());
        String codeContext = buildCodeContext(repoPath, request.issueDescription());
        String rawResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Issue description:\n" + request.issueDescription() + "\n\nCode context:\n" + codeContext)
                .call()
                .content();

        LlmPatchResponse llmResponse = parseResponse(rawResponse);
        Path outputDirectory = copyRepositoryToSiblingDirectory(repoPath, llmResponse.proposedFiles());
        return new PatchTaskResponse(llmResponse.plan(), llmResponse.proposedFiles(), outputDirectory.toString());
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

    private String buildCodeContext(Path repoPath, String issueDescription) {
        List<IndexedFile> files = collectRelevantFiles(repoPath, issueDescription);
        files.sort(Comparator.comparing(indexedFile -> indexedFile.relativePath().toString()));

        StringBuilder builder = new StringBuilder();
        for (IndexedFile indexedFile : files) {
            String section = "// FILE: " + indexedFile.relativePath() + System.lineSeparator() + indexedFile.content() + System.lineSeparator();
            if (builder.length() + section.length() > MAX_CODE_CONTEXT_CHARACTERS) {
                break;
            }
            builder.append(section);
        }
        return builder.toString();
    }

    private List<IndexedFile> collectRelevantFiles(Path repoPath, String issueDescription) {
        List<IndexedFile> allFiles = new ArrayList<>();
        try {
            Files.walkFileTree(repoPath, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(repoPath) && SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String extension = getExtension(file.getFileName().toString());
                    if (!INCLUDED_EXTENSIONS.contains(extension)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relativePath = repoPath.relativize(file);
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    allFiles.add(new IndexedFile(relativePath, content));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read repository files", exception);
        }

        String keywords = normalizeKeywords(issueDescription);
        if (keywords.isBlank()) {
            return allFiles;
        }

        List<IndexedFile> relevantFiles = new ArrayList<>();
        for (IndexedFile file : allFiles) {
            String haystack = (file.relativePath() + "\n" + file.content()).toLowerCase(Locale.ROOT);
            for (String keyword : keywords.split("\\s+")) {
                if (!keyword.isBlank() && haystack.contains(keyword)) {
                    relevantFiles.add(file);
                    break;
                }
            }
        }
        return relevantFiles.isEmpty() ? allFiles : relevantFiles;
    }

    private String normalizeKeywords(String issueDescription) {
        Matcher matcher = KEYWORD_PATTERN.matcher(issueDescription == null ? "" : issueDescription);
        StringJoiner joiner = new StringJoiner(" ");
        Set<String> uniqueKeywords = new HashSet<>();
        while (matcher.find()) {
            String keyword = matcher.group().toLowerCase(Locale.ROOT);
            if (uniqueKeywords.add(keyword)) {
                joiner.add(keyword);
            }
        }
        return joiner.toString();
    }

    private LlmPatchResponse parseResponse(String rawResponse) {
        String cleaned = stripMarkdownFences(rawResponse);
        try {
            return objectMapper.readValue(cleaned, LlmPatchResponse.class);
        } catch (JsonProcessingException exception) {
            throw new LlmResponseParseException("Failed to parse LLM output as JSON", rawResponse, exception);
        }
    }

    private String stripMarkdownFences(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private Path copyRepositoryToSiblingDirectory(Path repoPath, List<ProposedFile> proposedFiles) {
        Path parent = repoPath.getParent();
        if (parent == null) {
            throw new IllegalStateException("repoPath must have a parent directory so an output folder can be created");
        }

        Path outputDirectory = parent.resolve(repoPath.getFileName().toString() + "-agent-output");
        deleteIfExists(outputDirectory);
        copyDirectory(repoPath, outputDirectory);
        applyProposedFiles(outputDirectory, proposedFiles);
        return outputDirectory;
    }

    private void copyDirectory(Path source, Path target) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    Path destination = target.resolve(relative);
                    Files.createDirectories(destination);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    Path destination = target.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy repository to output directory", exception);
        }
    }

    private void applyProposedFiles(Path outputDirectory, List<ProposedFile> proposedFiles) {
        if (proposedFiles == null) {
            return;
        }
        for (ProposedFile proposedFile : proposedFiles) {
            if (proposedFile == null || proposedFile.path() == null || proposedFile.newContent() == null) {
                continue;
            }
            Path targetFile = outputDirectory.resolve(proposedFile.path()).normalize();
            if (!targetFile.startsWith(outputDirectory)) {
                throw new IllegalArgumentException("Proposed file path escapes the output directory: " + proposedFile.path());
            }
            try {
                Path parent = targetFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(targetFile, proposedFile.newContent(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write proposed file: " + proposedFile.path(), exception);
            }
        }
    }

    private void deleteIfExists(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clear existing output directory", exception);
        }
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private record IndexedFile(Path relativePath, String content) {
    }
}
