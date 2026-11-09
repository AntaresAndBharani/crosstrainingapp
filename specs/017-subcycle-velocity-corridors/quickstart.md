# Quickstart & Validation Guide: Subcycle Taxonomy & Velocity Corridors

**Feature**: `017-subcycle-velocity-corridors` | **Date**: 2026-11-09

## Prerequisites

- JDK 17 configured in `~/.gradle/gradle.properties`
- Android SDK 35
- Node.js 20+ (for `mcp-server/` test verification)
- PowerShell 7+ (for `Invoke-ScriptTests.ps1`)

## 1. Run Android Unit Tests

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

**Expected Outcome**:
- `FatLossAnalyticsTest`: Verifies `WeightSubcycleType` corridors (`Fat Loss`: `[-1.00%, -0.25%]`, `Recomposition`: `[-0.25%, +0.25%]`, `Muscle Gain`: `[+0.25%, +0.75%]`), strict boundary inequalities at `-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, and `+0.75%`, diagnostic badges/messages, and 5-segment gauge mapping.
- `WeightProgressionCardTest`: Verifies `SPEC-WEIGHT-CARD-02` and `SPEC-WEIGHT-SUBTYPE-SHEET-02` resolvers, pill labels (`Fat Loss ▾`, `Recomposition ▾`, `Muscle Gain ▾`), composite-only diagnostic badge/subtitle resolvers, and clean horizon row formatting.
- `CyclesScreenTest`: Verifies `[subcycle=FAT_LOSS]`, `[subcycle=RECOMPOSITION]`, `[subcycle=MUSCLE_GAIN]` encoding and backward-compatible decoding of legacy `[subcycle=WEIGHT_LOSS]` and `[subcycle=WEIGHT_GAIN]`.

## 2. Run MCP Server Tests

```powershell
Push-Location mcp-server
npm test
Pop-Location
```

**Expected Outcome**:
- All MCP unit tests pass, validating `FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN` corridors, legacy `WEIGHT_LOSS`/`WEIGHT_GAIN` normalization, `global_safety_bounds`, and `weekly_body_weight_composite` diagnostic fields.

## 3. Run Script Tests & Android Lint

```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```

**Expected Outcome**:
- 100% passing script tests and zero lint errors.
