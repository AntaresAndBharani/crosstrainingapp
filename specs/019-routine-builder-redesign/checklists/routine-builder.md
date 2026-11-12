# UX, Domain & Cross-Channel Sync Requirements Checklist: Daily Routine Builder Redesign

**Purpose**: Reviewer-level requirements quality validation ("Unit Tests for English") covering the 4-tier collapsible UX, per-exercise mixed-metric / `0 (Skip)` domain rules, workout logging expansion, and zero-migration Cloud Sync / MCP parity.
**Created**: 2026-11-12
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

**Note**: This custom checklist was generated for PR-reviewer requirements validation (`Depth: Standard`, `Audience: Reviewer (PR)`, `Focus: Collapsible Multi-Metric UX & Cross-Channel Sync/MCP Parity`).
**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the requirements-quality criterion is satisfied.
**Marker Semantics**: `[x]` means the criterion has been reviewed and satisfied for requirements quality in `spec.md`, `plan.md`, `data-model.md`, and `contracts/`. It does not mean implementation work is complete.

## Requirement Completeness

- [x] CHK001 Are expand/collapse behaviors and collapsed summary contents explicitly specified for all 4 hierarchy tiers (`Routine Info`, `Routine Block`, `Block Setup`, `Exercises` / `Exercise Card`)? [Completeness, Spec §FR-002, Contracts `ui-state-contract.md` §1]
- [x] CHK002 Are all supported per-exercise Target Metrics (`Reps`, `Calories`, `Time`, `Meters`), their default mappings from library `Exercise.metricType`, step increments, default values, and quick preset pills completely enumerated? [Completeness, Spec §FR-006, §FR-007, DataModel §1.2]
- [x] CHK003 Are all dirty-state dismissal triggers (both `Cancel` button and system back navigation) and confirmation prompt copy specified? [Completeness, Spec §FR-001, US1 Scenario 6]
- [x] CHK004 Are inline exercise search, category filtering, one-tap custom exercise creation (`+ Create & Add "{query}"`), reordering, and Undo deletion requirements defined? [Completeness, Spec §FR-011, US2 Scenarios 2 & 7]

## Requirement Clarity

- [x] CHK005 Are format-driven default `Block Sets` values unambiguously mapped for single-effort (`1`), `Tabata` (`8`), and structured strength/interval (`4`) execution formats, including manual override behavior? [Clarity, Spec §FR-005, US1 Scenario 5]
- [x] CHK006 Is the distinction between a valid per-set `0 (Skip)` slot and an invalid all-zero exercise (`Fixed = 0` or every `Per-Set` slot `= 0`) quantified with exact validation rules and inline error copy? [Clarity, Spec §FR-009, §FR-010]
- [x] CHK007 Are the `Block Sets` mutation rules (`oldCount -> newCount`) explicitly defined for both increasing sets (`Set N` copies `Set N-1`, including `0`) and decreasing sets (trimming to `newCount`)? [Clarity, Spec §FR-008, US2 Scenario 5]
- [x] CHK008 Is the Workout Logging (`Load Routine...`) expansion behavior clearly specified for both multi-exercise blocks (`SubBlockGroup`, preserved 1-based round indices across skipped `0` sets, metric column mapping) and single-exercise blocks? [Clarity, Spec §FR-013, US3 Scenarios 1–3, DataModel §4]

## Requirement Consistency

- [x] CHK009 Do `BlockKind` enumerations align consistently between Android (`Enums.kt`, including `GYMNASTICS` with `shortLabel <= 15` chars) and Remote MCP (`BlockKindValues` in `mcp-server/src/schemas.ts`)? [Consistency, DataModel §1.1, Contracts `mcp-routine-tools.md` §1]
- [x] CHK010 Does the `[rb_v2=...]` persistence grammar align identically across Room SQLite, CSV Backup, Cloud Sync, Workout Share Codes, and MCP `upsert_routine` / `list_routines` without leaking internal metadata headers into user-visible `notes`? [Consistency, Spec §FR-012, §FR-014, DataModel §2]
- [x] CHK011 Are sequence-order preservation rules for repeated exercises in a single block (`Shuttle Run -> Burpees -> Shuttle Run`) consistent with repository/cloud exercise deduplication rules? [Consistency, Spec §Edge Cases, Research §1, Plan §Constitution Check V]

## Acceptance Criteria Quality & Measurability

- [x] CHK012 Are touch target minimums (`48dp x 48dp`), viewport widths (`360dp` and `>= 600dp`), and OS font-scale thresholds (`1.0x` to `2.0x`, responsive breakpoint at `>= 1.4x`) objectively measurable? [Measurability, Spec §SC-003, Contracts `ui-state-contract.md` §2]
- [x] CHK013 Can lossless cross-channel round-trip fidelity (SC-002) and Workout Logging sub-block expansion accuracy (SC-004) be deterministically verified via automated unit tests? [Measurability, Spec §SC-002, §SC-004, Plan §Constitution Check III]

## Scenario & Edge Case Coverage

- [x] CHK014 Are legacy routine fallback requirements specified for pre-existing routines lacking `[rb_v2=...]` metadata (e.g. `"5"`, `"21-15-9"`, `"1 + 1"`, `"TRISET_1"`)? [Coverage, Spec §Assumptions, US4 Scenario 3, DataModel §2]
- [x] CHK015 Is in-session per-metric value caching specified when a user switches an exercise's Target Metric back and forth during editing? [Edge Case, Spec §Edge Cases, §FR-006]
- [x] CHK016 Is editor behavior specified when the user deletes all exercises or the last remaining block (`Save Routine` disabled, starter/add block available, 4s Undo)? [Edge Case, Spec §Edge Cases, §FR-010, §FR-011]
- [x] CHK017 Are high-set-count `Per-Set` scenarios (e.g., 8-set Tabata or 12-set EMOM waves) addressed via multi-row wrapping and pattern helpers (`Copy Set 1 to All`, `Descending`, `Alternate 0s`)? [Edge Case, Spec §Edge Cases, §FR-007]

## Non-Functional, Governance & Dependency Requirements

- [x] CHK018 Are non-color visual indicators (dashed border + explicit `(Skip)` text) and TalkBack screen-reader announcements specified for `0 (Skip)` set cards across light, dark, and high-contrast themes? [Accessibility, Spec §FR-009, Plan §Constitution Check IV]
- [x] CHK019 Are MCP JSON Schema connector constraints (zero `$ref`, zero `exclusiveMinimum`, inclusive `.min(0)` bounds, atomic `"exercises"` + `"routines"` mutation lock) explicitly documented? [Compliance, Spec §FR-014, Contracts `mcp-routine-tools.md`, Plan §Constitution Check VII]
- [x] CHK020 Is zero-migration Room `v10` compatibility explicitly documented and justified against Constitution Principle V? [Dependencies & Governance, Spec §FR-012, Research §1, Plan §Technical Context]

## Notes

- All 20 requirement-quality criteria (`CHK001`–`CHK020`) audited against `spec.md`, `plan.md`, `research.md`, `data-model.md`, and `contracts/` with zero unresolved gaps.
