# 📋 Implementation Plan & Refinement Lifecycle: Hierarchical Workout Tracking (Macro-Blocks, Sub-Blocks & Exercise Structure) and Existing Session Cleanup

## 📝 Initial Draft Proposal

### Context & Real-World Athlete Problem
Following the ingestion of `workout_example.md` via the Journey Setup Assistant, workouts in functional fitness follow an essential 3-tier hierarchy:
1. **Tier 1: Macro-Block / Section**: High-level training domains (e.g. `"Strength & Power"`, `"Accessories"`).
2. **Tier 2: Sub-Block / Timing & Modality Container**: Interval formats and cluster groupings:
   - For *Strength & Power*:
     - Sub-block A: `E3MOM COMPLEX` (7 sets weight progression across the barbell movement chain: Clean + Hang Clean + Front Squat + Push to Overhead).
     - Sub-block B: `E3MOM` (Front Squats, target 4 reps, 4 recorded sets with partials/failures).
   - For *Accessories*:
     - Sub-block A: `E3MOM Trisets` (Rotating Triset: 1. Romanian Deadlift, 2. Pullups, 3. DB Twist Curl, 4 rounds).
     - Sub-block B: `E2,5MOM Trisets` (Rotating Triset: 1. Barbell Calves Raises, 2. Banded Reverse Flys, 3. SkiErg, 4 rounds).
3. **Tier 3: Exercise Structure & Sets**: Sets, reps, loads, partials (`1 rep`), and fails (`fail`).

### The User Problem
Currently, in `SessionEditor.kt` and `HistoryScreen.kt`, the app renders a flat list of 8 unlinked cards. For Trisets, the 3 rotating movements are rendered as separate cards without visual connection to their parent interval block (`E3MOM Trisets`). Furthermore, the existing workout session in the database created from `workout_example.md` is currently stored in this unlinked, flat state.

### Core Objectives
1. **Hierarchical Visualization in `SessionEditor` & `HistoryScreen`**: Group blocks under Macro-Block section banners, and cluster linked movements (Trisets, Supersets, Complexes) into visual Sub-Block containers with format badges (`E3MOM`, `E2.5MOM`) and unified round-by-round logging.
2. **Data Model Enrichment**: Add `subBlock: String` (e.g. `"E3MOM Complex"`, `"E3MOM Front Squats"`, `"E3MOM Trisets"`, `"E2,5MOM Trisets"`) to `SessionBlock` and `RoutineBlock` (or deterministic derivation from `scheme` + `format`), ensuring sub-block identity is preserved across exports, imports, and manual edits.
3. **Existing Tracked Workout Cleanup**: Provide a non-destructive migration and re-sync routine in `AppViewModel` / `Repository` to clean up the existing session and routine in Room DB created from `workout_example.md`, aligning their blocks, formats, and titles with the 3-level hierarchy.

---

## 🔍 Review Iteration 1: 3-Amigos Critical Architectural Review

- **Date / Author:** 2026-09-13 | 3-Amigos Architectural Review (Gemini Architect & QA)
- **Status:** Evaluated & Scoped

### ⚖️ Verdict Matrix

| Proposal Item | Verdict | Technical Rationale & Architectural Safeguards |
| :--- | :--- | :--- |
| **1. 3-Tier Hierarchy Model (Macro-Block -> Sub-Block -> Exercise)** | **APPROVE** | Matches athlete mental model. Macro-block: `section` (`"Strength & Power"`, `"Accessories"`). Sub-block: `subBlock` / cluster identifier (`"E3MOM Complex"`, `"E3MOM Front Squats"`, `"E3MOM Trisets"`, `"E2,5MOM Trisets"`). Exercise: Individual movement within the sub-block. |
| **2. Room Schema Migration `MIGRATION_7_8` (`subBlock` column)** | **APPROVE** | Add `subBlock TEXT NOT NULL DEFAULT ''` to `session_blocks` and `routine_blocks`. While `scheme` stores internal tokens like `"TRISET_1"`, athletes need human-readable sub-block titles (`"E3MOM Trisets"`) that survive CSV backup/restore and manual edits in `SessionEditor`. |
| **3. Sub-Block Container UI in `SessionEditor.kt`** | **APPROVE** | Group blocks by `section` first, then cluster contiguous blocks sharing the same `subBlock` (or `scheme` when `kind == SUPERSET`) into an enclosed `OutlinedCard` sub-block container featuring the timing badge (`E3MOM`), round counter, and round-by-round movement cards. |
| **4. History & Library Screen Hierarchical Parity** | **APPROVE** | In `HistoryScreen.kt` and `LibraryScreen.kt`, render triset blocks inside a unified container chip/group rather than 3 detached rows, making multi-exercise interval workouts immediately legible. |
| **5. Clean Up Existing Tracked Workout Routine** | **APPROVE** | In `MIGRATION_7_8` and an idempotent startup/repair routine in `Repository.cleanupExistingTrackedWorkout()`, inspect existing sessions and routines matching the imported workout (`"Mondays"` / `"Monday: Strength & Accessories"`): update `section` and `subBlock` values, eliminate duplicate/orphaned blocks, and backfill `subBlock` for the Strength and Accessories blocks. |

---

## 🏛️ 3-Amigos Critical Architectural Analysis

### ⚖️ Technical Architecture & Invariants

1. **Sub-Block Grouping Key Invariant**:
   - In `SessionEditor.kt`, blocks are currently iterated linearly (`blocks.forEachIndexed`).
   - By structuring the presentation logic:
     ```
     Macro-Block Section Header ("Strength & Power")
       ├── Sub-Block Container 1 ("E3MOM Complex")
       │     └── Movement: Clean + Hang Clean + Front Squat + Push to Overhead (7 sets)
       └── Sub-Block Container 2 ("E3MOM Front Squats")
             └── Movement: Front Squats (4 sets)
     Macro-Block Section Header ("Accessories")
       ├── Sub-Block Container 3 ("E3MOM Trisets" - 4 rounds)
       │     ├── Movement 1: Romanian Deadlift (4 sets)
       │     ├── Movement 2: Pullups (4 sets)
       │     └── Movement 3: DB Twist Curl (4 sets)
       └── Sub-Block Container 4 ("E2,5MOM Trisets" - 4 rounds)
             ├── Movement 1: Barbell Calves Raises (4 sets)
             ├── Movement 2: Banded Reverse Flys (4 sets)
             └── Movement 3: SkiErg (4 sets)
     ```
   - Each movement retains its individual `SessionBlock` and `BlockSet` entities in Room DB, guaranteeing zero data loss or distortion in `ProgressAnalytics` (1RM and tonnage calculations remain 100% accurate).

2. **Database Schema & Room Migration `MIGRATION_7_8`**:
   - `session_blocks`: `ALTER TABLE session_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''`
   - `routine_blocks`: `ALTER TABLE routine_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''`
   - Bump database version from `7` to `8`.
   - Update `BackupData` and `BackupCsv` (`#crosstraining-backup-v5`): encode/decode `subBlock` in `#blocks` and `#routineBlocks`.

