# Implementation Plan: Subcycle Taxonomy & Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)

**Branch**: `feat/017-subcycle-velocity-corridors` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-subcycle-velocity-corridors/spec.md`

## Summary

Implement the updated Subcycle Taxonomy and Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` and `SPEC-WEIGHT-CARD-02`):
1. **Updated Subcycle Taxonomy & Physiological Corridors**: Rename `Weight Loss` to `Fat Loss` (`FAT_LOSS`, `-1.00%` to `-0.25%/wk`), narrow `Recomposition` (`RECOMPOSITION`, `-0.25%` to `+0.25%/wk`), and rename `Weight Gain` to `Muscle Gain` (`MUSCLE_GAIN`, `+0.25%` to `+0.75%/wk`), while maintaining 100% backward compatibility with legacy `WEIGHT_LOSS` and `WEIGHT_GAIN` cycle metadata and enum references.
2. **5-Zone Universal Velocity Spectrum & Strict Inequality Diagnostic Engine**: Evaluate global outer safety bounds first (`< -1.00%/wk` $\rightarrow$ `Possible muscle loss`, `> +0.75%/wk` $\rightarrow$ `Suboptimal muscle gain`), then apply strict subcycle-inclusive inequality rules for `Fat Loss` (`Optimal`, `Slow fat loss pace`, `Out of fat loss range`), `Recomposition` (`Warning: Fat loss phase`, `Optimal`, `Warning: Muscle gain phase`), and `Muscle Gain` (`Out of muscle gain range`, `Slow muscle gain pace`, `Optimal`) with severity tiers (`OPTIMAL_GREEN`, `WARNING_AMBER`, `CRITICAL_RED`, `NEUTRAL`) and coaching helper subtitles.
3. **UI Upgrades (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)**: Enforce single-line non-wrapping corridor pill badges (`maxLines = 1`, `softWrap = false`, `12sp` `tabular-nums`, `8dp` horizontal padding) in `SubcycleTypePickerBottomSheet`, render the 5-Segment Corridor Gauge (`15% | 22.5% | 25% | 22.5% | 15%`), semantic status pill badge, and inline `↳ {diagnosticMessage}` subtitle exclusively on the `✦ Weekly Body Weight` composite row in `CycleMomentumEstimationsCard`, and render `7D`, `14D`, `21D`, and `28D` rows cleanly without redundant per-row badges or gauges.
4. **Coaching MCP Server Parity**: Update `mcp-server/src/schemas.ts` and `mcp-server/test/mcp-server.test.ts` to support `FAT_LOSS`, `RECOMPOSITION`, and `MUSCLE_GAIN` (plus legacy `WEIGHT_LOSS`/`WEIGHT_GAIN` inputs), tightened corridors, `global_safety_bounds`, and composite diagnostic fields (`severity`, `diagnostic_code`, `diagnostic_badge`, `diagnostic_message`).

## Technical Context

**Language/Version**: Kotlin 2.0 (JVM 17) for Android; TypeScript 5.x (Node 20+) for `mcp-server/`

**Primary Dependencies**: Jetpack Compose (Material 3), Room 2.6 (v10 schema), Kotlin Coroutines & StateFlow, Zod & `@modelcontextprotocol/sdk`

