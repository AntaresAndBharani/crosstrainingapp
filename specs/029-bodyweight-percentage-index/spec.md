# Feature Specification: Body-Weight Percentage Index (`% BW`) Across Tracking Pages & Remote MCP

**Feature Branch**: `feat/029-bodyweight-percentage-index`  
**Created**: 2026-11-22  
**Status**: Approved  
**Input**: User request (`/e2e-sdlc --full --prompt docs/draft-requisites/bodyweight-percentage-index.md` + interactive prototype `bodyweight-index-prototype.html`), anchored to `design-system/crosstraining/MASTER.md` and `design-system/crosstraining/pages/progress-tracking.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deterministic `% BW` Calculation & 7-Day Body-Weight Resolution Engine (Priority: P1)

As an athlete tracking strength relative to my daily body weight, I want every lifted load in `kg` or `lbs` to derive a `% BW` index (`liftedWeightKg / bodyWeightKg * 100`, rounded `HALF_UP` to 1 decimal place trimmed when whole) using my active `WeightEntry` on the workout/PR date or a 7-day prior fallback (`~`), so that all tracking screens and Remote MCP read tools report identical, trustworthy relative-strength figures with zero database migration.

**Why this priority**: Every UI surface (`ProgressScreen`, `HistoryScreen`, `DailyRoutineCodec`) and MCP parity layer depends on one single source of truth for date resolution, unit normalization, rounding, missing-data guardrails, and independent Peak `% BW` tracking.

**Independent Test**: Run pure JVM unit tests in `RelativeStrengthAnalyticsTest.kt` and TypeScript tests in `mcp-server/test/mcp-server.test.ts` covering exact date matches, 1–7 day prior fallbacks (`ESTIMATED_PRIOR`), `>= 8` day gaps (`UNAVAILABLE` -> `—`), soft-deleted tombstones (`deletedAtMillis != null`), non-positive weights, `lbs` → `kg` invariance, non-weighted movement exclusion (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`), Volume ÷ BW, `HALF_UP` trimming (`140.0` → `140% BW`, `142.5` → `142.5% BW`), and independent Peak `% BW` evaluation.

**Acceptance Scenarios**:

1. **Given** a logged workout on `2026-11-20` with top lift `114 kg` and an active `WeightEntry` on `2026-11-20` of `80.0 kg`, **When** `% BW` is computed, **Then** it resolves `BodyWeightMatchKind.EXACT` (`isEstimated = false`), computes `142.5`, and formats `"142.5% BW"`.
2. **Given** no active `WeightEntry` on `2026-11-20` (missing or `deletedAtMillis != null`) and an active entry on `2026-11-16` (`4 days prior`) of `80.0 kg`, **When** `% BW` is computed for `2026-11-20`, **Then** it resolves `BodyWeightMatchKind.ESTIMATED_PRIOR` (`daysPrior = 4`, `isEstimated = true`) and formats `"~142.5% BW"`.
3. **Given** the latest active `WeightEntry` before `2026-11-20` is `8 days prior` (`2026-11-12`), or no active entries exist, or `weightKg <= 0.0`, **When** `% BW` is computed, **Then** it resolves `BodyWeightMatchKind.UNAVAILABLE`, returns `null` (zero `NaN`/`Infinity`), and formats `"—"`.
4. **Given** an exercise recorded in `lbs` (e.g., `220.46226 lbs` = `100 kg`) and a `WeightEntry` of `80.0 kg`, **When** `% BW` is computed, **Then** `lbs` converts to `kg` before dividing and returns `125% BW` (never `275.6% BW`), while whole `.0` decimals trim (`140.0` → `"140% BW"`).
5. **Given** an athlete lifted `100 kg` at `85 kg` BW (`117.6% BW`) on `10 Jan` and `95 kg` at `75 kg` BW (`126.7% BW`) on `10 May`, **When** `resolvePeakRelativeStrength` evaluates the series, **Then** `allTimeTop` remains `100 kg` (`117.6% BW`) while `PeakRelativeStrength` independently identifies `126.7% BW` (`95 kg` on `10 May`).

---

