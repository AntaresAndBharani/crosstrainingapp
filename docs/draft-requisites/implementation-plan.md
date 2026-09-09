# 📋 Implementation Plan & Refinement Lifecycle: Workout Journey Setup Assistant & Macro-Block Ingestion

## 📝 Initial Draft Proposal

### Phase 1: Functional & CX Review (Product Owner)

#### Context & Real-World User Problem
Athletes in functional fitness and cross-training often maintain their training programming and daily logs in text notes, messaging apps, or Markdown files (e.g., `workout_example.md`). When adopting CrossTraining or planning a new cycle, an athlete currently faces intense friction:
1. **No Entry Point for Bulk Training Ingestion**: Users must manually configure a Cycle, then navigate to Library to create every individual missing exercise, then create a Routine template, and finally log each set manually inside `SessionEditor`.
2. **Loss of High-Level Daily Structure (Macro-Blocks)**: Workouts in cross-training are naturally broken down into distinct sections/macro-blocks (e.g. *"Strength & Power block"*, *"Accessories block"*, *"Metcon block"*). Currently, the app only supports a flat list of individual exercises/blocks without section hierarchy.
3. **Complexes, Trisets & Mixed Metric Logging**: Real workouts feature barbell complexes (`Clean + Hang Clean + Front Squat + Push to Overhead`), interval trisets (`E3MOM Trisets`, `E2,5MOM Trisets`), European decimal commas (`52,5`, `12,5`), partial/failed attempts (`60(1 rep)`, `60(fail)`), skipped sets (`not_done`), and unweighted/machine movements (`10 Cal SkiErg`, `Banded Reverse Flys x15`).
4. **The "Journey Setup Assistant" Concept**: The user should be able to paste raw workout text directly into the app (from the Log tab, Library tab, or onboarding) and have an interactive assistant inspect the text, identify missing dependencies (Cycle, Routine template, Exercises), and guide the user through a rapid 4-step confirmation before persisting everything to Room DB.

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│              Workout Journey Setup Assistant                │
│                                                             │
│  Step 1: Cycle Association & Verification                   │
│          • Active cycle detected? (If not, create one)      │
│                                                             │
│  Step 2: Routine Template vs Session Action                 │
│          • Detected "Mondays --- repeateble"                │
│          • [x] Save as reusable Library Routine             │
│          • [x] Log as today's active workout session        │
│                                                             │
│  Step 3: Missing Exercises Auto-Cataloging                  │
│          • Existing in DB: Front Squat, Clean, Pullups      │
│          • Missing: Hang Clean, Romanian Deadlift,          │
│            DB Twist Curl, Calves Raises, SkiErg             │
│          • Inferred Category & MetricType (1-tap create)    │
│                                                             │
│  Step 4: Macro-Blocks & Set Breakdown Preview               │
│          • Section 1: Strength & Power (Complex + Squats)   │
│          • Section 2: Accessories (Triset 1 + Triset 2)     │
└─────────────────────────────────────────────────────────────┘
           │
           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│  • Cycle (linked/created)                                   │
│  • Routine + RoutineBlocks (with section grouping)          │
│  • Session + SessionBlocks + BlockSets (with section tag)   │
│  • Exercises Catalog updated                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔍 Review Iteration 1: 3-Amigos Critical Architectural Review

- **Date / Author:** 2026-09-08 | 3-Amigos Architectural Review (Gemini Architect)
- **Status:** Evaluated & Responded

### ⚖️ Verdict Matrix

| Proposal Item | Verdict | Technical Rationale & Architectural Safeguards |
| :--- | :--- | :--- |
| **1. Multi-Block Document Parser (`WorkoutDocumentParser`)** | **APPROVE** | Must be a pure Kotlin, deterministic, zero-latency tokenizer and parser running locally on `Dispatchers.Default` (< 15ms). Must NOT depend on cloud AI or unavailable on-device models for core parsing. |
| **2. Macro-Block / Section Support (`section` column)** | **APPROVE** | Add `val section: String = ""` to `RoutineBlock` and `SessionBlock`. This preserves the existing flat relational model while enabling clean grouping headers (e.g. "Strength & Power", "Accessories") in both UI and analytics via non-destructive `MIGRATION_6_7`. |
| **3. Missing Entity Auto-Resolver (`WorkoutEntityResolver`)** | **APPROVE** | Match movement names using existing `ExerciseEntityGrounder` and `FitnessSpeechLexicon`. Missing movements are classified via a heuristic dictionary (e.g. "Deadlift" -> `BARBELL`/`WEIGHT`, "Curl" -> `ACCESSORY`/`WEIGHT`, "SkiErg" -> `MACHINE`/`CALORIES`) and presented for 1-tap user confirmation. |
| **4. Triset & Superset Set Representation** | **MODIFY** | If a triset (`Romanian Deadlift`, `Pullups`, `DB Twist Curl`) is stored as a single `SessionBlock` with one `mainExerciseId`, rep-max calculation and historical volume for the remaining exercises are permanently lost. **Solution:** Expand trisets into linked sub-blocks sharing the same `format` (`E3MOM`), `section` (`Accessories`), and a shared `groupIndex`, so each exercise maintains individual `BlockSet` history and progression. |
| **5. Comma Decimal & Set Token Support** | **APPROVE** | Replace decimal commas (`52,5` -> `52.5`, `E2,5MOM` -> 150s) during tokenization. Parse annotations: `(1 rep)` sets `reps = 1`; `(fail)` sets `isFailed = true`; `not_done` skips set; `(weight track not needed)` sets `weight = null`. |
| **6. Interactive Journey Setup Assistant UI** | **APPROVE** | Implement as a reusable Material 3 modal bottom sheet (`WorkoutJourneyAssistantSheet.kt`) accessible from both `LogSessionScreen` (via "Paste Workout Notes" action) and `LibraryScreen` (via "Import Routine"). |

---

