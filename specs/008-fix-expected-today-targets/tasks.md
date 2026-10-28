# Tasks: Fix Expected Today Pace & Linear Targets Across Rolling Windows (7D–28D)

**Input**: Design documents from `/specs/008-fix-expected-today-targets/`

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `research.md`, `data-model.md`, `contracts/`

**Tests**: Unit and Compose UI test tasks are included per Constitution Principle II (Testing & CI Parity) and `spec.md` (`SC-001`–`SC-003`).

**Organization**: Tasks are grouped by user story to enable independent implementation and verification of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Exact file paths are included in every task description.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify branch state and baseline project files before modifying domain calculation logic.

- [X] T001 Verify active feature branch `feat/008-fix-expected-today-targets` and Spec-Kit configuration in `.specify/feature.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain baseline check in `FatLossAnalytics.kt` before applying rolling window calculation changes.

- [X] T002 Inspect `computeRollingWindowTargetRow` and `WindowCalculationStatus` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` to confirm shared window helper (`computeWindowAverageKg`) and unit rounding (`toUnit`, `round2`) contracts

**Checkpoint**: Foundation verified — User Story implementation can now proceed.

---

## Phase 3: User Story 1 - Consistent 1-Day Step Expected Today Across All Rolling Windows (Priority: P1) 🎯 MVP

**Goal**: Anchor `Expected Today` for all 4 rolling windows (`7D`, `14D`, `21D`, `28D`) in both `Pace` and `Linear` modes to yesterday's $N$-day rolling average ($[t - N, t - 1]$) plus $1 \times \text{dailyRequiredRateInUnit}$, then back-solve for today's scale weight over $[t - (N - 1), t - 1]$.

**Independent Test**: Run `./gradlew testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest"` and verify that 35-day multi-horizon histories produce monotonic, realistic `Expected Today` scale weights across `7D`, `14D`, `21D`, and `28D`.

### Implementation & Tests for User Story 1

- [X] T003 [US1] Update `computeRollingWindowTargetRow` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` to compute `yesterdayNdAvgKg = computeWindowAverageKg(allActiveEntries, anchorDate, referenceDate.minusDays(1L)) ?: priorLookbackAvgKg`, `yesterdayNdInUnit = toUnit(yesterdayNdAvgKg, weightUnit)`, `targetAvgTodayInUnit = yesterdayNdInUnit + dailyRequiredRateInUnit`, `expectedScaleTodayDisplay = round2((kToday + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`, and `baselineNdDisplay = round2(yesterdayNdInUnit)`, removing the unused `windowRequiredRateInUnit` variable
- [X] T004 [US1] Update existing `Expected Today` and `baselineNdDisplay` assertions in `paceAndLinearTargets US1 & US2`, `paceAndLinearTargets US1 - sub 0_1 daily step`, and `paceAndLinearTargets US1 - dense 7D window` in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`
- [X] T005 [US1] Add multi-horizon regression test `paceAndLinearTargets US1 - multi-horizon 7D 14D 21D 28D expected today does not diverge` in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` verifying all 4 horizons (`7D`, `14D`, `21D`, `28D`) in both `Pace` and `Linear` modes
- [X] T006 [P] [US1] Update `74.51 kg` assertion to `76.03 kg` in `scenario17_paceAndLinearTargets3x4ContainerRendersAt360dpFontScale13` in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt`

**Checkpoint**: User Story 1 is complete and independently verifiable.

---

## Phase 4: User Story 2 - Preserve Rolling Window Maturity & Needs-Log Guards (Priority: P2)

**Goal**: Preserve `WindowCalculationStatus.InsufficientHistory` when earliest log $> t - N$ and `WindowCalculationStatus.NeedsLog(requiredDate = t - N)` when $[t - 2N + 1, t - N]$ has no active weigh-ins, and verify sparse-window fallback when $[t - N, t - 1]$ is empty.

**Independent Test**: Run `./gradlew testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest"` and confirm all `US3` sparse/insufficient-history/needs-log tests pass.

### Implementation & Tests for User Story 2

- [X] T007 [US2] Update `paceAndLinearTargets US3 - empty prior window` in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` to verify that when `[t - 6, t - 1]` has zero entries ($k_{\text{today}} = 0$) and `[t - 13, t - 7]` has `80.0 kg`, `Expected Today` steps by 1 day (`79.93 kg`) via the `?: priorLookbackAvgKg` fallback while `InsufficientHistory` and `NeedsLog` tests remain intact

**Checkpoint**: User Stories 1 and 2 are both complete and verified.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Run full pre-PR verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`) and update `CHANGELOG.md`.

- [X] T008 Run full pre-PR verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon`) from repository root
- [X] T009 Add `### Fixed` entry under `## [Unreleased]` in `CHANGELOG.md` documenting the 1-day rolling window fix for `Expected Today` across `7D`, `14D`, `21D`, and `28D` in `Pace & Linear Targets`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup (`T001`).
- **User Story 1 (Phase 3)**: Depends on Foundational (`T002`). `T004`, `T005`, and `T006` depend on `T003`.
- **User Story 2 (Phase 4)**: Depends on `T003` in User Story 1.
- **Polish (Phase 5)**: Depends on all User Story tasks (`T003`–`T007`).

### Parallel Opportunities

- `T004`/`T005` (`FatLossAnalyticsTest.kt`) and `T006` (`WeightProgressionCardComposeTest.kt`) touch different files and can be updated in parallel after `T003`.

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & Phase 2 (`T001`–`T002`).
2. Complete Phase 3 (`T003`–`T006`) to fix `Expected Today` across all 4 horizons (`7D`, `14D`, `21D`, `28D`).
3. Complete Phase 4 (`T007`) and Phase 5 (`T008`–`T009`) to validate edge cases, run full regression suites, and document changes in `CHANGELOG.md`.
