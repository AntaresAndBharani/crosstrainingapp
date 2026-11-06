# CrossTraining Remote MCP Server

Unified **Model Context Protocol (MCP)** server with **OAuth 2.1 + RFC 7591 Dynamic Client Registration (DCR)** for the CrossTraining Android visualizer app (`virgymia-c2cc7`).

## Supported AI Platforms (Single Endpoint)

| Platform | How to Connect | Transport |
| :--- | :--- | :--- |
| **Google Gemini (Web & Mobile)** | `gemini.google.com` $\rightarrow$ **Settings** $\rightarrow$ **Connected Apps** $\rightarrow$ **Add a custom app** $\rightarrow$ paste `https://<CLOUD_RUN_URL>/mcp` | Streamable HTTP + OAuth 2.1 DCR |
| **Claude Web / Desktop** | **Settings** $\rightarrow$ **Connectors** $\rightarrow$ **Add Custom Connector** $\rightarrow$ paste `https://<CLOUD_RUN_URL>/mcp` | Streamable HTTP + OAuth 2.1 DCR |
| **Claude Code CLI** | `claude mcp add --transport http crosstraining https://<CLOUD_RUN_URL>/mcp` | Streamable HTTP |
| **ChatGPT Web** | `chatgpt.com` $\rightarrow$ **Settings** $\rightarrow$ **Connectors / Plugins** $\rightarrow$ **Add custom MCP server** $\rightarrow$ paste `https://<CLOUD_RUN_URL>/mcp` | Streamable HTTP + OAuth 2.1 DCR |
| **Antigravity (Local Stdio)** | `MCP_DEFAULT_UID=<your-firebase-uid> node mcp-server/dist/index.js --stdio` | Local `stdio` |

## Exposed MCP Tools (16 Tools across 7 Firestore Collections)

- **Exercises:** `list_exercises`, `upsert_exercise`
- **Routine Templates:** `list_routines`, `upsert_routine`, `delete_routine`
- **Workout Sessions:** `list_sessions`, `upsert_session`, `delete_session`
- **Training Cycle Goals:** `list_cycle_goals`, `upsert_cycle_goal`, `delete_cycle_goal`
- **Personal Records (Rep Maxes):** `list_rep_maxes`, `record_rep_max`
- **Body Weight Tracking:** `list_weight_entries`, `upsert_weight_entry`, `delete_weight_entry`
- **Daily Nutrition & Fasting Logs:** `list_daily_logs`, `upsert_daily_log`

## Local Development & Testing

```powershell
cd mcp-server
npm install
npm run build
npm test
```

## Deploying to Google Cloud Run (with Hard Cost Caps & Billing Kill-Switch)

```powershell
.\mcp-server\scripts\setup-gcp-and-deploy.ps1
```