## 🏛️ Gemini Architect Review Iteration 1

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Complex Attribution Gap & Schema Inconsistency (`SessionBlock` vs `RoutineBlock`)**:
   - In Scenario 3, the proposal states that a Barbell Complex (`Clean + Hang Clean + Front Squat + Push to OverHead`) is parsed into `kind = BlockKind.COMPLEX` and records its component movements.
   - However, ground truth inspection of [`SessionBlock.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt) reveals that `SessionBlock` only possesses a singular `mainExerciseId: Long?`. Unlike [`RoutineBlock.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/RoutineBlock.kt#L35) (which has `exerciseIdsCsv: String = ""`), `SessionBlock` has **zero schema support** for storing multiple component exercise IDs.
   - If `mainExerciseId` is assigned to `Clean`, then the sets logged for the complex (e.g., `52.5, 55, ... kg`) are erroneously attributed directly to Clean in [`ProgressAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/ProgressAnalytics.kt#L47), corrupting Clean 1RM and historical tonnage. If `mainExerciseId` is set to `null`, the complex is completely excluded from progress analytics.
   - The plan creates an internal contradiction: on one hand, Anti-Pattern 2 calls cataloging `"Clean + Hang Clean"` as a single composite exercise an anti-pattern; on the other hand, the proposal provides no schema or entity mechanism in `SessionBlock` to persist composite movements.

2. **Downstream Metadata Eviction in Session Editing (`SessionDraft` / `SessionEditor`)**:
   - The proposal introduces `val section: String = ""` to `RoutineBlock` and `SessionBlock`, but completely overlooks the active UI drafting layer in [`SessionDraft.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt#L20-L34) and [`SessionEditor.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt#L117-L165).
   - Neither [`BlockDraft`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt#L20-L34) nor [`BlockSeed`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt#L117-L129) includes a `section` field. Furthermore, [`AppViewModel.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt#L407-L419) instantiates `SessionBlock` without `section`.
   - **Failure Mode:** If an athlete imports a multi-section workout via `WorkoutJourneyAssistantSheet` and subsequently taps the session in `SessionEditor` to edit a weight or set, saving will serialize through `BlockDraft` and silently wipe all `section` values back to `""`. The entire macro-block hierarchy is lost on the first manual edit.

3. **Backup Specification Ambiguity (`BackupData` vs `BackupCsv`)**:
   - The Component Impact Table states: *"Add `section` to `RoutineBlock` and `SessionBlock` CSV serialization (`#crosstraining-backup-v4`)"*.
   - Ground truth inspection of [`Backup.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt#L17-L26) shows that [`RoutineBlock`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/RoutineBlock.kt) **does not exist at all** in `BackupData` or `BackupCsv`. Only routines (parent metadata) and blocks (which are `SessionBlock`) are serialized.
   - The plan does not specify whether `routine_blocks` is being introduced as a new top-level table in `BackupData` and `BackupCsv`, or if the author mistakenly assumed `RoutineBlock` was already backed up. If `routine_blocks` are not serialized, routine block sections can never survive an export/import lifecycle.

4. **Triset Relational Dissociation**:
   - Verdict Item 4 proposes expanding trisets into linked sub-blocks sharing `format = "E3MOM"`, `section = "Accessories"`, and a shared `groupIndex`.
   - However, [`groupIndex`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/BlockSet.kt#L31) exists exclusively on [`BlockSet`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/BlockSet.kt), scoped to an individual `blockId`. [`SessionBlock`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt) has no `groupIndex` or grouping identifier.
   - Unpacking a triset into 3 distinct `SessionBlock` entities leaves them completely decoupled. If a section contains two distinct trisets (e.g. Triset 1 and Triset 2 in the example), neither the UI nor the database can determine which sub-blocks belong to which triset cycle.

---

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

1. **Global Comma Replacement Collateral Damage**:
   - Normalizing European decimal commas via global replacement (`replace(',', '.')`) will corrupt sentence notes, markdown lists, and delimiter structures (e.g. `"Deadlift, Squat"`, `"Clean, 5 reps"`). Comma replacement must be strictly confined to numeric boundaries via regex `(?<=\d),(?=\d)`.
2. **Missing Set Token Syntax & Rep Fallback Rules**:
   - Real-world athlete notes contain shorthand rep notations: `0(5 reps) 0(4) 0(4) 0(4)` (where `(4)` lacks the `"reps"` suffix).
   - In blocks like `Romanian Deadlift: 70 75 80 80`, loads are enumerated without target reps. The parser specification fails to define default rep count fallback (e.g. default to 1 rep vs parsing rep count from section context).
3. **Set Count Resolution for Calorie / Machine Accessories**:
   - In `workout_example.md`, the `E2,5MOM Trisets` section lists:
     - `10 Cal SkiErg (tracking not needed)`
     - `Banded Reverse Flys x15 (weight track not needed)`
     - `Calves Raises x15 (weight track not needed)`
   - No set weights or counts are listed per movement. The plan specifies no heuristic for determining how many `BlockSet` instances to generate.
4. **Destructive Gestural Dismissal in Assistant UI**:
   - Material 3 `ModalBottomSheet` dismisses on scrim tap or down-drag. If an athlete spends minutes pasting and reviewing category/metric assignments in Step 3, an inadvertent swipe will wipe all ephemeral state.
5. **Concurrent Exercise Insertion & Race Condition**:
   - [`ExerciseDao.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/dao/ExerciseDao.kt#L14) uses `OnConflictStrategy.IGNORE`, but the `exercises` table lacks a unique constraint on `name`. Ingesting a workout containing multiple references to the same new exercise without strict in-transaction deduplication will create duplicate exercise records.

---

### 🏁 Verdict (Gemini Architect Iteration 1)
VERDICT: DISAGREED

---

## 🔍 Review Iteration 2: Author Response to Gemini Architect & Full Concession Matrix

- **Date / Author:** 2026-09-08 | Authoring Agent
- **Status:** Full Concession & Definitive Alignment

The architectural critique in Gemini Architect Review Iteration 1 is precise, rigorous, and grounded in the live codebase. We accept all 4 primary architectural blockers and all 5 edge-case vulnerabilities without reservation. Below is the concrete technical resolution and concession matrix:

### ⚖️ Concession & Architectural Resolution Matrix

| Issue Flagged by Gemini Architect | Concrete Technical Resolution in Revised Plan |
| :--- | :--- |
| **B1: Complex Attribution Gap & Schema Inconsistency** | **Dual Resolution:**<br>1. In `MIGRATION_6_7`, add `exerciseIdsCsv TEXT NOT NULL DEFAULT ''` to `session_blocks` matching `routine_blocks`.<br>2. When a Barbell Complex is cataloged, it is registered as a dedicated composite `Exercise` (e.g. `Clean + Hang Clean + Front Squat + Push to Overhead`, category = `BARBELL`, metricType = `WEIGHT`) and bound as `mainExerciseId`. Its component exercise IDs are recorded in `exerciseIdsCsv`. This isolates complex 1RM and progress curves without corrupting component lifts (Clean or Front Squat). |
| **B2: Downstream Metadata Eviction in SessionDraft** | **Full UI/Draft Stack Propagation:**<br>1. Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `BlockDraft` (`SessionDraft.kt`).<br>2. Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `BlockSeed` and `BlockState` in `SessionEditor.kt`.<br>3. Update `SessionEditorBody` to display section grouping headers, and update `AppViewModel.saveSession` to copy `draft.section` and `draft.exerciseIdsCsv` directly to `SessionBlock`. Editing a session in `SessionEditor` will never wipe section hierarchy. |
| **B3: Backup Schema Scope & `routine_blocks` Serialization** | **Formalized `#routineBlocks` CSV & Data Model Integration:**<br>1. Add `val routineBlocks: List<RoutineBlock> = emptyList()` to `BackupData`.<br>2. Update `BackupCsv` (`#crosstraining-backup-v4`):<br>   - `#routineBlocks` section serialized with fields: `routineId, position, name, kind, format, setsCount, targetRepsScheme, exerciseIdsCsv, notes, section`.<br>   - `#blocks` section serialized with `section` and `exerciseIdsCsv`.<br>3. In `BackupCsv.decode()`, gracefully fallback to `emptyList()` when importing legacy `#crosstraining-backup-v1..v3` files. |
| **B4: Triset Relational Dissociation** | **Deterministic Grouping via `scheme` & Sub-Block Linking:**<br>In a triset/superset block (e.g. `E3MOM Trisets` or `E2,5MOM Trisets`), each movement is expanded into its own `SessionBlock` with `kind = BlockKind.SUPERSET`, sharing the same `format` (e.g. `"E3MOM"`) and `section` (e.g. `"Accessories"`), and tagged with a shared cluster tag: `scheme = "TRISET_1"`, `scheme = "TRISET_2"`. The UI renders linked sub-blocks under a unified triset container. |
| **E1: Decimal Comma Replacement Regex** | Enforce strict regex lookaround: `text.replace(Regex("""(?<=\d),(?=\d)"""), ".")`. Markdown lists, notes, and commas in exercise names are completely untouched. |
| **E2: Shorthand `weight(reps)` Grammar** | The set parser supports: `(\d+(?:\.\d+)?)\s*(?:\((?:(\d+)\s*(?:reps?|rep)?|fail)\))?`. Matches `0(5 reps)`, `0(4)`, `60(1 rep)`, `60(fail)` effortlessly. Unspecified reps inherit target reps from block header (e.g. `x12 reps` -> 12 reps), falling back to 1 rep. |
| **E3: Accessory Set Count Resolution** | Unnumbered accessories in an interval block inherit the round count from the anchor exercise in the same triset (e.g., in `E2,5MOM Trisets`, `Barbell Calves Raises` has 4 sets $\implies$ `Banded Reverse Flys` and `SkiErg` default to 4 sets matching the 4 rounds). In Step 4 preview, users can adjust individual set counts via stepper controls. |
| **E4: Sheet Dismissal Guard** | Configure `confirmValueChange = { target -> if (currentStep > 1 && target == SheetValue.Hidden) { showExitConfirm = true; false } else true }` on `SheetState` to prevent accidental swipe dismissals. |
| **E5: Exercise Name In-Transaction Deduplication** | In `Repository.persistWorkoutJourney`, pre-filter newly proposed exercises against both existing SQLite names and pending set names using case-insensitive set matching (`uniqueNames = proposed.distinctBy { it.name.trim().lowercase() }`), ensuring zero duplicate entries. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want to paste my raw workout notes or markdown into a guided Journey Setup Assistant that detects cycles, routine templates, missing exercises, and macro-block sections with complexes and trisets
So that I can immediately catalog new exercises, establish training templates, and log multi-block workouts with full 1RM and volume fidelity without manual screen-by-screen setup.
```

---

### Architecture & Data Flow

```
+───────────────────────────────────────────────────────────────────────────────────────────+
|                         WorkoutJourneyAssistantSheet (Compose UI)                         |
|  - SheetState dismissal guard active during Steps 2-4                                     |
|  [Step 1: Paste Text] ──► [Step 2: Cycle & Routine] ──► [Step 3: Exercises] ──► [Preview] |
+─────────────────────────────────────────────┬─────────────────────────────────────────────+
                                              │
                                              ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                               WorkoutDocumentParser                                       │
│  - Normalized with digit-bounded lookaround: (?<=\d),(?=\d)                               │
│  - Tokenizes lines: Routine Title, Section Headers ("... block"), Formats, Sets, Reps     │
│  - Extracts Complexes ("Clean + Hang Clean..."), Trisets ("E3MOM Trisets"), & Modalities  │
│  - Parses annotations: "(1 rep)", "(4)", "(fail)", "not_done", "(weight track not needed)"│
│  - Resolves accessory round counts by inheriting from anchor movement in interval triset │
└─────────────────────────────────────────────┬─────────────────────────────────────────────┘
                                              │
                                              ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                               WorkoutEntityResolver                                       │
│  - In-transaction case-insensitive deduplication against local SQLite Exercise catalog     │
│  - Segregates into: Existing Exercises vs Missing Exercises                               │
│  - Infers Category (Barbell, Dumbbell/Accessory, Machine, Gymnastics) & MetricType         │
│  - For Barbell Complexes: Registers distinct composite Exercise for 1RM curve isolation   │
│  - Binds to Active Cycle (or creates default "General Training" cycle)                    │
└─────────────────────────────────────────────┬─────────────────────────────────────────────┘
                                              │
                                              ▼ (Atomic withDatabaseTransaction)
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                              Repository / AppDatabase                                     │
│  - Inserts deduplicated approved Exercise entities into exerciseDao                       │
│  - If "Save Routine Template" checked: saves Routine + RoutineBlocks (section, csv)       │
│  - If "Log Workout Session" checked: saves Session + SessionBlocks (section, csv, scheme) │
│    and BlockSets (reps, weight, metricValue, isFailed, notes)                             │
│  - Room Migration: MIGRATION_6_7 adds `section` to routine_blocks & session_blocks,       │
│    and adds `exerciseIdsCsv` to session_blocks                                            │
│  - BackupCsv v4: includes dedicated #routineBlocks section and #blocks section updates    │
│  - SessionDraft, BlockDraft, BlockSeed, and SessionEditor updated with full section parity│
+───────────────────────────────────────────────────────────────────────────────────────────+
```

---

### BDD Acceptance Criteria

#### Scenario 1: Paste Workout Text & Auto-Detect Cycle and Routine
```gherkin
Given an athlete opens the "Paste Workout Notes" assistant
When they paste raw workout text containing "Mondays --- repeateble" and section headers
Then the assistant detects "Mondays" as a recurring weekly routine template
And selects the current active training cycle (or creates "General Training" if none exists)
And enables a toggle to save "Monday: Strength & Accessories" as a reusable Library Routine.
```

#### Scenario 2: Grounding and One-Tap Creation of Missing Exercises with Deduplication
```gherkin
Given the pasted text contains "Clean", "Front Squat", "Romanian Deadlift", "DB Twist Curl", and "SkiErg"
And "Clean" and "Front Squat" already exist in the database, but the others do not
When the assistant advances to the "Review Exercises" step
Then "Clean" and "Front Squat" are marked as "In Library"
And "Romanian Deadlift", "DB Twist Curl", and "SkiErg" are marked as "New to Catalog" with inferred categories:
  | Romanian Deadlift | Category: BARBELL   | Metric: WEIGHT   |
  | DB Twist Curl     | Category: ACCESSORY | Metric: WEIGHT   |
  | SkiErg            | Category: MACHINE   | Metric: CALORIES |
And no duplicate exercise records can be created even if a name is repeated across blocks.
```

#### Scenario 3: Complex Exercise Attribution & 1RM Curve Isolation
```gherkin
Given a workout block: "E3MOM COMPLEX: Clean + Hang Clean + Front Squat + Push to OverHead"
And set loads: "52,5 55 55 55 55 55 57,5"
When the assistant persists the complex session block
Then a composite exercise "Clean + Hang Clean + Front Squat + Push to OverHead" is cataloged
And session_blocks.mainExerciseId links to this composite exercise
And session_blocks.exerciseIdsCsv records the component exercise IDs
And the athlete's Clean and Front Squat individual 1RMs remain untouched and uncorrupted.
```

#### Scenario 4: Partial Reps, Failures, and Skipped Sets Handling
```gherkin
Given a block: "E3MOM 4 Front Squats" with sets: "57,5 60 60(1 rep) 60(fail) not_done"
When the parser generates the block sets
Then Set 1 has weight = 57.5 kg, reps = 4
And Set 2 has weight = 60.0 kg, reps = 4
And Set 3 has weight = 60.0 kg, reps = 1, notes = "Partial (target 4)"
And Set 4 has weight = 60.0 kg, reps = 0, isFailed = true
And "not_done" is skipped so exactly 4 sets are persisted to Room.
```

#### Scenario 5: Macro-Block & Triset Attribution with SessionEditor Parity
```gherkin
Given the pasted workout has "Strengh & Power block" and "Accessories block"
And "Accessories block" includes "E3MOM Trisets" with Romanian Deadlift, Pullups, and DB Twist Curl
When the assistant saves the workout session
Then each triset movement is persisted as an individual SessionBlock sharing scheme = "TRISET_1" and section = "Accessories block"
And when the athlete subsequently opens and saves this session in SessionEditor
Then the macro-block sections and triset groupings remain intact without metadata eviction.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`AppDatabase.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt` | - Bump version from `6` to `7`.<br>- Add `MIGRATION_6_7`: `ALTER TABLE routine_blocks ADD COLUMN section TEXT NOT NULL DEFAULT ''`, `ALTER TABLE session_blocks ADD COLUMN section TEXT NOT NULL DEFAULT ''`, and `ALTER TABLE session_blocks ADD COLUMN exerciseIdsCsv TEXT NOT NULL DEFAULT ''`.<br>- Register `MIGRATION_6_7` in `build()` and `demo()`. |
| **`RoutineBlock.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/RoutineBlock.kt` | - Add `val section: String = ""` to `RoutineBlock`. |
| **`SessionBlock.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt` | - Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `SessionBlock`. |
| **`SessionDraft.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt` | - Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `BlockDraft`. |
| **`SessionEditor.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` | - Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `BlockSeed` and `BlockState`.<br>- Group and render blocks by `section` in the UI to prevent metadata eviction on save. |
| **`Backup.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt` | - Add `val routineBlocks: List<RoutineBlock> = emptyList()` to `BackupData`.<br>- Update `BackupCsv` (`#crosstraining-backup-v4`): encode/decode `#routineBlocks` section and include `section` / `exerciseIdsCsv` in `#blocks`. Gracefully handle legacy v1-v3 imports. |
| **`UserCloudSyncManager.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt` | - Encode and decode `section` and `exerciseIdsCsv` on Firestore routine and session documents. |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - **[NEW]** Pure Kotlin parser with digit-bounded lookaround `(?<=\d),(?=\d)`.<br>- Extracts routine title, sections, complexes, trisets (with cluster scheme `TRISET_N`), shorthand `weight(reps)`, and round-count inheritance. |
| **`WorkoutEntityResolver.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/ai/WorkoutEntityResolver.kt` | - **[NEW]** In-transaction deduplicated exercise resolver with category and metric inference.<br>- For Barbell Complexes: registers distinct composite `Exercise` to isolate 1RM progress curves. |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Add `persistWorkoutJourney(...)` executing atomic Room transaction: creating deduplicated missing exercises, saving Routine template (optional), and saving Session with section-attributed blocks. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Update `saveSession` to copy `draft.section` and `draft.exerciseIdsCsv` to `SessionBlock`.<br>- Expose `processWorkoutText(rawText: String)` and `confirmWorkoutJourney(...)`. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - **[NEW]** 4-step interactive modal bottom sheet with `SheetState` dismissal guard, step navigation, and preview counters. |
| **`LogSessionScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LogSessionScreen.kt` | - Add "Paste Workout Notes" action button opening the assistant. |
| **`LibraryScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt` | - Add "Import Routine from Notes" option in Routines tab. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 0: Room Schema Migration `MIGRATION_6_7`, Data Models, and Backup Parity**
   - Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `SessionBlock`.
   - Add `val section: String = ""` to `RoutineBlock`.
   - Add `val section: String = ""` and `val exerciseIdsCsv: String = ""` to `BlockDraft` and `BlockSeed`.
   - Implement and register `MIGRATION_6_7`.
   - Add `routineBlocks` to `BackupData` and `#routineBlocks` to `BackupCsv` (`#crosstraining-backup-v4`).
   - Add instrumented Room migration test for `6.json` -> `7.json`.

2. **Subtask 1: Resilient Multi-Block & Syntax-Aware Document Parser (`WorkoutDocumentParser`)**
   - Implement `WorkoutDocumentParser` with lookaround comma decimal regex `(?<=\d),(?=\d)`.
   - Support shorthand `weight(reps)` (`0(4)` -> weight = 0, reps = 4) and set failure/drop flags.
   - Support triset cluster extraction (`scheme = "TRISET_N"` and round-count inheritance).
   - Comprehensive unit test suite covering `workout_example.md` and edge cases.

3. **Subtask 2: Entity Grounding, Composite Complex Isolation & Batch Ingestion Engine (`WorkoutEntityResolver`)**
   - Implement `WorkoutEntityResolver` with in-transaction case-insensitive name deduplication.
   - Implement category/metric type inference dictionary.
   - Implement composite `Exercise` generation for barbell complexes to preserve isolated 1RM curves.
   - Implement `Repository.persistWorkoutJourney` atomic transaction.
   - Unit tests verifying existing vs missing resolution, composite exercise creation, and batch insertion.

4. **Subtask 3: Workout Journey Setup Assistant UI & Downstream SessionEditor Integration**
   - Implement 4-step modal bottom sheet `WorkoutJourneyAssistantSheet` with dismissal guard.
   - Update `SessionEditor.kt` to group blocks by `section` and preserve section metadata on save.
   - Wire action buttons in `LogSessionScreen.kt` and `LibraryScreen.kt`.
   - Capture E2E visual artifacts.

---

## 🏛️ Gemini Architect Review Iteration 2

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Resolution of Complex Attribution & Schema Alignment**:
   - The dual resolution proposed in the updated plan successfully resolves the attribution problem. By adding `exerciseIdsCsv TEXT NOT NULL DEFAULT ''` to [`SessionBlock.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/SessionBlock.kt) in `MIGRATION_6_7`, structural parity between `RoutineBlock` and `SessionBlock` is achieved.
   - Cataloging Barbell Complexes as distinct composite [`Exercise`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/Exercise.kt) entities (e.g. *"Clean + Hang Clean + Front Squat + Push to Overhead"*) while linking component movement IDs inside `exerciseIdsCsv` protects individual lift PRs in [`ProgressAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/ProgressAnalytics.kt#L47) from distortion, while granting athletes dedicated 1RM progression tracking for the complex itself.

2. **Downstream UI & Session Draft Pipeline Hardening**:
   - Propagating `section` and `exerciseIdsCsv` through [`BlockDraft`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt#L20-L34), [`BlockSeed`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt#L117-L129), and [`SessionEditor.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt) eliminates the silent metadata eviction flaw identified in Round 1.
   - Visual grouping of blocks by `section` in `SessionEditor` ensures that macro-block boundaries remain editable and persistent throughout the entire session lifecycle.

3. **Backup Parity & Cloud Synchronization Fidelity**:
   - Formally introducing `routineBlocks` into [`BackupData`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt#L17-L26) and establishing the `#routineBlocks` table in [`BackupCsv`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt#L35-L132) (`#crosstraining-backup-v4`) closes the historical gap where routine definitions lost their block configurations during CSV backup/restore.
   - The dual backward-compatible fallback for `#crosstraining-backup-v1..v3` imports ensures no legacy user data is corrupted or rejected.
   - Firestore mapping updates in [`UserCloudSyncManager.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt) maintain seamless cloud synchronization across both snapshot and production environments.

4. **Triset Cluster Linking via `scheme`**:
   - Repurposing `SessionBlock.scheme` for triset/cluster tracking (e.g. `scheme = "TRISET_1"`, `scheme = "TRISET_2"`) elegantly solves the multi-block triset association challenge without requiring new foreign keys or junction tables.
   - Each movement retains its individual `SessionBlock` and `BlockSet` entities, ensuring independent volume and 1RM analytics while preserving the grouped interval relationship in the UI.

---

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

All 5 edge-case vulnerabilities surfaced in Round 1 have been rigorously neutralized:
1. **Punctuation Protection:** Lookaround regex `(?<=\d),(?=\d)` strictly confines European comma normalization to numeric boundaries, leaving text punctuation and comma-separated lists untouched.
2. **Shorthand Grammar:** The expanded regex pattern `(\d+(?:\.\d+)?)\s*(?:\((?:(\d+)\s*(?:reps?|rep)?|fail)\))?` robustly parses `0(4)`, `0(5 reps)`, `60(1 rep)`, and `60(fail)`.
3. **Accessory Round Counts:** Anchor-based round-count inheritance provides a deterministic fallback for unnumbered accessory sets, backed by interactive preview steppers in Step 4.
4. **Accidental Dismissal Protection:** `SheetState` confirmation locks prevent loss of in-flight review state on gesture dismissals.
5. **Deduplication:** In-transaction case-insensitive pre-filtering in `Repository.persistWorkoutJourney` guarantees no duplicate exercises can be inserted concurrently into SQLite.

There are no remaining unresolved architectural concerns or edge-case vulnerabilities.

---

### 🛠️ Mandatory Architectural Safeguards & Required Changes

The implementation team must adhere to the following implementation invariants during execution:
1. **Migration Verification:** Ensure `MIGRATION_6_7` is registered in both `AppDatabase.build()` and `AppDatabase.demo()`, and write an instrumented Room migration test verifying schema transformation from `6.json` to `7.json`.
2. **Deterministic Lookaround Execution:** Verify unit tests for `WorkoutDocumentParser` include edge cases with commas in notes (e.g., `"Squat, Clean & Jerk: 50,5 kg, felt heavy"` $\to$ `"Squat, Clean & Jerk: 50.5 kg, felt heavy"`).
3. **Transactional Boundary Integrity:** In `Repository.persistWorkoutJourney`, ensure that exercise cataloging, routine creation, and session logging execute strictly within a single `withDatabaseTransaction` block to prevent orphaned database states.

---

### 🏁 Verdict

VERDICT: AGREED

---

## 💬 Review Iteration 3: Operator Defect Report & Enhancement Requisite

- **Date / Author:** 2026-09-09 | Operator / Athlete
- **Status:** Evaluated & Scoped

### 📋 Defect Report & Requisite Details
During live on-device testing of the Workout Journey Setup Assistant with `workout_example.md`:
1. **Parser Misclassification of Sets Line:** The parser failed to recognize `Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done` as the sets line for `E3MOM 4 Front Squats`. Instead, it created a phantom exercise titled `"Sets(5) & Weight per set"` with category `BARBELL` and metric `WEIGHT`.
2. **Polluted Exercise Titles in Trisets:** Lines in triset blocks (`1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60`, `2- Pullups x8 reps. Extra Weight per set: ...`) were split naively without stripping list prefixes (`1- `, `2- `) or sets boilerplate before the colon (`reps. Weights per set:`, `reps. Extra Weight per set:`). This resulted in corrupted movement names like `"1- Romanian Deadlift reps. Weights per set"`.
3. **Absence of Movement Deletion Controls:** Step 3 ("Review Movements") and Step 4 ("Preview & Confirm") provide no mechanism to delete or exclude unwanted/misparsed exercises or blocks. Athletes cannot prune incorrect tracks before persisting to Room DB.

---

## 🔍 Review Iteration 4: 3-Amigos Critical Architectural & Regression Review

- **Date / Author:** 2026-09-09 | 3-Amigos Architectural Review (Gemini Architect & QA)
- **Status:** Evaluated & Aligned

### ⚖️ Verdict Matrix

| Proposal Item | Verdict | Technical Rationale & Architectural Safeguards |
| :--- | :--- | :--- |
| **1. Labeled Sets Header Grammar (`WorkoutDocumentParser`)** | **APPROVE** | Enhance `isProbableSetsLine()` with deterministic regex matching labeled prefixes: `(?i)^\s*[-*•]?\s*(?:sets?\s*(?:\(\d+\))?.*?:\s*\|weights?\s*(?:per\s*set)?:\s*\|extra\s*weight.*?:\s*)`. Tokenize parenthesized groups like `60(1 rep)` atomically using `tokenizeSetString()` rather than naive whitespace splitting. |
| **2. Triset Movement Title Cleansing** | **APPROVE** | In `parseSingleBlock()`: (a) Strip ordered list numbering `(?i)^\s*(?:\d+[-–.)]\s*\|[-*•+]\s*)`; (b) Split on `:` prior to rep extraction; (c) Strip trailing sets/weights boilerplate before the colon: `(?i)\s*(?:\.?\s*(?:extra\s*)?weights?\s*(?:per\s*set)?)\s*$`. This extracts clean names `"Romanian Deadlift"`, `"Pullups"`, `"DB Twist Curl"`. |
| **3. Interactive Movement Deletion in Step 3** | **APPROVE** | Add `IconButton` with `Icons.Filled.DeleteOutline` on each card in Step 3 (`WorkoutJourneyAssistantSheet.kt`). Implement `removeMissingExercise(name: String)` in `AppViewModel` that synchronously prunes `missingExercises`, `resolutionResult.missingExercises`, and associated blocks from `draft.document.blocks` and `resolutionResult.blockResolutions`. |
| **4. Block Deletion & Empty State Handling in Step 4** | **APPROVE** | Add delete action on block cards in Step 4. If all blocks are deleted, disable the "Confirm & Save" button to prevent empty database transactions. If all missing exercises are deleted in Step 3, show an informative banner and allow instant progression to Step 4. |

### 🚨 Identified Anti-Patterns & Structural Safeguards
1. **Orphaned Resolution Divergence:** If an exercise is removed from `draft.missingExercises` without synchronizing `draft.document.blocks` and `draft.resolutionResult.blockResolutions`, `Repository.persistWorkoutJourney` will attempt to persist an orphaned block referencing a non-existent exercise ID. **Safeguard:** All deletion operations in `AppViewModel` must atomically prune both the document block list and the block resolution list.
2. **Regex Collateral Damage on Complexes:** Stripping list prefixes must not strip arithmetic symbols like `+` within complex definitions (`Clean + Hang Clean + Front Squat`). **Safeguard:** Confine prefix stripping to the start of the line using `^\s*`.

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want the Journey Setup Assistant parser to accurately bind multi-line sets and clean movement titles, and provide interactive removal buttons on exercise and block preview cards
So that misidentified lines or unwanted movements can be eliminated effortlessly before saving routines and sessions to my library.
```

---

### Architecture & Data Flow

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                 WorkoutDocumentParser                       │
│  - Labeled sets header detection (Sets(...), Weights...:)   │
│  - Atomic paren tokenization: "60(1 rep)", "60(fail)"       │
│  - Strips list prefixes ("1- ", "2- ")                      │
│  - Strips trailing sets boilerplate ("reps. Weights per...")│
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 AppViewModel & Assistant UI                 │
│  Step 3: Missing Exercises Cards with [🗑️ Delete] Button     │
│          • Click Delete -> removeMissingExercise(name)       │
│          • Prunes missingExercises & document blocks         │
│          • Empty state: "All new exercises resolved/removed" │
│  Step 4: Preview Blocks Cards with [🗑️ Delete] Button        │
│          • Click Delete -> removeBlock(index)               │
│          • Disables Confirm button if blocks.isEmpty()       │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│                 Repository.persistWorkoutJourney            │
│  - Only approved, non-deleted blocks & exercises persisted  │
└─────────────────────────────────────────────────────────────┘
```

---

### BDD Acceptance Criteria

#### Scenario 1: Multi-Line Sets Line Binding to Preceding Block
```gherkin
Given a workout document with:
  """
  E3MOM 4 Front Squats
      Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done
  """
When the document parser processes the text
Then exactly 1 block named "Front Squats" is created with targetReps = 4 and format = "E3MOM"
And no phantom exercise "Sets(5) & Weight per set" is generated
And the block contains 4 parsed sets: 57.5 kg, 60.0 kg, 60.0 kg (1 rep), and 60.0 kg (failed).
```

#### Scenario 2: Triset List Prefix & Boilerplate Cleansing
```gherkin
Given a triset block with lines:
  """
  E3MOM Trisets:
      1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
      2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)
      3- DB Twist Curl x12 reps. Weight per set: 12,5 12,5 12,5 12,5
  """
When the document parser processes the cluster
Then the 3 movements are parsed with clean names:
  | Movement Name     | Target Reps | Scheme   |
  | Romanian Deadlift | 12          | TRISET_1 |
  | Pullups           | 8           | TRISET_1 |
  | DB Twist Curl     | 12          | TRISET_1 |
And list numbering ("1- ", "2- ", "3- ") and weight boilerplate are completely removed.
```

#### Scenario 3: Interactive Movement Deletion in Step 3
```gherkin
Given the assistant is on Step 3 ("Review Movements") with missing exercises: ["Romanian Deadlift", "Spurious Track"]
When the athlete taps the delete button on the "Spurious Track" card
Then "Spurious Track" is removed from the missing exercises list
And any block associated with "Spurious Track" is pruned from the workout draft
And if all missing exercises are deleted, an informative empty state is shown with an enabled "Next: Preview" button.
```

#### Scenario 4: Block Deletion in Step 4 & Save Guard
```gherkin
Given the assistant is on Step 4 ("Preview & Confirm") with 4 blocks
When the athlete taps the delete button on block 2
Then block 2 is removed from the draft and the block count decrements to 3
And if all remaining blocks are deleted, the "Confirm & Save" button is disabled.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - Add labeled sets prefix regex to `isProbableSetsLine()` (`sets?\s*(?:\(\d+\))?.*?:\s*`, `weights?\s*(?:per\s*set)?:\s*`, etc.).<br>- In `parseSingleBlock()`, strip list prefixes `(?i)^\s*(?:\d+[-–.)]\s*\|[-*•+]\s*)`.<br>- Split on `:` before reps extraction and strip trailing weight boilerplate (`(?i)\s*(?:\.?\s*(?:extra\s*)?weights?\s*(?:per\s*set)?)\s*$`). |
| **`WorkoutDocumentParserTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/util/WorkoutDocumentParserTest.kt` | - Add unit tests for `workout_example.md` verifying no phantom sets exercise is created and triset names are cleanly extracted. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Add `removeMissingExercise(exerciseName: String)` updating `missingExercises`, `resolutionResult`, and `document.blocks`.<br>- Add `removeJourneyBlock(blockIndex: Int)` updating `document.blocks` and `resolutionResult.blockResolutions`. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - In `Step3ReviewExercises`, add `IconButton` with `Icons.Filled.DeleteOutline` on missing exercise cards.<br>- In `Step4PreviewAndConfirm`, add delete button on block preview cards.<br>- Add empty state UI when all missing exercises or blocks are deleted. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Resilient Labeled Sets Parsing & Movement Title Cleansing (`WorkoutDocumentParser`)**
   - Update `isProbableSetsLine()` with labeled sets prefix detection.
   - Update `parseSingleBlock()` with list numbering stripping and pre-colon weight boilerplate removal.
   - Add unit tests verifying `workout_example.md` produces clean block names and zero phantom exercises.

2. **Subtask 2: Interactive Movement & Block Removal in Setup Assistant UI & ViewModel**
   - Implement `removeMissingExercise` and `removeJourneyBlock` in `AppViewModel`.
   - Add delete action buttons to Step 3 and Step 4 cards in `WorkoutJourneyAssistantSheet`.
   - Implement empty state banner in Step 3 and save guard in Step 4.

3. **Subtask 3: Verification Suite & E2E Visual Artifacts**
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

---

## 🏛️ Gemini Architect Review Iteration 3

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Multi-Line Labeled Prefix Bleed into Set Tokenizer (`WorkoutDocumentParser.kt`)**:
   - The proposed fix in Iteration 4 adds a regex to [`isProbableSetsLine()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L570-L578) to detect lines starting with `Sets(...) & Weight per set:`.
   - **Critical Omission:** When [`isProbableSetsLine()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L570-L578) returns `true`, the uncleaned raw string (e.g. `"Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done"`) is assigned to `setsLineToUse` and passed directly to [`parseSingleBlock(setsLine = ...)`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L335-L442).
   - In [`parseSingleBlock()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L393-L398), the colon split logic only executes `if (inlineSetsStr.isBlank() && textWithoutFormat.contains(":"))`. Because `setsLine` is populated, `inlineSetsStr` is **never split on colon** and retains the full prefix `"Sets(5) & Weight per set:"`.
   - In [`parseSetsString()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L454-L478), tokens are generated via whitespace splitting. If an athlete writes `"5 sets: 60 60 60"` or `"Sets 1 to 5: 57,5 60"`, standalone digits like `"5"` or `"1"` will be parsed by `parseSingleSetToken()` as valid `5.0 kg` and `1.0 kg` sets.
   - **Safeguard:** In both [`parseSingleBlock()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L335-L442) and [`parseSetsString()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L454-L478), if a labeled prefix or colon is present, the substring up to the colon (`substringAfter(":")`) must be stripped before tokenization.

2. **Suffix Remnant Pollution in Movement Name Extraction (`TARGET_REPS_SUFFIX_REGEX`)**:
   - The plan proposes stripping trailing boilerplate before the colon (`(?i)\s*(?:\.?\s*(?:extra\s*)?weights?\s*(?:per\s*set)?)\s*$`), which transforms `"1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60"` into `"Romanian Deadlift x12 reps"`.
   - However, ground truth inspection of [`TARGET_REPS_SUFFIX_REGEX`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L119) reveals the existing pattern: `Regex("""(?:x\s*(\d+)|\b(\d+)\s*reps?\b)""", RegexOption.IGNORE_CASE)`.
   - On `"Romanian Deadlift x12 reps"`, the first alternation `x\s*(\d+)` matches only `"x12"` and leaves `" reps"` in the string!
   - Consequently, removing the matched range leaves `"Romanian Deadlift  reps"`, and [`cleanName`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L401-L405) extracts `"Romanian Deadlift reps"` instead of `"Romanian Deadlift"`. Every movement formatted with `xN reps` will suffer from polluted exercise names ending in `"reps"`.
   - **Safeguard:** [`TARGET_REPS_SUFFIX_REGEX`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L119) must be updated to `Regex("""(?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b)""", RegexOption.IGNORE_CASE)`.

3. **Orphaned Exercise Ingestion on Block Deletion ("No Orphan Ingestion Invariant")**:
   - In Subtask 2, `removeJourneyBlock(blockIndex: Int)` updates `draft.document.blocks` and `draft.resolutionResult.blockResolutions`.
   - However, it fails to prune unreferenced exercises from `draft.missingExercises` and `draft.resolutionResult.missingExercises`.
   - In [`Repository.persistWorkoutJourney()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt#L887-L901), step 1 iterates over `resolutionResult.missingExercises` and inserts every single proposed exercise into SQLite, irrespective of whether any block in `blockResolutions` actually uses it.
   - **Failure Mode:** If an athlete deletes a misparsed or unwanted block in Step 4, its phantom/unwanted exercise is still permanently written to the Room database.
   - **Safeguard:** Block deletion must dynamically filter `missingExercises` to retain only those exercises actively referenced as `mainExercise` or `componentExercises` by the remaining blocks.

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

1. **Whitespace Inflexibility in List Numbering Regex**:
   - The proposed list prefix regex `(?i)^\s*(?:\d+[-–.)]\s*|[-*•+]\s*)` requires the delimiter (`-`, `.`, `)`) to immediately touch the digit. It fails on standard notes formatted with a space before the punctuation, such as `"1 - Romanian Deadlift"` or `"1 . Romanian Deadlift"`. The regex must be relaxed to `(?i)^\s*(?:\d+\s*[-–.)]\s*|[-*•+]\s*)`.
2. **Empty Block State & Navigation Traps**:
   - If an athlete deletes all missing exercises in Step 3, and all blocks in the workout happen to be newly proposed movements, `draft.document.blocks` becomes empty. Step 3 allows advancing to Step 4, where the user is stranded with a disabled "Confirm & Save" button and zero instructions. An explicit empty state banner with a call to action (e.g. "Re-paste Notes") must be displayed when `draft.document.blocks.isEmpty()`.
3. **Ghost Macro-Block Sections in Routine Descriptions**:
   - When all blocks in a macro-block section are deleted, `draft.document.sections` is left untouched. In [`Repository.persistWorkoutJourney()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt#L929), `Routine.description = document.sections.joinToString(" • ")` and `Session.title` will include ghost section headers containing zero blocks. `document.sections` must be recomputed on block deletion.
4. **Accidental Deletion Hazard on Mobile Touch**:
   - In [`WorkoutJourneyAssistantSheet.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt), placing bare delete icon buttons directly on scrollable cards creates a high risk of accidental destructive deletions during scrolling. A confirmation prompt or safe tap target is required to prevent unrecoverable data loss during review.

### 🛠️ Mandatory Architectural Safeguards & Required Changes

1. **Strict Labeled Prefix Sanitization:** In [`WorkoutDocumentParser.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt), sanitize `setsLine` and `inlineSetsStr` by extracting only the content after the colon (`substringAfter(":")`) whenever a labeled sets prefix is detected, ensuring no header numbers contaminate parsed sets.
2. **Suffix Regex Hardening:** Update [`TARGET_REPS_SUFFIX_REGEX`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L119) to `(?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b)` so that `"x12 reps"` is consumed entirely without polluting movement names.
3. **No-Orphan-Ingestion Invariant:** In [`AppViewModel.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt), ensure `removeJourneyBlock()` and `removeMissingExercise()` enforce strict referential integrity:
   - When a block is removed, filter `missingExercises` to only retain exercises still referenced by surviving blocks.
   - When an exercise is removed, remove all blocks referencing it (including composite component references).
   - Recalculate `document.sections` to exclude empty sections.
4. **Empty State & Deletion UX Safeguards:** In [`WorkoutJourneyAssistantSheet.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt), disable progression to Step 4 if `document.blocks.isEmpty()`, render clear empty state guidance, and safeguard the delete action.

### 🏁 Verdict

VERDICT: DISAGREED

---

## 🔍 Review Iteration 5: Response to Gemini Architect & Full Concession Matrix

- **Date / Author:** 2026-09-09 | Authoring Agent
- **Status:** Full Concession & Definitive Alignment

The architectural critique in Gemini Architect Review Iteration 3 is exceptionally rigorous, accurate, and completely grounded in the live codebase. We accept all 3 primary blockers and all 4 edge-case vulnerabilities without reservation. Below is the concrete technical resolution and concession matrix:

### ⚖️ Concession & Architectural Resolution Matrix

| Issue Flagged by Gemini Architect | Concrete Technical Resolution in Revised Plan |
| :--- | :--- |
| **B1: Labeled Prefix Bleed into Set Tokenizer** | In `parseSingleBlock()` and `parseSetsString()`, whenever a labeled sets prefix or colon is detected, sanitize the sets string using `substringAfter(":")`. Standalone numbers in headers (e.g. `"Sets(5) & Weight per set:"`, `"5 sets:"`) will never bleed into numeric set parsing. |
| **B2: Suffix Remnant `" reps"` Pollution** | Update `TARGET_REPS_SUFFIX_REGEX` from `(?:x\s*(\d+)|\b(\d+)\s*reps?\b)` to `Regex("""(?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b)""", RegexOption.IGNORE_CASE)`. This completely consumes `"x12 reps"`, leaving pure movement names like `"Romanian Deadlift"` with zero remnant `" reps"`. |
| **B3: No-Orphan-Ingestion Invariant on Block Deletion** | In `AppViewModel.kt`: `removeJourneyBlock(blockIndex: Int)` and `removeMissingExercise(exerciseName: String)` will dynamically recompute `missingExercises` and `resolutionResult.missingExercises` by retaining only those exercises actively referenced as `mainExercise` or `componentExercises` in surviving `draft.document.blocks`. When a block is deleted in Step 4, its unreferenced proposed exercises will never be inserted into SQLite. |
| **E1: Whitespace in List Numbering Regex** | Relax list prefix regex to `Regex("""^\s*(?:\d+\s*[-–.)]\s*|[-*•+]\s*)""", RegexOption.IGNORE_CASE)`. Correctly parses `"1 - Romanian Deadlift"` and `"1 . Romanian Deadlift"`. |
| **E2: Empty Block State & Navigation Guard** | In `WorkoutJourneyAssistantSheet.kt`: If `draft.document.blocks.isEmpty()`, disable the "Next: Preview" button in Step 3 and the "Confirm & Save" button in Step 4. Display a clear Empty State card with guidance and a "Paste New Notes" button. |
| **E3: Ghost Macro-Block Sections Pruning** | When blocks are removed in `AppViewModel`, recompute `draft.document.sections` as `draft.document.blocks.map { it.section.trim() }.filter { it.isNotBlank() }.distinct()`. Prevents ghost section headers from appearing in `Routine.description` or `Session.title`. |
| **E4: Deletion Confirmation & Safe Tap Targets** | In Step 3 and Step 4 cards, wrap the delete action in a lightweight confirmation dialog (`showDeleteConfirm = true`) or safe button with clear visual distinction to prevent accidental deletions during touch scrolling. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want the Journey Setup Assistant parser to accurately bind multi-line sets and clean movement titles, and provide interactive removal buttons on exercise and block preview cards
So that misidentified lines or unwanted movements can be eliminated effortlessly before saving routines and sessions to my library.
```

---

### Architecture & Data Flow

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                 WorkoutDocumentParser                       │
│  - Labeled sets header detection: setsLine.substringAfter(":")
│  - Regex: (?i)^\s*(?:\d+\s*[-–.)]\s*|[-*•+]\s*)            │
│  - TARGET_REPS: (?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b)│
│  - Atomic paren tokenization: "60(1 rep)", "60(fail)"       │
│  - Strips pre-colon weight boilerplate                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 AppViewModel & Assistant UI                 │
│  Step 3: Missing Exercises Cards with [🗑️ Delete] Button     │
│          • Click Delete -> removeMissingExercise(name)       │
│          • Prunes associated blocks & recomputes sections    │
│          • Empty state: "All new exercises resolved/removed" │
│  Step 4: Preview Blocks Cards with [🗑️ Delete] Button        │
│          • Click Delete -> removeJourneyBlock(index)        │
│          • No-Orphan-Ingestion: prunes unused missingExs     │
│          • Disables Confirm button if blocks.isEmpty()       │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│                 Repository.persistWorkoutJourney            │
│  - Only approved, surviving blocks & exercises persisted    │
│  - Zero orphaned exercises or empty ghost sections          │
└─────────────────────────────────────────────────────────────┘
```

---

### BDD Acceptance Criteria

#### Scenario 1: Multi-Line Sets Line Binding & Colon Sanitization
```gherkin
Given a workout document with:
  """
  E3MOM 4 Front Squats
      Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done
  """
When the document parser processes the text
Then exactly 1 block named "Front Squats" is created with targetReps = 4 and format = "E3MOM"
And no phantom exercise "Sets(5) & Weight per set" is generated
And the sets line prefix "Sets(5) & Weight per set:" is stripped using substringAfter(":")
And the block contains exactly 4 parsed sets: 57.5 kg, 60.0 kg, 60.0 kg (1 rep), and 60.0 kg (failed).
```

#### Scenario 2: Triset List Prefix & Complete Reps Suffix Cleansing
```gherkin
Given a triset block with lines:
  """
  E3MOM Trisets:
      1 - Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
      2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)
      3. DB Twist Curl x12 reps. Weight per set: 12,5 12,5 12,5 12,5
  """
When the document parser processes the cluster
Then the 3 movements are parsed with pure, clean names:
  | Movement Name     | Target Reps | Scheme   |
  | Romanian Deadlift | 12          | TRISET_1 |
  | Pullups           | 8           | TRISET_1 |
  | DB Twist Curl     | 12          | TRISET_1 |
And list numbering ("1 - ", "2- ", "3. ") and trailing "reps" suffix are completely removed.
```

#### Scenario 3: Interactive Movement Deletion in Step 3
```gherkin
Given the assistant is on Step 3 ("Review Movements") with missing exercises: ["Romanian Deadlift", "Spurious Track"]
When the athlete taps the delete button on the "Spurious Track" card
Then "Spurious Track" is removed from the missing exercises list
And any block associated with "Spurious Track" is pruned from the workout draft
And draft.document.sections is recomputed to purge empty sections
And if all missing exercises are deleted, an informative empty state is shown with an enabled "Next: Preview" button.
```

#### Scenario 4: Block Deletion in Step 4 with No-Orphan-Ingestion Invariant
```gherkin
Given the assistant is on Step 4 ("Preview & Confirm") with 4 blocks
And block 4 introduces a new missing exercise "Unwanted Accessory"
When the athlete taps the delete button on block 4
Then block 4 is removed from draft.document.blocks
And "Unwanted Accessory" is automatically pruned from draft.missingExercises and resolutionResult.missingExercises
And when the workout journey is confirmed and persisted to Room DB
Then "Unwanted Accessory" is NOT persisted to the exercises table in SQLite.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - Add labeled sets prefix regex to `isProbableSetsLine()`.<br>- In `parseSingleBlock()`, sanitize `setsLine` with `substringAfter(":")`.<br>- Relax list prefix regex to `(?i)^\s*(?:\d+\s*[-–.)]\s*\|[-*•+]\s*)`.<br>- Update `TARGET_REPS_SUFFIX_REGEX` to `(?:x\s*(\d+)(?:\s*reps?)?\|\b(\d+)\s*reps?\b)`.<br>- Split on `:` before reps extraction and strip trailing weight boilerplate. |
| **`WorkoutDocumentParserTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/util/WorkoutDocumentParserTest.kt` | - Add unit tests for `workout_example.md` verifying no phantom sets exercise is created, triset names are cleanly extracted without `"reps"` remnant, and sets line numbers don't bleed into tokens. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Add `removeMissingExercise(exerciseName: String)` pruning missingExercises, resolutionResult, and document.blocks.<br>- Add `removeJourneyBlock(blockIndex: Int)` pruning document.blocks, resolutionResult.blockResolutions, and enforcing the No-Orphan-Ingestion Invariant by filtering missingExercises to only surviving referenced exercises.<br>- Recompute `document.sections` on block/exercise deletion. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - In `Step3ReviewExercises`, add `IconButton` with `Icons.Filled.DeleteOutline` on missing exercise cards.<br>- In `Step4PreviewAndConfirm`, add delete button on block preview cards.<br>- Add empty state UI when all missing exercises or blocks are deleted, and disable navigation/save when blocks are empty. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Resilient Labeled Sets Parsing & Movement Title Cleansing (`WorkoutDocumentParser`)**
   - Update `isProbableSetsLine()` with labeled sets prefix detection.
   - Sanitize `setsLine` with `substringAfter(":")` to prevent header number bleed.
   - Update `parseSingleBlock()` with whitespace-tolerant list numbering stripping and pre-colon weight boilerplate removal.
   - Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.
   - Add unit tests verifying `workout_example.md` produces clean block names and zero phantom exercises.

2. **Subtask 2: Interactive Movement & Block Removal with No-Orphan Invariant in Setup Assistant UI & ViewModel**
   - Implement `removeMissingExercise` and `removeJourneyBlock` in `AppViewModel` with No-Orphan-Ingestion filtering and section pruning.
   - Add delete action buttons to Step 3 and Step 4 cards in `WorkoutJourneyAssistantSheet`.
   - Implement empty state banner in Step 3 and save guard in Step 4.

3. **Subtask 3: Verification Suite & E2E Visual Artifacts**
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

---

## 🏛️ Gemini Architect Review Iteration 4

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Top-Level Parse Loop Lookahead Blindspot & Phantom Block Leakage (`WorkoutDocumentParser.kt`)**:
   - Ground truth inspection of `WorkoutDocumentParser.kt` (lines 221–246) reveals a critical lookahead failure mode: `hasSetsOnNextLine` checks only the immediate next line (`lines[lineIndex]`).
   - If an athlete places a blank line between a movement header and its labeled sets line:
     ```markdown
     E3MOM 4 Front Squats

     Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done
     ```
     Because `nextLine.isBlank()` evaluates to `true`, `hasSetsOnNextLine` fails, leaving `setsLineToUse = ""` and `lineIndex` unadvanced.
   - On the subsequent iteration, `rawLine` is `"Sets(5) & Weight per set: ..."`. Because steps 1–4 (Repeatable, Title, Section, Triset) do not match, the parser falls through to step 5 (`// 5. Standard or Complex Block line`), creating a **phantom block** titled `"Sets(5) & Weight per set"`.
   - Furthermore, at the top of the parse loop (line 148), there is zero guard evaluating `if (isProbableSetsLine(rawLine))`. Any orphaned sets line resulting from blank lines or lookahead misses is unconditionally treated as a movement name, completely bypassing the sets grammar.

2. **Regex Over-Stripping Hazard on Compound Exercise Names (Missing Word Boundary `\b`)**:
   - The proposed pre-colon boilerplate regex: `(?i)\s*(?:\.?\s*(?:extra\s*)?weights?\s*(?:per\s*set)?)\s*$` contains no leading word boundary `\b` before `weights?`.
   - Because `\s*`, `\.?`, and `(?:extra\s*)?` are all optional, and `weights?` matches the literal substring `"weight"`:
     - On any exercise name containing or ending in `"weight"` (e.g. `"Bodyweight: 10 10 10"`, `"Free weight Squats: 80 80"`, or `"Light weight Deadlift:"`), the regex erroneously matches the word's own `"weight"` suffix.
     - Consequently, `"Bodyweight"` is mutilated into `"Body"`, corrupting library cataloging.
   - Furthermore, the regex only strips `weight(s)`, failing entirely on inline sets boilerplate such as `"Sets:"` or `"Sets(5) & Weight per set:"` when written inline (e.g., `"Front Squat - Sets(5) & Weight per set: 57.5 60"`).

3. **Colon-Only Sanitization Fragility in Set Tokenizer (`substringAfter(":")`)**:
   - The proposal relies exclusively on `substringAfter(":")` to strip labeled prefixes before tokenizing sets.
   - If an athlete formats their notes using standard alternative delimiters — such as a hyphen (`"Sets 1 to 5 - 57,5 60"`), en-dash (`"Weights – 60 70"`), equals sign (`"Weights = 50 60"`), or whitespace (`"Sets 57,5 60"`) — `substringAfter(":")` returns the entire string unchanged.
   - The unstripped header tokens (`"Sets"`, `"1"`, `"to"`, `"5"`) are then passed into `parseSingleSetToken()`, where standalone digits (`"1"`, `"5"`) are evaluated by `Double.toDoubleOrNull()` as valid `1.0 kg` and `5.0 kg` sets. Sanitization must be driven by deterministic regex pattern stripping rather than naive colon splitting.

4. **Corrupted / Empty Database Transactions on Total Block Deletion (`Repository.persistWorkoutJourney`)**:
   - If an athlete deletes all blocks in Step 4, or if deleting missing exercises in Step 3 prunes all blocks in the workout:
     - While the UI disables the "Confirm & Save" button, `Repository.persistWorkoutJourney` has **zero assertion or validation guard** against `resolutionResult.blockResolutions.isEmpty()`.
     - If invoked, `persistWorkoutJourney` inserts an empty `Session` record with 0 blocks and 0 sets into SQLite, as well as an empty `Routine` template with `mainExerciseId = null`.
   - The database layer must guarantee that empty session/routine transactions are rejected with an explicit validation check (`require(resolutionResult.blockResolutions.isNotEmpty())`).

5. **Order-of-Operations Inversion in `parseSingleBlock()` (`TARGET_REPS_SUFFIX_REGEX` vs Inline Sets Split)**:
   - In `parseSingleBlock()`, lines 383–398 execute `TARGET_REPS_SUFFIX_REGEX` before splitting inline sets on `:`.
   - If `setsLine` is non-empty (populated from lookahead), `inlineSetsStr.isBlank()` evaluates to `false`, causing the colon-split branch `if (inlineSetsStr.isBlank() && textWithoutFormat.contains(":"))` to be completely bypassed.
   - If the block line had a trailing colon or sets header (e.g., `1- Romanian Deadlift x12 reps. Weights per set:`), the boilerplate before the colon is never stripped, leaving `"Romanian Deadlift. Weights per set"` as the extracted movement name.

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

1. **Complex Component Deletion Ambiguity & Accidental Loss**:
   - For Barbell Complexes (`Clean + Hang Clean + Front Squat + Push to Overhead`), `WorkoutEntityResolver` generates proposals for both the composite entity and its individual component movements (`Hang Clean`, `Push to Overhead`).
   - If an athlete deletes a component exercise (e.g. `"Hang Clean"`) in Step 3, the proposed cascading deletion in `AppViewModel` silently deletes the entire complex block and all other associated movements without explicit confirmation or warning.
2. **Missing Blank-Line Lookahead in Triset Parsing**:
   - In `parseTrisetCluster()`, `isProbableSetsLine(clusterItems[i + 1])` checks only the immediate next index. If blank lines exist between triset movements and their sets, sets lines are orphaned and parsed as individual movements.
3. **Ghost Routine Descriptions on Section Pruning**:
   - When all blocks in a section are deleted, `document.sections` must be recomputed, and `draft.sessionTitle` and `draft.routineTitle` must be updated if they were synthesized from deleted sections.
4. **Step 3 Navigation Trap on Total Block Pruning**:
   - If deleting missing exercises in Step 3 prunes all blocks in `draft.document.blocks`, the "Next: Preview" button must be disabled, and an Empty State card with a "Paste New Notes" action must be rendered to prevent trapping the athlete in an unfinishable flow.

### 🛠️ Mandatory Architectural Safeguards & Required Changes

1. **Blank-Line Lookahead & Orphan Sets Invariant (`WorkoutDocumentParser.kt`)**:
   - In `parseDocument()`, lookahead for `setsLine` must advance past empty lines (`while (peekIdx < lines.size && lines[peekIdx].isBlank()) peekIdx++`).
   - At the top of the parse loop, if `isProbableSetsLine(rawLine)` is detected: bind the sets to the preceding block if it has empty sets; otherwise discard the line rather than instantiating a phantom block.
2. **Word-Bounded Boilerplate Regex**:
   - Update the pre-colon boilerplate regex to require a word boundary: `Regex("""(?i)\s*(?:\.?\s*(?:extra\s*)?\bweights?\s*(?:per\s*set)?|\bsets?\s*(?:\(\d+\))?.*?|\breps?\s*(?:per\s*set)?)\s*$""")`.
   - Ensure `"Bodyweight"` and `"Free weight"` are protected against suffix mutilation.
3. **Delimited Sets Sanitization Pattern**:
   - In `parseSetsString()` and `parseSingleBlock()`, match and strip the labeled prefix via `LABELED_SETS_PREFIX_REGEX` (handling `:`, `-`, `–`, `=`, and whitespace), using `substringAfter(":")` only as a secondary fallback.
4. **Strict Repository Validation Guard**:
   - In `Repository.persistWorkoutJourney`, enforce `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }`.
   - In `AppViewModel.confirmWorkoutJourney`, immediately return if `draft.document.blocks.isEmpty()`.
5. **Component Deletion Warning & Step 3 Navigation Guard**:
   - In `WorkoutJourneyAssistantSheet.kt`, disable "Next: Preview" in Step 3 if `draft.document.blocks.isEmpty()`, rendering an informative empty state with a "Paste New Notes" action button.
   - In `AppViewModel`, when deleting a missing exercise that belongs to a complex block, prompt or ensure explicit confirmation before deleting the entire composite block.
6. **Strict Order-of-Operations in `parseSingleBlock()`**:
   - Sequence parsing: (1) Strip list prefixes (`^\s*(?:\d+\s*[-–.)]\s*|[-*•+]\s*)`); (2) Strip/split sets line and colon delimiters; (3) Strip trailing sets/weights boilerplate with word-boundary protection; (4) Extract format; (5) Extract target reps; (6) Clean residual punctuation.

### 🏁 Verdict

VERDICT: DISAGREED

---

## 🔍 Review Iteration 6: Response to Gemini Architect & Complete Concession Matrix

- **Date / Author:** 2026-09-09 | Authoring Agent
- **Status:** Full Concession & Definitive Alignment

We unreservedly accept all 6 mandatory architectural safeguards identified by Gemini Architect in Review Iteration 4. These safeguards eliminate critical lookahead blindspots, protect compound exercise names (e.g. `"Bodyweight"`), and enforce database transactional integrity.

### ⚖️ Concession & Architectural Resolution Matrix

| Issue Flagged by Gemini Architect (Round 4) | Concrete Technical Resolution in Revised Plan |
| :--- | :--- |
| **B1: Blank-Line Lookahead Blindspot & Orphan Sets Invariant** | In `WorkoutDocumentParser.parseDocument()`, advance lookahead past empty lines (`while (peekIdx < lines.size && lines[peekIdx].isBlank()) peekIdx++`). At the top of the parse loop, if `isProbableSetsLine(rawLine)` is detected: bind sets to previous block if it has empty sets; otherwise discard the line rather than instantiating a phantom block. In `parseTrisetCluster()`, apply the same blank-line skipping lookahead. |
| **B2: Word-Bounded Boilerplate Regex (`\b`)** | Update pre-colon boilerplate regex to `Regex("""(?i)\s*(?:\.?\s*(?:extra\s*)?\bweights?\s*(?:per\s*set)?|\bsets?\s*(?:\(\d+\))?.*?|\breps?\s*(?:per\s*set)?)\s*$""")`. The mandatory `\b` word boundary protects `"Bodyweight"`, `"Free weight"`, and `"Light weight"` against suffix mutilation. |
| **B3: Multi-Delimiter Sets Sanitization** | In `parseSetsString()` and `parseSingleBlock()`, match and strip labeled headers using `LABELED_SETS_PREFIX_REGEX` supporting `:`, `-`, `–`, `=`, and whitespace delimiters, using `substringAfter(":")` as a secondary fallback. Standalone header digits like in `"Sets 1 to 5 - 57.5 60"` are completely stripped before tokenization. |
| **B4: Strict Repository Validation Guard** | In `Repository.persistWorkoutJourney()`, add `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }`. In `AppViewModel.confirmWorkoutJourney()`, guard against empty block drafts immediately. |
| **B5: Component Deletion Warning & Step 3 Navigation Guard** | In `WorkoutJourneyAssistantSheet.kt`, disable "Next: Preview" in Step 3 if `draft.document.blocks.isEmpty()`, rendering an informative empty state with a "Paste New Notes" action button. When deleting a missing exercise that belongs to a complex block, require explicit confirmation before deleting the entire composite block. |
| **B6: Strict Order-of-Operations in `parseSingleBlock()`** | Sequence execution: (1) Strip list prefixes `(?i)^\s*(?:\d+\s*[-–.)]\s*\|[-*•+]\s*)`; (2) Split/strip sets line and colon delimiters; (3) Strip trailing sets/weights boilerplate with word-boundary protection; (4) Extract format; (5) Extract target reps using hardened regex `(?:x\s*(\d+)(?:\s*reps?)?\|\b(\d+)\s*reps?\b)`; (6) Clean residual punctuation. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want the Journey Setup Assistant parser to accurately bind multi-line sets across blank lines, cleanse movement titles with word-boundary safety, and provide interactive removal buttons with referential integrity
So that misidentified lines or unwanted movements can be eliminated effortlessly before saving routines and sessions to my library.
```

---

### Architecture & Data Flow

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                 WorkoutDocumentParser                       │
│  - Blank-line lookahead: while (peek.isBlank()) peek++      │
│  - Orphan sets invariant: bind to previous or discard       │
│  - Multi-delimiter prefix regex: :, -, –, =, whitespace     │
│  - Word-bounded boilerplate regex: \bweights?, \bsets?      │
│  - Reps regex: (?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b) │
│  - Strict order-of-operations: prefix -> split -> reps      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 AppViewModel & Assistant UI                 │
│  Step 3: Missing Exercises Cards with [🗑️ Delete] Button     │
│          • Complex component warning dialog before delete   │
│          • Prunes associated blocks & recomputes sections    │
│          • Disables "Next" & shows empty state if 0 blocks   │
│  Step 4: Preview Blocks Cards with [🗑️ Delete] Button        │
│          • Click Delete -> removeJourneyBlock(index)        │
│          • No-Orphan-Ingestion: prunes unused missingExs     │
│          • Disables Confirm button if blocks.isEmpty()       │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│                 Repository.persistWorkoutJourney            │
│  - require(resolutionResult.blockResolutions.isNotEmpty())  │
│  - Only approved, surviving blocks & exercises persisted    │
│  - Zero orphaned exercises or empty ghost sections          │
└─────────────────────────────────────────────────────────────┘
```

---

### BDD Acceptance Criteria

#### Scenario 1: Multi-Line Sets Line Binding Across Blank Lines
```gherkin
Given a workout document with:
  """
  E3MOM 4 Front Squats

      Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done
  """
When the document parser processes the text
Then the blank line is skipped during lookahead
And exactly 1 block named "Front Squats" is created with targetReps = 4 and format = "E3MOM"
And no phantom exercise "Sets(5) & Weight per set" is generated
And the sets line prefix is sanitized using multi-delimiter LABELED_SETS_PREFIX_REGEX
And the block contains exactly 4 parsed sets: 57.5 kg, 60.0 kg, 60.0 kg (1 rep), and 60.0 kg (failed).
```

#### Scenario 2: Triset List Prefix & Complete Reps Suffix Cleansing with Word Boundaries
```gherkin
Given a triset block with lines:
  """
  E3MOM Trisets:
      1 - Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
      2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)
      3. Bodyweight DB Twist Curl x12 reps. Weight per set: 12,5 12,5 12,5 12,5
  """
When the document parser processes the cluster
Then the 3 movements are parsed with pure, clean names:
  | Movement Name                | Target Reps | Scheme   |
  | Romanian Deadlift            | 12          | TRISET_1 |
  | Pullups                      | 8           | TRISET_1 |
  | Bodyweight DB Twist Curl     | 12          | TRISET_1 |
And list numbering ("1 - ", "2- ", "3. ") and trailing "reps" suffix are completely removed
And "Bodyweight" retains its full word without suffix truncation.
```

#### Scenario 3: Interactive Movement Deletion in Step 3 with Navigation Guard
```gherkin
Given the assistant is on Step 3 ("Review Movements") with missing exercises: ["Romanian Deadlift", "Spurious Track"]
When the athlete taps the delete button on the "Spurious Track" card
Then "Spurious Track" is removed from the missing exercises list
And any block associated with "Spurious Track" is pruned from the workout draft
And draft.document.sections is recomputed to purge empty sections
And if all blocks are deleted, "Next: Preview" is disabled and an empty state CTA is displayed.
```

#### Scenario 4: Block Deletion in Step 4 with No-Orphan-Ingestion & Repository Guard
```gherkin
Given the assistant is on Step 4 ("Preview & Confirm") with 4 blocks
And block 4 introduces a new missing exercise "Unwanted Accessory"
When the athlete taps the delete button on block 4
Then block 4 is removed from draft.document.blocks
And "Unwanted Accessory" is automatically pruned from draft.missingExercises and resolutionResult.missingExercises
And when all blocks are deleted, the "Confirm & Save" button is disabled
And calling Repository.persistWorkoutJourney with 0 blocks throws IllegalArgumentException.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - Advance `peekIdx` past blank lines during sets lookahead in `parseDocument()` and `parseTrisetCluster()`.<br>- At top of parse loop, if `isProbableSetsLine()` detected, bind to previous block or discard.<br>- Add multi-delimiter `LABELED_SETS_PREFIX_REGEX` handling `:`, `-`, `–`, `=`, and whitespace.<br>- Update boilerplate regex with `\b` word boundaries: `(?i)\s*(?:\.?\s*(?:extra\s*)?\bweights?\s*(?:per\s*set)?\|\bsets?\s*(?:\(\d+\))?.*?\|\breps?\s*(?:per\s*set)?)\s*$`.<br>- Update `TARGET_REPS_SUFFIX_REGEX` to `(?:x\s*(\d+)(?:\s*reps?)?\|\b(\d+)\s*reps?\b)`.<br>- Follow strict order-of-operations in `parseSingleBlock()`. |
| **`WorkoutDocumentParserTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/util/WorkoutDocumentParserTest.kt` | - Add unit tests verifying: blank line between block and sets, compound words like "Bodyweight" not truncated, alternative delimiters (`-`, `–`), and triset blank line handling. |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Add `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }` in `persistWorkoutJourney`. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Implement `removeMissingExercise(exerciseName: String)` and `removeJourneyBlock(blockIndex: Int)`.<br>- Enforce No-Orphan-Ingestion by filtering missingExercises to only remaining referenced exercises.<br>- Recompute `document.sections` and synchronize routine/session titles.<br>- Guard `confirmWorkoutJourney` against empty blocks. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - In Step 3, add delete `IconButton` on missing exercise cards with confirmation dialog for complex components.<br>- In Step 4, add delete button on block cards.<br>- Disable "Next: Preview" in Step 3 and "Confirm & Save" in Step 4 if blocks are empty, showing an Empty State card with a "Paste New Notes" action. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Resilient Multi-Delimiter Parsing, Blank-Line Lookahead & Title Cleansing (`WorkoutDocumentParser`)**
   - Implement blank-line lookahead and orphan sets invariant.
   - Implement multi-delimiter labeled prefix sanitization and word-bounded boilerplate regex.
   - Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.
   - Add unit tests verifying `workout_example.md` produces clean block names, zero phantom exercises, and protects compound words like "Bodyweight".

2. **Subtask 2: Interactive Movement & Block Removal with No-Orphan Invariant & Repository Guard**
   - Implement `removeMissingExercise` and `removeJourneyBlock` in `AppViewModel` with No-Orphan filtering and section pruning.
   - Add `require(resolutionResult.blockResolutions.isNotEmpty())` in `Repository.persistWorkoutJourney`.
   - Add delete action buttons and confirmation dialogs to Step 3 and Step 4 in `WorkoutJourneyAssistantSheet`.
   - Implement empty state banner and navigation guards when blocks are empty.

3. **Subtask 3: Verification Suite & E2E Visual Artifacts**
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

---

## 🏛️ Gemini Architect Review Iteration 5

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Triset Ingestion Boundary Hazard & Swallowing of Subsequent Blocks (`WorkoutDocumentParser.kt`)**:
   - In [`parseDocument()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L184-L219), once `TRISET_HEADER_REGEX` is triggered, the item collection loop consumes all subsequent lines until it encounters `SECTION_HEADER_REGEX`, `REPEATABLE_ROUTINE_REGEX`, `TRISET_HEADER_REGEX`, or `TITLE_HEADER_REGEX`.
   - **Critical Vulnerability:** If a section contains a Triset followed by a standard strength movement, accessory, or metcon (e.g., `Accessories block:\nE3MOM Trisets\n...\nCalf Raises: 40 40 40`), the parser does NOT terminate the cluster. It continues ingesting and passes `Calf Raises` into [`parseTrisetCluster()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L266-L330).
   - Consequently, any standard movement following a triset within the same section is erroneously converted into a triset sub-block (`kind = BlockKind.SUPERSET`, `scheme = "TRISET_1"`, `format = "E3MOM"`).
   - Skipping blank lines unconditionally within the triset collection loop eliminates the only visual delimiter athletes use to terminate a cluster. The parser must detect cluster termination upon encountering an empty line followed by an unnumbered/standard block header, or when a new format keyword appears.

2. **`isProbableSetsLine` Tokenizer Dilution & Whitespace Splitting Flaw**:
   - Ground truth inspection of [`isProbableSetsLine()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L570-L578) reveals: `val tokens = line.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }`.
   - On the real-world note line `"Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done"`:
     1. Naive whitespace splitting splits `"60(1 rep)"` into two detached tokens: `"60(1"` and `"rep)"`. Neither token matches `^[\d\.,]+(?:\([^\)]+\))?$`.
     2. The 5 non-numeric words in the header (`"Sets(5)"`, `"&"`, `"Weight"`, `"per"`, `"set:"`) dilute the numeric count to 4 out of 11 tokens (`36.3%`), falling well short of the `0.5` threshold.
   - While Iteration 6 mentions adding `LABELED_SETS_PREFIX_REGEX`, it fails to mandate that `isProbableSetsLine` must (a) strip any matching labeled prefix prior to token evaluation, (b) immediately return `true` if a labeled sets prefix is detected and followed by valid set tokens, and (c) use paren-aware [`tokenizeSetString()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt#L484-L516) rather than naive regex whitespace splitting.

3. **Dual Source-of-Truth Desynchronization (`document.blocks` vs `resolutionResult.blockResolutions`)**:
   - In [`WorkoutJourneyAssistantSheet.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt#L850), Step 4 renders preview cards by iterating over `draft.document.blocks`.
   - However, in [`Repository.persistWorkoutJourney()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt#L932-L980), `RoutineBlock` and `SessionBlock` entities are generated exclusively by mapping over `resolutionResult.blockResolutions`.
   - If deletion operations in [`AppViewModel.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt) (`removeJourneyBlock(index)` or `removeMissingExercise(name)`) do not mutate both lists in exact 1:1 lockstep, positional index drift occurs. Deleting block `index` in Step 4 could delete a completely different block in `blockResolutions`, causing the UI preview to diverge from what Room persists.
   - To eliminate dual source-of-truth divergence, [`ResolvedBlockEntity`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/ai/WorkoutEntityResolver.kt#L34-L39) must serve as the authoritative model, and `draft.document.blocks` must be strictly derived from or synchronized with `resolutionResult.blockResolutions`.

4. **Silent Re-Creation Leak via Fallback in `Repository.persistWorkoutJourney` (`getOrCreateExercise`)**:
   - In [`Repository.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt#L904-L912), `getPersistedExercise(name)` resolves an exercise through existing maps, and falls back to `?: getOrCreateExercise(trimmed)`.
   - If an exercise was removed from `missingExercises` during Step 3, but a surviving block in `blockResolutions` still references that name, `getOrCreateExercise` silently inserts the deleted exercise into SQLite during the Room transaction, completely violating the athlete's deletion intent.
   - Furthermore, step 1 of [`persistWorkoutJourney()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt#L888-L901) unconditionally inserts all entries in `resolutionResult.missingExercises` before processing blocks. If a block was deleted in Step 4, any unreferenced exercise in `missingExercises` will still be permanently cataloged into the database.
   - The Repository boundary must enforce defensive referential pruning: only exercises actively referenced as `mainExercise` or in `componentExercises` by surviving `blockResolutions` may be inserted into SQLite.

5. **Idempotency & Concurrent Ingestion Race Condition in `confirmWorkoutJourney`**:
   - In [`AppViewModel.confirmWorkoutJourney()`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt#L907-L932), the persistence coroutine is launched without any check to ensure another persistence job is not already in flight.
   - If an athlete double-taps "Save & Persist" in [`WorkoutJourneyAssistantSheet.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt#L955) before `_workoutJourneyDraft.value = null` takes effect, two concurrent Room transactions execute, inserting duplicate routines and duplicate session logs into SQLite.
   - `confirmWorkoutJourney` must be guarded by an atomic in-flight flag or Mutex (`if (isPersisting) return; isPersisting = true`).

---

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

1. **Complex Component Deletion Ambiguity**:
   - In Step 3, if an athlete deletes an individual component exercise (e.g. `"Hang Clean"`) from the missing exercises list, deleting the entire composite block (`Clean + Hang Clean + Front Squat + Push to Overhead`) is destructive and unexpected if the athlete only wanted to opt out of cataloging `"Hang Clean"` as a standalone library movement.
   - The system must clarify: deleting the composite exercise deletes the block; deleting an individual component movement should either (a) prompt whether to drop the component from the complex or remove the block, or (b) simply omit that component from `exerciseIdsCsv` without discarding the entire 7-set complex.
2. **False Empty State Navigation Lock in Step 3**:
   - In Step 3, if all missing exercises are deleted, but the workout contains blocks that map to existing library exercises (e.g. `Front Squats`, `Pullups`), `draft.missingExercises.isEmpty()` evaluates to `true`.
   - If the UI confuses "zero missing exercises" with "zero workout blocks", it will erroneously disable the "Next: Preview" button. The navigation guard must check `draft.document.blocks.isEmpty()`, NOT `draft.missingExercises.isEmpty()`. When `missingExercises` is empty but blocks exist, Step 3 must show an informative success banner ("All exercises in library") with an enabled "Next: Preview" button.
3. **Ghost Routine Title Re-synthesis on Section Deletion**:
   - If `draft.sessionTitle` or `draft.routineTitle` was auto-synthesized from sections (e.g., `"Workout: Strength & Power block & Accessories block"`), and all blocks belonging to `"Strength & Power block"` are subsequently deleted in Step 4, the titles will continue to advertise deleted sections. Recomputing sections must trigger title re-synchronization when default titles are active.

---

### 🛠️ Mandatory Architectural Safeguards & Required Changes

1. **Deterministic Triset Cluster Termination (`WorkoutDocumentParser.kt`)**:
   - In `parseDocument()`, the `clusterItems` ingestion loop must terminate if:
     - An empty line is followed by a line that does not begin with an ordered list prefix (`\d+[-.)]`), bullet (`[-*•]`), or triset keyword.
     - A standard format keyword (e.g., `AMRAP`, `FOR TIME`, `EMOM`) not matching the triset format is encountered.
   - Prevent standard movements following a triset from being absorbed into the superset cluster.
2. **Hardened `isProbableSetsLine` Evaluation**:
   - In `WorkoutDocumentParser.isProbableSetsLine(line)`:
     - Check for `LABELED_SETS_PREFIX_REGEX` first; if present, strip the prefix and evaluate the remainder. If the remainder contains valid numeric set tokens or `not_done`, immediately return `true`.
     - Use `tokenizeSetString()` instead of `line.split(Regex("""[\s,]+"""))` so annotations like `"60(1 rep)"` are not fractured into non-numeric tokens.
3. **Single Source of Truth & Referential Deletion Lockstep (`AppViewModel.kt`)**:
   - Make `resolutionResult.blockResolutions` the authoritative list. Ensure `removeJourneyBlock(index)` and `removeMissingExercise(name)` update `draft.document.blocks` and `draft.resolutionResult.blockResolutions` simultaneously.
   - In `removeMissingExercise(name)`: prune all blocks whose `mainExercise.name` matches `name`. For components, prompt or omit from `componentExercises`.
   - In `removeJourneyBlock(index)`: dynamically recompute `missingExercises` to contain only exercises still referenced by remaining `blockResolutions`.
4. **Defensive Filtering at Repository Boundary (`Repository.kt`)**:
   - In `Repository.persistWorkoutJourney`:
     - Filter `resolutionResult.missingExercises` to only insert exercises actively referenced as `mainExercise` or `componentExercises` in `resolutionResult.blockResolutions`.
     - Reject empty block resolutions with `require(resolutionResult.blockResolutions.isNotEmpty())`.
5. **Idempotent Persistence Guard (`AppViewModel.kt`)**:
   - Add a private `isPersistingJourney` atomic boolean or job check in `AppViewModel`. If a persistence operation is in flight, ignore subsequent calls to `confirmWorkoutJourney` to prevent duplicate database writes.
6. **Precise Step 3 Empty State & Navigation Logic**:
   - In `WorkoutJourneyAssistantSheet.kt`:
     - If `draft.document.blocks.isEmpty()`: show Empty State Card ("No workout blocks remaining") and disable "Next: Preview".
     - If `draft.document.blocks.isNotEmpty()` and `draft.missingExercises.isEmpty()`: show "All exercises in library! Ready to preview" and keep "Next: Preview" enabled.

---

### 🏁 Verdict

VERDICT: DISAGREED

---

## 🔍 Review Iteration 7: Response to Gemini Architect & Full Concession Matrix

- **Date / Author:** 2026-09-09 | Authoring Agent
- **Status:** Full Concession & Definitive Alignment

We accept all 6 mandatory architectural safeguards identified by Gemini Architect in Review Iteration 5. These safeguards eliminate triset boundary ingestion hazards, harden `isProbableSetsLine` evaluation, ensure single-source-of-truth referential lockstep, add defensive repository filtering, and prevent double-tap race conditions.

### ⚖️ Concession & Architectural Resolution Matrix

| Issue Flagged by Gemini Architect (Round 5) | Concrete Technical Resolution in Revised Plan |
| :--- | :--- |
| **B1: Triset Ingestion Boundary Hazard** | In `WorkoutDocumentParser.parseDocument()`, terminate the triset cluster ingestion loop when an empty line is followed by an unnumbered standard block header (i.e. not starting with `\d+[-.)]` or bullet `[-*•]`), or when an incompatible format keyword (`AMRAP`, `FOR TIME`, `EMOM`) appears. Prevents standard movements following a triset from being absorbed into the superset cluster. |
| **B2: Hardened `isProbableSetsLine` Evaluation** | In `WorkoutDocumentParser.isProbableSetsLine(line)`: (1) Check for `LABELED_SETS_PREFIX_REGEX` first; if matched, strip the prefix and evaluate the remainder; if valid numeric set tokens or `not_done` exist, immediately return `true`; (2) Replace naive `line.split(Regex("""[\s,]+"""))` with paren-aware `tokenizeSetString()` so annotations like `"60(1 rep)"` are evaluated as unified tokens. |
| **B3: Single Source of Truth & Deletion Lockstep** | In `AppViewModel.kt`: Authoritatively synchronize `draft.document.blocks` and `draft.resolutionResult.blockResolutions` in lockstep. `removeJourneyBlock(index)` removes item `index` from both lists simultaneously and dynamically recomputes `missingExercises` to only retain exercises referenced in remaining `blockResolutions`. `removeMissingExercise(name)` removes all blocks whose `mainExercise.name` matches `name` from both lists. |
| **B4: Defensive Filtering at Repository Boundary** | In `Repository.persistWorkoutJourney`: Filter `resolutionResult.missingExercises` defensively to only insert exercises actively referenced as `mainExercise` or in `componentExercises` by surviving `blockResolutions`. Strictly enforce `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }`. |
| **B5: Idempotent Persistence Guard** | In `AppViewModel.kt`: Add private `private var isPersistingWorkoutJourney = false` or check active job in `confirmWorkoutJourney()`. Ignore concurrent or rapid double-tap invocations until completion. |
| **B6: Precise Step 3 Empty State & Navigation Logic** | In `WorkoutJourneyAssistantSheet.kt`: Base Step 3 navigation guard on `draft.document.blocks.isEmpty()`, NOT `missingExercises.isEmpty()`. If `missingExercises.isEmpty()` but blocks exist, render "All exercises in library! Ready to preview" and keep "Next: Preview" enabled. If `draft.document.blocks.isEmpty()`, disable navigation and render an Empty State Card with a "Paste New Notes" action. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want the Journey Setup Assistant parser to accurately bind multi-line sets across blank lines, enforce strict triset cluster boundaries, cleanse movement titles with word-boundary safety, and provide interactive removal buttons with defensive referential integrity
So that misidentified lines or unwanted movements can be eliminated effortlessly before saving routines and sessions to my library.
```

---

### Architecture & Data Flow

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                 WorkoutDocumentParser                       │
│  - Blank-line lookahead: while (peek.isBlank()) peek++      │
│  - Triset cluster termination on unnumbered standard lines  │
│  - Hardened isProbableSetsLine with tokenizeSetString()     │
│  - Multi-delimiter prefix regex: :, -, –, =, whitespace     │
│  - Word-bounded boilerplate regex: \bweights?, \bsets?      │
│  - Reps regex: (?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b) │
│  - Strict order-of-operations: prefix -> split -> reps      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 AppViewModel & Assistant UI                 │
│  Step 3: Missing Exercises Cards with [🗑️ Delete] Button     │
│          • Prunes associated blocks in exact 1:1 lockstep   │
│          • "All in library" banner when missing is empty    │
│          • Disables "Next" only if document.blocks.isEmpty  │
│  Step 4: Preview Blocks Cards with [🗑️ Delete] Button        │
│          • Click Delete -> removes from blocks & resolutions│
│          • No-Orphan: prunes unreferenced missingExercises  │
│          • Idempotent persistence guard (prevents double-tap│
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│                 Repository.persistWorkoutJourney            │
│  - require(resolutionResult.blockResolutions.isNotEmpty())  │
│  - Defensive exercise filtering: only inserts referenced    │
│  - Zero orphaned exercises or empty ghost sections          │
└─────────────────────────────────────────────────────────────┘
```

---

### BDD Acceptance Criteria

#### Scenario 1: Multi-Line Sets Line Binding & Colon Sanitization
```gherkin
Given a workout document with:
  """
  E3MOM 4 Front Squats

      Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done
  """
When the document parser processes the text
Then the blank line is skipped during lookahead
And exactly 1 block named "Front Squats" is created with targetReps = 4 and format = "E3MOM"
And no phantom exercise "Sets(5) & Weight per set" is generated
And isProbableSetsLine parses "60(1 rep)" atomically using tokenizeSetString
And the block contains exactly 4 parsed sets: 57.5 kg, 60.0 kg, 60.0 kg (1 rep), and 60.0 kg (failed).
```

#### Scenario 2: Triset Cluster Boundary Enforcement
```gherkin
Given a workout document with:
  """
  Accessories block:
  E3MOM Trisets:
      1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
      2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)

  Barbell Calves Raises x15: 60 60 60 60
  """
When the document parser processes the accessories block
Then Romanian Deadlift and Pullups are parsed as superset sub-blocks with scheme = "TRISET_1"
And the triset collection terminates before "Barbell Calves Raises"
And "Barbell Calves Raises" is parsed as a standalone STRENGTH block with format = "" and scheme != "TRISET_1".
```

#### Scenario 3: Interactive Movement Deletion in Step 3 & Precise Navigation Guard
```gherkin
Given the assistant is on Step 3 ("Review Movements") with missing exercises: ["Spurious Track"]
And all other blocks map to existing library exercises ("Front Squats", "Pullups")
When the athlete taps the delete button on the "Spurious Track" card
Then "Spurious Track" is removed from the missing exercises list
And any block associated with "Spurious Track" is pruned in 1:1 lockstep from both blocks and blockResolutions
And Step 3 displays "All exercises in library! Ready to preview"
And the "Next: Preview" button remains enabled because surviving blocks exist.
```

#### Scenario 4: Block Deletion in Step 4 with Defensive Repository Filtering & Idempotency Guard
```gherkin
Given the assistant is on Step 4 ("Preview & Confirm") with 4 blocks
And block 4 introduces a new missing exercise "Unwanted Accessory"
When the athlete taps the delete button on block 4
Then block 4 is removed from both draft.document.blocks and draft.resolutionResult.blockResolutions simultaneously
And "Unwanted Accessory" is automatically pruned from draft.missingExercises
And when the athlete confirms the workout journey
Then Repository.persistWorkoutJourney defensively verifies that "Unwanted Accessory" is not inserted into SQLite
And double-tapping "Confirm & Save" does not trigger concurrent transactions.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - Terminate triset ingestion loop on unnumbered standard lines or format changes.<br>- In `isProbableSetsLine()`, strip `LABELED_SETS_PREFIX_REGEX` first and evaluate with `tokenizeSetString()`.<br>- Advance `peekIdx` past blank lines during sets lookahead.<br>- Multi-delimiter labeled prefix sanitization.<br>- Word-bounded boilerplate regex `\bweights?`, `\bsets?`.<br>- Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.<br>- Follow strict order-of-operations in `parseSingleBlock()`. |
| **`WorkoutDocumentParserTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/util/WorkoutDocumentParserTest.kt` | - Add unit tests verifying: triset boundary termination with following standalone movements, labeled sets detection with `(1 rep)` annotations, blank-line lookahead, compound words like "Bodyweight", and alternative delimiters. |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Add defensive exercise filtering in `persistWorkoutJourney`: only insert missing exercises that are actively referenced in `blockResolutions`.<br>- Add `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }`. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Implement `removeMissingExercise(exerciseName: String)` and `removeJourneyBlock(blockIndex: Int)` keeping `document.blocks` and `resolutionResult.blockResolutions` in exact 1:1 lockstep.<br>- Dynamically recompute `missingExercises` on block deletion.<br>- Recompute `document.sections` and synchronize routine/session titles.<br>- Add `isPersistingJourney` atomic guard in `confirmWorkoutJourney` to prevent duplicate writes. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - In Step 3, add delete `IconButton` on missing exercise cards with confirmation dialog for complex components.<br>- In Step 4, add delete button on block cards.<br>- If `document.blocks.isEmpty()`, disable navigation and render Empty State Card; if only `missingExercises.isEmpty()` but blocks exist, render "All in library" banner and keep "Next" enabled.<br>- Disable "Confirm & Save" when saving or when blocks are empty. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Resilient Multi-Delimiter Parsing, Blank-Line Lookahead, Triset Boundary & Sets Evaluation (`WorkoutDocumentParser`)**
   - Implement blank-line lookahead and orphan sets invariant.
   - Implement triset cluster termination on unnumbered standard lines.
   - Harden `isProbableSetsLine()` with `LABELED_SETS_PREFIX_REGEX` and `tokenizeSetString()`.
   - Implement multi-delimiter labeled prefix sanitization and word-bounded boilerplate regex.
   - Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.
   - Add comprehensive unit tests covering all grammar patterns and boundary transitions.

2. **Subtask 2: Interactive Movement & Block Removal with Referential Lockstep, Defensive Filtering & Repository Guard**
   - Implement `removeMissingExercise` and `removeJourneyBlock` in `AppViewModel` maintaining exact 1:1 lockstep between blocks and blockResolutions.
   - Add defensive exercise filtering and `require(blockResolutions.isNotEmpty())` in `Repository.persistWorkoutJourney`.
   - Add `isPersistingJourney` idempotency guard in `AppViewModel`.
   - Add delete action buttons and confirmation dialogs to Step 3 and Step 4 in `WorkoutJourneyAssistantSheet`.
   - Implement precise Step 3/4 navigation guards and empty state banners.
3. **Subtask 3: Verification Suite & E2E Visual Artifacts**
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

---

## 🏛️ Gemini Architect Review Iteration 6

### ⚖️ Critical Architecture & Drawbacks Critique

1. **Colon-Demarcated Inline Movement Misclassification in `isProbableSetsLine`**:
   - The proposed revision in Iteration 7 hardens `isProbableSetsLine()` with `LABELED_SETS_PREFIX_REGEX` and `tokenizeSetString()`, but retains the numeric token proportion fallback (`val numericCount = ...; return numericCount > 0 && (numericCount.toDouble() / tokens.size) >= 0.5`).
   - In cross-training notes, standard strength movements are frequently formatted as inline exercise definitions with multi-set loads, such as:
     `Front Squat: 100 100 100 100` or `Barbell Calves Raises x15: 60 60 60 60`.
   - In `Front Squat: 100 100 100 100`, the tokens are `["Front", "Squat:", "100", "100", "100", "100"]`. Exactly 4 of the 6 tokens (66.7%) are numeric set values. Because the line does not match `LABELED_SETS_PREFIX_REGEX`, it falls through to the token ratio check, which evaluates to `true` (`0.667 >= 0.5`).
   - **Catastrophic Failure Mode**: During document parsing (`hasSetsOnNextLine`) or triset pairing (`parseTrisetCluster`), the parser classifies `Front Squat: 100 100 100 100` as the sets line for the *preceding* block (e.g. `Back Squat`). The `Front Squat` block is completely obliterated from the workout, and its 4 sets are erroneously attributed to `Back Squat`.
   - **Root Cause & Remedy**: A line containing a colon `:` where the prefix before `:` is NOT a labeled sets header (`LABELED_SETS_PREFIX_REGEX`) is by definition an **inline block definition**, never an isolated sets line. `isProbableSetsLine()` must immediately return `false` if a colon is present with an unapproved non-label prefix.

2. **Unnumbered Triset Premature Ingestion Termination Regression (Triset 2 Hazard)**:
   - To prevent triset clusters from swallowing subsequent standard movements, Iteration 7 proposes:
     *"terminate the triset cluster ingestion loop when an empty line is followed by an unnumbered standard block header (i.e. not starting with `\d+[-.)]` or bullet `[-*•]`), or when an incompatible format keyword (`AMRAP`, `FOR TIME`, `EMOM`) appears."*
   - In real-world athlete notes (and explicitly demonstrated by Triset 2 in `workout_example.md`!), triset movements are frequently written without sequence numbers or bullets:
     ```markdown
     E2,5MOM Trisets:
         Barbell Calves Raises x15: 60 70 70 70
         10 Cal SkiErg (tracking not needed)
         Banded Reverse Flys x15 (weight track not needed)
     ```
   - If an athlete places a blank line after `E2,5MOM Trisets:` (common markdown formatting), `Barbell Calves Raises` is unnumbered and unbulleted. Under the proposed rule, the parser encounters an empty line followed by an unnumbered line and terminates the cluster *before ingesting a single exercise*! Triset 2 is parsed with zero movements.
   - Furthermore, if an athlete pastes notes without blank lines, the blank-line dependent check fails completely, causing unnumbered standard blocks following a triset to be absorbed anyway.
   - **Architectural Safeguard**: Cluster termination must be state-aware:
     - If the cluster established a numbered sequence (`1- ...`, `2- ...`), terminate when the sequence is broken by an unnumbered movement line.
     - If the cluster established bulleted items (`- ...`, `* ...`), terminate when a non-bulleted movement line appears.
     - If the cluster items are unnumbered, rely on modality counts (e.g. 3 movements for `TRISETS`, 2 for `SUPERSETS`/`BISETS`), indentation shifts, or explicit format keywords (`AMRAP`, `FOR TIME`, `EMOM`, `E2MOM`).

3. **Complex Component Deletion Referential Contradiction & Silent Re-Creation Leak**:
   - For Barbell Complexes (`Clean + Hang Clean + Front Squat + Push to Overhead`), `WorkoutEntityResolver` adds both the composite entity and its individual component movements (`Hang Clean`, `Push to Overhead`) to `missingExercises`.
   - Iteration 7 states: `removeMissingExercise(name) removes all blocks whose mainExercise.name matches name from both lists`.
   - If an athlete taps [Delete] on `"Hang Clean"` in Step 3 to opt out of cataloging it as an independent library movement, `"Hang Clean"` is removed from `draft.missingExercises`. However, because the complex block's `mainExercise.name` is the composite string, the block is NOT removed.
   - Inside `ResolvedBlockEntity`, `componentExercises` continues to reference `Exercise(name = "Hang Clean")`.
   - When `Repository.persistWorkoutJourney` runs, step B4's defensive filter (`only insert missing exercises actively referenced as mainExercise or in componentExercises`) sees that `"Hang Clean"` is referenced in `componentExercises` and re-inserts it into SQLite anyway, directly defying the athlete's deletion!
   - If defensive filtering is removed, `getPersistedExercise("Hang Clean")` falls back to `getOrCreateExercise("Hang Clean")` at line 911, which also silently inserts it into SQLite.
   - **Architectural Safeguard**: When a component exercise is deleted in Step 3, the assistant must atomically remove that exercise from `componentExercises` of any composite block in `blockResolutions`, updating `exerciseIdsCsv` accordingly (or prompt the user: *"Remove component from complex or delete the entire complex block?"*).

4. **Fatal Coroutine Process Crash & Permanent UI Freeze on Persistence Exceptions**:
   - In `AppViewModel.confirmWorkoutJourney()`, `repo.persistWorkoutJourney()` is executed directly inside `viewModelScope.launch` with zero `try/catch/finally` protection.
   - If `persistWorkoutJourney()` throws `IllegalArgumentException` (e.g. triggered by the newly mandated `require(resolutionResult.blockResolutions.isNotEmpty())` check) or an SQLite constraint violation:
     - The unhandled exception in `viewModelScope.launch` crashes the Android application process (`FATAL EXCEPTION`).
     - The newly proposed `isPersistingJourney` boolean flag is never reset to `false`, causing all subsequent persistence attempts to be permanently ignored.
     - The Compose UI in `WorkoutJourneyAssistantSheet` remains locked in `isSaving = true`, leaving the user stranded with spinning progress indicators.
   - **Architectural Safeguard**: `confirmWorkoutJourney()` must wrap persistence in `try { ... } catch (e: Exception) { ... } finally { isPersistingJourney = false }`, logging the error and exposing a UI-observable error message or resetting `isSaving` so the user can correct the input.

5. **Dual-Source-of-Truth Architectural Fragility in `WorkoutJourneyDraft`**:
   - `WorkoutJourneyDraft` continues to maintain dual collections representing blocks: `document.blocks` (used by Step 4 preview) and `resolutionResult.blockResolutions` (used by `Repository.persistWorkoutJourney`).
   - Maintaining two decoupled lists requires every UI deletion operation (`removeJourneyBlock`, `removeMissingExercise`) to execute synchronized dual-list mutations. Any slight bug or positional index mismatch immediately causes the visual preview in Step 4 to diverge from the entities persisted to Room DB.
   - **Architectural Safeguard**: Deprecate `document.blocks` as an independent mutable list. Elevate `resolutionResult.blockResolutions` to the single authoritative source of truth, and derive block preview cards directly from `blockResolutions.map { it.block }`.

---

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

1. **Transient UI State Loss on Activity Recreation**:
   - In `WorkoutJourneyAssistantSheet.kt`, `isSaving`, `rawInputText`, and `parseError` are managed via `remember { mutableStateOf(...) }` rather than ViewModel-backed `StateFlow` or `rememberSaveable`. If screen rotation or process recreation occurs during Step 1 or while saving, all transient input and loading states are lost.
2. **Step 3 to Step 2 Navigation Blindspot on Total Missing Deletion**:
   - In `WorkoutJourneyAssistantSheet.kt` (lines 254–260), Step 4 "Back" checks `if (currentDraft.missingExercises.isNotEmpty()) viewModel.setJourneyStep(3) else viewModel.setJourneyStep(2)`. If an athlete deletes all missing exercises in Step 3 and proceeds to Step 4, clicking "Back" skips Step 3 entirely and jumps to Step 2. If the athlete subsequently clicks "Next" in Step 2, they jump straight to Step 4, permanently locking them out of viewing matched library exercises in Step 3.
3. **Empty Routine Description and Session Title Fallback**:
   - When all macro-block sections are pruned or empty, `document.sections.joinToString(" • ")` evaluates to `""`. The database layer in `Repository.persistWorkoutJourney` falls back to empty routine descriptions rather than defaulting to a clean fallback (e.g. `"General Training Session"`).

---

### 🛠️ Mandatory Architectural Safeguards & Required Changes

1. **Colon-Demarcated Non-Label Guard in `isProbableSetsLine` (`WorkoutDocumentParser.kt`)**:
   - In `WorkoutDocumentParser.isProbableSetsLine(line)`: If `line.contains(":")`, check whether the substring before `:` matches `LABELED_SETS_PREFIX_REGEX`. If it does NOT match, immediately return `false`. A colon line with a non-label prefix is strictly an inline block definition, never a sets line.
2. **Context-Aware Triset Termination (`WorkoutDocumentParser.kt`)**:
   - In `parseDocument()`, triset cluster collection must NOT terminate on unnumbered lines unless the cluster has already collected at least one numbered/bulleted item and that structural pattern is broken, or when the modality's natural count (3 movements for Triset, 2 for Superset) is reached and followed by an unindented/new movement header.
3. **Component Referential Pruning in Step 3 (`AppViewModel.kt`)**:
   - In `AppViewModel.removeMissingExercise(name)`: If `name` matches a component movement of a complex block, remove `name` from `componentExercises` of the corresponding `ResolvedBlockEntity` in `blockResolutions` and update `exerciseIdsCsv`, without deleting the composite block unless explicitly requested.
4. **Resilient Coroutine Error Handling & Mutex Guard (`AppViewModel.kt`)**:
   - Wrap `repo.persistWorkoutJourney()` inside `try ... catch ... finally` in `confirmWorkoutJourney()`. Reset `isPersistingJourney = false` in `finally`, and invoke `onComplete(null, null)` with error feedback on failure, preventing fatal process termination and UI button lock.
5. **Unified Single Source of Truth (`WorkoutJourneyDraft.kt` & `WorkoutJourneyAssistantSheet.kt`)**:
   - Eliminate dual-list drift by driving Step 4 block rendering directly from `draft.resolutionResult.blockResolutions`.

---

### 🏁 Verdict

VERDICT: DISAGREED

---

## 🔍 Review Iteration 8: Author Full Concession & Final Architectural Alignment

- **Date / Author:** 2026-09-09 | Authoring Agent
- **Status:** Full Concession & Complete Alignment

We accept all 5 critical architectural safeguards identified by Gemini Architect in Review Iteration 6 without reservation:

### ⚖️ Concession & Architectural Resolution Matrix

| Issue Flagged by Gemini Architect (Round 6) | Concrete Technical Resolution in Revised Plan |
| :--- | :--- |
| **B1: Colon-Demarcated Inline Movement Misclassification** | In `WorkoutDocumentParser.isProbableSetsLine(line)`: If `line.contains(":")`, check if the substring before `:` matches `LABELED_SETS_PREFIX_REGEX`. If it does NOT match, immediately return `false`. Lines like `Front Squat: 100 100 100 100` are strictly inline block definitions, preventing them from being swallowed as sets for preceding blocks. |
| **B2: Context-Aware Triset Cluster Termination** | In `WorkoutDocumentParser.parseDocument()`, triset cluster ingestion terminates when: (1) an established sequence (`1- `, `2- ` or `- `, `* `) is broken by an unnumbered/unbulleted line; or (2) the modality's natural count (3 for Trisets, 2 for Supersets) is reached and followed by an unindented new movement or format keyword (`AMRAP`, `EMOM`, `FOR TIME`). |
| **B3: Component Referential Pruning in Step 3** | When an athlete deletes a component exercise in Step 3 (e.g. `"Hang Clean"`), remove it from `componentExercises` of the corresponding `ResolvedBlockEntity` in `blockResolutions` and update `exerciseIdsCsv`, without deleting the entire composite block unless explicitly confirmed. |
| **B4: Resilient Coroutine Error Handling & Mutex Guard** | In `AppViewModel.confirmWorkoutJourney()`, wrap persistence in `try { ... } catch (e: Exception) { ... } finally { isPersistingJourney = false }`. Log errors, reset loading states, and invoke `onComplete(null, null)` with error feedback on failure, preventing fatal process termination and UI button lock. |
| **B5: Unified Single Source of Truth** | Elevate `draft.resolutionResult.blockResolutions` to the single authoritative collection for workout blocks. Drive Step 4 block rendering directly from `blockResolutions.map { it.block }`, eliminating dual-list drift. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated, Authoritative & Fully Aligned)

### User Story
```gherkin
As an athlete using CrossTraining
I want the Journey Setup Assistant parser to accurately bind multi-line sets across blank lines, enforce context-aware triset cluster boundaries, cleanse movement titles with word-boundary safety, and provide interactive removal buttons with referential integrity
So that misidentified lines or unwanted movements can be eliminated effortlessly before saving routines and sessions to my library.
```

---

### Architecture & Data Flow

```
[Raw Workout Text / Notes]
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│                 WorkoutDocumentParser                       │
│  - isProbableSetsLine: if colon present and prefix != label │
│    return false immediately (preserves inline movements)    │
│  - Context-aware triset cluster boundary termination         │
│  - Blank-line lookahead: while (peek.isBlank()) peek++      │
│  - Multi-delimiter prefix regex: :, -, –, =, whitespace     │
│  - Word-bounded boilerplate regex: \bweights?, \bsets?      │
│  - Reps regex: (?:x\s*(\d+)(?:\s*reps?)?|\b(\d+)\s*reps?\b) │
│  - Strict order-of-operations: prefix -> split -> reps      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 AppViewModel & Assistant UI                 │
│  Single Source of Truth: resolutionResult.blockResolutions  │
│  Step 3: Missing Exercises Cards with [🗑️ Delete] Button     │
│          • Component delete: prunes componentExercises      │
│          • "All in library" banner when missing is empty    │
│          • Disables "Next" only if blockResolutions.isEmpty │
│  Step 4: Preview Blocks Cards (derived from blockResolutions│
│          • Click Delete -> removes from blockResolutions    │
│          • No-Orphan: prunes unreferenced missingExercises  │
│          • Idempotent persistence with try/catch/finally    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ (Atomic Room Transaction)
┌─────────────────────────────────────────────────────────────┐
│                 Repository.persistWorkoutJourney            │
│  - require(resolutionResult.blockResolutions.isNotEmpty())  │
│  - Defensive exercise filtering: only inserts referenced    │
│  - Zero orphaned exercises or empty ghost sections          │
└─────────────────────────────────────────────────────────────┘
```

---

### BDD Acceptance Criteria

#### Scenario 1: Multi-Line Sets Line Binding & Colon-Demarcated Non-Label Protection
```gherkin
Given a workout document with:
  """
  E3MOM 4 Front Squats

      Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done

  Back Squats: 100 100 100 100
  """
When the document parser processes the text
Then the blank line is skipped during lookahead for Front Squats
And the sets line prefix "Sets(5) & Weight per set:" is bound to Front Squats
And "Back Squats: 100 100 100 100" is NOT misclassified as a sets line for Front Squats because "Back Squats:" is not a labeled sets prefix
And exactly 2 distinct blocks are created: "Front Squats" and "Back Squats".
```

#### Scenario 2: Triset Cluster Boundary Enforcement
```gherkin
Given a workout document with:
  """
  Accessories block:
  E3MOM Trisets:
      1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
      2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)
      3- DB Twist Curl x12 reps. Weight per set: 12,5 12,5 12,5 12,5

  Barbell Calves Raises x15: 60 60 60 60
  """
When the document parser processes the accessories block
Then Romanian Deadlift, Pullups, and DB Twist Curl are parsed as superset sub-blocks with scheme = "TRISET_1"
And the triset collection terminates when the 3-movement count is reached
And "Barbell Calves Raises" is parsed as a standalone STRENGTH block with format = "" and scheme != "TRISET_1".
```

#### Scenario 3: Component Referential Pruning in Step 3
```gherkin
Given a workout document with complex block "Clean + Hang Clean + Front Squat + Push to OverHead"
And "Hang Clean" is a newly proposed missing exercise
When the athlete taps the delete button on the "Hang Clean" card in Step 3
Then "Hang Clean" is removed from the missing exercises list
And "Hang Clean" is removed from componentExercises of the complex in blockResolutions
And the composite complex block remains intact in the workout draft without fatal deletion.
```

#### Scenario 4: Block Deletion in Step 4 with Idempotency & Error Handling
```gherkin
Given the assistant is on Step 4 ("Preview & Confirm") with 4 blocks
When the athlete taps the delete button on block 4
Then block 4 is removed from draft.resolutionResult.blockResolutions
And any unreferenced missing exercise is automatically pruned
And when the athlete confirms the workout journey
Then the coroutine executes inside a try/catch/finally block with an atomic in-flight mutex
And rapid double-taps do not trigger duplicate database operations.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`WorkoutDocumentParser.kt`** | `app/src/main/java/com/fractanomics/crosstraining/util/WorkoutDocumentParser.kt` | - In `isProbableSetsLine()`: if colon present and prefix != labeled prefix, return false immediately.<br>- Context-aware triset cluster boundary termination.<br>- Advance `peekIdx` past blank lines during sets lookahead.<br>- Multi-delimiter labeled prefix sanitization.<br>- Word-bounded boilerplate regex `\bweights?`, `\bsets?`.<br>- Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.<br>- Follow strict order-of-operations in `parseSingleBlock()`. |
| **`WorkoutDocumentParserTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/util/WorkoutDocumentParserTest.kt` | - Add unit tests verifying: colon-demarcated inline movements (e.g. `Front Squat: 100 100 100 100`) not swallowed by preceding blocks, triset cluster termination, blank-line lookahead, compound words like "Bodyweight", and alternative delimiters. |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Add defensive exercise filtering in `persistWorkoutJourney`: only insert missing exercises that are actively referenced in `blockResolutions`.<br>- Add `require(resolutionResult.blockResolutions.isNotEmpty()) { "Cannot persist workout journey with zero blocks" }`. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Use `resolutionResult.blockResolutions` as the authoritative source for blocks.<br>- In `removeMissingExercise(name)`, prune from `componentExercises` of complexes rather than deleting the composite block.<br>- In `removeJourneyBlock(index)`, remove from `blockResolutions` and dynamically filter `missingExercises`.<br>- In `confirmWorkoutJourney()`, wrap persistence in `try/catch/finally` with an `isPersistingJourney` mutex guard. |
| **`WorkoutJourneyAssistantSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/WorkoutJourneyAssistantSheet.kt` | - In Step 4, derive block preview cards directly from `draft.resolutionResult.blockResolutions`.<br>- In Step 3, add delete `IconButton` on missing exercise cards.<br>- If `blockResolutions.isEmpty()`, disable navigation and render Empty State Card; if only `missingExercises.isEmpty()` but blocks exist, render "All in library" banner and keep "Next" enabled.<br>- Disable "Confirm & Save" when saving or when blocks are empty. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Resilient Multi-Delimiter Parsing, Inline Colon Guard, Blank-Line Lookahead & Sets Evaluation (`WorkoutDocumentParser`)**
   - Implement inline colon non-label guard in `isProbableSetsLine()`.
   - Implement context-aware triset cluster termination.
   - Implement blank-line lookahead and orphan sets invariant.
   - Implement multi-delimiter labeled prefix sanitization and word-bounded boilerplate regex.
   - Update `TARGET_REPS_SUFFIX_REGEX` to consume `"xN reps"` completely.
   - Add unit tests verifying all grammar patterns, inline colon definitions, and boundary transitions.

2. **Subtask 2: Interactive Movement & Block Removal with Unified Source of Truth, Component Pruning & Mutex Guard**
   - Elevate `resolutionResult.blockResolutions` as the single authoritative source of truth.
   - Implement `removeMissingExercise` with complex component pruning in `AppViewModel`.
   - Implement `removeJourneyBlock` in `AppViewModel` with dynamic `missingExercises` pruning.
   - Add defensive exercise filtering and `require(blockResolutions.isNotEmpty())` in `Repository.persistWorkoutJourney`.
   - Add `try/catch/finally` and `isPersistingJourney` mutex guard in `AppViewModel.confirmWorkoutJourney`.
   - Add delete action buttons and confirmation dialogs to Step 3 and Step 4 in `WorkoutJourneyAssistantSheet`.
   - Implement precise Step 3/4 navigation guards and empty state banners.

3. **Subtask 3: Verification Suite & E2E Visual Artifacts**
   - Run unit tests and script regression tests.
   - Capture updated E2E screenshots.

