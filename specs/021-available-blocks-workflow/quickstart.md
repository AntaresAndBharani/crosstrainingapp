# Quickstart & Verification Guide: `SPEC-021`

## 1. Local Verification Commands
```powershell
# 1. Remote MCP server build & unit/schema contract tests (33 tools, 0 $ref / exclusiveMinimum)
Push-Location mcp-server; npm run build; npm test; Pop-Location

# 2. Android JVM unit tests (AvailableBlockCodec, Repository multi-PR sync, ProgressAnalytics, existing UI state tests)
.\gradlew.bat testDebugUnitTest --no-daemon

# 3. Cross-platform PowerShell workflow/script tests
.\scripts\tests\Invoke-ScriptTests.ps1

# 4. Android Lint & Debug APK + local_test\latest.apk sync
.\gradlew.bat lintDebug --no-daemon
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

## 2. End-to-End Flow Smoke Path
1. Author an Available Block via MCP `upsert_available_block` (e.g., `"Clean Complex"`, exercises `["Clean Pull", "Clean", "Push Jerk"]`, tracked PRs `Clean 3RM`, `Push Jerk 3RM`) OR via Library `+` `QuickCreateBlockSheet`.
2. In Library → `Available Blocks ({count})`, filter via `"Search available blocks or exercises..."`, verify instructions expand/collapse and `🔥 2 tracked PR(s)` badge, then tap `Log Block ▸`.
3. In Log Session, inspect weight-only tiles (4-col normal, 2-col at `fontScale >= 1.4f`), enter `95,5` or leave bodyweight blank, verify auto-inherited PR chips toggle green `🔥 NEW PR`, open `+ Record PR 🔥` multi-movement sheet, and save via `"Save Session ({N} New PRs)"`.
4. Open Progress → `"By block"` to verify block KPIs/chart/expandable history and `"By exercise"` to verify PR pills showing `"Achieved in Clean Complex · {Date}"`.
