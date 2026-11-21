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

## Exposed MCP Tools (28 Tools across 7 Firestore Collections)

Every tool includes explicit MCP `ToolAnnotations` (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint: false`) and flat JSON Schema declarations (`0` `$ref`, `0` `exclusiveMinimum`) for Google Gemini, Claude, ChatGPT, and Antigravity compatibility.

- **Cycles & Workout Summary:** `get_active_cycle`, `list_cycles`, `get_workout_summary`
- **Training Cycle Goals:** `list_cycle_goals`, `manage_cycle_goals` *(primary multi-order batch upsert, `previousTargetReps` RM conversion & delete tool)*, `upsert_cycle_goal`, `delete_cycle_goal`
- **Exercises:** `list_exercises`, `upsert_exercise`, `batch_upsert_exercises`
- **Routine Templates:** `list_routines`, `upsert_routine`, `delete_routine`
- **Workout Sessions:** `list_sessions`, `upsert_session`, `delete_session`
- **Personal Records (Rep Maxes):** `list_rep_maxes`, `record_rep_max`, `batch_record_rep_maxes`
- **Body Weight & Momentum Analytics:** `list_weight_entries`, `upsert_weight_entry`, `batch_upsert_weight_entries`, `delete_weight_entry`, `get_weight_momentum_analytics`
- **Daily Nutrition & Fasting Logs:** `list_daily_logs`, `upsert_daily_log`, `batch_upsert_daily_logs`, `delete_daily_log`

## Configuration

| Variable | Required | Purpose |
| :--- | :--- | :--- |
| `MCP_JWT_SECRET` | **Yes** (HTTP mode) | Random value (>= 32 chars) that signs OAuth access/refresh tokens and stateless DCR `client_id`s. The server refuses to start without it. The deploy script stores it in Secret Manager (`mcp-jwt-secret`). Rotating it revokes every token and client registration. |
| `FIREBASE_PROJECT_ID` | No | Defaults to `virgymia-c2cc7`. |
| `FIREBASE_WEB_API_KEY` | No | Firebase Web API key for the `/authorize` login page. |
| `MCP_ISSUER_URL` | No | Public base URL; inferred from `X-Forwarded-*` headers when omitted. |
| `MCP_DEV_BEARER_TOKEN` + `MCP_DEFAULT_UID` | No | Static dev token mapped to a single uid. **Never set these on a public deployment.** |
| `MCP_DEFAULT_UID` / `MCP_DEFAULT_ENV` | `--stdio` only | Target uid / environment for local stdio mode. |
| `SHEETS_EXPORT_TOKEN` | No | Dedicated Bearer token (>= 32 chars, Secret Manager `sheets-export-token`) scoped solely to read-only `GET /export/sheets`. Rejected on `/mcp`. |
| `SHEETS_EXPORT_UID` | No (required if `SHEETS_EXPORT_TOKEN` set) | Target Firebase UID exported by `GET /export/sheets`. |
| `SHEETS_EXPORT_ENV` | No | Default Firestore environment for `GET /export/sheets` (`production` or `snapshot`, defaults to `production`). |
| `SHEETS_EXPORT_SUBCYCLE_TYPE` | No | Optional default weight corridor (`FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`); inferred from active cycle name when omitted. |
| `SHEETS_EXPORT_RATE_LIMIT_PER_MIN` | No | Per-minute IP rate limit for `GET /export/sheets` (defaults to `60`). |

## Read-Only Google Sheets Analytics Bridge (`GET /export/sheets`)

Exposes a read-only JSON snapshot across all 7 Firestore collections (`active_cycle`, `weights`, `sessions_sets`, `prs`, `cycles`, `cycle_goals`, `daily_logs`) for prototyping charts with **Gemini in Google Sheets**:

1. Deploy with `-SheetsExportUid` to provision `sheets-export-token` in Secret Manager and attach `SHEETS_EXPORT_UID` / `SHEETS_EXPORT_ENV` on Cloud Run:
   ```powershell
   .\mcp-server\scripts\setup-gcp-and-deploy.ps1 -SheetsExportUid "<YOUR_FIREBASE_UID>"
   ```
   Subsequent redeploys without `-SheetsExportUid` preserve existing `SHEETS_EXPORT_UID` and `SHEETS_EXPORT_ENV`.
2. In your Google Sheet, open **Extensions → Apps Script**, paste [`mcp-server/scripts/google-sheets-sync.gs`](scripts/google-sheets-sync.gs) into `Code.gs`, and configure **Project Settings → Script Properties**:
   - `EXPORT_URL` = `https://<CLOUD_RUN_URL>/export/sheets`
   - `EXPORT_TOKEN` = value of `gcloud secrets versions access latest --secret=sheets-export-token --project=virgymia-c2cc7`
3. Run `syncCrossTrainingData` once to authorize and populate the 6 data tabs (`Weights`, `Sessions_Sets`, `PRs`, `Cycles`, `Cycle_Goals`, `Daily_Logs`) plus `Dashboard`, then optionally run **CrossTraining → Install Daily Auto-Sync Trigger**.

### OAuth security model

- PKCE (`S256` only) is mandatory; `plain` is rejected.
- DCR is stateless: the registration (redirect URIs, auth method) is HMAC-signed into the `client_id`, so it survives Cloud Run scale-to-zero. Codes are only issued to a registered `redirect_uri` (loopback `http://localhost` URIs may use any port, per RFC 8252).
- Codes and refresh tokens are bound to the `client_id` they were issued to.

### Android sync contract & concurrency guarantees

- **Active cycle resolution:** Android uploads stamp `isActiveCycle` (`true`/`false`) alongside `cycleId` and `cycleName` on `cycle_goals`, `sessions`, and `rep_maxes`. When `cycleId` is omitted on MCP goal, session, or PR mutations, the server resolves the active cycle automatically (`isActiveCycle: true` first, then latest activity fallback).
- **Single-confirmation multi-order batching:** Multi-item prompts are consolidated into a single atomic call via `manage_cycle_goals` (supporting `upserts`, 1-step `previousTargetReps` RM conversion, and `deletes`), `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, and `batch_upsert_daily_logs`.
- **Tombstone deletion durability:** MCP writes stamp records with `updatedAtMillis`, and MCP deletions (`cycle_goals`, `routines`, `sessions`, `weight_entries`, `daily_logs`) write `deletedAtMillis` tombstones instead of silently splicing arrays. When the Android app runs **Sync Now**, `downloadUserData` applies both `updatedAtMillis` upserts and `deletedAtMillis` deletions locally before `uploadUserData` compacts the cloud snapshot—preventing deleted goals, routines, or sessions from resurrecting.
- **Atomic per-user mutations & proxy-safe rate limits:** Collection writes are serialized per `(env, uid)` via an in-process async mutex + Firestore `runTransaction`, and `/mcp` rate limiting isolates authenticated user tokens (`180 req/min/user`) behind shared Google proxy egress IPs (`300 req/min/IP` unauthenticated guard).

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
