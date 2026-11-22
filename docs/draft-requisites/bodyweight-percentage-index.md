# Requirement Specification: Body-Weight Percentage Index (`% BW`) Across Tracking Pages & Remote MCP

- **Status:** Draft Requirement (Approved Discovery — Ready for Refinement / Implementation Planning)
- **Created:** 2026-11-22
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (Android Jetpack Compose + `mcp-server/`)
- **Design System & Governance References:**
  - [`design-system/crosstraining/MASTER.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/MASTER.md)
  - [`design-system/crosstraining/pages/progress-tracking.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/pages/progress-tracking.md)
  - [`.agents/rules/03_compose_ui_standards.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/.agents/rules/03_compose_ui_standards.md)
  - [`.specify/memory/constitution.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/.specify/memory/constitution.md)

---

## 1. Problem Statement & Product Value

Raw lifted weight alone does not capture relative strength progression when an athlete's body weight changes across fat-loss, recomposition, or muscle-gain training cycles. An athlete whose Snatch stays at `80 kg` while dropping from `84 kg` to `78 kg` has meaningfully increased relative strength (`95.2% BW` → `102.6% BW`), yet current tracking pages only surface absolute load (`kg`/`lbs`) and volume (`kg`).

Deriving a **`% BW`** (body-weight percentage) index directly from existing workout records (`SessionWithBlocks`, `BlockSet`, `RepMax`, `CycleGoal`, `Cycle`) and daily body-weight logs (`WeightEntry`) provides immediate relative-strength visibility across every tracking view and Remote MCP read query—with **zero database schema migration**.

---

## 2. Locked Product & UX Decisions

| Decision Area | Locked Specification |
|---|---|
| **Index Name & Format** | **`% BW`** — formatted to 1 decimal place, trimmed when whole (e.g., `142.5% BW`, `140% BW`). Estimated values prefix `~` (e.g., `~142.5% BW`). Unavailable values render `—`. |
| **Core Formula** | `(liftedWeightKg / bodyWeightKg) × 100`, using `BigDecimal` `HALF_UP` rounding (scale `1`). Convert `lb`/`lbs` exercise loads to `kg` before dividing by `WeightEntry.weightKg`. |
| **Body-Weight Lookup Rule** | 1) Active `WeightEntry` (`deletedAtMillis == null && weightKg > 0`) on the exact `LocalDate`.<br>2) Fallback to the latest active `WeightEntry` within **1–7 days prior** (`date - 7 .. date - 1`), marked estimated (`~`).<br>3) Otherwise `Unavailable` (`—`). |
| **Included Tracking Surfaces** | **Progress** (`By exercise`, `By complex`, `By routine` overview & drill-down, `Cycle goals`), **Session History** (`HistoryScreen`), and **Remote MCP read tools** (`mcp-server/`). |
| **Included Lifted Metrics** | Top working set, Average working set, Session Volume / Tonnage (`volume ÷ BW`), Personal Records & Rep-max progression (`RepMax`), Session History block summary chips & expanded block summary lines, Cycle Goals (`Start`, `Current Best`, `Goal`), and Daily Routine overview cards. |
| **Excluded Lifted Metrics** | Individual expanded set tokens (e.g., `100×3` inside expanded set lists stays raw without per-set `% BW` clutter); non-weighted movements (`CALORIES`, `TIME`, `DISTANCE`, and unloaded `REPS`). |
| **UI Presentation (Hybrid)** | Always show inline `% BW` beside/below raw weights in KPI cards, PR table, routine overview cards, cycle goal cards, and history rows/chips **plus** a compact `Weight` / `% BW` switcher on charts (persisted via `rememberSaveable`). |
| **Peak `% BW` Handling** | Track **Peak `% BW`** independently from heaviest raw `All-time top` (show `% BW` of the heaviest raw lift AND highlight highest relative `% BW` when it occurred on a different session, or provide a dedicated `Best % BW` indicator). |
| **Chart Missing-Data Behavior** | Plot only dates with valid body weight; break/gap polyline cleanly across dates without valid BW (using null-gap handling in `LineChart.kt`); display a coverage caption (e.g., `"6 of 8 sessions with BW"`); when 0 points have valid BW, show an empty-state message with a `"Log body weight"` CTA. |
| **Cycle Goals Denominator** | Use logged body weight on each achievement date (`exact` → `7-day fallback`) for **Start** and **Current Best**; use **`Cycle.targetWeightKg`** (falling back to latest active `WeightEntry` if unset) for **Goal**. |
| **Database Impact** | **Zero Room schema migration** — computed purely from existing entities and Firestore collections. |

