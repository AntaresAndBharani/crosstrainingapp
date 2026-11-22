# Implementation Plan: Body-Weight Percentage Index (`% BW`) Across Tracking Pages & Remote MCP

**Branch**: `feat/029-bodyweight-percentage-index` | **Date**: 2026-11-22 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/029-bodyweight-percentage-index/spec.md)  
**Input**: Feature specification from `specs/029-bodyweight-percentage-index/spec.md`

## Summary

Add a derived **`% BW`** relative-strength index (`liftedWeightKg / bodyWeightKg * 100`, `BigDecimal` `HALF_UP` to 1 decimal place trimmed when whole) across all Android tracking screens (`ProgressScreen` — `By exercise`, `By complex`, `By routine`, `Cycle goals`; `HistoryScreen` — `CompactSessionCard` & `HistoryBlockCard`) and Remote MCP read tools (`get_workout_summary`, `list_sessions`, `list_rep_maxes`, `list_cycle_goals`, `get_daily_routine_progress`), using an exact-date → 7-day-prior body-weight fallback (`~` estimated, `—` unavailable) with **zero Room database schema migration** and strict adherence to `design-system/crosstraining/MASTER.md` and `pages/progress-tracking.md`.

## Technical Context

- **Language / Platform**: Android (SDK 35, `minSdk = 26`, Kotlin JVM 17, Jetpack Compose, Material 3) + Remote MCP Server (`mcp-server/`, Node.js 22, TypeScript, `@modelcontextprotocol/sdk`).
- **Storage**: Room SQLite (`v10`, unchanged) + Cloud Firestore (unchanged).
- **Testing**: JUnit 4 (`testDebugUnitTest`), Android Lint (`lintDebug`), PowerShell Pester/script tests (`Invoke-ScriptTests.ps1`), Vitest (`mcp-server/test/mcp-server.test.ts`).
- **Design System**: `design-system/crosstraining/MASTER.md`, `design-system/crosstraining/pages/progress-tracking.md`, `.agents/rules/03_compose_ui_standards.md`.

## Constitution Check

- **I. MVVM & UDF**: Pure domain calculation resides in `RelativeStrengthAnalytics.kt` and `DailyRoutineCodec.kt`; UI screens consume memoized/ViewModel flows with `collectAsStateWithLifecycle()`. PASS.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: `RelativeStrengthAnalytics` uses `BigDecimal` `HALF_UP` rounding (`scale = 1`), explicit date inputs, and converts `lbs` → `kg` so `% BW` is unit-invariant. PASS.
- **III. Test-First & 100% CI Parity**: Covered by `RelativeStrengthAnalyticsTest.kt` and `mcp-server.test.ts`, plus full Gradle/Lint/Script/MCP test suite before push. PASS.
- **IV. Responsive Mobile UX & Accessibility**: `MaterialTheme.colorScheme.*` across all 4 `AppThemeMode`s, `tnum`, `>= 48.dp` switchers/CTAs, `fontScale >= 1.3f` reflow on `360dp` viewports, non-color delta cues, and merged TalkBack semantics. PASS.
- **V. Offline-First & Zero Schema Migration**: Purely derived from existing `WeightEntry`, `SessionWithBlocks`, `RepMax`, `CycleGoal`, `Cycle` entities. PASS.
- **VI & VII. Reliable Flat MCP**: `45` tools, `ToolAnnotations`, `0` `$ref`, `0` `exclusiveMinimum` preserved; only additive response enrichment in read tools. PASS.

## Project Structure (Touched Files)

- `app/src/main/java/com/fractanomics/crosstraining/data/analytics/RelativeStrengthAnalytics.kt` *(new)*
- `app/src/main/java/com/fractanomics/crosstraining/ui/components/LineChart.kt` *(modify)*
- `app/src/main/java/com/fractanomics/crosstraining/util/DailyRoutineCodec.kt` *(modify)*
- `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` *(modify)*
- `app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt` *(modify)*
- `app/src/test/java/com/fractanomics/crosstraining/RelativeStrengthAnalyticsTest.kt` *(new)*
- `mcp-server/src/relative-strength.ts` *(new)*
- `mcp-server/src/store.ts` *(modify)*
- `mcp-server/test/mcp-server.test.ts` *(modify)*
