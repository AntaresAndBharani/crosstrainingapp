# 📋 Implementation Plan & Refinement Lifecycle: Exercise Name Priority Layout & Library Action Chips Responsive Wrap

## 📝 Initial Draft Proposal

### Context & Real-World Athlete & Coach Feedback
Following recent UI visual reviews on physical devices (screenshots attached by user):
1. **Defect 1 (Session Editor - Movement Header Text Occlusion):**
   In `SessionEditor.kt` (`CompactBlockEditor`), the exercise/block header row places the exercise name and the block type badge (`AssistChip`) in the same horizontal row. When an exercise has a longer type label—specifically `Superset / Bi-set / Tri-set` (30 characters)—or when the screen has standard mobile width, the type badge aggressively consumes available width and truncates/occludes the movement name (e.g. "Front Squats" truncated to "Front", or 3rd movement name occluded completely). The user explicitly states:
   > *"The type of the exercise/complex, in this case Superset/bi-set/Tri-set is overlapping the name of the exercise. The name is more important than the type."*
2. **Defect 2 (Library Screen - Daily Routines Action Chips Vertical Distortion):**
   In `LibraryScreen.kt` (under the "Daily Routines" tab), the action chips (`Import from Notes`, `Import Code`, `Community Library`) are placed inside a rigid single-line `Row` with `Modifier.fillMaxWidth()`. On standard mobile screens, the three chips exceed the screen width. Because they are in a horizontal `Row` without wrapping or scrolling, Compose's layout engine constrains the third chip (`Community Library`) to a microscopic width (approx 30dp wide) and forces the text to wrap character-by-character vertically down the screen across 16 vertical lines:
   `C\no\nm\nm\nu\nn\ni\nt\ny\n...\ny`
   This huge vertical column towers over the routine cards below, rendering the routine list completely illegible and occluding routine contents.

---

## 🔍 Review Iteration 1: Author 3-Amigos Architectural Analysis & Codebase Ground Truth

- **Date / Author:** 2026-09-14 | Author Agent
- **Status:** Initial 3-Amigos Architectural Review & Ground Truth Verification

### ⚖️ Technical Ground Truth & Codebase State

1. **`SessionEditor.kt` (`CompactBlockEditor` lines 1015–1080):**
   - Currently, the header row is:
     ```kotlin
     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
         Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
             Box(...) { Text("${index + 1}") }
             Text(text = block.exercise?.name ?: block.name.ifBlank { ... }, maxLines = 1, ...)
         }
         Row(verticalAlignment = Alignment.CenterVertically) {
             if (onLaunchTimer != null) { IconButton(...) }
             AssistChip(onClick = { ... }, label = { Text(block.kind.label) })
             IconButton(onClick = { block.isExpanded = !block.isExpanded })
             IconButton(onClick = onRemove)
         }
     }
     ```
   - **Root Cause:** 
     1. `block.kind.label` for `BlockKind.SUPERSET` is `"Superset / Bi-set / Tri-set"`. That is 28 characters long.
     2. In addition, within a Sub-Block (such as an `E3MOM Trisets` container), the sub-block header already states `E3MOM Trisets`. Showing `Superset / Bi-set / Tri-set` on every child movement card is completely redundant and steals ~180dp of horizontal space.
     3. The actions row on the right contains up to 4 elements: Timer button (32dp), AssistChip (~180dp), Tune button (32dp), Delete button (32dp) = ~276dp. On a 360dp-wide screen, the movement name on the left is left with less than 60dp!
   - **Architectural Remedy:**
     1. **Name Priority & Flexible Ellipsis:** Allocate flex space with primary visual hierarchy to the movement title: `Text(..., maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))`.
     2. **Concise Chip Labels:** In `BlockKind`, shorten verbose display labels for chips or provide a `shortLabel`:
        - `SUPERSET` -> `"Superset"` (or `"Triset"` / `"Superset"` instead of the slash-delimited 28-char monster).
        - `WEIGHTLIFTING` -> `"Lifting"` / `"Weightlifting"`.
        - `HYPERTROPHY` -> `"Hypertrophy"`.
     3. **Contextual Chip Suppression in Sub-Blocks:** When a block is already inside a `SubBlockGroup` (where the container is already marked as a Triset / Superset / Complex), do not render the redundant `Superset / Bi-set / Tri-set` kind chip on each individual movement row, freeing up massive space for the exercise name!
     4. **Header Layout Restructuring:** If the chip is shown, ensure the title is never crushed: use `Row` with proper weights, or place the type badge as a subtle subtitle / chip below the title if space is constrained.

