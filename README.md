# Autonomous SWE Agent Platform

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Driven-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![Model Context Protocol](https://img.shields.io/badge/MCP-Tool_Calling-8A2BE2?style=for-the-badge&logo=anthropic&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Sandboxed_Testing-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Groq](https://img.shields.io/badge/Groq_LLM-gpt--oss--120b-f55036?style=for-the-badge&logo=groq&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-Agentic_Loop-00599C?style=for-the-badge&logo=spring&logoColor=white)

An event-driven, multi-service platform that uses Large Language Models (Groq via Spring AI), Model Context Protocol (MCP) tool integration, Apache Kafka event streaming, PostgreSQL persistence, and isolated Docker sandboxes to autonomously explore codebases, apply patches, execute test suites, self-correct bugs, and serve real-time status history.

---

## System Architecture

The system is built on a **Decoupled Microservice Architecture** with event-driven communication:

```
[ Client / Browser Dashboard ]
        │
        │ POST /api/tasks (returns 202 Accepted immediately)
        ▼
┌─────────────────────────────────────────────────────────────┐
│  swe-agent (Orchestrator Service - Port 8080)               │
│  - Exposes REST API gateway with CORS enabled              │
│  - Persists state into PostgreSQL DB                       │
│  - Publishes task submission events to Kafka                │
│  - Serves Actuator /actuator/health & /prometheus          │
└──────────────┬──────────────────────────────▲───────────────┘
               │                              │
  TaskSubmittedEvent                          │ TaskStatusEvent
  (topic: task-submitted)                     │ (topic: task-status)
               │                              │
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│  Apache Kafka Broker (Port 9092)                            │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│  swe-agent-worker (Autonomous Worker Node - Port 8082)      │
│  - Consumes tasks & manages Spring AI ChatClient loop       │
│  - Interfaces with Groq LLM (openai/gpt-oss-120b)          │
│  - Emits real-time state transitions to Kafka               │
│  - Publishes unhandled errors to DLQ (task-dlq)             │
└──────────────┬──────────────────────────────────────────────┘
               │
               │ MCP Tool Calls over Streamable HTTP / SSE (Port 8081)
               ▼
┌─────────────────────────────────────────────────────────────┐
│  swe-agent-mcp-tools (MCP Tool Server - Port 8081)          │
│  - Implements 5 filesystem & execution tools                │
│  - Normalizes host & container paths across OS environments │
│  - Enforces path-escape security checks                     │
└──────────────┬──────────────────────────────────────────────┘
               │
               │ Runs commands inside ephemeral container
               ▼
┌─────────────────────────────────────────────────────────────┐
│  Docker Sandbox (Image: maven:3.9-eclipse-temurin-17)       │
│  - Ephemeral, isolated container mounted to repository root │
└─────────────────────────────────────────────────────────────┘
```

---

## Related Repositories

This repository (`autonomous-swe-agent`) contains the **Orchestrator API Service**. It is one component of a 4-repository microservice ecosystem that makes up the complete Autonomous SWE Agent Platform:

* 🛠️ **[swe-agent-worker](https://github.com/AnoopNadagouda/swe_agent-worker)**: The independent Kafka consumer service running the multi-turn LLM reasoning loop, prompt management, and tool execution orchestration.
* 📦 **[swe-agent-stack](https://github.com/AnoopNadagouda/swe-agent-stack)**: The local deployment stack (`docker-compose.yml`), Render cloud deployment blueprint (`render.yaml`), and setup documentation tying all 5 services together.
* 🖥️ **[swe-agent-dashboard](https://github.com/AnoopNadagouda/swe-agent-dashboard)**: The React + Vite frontend dashboard providing a live, real-time UI for dispatching tasks and monitoring execution traces.
* 🌐 **[swe-agent-mcp-tools](https://github.com/AnoopNadagouda/swe-agent-mcp-tools)**: The standalone Model Context Protocol (MCP) server providing filesystem, git diff, and Docker sandbox tools.

---

## Services & Port Mappings

1. **`swe-agent` (Orchestrator - Port 8080)**: Public REST API gateway for submitting tasks, querying status updates, and persisting records in PostgreSQL.
2. **`swe-agent-worker` (Worker - Port 8082)**: Background event consumer executing the Spring AI agentic loop, tool calls, and DLQ error emission.
3. **`swe-agent-mcp-tools` (MCP Server - Port 8081)**: Dedicated MCP server exposing file system operations, git diff generation, and sandboxed test execution.
4. **PostgreSQL 16 (Port 5432)**: Relational data store persisting task execution history, status transitions, tool traces, and final git diffs.
5. **Apache Kafka (Broker - Port 9092)**: High-throughput event streaming backbone for asynchronous task dispatch, status streaming, and DLQ routing.
6. **Agent Dashboard (Port 5173)**: React frontend dev server for task dispatch and live execution tracking.

---

## How It Works (Step-by-Step)

1. **Task Submission**: The client sends an HTTP `POST /api/tasks` request with target `repoPath` and `issueDescription`. The Orchestrator generates a unique `taskId` (UUID), persists a `PENDING` record in PostgreSQL (`tasks` table), publishes a `TaskSubmittedEvent` to Kafka (`swe-agent.task-submitted`), and immediately returns HTTP `202 Accepted`.
2. **Async Task Consumption**: The `swe-agent-worker` consumes the event from Kafka and initiates the multi-turn agent loop.
3. **Repository Exploration**: The worker invokes MCP tools (`list_directory`, `read_file`) to explore the target codebase structure safely.
4. **In-Place Modification**: The worker applies code fixes in-place via the `write_file` tool inside the mounted working tree.
5. **Sandboxed Verification**: The worker invokes `run_in_sandbox` to run `mvn test` inside an isolated, ephemeral Docker container.
6. **Self-Correction & DLQ**: If tests fail, stderr/stdout feedback is routed back to the LLM for self-correction (up to 3 attempts). Uncaught exceptions publish to `swe-agent.task-dlq`.
7. **Postgres State & Live Polling**: State transitions (`PLANNING`, `CODING`, `TESTING`, `COMPLETED`, `FAILED`) are streamed to Kafka and updated in PostgreSQL for real-time frontend dashboard polling via `GET /api/tasks/{taskId}`.

---

## Tech Stack & Rationale

- **Spring Boot 3.3+ / Java 17**: Core application framework offering robust production-ready services, dependency injection, and native web server support.
- **Spring Data JPA & PostgreSQL**: Relational database persistence ensuring state durability across service restarts and scale-out.
- **Spring AI**: Unified AI abstraction framework simplifying prompt engineering, model integration with Groq/OpenAI, and tool callbacks.
- **Model Context Protocol (MCP)**: Standardized protocol providing secure, structured context injection and safe tool invocation interfaces for LLMs.
- **Apache Kafka (`spring-kafka`)**: Distributed event streaming platform enabling asynchronous, non-blocking task processing and state streaming.
- **Docker**: Containerization technology powering clean, reproducible, and isolated sandbox environments for running unverified user test suites.
- **Spring Boot Actuator & Micrometer**: Production monitoring exposing `/actuator/health` and Prometheus metrics (`/actuator/prometheus`).

---

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Docker Desktop**: Required for PostgreSQL 16 (`swe-agent-postgres`), Kafka broker (`swe-agent-kafka`), and sandboxed container execution (`maven:3.9-eclipse-temurin-17`).
- **PostgreSQL 16**: Database `sweagent`, User `sweagent`, Password `sweagent` on port `5432`.
- **Apache Kafka Broker**: Running locally or in Docker on port `9092`.
- **Groq API Key**: Environment variable `GROQ_API_KEY` set with a valid key.

---

## Setup & Startup Instructions

Services can be launched individually or using the local Docker Compose stack in `swe-agent-stack`:

### 1. Set Environment Variable
```powershell
$env:GROQ_API_KEY="your-groq-api-key"
```

### 2. Start Full Local Stack via Docker Compose
```powershell
cd C:\Users\ANOOP\Desktop\projects\swe-agent-stack
docker compose up -d --build
```

### 3. Individual Service Startup (Alternative)
If running services natively outside Docker Compose:
1. **PostgreSQL**: `docker run -d --name postgres -p 5432:5432 -e POSTGRES_DB=sweagent -e POSTGRES_USER=sweagent -e POSTGRES_PASSWORD=sweagent postgres:16-alpine`
2. **Kafka**: `docker run -d --name kafka -p 9092:9092 apache/kafka:latest`
3. **MCP Tools**: `cd swe-agent-mcp-tools && mvn spring-boot:run`
4. **Worker Agent**: `cd swe-agent-worker && mvn spring-boot:run`
5. **Orchestrator API**: `cd autonomous-swe-agent && mvn spring-boot:run`
6. **Agent Dashboard**: `cd swe-agent-dashboard && npm run dev`

---

## REST API Reference

### 1. Submit Task
`POST /api/tasks`

#### Request Body (`TaskCreateRequest`)
```json
{
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "issueDescription": "The add method in Calculator.java returns the wrong result, it subtracts instead of adding."
}
```

#### Response (`202 Accepted`)
```json
{
  "taskId": "f5397d7b-6233-4e26-b3b8-4d1138a62f41",
  "status": "PENDING"
}
```

---

### 2. Poll Task Status
`GET /api/tasks/{taskId}`

#### Response (`200 OK` - `TaskRecord`)
```json
{
  "taskId": "f5397d7b-6233-4e26-b3b8-4d1138a62f41",
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "issueDescription": "The add method in Calculator.java returns the wrong result, it subtracts instead of adding.",
  "currentStatus": "COMPLETED",
  "summary": "Explored Calculator.java, identified subtraction bug, updated code to addition, verified with mvn test in Docker sandbox.",
  "gitDiff": "diff --git a/src/main/java/Calculator.java b/src/main/java/Calculator.java\nindex 302a05e..8b00a29 100644\n--- a/src/main/java/Calculator.java\n+++ b/src/main/java/Calculator.java\n@@ -2,3 +2,3 @@ public class Calculator {\n     public int add(int a, int b) {\n-        return a - b;\n+        return a + b;\n     }",
  "finalTestResult": "Exit Code: 0\nStdout:\n[INFO] BUILD SUCCESS...",
  "attemptsMade": 1,
  "createdAt": 1784965447170,
  "updatedAt": 1784965448278,
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
    }
  ],
  "history": [
    { "status": "PENDING", "summary": "Task submitted", "timestamp": 1784965447170 },
    { "status": "PLANNING", "summary": "Agent started exploring repository", "timestamp": 1784965447194 },
    { "status": "CODING", "summary": "Executing tool: write_file", "timestamp": 1784965447500 },
    { "status": "TESTING", "summary": "Executing tool: run_in_sandbox", "timestamp": 1784965447925 },
    { "status": "COMPLETED", "summary": "Explored Calculator.java...", "timestamp": 1784965448268 }
  ]
}
```

---

## Development History & Build Phases

- **Phase 0**: Single-shot patch generation directly writing output into a separate `-agent-output` directory without repository exploration or verification.
- **Phase 1**: Transitioned to a two-service architecture (`swe-agent` + `swe-agent-mcp-tools` over MCP Streamable HTTP) enabling interactive multi-turn repository exploration (`list_directory`, `read_file`) and in-place file modifications (`write_file`).
- **Phase 2**: Introduced Docker sandbox test execution (`run_in_sandbox`), automated test verification (`mvn test`), a 3-attempt self-correction loop on test failures, and git diff reporting (`git_diff`).
- **Phase 3**: Upgraded to an asynchronous event-driven pipeline via Apache Kafka, decoupling the Orchestrator API (`swe-agent` on 8080) from a background Worker process (`swe-agent-worker` on 8082) with instant HTTP 202 Accepted task submission and real-time status streaming.
- **Phase 4**: Production Hardening & Full Stack Architecture.
  - **PostgreSQL Persistence**: Replaced in-memory map with Spring Data JPA entity (`TaskRecord`) and repository (`TaskRepository`) backed by PostgreSQL 16.
  - **Dead-Letter Queue (DLQ)**: Added uncaught exception counter and automatic routing to Kafka topic `swe-agent.task-dlq` in `swe-agent-worker`.
  - **Observability & Health Gates**: Added `spring-boot-starter-actuator` and Micrometer Prometheus metrics across all microservices exposing `/actuator/health` and `/actuator/prometheus`.
  - **Containerization & Deployment**: Created multi-stage Dockerfiles for all microservices, local orchestration via `docker-compose.yml`, Render deployment blueprint (`render.yaml`), and the React-based `swe-agent-dashboard` frontend.
  - **Reliability & Self-Correction Hardening**:
    - **Multi-Turn Agent Loop Fix**: Resolved early loop exit bug where worker terminated after a single tool call without executing `write_file` or running test suites.
    - **Hallucinated Tool-Call Recovery**: Implemented automated detection and corrective retries for model hallucinations across both narrated-text format and pseudo-XML (`<function=...`) syntax.
    - **Hard Completion Guard**: Enforced strict condition requiring `write_file` and a passing `run_in_sandbox` test before marking tasks `COMPLETED`.
    - **Groq Rate-Limit & Backoff**: Added exponential backoff and automatic pause/retry handling for Groq TPM (tokens per minute) rate limits and immediate failover on TPD quota limits.
    - **Hard 3-Attempt Cap Enforcement**: Enforced strict 3-attempt cap for test self-corrections, marking tasks `FAILED` with explicit error summaries when exceeding maximum attempts.