**Storage**: Room SQLite (`AppDatabase` version 10 — zero schema migration required; `Cycle.parseSubcycleMetadata` accepts both canonical `FAT_LOSS`/`MUSCLE_GAIN` and legacy `WEIGHT_LOSS`/`WEIGHT_GAIN` tokens)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Node test runner (`npm test` in `mcp-server/`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android SDK 35 (`minSdk 26`) + Node.js Coaching MCP Server

**Project Type**: Mobile Application (`app/`) + Coaching MCP Server (`mcp-server/`)

**Performance Goals**: O(1) diagnostic classification and gauge mapping; 60fps Compose rendering with zero text clipping or multi-line badge wrapping on `360dp` viewports

**Constraints**: Preserve 100% backward compatibility with Room schema v10 (`10.json`), legacy `[subcycle=WEIGHT_LOSS]` / `[subcycle=WEIGHT_GAIN]` metadata tags, and existing unit tests

**Scale/Scope**: 4 user stories (P1–P3), 10 functional requirements (`FR-001`–`FR-010`), touching Android model/analytics/UI layers and `mcp-server/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow**: **PASS** — `ProgressScreen` and `CyclesScreen` remain stateless UI layers consuming `CycleMomentumState` and `Cycle` models from `AppViewModel`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — All 5-zone velocity spectrum rules, strict boundary inequalities (`-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, `+0.75%`), 5-segment gauge mapping, and compounded projections are pure deterministic functions in `FatLossAnalytics`.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: **PASS** — Comprehensive unit tests in `FatLossAnalyticsTest`, `WeightProgressionCardTest`, `CyclesScreenTest`, and `mcp-server.test.ts` verify all boundaries and UI resolvers.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Single-line corridor pills (`maxLines = 1`, `softWrap = false`, `8dp` horizontal padding, `12sp` `tabular-nums`) with vertical stacking at `fontScale >= 1.5f`, composite-only warning badges/subtitles, and merged TalkBack semantics.
- **V. Offline-First, Idempotent Synchronization & Environment Isolation**: **PASS** — Zero Room schema changes; `Cycle.goal` metadata parser seamlessly handles both legacy (`WEIGHT_LOSS`, `WEIGHT_GAIN`) and new (`FAT_LOSS`, `MUSCLE_GAIN`) tokens.

## Project Structure

### Documentation (this feature)

```text
specs/017-subcycle-velocity-corridors/
├── plan.md                                              # Implementation plan
├── research.md                                          # Phase 0 architectural decisions
├── data-model.md                                        # Phase 1 domain & entity models
├── quickstart.md                                        # Phase 1 validation guide
├── contracts/
│   └── subcycle-velocity-corridors-contract.md          # UI & MCP contracts
├── checklists/
│   ├── requirements.md                                  # Spec quality checklist
│   └── domain-quality.md                                # Phase 6 requirements quality checklist
└── tasks.md                                             # Phase 7 actionable task list
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── analytics/
│   │   └── FatLossAnalytics.kt                          # 5-zone spectrum, strict inequality engine, 5-segment gauge & Optimal Range header
│   └── model/
│       ├── Cycle.kt                                     # Backward-compatible FAT_LOSS/WEIGHT_LOSS & MUSCLE_GAIN/WEIGHT_GAIN metadata parsing
│       └── Enums.kt                                     # Updated WeightSubcycleType (Fat Loss, Recomposition, Muscle Gain) & legacy aliases
└── ui/
    └── screens/
        ├── CyclesScreen.kt                              # Updated subcycle validation & labels
        └── ProgressScreen.kt                            # SPEC-WEIGHT-CARD-02 & SPEC-WEIGHT-SUBTYPE-SHEET-02 UI components & resolvers

app/src/test/java/com/fractanomics/crosstraining/
├── data/analytics/
│   └── FatLossAnalyticsTest.kt                          # Unit tests for 5-zone spectrum, strict inequalities & corridors
└── ui/screens/
    ├── CyclesScreenTest.kt                              # Unit tests for canonical & legacy subcycle metadata encoding/decoding
    └── WeightProgressionCardTest.kt                     # Unit tests for SPEC-WEIGHT-CARD-02 & SPEC-WEIGHT-SUBTYPE-SHEET-02 resolvers

mcp-server/
├── src/
│   └── schemas.ts                                       # Updated subcycle corridors, diagnostic engine & legacy input normalization
└── test/
    └── mcp-server.test.ts                               # MCP server unit tests for updated corridors & diagnostic fields
```

**Structure Decision**: Refines existing Android MVVM files (`Enums.kt`, `Cycle.kt`, `FatLossAnalytics.kt`, `CyclesScreen.kt`, `ProgressScreen.kt`) and `mcp-server/src/schemas.ts` in place with zero schema migrations.

## Complexity Tracking

No Constitution violations — table omitted.
