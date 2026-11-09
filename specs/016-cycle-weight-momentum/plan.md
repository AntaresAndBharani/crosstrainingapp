# Implementation Plan: Universal Cycle Weight Tracking & Momentum Engine

**Branch**: `feat/016-cycle-weight-momentum` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-cycle-weight-momentum/spec.md`

## Summary

Implement the Universal Cycle Weight Tracking & Momentum Engine (`SPEC-WEIGHT-CARD-01` and `SPEC-WEIGHT-SUBTYPE-SHEET-01`):
1. **Universal Cycle Scope & `WeightSubcycleType`**: Enable body weight tracking and subcycle velocity corridors (`WEIGHT_LOSS`: `-1.50%` to `-0.50%/wk`, `RECOMPOSITION`: `-0.50%` to `+0.25%/wk`, `WEIGHT_GAIN`: `+0.25%` to `+1.50%/wk`) across all training cycles (`STRENGTH_WEIGHTLIFTING` and `FAT_LOSS_BODYBUILDING`), persisted backward-compatibly in `Cycle.goal` metadata without altering the Room v10 schema.
2. **Continuous Daily Timeline (`REAL` vs `THEORETICAL`) & Cumulative/Lookback Multi-Horizon Averages**: Linearly interpolate daily `THEORETICAL` weights between consecutive `REAL` weigh-ins, reactively recalculate upon insert/edit/delete (including promoting an edited `THEORETICAL` day to `REAL`), and compute cumulative cold-start or historical-lookback `7D`, `14D`, `21D`, and `28D` moving averages.
3. **`3:4:2:1` Weekly Body Weight Composite, Momentum & Dynamic Finish Projection**: Compute $\text{BW}_{\text{weekly}} = (3\cdot\text{7D} + 4\cdot\text{14D} + 2\cdot\text{21D} + 1\cdot\text{28D})/10$, weekly percentage Momentum vs $t-7$ (with an 8-day warm-up calibration gate), diagnostic velocity zones, and compounded finish weight window $\text{BW}_{\text{weekly}} \times (1 + r)^{W_{\text{left}}}$.
4. **UI Components (`SPEC-WEIGHT-CARD-01` & `SPEC-WEIGHT-SUBTYPE-SHEET-01`) & Coaching MCP Integration**: Upgrade `ProgressScreen.kt` (`MultiHorizonGoalEstimationsBanner`, `WeightProgressionCard`, `CycleGoalsProgress`, `WeightOverviewContent`, and `SubcycleTypePickerBottomSheet`), `CyclesScreen.kt` (`CycleEditorDialog` & `CycleCard`), `AppViewModel.kt`, and `mcp-server/` (`get_weight_momentum_analytics`).

## Technical Context

**Language/Version**: Kotlin 2.0 (JVM 17) for Android; TypeScript 5.x (Node 20+) for `mcp-server/`

**Primary Dependencies**: Jetpack Compose (Material 3), Room 2.6 (v10 schema), Kotlin Coroutines & StateFlow, Zod & `@modelcontextprotocol/sdk`

**Storage**: Room SQLite (`AppDatabase` version 10 — zero schema migration required via `Cycle.goal` metadata header `[subcycle=...]`) + Cloud Firestore (`weight_entries` LWW sync)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester-style script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Node test runner (`npm test` in `mcp-server/`)

**Target Platform**: Android SDK 35 (minSdk 26) + Node.js MCP Server

**Project Type**: Mobile Application (`app/`) + Coaching MCP Server (`mcp-server/`)

**Performance Goals**: O(N) daily timeline interpolation and rolling average calculation (< 2ms for 365 days of daily weight points); 60fps Compose rendering

**Constraints**: Preserve 100% backward compatibility with Room schema v10 (`10.json`) and all existing unit tests in `FatLossAnalyticsTest`, `WeightProgressionCardTest`, and `CyclesScreenTest`

**Scale/Scope**: 4 user stories (P1–P3), 15 functional requirements (`FR-001`–`FR-015`), touching Android domain/model/UI layers and `mcp-server/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow**: **PASS** — `ProgressScreen` and `CyclesScreen` observe immutable `StateFlow` state from `AppViewModel` and dispatch user intents (`updateCycleSubcycleType`, `saveWeightEntry`, `deleteWeightEntry`) through `AppViewModel` $\rightarrow$ `Repository`.
- **II. Pure Domain Analytics & Deterministic Precision**: **PASS** — All interpolation, cumulative/lookback horizon averages (`7D`, `14D`, `21D`, `28D`), `3:4:2:1` composite weight, weekly `%` momentum, corridor compliance, and compounded finish weight projections live in pure, deterministic functions in `FatLossAnalytics` / `WeightAnalytics` with explicit `LocalDate` injection.
- **III. Test-First & Zero CI Regression**: **PASS** — All legacy functions and overloads remain intact so existing unit tests pass without regression, and comprehensive new unit tests cover every new formula, edge case, and UI resolver.
- **IV. Material 3 & Accessible UI Craft**: **PASS** — Implements M3 semantic tokens, light/dark subtype pill palettes, `48dp` minimum touch target on `[Subtype ▾]`, responsive `FlowRow` wrapping for `<360dp` and `1.3x` font scale, and TalkBack `contentDescription` semantics.
- **V. Simplicity & Backward-Compatible Persistence**: **PASS** — Uses `[subcycle=...]` metadata encoding in `Cycle.goal` (`@Ignore` properties on `Cycle`) and deterministic domain derivation of `THEORETICAL` weights from `REAL` `weight_entries`, requiring zero Room schema migrations.