### User Story 2 - `ProgressScreen` (`By exercise`, `By complex`, and `By routine`) Hybrid `% BW` KPIs, Charts & History (Priority: P1)

As an athlete reviewing progress across `By exercise`, `By complex`, and `By routine` in `ProgressScreen`, I want inline `% BW` on KPI cards, PR tables, routine overview cards, and block history rows plus a compact `Weight | % BW` switcher on Evolution, Volume, and Rep-Max charts (with gap-aware polylines, coverage captions, and a `"Log body weight"` empty-state CTA), so I can inspect relative strength alongside raw weights without clutter or clipping on `360dp` screens.

**Why this priority**: `ProgressScreen` (`By exercise`, `By complex`, `By routine`) is the primary analytical workspace where athletes track lift and volume progression over time.

**Independent Test**: Verify `DailyRoutineCodec.computeDailyRoutineProgress`, `ProgressScreen` helpers (`KpiRows`, `EvolutionCards`, `ExerciseProgress`, `RoutineProgress`, `DailyRoutineConstituentOverviewCard`, `BlockHistoryCard`), and `LineChart` Y-axis suffix formatting render accurate `% BW` values, gap-aware `null` points, independent Peak `% BW` callouts, non-weighted movement omission (`cal`/`s`/`m`/`reps`), `"Log body weight"` CTA when 0 valid points exist, and raw expanded set tokens.

**Acceptance Scenarios**:

