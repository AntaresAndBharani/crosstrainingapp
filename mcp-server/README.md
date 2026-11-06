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

## Exposed MCP Tools (18 Tools across 7 Firestore Collections)

- **Exercises:** `list_exercises`, `upsert_exercise`
- **Routine Templates:** `list_routines`, `upsert_routine`, `delete_routine`
- **Workout Sessions:** `list_sessions`, `upsert_session`, `delete_session`
- **Training Cycle Goals:** `list_cycle_goals`, `upsert_cycle_goal`, `delete_cycle_goal`
- **Personal Records (Rep Maxes):** `list_rep_maxes`, `record_rep_max`
- **Body Weight Tracking:** `list_weight_entries`, `upsert_weight_entry`, `delete_weight_entry`
- **Daily Nutrition & Fasting Logs:** `list_daily_logs`, `upsert_daily_log`

## Configuration

| Variable | Required | Purpose |
| :--- | :--- | :--- |
| `MCP_JWT_SECRET` | **Yes** (HTTP mode) | Random value (>= 32 chars) that signs OAuth access/refresh tokens and stateless DCR `client_id`s. The server refuses to start without it. The deploy script stores it in Secret Manager (`mcp-jwt-secret`). Rotating it revokes every token and client registration. |
| `FIREBASE_PROJECT_ID` | No | Defaults to `virgymia-c2cc7`. |
| `FIREBASE_WEB_API_KEY` | No | Firebase Web API key for the `/authorize` login page. |
| `MCP_ISSUER_URL` | No | Public base URL; inferred from `X-Forwarded-*` headers when omitted. |
| `MCP_DEV_BEARER_TOKEN` + `MCP_DEFAULT_UID` | No | Static dev token mapped to a single uid. **Never set these on a public deployment.** |
| `MCP_DEFAULT_UID` / `MCP_DEFAULT_ENV` | `--stdio` only | Target uid / environment for local stdio mode. |

### OAuth security model

- PKCE (`S256` only) is mandatory; `plain` is rejected.
- DCR is stateless: the registration (redirect URIs, auth method) is HMAC-signed into the `client_id`, so it survives Cloud Run scale-to-zero. Codes are only issued to a registered `redirect_uri` (loopback `http://localhost` URIs may use any port, per RFC 8252).
- Codes and refresh tokens are bound to the `client_id` they were issued to.

### Android sync contract

MCP writes stamp records with `updatedAtMillis`. The app's manual **Sync Now** downloads first and only imports stamped exercises, routines, sessions, cycle goals and rep maxes, then uploads the full local state. That upload clears the stamps. Background uploads (for example after logging a weight) replace the remote lists **without** downloading first, so open the app and run **Sync Now** after making MCP edits.

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
