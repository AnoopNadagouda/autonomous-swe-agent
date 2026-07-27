# Autonomous SWE Agent Platform: Production Deployment Guide

This guide covers deployment procedures for local Docker Compose, Render cloud hosting, and Vercel dashboard deployment.

---

## 1. Local Docker Compose Deployment

### Prerequisites
- Docker Engine 24.0+
- Docker Compose v2+
- Groq API Key

### Deployment Steps
```bash
# Clone repository stack
git clone https://github.com/AnoopNadagouda/autonomous-swe-agent.git
cd autonomous-swe-agent

# Set required environment variables
export GROQ_API_KEY="your_groq_api_key_here"

# Build and start services in detached mode
docker compose up --build -d

# Verify service health status
docker compose ps
```

---

## 2. Cloud Deployment on Render

The platform includes a native `render.yaml` deployment blueprint.

### Architecture Blueprint
- **`swe-agent`**: Web Service (Docker)
- **`swe-agent-worker`**: Worker Service (Docker)
- **`swe-agent-mcp-tools`**: Private Web Service (Docker)
- **PostgreSQL**: Render Managed PostgreSQL Database

### Deployment Steps
1. Push repository to GitHub.
2. Log into [Render Dashboard](https://dashboard.render.com).
3. Click **New +** $\rightarrow$ **Blueprint**.
4. Connect repository `autonomous-swe-agent`.
5. Environment Variables:
   - Set `GROQ_API_KEY` under Environment Group.
6. Click **Apply**. Render will automatically provision PostgreSQL, Kafka, and the 3 Docker services.

---

## 3. Dashboard Deployment on Vercel

The frontend dashboard (`swe-agent-dashboard`) is pre-configured for Vercel.

```bash
cd swe-agent-dashboard
npm install
npm run build
```

Set environment variable in Vercel settings:
`NEXT_PUBLIC_API_BASE_URL=https://your-orchestrator-app.onrender.com`