1. **Given** a weighted exercise, complex, or routine constituent in `ProgressScreen`, **When** `KpiRows` renders, **Then** `All-time top`, `Last top`, `Last average`, and `Last volume` display inline `% BW` (`~` when estimated, `—` when unavailable) and `% BW` session-to-session deltas (`▲`/`▼`/`=`), and when `Peak % BW` occurred on a different date with higher `% BW` than `All-time top`, an explicit Peak `% BW` callout surfaces its value, raw weight, and date.
2. **Given** `EvolutionCards` (Top/Average and Volume charts) or `Rep-max progression` chart for a weighted movement, **When** toggling the `Weight ({unit})` | `% BW` switcher (`>= 48.dp` touch target, persisted via `rememberSaveable`), **Then** Y-axis labels display `%`, dates without valid body weight map to `null` breaking polylines cleanly (`LineChart.kt`), a coverage caption reports `"X of Y sessions with BW"` (noting `"~ = 7-day prior BW"` when estimated), and if `X == 0`, an empty-state card with `"Log body weight"` opens `WeightEntryBottomSheet`.
3. **Given** a non-weighted movement or metric (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`), **When** viewing `By exercise`, `By complex`, or `By routine`, **Then** `% BW` badges and the `Weight | % BW` chart switcher are omitted cleanly.
4. **Given** `Personal records`, `DailyRoutineConstituentOverviewCard`, and `BlockHistoryCard` / `BlockHistoryRow` for weighted movements, **When** rendered, **Then** PR rows (`RepMax.date`), overview cards (`All-Time Top` & `Last`), and history rows (`Avg`, `Top`, expanded `volume` + body-weight source anchor disclosure) show `% BW`, while expanded `"Sets: ..."` tokens stay raw without per-set `% BW` badges.

---

### User Story 3 - `ProgressScreen` (`Cycle goals`) Date-Anchored `% BW` Tracking (Priority: P2)

As an athlete tracking cycle goals in `ProgressScreen -> Cycle goals`, I want `Start`, `Current Best`, and `Goal` weights on `MetricType.WEIGHT` goals to display `% BW` anchored to achievement dates (`Start` and `Current Best`) and cycle target body weight (`Goal`, falling back to latest active weight when unset), so I can measure relative-strength targets during fat-loss or strength cycles.

**Why this priority**: During body-weight-change cycles, comparing goal progress in `% BW` clarifies whether relative strength is progressing toward the cycle's target weight.

**Independent Test**: Unit-test `evaluateCycleGoalProgress` returning `baselineStartDate` and `currentBestDate`, plus `resolveCycleGoalTargetBodyWeight` preferring `Cycle.targetWeightKg` and falling back to latest active `WeightEntry` (`LATEST_BW`), and verify `CALORIES` / `REPS` cycle goals omit `% BW`.

**Acceptance Scenarios**:

1. **Given** a `CycleGoal` with `goalMetric == MetricType.WEIGHT`, **When** `evaluateCycleGoalProgress` runs, **Then** it populates `baselineStartDate` and `currentBestDate` alongside `baselineStartWeight` and `currentBest`.
2. **Given** a `WEIGHT` goal rendered in `CycleGoalsProgress`, **When** computing `% BW`, **Then** `Start` uses `baselineStartDate` BW, `Current Best` uses `currentBestDate` BW, and `Goal` uses `Cycle.targetWeightKg` (`TARGET_CYCLE_BW`) when `> 0.0`, or latest active `WeightEntry` (`LATEST_BW`, marked `~`) when unset, or `—` when unavailable.
3. **Given** a `CycleGoal` with `goalMetric == MetricType.CALORIES` or `MetricType.REPS`, **When** rendered, **Then** no `% BW` labels appear.

---

### User Story 4 - `HistoryScreen` Weighted Block Chips & Expanded Summaries (Priority: P2)

As an athlete browsing past workouts in `Session History`, I want weighted block chips and expanded block summaries in `CompactSessionCard` to display `% BW` resolved against `Session.date`, so I can judge relative effort across sessions while keeping individual set tokens readable.

**Why this priority**: `HistoryScreen` is where athletes inspect individual past sessions; surfacing concise `% BW` on block chips and expanded summaries completes visual tracking coverage without crowding `#N:` set pills.

**Independent Test**: Verify `HistoryScreen` resolves `Session.date` against the active weight index, formats weighted summary chips with `% BW`, displays body-weight source anchor and block summary `% BW` on expansion, omits `% BW` on non-weighted blocks, and leaves `#N: <weight>×<reps>` set badges unchanged.

**Acceptance Scenarios**:

1. **Given** a logged session on `Session.date` with a weighted block in `CompactSessionCard`, **When** collapsed, **Then** the block summary chip shows top weight plus inline `% BW` (`~` when 7-day prior, `—` when unavailable).
2. **Given** the session card is expanded, **When** viewing weighted `HistoryBlockCard` details, **Then** the resolved body-weight anchor and block summary `% BW` (`Top`, `Avg`, `Vol`) appear cleanly, while `#1: 85×2` set badges remain raw without per-set `% BW` text, and non-weighted blocks omit `% BW`.

---

### User Story 5 - Remote MCP Read-Tool `% BW` Parity (`mcp-server/`) (Priority: P2)

As an athlete querying workouts, PRs, cycle goals, or daily routines via an AI assistant connected to Remote MCP (`mcp-server/`), I want read tools (`get_workout_summary`, `list_sessions`, `list_rep_maxes`, `list_cycle_goals`, `get_daily_routine_progress`) to include additive optional `% BW` and body-weight-resolution fields computed with identical 7-day fallback and `HALF_UP` rounding rules, so AI coaching matches Android numbers identically.

**Why this priority**: Constitution Principle VII and dual-client parity require Remote MCP read responses to match Android tracking calculations without breaking existing tool schemas.

**Independent Test**: Run `npm run build && npm test` in `mcp-server/` verifying `relative-strength.ts` parity (exact, 1–7d fallback, >=8d null, tombstone ignore, `lbs` conversion, `HALF_UP` 1-decimal rounding), additive `% BW` fields across all 5 read tools, `CALORIES`/`REPS` exclusion, and unchanged `45` tools / `0` `$ref` / `0` `exclusiveMinimum` schema assertions.

**Acceptance Scenarios**:

1. **Given** an account with active `weight_entries`, sessions, rep maxes, cycle goals, and daily routines, **When** calling `list_sessions`, `list_rep_maxes`, `list_cycle_goals`, `get_daily_routine_progress`, or `get_workout_summary`, **Then** weighted entries include additive `% BW` and body-weight resolution metadata matching Android `RelativeStrengthAnalytics` rounding and 7-day fallback behavior.
2. **Given** `get_daily_routine_progress` on a weighted constituent whose heaviest raw lift differs from its highest relative `% BW`, **When** queried, **Then** response includes `allTimeTopPercentBw`, `peakTopPercentBw` (`peakTopPercentBwDate`, `peakTopPercentBwRawKg`), `lastTopPercentBw`, `lastAveragePercentBw`, `lastVolumePercentBw`, `topPercentBwDelta`, `bodyWeightCoverage`, and per-point `% BW` fields while non-weighted (`cal`/`s`/`m`/`reps`) constituents omit/null `% BW`.
3. **Given** `tools/list` schema validation in `mcp-server.test.ts`, **When** tests run, **Then** all `45` tools remain registered with accurate `ToolAnnotations`, `0` `$ref`, and `0` `exclusiveMinimum`.

---

### Edge Cases

- **Zero `WeightEntry` rows**: All `% BW` labels display `"—"`; `% BW` charts display `"0 of N sessions have body weight logged within 7 days"` + `"Log body weight"` button opening `WeightEntryBottomSheet`; absolute-weight views remain 100% functional.
- **Tombstoned `WeightEntry` (`deletedAtMillis != null`)**: Excluded before index construction; falls back to earlier active entries within `1..7 days` or `"—"`.
- **Non-positive / corrupted weights (`weightKg <= 0.0`, `NaN`, `Infinity`)**: Filtered out so division by zero or `NaN` propagation never occurs.
- **Non-weighted exercises (`CALORIES`, `TIME`, `DISTANCE`, bodyweight-only `REPS`)**: Never compute `% BW` or display `Weight | % BW` toggles.
- **Warm-up and failed sets**: Excluded matching `BlockPerformance.workingSets`.
- **Multiple blocks on the same date**: `DayPerformance` aggregates working sets first; both session blocks and daily points resolve the same date's body weight.
- **`Cycle.targetWeightKg` missing/unset (and `mcp-server` local-only cycles)**: Goal `% BW` falls back to `LATEST_BW` (marked estimated `~`) or `UNAVAILABLE` (`—`).
- **`fontScale >= 1.3f` on `360dp` viewports**: Chart headers reflow the `Weight | % BW` switcher onto a second row cleanly without clipping labels or shrinking touch targets below `48.dp`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST compute `% BW` as `(liftedWeightKg / bodyWeightKg) * 100` using `BigDecimal` `RoundingMode.HALF_UP` scale `1`, trimming `.0` for whole numbers (`140% BW`, `142.5% BW`), converting `lb`/`lbs` lifted loads to `kg` before division.
- **FR-002**: System MUST resolve body weight for any target `LocalDate` strictly from active (`deletedAtMillis == null`, `weightKg > 0`) `WeightEntry` rows: exact date (`EXACT`) → latest entry in `[date - 7 days, date - 1 day]` (`ESTIMATED_PRIOR`, formatted with `~` prefix) → `UNAVAILABLE` (`—`).
- **FR-003**: System MUST exclude non-weighted metrics (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`) and warm-up/failed sets (`isWarmup`, `isFailed`) from `% BW` calculation and UI presentation.
- **FR-004**: `RelativeStrengthAnalytics` and `DailyRoutineCodec` MUST compute `PeakRelativeStrength` independently from heaviest raw `All-time top` so KPI summaries highlight when a lighter-body-weight session achieved a higher relative `% BW`.
- **FR-005**: `ProgressScreen` (`By exercise`, `By complex`, `By routine` drill-down) MUST display inline `% BW` and `% BW` deltas across `KpiRows` (`All-time top`, `Last top`, `Last average`, `Last volume` as `volume ÷ BW`), `Personal records`, `DailyRoutineConstituentOverviewCard`, and `BlockHistoryCard`/`BlockHistoryRow` (with expanded BW source anchor disclosure), while keeping `"Sets: ..."` tokens raw.
- **FR-006**: `ProgressScreen` (`EvolutionCards` Top/Avg & Volume, and `Rep-max progression`) MUST provide a `Weight | % BW` switcher (`>= 48.dp` target, `rememberSaveable`), plot missing-BW dates as `null` gaps breaking polylines (`LineChart.kt` with `valueSuffix = "%"`), display `"X of Y sessions with BW"` coverage captions, and render a `"Log body weight"` empty-state CTA opening `WeightEntryBottomSheet` when `0` points have valid BW.
- **FR-007**: `ProgressScreen` `CycleGoalsProgress` and `evaluateCycleGoalProgress` MUST track `baselineStartDate` and `currentBestDate` and render `% BW` on `MetricType.WEIGHT` goals (`Start` & `Current Best` via achievement dates; `Goal` via `Cycle.targetWeightKg` falling back to latest active `WeightEntry`).
- **FR-008**: `HistoryScreen` (`CompactSessionCard` & `HistoryBlockCard`) MUST display compact `% BW` on weighted block chips and expanded block summaries (plus BW anchor disclosure), leaving `#N:` set badges raw.
- **FR-009**: UI changes MUST comply with `design-system/crosstraining/MASTER.md` and `pages/progress-tracking.md`: `MaterialTheme.colorScheme.*` only (all 4 `AppThemeMode`s), `fontFeatureSettings = "tnum"`, `360dp` + `fontScale >= 1.3f` reflow, non-color delta cues, and merged TalkBack semantics (`Modifier.semantics(mergeDescendants = true)`).
- **FR-010**: `mcp-server/` read tools (`get_workout_summary`, `list_sessions`, `list_rep_maxes`, `list_cycle_goals`, `get_daily_routine_progress`) MUST return additive optional `% BW` & BW-resolution metadata matching Android `HALF_UP` and 7-day fallback parity while preserving `45` tools, `ToolAnnotations`, `0` `$ref`, and `0` `exclusiveMinimum`.
- **FR-011**: Feature MUST require **zero Room database schema migration** and **zero Firestore collection breaking changes**.

### Key Entities

- **`ResolvedBodyWeight` & `BodyWeightMatchKind`**: Pure domain resolution (`EXACT`, `ESTIMATED_PRIOR`, `TARGET_CYCLE_BW`, `LATEST_BW`, `UNAVAILABLE`) carrying `weightKg`, `sourceDate`, `daysPrior`, and `isEstimated`.
- **`PeakRelativeStrength` & `DayRelativeMetrics`**: Pure projections capturing independent relative-strength peak and per-day Top/Avg/Volume `% BW` values.
- **`EvaluatedCycleGoalProgress` Extensions**: Adds `baselineStartDate` and `currentBestDate` for date-accurate goal `% BW` anchoring.
- **`ConstituentProgressSummary` Extensions**: Adds `isWeightedMetric`, `allTimeTopPercentBw`, `peakTopPercentBw`, `lastTopPercentBw`, `topPercentBwDelta`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Deterministic JVM unit tests in `RelativeStrengthAnalyticsTest.kt` verify 100% of rounding (`HALF_UP` trimmed), `lbs` conversion, 7-day lookback, tombstone filtering, Peak `% BW` separation, Cycle Goal date tracking, and Daily Routine `% BW` projections.
- **SC-002**: Full Android pre-PR verification (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\gradlew.bat lintDebug --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`) passes at 100% with 0 warnings/errors.
- **SC-003**: `mcp-server/` build & test suite (`npm run build && npm test`) passes at 100%, confirming `% BW` parity across all 5 enriched read tools while preserving `45` tools, `0` `$ref`, and `0` `exclusiveMinimum`.
- **SC-004**: All tracking surfaces preserve `360dp` responsiveness (`fontScale >= 1.3f` reflow), `48.dp` touch targets, `tnum` alignment, zero hardcoded hex colors across all 4 `AppThemeMode`s, and zero Room schema migrations.

## Assumptions

- `ProgressScreen` and `HistoryScreen` continue targeting Android Jetpack Compose (`minSdk = 26`, `compileSdk = 35`) and `mcp-server/` Node/TypeScript.
- Existing `Cycle` rows are local-only in Room (`UserCloudSyncManager.kt`), so `mcp-server` Cycle Goal `goalBodyWeightBasis` resolves to `LATEST_BW` when no cycle target BW is synced.