3. **Data Cleanup Routine for Existing Tracked Workout**:
   - The workout already imported into the app from `workout_example.md` currently has 8 blocks where:
     - Block 1: `Clean + Hang Clean + Front Squat + Push to OverHead` (Format: `E3MOM`, Section: `Strengh & Power block`)
     - Block 2: `Front Squats` (Format: `E3MOM`, Section: `Strengh & Power block`)
     - Blocks 3–5: `Romanian Deadlift`, `Pullups`, `DB Twist Curl` (Format: `E3MOM`, Scheme: `TRISET_1`, Section: `Accessories block`)
     - Blocks 6–8: `Barbell Calves Raises`, `Banded Reverse Flys`, `SkiErg` (Format: `E2.5MOM`, Scheme: `TRISET_2`, Section: `Accessories block`)
   - An idempotent repair function in `Repository` will run once on database migration / initialization:
     - Sets `subBlock = "E3MOM Complex"` for block 1.
     - Sets `subBlock = "E3MOM Front Squats"` for block 2.
     - Sets `subBlock = "E3MOM Trisets"` for blocks 3–5.
     - Sets `subBlock = "E2,5MOM Trisets"` for blocks 6–8.
     - Updates normalized section names: `"Strength & Power"` and `"Accessories"`.

---

## 🎯 Final Decision Plan & User Story Specification

> **Status:** ✅ **APPROVED BY ARCHITECT & QA CONSENSUS** (Round 3 Dual Gate Passed: Both Gemini Architect & Claude QA AGREED)

### Title: feat: Hierarchical Workout Tracking (Macro-Blocks, Sub-Blocks & Exercise Structure) and Existing Session Cleanup

### User Story
**As an** athlete using CrossTraining
**I want** my workout sessions, routines, and editors to organize exercises hierarchically into Macro-Blocks (Sections) and Sub-Blocks (Interval / Triset / Complex Containers)
**So that** multi-exercise interval structures like E3MOM Complexes and Trisets are tracked and logged as cohesive units while cleaning up my existing imported workout.

---

### Architecture & Data Flow

```
[Workout Note / Import] ──► [WorkoutDocumentParser] (assigns section & subBlock)
                                      │
                                      ▼
                        [WorkoutEntityResolver]
                                      │
                                      ▼
                      [Repository.persistWorkoutJourney]
                                      │
                                      ▼
                          [Room DB (Version 8)]
                    ├── session_blocks (section, subBlock, scheme)
                    └── routine_blocks (section, subBlock, scheme)
                                      │
                                      ▼
         ┌────────────────────────────┼────────────────────────────┐
         ▼                            ▼                            ▼
  [SessionEditor]               [HistoryScreen]              [LibraryScreen]
- Macro-Block Banner          - Section Headers            - Section Headers
- Sub-Block Container Cards   - Grouped Sub-Block Chips    - Grouped Sub-Blocks
- Synchronized Round Logging  - Clear Round Breakdown      - Clear Structure
```

---

### BDD Acceptance Criteria

#### Scenario 1: Sub-Block Container Grouping in SessionEditor
```gherkin
Given an athlete opens a session containing "Accessories" with an E3MOM Triset of 3 exercises
When the session is displayed in SessionEditor
Then the 3 exercises are rendered inside a single "E3MOM Trisets" sub-block container card
And each exercise displays its respective movement name and set inputs
And editing the session preserves both section and subBlock values upon saving.
```

#### Scenario 2: Strength Macro-Block Structure (Complex + Exercise)
```gherkin
Given a workout with Macro-Block "Strength & Power" containing an E3MOM Complex and an E3MOM Front Squat block
When the session is logged or viewed
Then "Strength & Power" appears as the macro-block banner
And the Complex is housed in an "E3MOM Complex" sub-block container
And Front Squats are housed in an "E3MOM Front Squats" sub-block container.
```

#### Scenario 3: Existing Workout Data Cleanup
```gherkin
Given an existing database with a session previously imported with section names matching "%Strengh%" or "%Strength%" or "%Accessories%"
When the application upgrades to database version 8 via MIGRATION_7_8
Then the existing session and routine blocks are updated to the normalized section and subBlock:
  | Block Name                                              | Matched Section Pattern | Normalized Section | Target SubBlock     |
  | Clean + Hang Clean + Front Squat + Push to OverHead     | %Strengh% / %Strength%  | Strength & Power   | E3MOM Complex       |
  | Front Squats                                            | %Strengh% / %Strength%  | Strength & Power   | E3MOM Front Squats  |
  | Romanian Deadlift                                       | %Accessories%           | Accessories        | E3MOM Trisets       |
  | Pullups                                                 | %Accessories%           | Accessories        | E3MOM Trisets       |
  | DB Twist Curl                                           | %Accessories%           | Accessories        | E3MOM Trisets       |
  | Barbell Calves Raises                                   | %Accessories%           | Accessories        | E2,5MOM Trisets     |
  | Banded Reverse Flys                                     | %Accessories%           | Accessories        | E2,5MOM Trisets     |
  | SkiErg                                                  | %Accessories%           | Accessories        | E2,5MOM Trisets     |
And no orphaned or duplicate blocks remain.
```

#### Scenario 4: History and Library Screen Hierarchical Legibility
```gherkin
Given a completed workout session with multiple sub-blocks
When the athlete inspects the workout in HistoryScreen
Then movements belonging to the same sub-block are visually grouped together under their sub-block header
And total volume and PR tracking for each individual movement remain intact and uncorrupted.
```

#### Scenario 5: Migration Idempotency & Safe No-Op on Renamed or Unrelated Blocks
```gherkin
Given an existing database where an athlete has customized or renamed blocks, or where MIGRATION_7_8 has already executed
When MIGRATION_7_8 executes or is re-run
Then blocks that do not match the narrow composite criteria (name AND section pattern) remain untouched
And no data loss, unhandled exception, or corruption occurs.
```

#### Scenario 6: Fallback UX for Empty or Blank Sub-Block
```gherkin
Given an un-subblocked legacy session or a block with empty string subBlock ("")
When viewed in SessionEditor, HistoryScreen, or LibraryScreen
Then the block renders as an individual standalone block card without grouping containers or orphaned badges
And contiguous blocks sharing non-empty subBlock remain cleanly grouped.
```

#### Scenario 7: Triset Atomic Grouping and Broken Triset Resilience
```gherkin
Given a triset sub-block where only a subset of sibling movements match a subBlock tag or where an athlete logged asymmetric sets
When viewed in SessionEditor or HistoryScreen
Then movements sharing the non-blank subBlock cluster cleanly with format badges and maxOf(sets.size) round reconciliation
And any unmatched or blank sibling movement renders gracefully as a standalone card without index crash or UI fragmentation.
```

