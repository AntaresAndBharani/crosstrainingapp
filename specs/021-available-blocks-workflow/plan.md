# Implementation Plan: Available Blocks Workflow (`SPEC-021`)

**Branch**: `feat/021-available-blocks-workflow` | **Date**: 2026-11-14 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/021-available-blocks-workflow/spec.md)

## Summary
Replace the multi-tier `Daily Routines` authoring/logging UX with a streamlined **Available Blocks** workflow spanning Library (`Available Blocks ({count})`, `AvailableBlockCard`, `QuickCreateBlockSheet`, Undo Snackbar), Log Session (`Log Block ▸` / `Load Block ▾`, last-session weight pre-fill, 4/2-column weight-only set tiles, auto-inherited multi-movement `🔥 NEW PR` vs `Best:` evaluation, `MultiMovementPrPickerSheet`), Progress (`By block` chip, isolated block telemetry, `By exercise` block PR attribution), Cloud Sync, and `5` new Remote MCP tools (`33` total). Persist `AvailableBlock` cleanly on top of `RoutineWithBlocks` (`defaultFormat = "AVAILABLE_BLOCK"` + `[ab_prs=...]`) so zero Room schema or `firestore.rules` changes are required.

## Technical Context
- **Language/Version**: Kotlin (JVM 17, Android SDK 35 / minSdk 26) & TypeScript (Node 22, ESM)
- **Primary Dependencies**: Jetpack Compose Material 3, Room SQLite (`v10`), Firebase Firestore (`UserCloudSyncManager`), `@modelcontextprotocol/sdk` + Zod + `firebase-admin`
- **Storage**: Room `v10` (`RoutineWithBlocks`, `SessionWithBlocks`, `RepMax`) & Firestore 7-document model (`routines`, `sessions`, `rep_maxes`, etc.)
- **Testing**: JUnit 4 (`testDebugUnitTest`), Node test runner (`npm test` in `mcp-server`), Pester/PowerShell (`Invoke-ScriptTests.ps1`), Android Lint (`lintDebug`)

## Constitution Check
- **I. MVVM & UDF**: Pure domain logic in `AvailableBlockCodec.kt` and `ProgressAnalytics.kt`; immutable `StateFlow` in `AppViewModel.kt`; Compose screens stay stateless/UDF-compliant. **PASS**
- **II. Deterministic Analytics & Unit Consistency**: Set weight parsing (`.` / `,`), PR comparisons, `lastSession` weight formatting, and `ProgressAnalytics` helpers are pure deterministic Kotlin functions. **PASS**
- **III. Test-First & 100% CI Parity**: New unit/contract tests (`AvailableBlockCodecTest.kt`, `AvailableBlockRepositoryAndSyncTest.kt`, extended `mcp-server.test.ts`), all existing Android/PS1/MCP tests green. **PASS**
- **IV. Responsive Mobile UX & Accessibility**: `48.dp` minimum `Log Block ▸` pill height, `64.dp` weight tile minHeight, 2-column responsive switch at `fontScale >= 1.4f`, `tnum` figures, `mergeDescendants` accessibility semantics. **PASS**
- **V. Offline-First & Idempotent Sync**: Zero Room `v10` bump, zero new Firestore collection names (`firestore.rules` compliant), FK-safe `routineId` resolution on cloud download, idempotent MCP upserts. **PASS**
- **VII. Flat MCP Schemas & Atomic Mutations**: 5 new tools use flat schemas (`0` `$ref`, `0` `exclusiveMinimum`), accurate annotations, transactional Firestore writes, and dynamic active-cycle resolution. **PASS**

## Project Structure

```text
specs/021-available-blocks-workflow/
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── plan.md
├── tasks.md
├── contracts/
│   └── mcp-available-blocks.md
└── checklists/
    ├── requirements.md
    └── ux-and-mcp.md

app/src/main/java/com/fractanomics/crosstraining/
├── util/AvailableBlockCodec.kt               # [NEW] Codec, pre-fill resolver, PR evaluator, decimal parser
├── util/RoutineTargetCodec.kt                # [MOD] Strip [ab_prs=...] header in extractCleanNotes
├── data/Repository.kt                        # [MOD] BlockInsert.newRepMaxes + updateSession blockId relinking
├── data/firebase/UserCloudSyncManager.kt     # [MOD] [ab_prs=...] exercise ensure, safe routineId, RM attribution
├── ui/model/SessionDraft.kt                  # [MOD] BlockDraft.newRepMaxes support
├── ui/ProgressAnalytics.kt                   # [MOD] Isolated routineBlockPerformances + resolveRepMaxAttribution
├── ui/AppViewModel.kt                        # [MOD] availableBlocks state, CRUD, undo restore, Log Block handoff
├── ui/navigation/AppNavigation.kt            # [MOD] Library -> Log route handoff wiring
├── ui/screens/LibraryScreen.kt               # [MOD] Available Blocks tab, cards, search, Undo snackbar, 3-field sheet
├── ui/screens/LogSessionScreen.kt            # [MOD] Consume pendingBlockIdsToLog, Load Block, Save CTA PR count
├── ui/components/SessionEditor.kt            # [MOD] Weight-only tiles, pre-fill banner, PR chips, MultiMovementPrPickerSheet
└── ui/screens/ProgressScreen.kt              # [MOD] "By block" label, block history PRs, exercise PR attribution

mcp-server/
├── src/schemas.ts                            # [MOD] [ab_prs=...] TS codec + 5 flat Zod schemas
├── src/store.ts                              # [MOD] WorkoutDomainService block CRUD, log_block_session, summary count
├── src/mcp.ts                                # [MOD] Register 5 tools (33 total) + updated instructions
└── test/mcp-server.test.ts                   # [MOD] Update count to 33 + test 5 new tools
```
