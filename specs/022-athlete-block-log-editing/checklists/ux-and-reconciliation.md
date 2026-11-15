# UX, Accessibility & PR Reconciliation Quality Checklist (`SPEC-022`)

**Purpose**: Validate UX parity, accessibility guardrails, live auto-sync rules, and DB/cloud reconciliation invariants  
**Created**: 2026-11-15  
**Feature**: [spec.md](../spec.md)

## UX Parity & Container Chrome (`SPEC-BLOCK-LOG-EDIT-01`)

- [x] History -> Edit opens `SessionEditorScreen` with `copy == false` and hydrates logged strength/Available Blocks into `SimplifiedBlockLogCard`
- [x] `"Pre-filled from last session ({Date})"` subtitle is hidden when `isEditMode == true`
- [x] `Load Block ▾` and `[ + Add Block ]` remain available to append blocks in edit mode
- [x] Non-weight legacy metcon blocks with `resultText` / `resultValue` / non-weight `metricValue` remain in `residualLegacyBlocks` (`CompactBlockEditor`)
- [x] Unsaved modifications enable `BackHandler(enabled = isDirty)` showing a Discard Changes confirmation dialog

## Live Set-to-PR Auto-Sync & Modal Ergonomics

- [x] Tracked PR slot maintains `isCustomWeightOverride` (`false` when linked to max set weight, `true` on manual sheet edit or divergent persisted PR weight)
- [x] Editing, adding, or deleting weighted set tiles live-updates all non-overridden PR chips and `Record / Track PRs` inputs for that `movementId`
- [x] `bestStoredRepMaxWeight` and `resolveLastBlockSession` exclude `editingSessionId` during edit-mode PR badge evaluation
- [x] Removing a PR chip via `✕` in edit mode surfaces an `[Undo]` Snackbar prior to Save
- [x] Multi-movement blocks key PR slots by `(movementId, rmReps)` without cross-movement collisions

## Accessibility & Visual Tokens

- [x] Header timer (`⏱`), header delete (`🗑`), set delete (`✕`), and PR chip dismiss (`✕`) meet `48.dp` minimum touch targets
- [x] PR chips render high-contrast emerald/green feedback (`#0B6E3F` / `#2ECC71`) and merged TalkBack semantics
- [x] Weight values and inputs apply tabular numerals (`fontFeatureSettings = "tnum"`)
- [x] `Record / Track PRs` `ModalBottomSheet` applies `Modifier.imePadding()` and scrolls cleanly on small viewports

## Persistence, Template Inheritance & MCP Sync (`SPEC-MCP-BLOCKS-02`)

- [x] `Repository.updateSession(..., reconcileRepMaxes = true)` updates modified PRs in place (`RepMax.id` preserved), inserts additions, deletes removals
- [x] Saving merges newly tracked `(movementId, movementName, rmReps)` slots onto parent `AvailableBlock` (`[ab_prs=...]`)
- [x] `AppViewModel.saveSession` / `updateSession` / `deleteSession` enqueue `UserCloudSyncManager.uploadUserData`
- [x] MCP `update_block_session_log` (#34) uses flat schema (`0` `$ref`, `0` `exclusiveMinimum`) and tombstones replaced/removed `RepMaxRecord`s (`deletedAtMillis`)
- [x] Android `UserCloudSyncManager` Step 5 deduplicates natural keys and deletes matching local `RepMax` rows on tombstones
