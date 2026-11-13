# Implementation Plan: End-of-Cycle Estimated Body Weight on Weekly Body Weight

**Branch**: `feat/020-estimated-finish-weight` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-estimated-finish-weight/spec.md`

## Summary

Add dual end-of-cycle estimated body weight projections derived from the calibrated `3:4:2:1` `Weekly Body Weight` composite—showing both the **compounded weekly Momentum `%` estimate** (`BW_weekly * max(0, 1 + displayedMomentumPct / 100)^weeksRemaining`) and the **linear weekly Pace estimate** (`max(0, BW_weekly_unit + round2(pace_unit) * weeksRemaining)`)—inside the `✦ Weekly Body Weight` section of `CycleMomentumEstimationsCard` (`GOAL ARRIVAL ESTIMATIONS`) directly beneath `"Pace: ... · Momentum: ..."` and above `CorridorRangeGauge`, with matching fields in the Remote MCP Server's `computeWeightMomentumAnalytics` (`get_weight_momentum_analytics`).

## Technical Context

**Language/Version**: Kotlin (JVM 17) on Android; TypeScript (Node.js 22) on `mcp-server/`
**Primary Dependencies**: Jetpack Compose Material 3, Kotlin `java.time` / `BigDecimal`, Vitest / Supertest (`mcp-server/`)
**Storage**: N/A (pure deterministic analytics derived from existing `WeightEntry` and `Cycle` entities; Room stays at `v10`)
**Testing**: JUnit4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), Vitest (`npm test` in `mcp-server/`)
**Target Platform**: Android (SDK 35, minSdk 26) & Google Cloud Run MCP Server
**Project Type**: Mobile + Remote MCP Server
**Performance Goals**: Pure $O(1)$ arithmetic per cycle evaluation; zero main-thread blocking
**Constraints**: Display-Unit Arithmetic Consistency (`round1`, `round2`, `displayedMomentumPct`), 8-day calibration gate (`null` on Days 1–7), `360dp` viewport @ `1.3x` fontScale non-clipping, TalkBack `mergeDescendants = true` parity, flat Gemini-compatible MCP JSON Schemas (`0` `$ref`, `0` `exclusiveMinimum`)
**Scale/Scope**: 3 production source files (`FatLossAnalytics.kt`, `ProgressScreen.kt`, `mcp-server/src/schemas.ts`, plus `mcp-server/src/mcp.ts` description) and 3 unit test suites (`FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`, `mcp-server/test/mcp-server.test.ts`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|-----------|------------|-------|
| **I. MVVM & Unidirectional Data Flow** | PASS | `ProgressScreen.kt` renders pre-computed `CycleMomentumState.Active` via pure stateless formatting helpers; zero business math in Composables. |
| **II. Pure Deterministic Analytics & Display-Unit Consistency** | PASS | `FatLossAnalytics.computeEstimatedFinishWeights` is pure with injected `today`/`weeksRemaining`, rounds displayed inputs (`displayedMomentumPct`, `round2(pace)`), and clamps at `0.0`. |
| **III. Test-First & 100% CI Parity** | PASS | Comprehensive JUnit (`FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`) and Vitest (`mcp-server.test.ts`) tests plus full pre-PR verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `npm run build && npm test`). |
| **IV. Responsive Mobile UX & Accessibility** | PASS | Unbounded text wrapping (`bodySmall`, `tnum`) prevents clipping on `360dp` @ `1.3x`+; merged `contentDescription` announces the estimated finish weights cleanly. |
| **V. Offline-First & Environment Isolation** | PASS | Zero Room schema changes (`v10`), zero `gradle.properties` machine paths. |
| **VI. Agentic Collaboration & PR Governance** | PASS | Feature branch `feat/020-estimated-finish-weight`, `CHANGELOG.md` updated under `## [Unreleased]`, PR opened and CI monitored. |
| **VII. Reliable & Client-Compatible MCP** | PASS | Additive output fields in `computeWeightMomentumAnalytics`; input Zod schemas remain flat (`0` `$ref`, `0` `exclusiveMinimum`). |

## Project Structure

### Documentation (this feature)

```text
specs/020-estimated-finish-weight/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   ├── requirements.md
│   └── domain-quality.md
├── contracts/
│   └── estimated-finish-weight-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/analytics/FatLossAnalytics.kt
└── ui/screens/ProgressScreen.kt

app/src/test/java/com/fractanomics/crosstraining/
├── data/analytics/FatLossAnalyticsTest.kt
└── ui/screens/WeightProgressionCardTest.kt

mcp-server/
├── src/schemas.ts
├── src/mcp.ts
└── test/mcp-server.test.ts
```

**Structure Decision**: Extend the existing pure analytics (`FatLossAnalytics.kt`), Compose card (`ProgressScreen.kt`), and MCP analytics helper (`mcp-server/src/schemas.ts`) in place rather than introducing redundant wrapper files.

## Complexity Tracking

No Constitution violations; complexity tracking not required.