---

## 3. Pure Domain Analytics & Data Resolution Specification

### 3.1 Body-Weight Resolution Contract
Given a target `LocalDate` and the set of active `WeightEntry` rows (`deletedAtMillis == null` and `weightKg > 0.0`):

1. **Exact Match (`EXACT`):** Active `WeightEntry` with `entry.date == targetDate`.
   - `isEstimated = false`, `sourceDate = targetDate`, `daysPrior = 0`.
2. **7-Day Prior Fallback (`ESTIMATED_PRIOR`):** If no exact match exists, select the latest active `WeightEntry` where `entry.date in (targetDate.minusDays(7)..targetDate.minusDays(1))`.
   - `isEstimated = true`, `sourceDate = entry.date`, `daysPrior = ChronoUnit.DAYS.between(entry.date, targetDate).toInt()`.
3. **Unavailable (`UNAVAILABLE`):** No active `WeightEntry` within `targetDate.minusDays(7)..targetDate`.
   - `% BW` evaluates to `null` (rendered in UI as `—`).

> **Cycle Goal Target Denominator (`Goal` value only):**
> 1. If `Cycle.targetWeightKg != null && Cycle.targetWeightKg > 0.0`, resolve with `TARGET_CYCLE_BW` (`isEstimated = false`).
> 2. Else, fall back to the latest active `WeightEntry` across the log (`LATEST_BW`, `isEstimated = true` with `sourceDate`).
> 3. Else, `UNAVAILABLE` (`—`).

### 3.2 Unit Normalization & Weighted-Movement Eligibility
- **Weighted Eligibility:** Only compute `% BW` when the lifted value represents a weighted load (`MetricType.WEIGHT` / loaded sets where `BlockSet.weight > 0.0`, `RepMax`, or `CycleGoal` with `goalMetric == MetricType.WEIGHT`). Non-weighted metrics (`CALORIES`, `TIME`, `DISTANCE`, bodyweight-only `REPS`) MUST NOT produce a `% BW` index.
- **Warm-up & Failed Exclusion:** Match existing `BlockPerformance.workingSets` semantics—exclude `isWarmup == true` and `isFailed == true` sets from Top, Average, and Volume `% BW` calculations.
- **Unit Alignment (`kg` vs `lbs`):** Convert lifted load from `lb`/`lbs` to `kg` (`weightLb * 0.45359237` or shared app conversion constant) before dividing by `WeightEntry.weightKg`. Because `% BW` is a dimensionless ratio of matching mass units, switching UI display units (`kg` ↔ `lbs`) never changes the `% BW` percentage.

### 3.3 Deterministic Rounding & Arithmetic Consistency
Aligned with Constitution Principle II:
- Use `BigDecimal` arithmetic with `RoundingMode.HALF_UP` to **1 decimal place** (`scale = 1`).
- **Display formatting:** Strip trailing `.0` when whole (`140.0` → `140% BW`; `142.5` → `142.5% BW`). Prefix `~` when `isEstimated == true` (`~142.5% BW`).
- **Session-to-Session Deltas:** Compute `% BW` delta between consecutive valid sessions from the rounded 1-decimal `% BW` values (`latestRounded - previousRounded`) so printed deltas reconcile exactly with displayed card values (e.g., `▲ +3.5% BW`, `▼ -1.2% BW`, `= no change`).
- **Volume `% BW`:** Compute `(volumeKg / bodyWeightKg) * 100` using the same date resolution and `HALF_UP` scale-1 trimmed formatting (e.g., `2400 kg` ÷ `80 kg` = `3000% BW`).

