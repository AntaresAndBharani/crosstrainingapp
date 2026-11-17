# UX, Data & MCP Requirements Quality Checklist: Daily Routines (`SPEC-025`)

**Purpose**: Reviewer validation of requirements quality, edge-case coverage, accessibility, zero-migration persistence, and MCP parity across `spec.md` and `plan.md`  
**Created**: 2026-11-17  
**Feature**: [spec.md](../spec.md)

**Review Ownership**: Self-reviewed against `spec.md`, `plan.md`, `data-model.md`, and `contracts/daily-routines-ui-and-mcp-contract.md` using Standard Depth defaults.  
**Marker Semantics**: `[x]` means the requirement-quality criterion was verified in the specification and plan.

## Library & Composer UX (`SPEC-DAILY-ROUTINES-LIB-01`)

- [x] CHK001 Are the 3 Library tab titles (`Routines ({count})`, `Blocks ({count})`, `Exercises ({count})`), ordering, and `360dp` single-line `maxLines = 1` rules explicitly specified?
- [x] CHK002 Does the Fast Composer specification explicitly cover Title validation (`non-blank`), optional Notes, `>= 1` item gate, inline per-exercise note editing, `↑`/`↓` reorder, and `✕` removal?
- [x] CHK003 Are distinct visual chip badges (`BLOCK:` vs `EX:`), `> 6` item `+N more` overflow behavior, and deleted-block warning state (`missingBlockCount`) specified?
- [x] CHK004 Does `DailyRoutineCard` specify `Log Routine ▸` (`HapticFeedbackConstants.CLOCK_TICK`, `>= 48.dp`), `⋮` (`Edit`, `Duplicate`, `Delete` + 4s `[Undo]` Snackbar), and empty state?

## Session Tracking & Unified History (`SPEC-ROUTINE-SESSION-TRACK-01`)

- [x] CHK005 Is the `Load Daily Routine ▾` selector bar + `Log Routine ▸` navigation handoff defined for loading both Blocks and standalone Exercises in routine order?
- [x] CHK006 Is the elimination of the blank legacy `1 Move...` spreadsheet card on fresh `Log Session` explicitly required while preserving `CompactBlockEditor` for residual legacy/metcon blocks in `Edit Session`?
- [x] CHK007 Are `SimplifiedExerciseLogCard` (`EXERCISE: {Name}`), unified cross-app set weight pre-fill (`"Pre-filled from last session ({Date})"` vs 4 empty tiles + `"First time logging — enter set weights"`), and live PR chip auto-sync explicitly specified?
- [x] CHK008 Is the conditional `[ ] Also update "{Routine Title}" routine template` checkbox behavior (unchecked by default, visible only when structural diff exists against a loaded routine) fully specified?
- [x] CHK009 Is the `DailyCheckInCard` `Rest` chip single-line anti-wrap requirement (`maxLines = 1, softWrap = false`, `FlowRow`) explicitly captured?

## Progress & Goals (`SPEC-ROUTINE-PROGRESS-01`)

- [x] CHK010 Is `By routine` (`ProgressMode.BY_DAILY_ROUTINE`) specified as the first filter chip while preserving `By block` (`ProgressMode.BY_ROUTINE`) and `AppViewModel.progressMode` default `BY_EXERCISE`?
- [x] CHK011 Are the 0-routines empty state (`Library -> Routines` CTA), `All Items ({count})` overview summary cards with directional non-color trend indicators (`↗`, `→`, `↘`), and unlogged item placeholders defined?
- [x] CHK012 Does Constituent Drill-Down specify `[ ⬅ All Items ]`, 2x2 KPI Grid (`All-time top`, `Last top`, `Last average`, `Last volume`), `EvolutionCards`, Tracked PRs, and expandable `BlockHistoryCard`?

## Data Model, Cloud Sync & MCP Parity (`SPEC-MCP-DAILY-ROUTINES-01`)

- [x] CHK013 Is Room schema v10 zero-migration encoding (`DAILY_ROUTINE`, `DR_BLOCK`, `DR_EXERCISE`, `STANDALONE_EXERCISE`, `[dr_item=...]`, `[ab_prs=...]`) explicitly specified?
- [x] CHK014 Are `dr:` vs `ab:` namespace-scoped natural keys enforced across `Repository`, `UserCloudSyncManager`, and `mcp-server` so identical names never collide or overwrite?
- [x] CHK015 Are all 5 new MCP tools (`list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`) specified with flat schemas (`0` `$ref`, `0` `exclusiveMinimum`) and accurate `ToolAnnotations`?
- [x] CHK016 Does `list_available_blocks` / `get_workout_summary` explicitly isolate `DAILY_ROUTINE` entries from `AvailableBlock` counts?

## Accessibility, Compatibility & Pre-PR Gates

- [x] CHK017 Are `48.dp` touch targets, `tnum` numeric styling, `Modifier.imePadding()`, and merged TalkBack descriptions specified across new/updated UI surfaces?
- [x] CHK018 Are existing test invariants (`DemoData.SEED_VERSION == 4`, `buildCustomAvailableBlockState` 3-tile/null-hint behavior, `AppViewModel.progressMode` initial value) explicitly protected?
- [x] CHK019 Are JVM unit test, Pester static script test, Android lint, and MCP Vitest test deliverables mapped in `plan.md`?
- [x] CHK020 Are zero `[NEEDS CLARIFICATION]` gaps or contradictions remaining between `spec.md`, `plan.md`, `data-model.md`, and `contracts/`?

## Notes

- All 20 quality gate items (`CHK001`–`CHK020`) audited against `spec.md`, `plan.md`, `data-model.md`, and `contracts/daily-routines-ui-and-mcp-contract.md` and marked `[x]` on 2026-11-17.