2. **`LibraryScreen.kt` (lines 337–360):**
   - Currently:
     ```kotlin
     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
         FilterChip(label = { Text("Import from Notes") }, ...)
         FilterChip(label = { Text("Import Code") }, ...)
         FilterChip(label = { Text("Community Library") }, ...)
     }
     ```
   - **Root Cause:** A non-wrapping `Row` containing three `FilterChip` items that have fixed-length labels. Total width required is ~160dp + ~120dp + ~165dp = ~445dp. Mobile viewports are 360dp–412dp wide. The third chip is compressed to whatever single-digit pixels remain, forcing vertical character stacking.
   - Note that in the "Exercises" tab (line 284), the author properly used:
     ```kotlin
     FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ... }
     ```
   - **Architectural Remedy:**
     Change the rigid `Row` in `LibraryScreen.kt` line 337 to a responsive `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp))` (matching standard Material 3 filter chip rows across the app), OR a horizontally scrollable chip row. `FlowRow` cleanly places the third chip on line 2 with standard height, eliminating the vertical tower defect completely!

---

### ⚖️ Verdict Matrix

| Proposal Item | Verdict | Technical Rationale & Architectural Safeguards |
| :--- | :--- | :--- |
| **1. Concise BlockKind Chip Labels & Name Prioritization** | **APPROVE** | Exercise name is the primary domain entity; type badge is secondary metadata. Shortening `BlockKind.SUPERSET.label` or providing concise display token (`"Superset"` / `"Triset"`) and giving title `weight(1f, fill = false)` guarantees exercise legibility on all screen sizes. |
| **2. Sub-Block Movement Header Clean-up** | **APPROVE** | Redundant type badges inside nested sub-block containers are suppressed or compacted, preventing visual clutter in Trisets and Complexes. |
| **3. Library Action Chips FlowRow / Responsive Wrapping** | **APPROVE** | Replacing unconstrained single-line `Row` with `FlowRow` fixes the 16-line vertical character stretch bug, restoring routine card visibility in Coach Library. |

---

## 🎯 Final Decision Plan & User Story Specification

### Title: feat(ui): Exercise Name Header Priority & Library Action Chips Responsive Wrap

### User Story
**As an** athlete and coach using CrossTraining
**I want** exercise names in the session editor to remain fully legible without being squished by block type badges, and action chips in the routine library to wrap responsively
**So that** I can easily identify movements during workouts and access library import/community actions without vertical layout distortion.

---

### Architecture & UI Layout Invariants

1. **SessionEditor Movement Header:**
   - Exercise name has priority: `style = MaterialTheme.typography.titleMedium`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
   - `BlockKind.label` for `SUPERSET` is normalized to concise `"Superset"` (or `shortLabel`), avoiding 30-char strings in tight rows.
   - Inside `CompactBlockEditor`, actions on the right are sized with compact bounds (`Modifier.size(32.dp)`), leaving maximum width for the title.
2. **LibraryScreen Action Chips:**
   - The action row below the routine search bar is changed from rigid `Row` to `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp))`.
   - Chips always maintain natural height (32dp) and never distort vertically.

---

### BDD Acceptance Criteria

#### Scenario 1: Long Movement Name in Superset / Triset Does Not Truncate Prematurely
```gherkin
Given an athlete editing a workout with a block kind "SUPERSET"
When the block header is rendered in CompactBlockEditor on a 360dp mobile viewport
Then the exercise title receives layout priority and displays prominently
And the type badge uses a concise label ("Superset") that never squishes the title to a single word
And tune and delete buttons remain accessible on the right edge.
```

#### Scenario 2: Nested Sub-Block Movements Maintain Clean Title Visibility
```gherkin
Given movements grouped inside an "E3MOM Trisets" sub-block container
When viewed in SessionEditor
Then individual movement cards display their exercise names clearly (e.g. "Romanian Deadlift", "Pullups", "DB Twist Curl")
Without repetitive 30-character type badges occluding the movement title.
```