### 3.4 Peak `% BW` Independent Evaluation
Because heaviest absolute weight and highest relative `% BW` can occur on different dates (e.g., `100 kg @ 85 kg = 117.6% BW` vs. `95 kg @ 75 kg = 126.7% BW`), domain analytics MUST compute both:
1. **Heaviest Raw Lift (`allTimeTop`):** Max absolute `top` across filtered blocks/days, paired with that session's `% BW` (or `—` if unavailable).
2. **Peak Relative `% BW` (`peakTopPercentBw`):** Max `% BW` across all filtered days/blocks that have valid body-weight resolution, retaining `peakDate`, `rawWeightAtPeak`, `resolvedBodyWeightKg`, and `isEstimated`.
   - When `peakTopPercentBw` occurred on a **different** session/value than `allTimeTop` (or exceeds `allTimeTop`'s `% BW`), highlight it clearly in the KPI summary (e.g., a dedicated `Best % BW` sub-callout or KPI row).

---

## 4. User Stories & Acceptance Criteria

### US-1: Deterministic `% BW` Calculation & Body-Weight Fallback Engine
**As an** athlete tracking strength relative to body mass,
**I want** `% BW` to resolve consistently using my exact workout-date body weight or a 7-day fallback without changing database schemas,
**So that** every screen and MCP client reports identical, trustworthy relative-strength figures.

**Acceptance Criteria:**
- **Given** a logged workout on `2026-11-20` with top lift `114 kg` and an active `WeightEntry` on `2026-11-20` of `80.0 kg`, **When** `% BW` is computed, **Then** it returns `142.5% BW` (`isEstimated = false`).
- **Given** no `WeightEntry` on `2026-11-20` (or only a soft-deleted tombstone) and an active entry on `2026-11-16` (`4 days prior`) of `80.0 kg`, **When** `% BW` is computed for `2026-11-20`, **Then** it resolves `80.0 kg` (`ESTIMATED_PRIOR`, `daysPrior = 4`) and formats `~142.5% BW`.
- **Given** the most recent active `WeightEntry` is `8 days prior` (`2026-11-12`) or none exists, **When** `% BW` is computed for `2026-11-20`, **Then** it resolves `UNAVAILABLE` (`null`) and renders `—`.
- **Given** an exercise configured in `lbs` (e.g., `220.46226 lbs` = `100 kg`) and a `WeightEntry` of `80.0 kg`, **When** `% BW` is computed, **Then** it yields `125% BW` rather than `275.6% BW`.
- **Given** an exercise with `MetricType.CALORIES`, `TIME`, `DISTANCE`, or unloaded `REPS` (`weight == null`), **When** progress is computed, **Then** no `% BW` value or chart switcher is shown for that non-weighted movement.

---

### US-2: `ProgressScreen` — `By exercise`, `By complex`, and `By routine`
**As an** athlete reviewing progression in `ProgressScreen`,
**I want** inline `% BW` on KPIs, PRs, routine overview cards, and history rows plus a `% BW` chart mode,
**So that** I can compare relative strength and relative volume alongside absolute loads.

**Acceptance Criteria:**
- **Given** a weighted exercise, complex, or daily-routine constituent in `ProgressScreen`, **When** `KpiRows` renders, **Then**:
  - `All-time top`, `Last top`, `Last average`, and `Last volume` display inline `% BW` (or `~...% BW` / `—`) below or beside the raw value, plus `% BW` delta when both latest and previous sessions have valid body weight.
  - When the highest relative `% BW` occurred on a different session than the heaviest raw lift, the KPI summary explicitly surfaces that Peak `% BW` (value + date).
- **Given** `EvolutionCards` (Top/Average and Volume charts) or `Rep-max progression` chart for a weighted movement, **When** viewing the chart card, **Then**:
  - A compact 2-option switcher (`Weight` | `% BW`) lets the athlete toggle the chart Y-axis between absolute load and `% BW`, persisting selection via `rememberSaveable`.
  - In `% BW` mode, dates without valid body weight map to `null` points so `LineChart` / `MultiLineChart` breaks polylines across missing dates instead of interpolating across gaps.
  - A concise coverage caption shows `"X of Y sessions with BW"` (and notes `"~ = 7-day prior BW"` when estimated points are included).
  - If `0` sessions in the series have valid body weight (`X == 0`), the chart area displays an informative empty state with a `"Log body weight"` CTA button opening the existing `WeightEntryBottomSheet`.
- **Given** `Personal records` (`By exercise`) or `DailyRoutineConstituentOverviewCard` (`By routine`), **When** rendering weighted items, **Then** each PR row (`RepMax.date`) and each weighted routine overview card (`All-Time Top`, `Last`) displays its inline `% BW` (omitting `% BW` cleanly for `CALORIES`/`TIME`/`DISTANCE`/`REPS` constituents).
- **Given** `BlockHistoryCard` / `BlockHistoryRow`, **When** viewing collapsed and expanded rows for weighted occurrences, **Then** inline `% BW` appears for Top, Avg, and expanded Volume (`volume ÷ BW`), and expanded detail discloses the resolved body-weight anchor (e.g., `"BW: 80 kg (16 Nov, est.)"`), while individual `"Sets: ..."` tokens remain raw (`100×3`) without per-set `% BW`.

---

### US-3: `ProgressScreen` — `Cycle goals` (`CycleGoalsProgress`)
**As an** athlete tracking cycle targets in `Cycle goals`,
**I want** to see `% BW` on my `Start`, `Current Best`, and `Goal` weights for strength goals,
**So that** I can evaluate both my starting/current relative strength and my target `% BW` at my cycle's target body weight.

**Acceptance Criteria:**
- **Given** a `CycleGoal` with `goalMetric == MetricType.WEIGHT`, **When** `CycleGoalsProgress` evaluates `Start`, `Current Best`, and `Goal`, **Then**:
  - `Start` displays `% BW` resolved against the body weight on the baseline achievement date (exact → 7-day fallback → `—`).
  - `Current Best` displays `% BW` resolved against the body weight on the best achievement date (exact → 7-day fallback → `—`).
  - `Goal` displays `% BW` resolved against `Cycle.targetWeightKg` when set (`> 0`), or falls back to the latest active `WeightEntry` (clearly marked `~` / `"latest BW"` when target body weight is unset), or `—` when neither exists.
- **Given** a `CycleGoal` with `goalMetric == MetricType.CALORIES` or `MetricType.REPS`, **When** rendered, **Then** no `% BW` suffix is shown.

---

### US-4: `HistoryScreen` — Session Cards & Block Summaries
**As an** athlete browsing past workouts in `Session History`,
**I want** to see `% BW` on weighted block chips and expanded block summaries,
**So that** I can immediately gauge relative intensity on any historical training day.

**Acceptance Criteria:**
- **Given** a logged session on `Session.date` in `HistoryScreen` (`CompactSessionCard`), **When** rendering collapsed weighted block summary chips and expanded `HistoryBlockCard` summaries, **Then**:
  - Weighted blocks display compact inline `% BW` alongside the top/summary weight (e.g., `Snatch (90kg · 112.5% BW)` or `~112.5% BW`), resolved via `Session.date`.
  - Expanded block details show `% BW` for block summary metrics (and BW source disclosure if estimated), while individual set tokens remain raw without per-set `% BW` badges.
  - Non-weighted blocks (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`) omit `% BW`.

---

### US-5: Remote MCP Read-Tool Parity (`mcp-server/`)
**As an** athlete querying my workout progress through an AI assistant via Remote MCP,
**I want** MCP read tools to return consistent `% BW` fields alongside raw weights,
**So that** AI summaries match the Android app with zero discrepancies.

**Acceptance Criteria:**
- **Given** read tools in `mcp-server/src/mcp.ts` backed by `WorkoutDomainService` in `mcp-server/src/store.ts` (`get_workout_summary`, `list_sessions`, `list_rep_maxes`, `list_cycle_goals`, `get_daily_routine_progress`), **When** invoked for a user with active `WeightEntry` data, **Then**:
  - Weighted items include backward-compatible optional relative-strength metadata (`percentBw` / `topPercentBw` / `avgPercentBw` / `volumePercentBw`, `bodyWeightKg`, `bodyWeightDate`, `isBodyWeightEstimated`), computed with the same exact-day → 7-day prior rule and `HALF_UP` 1-decimal rounding.
  - `get_daily_routine_progress` includes `allTimeTopPercentBw`, `peakTopPercentBw` (with date/raw weight), `lastTopPercentBw`, `lastAveragePercentBw`, `lastVolumePercentBw`, and per-session/chart `% BW` fields for weighted constituents.
  - `list_cycle_goals` includes `startPercentBw`, `currentBestPercentBw`, and `goalPercentBw` (anchored to `Cycle.targetWeightKg` or latest active weight) for `WEIGHT` goals.
  - All existing `ToolAnnotations`, tool count invariants, and flat JSON Schema invariants (`0` `$ref`, `0` `exclusiveMinimum`) remain intact.

---

## 5. UI/UX, Design System & Accessibility Requirements

All UI implementation MUST comply with [`MASTER.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/MASTER.md) and [`progress-tracking.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/pages/progress-tracking.md):

1. **Theming & Contrast:** Use `MaterialTheme.colorScheme.*` semantic tokens exclusively—no hardcoded hex colors. Verify `>= 4.5:1` text contrast and `>= 3:1` border/icon contrast across all 4 `AppThemeMode`s (`LIGHT`, `DARK`, `LIGHT_HIGH_CONTRAST`, `DARK_HIGH_CONTRAST`).
2. **Tabular Numerals & Compact Badges:** Apply `fontFeatureSettings = "tnum"` on every `% BW` number, delta, and table column. Keep `% BW` pills single-line (`maxLines = 1, softWrap = false`) where bounded.
3. **360dp & Dynamic Type Reflow:** On `360dp`-wide viewports up to `fontScale = 1.3f` (and graceful reflow up to `2.0f`), allow secondary `% BW` labels to wrap onto a second line cleanly beneath the raw value rather than clipping or squeezing columns.
4. **Touch Targets & State Persistence:** Enforce `>= 48.dp x 48.dp` touch targets and `>= 8.dp` spacing on chart `Weight | % BW` switchers and `"Log body weight"` CTA buttons. Persist UI chart-mode toggles with `rememberSaveable`.
5. **Non-Color Redundancy & TalkBack:** Pair delta colors with `▲`/`▼`/`=` arrows; pair charts with visible `BlockHistoryCard` tables; apply `Modifier.semantics(mergeDescendants = true)` on composite KPI/history/PR rows so TalkBack reads complete context (including `"estimated from <date>"` when `~` is displayed, or `"no body weight logged"` when `—` is displayed).
6. **Recomposition Performance:** Build the active `LocalDate -> WeightEntry` lookup inside `remember(...)` or domain analytics helpers—never scan `weightEntries` inside Compose row loops.

---

## 6. Architectural Impact Map (Reference Only — Zero Schema Migration)

| Layer / File | Expected Responsibility |
|---|---|
| `app/.../ui/ProgressAnalytics.kt` (and/or new pure `RelativeStrengthAnalytics.kt`) | Body-weight date resolver (`exact` → `7-day prior` → `unavailable`), unit normalization (`lbs` → `kg`), `BigDecimal` `HALF_UP` `% BW` calculator & formatter, `Peak % BW` tracking, `DayPerformance` / `BlockPerformance` `% BW` projections. |
| `app/.../util/DailyRoutineCodec.kt` | Extend `ConstituentProgressSummary` and `computeDailyRoutineProgress` with `% BW` top/avg/volume, `Peak % BW`, and chart `% BW` series for weighted constituents. |
| `app/.../ui/screens/ProgressScreen.kt` | Extend `KpiRows`, `EvolutionCards`, `ExerciseProgress` (Rep-max chart & Personal records table), `RoutineProgress`, `DailyRoutineConstituentOverviewCard`, `DailyRoutineConstituentDrillDown`, `BlockHistoryCard`/`BlockHistoryRow`, and `evaluateCycleGoalProgress` / `CycleGoalsProgress` (capturing `baselineStartDate` & `currentBestDate`). |
| `app/.../ui/screens/HistoryScreen.kt` | Pass active `weightEntries` into `CompactSessionCard` and display inline `% BW` on weighted block chips & expanded block summaries. |
| `app/.../ui/components/LineChart.kt` | Optional value/axis unit suffix support (e.g., `%` formatting on Y-axis labels) while preserving existing null-gap polyline breaking. |
| `mcp-server/src/store.ts`, `mcp.ts`, `schemas.ts` | Shared TypeScript `% BW` resolver & rounding matching Android `HALF_UP` parity; optional `% BW` enrichment fields in read-tool responses. |

---

## 7. Edge Cases & Guardrails

| Edge Case | Required Behavior |
|---|---|
| **Zero `WeightEntry` rows** | All `% BW` labels render `—`; charts in `% BW` mode show empty-state + `"Log body weight"` CTA; raw weight views remain 100% functional. |
| **Tombstoned `WeightEntry` on workout date** | `deletedAtMillis != null` entries are ignored; falls back to next active entry in `1..7 days prior` or `—`. |
| **`WeightEntry.weightKg <= 0.0` or corrupted input** | Treat as invalid / guard against division by zero → skip or return `—` (zero `NaN`/`Infinity`). |
| **Non-weighted exercises (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`)** | Never compute or render `% BW` or show the `% BW` chart toggle. |
| **Mixed warm-up / failed sets** | Excluded from working Top, Average, and Volume `% BW` calculations (consistent with `BlockPerformance.workingSets`). |
| **Multiple sessions/blocks on the same date** | Share the same resolved daily body weight; `DayPerformance` aggregates working sets for that date before deriving day Top/Average/Volume `% BW`. |
| **1–3 valid `% BW` chart points** | `EvolutionCards` / `LineChart` handles 1–3 points cleanly without NaN while `KpiRows` + `BlockHistoryCard` carry the numerical summary. |
| **Cycle has no `targetWeightKg` set** | `CycleGoalsProgress` Goal `% BW` falls back to latest active `WeightEntry` (marked estimated / latest BW) or `—` if none logged. |
| **Legacy MCP consumers** | All `% BW` response fields in `mcp-server/` are additive and optional so existing callers remain backward compatible. |

---

## 8. Out of Scope

- Any Room SQLite schema migration or new persisted entity columns.
- Per-set `% BW` badges inside expanded `"Sets: ..."` strings (`HistoryScreen` or `BlockHistoryRow`).
- Powerlifting/weightlifting coefficient formulas (Wilks, DOTS, IPF GL, Sinclair).
- New MCP tool names or write-tool mutations for `% BW`.
- Inline body-weight logging sheet directly inside `HistoryScreen`.

---

## 9. Verification & Definition of Done (for Future Implementation)

1. **Android Unit Tests:** Deterministic JVM tests covering exact vs. `1..7`-day fallback vs. `>= 8`-day `—`, tombstone filtering, `lbs` conversion, `BigDecimal` `HALF_UP` rounding & trimming, Peak `% BW` independence, Volume ÷ BW, `CycleGoals` date anchoring, and `DailyRoutineCodec` `% BW` projections (`.\gradlew.bat testDebugUnitTest --no-daemon`).
2. **Android Lint & Script Tests:** `.\gradlew.bat lintDebug --no-daemon` and `.\scripts\tests\Invoke-ScriptTests.ps1` pass with 0 errors.
3. **MCP Server Parity Tests:** `npm run build && npm test` inside `mcp-server/` pass with schema-flatness (`0` `$ref`, `0` `exclusiveMinimum`) and rounding parity verified.
4. **Manual / Pre-Delivery UI Audit:** Verified against `design-system/crosstraining/MASTER.md` and `pages/progress-tracking.md` across all 4 `AppThemeMode`s, `360dp` viewports, and `fontScale >= 1.3f`.
5. **Changelog & CI:** `CHANGELOG.md` updated under `## [Unreleased]` and remote GitHub Actions CI checks 100% green prior to merge.