#### Scenario 8: Recurring Monday Session Normalization Scope
```gherkin
Given multiple historical or recurring sessions matching the Monday functional fitness program with unassigned subBlocks ("")
When reconcileLegacyWorkoutSubBlocks() executes upon application bootstrap
Then all matching historical Monday program sessions with subBlock = "" are intentionally upgraded to the standardized 3-tier subBlock tags
And sessions already possessing customized non-empty subBlocks are left untouched.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`AppDatabase.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt` | - Bump version from `7` to `8`.<br>- Add `MIGRATION_7_8` as **Pure DDL**: `ALTER TABLE session_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''`, `ALTER TABLE routine_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''`.<br>- **Dual Builder Invariant:** Register `MIGRATION_7_8` in both `AppDatabase.build()` (production) AND `AppDatabase.demo()` (demo).<br>- Hook async backfill trigger: Invoke `Repository.reconcileLegacyWorkoutSubBlocks()` inside `AppDatabase.Callback.onOpen` running asynchronously on `Dispatchers.IO`.<br>- Generate and commit Room schema artifact `app/schemas/.../8.json`. |
| **`SessionBlock.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt` | - Add `val subBlock: String = ""` to `SessionBlock`. |
| **`RoutineBlock.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/RoutineBlock.kt` | - Add `val subBlock: String = ""` to `RoutineBlock`. Note: RoutineBlock uses `targetRepsScheme: String`, not `scheme`. Grouping in LibraryScreen must key off `targetRepsScheme` and `subBlock`. |
| **`SessionDraft.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt` | - Add `val subBlock: String = ""` to `BlockDraft`. |
| **`SessionEditor.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` | - Add `subBlock` to `BlockSeed` and `BlockState`.<br>- Group consecutive blocks by `section` and `subBlock`. If `subBlock.isBlank()`, render fallback standalone card.<br>- Render clustered Sub-Block Container cards for Trisets/Supersets with shared timing badges.<br>- Reconcile asymmetric triset round count via `maxOf(sets.size)` across movements, showing empty trailing set slots as pending.<br>- Enforce contiguous block `position` invariants on save/reorder. |
| **`HistoryScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt` | - Visually group blocks by `section` and `subBlock` in expanded card view (fallback to standalone if blank).<br>- Apply `maxOf(sets.size)` round reconciliation for read-only triset display across sibling movements with asymmetric logged sets. |
| **`LibraryScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt` | - Group routine blocks by `section` and `subBlock` (or `targetRepsScheme`) in `RoutineCard`. |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - Extract and populate `subBlock` on `ParsedDocumentBlock` (e.g. `"E3MOM Complex"`, `"E3MOM Front Squats"`, `"E3MOM Trisets"`, `"E2,5MOM Trisets"`). |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Propagate `subBlock` when persisting `RoutineBlock` and `SessionBlock`.<br>- Implement async, scoped `reconcileLegacyWorkoutSubBlocks()` executed on `Dispatchers.IO` (triggered via `AppDatabase.Callback.onOpen`). Scope is intentionally designed to upgrade all matching Monday functional fitness program sessions where `subBlock = ''` and section matches legacy `%Strengh%` or normalized `%Strength%`, protecting customized rows. |
| **`Backup.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt` | - Update `BackupCsv` (`#crosstraining-backup-v5`).<br>- **Tail-Position Invariant:** Append `subBlock` strictly as trailing column in `#routineBlocks` (index 11) and `#blocks` (index 15).<br>- Use `getOrNull()?.trim() ?: ""` to ensure seamless backward compatibility with v1–v4 backups. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1 (Pure DDL MIGRATION_7_8, Dual DB Registration, Data Models & Scoped Async Backfill):**
   - Add synthetic pre-migration Room test fixture representing the imported legacy workout.
   - Add `val subBlock: String = ""` to `SessionBlock`, `RoutineBlock`, `BlockDraft`, `BlockSeed`.
   - Implement `MIGRATION_7_8` as pure DDL and register in both `AppDatabase.build()` and `AppDatabase.demo()`.
   - Wire `reconcileLegacyWorkoutSubBlocks()` to `AppDatabase.Callback.onOpen` on `Dispatchers.IO`.
   - Generate and commit Room schema `8.json` and migration test in `AppDatabaseMigrationTest.kt`.
   - Implement async, parent-bounded `reconcileLegacyWorkoutSubBlocks()` in `Repository.kt` with composite typo/section guards and recurring program scope.
   - Update `BackupCsv` to `#crosstraining-backup-v5` appending `subBlock` to the tail of `#blocks` and `#routineBlocks`.
   - Update `WorkoutDocumentParser` to populate `subBlock`.

2. **Subtask 2 (Hierarchical Sub-Block Container UI in SessionEditor.kt):**
   - Group contiguous blocks sharing non-blank `subBlock` (or triset scheme) under unified Sub-Block Container cards.
   - Implement fallback rendering for `subBlock.isBlank()`.
   - Display format badges and round counters with `maxOf(sets.size)` reconciliation for asymmetric rounds.
   - Ensure saving preserves both `section` and `subBlock` while keeping block `position` contiguous.

3. **Subtask 3 (History & Library Screen Hierarchical Parity & E2E Visual Verification):**
   - Update `HistoryScreen.kt` and `LibraryScreen.kt` to display grouped sub-blocks (using `targetRepsScheme` on routine side).
   - Implement `maxOf(sets.size)` round reconciliation in `HistoryScreen.kt` for historical triset display.
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

---

## 🧪 Claude QA Review Iteration 1 (Requirements & UX/UI Guardian)

- **Date / Author:** 2026-09-13 | Claude Sonnet 5, QA Lead & Requirements Guardian
- **Method:** Read the full plan, cross-checked its factual claims against the live codebase (`AppDatabase.kt`, `SessionBlock.kt`, `RoutineBlock.kt`, `WorkoutDocumentParser.kt`, `SessionEditor.kt`, `HistoryScreen.kt`, `Backup.kt`) rather than taking them at face value.

### 1. Anti-Drift Check (against original user requirements)

- **Hierarchy requirement — MET.** The 3-tier model (Macro-block → Sub-block → Exercise structure) is faithfully represented, and the worked examples (`E3MOM Complex`, `E3MOM Front Squat`, `E3MOM Trisets`, `E2,5MOM Trisets`) match the user's literal wording. No scope creep into unrelated features (no new screens, no analytics rework, no auth/sync changes) — the plan stays tightly scoped to tracking + cleanup as requested.
- **"Track all of it" requirement — MOSTLY MET, one soft spot.** Sub-block identity is added as a plain `String` column derived deterministically from `scheme`/`format`, not a proper join to a `SubBlock` entity. This satisfies "track it" literally, but round-count and rotation-order (e.g. "4 rounds" for a triset) are not modeled as first-class fields anywhere in the Component Impact Table — they appear to be inferred at render time from `sets.size` per movement. This is an acceptable simplification for MVP but should be called out explicitly as a known limitation, not silently assumed.
- **Data model asymmetry not addressed by the plan.** Verification shows `RoutineBlock.kt` has no `scheme` field — it uses `targetRepsScheme` instead of `scheme` (which `SessionBlock` has). The plan's Verdict Matrix and Component Impact Table treat `SessionBlock` and `RoutineBlock` as symmetric (both just "add `subBlock: String`"), but the routine-side grouping logic in `LibraryScreen.kt` will need to key off `targetRepsScheme`, not `scheme`, or the "cluster contiguous blocks sharing scheme" grouping rule described for `SessionEditor.kt` will silently fail to generalize to routines. **This is a dropped invariant that must be corrected before implementation**, not just a naming nit — it affects Subtask 2/3 and the Component Impact Table row for `RoutineBlock.kt` / `LibraryScreen.kt`.

### 2. Cleanup Requirement — Critical Finding

The user's second requirement ("Clean the current tracked workout in the database to be aligned with this structure") is the plan's **weakest-verified area**:

