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
