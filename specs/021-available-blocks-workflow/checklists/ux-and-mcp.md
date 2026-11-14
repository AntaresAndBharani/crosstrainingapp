# Requirements Quality Checklist: UX & MCP Contracts (`SPEC-021`)

**Purpose**: Gate Android Compose responsiveness, weight-only ergonomics, multi-PR correctness, and MCP schema compliance.
**Created**: 2026-11-14
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/021-available-blocks-workflow/spec.md)

## Library & Logging UX

- [x] CHK001 Library Tab 1 displays `"Available Blocks ({count})"` with live search over title, instructions, and linked exercises
- [x] CHK002 `AvailableBlockCard` enforces `>= 48.dp` `Log Block ▸` touch target, haptic `CLOCK_TICK`, expandable instructions (`maxLines = 4`), `tnum` last weights, `🔥 {N} tracked PR(s)` badge, and 4s `"Block removed"` `[Undo]` Snackbar
- [x] CHK003 Log Session renders weight-only `4`-column `FlowRow` tiles (`2`-column at `fontScale >= 1.4f`, `minHeight = 64.dp`), parses `.` & `,` decimals, permits blank bodyweight/cardio tiles, and shows `"Pre-filled from last session ({Date})"` when prior history exists
- [x] CHK004 Tracked PR chips evaluate live against stored best `RepMax`, `[✕]` persists removal onto `trackedPrs`, and `+ Record PR 🔥` supports multi-movement selection with `1RM | 2RM | 3RM | 5RM | 10RM`

## Analytics, Sync & MCP Contracts

- [x] CHK005 `ProgressMode.BY_ROUTINE` enum symbol preserved for existing tests while displayed as `"By block"`, with isolated `routineBlockPerformances` and `"Achieved in {Block} · {Date}"` PR attribution
- [x] CHK006 Zero Room DB version bump (`v10`), zero `firestore.rules` changes, safe `routineId` download fallback, and `RepMax.blockId` preservation on `updateSession`
- [x] CHK007 All 33 MCP tools expose flat JSON schemas (`0` `$ref`, `0` `exclusiveMinimum`) and pass `npm run build && npm test`
