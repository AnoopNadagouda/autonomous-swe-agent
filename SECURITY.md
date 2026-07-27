# Security Policy & Architecture

## Security Architecture Overview

The Autonomous SWE Agent Platform is engineered with multi-layered isolation, path verification, and input sanitization to ensure safe execution when exploring untrusted repositories or running automated build scripts.

## Key Security Controls

### 1. Docker Sandbox Isolation
- Tool execution for `run_in_sandbox` occurs within ephemeral Docker containers (`maven:3.9-eclipse-temurin-17`).
- Container command validation in `RepositoryTools.java` (`validateSandboxCommand`) restricts executions to approved build tools (`mvn`, `./mvnw`, `gradle`, `./gradlew`, `npm test`, `pytest`) and rejects dangerous shell command injection operators (`;`, `&&`, `||`, backticks, `$()`).

### 2. Path Escape Protection
- All filesystem tool calls (`list_directory`, `read_file`, `write_file`) resolve paths relative to the normalized repository root (`repoPath`).
- `enforcePathEscapeProtection` verifies `targetPath.startsWith(repoRoot)` before executing any File I/O, preventing directory traversal attacks (`../`).

### 3. File System Exclusions
- The agent explicitly ignores sensitive or generated directories (`target/`, `.git/`, `node_modules/`, `build/`, `dist/`, binary files).

### 4. Secret & Credentials Policy
- Sensitive API keys (`GROQ_API_KEY`, database credentials) are injected via environment variables and never committed to source control.
