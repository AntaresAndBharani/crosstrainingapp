# Quickstart & Validation Guide: Reliable Multi-Order & Active-Cycle MCP Communication

**Feature**: `018-mcp-reliable-orders`
**References**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/mcp-tools.md](./contracts/mcp-tools.md)

## Prerequisites

- Node.js 22+ (`npm`) for `mcp-server/`
- JDK 17 + Gradle wrapper (`.\gradlew.bat`) for Android unit tests and lint
- PowerShell 7 (`pwsh`) for `scripts/tests/Invoke-ScriptTests.ps1`
- Authenticated `gcloud` CLI (`virgymia-c2cc7`) for post-CI Cloud Run live deployment

## 1. Build & Test MCP Connector (`mcp-server/`)

```powershell
Push-Location mcp-server
npm run build
npm test
Pop-Location
```

### Expected Outcomes
- TypeScript compiles cleanly with zero errors.
- Vitest suite passes 100%, verifying:
  - Every tool in `tools/list` advertises valid `ToolAnnotations` (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) and zero `$ref` / `exclusiveMinimum` schema keywords.
  - `manage_cycle_goals` executes multi-order prompts (e.g. `2RM -> 3RM Snatch` + 5 new RM goals) in a single atomic call, creating missing exercises once without duplicates.
  - `get_active_cycle` and `list_cycles` resolve `Cycle 4: Recomposition` accurately (both from heuristic highest/latest cycle and from explicit `isActiveCycle: true` mobile markers), and omitted `cycleId` mutations target the active cycle.
  - 10 concurrent `POST /mcp` mutations against the same user complete with zero lost updates.
  - Tombstone deletions (`deletedAtMillis`) hide deleted goals/routines/sessions from MCP read queries immediately.
  - `GET /mcp` returns `405` with `Allow: POST, DELETE`, `Mcp-Protocol-Version` is allowed/exposed in CORS, JSON-only `Accept` headers work, and user-scoped rate limiting isolates authenticated sessions behind shared proxy IPs.

## 2. Run Android Cloud Sync & Repository Verification Suite

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

### Expected Outcomes
- `McpRemoteSyncTest` and full Android unit test suite pass 100%, confirming:
  - Android `uploadUserData` stamps `isActiveCycle` on `cycle_goals`, `sessions`, and `rep_maxes`.
  - Android `downloadUserData` removes local `CycleGoal`, `Routine`, and `Session` rows when remote MCP tombstones (`deletedAtMillis`) are present, preventing goal resurrection on `Sync Now`.
- Script regression tests and `lintDebug` pass with zero errors.

## 3. Deploy Live Cloud Run Service (Post-Verification)

```powershell
gcloud run deploy crosstraining-mcp `
    --source=./mcp-server `
    --region=europe-west1 `
    --project=virgymia-c2cc7 `
    --allow-unauthenticated `
    --max-instances=1 `
    --min-instances=0 `
    --cpu=1 `
    --memory=256Mi `
    --cpu-throttling `
    --concurrency=20 `
    --timeout=30s `
    --set-secrets="MCP_JWT_SECRET=mcp-jwt-secret:latest" `
    --set-env-vars="FIREBASE_PROJECT_ID=virgymia-c2cc7" `
    --quiet
Invoke-RestMethod https://crosstraining-mcp-384900244521.europe-west1.run.app/health
```

### Expected Outcomes
- Live Cloud Run revision deploys cleanly while preserving existing OAuth secrets and environment variables, and `/health` returns `{"status":"ok","service":"crosstraining-mcp-server","version":"1.1.0"}` (note: Google Cloud Run's edge front-end reserves `/healthz`, so use `/health` externally).
