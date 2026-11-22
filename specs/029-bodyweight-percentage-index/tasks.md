# Tasks: Body-Weight Percentage Index (`% BW`) Across Tracking Pages & Remote MCP

**Input**: Design documents from `specs/029-bodyweight-percentage-index/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/relative-strength-api.md`)

## Phase 1: Setup & Domain Foundation

- [X] T001 Create pure domain analytics module `RelativeStrengthAnalytics` with `BodyWeightMatchKind`, `ResolvedBodyWeight`, `PeakRelativeStrength`, `DayRelativeMetrics`, `buildActiveWeightIndex`, `resolveBodyWeightForDate`, `resolveCycleGoalTargetBodyWeight`, `calculatePercentBw`, `computePercentBwDelta`, formatting helpers (`formatPercentBw`, `formatCompactPercent`, `formatBodyWeightAnchor`, `formatTalkBackPercentBw`), `resolvePeakRelativeStrength`, and `resolveDayRelativeMetrics` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/RelativeStrengthAnalytics.kt`
- [X] T002 [P] [US5] Create pure TypeScript mirror `mcp-server/src/relative-strength.ts` implementing `roundHalfUp1`, `resolveBodyWeightForIsoDate`, `resolveLatestActiveBodyWeight`, `computePercentBw`, and `computePercentBwDelta` with exact Android parity

## Phase 2: Chart & Shared Codec Infrastructure (US1 & US2)

- [X] T003 [P] [US2] Add optional `valueSuffix: String = ""` parameter to `LineChart`, `MultiLineChart`, and `ChartCanvas` for `%` Y-axis formatting in `app/src/main/java/com/fractanomics/crosstraining/ui/components/LineChart.kt`
- [X] T004 [US1] [US2] Extend `ConstituentProgressSummary` and `computeDailyRoutineProgress` with `weightEntries` resolution, `isWeightedMetric`, `allTimeTopPercentBw`, `peakTopPercentBw`, `lastTopPercentBw`, and `topPercentBwDelta` in `app/src/main/java/com/fractanomics/crosstraining/util/DailyRoutineCodec.kt`

## Phase 3: Android `ProgressScreen` Tracking Modes (US2 & US3)

- [X] T005 [US2] Wire memoized `activeWeightIndex` and `openWeightLogger` callback in `ProgressScreen` and add shared `ChartMetricMode` / `ChartMetricSwitcher` (`>= 48.dp`, `rememberSaveable`, `fontScale >= 1.3f` reflow, `MaterialTheme.colorScheme.*`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T006 [US2] Update `KpiRows` (inline `% BW` on `All-time top`, `Last top`, `Last average`, `Last volume`, `% BW` deltas, merged TalkBack semantics, and independent `Peak % BW` callout) and `EvolutionCards` (`Weight | % BW` switcher, gap-aware `null` points, coverage caption, `"Log body weight"` empty-state CTA) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T007 [US2] Update `ExerciseProgress` (`Rep-max progression` `Weight | % BW` chart switcher + `Personal records` `% BW`), `RoutineProgress`, `DailyRoutineProgressSection` (`DailyRoutineConstituentOverviewCard` & `DailyRoutineConstituentDrillDown`), and `BlockHistoryCard` / `BlockHistoryRow` (inline `% BW` + expanded BW source anchor while keeping `"Sets: ..."` raw) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T008 [US3] Extend `EvaluatedCycleGoalProgress` and `evaluateCycleGoalProgress` with `baselineStartDate` and `currentBestDate`, and display `% BW` on `Start`, `Current Best`, and `Goal` (`Cycle.targetWeightKg` or `LATEST_BW` fallback) for `MetricType.WEIGHT` goals in `CycleGoalsProgress` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

## Phase 4: Android `HistoryScreen` & Unit Tests (US1, US2, US3, US4)

- [X] T009 [US4] Collect `weightEntries` & `weightUnit` in `HistoryScreen`, memoize `activeWeightIndex`, and show inline `% BW` on weighted block chips and expanded `HistoryBlockCard` summaries (plus BW anchor disclosure) while keeping `#N:` set badges raw in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt`
- [X] T010 [P] [US1] [US2] [US3] Create comprehensive JVM test suite in `app/src/test/java/com/fractanomics/crosstraining/RelativeStrengthAnalyticsTest.kt` covering resolution precedence, tombstones, `lbs` conversion, `HALF_UP` rounding/trimming, Volume ÷ BW, Peak `% BW` separation, Cycle Goal date tracking, and `DailyRoutineCodec` `% BW` parity

## Phase 5: Remote MCP Server Read Parity & Tests (US5)

- [X] T011 [US5] Enrich `listSessions`, `listRepMaxes`, `listCycleGoals`, `getDailyRoutineProgress`, and `getWorkoutSummary` with additive optional `% BW` and body-weight resolution fields in `mcp-server/src/store.ts` while preserving all `45` tool registrations and flat schemas
- [X] T012 [US5] Add unit & integration tests covering `% BW` parity, 7-day fallback, `Peak % BW`, `CALORIES` omission, and `45` flat schema invariants (`0` `$ref`, `0` `exclusiveMinimum`) in `mcp-server/test/mcp-server.test.ts`

## Phase 6: Polish, Convergence & Full Pre-PR Verification

- [X] T013 Run full verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\gradlew.bat lintDebug --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `Push-Location mcp-server; npm run build; npm test; Pop-Location`)
- [X] T014 Update `CHANGELOG.md` under `## [Unreleased]`, commit feature files to `feat/029-bodyweight-percentage-index`, push, open Pull Request, and monitor remote CI until 100% green