#### Scenario 3: Routine Library Action Chips Responsive Multi-Line Flow
```gherkin
Given a coach navigating to LibraryScreen under the "Daily Routines" tab on a standard mobile device
When the action chips ("Import from Notes", "Import Code", "Community Library") are rendered
Then the chips wrap cleanly using FlowRow without vertical letter-stacking distortion
And each chip maintains a single-line label with standard height (32dp)
And routine cards underneath remain fully visible and unobstructed.
```

#### Scenario 4: Community Library Chip Clickability and Dialog Launch
```gherkin
Given the coach viewing the Library screen
When the coach taps the wrapped "Community Library" chip
Then the Community Library modal dialog opens normally
And no layout shifts or touch target misalignments occur.
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`Enums.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/Enums.kt` | - Normalize `BlockKind.SUPERSET("Superset / Bi-set / Tri-set")` to `"Superset"` (or add `val shortLabel: String = "Superset"`) so chip labels are concise and fit standard mobile viewports. |
| **`SessionEditor.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` | - In `CompactBlockEditor`: ensure title has layout priority with `Modifier.weight(1f, fill = false)` and `TextOverflow.Ellipsis`.<br>- Use concise label on `AssistChip` and compact padding so actions row does not crowd the title.<br>- Suppress redundant kind chip if parent container is already a sub-block or display as secondary metadata. |
| **`LibraryScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt` | - Replace rigid `Row` at line 337 with `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp))` so `Community Library` wraps cleanly to the second line without vertical letter stacking. |
| **`LibraryScreenTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryScreenTest.kt` | - Add unit/layout tests verifying routine action chip grouping and routine card visibility. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1 (feat(ui): Exercise Title Layout Priority & Concise BlockKind Badges):**
   - Normalize `BlockKind.SUPERSET` label to `"Superset"` (or add concise display helper).
   - Refactor `CompactBlockEditor` header row in `SessionEditor.kt` so exercise titles receive flex priority and are never crushed by type badges.
   - Run unit tests and regression suite.
2. **Subtask 2 (feat(ui): Library Routine Action Chips Responsive FlowRow & Card Visibility):**
   - Refactor `LibraryScreen.kt` line 337 from rigid single-line `Row` to responsive `FlowRow`.
   - Verify all 3 chips (`Import from Notes`, `Import Code`, `Community Library`) maintain standard height and clean touch targets.
   - Run unit tests and verify clean card rendering.

---

## 🏛️ Gemini Architect Review Iteration 1

- **Reviewer:** Principal Systems Architect (Gemini Architect `gemini-3.8-flash-high`)
- **Date:** 2026-09-14
- **Topic:** Exercise Name Priority Layout & Library Action Chips Responsive Wrap
- **Artifacts Audited:**
  - [`docs/draft-requisites/implementation-plan.md`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/docs/draft-requisites/implementation-plan.md)
  - [`SessionEditor.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt)
  - [`LibraryScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt)
  - [`Enums.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/Enums.kt)

---

### 1. Systems Architecture & Data Contract Evaluation (`Enums.kt`)

