# Autonomous SWE Agent Platform: REST API Reference

## Endpoints Summary

| Method | Endpoint | Description | Response Code |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/tasks` | Submit new autonomous repair task | `202 Accepted` |
| `GET` | `/api/tasks/{taskId}` | Retrieve task status, history, git diff, & test result | `200 OK` / `404` |
| `POST` | `/api/tasks/patch` | Synchronous patch creation helper | `200 OK` |

---

## Endpoint Specifications

### 1. Submit Task
`POST /api/tasks`

#### Request Body
```json
{
  "repoPath": "C:\\projects\\target-repo",
  "issueDescription": "Fix NullPointerException in UserService.java"
}
```

#### Response Header & Body (`202 Accepted`)
```json
{
  "taskId": "c6183508-9ce6-4017-864b-84ae14a7afdc",
  "status": "PENDING"
}
```

---

### 2. Get Task Details
`GET /api/tasks/{taskId}`

#### Response Body (`200 OK`)
```json
{
  "taskId": "c6183508-9ce6-4017-864b-84ae14a7afdc",
  "repoPath": "C:\\projects\\target-repo",
  "issueDescription": "Fix NullPointerException in UserService.java",
  "currentStatus": "COMPLETED",
  "summary": "Task completed successfully for target-repo. Issue resolved: 'Fix NullPointerException in UserService.java'. Executed 4 tool calls and verified fix.",
  "gitDiff": "diff --git a/UserService.java b/UserService.java\nindex 123..456 100644\n--- a/UserService.java\n+++ b/UserService.java\n@@ -10,2 +10,2 @@\n-if (user == null) return null;\n+if (user == null) return User.empty();",
  "finalTestResult": "Exit Code: 0\nStdout:\nBUILD SUCCESS\nExecution Time: 3420 ms",
  "attemptsMade": 1,
  "createdAt": 1753641000000,
  "updatedAt": 1753641015000,
  "history": [
    {
      "status": "PENDING",
      "summary": "Task submitted",
      "timestamp": 1753641000000
    },
    {
      "status": "PLANNING",
      "summary": "Agent started exploring repository",
      "timestamp": 1753641002000
    },
    {
      "status": "COMPLETED",
      "summary": "Task completed successfully",
      "timestamp": 1753641015000
    }
  ]
}
```
