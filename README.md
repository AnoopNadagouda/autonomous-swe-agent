# swe-agent

A Spring Boot 3.3+ / Java 17 service that asks an OpenAI-compatible model on Groq to propose minimal code patches for a local repository, then materializes those proposed changes into a sibling output directory for review.

## Environment setup on Windows

Set your Groq API key in a new terminal session:

```powershell
setx GROQ_API_KEY "your-groq-api-key"
```

After running `setx`, open a new terminal or restart VS Code so the environment variable is available.

## Run the app

```powershell
mvn spring-boot:run
```

## Sample request

```bash
curl.exe -X POST http://localhost:8080/api/tasks/patch ^
  -H "Content-Type: application/json" ^
  -d "{\"repoPath\":\"C:\\path\\to\\your\\repo\",\"issueDescription\":\"Fix the startup error in the task controller\"}"
```

## Response shape

The API returns the model plan, the proposed files, and the sibling output directory path where the patch was applied.