- `workout_example.md` **does not exist anywhere in the current repository** (confirmed via repo-wide search), and the specific 8-block workout described in Review Iteration 1 / Scenario 3 (Clean+Hang Clean+Front Squat+Push to Overhead, Front Squats, RDL, Pullups, DB Twist Curl, Barbell Calf Raises, Banded Reverse Flys, SkiErg) does not appear in any seed data, `DemoData.kt`, or existing migration. The plan's cleanup routine is therefore designed against an **assumed** database state that could not be confirmed to exist on disk, in seed data, or in a prior migration.
- Even setting that aside, the proposed cleanup strategy — matching rows by hardcoded exercise **name** strings inside `MIGRATION_7_8` — is fragile by construction: `name`/`section` are plain mutable `String` columns with no uniqueness constraint. Any athlete who renamed a block, re-imported the workout, or has a duplicate exercise name elsewhere in their history will either not get cleaned up (silent no-op — the safer failure) or, worse, get an unrelated block mutated because it happened to match a name (an unsafe failure the plan does not guard against).
- There is a real spelling risk baked into the plan itself: the "Strength & Power" section name is spelled correctly in the code's KDoc but appears as `"Strengh & Power block"` (typo) in a code comment. The plan's Scenario 3 table uses the *correct* spelling as the target, which is right, but if the cleanup migration's *match* clause is copy-pasted from the typo'd comment instead of the parser's actual runtime output, the migration will not match any row and will silently no-op. **The plan must specify that the match values are pulled from the parser's actual emitted strings (verified at implementation time), not from either doc string.**
- **Recommendation:** Before writing `MIGRATION_7_8`, add a preceding verification/logging step (e.g., a debug query or a short Room test) that confirms which rows, if any, in a real user's `session_blocks`/`routine_blocks` actually match the assumed name/section values, and make the cleanup idempotent-and-safe (match on a narrower composite key such as `name + section + format`, and log/skip rather than silently mutate on partial matches). This directly serves the "non-destructive" safety property the plan already claims (§ Core Objective 3) but does not yet fully substantiate.

### 3. UX/UI & Functional Check

- **SessionEditor grouping (Scenario 1/2):** Grouping "contiguous blocks sharing `subBlock`" is a reasonable heuristic given `SessionEditor.kt` already iterates `blocks.forEachIndexed` (confirmed at line 521) in position order, so contiguity should hold as long as `position` ordering is preserved on every persistence path (manual reorder, import, and the cleanup migration itself). The plan should explicitly state that block `position` is never renumbered independently of `subBlock` — otherwise a manually reordered session could scramble a sub-block's contiguity and break the grouping UI silently (blocks would render as separate ungrouped cards, not crash, so this failure mode is easy to miss in manual QA).
- **HistoryScreen legibility (Scenario 4):** Confirmed `HistoryScreen.kt` currently iterates flatly (`bws.sets.sortedBy{...}.forEachIndexed`), so grouping by `subBlock` header is a real, non-trivial change, not cosmetic. Good that Scenario 4 explicitly asserts volume/PR tracking integrity is preserved — this is the correct regression to guard.
- **Round-by-round triset logging:** The plan states round counts are inferred rather than stored. UX-wise, if two movements within the same triset sub-block end up with a different number of logged sets (e.g., an athlete logs a failed/incomplete round for one exercise but not the others), the round-counter UI has no explicit rule for how to reconcile round number across movements. The plan should add an explicit rule (e.g., round count = max sets across sibling movements, with missing sets shown as pending) rather than leaving this to implementation-time interpretation.
- **Data cleanup safety:** Addressed above (Section 2) — currently insufficient.

### 4. Testability Check (Gherkin coverage)

- Scenarios 1, 2, and 4 are reasonably specified and map to concrete, checkable UI states.
- **Scenario 3 (cleanup) has a testability gap**: it asserts a `Given` precondition ("an existing database with a session previously imported from `workout_example.md`") that cannot currently be constructed from anything in the repo — there is no fixture, seed, or migration test asset representing that precondition. As written, this scenario cannot be automated without first creating the fixture data it assumes already exists. The plan should add a subtask to construct (or explicitly synthesize for test purposes) the pre-migration fixture, and add a **negative** scenario: "Given a database where the imported workout was already renamed/edited by the user, When the app upgrades to v8, Then no unrelated block is mutated and the migration is a no-op" — this failure mode is currently untested and unaddressed.
- Missing edge case: partial-match / zero-match idempotency on repeated migration runs (the plan says "idempotent" but no scenario tests running the repair routine twice, or running it against a database where a prior partial cleanup already occurred).

### Summary of Required Fixes Before Implementation

1. Resolve the `RoutineBlock.scheme` vs `targetRepsScheme` naming mismatch in the Component Impact Table and grouping logic.
2. Verify (not assume) the pre-migration database state the cleanup routine targets; if no such row can be produced/confirmed, either scope Objective 3 down to "no matching legacy data found → no-op" or add the fixture-construction step explicitly.
3. Harden the cleanup migration's match strategy against renamed/duplicate blocks (composite key + skip-on-ambiguity), and pull match strings from verified parser output, not doc comments.
4. Add an explicit round-count reconciliation rule for asymmetric triset logging.
5. Add the missing negative/idempotency Gherkin scenarios for the cleanup migration.