## Project Structure

### Documentation (this feature)

```text
specs/016-cycle-weight-momentum/
├── plan.md                                      # Implementation plan
├── research.md                                  # Phase 0 architectural decisions
├── data-model.md                                # Phase 1 domain & entity models
├── quickstart.md                                # Phase 1 validation guide
├── contracts/
│   └── cycle-weight-momentum-contract.md        # UI & MCP contracts
├── checklists/
│   ├── requirements.md                          # Spec quality checklist
│   └── domain-quality.md                        # Phase 6 requirements quality checklist
└── tasks.md                                     # Phase 7 actionable task list
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── analytics/
│   │   ├── FatLossAnalytics.kt                  # Interpolation, 7D/14D/21D/28D, 3:4:2:1 composite, momentum & corridor engine
│   │   └── WeightAnalytics.kt                   # Daily timeline helper integration
│   └── model/
│       ├── Cycle.kt                             # subcycleType, cleanGoal, withSubcycleType metadata encoding
│       └── Enums.kt                             # WeightSubcycleType & WeightMeasureSource enums
├── ui/
│   ├── AppViewModel.kt                          # updateCycleSubcycleType & universal cycle baseline support
│   └── screens/
│       ├── CyclesScreen.kt                      # Universal subcycle & body weight config in CycleEditorDialog & CycleCard
│       └── ProgressScreen.kt                    # SPEC-WEIGHT-CARD-01, SPEC-WEIGHT-SUBTYPE-SHEET-01, universal cycle weight card & Real/Theoretical history

app/src/test/java/com/fractanomics/crosstraining/
├── data/analytics/
│   └── FatLossAnalyticsTest.kt                  # Unit tests for interpolation, 3:4:2:1 composite, momentum & corridors
└── ui/screens/
    ├── CyclesScreenTest.kt                      # Unit tests for Cycle subcycle metadata & editor validation
    └── WeightProgressionCardTest.kt             # Unit tests for SPEC-WEIGHT-CARD-01 & SPEC-WEIGHT-SUBTYPE-SHEET-01 resolvers

mcp-server/
├── src/
│   ├── schemas.ts                               # WeightSubcycleType & momentum analytics schemas/helpers
│   └── mcp.ts                                   # get_weight_momentum_analytics tool
└── test/
    └── mcp-server.test.ts                       # MCP server unit tests for interpolation & momentum analytics
```

**Structure Decision**: Extends existing Android MVVM packages (`data/model`, `data/analytics`, `ui/screens`) and `mcp-server/src` without introducing unnecessary modules or database migrations.

## Complexity Tracking

No Constitution violations — table omitted.
