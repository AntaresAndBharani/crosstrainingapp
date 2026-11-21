# Compact Complex Logging Requirements Checklist: Compact Complex Session Logging

**Purpose**: Reviewer-owned verification of requirements completeness, clarity, and architectural parity across `spec.md`, `plan.md`, `data-model.md`, and `contracts/`.  
**Created**: 2026-11-21  
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/028-compact-complex-logging/spec.md)

## Cluster 1: Compact UX, Override Drawer & Accessibility

- [x] CHK001 Single-component delegation boundary (`itemKind == BLOCK && exerciseSections.size >= 2`) explicitly isolates single-exercise Blocks and `StandaloneExercise` cards (`FR-001`, `Edge Cases`).
- [x] CHK002 Unified `S1..SN` round numbering eliminates `Set N` vs `Rd N` mismatch across sparse complexes such as `1-2-3-1-2-3` and `3-1-3-1-3-1` (`US1`, `FR-002`).
- [x] CHK003 4-button `Reps | Cal | Time | Meters` chip bar is explicitly removed from the default Complex view (`3A`) and relocated into secondary `By ex. ▸` -> `Unit: {Metric} ▾` (`FR-003`).
- [x] CHK004 Inline `SetExerciseOverrideDrawer` (`AnimatedVisibility`) defines `Active`/`Skip` toggle semantics (`Skip -> Active` inherits linked shared weight & restores target; `Active -> Skip` clears weight & excludes from PRs) (`FR-004`, `FR-005`).
- [x] CHK005 `resolveSharedSetWeight` exhaustively covers `LINKED`, `MIXED` (with `majorityWeightText` & `divergentExerciseIndices`), `ALL_SKIPPED`, and `NON_WEIGHTED_ONLY` (`— (BW)`) states (`FR-006`, `FR-007`).
- [x] CHK006 Undo behaviors are specified with 4-second `[Undo]` Snackbars for both `Mixed -> Linked` edits/re-links and `removeComplexSet` (when `> 1` set remains) (`FR-007`, `FR-008`).
- [x] CHK007 Decimal entry edge cases (`,` to `.`, mid-typing trailing `.`, negative/non-numeric filtering, `<= 9999` clamp) and `ImeAction.Next` vertical column traversal are specified (`US1`, `Edge Cases`).
- [x] CHK008 Responsive breakpoints (`360dp` column budget, `widthIn(max = 640.dp)`, `>= 520dp` short names in pills, `fontScale >= 1.4f` 2-line reflow), `.imePadding()`/`BringIntoViewRequester`, `>= 48.dp` touch targets, WCAG AA contrast, and symbol-free TalkBack descriptions are specified (`FR-011`).

## Cluster 2: Zero-Migration Persistence, PR Isolation & MCP Parity

- [x] CHK009 Skip-aware PR evaluation (`blockMaxSetWeight` / `maxSetWeightFor`) excludes skipped rounds (`skippedFlags`) so skipped movements cannot pick up heavier sibling set weights (`FR-009`).
- [x] CHK010 Mixed barbell + bodyweight/erg complexes apply shared `kg` strictly to weighted (`REPS` + `MetricType.WEIGHT`) movements (`US3`, `data-model.md`).
- [x] CHK011 Zero Room schema migrations (`v10`): skipped rounds persist via omitted `BlockSet` rows + `groupIndex` canonical indices, and all-skipped movements round-trip via `hasNoLoggedSets` (`FR-010`, `data-model.md`).
- [x] CHK012 Pre-fill alignment maps `LastBlockSessionSummary.exerciseGroupIndicesById/Name` (`groupIndex - 1`, fallback ordinal) and hides `"Pre-filled from last session ({Date})"` in `Edit Session` (`US1`, `US3`, `FR-010`).
- [x] CHK013 `mcp-server` contract specifies `shared_set_weights_kg` (and `exercise_targets`) across `log_complex_session`, `log_block_session`, `update_complex_session_log`, `update_block_session_log`, and `log_daily_routine_session`, preserving `45` tools, `0` `$ref`, and `0` `exclusiveMinimum` (`FR-012`, `contracts/mcp-complex-shared-weights.md`).
- [x] CHK014 Verification commands and test coverage targets span Android JUnit (`testDebugUnitTest`), `lintDebug`, PowerShell Pester suite (`Invoke-ScriptTests.ps1`), and `mcp-server` (`npm run build && npm test`) (`quickstart.md`, `SC-003`).

## Notes

- Audited `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/mcp-complex-shared-weights.md`, and `quickstart.md` against `CHK001..CHK014`; all 14 items verified complete and unambiguous.