None of these findings invalidate the architecture — the 3-tier model, schema approach, and UI grouping strategy are sound and faithful to the user's request. The gaps are concentrated entirely in the "clean up existing data" requirement, which is under-verified against real repo state and needs the safety hardening above before it's safe to implement as a schema migration (an operation that is hard to reverse once shipped to users' devices).

**VERDICT: DISAGREED**

---

## 🧪 Claude QA Review Iteration 2 (Requirements & UX/UI Guardian)

- **Date / Author:** 2026-09-13 | Claude Sonnet 5, QA Lead & Requirements Guardian
- **Method:** Re-read the full plan end-to-end. No architect/3-Amigos response addressing Iteration 1's "Summary of Required Fixes Before Implementation" (5 items) has been appended to this document since Iteration 1 — the plan is byte-for-byte unrevised on those points. Re-verified the underlying factual claims directly against the live codebase (`RoutineBlock.kt`, `SessionBlock.kt`, `WorkoutDocumentParser.kt`, `SessionEditorSectionParityTest.kt`, `WorkoutDocumentParserTest.kt`, `SessionEditor.kt`) to confirm none of the findings have been overtaken by code changes since Iteration 1.

### 🎯 Requirements Fidelity & Scope Alignment Audit

- The 3-tier hierarchy (Macro-Block → Sub-Block → Exercise) remains faithfully scoped to the operator's original request; no new drift introduced since Iteration 1 (plan content is unchanged).
- **Fix #1 (RoutineBlock naming mismatch) — still unresolved.** Verified again: `RoutineBlock.kt:34` still declares `val targetRepsScheme: String = ""` with no `scheme` field, while `SessionBlock.kt:49` has `val scheme: String = ""`. The Component Impact Table (line 181) still instructs "Add `val subBlock: String = ""` to `RoutineBlock`" with no acknowledgement that the grouping key used for `SessionEditor.kt` (`scheme`) does not exist on the routine side. This is an unaddressed, dropped invariant carried forward verbatim from Iteration 1 — `LibraryScreen.kt` grouping (Component Impact Table line 185) will need to be specified against `targetRepsScheme`, not `scheme`, or it silently fails to generalize.
- **Fix #3 (match-string provenance) — new evidence makes this worse, not better.** Grepping the live codebase shows the *actual* runtime/test section value is `"Strengh & Power block"` (typo preserved, plus a literal `" block"` suffix) — confirmed in `WorkoutDocumentParser.kt:187` (comment), `WorkoutDocumentParserTest.kt:121/127/334`, `WorkoutJourneyRepositoryTest.kt:73/77/146`, and `SessionEditorSectionParityTest.kt` (7 occurrences). The plan's Scenario 3 table and Review Iteration 1 target `"Strength & Power"` (correct spelling, no `" block"` suffix) as the *normalized* output, which is a reasonable target — but the plan still does not specify the migration's *match* clause (the `WHERE` condition selecting existing rows to update) anywhere in `MIGRATION_7_8`'s description. Since the real persisted value is `"Strengh & Power block"`, a migration author copying the *target* string into the *match* clause (an easy mistake given the table's visual layout) would silently no-op against real data. This is exactly the failure mode Iteration 1 warned about, now confirmed against actual code rather than inferred — it remains an open, unaddressed blocking risk.

### 🖥️ UX/UI & Functional Rigor Review

- `SessionEditor.kt:521` still iterates `blocks.forEachIndexed` in position order — the contiguity assumption underlying Sub-Block Container grouping (Fix from Iteration 1, item on `position` renumbering) is unchanged and still unstated in the plan text itself (it lives only in the Iteration 1 QA note, not in the Final Decision Plan / Component Impact Table where an implementing engineer would actually look).
- Round-count reconciliation for asymmetric triset logging (Fix #4) is still absent from Scenario 1/2/4 and from the Component Impact Table — no rule has been added for what the UI does when sibling movements in a sub-block have differing logged-set counts.
- No new UI mockup, wireframe, or state-by-state (loading/empty/error) description has been added for the Sub-Block Container card itself — Mandate 2's responsive-state review (loading/empty/error/active-cursor) still cannot be performed because the plan describes the container only structurally ("`OutlinedCard` sub-block container featuring the timing badge, round counter, and round-by-round movement cards"), not its states. This is a gap independent of Iteration 1 and should be added before implementation: what renders in the container while a session is being created (no sets logged yet), and what renders if `subBlock` is empty string (pre-migration / un-backfilled data — see next section)?

### 🚨 Edge Cases, Failure Modes & User Impact

- **Empty/blank `subBlock` state — unaddressed.** `MIGRATION_7_8` adds `subBlock TEXT NOT NULL DEFAULT ''`. Every existing block that the cleanup routine's match clause fails to hit (a real risk per the point above) will persist with `subBlock = ""`. No scenario or UI rule specifies what `SessionEditor`/`HistoryScreen`/`LibraryScreen` render for a block with an empty `subBlock` — does it fall back to one card per block (today's flat behavior), get grouped into a single anonymous container with all other blank-subBlock blocks in the same section (visually confusing/wrong), or something else? This is the direct, concrete failure mode that results if Fix #3 is not implemented, and the plan still has no fallback UX defined for it.
- Fix #2 (verify pre-migration DB state) and Fix #5 (negative/idempotency Gherkin scenarios) remain unaddressed — Scenario 3 is unchanged from Iteration 1 and still asserts a `Given` precondition with no corresponding fixture/test-data construction step, and there is still no scenario covering "migration runs twice" or "user already renamed a block before upgrading."
- Data-loss blast radius is unchanged and still meaningful: this ships as a Room schema migration (version bump 7→8) applied automatically on app upgrade to every existing user's device, with no rollback path described if the cleanup routine mutates the wrong rows.

### 🧪 Acceptance Criteria & Testability Assessment

- Scenarios 1, 2, and 4 remain well-specified and unchanged; still acceptable as written.
- Scenario 3 remains untestable as written for the same reason identified in Iteration 1 — its `Given` clause references a precondition (`workout_example.md`-derived DB state) with no buildable fixture, and no new fixture-construction subtask has been added to the Phased INVEST Breakdown.
- No acceptance criterion exists yet for the empty/blank-`subBlock` fallback UX identified above — this is a new testability gap on top of the five carried over from Iteration 1.

### 🏁 Verdict

None of the five required fixes from Claude QA Review Iteration 1 have been incorporated into the plan text, and direct re-verification against the live codebase this round confirms the underlying risks are real (not hypothetical) — in particular the `"Strengh & Power block"` vs `"Strength & Power"` match/target mismatch, which is now evidenced in seven+ call sites across parser, repository, and UI tests rather than a single doc comment. The architecture (3-tier model, schema shape, UI grouping strategy) remains sound, but the data-cleanup migration — the highest-blast-radius, least-reversible part of this change — is still specified at a level too loose to implement safely. Six items must be closed (the original five, plus the new empty-`subBlock` fallback UX gap) before this plan is safe to hand to a developer for a schema migration that runs unattended on every user's device.

**VERDICT: DISAGREED**

---

## 🏛️ Gemini Architect Review Iteration 2

- **Date / Author:** 2026-09-13 | Gemini 3.8 Flash (High), Principal Systems Architect
- **Method:** Deep systems architectural audit of `docs/draft-requisites/implementation-plan.md` cross-referenced with live Room database artifacts (`AppDatabase.kt`, `7.json`, `Relations.kt`, `BlockDao.kt`, `RoutineDao.kt`, `Repository.kt`, `Backup.kt`, and `AppDatabaseMigrationTest.kt`).

### 1. Database Schema Integrity & Pure DDL Invariant (`MIGRATION_7_8`)

- **DDL Simplicity & Safety ($O(1)$ Schema Evolution):**
  Adding `subBlock TEXT NOT NULL DEFAULT ''` to `session_blocks` and `routine_blocks` via `ALTER TABLE ... ADD COLUMN` is natively supported in SQLite (3.1.3+) with zero page reallocation or table copying. No existing foreign key constraints (`sessionId -> sessions.id CASCADE`, `routineId -> routines.id CASCADE`, `mainExerciseId -> exercises.id SET NULL`) are affected or invalidated.
- **Critical Architectural Anti-Pattern (DDL/DML Conflation):**
  The plan proposes executing both DDL column additions AND heuristic business-logic data cleanup (`UPDATE session_blocks SET subBlock = ... WHERE ...`) inside `MIGRATION_7_8`. This is an architectural defect for three reasons:
  1. **Irreversibility & Permadeath:** Room migrations execute once and only once. If a heuristic SQL query inside `MIGRATION_7_8` contains a typo, matches zero rows (e.g. Due to the `"Strengh & Power block"` vs `"Strength & Power"` naming mismatch), or partially fails, Room updates `room_master_table` to version 8 and will **never re-run that migration**. Users whose records missed the pattern are permanently stranded without recourse.
  2. **Startup Concurrency & SQLite Exclusive Lock Latency:** Room executes migrations synchronously inside an exclusive transaction (`BEGIN EXCLUSIVE`) on whichever thread first accesses `AppDatabase.get(context)`. Executing complex string pattern-matching queries over historical tables during app startup introduces startup latency and risks Android ANR (Application Not Responding) if triggered from the main thread during splash or dependency injection initialization.
  3. **Mandatory Architectural Decoupling:**
     - **Layer 1 (`MIGRATION_7_8`):** Must remain **100% pure DDL**:
       ```sql
       ALTER TABLE `session_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT '';
       ALTER TABLE `routine_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT '';
       ```
     - **Layer 2 (Data Backfill & Repair):** Must be decoupled into an asynchronous, idempotent data maintenance function in `Repository` (e.g. `reconcileLegacyWorkoutSubBlocks()`), invoked via `AppDatabase.Callback.onOpen` or repository initialization on `Dispatchers.IO` wrapped in `withDatabaseTransaction`.

### 2. Dual Database Builder Registration Invariant (`crosstraining.db` vs `crosstraining-demo.db`)

- In `AppDatabase.kt`, there are two independent database singletons:
  - Production database: `build(context)` -> `"crosstraining.db"`
  - Demo database: `demo(context)` -> `"crosstraining-demo.db"`
- Both builders explicitly register migrations via `.addMigrations(MIGRATION_1_2, ..., MIGRATION_6_7)`.
- The current plan only references updating `build()`. If `MIGRATION_7_8` is omitted from `demo()`, any athlete or automated test switching to Demo Mode post-upgrade will immediately crash with:
  `IllegalStateException: A migration from 7 to 8 was required but not found.`
- **Requirement:** `MIGRATION_7_8` must be registered in both `AppDatabase.build()` and `AppDatabase.demo()`.

### 3. Query Blast Radius & False-Positive Mutation Hazards

- The plan's Scenario 3 specifies updating rows matching exercise names (`"Clean + Hang Clean..."`, `"Front Squats"`, `"Romanian Deadlift"`).
- **Hazard:** Name-only matching (e.g. `UPDATE session_blocks SET subBlock = 'E3MOM Front Squats' WHERE name = 'Front Squats'`) has an unacceptable blast radius: it will rewrite EVERY session across the user's entire training history that contains Front Squats, even if performed as a 5x5 linear progression or strength wave.
- **Requirement:** The backfill query must enforce a **strict composite parent-child guard**:
  - Must join or filter on parent `sessionId` (or `routineId`) where session title matches `"Monday%"` or routine name matches `"Monday%"`.
  - Must check that `subBlock = ''` (never overwrite non-empty or user-edited sub-blocks).
  - Must check that `section` matches either the legacy parser artifact (`"Strengh & Power block"` / `"Accessories block"`) or the normalized string (`"Strength & Power"` / `"Accessories"`).

### 4. Asymmetric Entity Schema: `targetRepsScheme` vs `scheme`

- Live codebase verification confirms:
  - `SessionBlock.kt` defines `val scheme: String = ""` (used for `"TRISET_1"`, etc.).
  - `RoutineBlock.kt` has NO `scheme` property; it defines `val targetRepsScheme: String = ""`.
  - In `Repository.kt` line 961, `persistWorkoutJourney` persists triset tokens via:
    `targetRepsScheme = res.block.scheme`.
- The plan treats both entities as identical. Adding `val subBlock: String = ""` to `RoutineBlock` is necessary and correct, but UI grouping in `LibraryScreen.kt` must explicitly account for `targetRepsScheme` rather than assuming `scheme` exists on routines.

### 5. CSV Backup Serialization Invariants (`Backup.kt`)

- `Backup.kt` parses CSV lines positionally (`rec.str(13)`, `rec.str(14)`).
- When upgrading backup schema from `#crosstraining-backup-v4` to `#crosstraining-backup-v5`:
  - `subBlock` MUST be appended as the **last column** in `#routineBlocks` (index 11) and `#blocks` (index 15).
  - Because `rec.str()` uses `getOrNull()?.trim() ?: ""`, older `v4` backups imported into `v5` will gracefully default `subBlock` to `""`.
  - Appending to the tail ensures that if a `v5` backup is ever parsed by older code, existing positional fields are not shifted or corrupted.

### 6. Room Schema Export & CI Test Invariants

- `AppDatabase.kt` defines `exportSchema = true`.
- Bumping database version from 7 to 8 requires:
  1. Room KSP code generator to emit `app/schemas/com.fractanomics.crosstraining.data.AppDatabase/8.json`.
  2. Committing `8.json` to source control.
  3. Adding a dedicated migration test in `AppDatabaseMigrationTest.kt` (`migrate7To8_addsSubBlockColumnAndValidatesSchema`) using `MigrationTestHelper` to validate identity hash integrity.

### 🏁 Verdict

The 3-tier domain model and UI grouping goals are architecturally sound. However, the migration implementation strategy is unsafe: mixing DML data repairs into `MIGRATION_7_8`, omitting the demo database migration, unconstrained query blast radius, and unaddressed `targetRepsScheme` asymmetry present substantial regression risks to user data.

**VERDICT: DISAGREED**



---


## 🔍 Review Iteration 3: Author Response & Full Alignment Matrix

- **Date / Author:** 2026-09-13 | Author Agent (Synthesizer)
- **Status:** All 6 Claude QA objections and all 6 Gemini Architect objections fully conceded and incorporated into the plan specification.

### 📋 Full Concession & Resolution Matrix

| # | Reviewer | Finding / Concern | Author Resolution & Architectural Enforcement in Plan |
| :--- | :--- | :--- | :--- |
| **1** | Claude QA & Gemini | RoutineBlock has no scheme field (	argetRepsScheme: String vs SessionBlock.scheme). | **Conceded.** Grouping in LibraryScreen.kt keys strictly off subBlock (with fallback to 	argetRepsScheme), and does not reference non-existent scheme. Updated in Component Impact Table and Subtask 3. |
| **2** | Claude QA | Assumed pre-migration DB state for workout_example.md had no test fixture. | **Conceded.** Explicit subtask added to construct a synthetic Room pre-migration test fixture reproducing the imported state before applying migration/repair tests. |
| **3** | Claude QA & Gemini | Direct DML data repairs inside MIGRATION_7_8 risk permadeath, startup thread blocking, and query blast radius. | **Conceded.** MIGRATION_7_8 is decoupled into **100% Pure DDL** (ALTER TABLE ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''). Data cleanup is moved out of the migration into an asynchronous, idempotent maintenance function in Repository.kt on Dispatchers.IO (econcileLegacyWorkoutSubBlocks()). |
| **4** | Claude QA & Gemini | Cleanup matching clause fragile against "Strengh & Power block" typo and threatens unrelated sessions. | **Conceded.** Query is strictly bounded to parent session/routine FK (sessionId matching Monday import), requires subBlock = '', and uses composite matching against both legacy typo ("%Strengh%") and normalized target ("%Strength%"). Unrelated or user-renamed workouts are untouched. |
| **5** | Claude QA | Asymmetric triset logging (differing set counts across sibling movements) lacked reconciliation rule. | **Conceded.** Round count is explicitly calculated as maxOf(sets.size) across movements in the sub-block; trailing missing sets render as pending set slots. |
| **6** | Claude QA | Missing fallback UX for empty/blank subBlock (""). | **Conceded.** Scenario 6 added: blocks with blank subBlock fall back to standard standalone card rendering, preventing accidental coalescing into anonymous containers. |
| **7** | Gemini Architect | crosstraining-demo.db singleton omitted from migration registration. | **Conceded.** Registered MIGRATION_7_8 in both AppDatabase.build() (production) and AppDatabase.demo() (demo) in Component Impact Table and Subtask 1. |
| **8** | Gemini Architect | CSV positional shift risk in #crosstraining-backup-v5 (Backup.kt). | **Conceded.** subBlock is strictly appended as the trailing column in #routineBlocks (index 11) and #blocks (index 15), using getOrNull()?.trim() ?: "" for safe backward/forward compatibility. |
| **9** | Gemini Architect | Room schema export 8.json and migration test in AppDatabaseMigrationTest.kt. | **Conceded.** Added Room schema 8.json export generation and MigrationTestHelper test to Subtask 1. |

With these 9 concessions fully specified in the Final Decision Plan, Component Impact Table, and INVEST Breakdown, the plan satisfies all requirements of the Tri-Party Council.
## ðŸ§ª Claude QA Review Iteration 3 (Requirements & UX/UI Guardian)

- **Date / Author:** 2026-09-13 | Claude Sonnet 5, QA Lead & Requirements Guardian
- **Method:** Re-read the full plan end-to-end, including the Iteration 3 Concession Matrix, and re-verified that each claimed resolution actually landed in the Final Decision Plan / Component Impact Table / Scenarios text (not just asserted in the matrix). All 6 of my Iteration 1/2 findings are confirmed present in the plan body: `targetRepsScheme` fallback (Component Impact Table, `RoutineBlock.kt` & `LibraryScreen.kt` rows), synthetic pre-migration fixture (Subtask 1), composite `%Strengh%`/`%Strength%` matching bounded by parent FK + `subBlock=''` (Repository.kt row, Scenario 3 table), `maxOf(sets.size)` round reconciliation (SessionEditor.kt row, Subtask 2), Scenario 5 (idempotency/renamed-block no-op), and Scenario 6 (blank-`subBlock` standalone fallback). I did not simply trust the matrix â€” I traced each row back to its concrete location in the plan text.

### ðŸŽ¯ Requirements Fidelity & Scope Alignment Audit

- The 3-tier hierarchy and cleanup requirement remain faithfully scoped; no drift introduced by the Round 3 revisions.
- **New finding â€” recurring-session blast radius.** The Repository.kt row now bounds `reconcileLegacyWorkoutSubBlocks()` to "`sessionId` matching Monday workouts" plus `subBlock=''` plus composite section matching. This closes the *cross-session* false-positive risk Gemini raised (a totally unrelated "Front Squats" block elsewhere), but it does not address a *recurring*-session case: if the athlete logs "Monday: Strength & Accessories" every week (a realistic pattern for a recurring functional-fitness program), every week's session will independently satisfy the same title + section + `subBlock=''` match, not just the one session originally created from `workout_example.md`. The user's original ask was to clean up "the existing tracked workout" (singular), but the current guard will silently rewrite `subBlock`/`section` on **every** matching Monday session past and future, not just the one instance. This may be desirable (arguably correct behavior) but the plan never states this is intentional â€” it should be called out explicitly as in-scope-by-design, or the match narrowed to a specific `sessionId`/creation-timestamp if it's meant to be a one-time repair.
- **New finding â€” invocation trigger unspecified.** The Component Impact Table says `reconcileLegacyWorkoutSubBlocks()` runs "async... on `Dispatchers.IO`" but never states *where it is invoked from* (app startup callback, first `Repository` call, `AppDatabase.Callback.onOpen`, etc.). Gemini's Iteration 2 review explicitly suggested `AppDatabase.Callback.onOpen` as one option â€” the plan needs to pick one and specify it, otherwise an implementer could wire it to a rarely-hit code path (e.g., only on cold app launch) and the backfill silently never runs for users who don't restart the app.

### ðŸ–¥ï¸ UX/UI & Functional Rigor Review

- The `maxOf(sets.size)` round-reconciliation rule is now specified for `SessionEditor.kt` (Subtask 2), but the `HistoryScreen.kt` row (line 200) still only says "group by `section`/`subBlock`... fallback to standalone if blank" â€” it does not restate the round-reconciliation rule for read-only historical display of a triset where sibling movements have asymmetric logged-set counts (this is a real historical-data case, not just a live-logging one, since legacy/partially-migrated sessions are exactly where mismatched counts are likely). This should be copied into the `HistoryScreen.kt` row or explicitly marked "not applicable" with a reason.
- **New finding â€” partial-match broken triset grouping.** Because the backfill/cleanup match runs per-row (per-block), it's possible for 2 of 3 sibling movements in a triset to match the composite criteria while the 3rd doesn't (e.g., a title variant on one exercise). Scenario 6's fallback renders the unmatched block as a standalone card â€” which is correct in isolation, but the net visible result is a broken/split triset (2 grouped + 1 orphaned standalone) rather than a clean "fully grouped" or "fully fallback" state. Neither Scenario 5 nor 6 covers this partial-match case. This is a plausible real-world outcome of the composite matching strategy just introduced, not a theoretical edge case.

### ðŸš¨ Edge Cases, Failure Modes & User Impact

- Recurring-session blast radius (above) is the most consequential open item â€” it changes the effective scope of a migration that runs unattended on every user's device.
- Partial-match broken triset grouping (above) is a lower-severity but concrete UX regression risk for exactly the workout type (Trisets) the plan is designed to make legible.
- The six original blast-radius/safety findings (typo matching, DML-in-migration, blank fallback, etc.) are now genuinely closed at the specification level.

### ðŸ§ª Acceptance Criteria & Testability Assessment

- Scenario 3 still doesn't assert a negative case for the recurring-session scenario ("Given two Monday sessions both matching the composite pattern, When migration runs, Then both/only-the-intended-one are updated" â€” whichever behavior is intended needs an explicit scenario).
- No scenario covers the partial-match/broken-triset case identified above.
- No scenario or table row covers round-reconciliation display in `HistoryScreen.kt` specifically.

### ðŸ Verdict

All 6 of my Iteration 1/2 findings and all 3 of Gemini's are confirmed resolved in the plan text â€” solid, verifiable progress. However, direct re-verification against the revised text surfaced 3 new, concrete gaps introduced or exposed by the fixes themselves: (1) unscoped recurring-session blast radius from the new composite match, (2) unspecified invocation trigger for the async backfill, and (3) partial-match broken-triset display with no fallback rule or test coverage. These are lower severity than the original 6 (none risk permadeath or cross-workout corruption), but the first one directly affects how much data gets mutated on a migration that is still irreversible once shipped, so it needs an explicit decision (intentional vs. needs narrowing) before implementation, not silent inference.

**VERDICT: DISAGREED**

I have full context now â€” this is Round 3 final re-verification of the 3 items I flagged as open in my prior iteration. All three are confirmed present in the plan text (Scenario 8, Component Impact Table, Scenario 7), verified directly against the document rather than trusting the concession matrix.

## ðŸ§ª Claude QA Review Iteration 3 (Final Re-Verification)

### ðŸŽ¯ Final Requirements & Scope Verification

- **Recurring-session blast radius â€” resolved.** Scenario 8 (line 197-203) now explicitly states the intent: `reconcileLegacyWorkoutSubBlocks()` upgrades *all* matching historical/recurring Monday sessions where `subBlock = ""`, and sessions with customized non-empty `subBlock` are left untouched. This is exactly the disclosure I asked for â€” the wide blast radius is no longer an unstated side effect, it's a declared design decision the user/stakeholders can accept or reject with eyes open. Combined with the Repository.kt row's FK/section/`subBlock=''` bounding, this closes the finding.
- **Invocation trigger â€” resolved.** The `AppDatabase.kt` row now states plainly: "Hook async backfill trigger: Invoke `Repository.reconcileLegacyWorkoutSubBlocks()` inside `AppDatabase.Callback.onOpen` running asynchronously on `Dispatchers.IO`." This is a concrete, unambiguous wiring point â€” no longer left to implementer inference, and it fires on every DB open rather than a rare cold-launch path, so the backfill will actually run for existing users.

### ðŸ–¥ï¸ UX/UI & Functional Verification

- **Broken triset & partial-match display — resolved.** Scenario 7 now explicitly covers the case where "only a subset of sibling movements match a subBlock tag" — matched movements cluster with `maxOf(sets.size)` round reconciliation, and unmatched/blank siblings render standalone "without index crash or UI fragmentation." The `HistoryScreen.kt` row in the Component Impact Table was also extended to carry the same `maxOf(sets.size)` reconciliation rule for read-only historical display, closing the gap where only `SessionEditor.kt` had it previously. This directly answers both my UX finding (missing HistoryScreen round-reconciliation) and my edge-case finding (partial-match broken triset with no fallback rule).

### 🏁 Verdict

All 3 outstanding items from Iteration 3 are now formally specified in the plan body (Scenarios 7/8, Component Impact Table), not merely asserted in a concession matrix — I traced each to its concrete location as in prior rounds. No new gaps surfaced on this pass. The plan has converged: irreversible-migration risk is properly disclosed and scoped, the async trigger is unambiguous, and triset display degrades gracefully under partial matches with reconciliation now specified for both live and historical views.

VERDICT: AGREED

---


## 🏛️ Gemini Architect Review Iteration 3 (Final Re-Verification)

- **Date / Author:** 2026-09-13 | Gemini 3.8 Flash (High), Principal Systems Architect
- **Status:** Evaluated & Verified

### ⚖️ Systems Architecture & Safety Verification

1. **Pure DDL Invariant ($O(1)$ Schema Evolution):**
   - `MIGRATION_7_8` is strictly confined to atomic DDL (`ALTER TABLE session_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''` and `ALTER TABLE routine_blocks ADD COLUMN subBlock TEXT NOT NULL DEFAULT ''`).
   - Zero heuristic DML queries exist within the migration transaction, eliminating all risks of migration permadeath, startup SQLite exclusive lock contention, or ANR during app cold-start.

2. **Dual Database Builder Registration:**
   - Explicitly mandated and verified in both `AppDatabase.build()` (production `"crosstraining.db"`) and `AppDatabase.demo()` (demo `"crosstraining-demo.db"`).
   - Guarantees complete runtime parity and prevents `IllegalStateException` crashes in Demo mode.

3. **Asynchronous, Idempotent Data Maintenance Decoupling:**
   - Legacy data reconciliation is fully decoupled into `Repository.reconcileLegacyWorkoutSubBlocks()`, triggered via `AppDatabase.Callback.onOpen` and executed asynchronously on `Dispatchers.IO` within a scoped database transaction.
   - Blast radius is strictly constrained by parent FK relations (`sessionId`/`routineId`), empty-field guard (`subBlock = ''`), and composite regex/wildcard matching across both legacy typo (`%Strengh%`) and normalized (`%Strength%`) section names.
   - User customizations and pre-existing non-blank sub-blocks are protected unconditionally.
   - Recurring-session scope is explicitly acknowledged and handled idempotently without corrupting training analytics or 1RM records.

4. **CSV Backup Serialization Safety & Backward Compatibility:**
   - `BackupCsv` (`#crosstraining-backup-v5`) appends `subBlock` strictly to the trailing position of `#routineBlocks` (index 11) and `#blocks` (index 15).
   - The use of `getOrNull()?.trim() ?: ""` guarantees non-breaking forward and backward compatibility with `v1`–`v4` backup archives without positional column offset regressions.

5. **Room Schema Verification & CI Guardrails:**
   - Concrete tasks mandate Room KSP export of `app/schemas/com.fractanomics.crosstraining.data.AppDatabase/8.json`.
   - Migration integrity and SQLite identity hash validation are enforced via `MigrationTestHelper` in `AppDatabaseMigrationTest.kt` (`migrate7To8_addsSubBlockColumnAndValidatesSchema`).

6. **Asymmetric Entity & Broken-Cluster Fallback Resilience:**
   - UI grouping contracts explicitly distinguish between `SessionBlock.scheme` and `RoutineBlock.targetRepsScheme`.
   - Partial triset matches and unassigned blocks gracefully degrade to standalone card rendering without throwing index out-of-bounds or fragmenting the UI layout.

### 🏁 Verdict

All architectural safeguards, concurrency boundaries, schema evolution invariants, and failure modes have been rigorously addressed and incorporated into the specification. With Claude QA having also verified requirements fidelity and issued `VERDICT: AGREED`, full dual consensus of the Review Council is achieved.

VERDICT: AGREED
## ðŸ›ï¸ Gemini Architect Review Iteration 3 (Final Re-Verification)

- **Date / Author:** 2026-09-13 | Gemini 3.8 Flash (High), Principal Systems Architect
- **Target Plan:** [docs/draft-requisites/implementation-plan.md](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/docs/draft-requisites/implementation-plan.md)
- **Status:** Evaluated & Verified

---

### âš–ï¸ Systems Architecture & Safety Verification

1. **Pure DDL Schema Evolution Invariant ($O(1)$ Migration Safety):**
   - [`MIGRATION_7_8`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt) is strictly isolated to pure SQLite DDL:
     ```sql
     ALTER TABLE `session_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT '';
     ALTER TABLE `routine_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT '';
     ```
   - All heuristic DML mutation logic has been completely excised from the migration transaction, eliminating the hazard of migration permadeath, startup SQLite exclusive lock contention, or ANR during dependency injection / app cold-start.

2. **Dual Database Builder Registration Invariant:**
   - Registration of `MIGRATION_7_8` is formally specified and verified across both singletons in [`AppDatabase.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt): `AppDatabase.build()` (production `"crosstraining.db"`) and `AppDatabase.demo()` (demo `"crosstraining-demo.db"`).
   - Guarantees full runtime parity across builds and prevents `IllegalStateException: A migration from 7 to 8 was required but not found` crashes in Demo mode or automated instrumentation tests.

3. **Asynchronous, Idempotent Data Maintenance Decoupling:**
   - Data cleanup is moved out of the migration transaction and encapsulated in [`reconcileLegacyWorkoutSubBlocks()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt), executed on `Dispatchers.IO` via [`AppDatabase.Callback.onOpen`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt).
   - False-positive mutation blast radius is securely bounded by parent foreign keys (`sessionId` / `routineId` matching Monday programs), an empty-field guard (`subBlock == ''`), and composite matching against both legacy typo patterns (`%Strengh%`) and normalized targets (`%Strength%`).
   - User customizations and pre-existing non-blank sub-blocks are protected unconditionally, while recurring Monday sessions are idempotently normalized without corrupting volume or PR tracking history.

4. **CSV Backup Serialization Safety & Backward Compatibility:**
   - [`Backup.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt) (`#crosstraining-backup-v5`) strictly appends `subBlock` as the trailing positional column in `#routineBlocks` (index 11) and `#blocks` (index 15).
   - Fallback parsing via `getOrNull()?.trim() ?: ""` guarantees seamless forward and backward compatibility with `v1`â€“`v4` backup archives without column-offset corruption.

5. **Room Schema Verification & CI Testing Invariants:**
   - Explicit deliverables mandate the Room KSP generation and source commit of `app/schemas/com.fractanomics.crosstraining.data.AppDatabase/8.json`.
   - Migration identity hash verification is enforced via a dedicated test in [`AppDatabaseMigrationTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/data/AppDatabaseMigrationTest.kt) (`migrate7To8_addsSubBlockColumnAndValidatesSchema`) leveraging `MigrationTestHelper`.

6. **Entity Schema Asymmetry & Broken-Cluster UI Resilience:**
   - Distinguishes between [`SessionBlock.scheme`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt) and [`RoutineBlock.targetRepsScheme`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/RoutineBlock.kt) for UI clustering in routines and sessions.
   - Scenario 7 and the Component Impact Table formally define fallback rendering for unassigned/blank blocks and asymmetric triset round reconciliation using `maxOf(sets.size)` across sibling movements for both active editing and historical display.

---

### ðŸ Verdict

All 6 architectural and concurrency invariants have been fully conceded and specified in the living plan. With Claude QA having also verified requirements fidelity, UI fallback rigor, and issued `VERDICT: AGREED`, full dual consensus of the Review Council is achieved.

VERDICT: AGREED

