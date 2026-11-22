# Requirements Quality Checklist: Relative Strength (`% BW`)

**Purpose**: Pre-implementation PR-reviewer audit of `spec.md` and `plan.md`  
**Depth**: Standard | **Audience**: PR Reviewer  
**Created**: 2026-11-22

- [x] CHK001 — Formula `(liftedWeightKg / bodyWeightKg) * 100`, `BigDecimal` `HALF_UP` scale `1`, and `.0` trimming rules are unambiguously specified (`FR-001`, `contracts/relative-strength-api.md`).
- [x] CHK002 — Unit alignment (`lbs` → `kg` via `WeightAnalytics.LBS_PER_KG`) guarantees identical `% BW` in `kg` and `lbs` modes (`FR-001`, `research.md`).
- [x] CHK003 — Body-weight resolution precedence (`EXACT` → `1..7 days prior` `ESTIMATED_PRIOR` with `~` → `UNAVAILABLE` `—`) and tombstone (`deletedAtMillis != null`) exclusion are explicitly covered (`FR-002`, `US-1`).
- [x] CHK004 — Non-weighted exclusion (`CALORIES`, `TIME`, `DISTANCE`, unloaded `REPS`) and warm-up/failed set exclusion are consistent across Android and MCP (`FR-003`, `US-1`, `US-5`).
- [x] CHK005 — Independent Peak `% BW` evaluation is distinguished from heaviest raw `All-time top` when achieved on different dates (`FR-004`, `US-2`).
- [x] CHK006 — Hybrid UI rules cover inline `% BW` on KPIs, PR rows, Routine overview cards, History rows (while keeping raw `"Sets: ..."`), plus `rememberSaveable` `Weight | % BW` chart switchers (`FR-005`, `FR-006`).
- [x] CHK007 — Chart gap-breaking (`null` points), coverage caption (`"X of Y sessions with BW"`), and `"Log body weight"` empty-state CTA opening `WeightEntryBottomSheet` are clearly mapped (`FR-006`, `research.md`).
- [x] CHK008 — Cycle Goals `Start` and `Current Best` achievement-date tracking (`baselineStartDate`, `currentBestDate`) and `Goal` anchor (`Cycle.targetWeightKg` → `LATEST_BW` fallback) are defined (`FR-007`, `US-3`).
- [x] CHK009 — `HistoryScreen` chip + expanded block summary `% BW` and raw `#N:` set isolation are verified (`FR-008`, `US-4`).
- [x] CHK010 — `MASTER.md` & `progress-tracking.md` requirements (Material 3 color tokens across 4 themes, `tnum`, `>= 48.dp` touch targets, `360dp` + `fontScale >= 1.3f` reflow, merged TalkBack semantics) are enforced (`FR-009`).
- [x] CHK011 — Remote MCP parity adds strictly optional fields across the 5 read tools while preserving `45` tools, `0` `$ref`, and `0` `exclusiveMinimum` (`FR-010`, `data-model.md`).
- [x] CHK012 — Zero Room schema migration and zero Firestore collection breaking changes are confirmed (`FR-011`).
