# Complex Terminology, Responsive UX & MCP Compatibility Checklist: Rename Blocks to Complexes

**Purpose**: Validate requirements quality across terminology consistency, compact 360dp & scaled-font layouts, zero-migration persistence, scope boundaries, and MCP schema compatibility before implementation  
**Created**: 2026-11-21  
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/027-rename-blocks-to-complexes/spec.md)

**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the reviewer determines the requirements-quality criterion is satisfied.

## Terminology & Scope Completeness

- [x] CHK001 Are all 6 primary Android surfaces (`Library`, `New/Edit Complex`, `Daily Routine Composer & Cards`, `Log/Edit Session`, `Record/Track PRs`, and `Progress & Goals`) explicitly specified with `"Complex"` / `"Complexes"` wording and zero `"Available"` prefix?
- [x] CHK002 Are excluded secondary/legacy surfaces (`Training Cycles` periodization blocks, Fat Loss `"Block Completion"` KPI, Quick Parse, Workout Journey Assistant, Voice Ingestion, legacy Routine Builder) explicitly isolated so they remain untouched?
- [x] CHK003 Are all interactive states (empty states, zero-match search states, inline validation errors, undo snackbars, deleted-complex notices, accessibility `contentDescription` strings) defined in `spec.md` and `contracts/`?

## Responsive 360dp Layout, Validation & Accessibility

- [x] CHK004 Does the specification define single-line `13.sp` tabular-num tab behavior on `360dp` devices and `ScrollableTabRow` fallback at `fontScale >= 1.2f` for `Complexes ({count})`?
- [x] CHK005 Are non-truncating search bar placeholders (`"Search routines..."`, `"Search complexes..."`, `"Search exercises..."`) and `maxLines = 2` `Complex Name` inputs explicitly specified?
- [x] CHK006 Is the `Edit Daily Routine` eyebrow layout (Row 1: index + `COMPLEX`/`EXERCISE` badge + `↑`/`↓`/`✕`; Row 2: full-width up-to-2-line title) specified alongside `fontScale >= 1.4f` vertical stacking for dual bottom buttons and `Log Complex ▸` card CTA?
- [x] CHK007 Are `New Complex` / `Edit Complex` save validation rules clearly specified to require both a non-blank Complex Name and at least 1 selected exercise with `"Enter a complex name and select at least 1 exercise."`?

## Zero-Migration Persistence & MCP Backward Compatibility

- [x] CHK008 Are all persisted Room schema tables/columns, navigation route keys, Firestore collections, and format markers (`AVAILABLE_BLOCK`, `DR_BLOCK`, `[ab_prs=...]`, `[dr_item=BLOCK:...]`) explicitly preserved unchanged?
- [x] CHK009 Are all 6 Complex-first MCP tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`), 6 legacy Block aliases, and Daily Routine `item_type: "COMPLEX"` / `complex_id` / `complex_title` precedence rules specified with flat schemas (`0` `$ref`, `0` `exclusiveMinimum`)?
- [x] CHK010 Do MCP response requirements preserve legacy compatibility keys (`blockId`, `blockTitle`, `blockCount`, `availableBlockCount`, `availableBlocks`/`availableBlock` on legacy endpoints) while introducing Complex-first keys (`itemType: "COMPLEX"`, `complexId`, `complexTitle`, `complexCount`, `complexesCount`)?

## Notes

- Audited `spec.md`, `plan.md`, `research.md`, `data-model.md`, and `contracts/complexes-ui-and-mcp-contract.md` against all 10 items `CHK001..CHK010`; all criteria pass with zero remaining ambiguities.
