# Autonomous SWE Agent Platform

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Driven-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![Model Context Protocol](https://img.shields.io/badge/MCP-Tool_Calling-8A2BE2?style=for-the-badge&logo=anthropic&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Sandboxed_Testing-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Groq](https://img.shields.io/badge/Groq_LLM-llama--3.3--70b-f55036?style=for-the-badge&logo=groq&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-Agentic_Loop-00599C?style=for-the-badge&logo=spring&logoColor=white)
![Python](https://img.shields.io/badge/Python-E2E_Testing-3776AB?style=for-the-badge&logo=python&logoColor=white)

An event-driven, multi-service platform that uses Large Language Models (Groq via Spring AI), Model Context Protocol (MCP) tool integration, Apache Kafka event streaming, and isolated Docker sandboxes to autonomously explore codebases, apply patches, execute test suites, self-correct bugs, and serve real-time status history.

---


## System Architecture

The system is built on a **Decoupled Microservice Architecture** with event-driven communication:

```
[ Client / User ]
        │
        │ POST /api/tasks (returns 202 Accepted immediately)
        ▼
┌─────────────────────────────────────────────────────────────┐
│  swe-agent (Orchestrator Service - Port 8080)               │
│  - Exposes REST API endpoints                               │
│  - Publishes task submission events to Kafka                │
│  - Consumes status updates & maintains in-memory history    │
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
│  - Interfaces with Groq LLM (llama-3.3-70b-versatile)       │
│  - Emits real-time state transitions to Kafka               │
└──────────────┬──────────────────────────────────────────────┘
               │
               │ MCP Tool Calls over Streamable HTTP (Port 8081)
               ▼
┌─────────────────────────────────────────────────────────────┐
│  swe-agent-mcp-tools (MCP Tool Server - Port 8081)          │
│  - Implements 5 filesystem & execution tools                │
│  - Enforces path-escape security checks                     │
└──────────────┬──────────────────────────────────────────────┘
               │
               │ Runs commands inside ephemeral container
               ▼
┌─────────────────────────────────────────────────────────────┐
│  Docker Sandbox (Image: maven:3.9-eclipse-temurin-17)       │
│  - Ephemeral, isolated container mounted to repository root  │
└─────────────────────────────────────────────────────────────┘
```

### Services & Port Mappings

1. **`swe-agent` (Orchestrator - Port 8080)**: Public REST API gateway for submitting tasks and polling status updates. Decoupled from agent execution.
2. **`swe-agent-worker` (Worker - Port 8082)**: Independent background consumer running the multi-turn Spring AI LLM loop, trace recording, and status event publishing.
3. **`swe-agent-mcp-tools` (MCP Server - Port 8081)**: Dedicated MCP server exposing file system operations, git diff generation, and sandboxed test execution.
4. **Apache Kafka (Broker - Port 9092)**: High-throughput event streaming backbone facilitating async communication between Orchestrator and Worker nodes.

---

## How It Works (Step-by-Step)

1. **Task Submission**: The client sends an HTTP `POST /api/tasks` request with the target `repoPath` and `issueDescription`. The Orchestrator generates a unique `taskId` (UUID), stores an initial `PENDING` record, publishes a `TaskSubmittedEvent` to Kafka, and immediately returns HTTP `202 Accepted`.
2. **Async Task Consumption**: The `swe-agent-worker` consumes the task from the `swe-agent.task-submitted` Kafka topic and initiates the agent execution loop.
3. **Repository Exploration**: The worker uses MCP tools (`list_directory`, `read_file`) to inspect the directory structure and relevant source code files without making assumptions.
4. **Direct Patch Application**: Upon identifying the bug, the worker calls `write_file` to edit the original source code in-place inside the repository working tree.
5. **Sandboxed Test Verification**: After writing code, the worker invokes `run_in_sandbox` to execute the repository test suite (`mvn test`) inside an isolated, ephemeral Docker container.
6. **Self-Correction Loop**: If tests fail, the worker captures stdout/stderr, feeds the failure output back to the LLM, revises the fix with `write_file`, and re-runs tests (up to 3 total attempts).
7. **Status Streaming & Real-Time Polling**: Throughout execution, the worker publishes `TaskStatusEvent` messages (`PLANNING`, `CODING`, `TESTING`, `COMPLETED`, `FAILED`) to Kafka. The Orchestrator updates its state store, allowing clients to track progress via `GET /api/tasks/{taskId}`.

---

## Tech Stack & Rationale

- **Spring Boot 3.3+ / Java 17**: Core application framework offering robust production-ready services, dependency injection, and native web server support.
- **Spring AI**: Unified AI abstraction framework simplifying prompt engineering, model integration with Groq/OpenAI, and tool callbacks.
- **Model Context Protocol (MCP)**: Standardized protocol providing secure, structured context injection and safe tool invocation interfaces for LLMs.
- **Apache Kafka (`spring-kafka`)**: Distributed event streaming platform enabling asynchronous, non-blocking task processing and state streaming.
- **Docker**: Containerization technology powering clean, reproducible, and isolated sandbox environments for running unverified user test suites.

---

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Docker Desktop**: Must be running on the host machine for both the Kafka broker container and sandboxed test execution (`maven:3.9-eclipse-temurin-17`).
- **Apache Kafka Broker**: Running locally on port `9092`.
- **Groq API Key**: Environment variable `GROQ_API_KEY` set with a valid key.

---

## Setup & Startup Instructions

Services **must be started in the following order**:

### 1. Set Environment Variable
```powershell
$env:GROQ_API_KEY="your-groq-api-key"
```

### 2. Start Kafka Broker (Port 9092)
```powershell
docker run -d --name kafka -p 9092:9092 apache/kafka:latest
```

### 3. Start MCP Tools Server (Port 8081)
```powershell
cd swe-agent-mcp-tools
mvn spring-boot:run
```

### 4. Start Agent Worker (Port 8082)
```powershell
cd ../swe-agent-worker
mvn spring-boot:run
```

### 5. Start Orchestrator API (Port 8080)
```powershell
cd ../swe-agent
mvn spring-boot:run
```

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
  "gitDiff": "diff --git a/src/main/java/Calculator.java b/src/main/java/Calculator.java\nindex 302a05e..8b00a29 100644\n--- a/src/main/java/Calculator.java\n+++ b/src/main/java/Calculator.java\n@@ -2,3 +2,3 @@ public class Calculator {\n     public int add(int a, int b) {\n-        return a - b;\n+        return a + b;\n     }",
  "finalTestResult": "Exit Code: 0\nStdout:\n[INFO] BUILD SUCCESS...",
  "attemptsMade": 1,
  "createdAt": 1784965447170,
  "updatedAt": 1784965448278,
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
