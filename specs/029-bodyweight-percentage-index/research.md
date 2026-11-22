# Technical Research: Body-Weight Percentage Index (`% BW`)

## 1. Existing Data Models & Storage (Zero Migration Verification)
- **Android Body Weight (`WeightEntry.kt`, `WeightDao.kt`):**
  - Natural `@PrimaryKey val date: LocalDate`, `val weightKg: Double`, `val deletedAtMillis: Long?`.
  - Filtering `deletedAtMillis == null && weightKg.isFinite() && weightKg > 0.0` into a `TreeMap<LocalDate, WeightEntry>` allows $O(\log N)$ lookup via `floorEntry(targetDate)` for both exact same-day and 1–7 day prior fallback.
- **Weight Unit Conversion (`WeightAnalytics.kt`):**
  - Already defines `const val LBS_PER_KG: Double = 2.20462262185` and `kgToLbs(kg)` / `lbsToKg(lbs)`. Reusing `WeightAnalytics.lbsToKg` and `WeightAnalytics.kgToLbs` avoids constant duplication.
- **Non-Weighted Exclusion (`Enums.kt`, `ProgressAnalytics.kt`, `DailyRoutineCodec.kt`):**
  - `Exercise.unit` uses `"kg"` or `"lbs"` (`"lb"` accepted defensively) for weighted lifts; conditioning/bodyweight movements use `"cal"`, `"s"`, `"m"`, `"reps"`, or `MetricType.CALORIES` / `TIME` / `DISTANCE` / `REPS` (`BlockSet.weight == null`).
  - `BlockPerformance.workingSets` already filters `!it.isWarmup && !it.isFailed`. Checking both `isWeightedUnit(unit)` and presence of positive `BlockSet.weight` prevents bodyweight `REPS` sets from producing false `% BW` badges.

## 2. Chart Gap-Breaking & Y-Axis Formatting (`LineChart.kt`)
- `ChartCanvas` (`LineChart.kt` lines 136–153) already supports gap-aware polyline breaking: whenever `ChartPoint.value == null`, it resets `lastPoint = null` so missing dates break the line cleanly without interpolation, and returns early with zero `NaN` propagation when `values.isEmpty()`.
- Adding `valueSuffix: String = ""` to `LineChart`, `MultiLineChart`, and `ChartCanvas` lets `% BW` charts format Y-axis ticks (`fmt(v) + "%"`) cleanly without modifying existing weight charts.

## 3. `ProgressScreen.kt` Integration Points
- **Weight Sheet Reuse:** `ProgressScreen` already owns `showWeightSheet` / `editingWeightEntry` (lines 241–242, 472–517) opening `WeightEntryBottomSheet` directly on screen. Passing an `onLogWeight` callback into `EvolutionCards`, `ExerciseProgress`, `RoutineProgress`, and `DailyRoutineConstituentDrillDown` allows the 0-point `% BW` chart empty state `"Log body weight"` CTA to open `WeightEntryBottomSheet` in place with zero navigation friction.
- **Cycle Goals Achievement Dates (`evaluateCycleGoalProgress` lines 3674–3764):** Currently computes `baselineStartWeight` and `currentBest` without returning which `LocalDate` produced them. Adding `baselineStartDate: LocalDate? = null` and `currentBestDate: LocalDate? = null` to `EvaluatedCycleGoalProgress` preserves backward compatibility for existing callers/tests while giving `CycleGoalsProgress` exact dates for body-weight lookup (`Start` and `Current Best`).
- **Design System Compliance (`MASTER.md` & `progress-tracking.md`):**
  - All new UI uses `MaterialTheme.colorScheme.*` only (zero hardcoded hex colors).
  - Numeric `% BW` text applies `fontFeatureSettings = "tnum"`.
  - Chart `Weight | % BW` switchers enforce `>= 48.dp` height (`heightIn(min = 48.dp)`), `8.dp` gaps, `rememberSaveable`, and 2-line header reflow when `LocalConfiguration.current.fontScale >= 1.3f`.

## 4. `HistoryScreen.kt` Integration Points
- `HistoryScreen` receives `viewModel: AppViewModel`. Collecting `viewModel.weightEntries` and `viewModel.weightUnit` via `collectAsStateWithLifecycle()` and memoizing `buildActiveWeightIndex(weightEntries)` with `remember(weightEntries)` enables $O(\log N)$ per-session BW resolution without querying Room inside Compose row loops.

## 5. Remote MCP Server Parity (`mcp-server/src/store.ts`, `mcp.ts`, `schemas.ts`)
- `WeightEntryRecord.date` in Firestore/MCP is stored as an **epoch day (`number`)** alongside `epochDayToIsoDate` / `isoDateToEpochDay` (`store.ts` lines 507–568).
- `UserCloudSyncManager.kt` confirms `cycles` are local-only in Room (`UserCloudSyncManager.kt:1117`) and NOT persisted as a Firestore collection; `listCycles` / `computeCyclesFromRaw` derive cycles dynamically. Consequently, `listCycleGoals` in `mcp-server` resolves `goalBodyWeightBasis` using `LATEST_BW` (latest active `WeightEntryRecord`, or `UNAVAILABLE` if none exist) unless a cycle target BW is attached in memory.
- All `45` tools in `mcp.ts` pass input Zod schemas (`schemas.ts`) validated for `0` `$ref` and `0` `exclusiveMinimum` in `mcp-server/test/mcp-server.test.ts`. Enriching read tool **outputs** additively inside `store.ts` via a dedicated pure helper `mcp-server/src/relative-strength.ts` leaves all `45` tool registrations and Zod input schemas 100% untouched.
