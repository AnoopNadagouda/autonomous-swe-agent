# Autonomous SWE Agent (Phase 3: Event-Driven Kafka Pipeline)

A Java 17 / Spring Boot 3.3+ autonomous software engineering platform using an event-driven Kafka pipeline, Model Context Protocol (MCP) tool integration, and Docker sandbox test execution.

## System Architecture

The platform consists of four distinct components:

```
                          +-------------------------+
                          |  Client / REST API      |
                          +------------+------------+
                                       | POST /api/tasks (202 Accepted)
                                       v
                          +-------------------------+
                          |   swe-agent             | (Orchestrator - Port 8080)
                          |   (API & Task State)    |
                          +------+-----------+------+
                                 |           ^
        TaskSubmittedEvent       |           |  TaskStatusEvent
   (topic: task-submitted)       v           | (topic: task-status)
                          +-------------------------+
                          |   Apache Kafka Broker   | (Port 9092)
                          +------------+------------+
                                       |
                                       v
                          +-------------------------+
                          |   swe-agent-worker      | (Worker Node - Port 8082)
                          |   (Agent Loop & LLM)    |
                          +------------+------------+
                                       |
                                       v MCP Tool Calls (HTTP)
                          +-------------------------+
                          |  swe-agent-mcp-tools    | (MCP Server - Port 8081)
                          +------------+------------+
                                       |
                                       v Docker Run
                          +-------------------------+
                          |   Docker Sandbox        | (maven:3.9-eclipse-temurin-17)
                          +-------------------------+
```

### Components

1. **`apache/kafka` Broker (Port 9092)**:
   - Handles asynchronous event streaming between Orchestrator and Worker.
   - Topics:
     - `swe-agent.task-submitted`: Task submissions from Orchestrator to Worker.
     - `swe-agent.task-status`: Granular status transitions and progress updates from Worker to Orchestrator.

2. **`swe-agent` (Orchestrator - Port 8080)**:
   - Lightweight REST API layer.
   - Accepts task submissions via `POST /api/tasks`, publishes `TaskSubmittedEvent`, and returns HTTP `202 Accepted` immediately with a `taskId`.
   - Consumes status updates from Kafka (`swe-agent.task-status`) and maintains in-memory status history for polling via `GET /api/tasks/{taskId}`.

3. **`swe-agent-worker` (Worker Service - Port 8082)**:
   - Decoupled worker application consuming tasks from `swe-agent.task-submitted`.
   - Runs the autonomous agent execution loop with Groq LLM (`llama-3.3-70b-versatile`) via Spring AI.
   - Connects to `swe-agent-mcp-tools` over HTTP MCP protocol.
   - Emits real-time state transitions (`PLANNING`, `CODING`, `TESTING`, `COMPLETED`, `FAILED`) to Kafka.

4. **`swe-agent-mcp-tools` (MCP Server - Port 8081)**:
   - Spring Boot MCP tool server exposing repository tools: `list_directory`, `read_file`, `write_file`, `git_diff`, and `run_in_sandbox`.
   - Executes unit tests in isolated Docker containers.

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Docker Desktop**: Must be running for Kafka broker container and sandboxed test execution.
- **Groq API Key**: Set via environment variable `GROQ_API_KEY`.

## Setup & Running

### 1. Environment Variable
```powershell
$env:GROQ_API_KEY="your-groq-api-key"
```

### 2. Start Kafka Broker (Port 9092)
```powershell
docker run -d --name kafka -p 9092:9092 apache/kafka:latest
```

### 3. Start MCP Tools Server (Port 8081)
In `./swe-agent-mcp-tools`:
```powershell
mvn spring-boot:run
```

### 4. Start Agent Worker (Port 8082)
In `../swe-agent-worker`:
```powershell
mvn spring-boot:run
```

### 5. Start Orchestrator (Port 8080)
In main repository root (`swe-agent`):
```powershell
mvn spring-boot:run
```

## API Specification

### Submit Task
`POST /api/tasks`

Request Body:
```json
{
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "issueDescription": "The add method returns the wrong result, it subtracts instead of adding."
}
```

Response (`202 Accepted`):
```json
{
  "taskId": "c5f94d93-128a-4f51-a209-7d8fa315b9c1",
  "status": "PENDING"
}
```

### Poll Task Status
`GET /api/tasks/{taskId}`

Response (`200 OK`):
```json
{
  "taskId": "c5f94d93-128a-4f51-a209-7d8fa315b9c1",
  "repoPath": "C:\\Users\\ANOOP\\Desktop\\test-repo",
  "issueDescription": "The add method returns the wrong result, it subtracts instead of adding.",
  "currentStatus": "COMPLETED",
  "summary": "Explored Calculator.java, identified subtraction bug, updated code, verified with mvn test in Docker sandbox.",
  "toolTrace": [ ... ],
  "gitDiff": "diff --git a/src/main/java/Calculator.java...",
  "finalTestResult": "Exit Code: 0\nStdout:\n[INFO] BUILD SUCCESS",
  "attemptsMade": 1,
  "createdAt": 1721900000000,
  "updatedAt": 1721900045000,
  "history": [
    { "status": "PENDING", "summary": "Task submitted", "timestamp": 1721900000000 },
    { "status": "PLANNING", "summary": "Agent started exploring repository", "timestamp": 1721900002000 },
    { "status": "CODING", "summary": "Executing tool: write_file", "timestamp": 1721900015000 },
    { "status": "TESTING", "summary": "Executing tool: run_in_sandbox", "timestamp": 1721900025000 },
    { "status": "COMPLETED", "summary": "Explored Calculator.java...", "timestamp": 1721900045000 }
  ]
}
```
