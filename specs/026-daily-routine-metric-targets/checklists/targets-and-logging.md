# Requirements Quality Checklist: Daily Routine Targets & Metric-Aware Workout Logging (`SPEC-026`)

**Purpose**: Validate clarity, completeness, consistency, and measurability of `SPEC-026` requirements before implementation  
**Created**: 2026-11-19  
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/026-daily-routine-metric-targets/spec.md)  
**Review Ownership**: Self-reviewed under `/e2e-sdlc --full` Phase 6 gate (`[x]` indicates requirement quality is satisfied).

## Requirement Completeness & Scope Boundaries

- [x] CHK001 - Does the spec explicitly state that Available Blocks (`Library -> Blocks`) and Exercises (`Library -> Exercises`) remain lightweight and instructions-only without added target inputs? [Completeness, Spec §FR-001]
- [x] CHK002 - Are optional target controls specified for both standalone `EXERCISE` items and `BLOCK` items inside Daily Routines, with targets defaulting to unset? [Completeness, Spec §FR-002]
- [x] CHK003 - Does the spec define all four target metrics (`Reps`, `Calories`, `Time`, `Meters`) and both modes (`Fixed` and `Per-Set` with `0 (Skip)`)? [Completeness, Spec §US1, §FR-004]
- [x] CHK004 - Are the exact metadata persistence mechanisms (`RoutineBlock.setsCount`, `targetRepsScheme`, `[rb_v2=...]`, `[log_metric=...]`) documented as requiring zero Room schema migration? [Completeness, Spec §FR-002, §FR-009]
- [x] CHK005 - Does the spec specify requirements for both `EXERCISE:` and `BLOCK:` cards in `Log Session` and `Edit Session`? [Completeness, Spec §FR-006, §FR-007]

## Requirement Clarity & UX Precision

- [x] CHK006 - Is the validation rule for all-zero enabled targets vs cleared/unset targets unambiguous and accompanied by exact validation error guidance? [Clarity, Spec §US1/AC4, §FR-004]
- [x] CHK007 - Are responsive column counts quantified for `Reps + Weight` tiles (`2` cols `<600dp`, `1` col at `fontScale >= 1.5f`, `4` cols `>=600dp`) and conditioning metric tiles (`3` cols `<600dp`, `2` cols at `fontScale >= 1.3f`, `4` cols `>=600dp`)? [Clarity, Spec §FR-007]
- [x] CHK008 - Is the precedence order for initializing an exercise's active logging metric explicitly defined (Daily Routine target -> hydrated session `[log_metric=...]` -> library `Exercise.metricType`)? [Clarity, Spec §FR-006]
- [x] CHK009 - Are formatting expectations specified for `HistoryScreen`, `ProgressScreen`, and `DailyRoutineCard` item chips across weighted reps, bodyweight reps, and metric-value sets? [Clarity, Spec §FR-005, §FR-011]

## Scenario & Edge Case Coverage

- [x] CHK010 - Does the spec define behavior when toggling an exercise's active metric chip (`Reps` -> `Cal` -> `Time` -> `Reps`) after entering values? [Coverage, Spec §Edge Cases, §FR-006]
- [x] CHK011 - Does the spec define how bodyweight rep-only sets (blank weight, positive reps) persist, display, and avoid spurious PR chips? [Coverage, Spec §Edge Cases, §FR-009]
- [x] CHK012 - Are round-number preservation rules (`0 (Skip)` rounds omitted, 1-based `groupIndex` displayed as `Rd N`) consistent across `Log Session` and `Edit Session`? [Consistency, Spec §FR-008, §FR-010]
- [x] CHK013 - Is the Edit Session boundary between simplified card hydration and legacy `CompactBlockEditor` retention (`isWarmup`, `isFailed`, Metcon `resultText`) clearly defined? [Clarity, Spec §FR-010]
- [x] CHK014 - Does the spec address combining Daily Routine target reps with historical prior-session weight pre-fill? [Coverage, Spec §US3/AC2, §FR-008]
- [x] CHK015 - Does the spec ensure `"Also update ... routine template"` preserves configured targets on surviving routine items? [Consistency, Spec §Edge Cases, §FR-012]

## Dual-Client Parity & Non-Functional Criteria

- [x] CHK016 - Are Remote MCP input/output expectations consistent across `upsert_daily_routine`, `list_daily_routines`, `log_daily_routine_session`, `get_daily_routine_progress`, `log_block_session`, and `update_block_session_log`? [Consistency, Spec §FR-013]
- [x] CHK017 - Is backward compatibility for legacy MCP callers (`set_weights_kg`, `weights`) explicitly guaranteed alongside `sets` and `metric`? [Completeness, Spec §US4/AC2, §Assumptions]
- [x] CHK018 - Are MCP schema constraints (`39` tools, `0` `$ref`, `0` `exclusiveMinimum`) explicitly captured as verifiable criteria? [Measurability, Spec §FR-013]
- [x] CHK019 - Are mobile accessibility requirements (`>= 48.dp` touch targets, `360dp` non-wrapping chips, merged TalkBack semantics) specified consistently with the project constitution? [Non-Functional, Spec §SC-003]
- [x] CHK020 - Do measurable outcomes in `SC-001..SC-004` map directly to deterministic Android, PowerShell, Lint, and MCP verification gates? [Measurability, Spec §FR-014, §SC-004]

## Notes

- All 20 requirement-quality checklist items audited against `spec.md`, `plan.md`, `data-model.md`, and `contracts/routine-targets-and-logging-contract.md` with zero unresolved gaps.
