# Autonomous SWE Agent (Phase 1)

A Spring Boot 3.3+ / Java 17 autonomous software engineering agent that uses Groq via Spring AI and a real Model Context Protocol (MCP) tool-calling loop for repository exploration, inspection, and direct code modifications.

## Architecture

This system uses a **Two-Service Architecture**:

1. **`swe-agent-mcp-tools` (Port 8081)**:
   - Sibling Spring Boot project at `../swe-agent-mcp-tools`.
   - Acts as an MCP server using `spring-ai-starter-mcp-server-webmvc` over Streamable HTTP protocol.
   - Exposes repository tools: `list_directory`, `read_file`, `write_file`, `git_diff`.
   - Enforces path-escape security checks on all operations.

2. **`swe-agent` (Port 8080)**:
   - Main agent application (this repository).
   - Acts as an MCP client using `spring-ai-starter-mcp-client` connected to `http://localhost:8081`.
   - Runs a multi-turn agent interaction loop with Groq LLM to explore repository files before applying fixes.

> **Note on Direct Repository Editing**:
> The `write_file` tool edits the **original repository directly** in place. No temporary `-agent-output` copy directory is created so that `git_diff` operates directly on a real git working tree.

Both services **must be running simultaneously** for patch generation tasks to work.

## Setup & Running

### 1. Environment Variable
Set your Groq API key:
```powershell
$env:GROQ_API_KEY="your-groq-api-key"
```

### 2. Start MCP Tools Server (Port 8081)
In `../swe-agent-mcp-tools`:
```powershell
mvn spring-boot:run
```

### 3. Start SWE Agent Main App (Port 8080)
In this directory (`autonomus agent`):
```powershell
mvn spring-boot:run
```

## REST API Request Example

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/tasks/patch" -Method Post `
  -ContentType "application/json" `
  -Body (@{
    repoPath = "C:\Users\ANOOP\Desktop\test-repo"
    issueDescription = "The add method in Calculator.java returns the wrong result. It should add the two numbers but instead subtracts them."
  } | ConvertTo-Json)
```

## Response DTO
Returns a `PatchTaskResponse` containing:
- **`summary`**: LLM's final plan and fix explanation.
- **`toolTrace`**: List of tool calls made (`toolName`, `input`, `output`) proving step-by-step repository exploration.
- **`gitDiff`**: Output of `git diff` on the target repository.
- **`repoPath`**: Absolute path of the patched repository.
