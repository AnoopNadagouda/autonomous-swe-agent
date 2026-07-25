# Autonomous SWE Agent (Phase 2)

A Spring Boot 3.3+ / Java 17 autonomous software engineering agent that uses Groq via Spring AI and a real Model Context Protocol (MCP) tool-calling loop for repository exploration, inspection, direct code modifications, and sandboxed verification with self-correction.

## Capabilities

The agent autonomously resolves issues in codebases through an iterative loop:
1. **Repository Exploration**: Explores repo files and directory structure using `list_directory` and `read_file`.
2. **Direct Patching**: Applies changes directly to target files using `write_file`.
3. **Sandboxed Verification**: Executes the project's test suite (e.g., `mvn test`) inside an isolated, ephemeral Docker container using `run_in_sandbox`.
4. **Self-Correction Loop**: If tests fail, the agent analyzes the failure output, re-edits the code, and re-tests, repeating up to 3 attempts total.
5. **Git Diff Inspection**: Captures the exact uncommitted changes using `git_diff` before finishing.

## Architecture

This system uses a **Two-Service Architecture**:

1. **`swe-agent-mcp-tools` (Port 8081)**:
   - Sibling Spring Boot project at `./swe-agent-mcp-tools`.
   - Acts as an MCP server using Spring AI MCP Server over Streamable HTTP protocol.
   - Exposes tools: `list_directory`, `read_file`, `write_file`, `git_diff`, and `run_in_sandbox`.
   - Enforces path-escape security checks on all file operations.

2. **`swe-agent` (Port 8080)**:
   - Main orchestrator application.
   - Acts as an MCP client using Spring AI MCP Client connected to `http://localhost:8081`.
   - Manages the multi-turn agent loop with Groq LLM (`llama-3.3-70b-versatile`).

Both services must be running simultaneously for patch generation tasks to work.

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Groq API Key**: Set via environment variable `GROQ_API_KEY`.
- **Docker Desktop**: Must be running on the host machine for `run_in_sandbox` test execution (`maven:3.9-eclipse-temurin-17` container).

## Setup & Running

### 1. Environment Variable
Set your Groq API key:
```powershell
$env:GROQ_API_KEY="your-groq-api-key"
```

### 2. Start MCP Tools Server (Port 8081)
Navigate to `swe-agent-mcp-tools` and start the server:
```powershell
cd swe-agent-mcp-tools
mvn spring-boot:run
```

### 3. Start SWE Agent Orchestrator (Port 8080)
In the main project root directory:
```powershell
mvn spring-boot:run
```

## REST API Request & Response

### Request Example
`POST /api/tasks/patch`
```json
{
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "issueDescription": "The add method in Calculator.java returns the wrong result, it subtracts instead of adding."
}
```

### Sample Response (`PatchTaskResponse`)
```json
{
  "summary": "Explored Calculator.java, identified subtraction bug, replaced - with +, verified with mvn test in Docker sandbox (Exit Code: 0).",
  "toolTrace": [
    {
      "toolName": "list_directory",
      "input": "{\"repoPath\":\"C:\\\\Users\\\\ANOOP\\\\Desktop\\\\test-repo\",\"relativePath\":\".\"}",
      "output": "pom.xml\nsrc/"
    },
    {
      "toolName": "read_file",
      "input": "{\"repoPath\":\"C:\\\\Users\\\\ANOOP\\\\Desktop\\\\test-repo\",\"relativePath\":\"src/main/java/Calculator.java\"}",
      "output": "public class Calculator {\n    public int add(int a, int b) {\n        return a - b;\n    }\n}"
    },
    {
      "toolName": "write_file",
      "input": "{\"repoPath\":\"C:\\\\Users\\\\ANOOP\\\\Desktop\\\\test-repo\",\"relativePath\":\"src/main/java/Calculator.java\",\"content\":\"public class Calculator {\\n    public int add(int a, int b) {\\n        return a + b;\\n    }\\n}\"}",
      "output": "Successfully wrote to src/main/java/Calculator.java"
    },
    {
      "toolName": "run_in_sandbox",
      "input": "{\"repoPath\":\"C:\\\\Users\\\\ANOOP\\\\Desktop\\\\test-repo\",\"command\":\"mvn test\"}",
      "output": "Exit Code: 0\nStdout:\n[INFO] BUILD SUCCESS..."
    },
    {
      "toolName": "git_diff",
      "input": "{\"repoPath\":\"C:\\\\Users\\\\ANOOP\\\\Desktop\\\\test-repo\"}",
      "output": "diff --git a/src/main/java/Calculator.java b/src/main/java/Calculator.java..."
    }
  ],
  "gitDiff": "diff --git a/src/main/java/Calculator.java b/src/main/java/Calculator.java\nindex 1234567..89abcdef 100644\n--- a/src/main/java/Calculator.java\n+++ b/src/main/java/Calculator.java\n@@ -2,3 +2,3 @@ public class Calculator {\n     public int add(int a, int b) {\n-        return a - b;\n+        return a + b;\n     }",
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "attemptsMade": 1,
  "finalTestResult": "Exit Code: 0\nStdout:\n[INFO] BUILD SUCCESS..."
}
```