#### A. Persistence & Room Schema Safety
- **Ground Truth Verification:** Audited [`Converters.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Converters.kt#L31-L34) and [`Backup.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt#L207). 
- Room entities (`SessionBlock`, `RoutineBlock`) persist `kind` as a string using `BlockKind.name` (e.g. `"SUPERSET"`, `"STRENGTH"`). Similarly, backup CSV V4 and JSON ingestion serialize and parse enum constants by identifier name via `runCatching { BlockKind.valueOf(...) }`.
- **Verdict:** Shortening `BlockKind.SUPERSET.label` from `"Superset / Bi-set / Tri-set"` to `"Superset"` carries **zero Room schema migration risk**, does not increment Room schema version, and preserves 100% database backward/forward compatibility.

#### B. Fallback Label Reconciliation & Domain Matching
- In `SessionEditor.kt` (line 673) and `LibraryScreen.kt` (lines 843, 853, 1459), when a block title is blank, the app defaults to `blk.name.ifBlank { blk.kind.label }`. Shortening the fallback string to `"Superset"` prevents unintended 28-character label pollution during new block initialization.
- In `ProgressScreen.kt` (lines 793–800), block performance matching uses:
  ```kotlin
  val bName = rBlk.name.ifBlank { rBlk.kind.label }
  val pbName = pb.block.name.ifBlank { pb.block.kind.label }
  ```
  Because older stored sessions may have `name == "Superset / Bi-set / Tri-set"` if saved under previous releases, the implementation should ensure `matchesRoutineBlock` either checks `pb.block.kind == rBlk.kind` or safely reconciles legacy labels (`"Superset / Bi-set / Tri-set"`) against `"Superset"`.

---

### 2. UI Layout Mechanics & Compose Constraint Analysis (`SessionEditor.kt`)

#### A. The Unweighted Measurement Physics Defect
- In `SessionEditor.kt` (`CompactBlockEditor` lines 1016–1050), the block header is constructed as:
  ```kotlin
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Row(modifier = Modifier.weight(1f)) { ... } // Left: index badge + exercise title
      Row(verticalAlignment = Alignment.CenterVertically) { ... } // Right: timer + chip + tune + delete
  }
  ```
- **Compose Layout Engine Mechanics:** In Jetpack Compose, a `Row` measures all **unweighted** children first without constraints, subtracts their measured width from `maxWidth`, and only then allocates remaining width to weighted children.
- Because the right `Row` is unweighted, its contents (Timer button [32dp] + `AssistChip` [~190dp for 28 characters] + Tune button [32dp] + Delete button [32dp] = ~286dp) greedily demand the entire viewport. On a standard 360dp mobile screen (with card padding leaving ~304dp available width), the left weighted `Row` is left with a starved allocation of $\approx 18\text{dp}$.
- Inside the left `Row`, the index circle badge alone takes $28\text{dp} + 8\text{dp} = 36\text{dp}$. Consequently, the exercise name `Text` receives zero or near-zero pixels, truncating names to 2–3 letters or occluding them entirely.

#### B. Architectural Safeguards for Header Prioritization
1. **Sub-Block Contextual Suppression Invariant:**
   - In sub-block groups (e.g. `EditorBlockItem.SubBlockGroup` lines 786–830), the outer container card already displays `item.subBlockName` (e.g. "E3MOM Trisets"), format badges, and round counters.
   - Displaying an `AssistChip("Superset")` on every nested child movement card is completely redundant.
   - **Mandate:** Introduce `inSubBlock: Boolean = false` to `CompactBlockEditor`. When `inSubBlock == true`, suppress the `AssistChip` entirely. This instantly recaptures $\approx 70\text{dp}\text{–}190\text{dp}$ of horizontal space for nested movement names.
2. **Inner Row Flexibility:**
   - Inside the left `Row`, apply `Modifier.weight(1f, fill = false)` to `Text` with `overflow = TextOverflow.Ellipsis` and `maxLines = 1`. This allows the text to expand up to available space without pushing other elements or crashing when bounded.
3. **Action Cluster Compaction:**
   - On standalone blocks where `AssistChip` is displayed, use the shortened `"Superset"` label, apply compact chip padding (`Modifier.height(26.dp)` or minimal horizontal content padding), and tighten button spacing (`Arrangement.spacedBy(2.dp)`). This caps the right action cluster to $\le 140\text{dp}$, leaving $\ge 164\text{dp}$ for the movement name on even the narrowest 360dp viewports.

---

### 3. Responsive Wrapping & Layout Consistency (`LibraryScreen.kt`)

#### A. Root Cause of the 16-Line Vertical Tower Defect
- In `LibraryScreen.kt` (lines 337–360), the "Daily Routines" tab places three action chips (`Import from Notes`, `Import Code`, `Community Library`) in a rigid horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))`.
- Each `FilterChip` contains a 16dp leading icon, internal padding, and text. Total minimum width required is $\approx 140\text{dp} + 110\text{dp} + 150\text{dp} + 16\text{dp} = 416\text{dp}$.
- On a 360dp or 390dp mobile device (with 16dp screen margins leaving 328dp content area), the third chip (`Community Library`) is squeezed into $\approx 25\text{dp}\text{–}30\text{dp}$. 
- Because `FilterChip` does not truncate by default, Compose breaks the text character-by-character over 16 vertical lines (`C\no\nm\nm...`), resulting in a $\approx 350\text{dp}$-tall vertical tower that destroys layout geometry and hides the routine cards beneath it.

#### B. Architectural Solution & Compose Performance
- Replacing `Row` with `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp))` resolves the issue completely:
  - On phones ($< 420\text{dp}$), `FlowRow` automatically and gracefully wraps `Community Library` to row 2 at its standard single-line height ($32\text{dp}$).
  - On tablets or landscape ($> 420\text{dp}$), all three chips naturally render in a single horizontal row without requiring separate layout branches.
  - **Performance:** `FlowRow` is already imported from `androidx.compose.foundation.layout.FlowRow` and proven in `LibraryScreen.kt` (line 284 for `ExerciseCategory` filters). It introduces zero measurable recomposition overhead.

---

### 4. Backward Compatibility, Migration & Test Verification Matrix

| Area | Status | Architectural Findings |
| :--- | :---: | :--- |
| **Room Database & Schema** | **PASS** | No migration needed; `Converters.kt` stores enum name strings (`"SUPERSET"`). |
| **Backup / Restore (CSV/JSON)** | **PASS** | Parsing uses `BlockKind.valueOf(name)`; label modification is purely presentation-layer. |
| **Sub-Block Hierarchical UI** | **PASS** | Suppressing type badges in sub-blocks enhances visual hierarchy and cleans up triset display. |
| **Compose Performance** | **PASS** | No unnecessary recompositions; state is tracked via fine-grained `mutableStateOf` in `BlockState`. |
| **Screen Density & Small Viewports** | **PASS** | Guarantees $\ge 164\text{dp}$ title allocation on 360dp devices, completely resolving the text occlusion bug. |

---

### 🏛️ Council Architect Conclusion

The implementation plan is architecturally sound, safe, and directly addresses the physical device regressions reported by athletes and coaches with zero schema or performance side-effects.

**VERDICT: AGREED**

---

## 🧪 Claude QA Review Iteration 1 (Requirements & UX/UI Guardian)

- **Reviewer:** QA Lead & Requirements Guardian (Claude, Round 1 of Tri-Party Review Council)
- **Date:** 2026-09-14

### Anti-Drift Check: Does the Plan Solve the Two User-Reported Defects?

1. **Defect 1 (exercise name overlapping/truncated by type badge):** ✅ Solved at the root cause. The plan (a) shortens `BlockKind.SUPERSET.label` from the 28-char `"Superset / Bi-set / Tri-set"` to `"Superset"`, (b) gives the title `Modifier.weight(1f, fill = false)` + `TextOverflow.Ellipsis` so it is measured with priority instead of being starved by the unweighted action `Row`, and (c) suppresses the redundant chip entirely inside sub-block containers where the kind is already shown by the parent. This directly matches the user's stated priority ("The name is more important than the type") and the screenshot evidence.
2. **Defect 2 (Community Library chip vertical character-stacking):** ✅ Solved at the root cause. Replacing the rigid `Row(fillMaxWidth())` with `FlowRow` is the same pattern already proven elsewhere in `LibraryScreen.kt` (Exercises tab, line 284), so this is a low-risk, codebase-consistent fix rather than a novel pattern.

Both defects are addressed structurally, not cosmetically (e.g. not just reducing font size or adding `Modifier.basicMarquee()` as a band-aid), which is the right level of fix.

### UX/UI Ergonomics Audit

- **Touch targets:** Compacting the right action cluster (`Modifier.height(26.dp)`, `spacedBy(2.dp)`) risks pushing the Timer/Tune/Delete icon buttons below Material's 48dp recommended touch target if the *visual* chip height is shrunk but the *touch* bounds aren't independently preserved. The plan should explicitly state that icon button touch targets remain ≥48dp via `Modifier.size(48.dp)` wrapping a smaller visual icon, not literal 26–32dp touch bounds — this isn't called out anywhere in the Architecture & UI Layout Invariants section.
- **FlowRow chip wrap order:** Scenario 3 assumes `Community Library` wraps to line 2 alone; worth confirming (in implementation, not just spec) that `Import from Notes` + `Import Code` don't also partially wrap on the narrowest supported width (e.g. 320dp Android Go devices), since only 360dp/390dp are analyzed.
- **Sub-block suppression consistency:** Good ergonomic call to remove the redundant chip inside sub-blocks — reduces noise without losing information the container header already renders.

### Testability Audit

- **Gap — matching-logic regression untracked:** The Gemini review (Section 1B) correctly identifies that `ProgressScreen.kt` performs PB/routine matching via `name.ifBlank { kind.label }` string comparison, and flags a need to "ensure `matchesRoutineBlock` either checks `kind` directly or reconciles legacy labels." **This finding is not carried into the Component Impact Table or the Phased INVEST Subtask Breakdown** — there is no file entry for `ProgressScreen.kt` and no subtask covering it. As written, this is an identified risk with no owned task, which means it will very likely be skipped during implementation. **Required before merge sign-off:** add `ProgressScreen.kt` to the Component Impact Table and add an explicit subtask/acceptance criterion asserting that block-matching keys off `kind` enum identity (or reconciles the legacy string), not the display label.
- **BDD scenarios are visually descriptive but not automation-ready:** Scenarios 1–3 assert outcomes like "displays prominently" and "wraps cleanly" without a concrete, assertable signal (e.g. a `testTag` on the title `Text` and on the `FlowRow`, plus a semantics-tree assertion that verifies the title's rendered width/lines or that the third chip's `top` position differs from the first two, proving wrap rather than truncation). Recommend adding `testTag("blockHeaderTitle")` / `testTag("libraryActionChipsRow")` to the impacted composables and rewriting Scenarios 1 and 3 in terms of semantics-tree queries so they're implementable as real Compose UI tests, not just manual/visual checks.
- **`LibraryScreenTest.kt` subtask is vague:** "Add unit/layout tests verifying routine action chip grouping and routine card visibility" doesn't specify what is asserted. Should specify: assert exactly 3 chips exist, assert the "Community Library" chip's height stays at standard chip height (not multiplied), and assert it remains clickable/enabled per Scenario 4.
- Scenario 4 (dialog launch on tap) is good — it protects against a regression where wrapping changes hit-testing/touch target alignment, which is a real risk when changing layout containers.

### Verdict

The plan correctly diagnoses and fixes both root causes with an idiomatic, low-risk, codebase-consistent approach (`FlowRow`, `weight(1f, fill=false)`, concise labels, sub-block suppression). The gaps found are real but are documentation/tracking gaps rather than architectural flaws: the `ProgressScreen.kt` matching-risk must be promoted from "review commentary" to a tracked subtask with its own acceptance criterion, and BDD scenarios need concrete `testTag`/semantics assertions to be genuinely testable rather than manually verified. These are correctable before implementation starts without altering the chosen architecture.

**VERDICT: AGREED**


---

## 🏛️ Tri-Party Review Council Consensus Ratification

- **Date / Author:** 2026-09-14 | Tri-Party Review Council (Author, Gemini Architect, Claude QA Guardian)
- **Status:** **✅ APPROVED BY ARCHITECT & QA CONSENSUS**

### 🏁 Final Dual Agreement
- **Gemini Architect (\gemini-3.8-flash-high\ via \gy\ CLI):** **\VERDICT: AGREED\**
- **Claude QA Guardian (\sonnet\, \effort: low\ via \claude\ CLI):** **\VERDICT: AGREED\**

### 📌 Ratified Implementation Invariants
1. **Title Layout Priority & Ellipsis:** In \CompactBlockEditor\ (\SessionEditor.kt\), the title row receives flex priority (\weight(1f, fill = false)\) and \TextOverflow.Ellipsis\. Redundant \AssistChip\ is suppressed inside \SubBlockGroup\ containers.
2. **Safe BlockKind Label Compaction & ProgressScreen Hardening:** Normalize \BlockKind.SUPERSET.label\ to \\"Superset\"\. Harden \matchesRoutineBlock\ in \ProgressScreen.kt\ to match on \pb.block.kind == rBlk.kind\ in addition to name/scheme strings so historical data matching never regresses.
3. **Responsive FlowRow in LibraryScreen:** Replace rigid single-line \Row\ with \FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp))\ so \Community Library\ wraps cleanly without letter-stacking distortion, maintaining standard 32dp height.
4. **Touch Target Ergonomics:** Action buttons maintain Material 3 \minimumInteractiveComponentSize()\ (≥48dp touch bounds).

Both Council review nodes have ratified the Final Decision Plan. The plan is 100% ready for provisioning via \/provision-story\.