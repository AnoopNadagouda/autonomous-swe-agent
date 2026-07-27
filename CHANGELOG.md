# Changelog

All notable changes to the Autonomous SWE Agent Platform will be documented in this file.

## [1.0.0] - 2026-07-27

### Added
- **Token Budgeting & Context Reduction**: Implemented `TokenBudgetService` and `ContextReducer` to estimate prompt tokens and compress conversation history into structured reasoning summaries when context exceeds 4,000 tokens.
- **Jittered Exponential Backoff**: Implemented `RetryPolicy` with random jitter ($\pm 25\%$) and `Retry-After` header extraction for handling HTTP 429 rate limits.
- **Modular Service Architecture**: Decomposed monolithic worker into focused services (`TokenBudgetService`, `ContextReducer`, `RetryPolicy`, `ExecutionPlanner`, `GitDiffManager`, `PromptBuilder`, `PromptLogger`).
- **Security Command Sanitization**: Implemented `validateSandboxCommand` in `RepositoryTools` to block command injection vulnerabilities in Docker sandbox executions.
- **Unit Test Coverage**: Added 22 unit tests across `swe-agent-worker`, `swe-agent-mcp-tools`, and `autonomus agent`.

### Fixed
- Resolved `Groq TPM exceeded` rate limit failures.
- Fixed non-transactional entity mutation in JPA listener handlers.
