# 📋 Implementation Plan & Refinement Lifecycle: Athlete Body Weight Tracker & Progress Correlation

## 📝 Initial Draft Proposal

### Phase 1: Functional & CX Review (Product Owner)

#### Workflow Analysis & Screen Architecture
Integrating a dedicated Body Weight Tracker into `crosstrainingapp` enables athletes to correlate body mass trends with strength progression and conditioning benchmarks.

```
[Progress Screen / Navigation]
           │
           ├──► [Weight Overview Tab / Dashboard]
           │         ├── Metric KPI Card (Current Weight & Delta %)
           │         ├── Granularity Filter Chips (7D | 30D | 90D | 1Y | All)
           │         ├── Interactive Weight Line Chart (Canvas-based)
           │         └── Weight History List (Chronological Cards)
           │
           └──► [Log Weight Bottom Sheet] (Triggered via FAB / Edit Action)
                     ├── Decimal Keypad / Numeric Input (Pre-filled with last log)
                     ├── Date Picker with Fast Toggles (Today | Yesterday)
                     └── Action Buttons (Save / Delete if editing)
```

* **Friction Points & Solutions**:
  * **Friction**: Manually typing the date every morning slows down logging.  
    **Solution**: Default date to current local day, offering one-tap chips for "Today" and "Yesterday" alongside a date picker.
  * **Friction**: Fluctuations in daily hydration can cause false impressions of progress.  
    **Solution**: Display moving average lines (e.g., 7-day rolling average) overlaid on top of raw daily data.
  * **Friction**: Cluttered bottom navigation.  
    **Solution**: Anchor the Weight Tracker directly as a sub-tab or top-level segment inside `ProgressScreen.kt` alongside Performance and 1RM tracking.

#### Edge Cases
* **Multiple Entries on the Same Date**: When an athlete logs weight twice on the same calendar day, seamlessly upsert the entry for that date while preserving history.
* **Sparse / Missing Data Intervals**: When filtering across wide intervals with missed days, interpolate the trend line gracefully without dropping to zero.
* **Radical Outliers / Typing Errors**: If an input deviates by more than 15% from the 7-day average, show a non-blocking confirmation warning.
* **Zero or Negative Values**: Block save action; disable CTA until input matches a valid decimal format.
* **Offline Logging**: Save directly to local Room database immediately. Enqueue cloud synchronization via the existing background sync worker.

#### Acceptance Criteria (BDD)
* **Scenario 1: Add a new weight entry**: Given the user opens the "Log Weight" sheet, when they enter a valid weight (e.g., `82.4`) and tap "Save", then the entry is saved to Room DB, the bottom sheet dismisses, the KPI card updates to `82.4 kg`, and the chart animates to include the new point.
* **Scenario 2: Granularity filtering and percentage change**: Given weight records exist spanning at least 30 days, when the user selects the "30D" chip filter, then the chart aggregates entries into the 30-day window, displays the start-to-end delta, and renders the percentage change.
* **Scenario 3: Edit and delete an existing weight entry**: Given an entry exists in the weight history list, when the user taps an entry item and edits the value or taps "Delete", then the entry is updated or removed from the database, with an undo Snackbar.

---

### Phase 2: Architectural & Implementation Draft (Software Architect)

#### Proposed Codebase Impact
* **Room Database**: Increment `version` in `AppDatabase.kt`, register `WeightEntry` entity, add `WeightDao`.
* **Data Models**: `WeightEntry.kt` representing timestamped bodyweight records.
* **DAO Layer**: `WeightDao.kt` for CRUD operations and date-bounded queries.
* **Repository**: `Repository.kt` exposing reactive `Flow<List<WeightEntry>>` and CRUD suspend functions.
* **Analytics Engine**: `WeightAnalytics.kt` pure Kotlin aggregator for 7D rolling averages and deltas.
* **ViewModel**: `WeightTrackerViewModel.kt` or integration into `AppViewModel.kt`.
* **UI Screens & Components**: Integration into `ProgressScreen.kt`, `WeightEntryBottomSheet.kt`, `WeightSummaryCard.kt`.

---

## 🔍 Review Iteration 1: 3-Amigos Critical Architectural Review

- **Date / Author:** 2026-09-07 | Antigravity AI Architect
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ `efd0450`
- **Scope Reviewed:** `AppDatabase.kt` (lines 1–197), `Converters.kt`, `ProgressScreen.kt` (lines 1–150), `LineChart.kt` (lines 1–160), `UserCloudSyncManager.kt` (lines 450–600), `DataModeManager.kt`, `DemoData.kt`, `Repository.kt`.

### 1. Verdict Matrix

| Feature Component | Proposed Plan | Ground Truth Codebase Analysis | Verdict | Architectural Rationale & Required Adjustments |
| :--- | :--- | :--- | :--- | :--- |
| **Room Schema & Migration** | Increment DB version, add `WeightEntry`, use automated or explicit migration. | `AppDatabase.kt` is currently at `version = 5`. `Converters.kt` converts `LocalDate` to `Long` via `toEpochDay()`, meaning the column in SQLite must be `INTEGER NOT NULL`, and `weightKg` is `REAL NOT NULL`. Both `crosstraining.db` and `crosstraining-demo.db` must receive `MIGRATION_5_6`. | **MODIFY** | Provide explicit, concrete `MIGRATION_5_6` in `AppDatabase.kt` with exact SQL: `CREATE TABLE IF NOT EXISTS weight_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, weightKg REAL NOT NULL, date INTEGER NOT NULL, notes TEXT, createdAtMillis INTEGER NOT NULL)` and unique index on `date`. Register in both `get()` and `getDemo()`. |
| **DAO & Concurrency** | Use `@Insert(onConflict = OnConflictStrategy.REPLACE)`. | `OnConflictStrategy.REPLACE` on an auto-increment primary key causes a DELETE + INSERT in SQLite, which mutates the primary key `id` on updates. Room 2.6.1 is installed in `libs.versions.toml`. | **MODIFY** | Use Room 2.6.1's native `@Upsert` annotation on `WeightDao.upsert(entry: WeightEntry): Long`. This performs a true SQL `UPSERT` without primary key churn. |
| **Cloud Backup & Sync** | "Enqueue cloud synchronization via the existing background sync worker." | Cloud sync is executed by `UserCloudSyncManager.kt` via `supervisorScope` across explicit Firestore document paths (`exercises`, `routines`, `cycles`, `sessions`, `rep_maxes`). Weight entries are completely unmentioned in `UserCloudSyncManager.kt`. | **MODIFY** | Add `weight_entries` collection to `UserCloudSyncManager.uploadUserData` and `downloadUserData` under `userDoc(uid).collection("data").document("weight_entries")`. Route sync strictly to `data.realRepository`. |
| **Demo Mode Isolation** | None specified. | In `crosstrainingapp`, demo mode uses `crosstraining-demo.db` (`DataModeManager`). If demo mode has zero weight entries, the Weight Tracker will be completely blank and non-demonstrable in demo mode. | **MODIFY** | Add 30-day realistic sample weight trend in `DemoData.kt` (`seedDemoData`) so athletes previewing demo mode can immediately experience the weight tracking and chart features. |
| **Unit System (kg vs. lbs)** | Hardcoded `weightKg: Double` with no unit switching. | While athletes in some regions track in `kg`, athletes in the US and UK track in `lbs`. The app already supports imperial and metric across exercises and timers. | **MODIFY** | Store canonical `weightKg: Double` in Room and Firestore. Expose a user unit preference (`kg` vs. `lbs`) in UI settings and provide a one-tap toggle on the Weight Tracker card to view and input in lbs with automatic conversion ($1\text{ kg} \approx 2.20462\text{ lbs}$). |
| **Progress Screen Gating** | Embed as sub-tab in `ProgressScreen.kt`. | `ProgressScreen.kt` line 118 has an early return: `if (exercises.isEmpty()) { EmptyState(...); return@Scaffold }`. An athlete who wants to track body weight before logging any exercises would be blocked from viewing the weight tracker! | **MODIFY** | Lift the `exercises.isEmpty()` guard so that `ProgressMode.BODY_WEIGHT` renders independently of whether exercises have been created. |
| **Chart Visualization** | "Adapt canvas point mapping for date-stamped weight float values." | `MultiLineChart.kt` already exists in `com.fractanomics.crosstraining.ui.components` and accepts `List<ChartSeries>`. It renders canvas points and lines with zero external dependencies. | **APPROVE** | Reuse `MultiLineChart` directly: Series 1 = Daily weight logs (`ChartPoint(label, value)` in accent color), Series 2 = 7-Day Moving Average trend line in primary color. |

---

### 2. Identified Weak Points & Anti-Patterns

1. **Anti-Pattern 1: Unchecked Room Migration Type Mismatch (`LocalDate` as `TEXT` vs `INTEGER`)**
   Because `Converters.kt` implements `@TypeConverter fun toEpochDay(date: LocalDate?): Long?`, Room maps `LocalDate` to SQLite `INTEGER`. If the migration SQL uses `TEXT` for the `date` column, Room's schema validation throws `IllegalStateException: Migration didn't properly handle: weight_entries` on app startup, crashing existing installs.
   *Remedy:* Specify the exact `MIGRATION_5_6` DDL with `date INTEGER NOT NULL`.

2. **Anti-Pattern 2: Skipping Cloud Synchronization**
   If `weight_entries` is added solely to the local Room database without Firestore serialization in `UserCloudSyncManager.kt`, athletes who switch devices or reinstall the app will lose all historical bodyweight entries while their routines and sessions survive.
   *Remedy:* Add `weight_entries` as a first-class collection in `UserCloudSyncManager.kt` using the established `supervisorScope` pattern with overwrite protection.

3. **Anti-Pattern 3: Primary Key Mutation on Duplicate Day Logging**
   Using `@Insert(onConflict = OnConflictStrategy.REPLACE)` on SQLite tables with `@PrimaryKey(autoGenerate = true)` triggers a full row deletion and re-insertion with a new auto-increment ID whenever an athlete logs weight for the same date twice.
   *Remedy:* Use Room 2.6.1's `@Upsert` to perform an in-place SQLite `INSERT ... ON CONFLICT DO UPDATE`.

4. **Anti-Pattern 4: Empty State Gating in ProgressScreen**
   Currently, `ProgressScreen.kt` guards the entire screen content with:
   ```kotlin
   if (exercises.isEmpty()) {
       EmptyState("Add exercises and log sessions to see progress here.", Modifier.padding(pad))
       return@Scaffold
   }
   ```
   If an athlete starts in Real Data mode and taps Progress to log their body weight, they are blocked by an empty exercise message.
   *Remedy:* Restructure `ProgressScreen.kt` so the filter chips (`By exercise`, `By routine`, `Cycle goals`, `Body weight`) are always reachable, and the empty state only applies when the selected mode has no data.

---

### 3. Edge Cases & Resilience Invariants

* **Invariant 1: Cloud Sync Isolation**: Cloud backup of weight entries must only operate on `data.realRepository`. Demo weight entries from `crosstraining-demo.db` must never be uploaded to Firestore.
* **Invariant 2: Biological Smoothing (7-Day SMA)**: Daily body weight fluctuates by 1–2% due to water retention and glycogen. The dashboard must always calculate a 7-day Simple Moving Average:
  $$\text{SMA}_7(t) = \frac{1}{N} \sum_{i=0}^{N-1} W(t - i) \quad (N \le 7)$$
  over available prior logs within a 7-day window.
* **Invariant 3: Reasonable Weight Range Validation**: Prevent typographical blunders (e.g. typing 750 instead of 75.0, or 0.8 instead of 80) by enforcing valid human ranges:
  - Metric: $20.0\text{ kg} \le W \le 350.0\text{ kg}$
  - Imperial: $44.0\text{ lbs} \le W \le 770.0\text{ lbs}$
  Input outside this range disables the Save CTA and displays an inline guidance tip.
* **Invariant 4: Calendar Date Normalization**: All weight entries are indexed strictly on `LocalDate` to prevent timezone offsets from fragmenting daily weigh-ins into duplicate days.

---

---

## 🏛️ Claude Review Iteration 1

- **Date / Author:** 2026-09-07 | Claude (Principal Architect) — Round 1 Architectural Cross-Review
- **Baseline:** `main` @ `efd0450`
- **Ground truth inspected:** `data/AppDatabase.kt` (197 L), `data/Converters.kt`, `data/Backup.kt`, `data/Repository.kt` (818 L), `data/DataModeManager.kt` (190 L), `data/DemoData.kt` (274 L), `data/firebase/UserCloudSyncManager.kt` (985 L), `ui/AppViewModel.kt` (795 L), `ui/screens/ProgressScreen.kt` (917 L), `ui/components/LineChart.kt` (159 L), `gradle/libs.versions.toml`, `app/src/{test,androidTest,snapshot}`.

### ⚖️ Critical Architecture & Drawbacks Critique

**C1. The headline remedy of Review Iteration 1 — `@Upsert` — is itself the defect. (BLOCKING)**
Iteration 1 asserts that Room 2.6.1's `@Upsert` "performs a true SQL `UPSERT` without primary key churn". It does not. Room's `@Upsert` is not compiled to `INSERT … ON CONFLICT(date) DO UPDATE`; it generates an `EntityUpsertionAdapter` that attempts `INSERT OR ABORT`, catches `SQLiteConstraintException`, and then falls back to an **`UPDATE … WHERE id = ?` keyed on the primary key**. The proposed entity has a surrogate `@PrimaryKey(autoGenerate = true) id: Long = 0` plus a *separate* unique index on `date`. Therefore a second weigh-in for an existing date arrives as `id = 0`, the insert aborts on the `date` index, and the fallback update matches **zero rows**. The write is silently discarded with a returned rowid of `-1`. BDD Scenario 2 ("Overwrite / Update Same-Day Weigh-In") fails outright, and it fails *quietly* — no exception, no toast, the UI's optimistic state diverges from the database until the next Flow emission reverts it. `@Upsert` only resolves conflicts on the primary key; it has never handled conflicts on secondary unique indices.

**C2. The migration is unverifiable by construction. (BLOCKING)**
`AppDatabase.kt:41` sets `exportSchema = false`. There is no `app/schemas/` directory, no `room.schemaLocation` KSP argument in the Gradle config, and no `androidx.room:room-testing` entry in `gradle/libs.versions.toml`. `MigrationTestHelper` therefore cannot be instantiated. Subtask 1's deliverable "Unit tests for DAO operations and migration" and the `WeightTrackerUnitTest.kt` line item "Room DAO upsert on same date" are **not buildable against the current project**: `src/test/` is plain JVM JUnit 4 with no Robolectric, so it cannot open a Room database at all. Compounding this, `build()` and `demo()` register only `fallbackToDestructiveMigrationOnDowngrade()` — there is no upgrade fallback. Any divergence between the hand-written `MIGRATION_5_6` DDL and Room's generated identity hash throws `IllegalStateException: Migration didn't properly handle: weight_entries` at `Room.build()` on **every launch**, producing an unrecoverable crash loop on shipped installs. The plan ships a migration into production with the verification loop entirely absent and does not acknowledge it.

**C3. The chart component was approved without reading its contract. (BLOCKING)**
Iteration 1 marks Chart Visualization **APPROVE** — "Reuse `MultiLineChart` directly". Three properties of `LineChart.kt` invalidate that:

- **The X axis is ordinal, not temporal.** `ChartCanvas` computes `xAt(i) = leftPad + plotW * i / (xCount - 1)` (`LineChart.kt:110-112`). Points are spaced by *array index*, not by date. A three-week gap between weigh-ins renders identically to consecutive days. This directly contradicts Phase 1's edge case "Sparse / Missing Data Intervals … interpolate the trend line gracefully" and makes every visual trend on non-daily data misleading.
- **Series are index-aligned, and the plan guarantees misalignment.** The KDoc at `LineChart.kt:31-32` and `:52-54` is explicit: "Series are matched by point index, so they should be built from the same ordered date list." `WeightAnalytics.calculate7DayMovingAverage` is specified to return `List<Pair<LocalDate, Float>>` — a different type *and* a different length from the raw entry list (any leading-edge warm-up or gap handling changes the count). Two series of unequal length are drawn over the same normalized 0..1 X span, so the SMA-7 trendline is silently stretched and offset relative to the raw points it is supposed to smooth. There is no runtime check; the chart just draws a wrong picture.
- **It is neither interactive nor animated.** `ChartCanvas` has no `pointerInput`, no tooltip, no `animate*AsState`. Phase 1 specifies an "Interactive Weight Line Chart (Canvas-based)" and BDD Scenario 1 asserts "the chart animates to include the new point". Both are unimplementable against the approved component, and the Component Impact Table budgets **zero** work for `LineChart.kt`. An acceptance criterion that cannot pass is worse than a missing one.

**C4. Demo seeding targets a function that does not exist, and misses the version gate. (BLOCKING)**
Iteration 1 instructs seeding into "`DemoData.kt` (`seedDemoData`)". `DemoData` has no such function. It is a pure snapshot builder: `DemoData.snapshot()` (`DemoData.kt:27`) returns a `BackupData`, which `DataModeManager.resetDemoData()` (`DataModeManager.kt:170-173`) feeds to `Repository.importSnapshot()`. The real change surface is four files the plan never names: `BackupData` (`Backup.kt:16-24`), `DemoData.Builder.build()` (`DemoData.kt:71-78`), and `Repository.exportSnapshot()/importSnapshot()` (`Repository.kt:350-384`). Critically, `DemoData.SEED_VERSION` (`DemoData.kt:26`, currently `3`) **must** be bumped: `seedIfNeeded()` (`DataModeManager.kt:176-179`) re-seeds only when `savedVersion < SEED_VERSION` or sessions are empty. Without the bump, every existing install's demo database keeps its current contents and the Weight Tracker is permanently blank in demo mode — the exact failure the demo-data requirement exists to prevent.

**C5. Local backup/restore is omitted, and the omission is actively destructive. (BLOCKING)**
`BackupCsv` (`Backup.kt:27-222`) is the app's only local export path, wired through `AppViewModel:411` / `:429`. The plan does not mention it. Two distinct failures follow:

- *Silent data loss:* an athlete exports a backup, reinstalls, restores — routines, sessions and rep-maxes return; the entire weight history is gone, with no warning.
- *Cross-profile contamination:* `importSnapshot()` clears seven tables before reinserting (`Repository.kt:367-374`). If `weight_entries` is not added to that clear phase, restoring backup **B** over profile **A** wipes everything *except* weight — A's body-weight history silently survives into B's restored dataset and is then merged into B's chart, SMA, and cloud upload. The same defect corrupts demo mode: `resetDemoData()` uses the identical path, so demo weight rows accumulate across re-seeds and leak into the "pristine" dataset.

**C6. The Firestore path in the plan and in BDD Scenario 5 is wrong.**
`userDoc()` resolves to `environments/{currentEnv}/users/{uid}` (`UserCloudSyncManager.kt:55-56`). The plan's Component Impact Table and the data-flow diagram both specify `users/{uid}/data/weight_entries`, and BDD Scenario 5 asserts that path verbatim. Written as stated, the acceptance test validates a location the app never writes to. The environment-scoped path also means the new document requires a corresponding Firestore **security rule** update — not mentioned anywhere in the plan, and a missing rule surfaces only as a `PERMISSION_DENIED` inside the `runCatching` of the upload task, i.e. as a degraded partial sync rather than a hard failure.

**C7. Cloud sync semantics are whole-document overwrite, so multi-device use destroys history.**
`uploadCollectionWithGuard` (`UserCloudSyncManager.kt:441-475`) performs `docRef.set(mapOf("list" to payload))` — a **full replacement** of the collection document. Its only protection is the narrow case "local is entirely empty AND remote is populated". For weight this is uniquely dangerous: the data is append-only, low-volume, and generated independently on each device. Phone logs Mon–Wed, tablet logs Thu–Fri; whichever syncs second replaces the other's document wholesale and the losing days are gone from both cloud and (after the next download) local. BDD Scenario 5's claim that a new device "restores the weight history seamlessly" is only true for the single-device, first-install case. Body weight has a perfect idempotent merge key — the calendar date — and the plan mandates no merge at all.

**C8. The legacy dual-read emptiness probe will be left stale.**
`downloadUserData` computes `isNewUidEmpty` from exactly five hard-coded lists (`UserCloudSyncManager.kt:723`) and `hasLegacyData` from the same five (`:744`). Adding weight to *upload* without adding it to *both predicates* creates a real regression path: an athlete who has logged only body weight under their new UID is classified as empty, the legacy `users/{email}` document is dual-read, and legacy data is substituted. The plan's `downloadUserData` line item says only "Restore weight entries list" and is silent on both predicates.

**C9. `ProgressMode` has an `else` catch-all — the compiler will not catch the missing branch.**
`ProgressScreen.kt:173-181` ends its `when (progressMode)` with `else -> { ExerciseProgress(...) }`. Adding `BODY_WEIGHT` to the enum compiles cleanly and silently renders the exercise chart under the "Body weight" chip. There is no exhaustiveness error to backstop the wiring step, and a UI test that merely asserts "a chart is displayed" would pass.

**C10. Iteration 1 understates the `ProgressScreen` gating problem.**
The `if (exercises.isEmpty()) { … return@Scaffold }` guard at `:118-121` is correctly identified, but two further gates are missed: (a) the `FilterChip` row itself conditionally hides chips behind `routines.isNotEmpty()` and `cycles.isNotEmpty()` (`:130-146`), so the "Body weight" chip must be added *unconditionally* or it inherits the same class of bug; (b) `progressMode` is held in `remember { mutableStateOf(...) }` (`:98`), **not** `rememberSaveable`. An athlete mid-weigh-in who rotates the device or returns after process death is silently thrown back to `BY_EXERCISE` — tolerable for the existing tabs, unacceptable for a tab that owns a data-entry flow.

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

**V1. The SMA-7 definition is mathematically underdetermined on real data.** `SMA₇(t) = (1/N) Σ W(t−i), N ≤ 7` leaves `W(t−i)` **undefined** for any missed day, which is the normal case. The spec never states whether the window is *7 calendar days* or *the last 7 logged entries*, nor whether `N` is the window width or the count of available samples. Those readings produce different numbers on identical data. Worse, at the leading edge `N = 1` makes the "smoothed" line exactly equal to the raw line — the SMA visually collapses onto the noise it exists to remove, directly defeating Invariant 2's stated rationale (1–2% daily hydration swing). No minimum-sample threshold, no gap policy, no "insufficient data" state is specified.

**V2. The ±15% outlier warning is both undefined and near-useless.** It is specified against "the 7-day average" — which V1 leaves undefined — with no stated behavior for the first-ever entry (no average exists) or for a resumed log after a long gap. And 15% of 80 kg is 12 kg: a genuine fat-finger such as `88.4` for `78.4` (12.7%) passes unchallenged, while the rule fires only on errors the range validation (V3) already catches.

**V3. The metric and imperial validation bounds are not equivalent, and the conversion contract is missing.** `20.0 kg` = `44.09 lbs`, but the stated imperial floor is `44.0 lbs` = `19.958 kg`. `770 lbs` = `349.27 kg` against a `350.0 kg` ceiling. If validation runs on the canonical kg value (as the storage model implies), the documented imperial floor is rejected; if it runs pre-conversion, the two unit modes admit materially different real weights. Separately, no rounding or precision contract is defined for the lbs↔kg round trip: displaying a stored kg value at one decimal, converting to lbs for edit, and converting back accumulates drift on every re-save — at exactly the 0.1 kg resolution the feature is built to measure.

**V4. `LocalDate.now()` is device-timezone-dependent, which Invariant 4 does not actually solve.** Normalizing storage to `LocalDate` prevents *offset* fragmentation but not *day-boundary* fragmentation: an athlete who flies east loses a calendar day, one who flies west gets a duplicate "today" that already has an entry. With `date` as a uniqueness key this is not cosmetic — it is a write conflict that lands squarely on the broken C1 upsert path.

**V5. No downsampling for the "All" / "1Y" timeframes.** `ChartCanvas` draws one `drawLine` plus one `drawCircle` per point with no decimation. Three years of daily logs is ~1,100 points across a ~360 dp canvas: sub-pixel X spacing, ~2,200 draw calls per frame, and overlapping 4 dp dots that render as a solid band. The chart also labels only the first and last X positions (`:148-155`), so a multi-year view is an unreadable smear with two dates under it.

**V6. Undo has an unbounded race against sync.** BDD Scenario 4 deletes immediately and offers a 4-second undo. `AppViewModel` triggers `uploadUserData(data.realRepository)` from several call sites (`:120, :195, :303, :309, :314`). A sync firing inside the undo window uploads the post-delete state; the subsequent undo restores locally but — given C7's whole-document overwrite — the restored row is only reconciled if another upload happens to follow. Deletion should be deferred until the snackbar dismisses, not executed optimistically.

**V7. `createdAtMillis` is a column with no consumer.** It appears in the entity and the DDL but in no query, sort order, sync payload, or UI element anywhere in the plan. Either give it a defined purpose — the natural one being a last-write-wins tiebreaker for the date-keyed cloud merge required by C7 — or remove it rather than migrating a dead column into production.

**V8. Schema conventions diverge, and the index annotation is a validation trap.** Every other entity in this schema uses non-null `notes TEXT NOT NULL DEFAULT ''` (see `MIGRATION_1_2`, `MIGRATION_4_5` in `AppDatabase.kt:63-108`); the plan introduces a nullable `notes: String?`. More dangerously, the entity field list omits the `@Index(unique = true)` annotation while the DDL creates a unique index. Room validates indices as part of the identity hash: an index present in SQLite but absent from the entity fails validation exactly as harshly as a missing one, and the index name must match Room's generated form `index_weight_entries_date` **exactly**.

**V9. `WeightAnalytics.kt` is placed in the wrong layer and narrows precision.** Siting a pure-domain aggregator at `ui/weight/WeightAnalytics.kt` contradicts the established `data/` ↔ `ui/` split and makes it awkward to unit-test without the Compose toolchain. Its return type `List<Pair<LocalDate, Float>>` also narrows `Double` storage to `Float` — 24 bits of mantissa applied to values whose meaningful deltas are 0.1 kg.

**V10. Demo-mode isolation (Invariant 1) is already satisfied and is not a real risk; the plan over-weights it.** Every sync call site in `AppViewModel` (`:120, :135, :150, :165, :195, :196, :248, :303, :309, :314`) already passes `data.realRepository` explicitly, and `demoRepository` is a distinct `Repository` over `crosstraining-demo.db`. Invariant 1 requires *no new work* beyond not regressing it. The genuine, unaddressed demo-mode risk is C4/C5 — leakage through `importSnapshot`, not through Firestore.

**V11. Firestore's 1 MiB per-document ceiling applies.** The whole collection is stored as a single `list` array in one document. Weight entries are small, but the plan should record the bound and the resulting retention/paging behavior rather than leaving an unbounded append-only array in a fixed-size document.

**V12. No entry point is specified for the log action.** `ProgressScreen`'s `Scaffold` (`:99-116`) declares no `floatingActionButton`, and the screen already consumes `outerPadding.calculateBottomPadding()` for the bottom navigation bar. Phase 1 specifies "Triggered via FAB". Adding a mode-conditional FAB and its interaction with the bottom-nav inset is unspecified work not reflected in any subtask.

### 🛠️ Mandatory Architectural Safeguards & Required Changes

**M1 — Replace the surrogate key with a natural date primary key.** Define `@Entity(tableName = "weight_entries") data class WeightEntry(@PrimaryKey val date: LocalDate, val weightKg: Double, val notes: String = "", val updatedAtMillis: Long)`. This dissolves C1 entirely: with `date` as the primary key, `@Upsert`'s update-by-PK fallback is *correct*, no separate unique index exists to trip the identity hash (V8), same-day re-logging is genuinely idempotent, and the cloud merge in M6 gets a stable cross-device key for free. Delete and undo key on `date` rather than a rowid that changes. DDL becomes: `CREATE TABLE IF NOT EXISTS 'weight_entries' ('date' INTEGER NOT NULL, 'weightKg' REAL NOT NULL, 'notes' TEXT NOT NULL DEFAULT '', 'updatedAtMillis' INTEGER NOT NULL, PRIMARY KEY('date'))` (using Room's backtick quoting). If a surrogate `id` is retained for any reason, `@Upsert` **must** be replaced by an explicit `@Transaction` that reads `getEntryByDate(date)` and dispatches to `@Update` or `@Insert`.

**M2 — Enable schema export before writing the migration.** Set `exportSchema = true`, add the `room.schemaLocation` KSP argument pointing at `app/schemas`, commit the generated `6.json`, and add `androidx.room:room-testing` to the version catalog. Then add a real `MigrationTestHelper` test in `src/androidTest/` that opens a v5 database, runs `MIGRATION_5_6`, and validates. Without this, C2 stands and the migration ships unverified. Move the DAO tests from `src/test/` to `src/androidTest/` (in-memory `Room.inMemoryDatabaseBuilder`) or add Robolectric — as written they cannot compile.

**M3 — Register `MIGRATION_5_6` in both builders, by their real names.** The demo builder is `AppDatabase.demo(context)` (`AppDatabase.kt:130`), **not** `getDemo()` as Iteration 1 states. Both `build()` (`:181`) and `demo()` (`:136`) must have the migration appended to their `addMigrations(...)` chains; omitting either crashes that database on first open after upgrade.

**M4 — Make `BackupData` the single source of truth for the new table.** Add `weightEntries: List<WeightEntry> = emptyList()` to `BackupData`; emit it from `DemoData.Builder.build()`; read it in `Repository.exportSnapshot()`; **clear it in `importSnapshot()`'s delete phase** and reinsert it; add a `#weightEntries` section to `BackupCsv.encode`/`decode` (the section-dispatch parser ignores unknown sections, so old v2 files still decode — but bump the header to `#crosstraining-backup-v3` for clarity). Bump `DemoData.SEED_VERSION` from `3` to `4`. All five of these are required together; any one omitted reproduces C4 or C5.

**M5 — Correct the Firestore path everywhere and add the security rule.** The document is `environments/{currentEnv}/users/{uid}/data/weight_entries`. Fix the Component Impact Table, the ASCII data-flow diagram, and BDD Scenario 5. Add the matching rule to the Firestore ruleset as an explicit deliverable in Subtask 3.

**M6 — Specify date-keyed merge, not whole-document replace, for weight sync.** `downloadUserData` must union remote and local entries keyed on `date`, resolving collisions by the later `updatedAtMillis`, and `uploadUserData` must publish the merged result. Add `weightList` to **both** `isNewUidEmpty` (`:723`) and `hasLegacyData` (`:744`). Serialize `date` as an epoch-day `Long` for consistency with `Converters`, not as `date.toString()` (the `rep_maxes` payload at `:646` uses `toString()`; do not propagate that inconsistency into a field used as a merge key).

**M7 — Pin the SMA-7 contract in the plan text, with an explicit gap policy.** Mandate: window = the closed calendar interval `[t−6, t]`; `N` = the count of *logged* entries within that window; emit no SMA point where `N < 3`; never interpolate missing days into the raw series. State this as a testable invariant and add table-driven unit tests for the sparse, single-entry, and leading-edge cases. Re-express the ±15% outlier check against this defined average and specify its behavior when no average exists.

**M8 — Give the chart a temporal X axis and equal-length series, or budget the work.** Either (a) extend `ChartCanvas` with an optional numeric X position per point so date gaps render proportionally and unequal-length series align correctly, and add the `pointerInput` tap-to-inspect the UX requires — an explicit change to `LineChart.kt` that must appear in the Component Impact Table and gain its own subtask; or (b) formally downgrade Phase 1's "Interactive" chart and BDD Scenario 1's "chart animates" to match what the existing component does, and require `WeightAnalytics` to emit an SMA series **padded to exactly the same length and date ordering as the raw series** (index-parallel) with a unit test asserting `raw.size == sma.size`. Option (b) is acceptable for v1; leaving the contradiction unresolved is not. Additionally, decimate to ≤ 120 rendered points for the `1Y`/`All` timeframes (V5).

**M9 — Fix the `when` exhaustiveness and the gating in `ProgressScreen`.** Replace `else ->` with an explicit `ProgressMode.BY_EXERCISE ->` branch so the compiler enforces the new case (C9). Render the chip row and the mode dispatch *above* the `exercises.isEmpty()` guard, scope the empty state to the selected mode, add the "Body weight" chip unconditionally, and promote `progressMode` to `rememberSaveable`.

**M10 — Define one canonical validation predicate in kg.** Validate `20.0 ≤ weightKg ≤ 350.0` on the converted canonical value only; derive the displayed imperial bounds from it (`44.1 … 771.6 lbs`) rather than hard-coding a second, inconsistent pair. Fix the conversion factor and rounding contract explicitly: round only at the display boundary, never round-trip through the stored value, and persist full `Double` precision.

**M11 — Relocate `WeightAnalytics` and preserve precision.** Move to `data/analytics/WeightAnalytics.kt` (or `data/`, matching the existing layering) and return `Double`, not `Float`.

**M12 — Sequence the subtasks to match the real dependency graph.** Subtask 1 cannot be verified until M2 lands, so schema-export enablement must precede it as Subtask 0. `BackupData`/CSV/`SEED_VERSION` (M4) belongs in Subtask 1 alongside the schema, not deferred into the demo-data subtask, because `importSnapshot` correctness is a data-integrity property of the schema change itself.

### 🏁 Verdict

The plan is directionally correct — the feature is well-scoped from a product standpoint, the entity model is close, and Review Iteration 1 caught the migration type mismatch and the `ProgressScreen` gating. But it is not implementable as written. Its single most emphasized technical remedy (`@Upsert`) silently discards data against the schema it proposes (**C1**); its central migration cannot be tested because schema export is disabled and `room-testing` is absent (**C2**); the chart it approves for reuse cannot express dates or align series of unequal length, making two stated acceptance criteria unpassable (**C3**); its demo-data instruction names a function that does not exist and omits the `SEED_VERSION` gate that governs whether the seed ever runs (**C4**); and it is entirely silent on `BackupData`/`BackupCsv`, where the omission does not merely lose data but leaks one profile's weight history into another's restore (**C5**). The Firestore path is stated incorrectly in three places including a BDD assertion (**C6**), and the sync model is a whole-document overwrite that destroys multi-device history for exactly the kind of append-only data this feature produces (**C7**).

`VERDICT: DISAGREED`

---

## 🔍 Review Iteration 2: Gemini Response & Full Architectural Concession Matrix (Round 2)

- **Date / Author:** 2026-09-07 | Antigravity AI Architect (Gemini)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ `efd0450`
- **Context:** Formal response to Claude Review Iteration 1. All 10 critiques (C1–C10), 12 vulnerabilities (V1–V12), and 12 mandatory safeguards (M1–M12) have been thoroughly verified against the codebase and are **fully conceded and incorporated without exception**.

---

### 1. Architectural Concession & Concrete Design Decision Matrix

| Finding / Mandatory Safeguard | Severity | Gemini Assessment | Architectural Concession & Concrete Implementation |
| :--- | :--- | :--- | :--- |
| **M1: Surrogate PK breaks `@Upsert` silently (C1, V8)** | 🔴 **Blocker** | **FULL CONCESSION** | **Adopt natural `date` primary key.** Eliminate `id: Long`. Define `@Entity(tableName = "weight_entries") data class WeightEntry(@PrimaryKey val date: LocalDate, val weightKg: Double, val notes: String = "", val updatedAtMillis: Long)`. `@Upsert` fallback `UPDATE ... WHERE date = ?` is now 100% correct, no secondary index needed, eliminates identity-hash traps, and gives a stable cross-device key for free. |
| **M2: Migration unverified; schema export disabled (C2)** | 🔴 **Blocker** | **FULL CONCESSION** | **Enable schema export & instrumented migration verification.**<br>1. In `app/build.gradle.kts`, configure KSP argument: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.<br>2. Set `exportSchema = true` in `AppDatabase.kt`.<br>3. Add `androidx-room-testing` to `gradle/libs.versions.toml`.<br>4. Write `AppDatabaseMigrationTest.kt` in `app/src/androidTest/` using `MigrationTestHelper` opening a v5 DB, executing `MIGRATION_5_6`, and asserting table integrity and seed read/write. |
| **M3: Builder names & migration registration (M3)** | 🔴 **Blocker** | **FULL CONCESSION** | Register `MIGRATION_5_6` in both `AppDatabase.build(context)` (`AppDatabase.kt:181`) and `AppDatabase.demo(context)` (`AppDatabase.kt:136`). |
| **M4: `BackupData`, `BackupCsv` & `SEED_VERSION` missing (C4, C5)** | 🔴 **Blocker** | **FULL CONCESSION** | **Integrate snapshot and backup lifecycle.**<br>1. Add `weightEntries: List<WeightEntry> = emptyList()` to `BackupData` (`Backup.kt:16`).<br>2. Add `db.weightDao().deleteAll()` to `Repository.importSnapshot()` delete phase before reinserting (strictly preventing cross-profile leaks and demo accumulation).<br>3. In `BackupCsv.kt`, implement `#weightEntries` CSV section encoding/decoding and bump file header to `#crosstraining-backup-v3`.<br>4. In `DemoData.kt`, populate `weightEntries` in `DemoData.Builder.build()` and bump `DemoData.SEED_VERSION` from `3` to `4` (`DemoData.kt:26`) so existing installs execute the seed. |
| **M5: Firestore path & security rules (C6)** | 🟠 **Major** | **FULL CONCESSION** | Correct path to `environments/{currentEnv}/users/{uid}/data/weight_entries` across all diagrams, tables, and BDD assertions. Deliver Firestore security rule for `data/weight_entries` in Subtask 3. |
| **M6: Cloud sync whole-document overwrite vs date-keyed merge (C7, C8)** | 🔴 **Blocker** | **FULL CONCESSION** | **Implement date-keyed merge in `UserCloudSyncManager`.**<br>1. In `downloadUserData`: Merge remote weight entries with local Room records keyed on `date`, resolving collisions using `updatedAtMillis` (last-write-wins). Serialize/deserialize `date` as epoch-day `Long`.<br>2. In `uploadUserData`: Upload the merged union back to Firestore.<br>3. Add `weightList` to both `isNewUidEmpty` (`:723`) and `hasLegacyData` (`:744`) to prevent false legacy fallbacks. |
| **M7: SMA-7 mathematical contract & gap policy (V1, V2)** | 🟠 **Major** | **FULL CONCESSION** | **Pin exact mathematical contract in `WeightAnalytics`:**<br>- For any target date $t$, window is closed calendar interval $[t - 6, t]$.<br>- $N$ is the count of *logged entries* within that 7-day interval.<br>- Emit SMA point if and only if $N \ge 3$; otherwise omit.<br>- Do not synthesize or interpolate phantom daily entries into raw series.<br>- Outlier warning evaluates $|W(t) - \text{SMA}_7(t)| / \text{SMA}_7(t) > 0.15$ only when $N \ge 3$. |
| **M8: Chart ordinality & index-parallel series alignment (C3, V5)** | 🔴 **Blocker** | **FULL CONCESSION** | **Adopt index-parallel aligned series & decimation:**<br>1. Downgrade unsupported interactive/animation claims to match Canvas `LineChart.kt`.<br>2. `WeightAnalytics` emits an SMA series strictly index-parallel to raw entries (same size, matching dates), passing `Float.NaN` or omitting lines where $N < 3$, with a unit test asserting `raw.size == sma.size`.<br>3. Decimate points to $\le 120$ points for 1Y/All timeframes. |
| **M9: `ProgressScreen` exhaustiveness & gating (C9, C10)** | 🟠 **Major** | **FULL CONCESSION** | Replace `else ->` in `ProgressScreen.kt:when(progressMode)` with explicit `ProgressMode.BY_EXERCISE ->`. Hoist `FilterChip` row above `exercises.isEmpty()`. Add "Body weight" chip unconditionally. Promote `progressMode` to `rememberSaveable`. |
| **M10: Canonical validation in kg & precision (V3)** | 🟡 **Minor** | **FULL CONCESSION** | Enforce single canonical validation $20.0 \le \text{weightKg} \le 350.0$. Imperial values converted to kg before validation; displayed bounds derived ($44.1 \dots 771.6\text{ lbs}$). Full `Double` precision preserved; round only for UI display. |
| **M11: Relocate `WeightAnalytics` to data layer (V9)** | 🟡 **Minor** | **FULL CONCESSION** | Relocate to `data/analytics/WeightAnalytics.kt` and operate on `Double`. |
| **M12: Subtask resequencing (M12)** | 🟠 **Major** | **FULL CONCESSION** | Sequence: Subtask 0 (Schema export & verification harness) $\to$ Subtask 1 (Room Schema, BackupData, BackupCsv & SEED_VERSION 4) $\to$ Subtask 2 (WeightAnalytics & DemoData) $\to$ Subtask 3 (Cloud Sync date-keyed merge) $\to$ Subtask 4 (ProgressScreen UI & BottomSheet). |

---

*(Intermediate plan superseded by Claude Review Iteration 2 and Gemini Review Iteration 3 below)*


---

## 🏛️ Claude Review Iteration 2

- **Date / Author:** 2026-09-07 | Claude (Principal Architect) — Round 2 Architectural Cross-Review
- **Baseline:** `main` @ `efd0450`
- **Scope of this round:** Review Iteration 2 concession matrix + the consolidated Final Decision Plan, re-verified line-by-line against `LineChart.kt` (159 L), `Backup.kt` (222 L), `Repository.kt:345-384`, `AppDatabase.kt:32-45,132,188`, `DemoData.kt:25-71`, `UserCloudSyncManager.kt:441-475,700-760`, `ProgressScreen.kt:90-185`, `DataModeManager.kt:26-30`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/androidTest/**`, and a repository-wide search for `*.rules` / `firebase.json`.

### ⚖️ Evaluation of Concessions & Refined Architecture

Ten of the twelve safeguards are conceded correctly, and — unusually — the concessions are technically accurate rather than merely agreeable. Verified individually:

**M1 (natural `date` primary key) — ACCEPTED, and it is the load-bearing fix.** With `@PrimaryKey val date: LocalDate`, Room's `EntityUpsertionAdapter` fallback (`UPDATE … WHERE date = ?`) matches the row it aborted on, so C1's silent write-drop is genuinely dissolved rather than papered over. It simultaneously retires V8 (no secondary unique index, so no identity-hash trap), neutralises V4 (a duplicated travel "today" is now an idempotent overwrite, not a constraint violation), and supplies the cross-device merge key M6 needs. Renaming `createdAtMillis` → `updatedAtMillis` gives V7's dead column a real consumer. The DDL in the Component Impact Table matches Room's generated form for this entity, including `'notes' TEXT NOT NULL DEFAULT ''`, which restores the schema convention V8 flagged.

**M2/M3 (schema export + migration registration) — ACCEPTED.** Ground truth reconfirmed: `AppDatabase.kt:44-45` is `version = 5, exportSchema = false`; there is no `app/schemas/`; `libs.versions.toml` has `room = "2.6.1"` with runtime/ktx/compiler but no `room-testing`; `app/build.gradle.kts` has the KSP plugin but no `room.schemaLocation` argument. `app/src/androidTest/` exists with four Compose tests, so the instrumented source set is real and `MigrationTestHelper` is viable once the artifact is added. The builder names are now correct — `build(context)` and `demo(context)`, with `addMigrations(...)` at `:188` and `:132` respectively. (See Ma2 below for the one sequencing trap this row still contains.)

**M4 (`BackupData` / `BackupCsv` / `SEED_VERSION`) — ACCEPTED, and the compatibility reasoning holds.** Verified: `BackupData` (`Backup.kt:16-24`) carries seven lists; `importSnapshot()` (`Repository.kt:365-383`) clears exactly those seven tables inside `withDatabaseTransaction` before reinserting, so adding `weightDao().deleteAll()` there is precisely what closes C5's cross-profile leak and the demo re-seed accumulation. `BackupCsv.decode` (`:124-150`) dispatches on `#section` names and treats any unrecognised section as inert, and its only header assertion is `name.startsWith("crosstraining-backup")` (`:141`) — so bumping the emitted header to `#crosstraining-backup-v3` is safe in **both** directions: v3 readers accept v2 files and v2 readers accept v3 files (ignoring the unknown `#weightEntries` block). `DemoData.SEED_VERSION = 3` (`DemoData.kt:25`) confirmed; the bump to `4` is what actually makes existing demo installs re-seed.

**M5 (Firestore path) — ACCEPTED for the path itself.** `environments/{currentEnv}/users/{uid}/data/weight_entries` is correct against `userDoc()`, and the correction has been propagated to the Component Impact Table, the data-flow diagram, and BDD Scenario 5. The security-rule half of M5 has a ground-truth problem — see Ma3.

**M6 (dual-read predicates) — ACCEPTED.** `isNewUidEmpty` and `hasLegacyData` are confirmed as five-list conjunctions/disjunctions built from `exercises/routines/sessions/cycle_goals/rep_maxes`; adding `weightList` to both closes C8's regression path exactly. Epoch-day `Long` serialisation for the merge key is the right call. The *merge* half of M6 is where a new blocker surfaces — see B2.

**M7 (SMA-7 contract) — ACCEPTED and now fully determined.** Window `[t−6, t]`, `N` = count of logged entries in that window, emit iff `N ≥ 3`, no phantom interpolation. This closes V1 completely: the previously ambiguous readings now yield one number on any given dataset, and the `N ≥ 3` floor stops the smoothed line from collapsing onto the raw line at the leading edge. Re-expressing the outlier check as `|W(t) − SMA₇(t)| / SMA₇(t) > 0.15` gated on `N ≥ 3` resolves V2's undefined-baseline and first-entry cases. (The 15 % threshold remains coarse relative to the 20–350 kg range gate, but it is now well-defined and non-blocking.)

**M9 (`ProgressScreen`) — ACCEPTED, all four sub-fixes verified as necessary.** Ground truth at `:98` is `remember { mutableStateOf(ProgressMode.BY_EXERCISE) }`, at `:118-121` the `exercises.isEmpty()` early `return@Scaffold`, at `:135/:143` the `routines.isNotEmpty()` / `cycles.isNotEmpty()` chip gates, and at `:173` the `else ->` catch-all. The plan now addresses each one specifically.

**M10 / M11 / M12 — ACCEPTED.** Single canonical kg predicate with derived display bounds retires V3's asymmetry and the round-trip drift; `data/analytics/WeightAnalytics.kt` returning `Double` matches the existing `data/` ↔ `ui/` split and retires V9; Subtask 0 correctly precedes the schema work.

**M8 — CONCEDED IN PRINCIPLE, BUT THE STATED MECHANISM DOES NOT COMPILE AGAINST THE COMPONENT.** This is the one concession that does not survive contact with the code. Detailed below as B1.

### 🚨 Remaining or Newly Introduced Concerns

**B1. The M8 SMA sentinel is unrepresentable in `ChartSeries`, and the `Float.NaN` variant blanks the entire chart. (BLOCKING)**

`ChartPoint` is `data class ChartPoint(val label: String, val value: Float)` (`LineChart.kt:27`) — the value is a **non-nullable `Float`**. The M8 concession row offers two mechanisms and the Component Impact Table specifies a third; all three are invalid:

- **`Float.NaN` is catastrophic, not degraded.** `ChartCanvas` computes `values = visible.flatMap { s -> s.points.map { it.value } }` then `minV = values.min()` / `maxV = values.max()` (`LineChart.kt:104-106`). Kotlin's `min`/`max` over `Float` delegate to `Math.min`/`Math.max`, which **propagate NaN**. A single NaN anywhere in the SMA series makes `minV`, `maxV` and `range` all NaN, therefore `yAt(v) = topPad + plotH * (1f - (v - minV) / range)` returns NaN for **every point of every series**. Skia discards NaN-offset geometry, so the raw weight line disappears along with the trendline, and the three Y-axis labels render the literal text `"NaN"` (`fmt()` at `:30` fails its `v % 1f == 0f` test and falls through to `String.format("%.1f", NaN)`). The failure is not confined to the leading edge — it is total, and it is exactly the first-run state, because a new athlete's first two entries always produce `N < 3`.
- **"Omitting lines where `N < 3`" reintroduces C3 verbatim.** `xCount = visible.maxOf { it.points.size }` (`:108`) and `xAt(i) = leftPad + plotW * i / (xCount - 1)` (`:110-112`) normalise each series independently across the full plot width. A shorter SMA list is stretched to span the same X range as the raw list — the precise silent misalignment M8 exists to prevent.
- **`List<Double?>` (the signature in the Component Impact Table) has no `ChartSeries` representation at all.** There is no defined lowering from a null-holed analytics result to a non-nullable `ChartPoint` list, so the plan's own two artifacts contradict each other.

This must be resolved with a concrete, budgeted decision, and every option is a change to shared code: either (a) widen `ChartPoint.value` to `Float?` and teach `ChartCanvas` to exclude nulls from the min/max scan and break the polyline across gaps — an explicit `LineChart.kt` change that needs a Component Impact Table row, its own subtask, and regression coverage for the existing `ExerciseProgress`/`RoutineProgress` call sites; or (b) trim **both** series to the first index at which `N ≥ 3`, preserving equal length at the cost of hiding the athlete's first days of data, and state that trade-off in the BDD. The current text picks none of them.

**B2. Date-keyed union merge with no tombstone makes deletion non-durable — deletes silently resurrect. (BLOCKING)**

This one originates in my own M6 wording, which specified "union remote and local entries keyed on `date`" without a deletion contract; the concession adopted it faithfully and the consolidated plan inherits the hole. `WeightEntry` as specified — `(date, weightKg, notes, updatedAtMillis)` — carries **no representation of a deleted date**. The consequence is user-visible on a single device:

1. Athlete deletes the entry for date D; `deleteWeightEntryByDate(D)` removes the Room row.
2. The Firestore document still contains D.
3. The next `downloadUserData` unions remote ∪ local keyed on `date`. D is present remotely and absent locally, so it is **restored**.
4. The next `uploadUserData` republishes the merged set, re-cementing D.

The deleted weigh-in reappears with no user action, and BDD Scenario 4's "the entry is removed from the database by primary key date D" is false past the next sync tick. Multi-device makes it permanent: device B never learns of the deletion at all. This is strictly *worse* than the whole-document `set()` overwrite (`UserCloudSyncManager.kt:471`) that M6 replaced, which — for all its faults under C7 — at least propagated deletions. It also subsumes V6, which the concession matrix never answered: the 4-second undo window is moot when the delete does not stick regardless.

Required, and it must land in **Subtask 1**, not be retrofitted: add a tombstone field (`deletedAtMillis: Long? = null`, or an `isDeleted` flag paired with `updatedAtMillis`) to the entity, to the `MIGRATION_5_6` DDL, to the `#weightEntries` CSV section, and to the merge resolver; filter tombstones out of every read query, the analytics input, and the history list; and state a purge horizon so the array does not grow without bound. Because it changes the entity and the DDL, deferring it past Subtask 1 means a second migration.

**Ma1. Decimation and SMA ordering is unspecified, and two of the three possible orderings are wrong. (Major)**

`decimatePoints(entries: List<WeightEntry>, maxPoints: Int = 120): List<WeightEntry>` takes and returns raw entries only, with no stated position in the pipeline:

- *Decimate → SMA*: the `[t−6, t]` window and the `N ≥ 3` floor are then evaluated over a thinned series. On a three-year "All" view decimated to 120 points, consecutive retained samples are ~9 days apart, so almost no 7-day window ever holds 3 logged entries and the SMA series is empty everywhere — the trendline vanishes precisely on the timeframes it is most useful.
- *SMA → decimate raw only*: lengths diverge, and C3/B1 recur verbatim.
- *SMA → decimate both against one shared index selection*: correct, and the only correct ordering — but the plan's signature cannot express it, since it returns a single `List<WeightEntry>`.

Specify the third explicitly (one index-selection function applied to the paired index-aligned arrays), and note that the mandated `assert raw.size == sma.size` unit test runs **pre-decimation** and would pass while the rendered 1Y/All chart is misaligned; the assertion must be repeated on the post-decimation output.

**Ma2. The Component Impact Table prescribes an edit that destroys the ability to generate the v5 baseline schema. (Major)**

`MigrationTestHelper.createDatabase(TEST_DB, 5)` requires `app/schemas/…AppDatabase/5.json`. Because `exportSchema` has always been `false`, that file has never existed, and Room only ever exports the schema of the version currently declared in source — so `5.json` can only be produced by building **while `@Database(version = 5)` is still in effect**. Subtask 0 is sequenced correctly, but the `AppDatabase.kt` row of the Component Impact Table reads "Set `exportSchema = true` **and** increment version to `6`" as a single instruction. Executed as one edit — the natural reading of one table cell — only `6.json` is generated, and the migration test that is the entire justification for M2 cannot be written without reverting the source tree to regenerate the baseline. Split it: Subtask 0 = enable export at v5, build, **commit `5.json`**, and assert its presence; Subtask 1 = bump to 6 and commit `6.json`. Also note `androidTest` currently declares only `ui-test-junit4`, `test.ext.junit` and `espresso-core` — `androidx.test:runner` should be added alongside `room-testing` for `MigrationTestHelper`.

**Ma3. `firestore.rules` does not exist in this repository. (Major)**

The Component Impact Table lists `firestore.rules` at the repository root, and Subtask 3 makes "Update `firestore.rules`" a deliverable. A repository-wide search finds **no `.rules` file anywhere in the tree and no `firebase.json`** — the Firestore ruleset for this project is managed outside this repo (console or separate infra). As written, Subtask 3 contains an acceptance condition that cannot be satisfied here, and the failure mode is silent in exactly the way C6 described: an implementer who finds no rules file reasonably concludes none is needed, the `data/weight_entries` write returns `PERMISSION_DENIED`, and `uploadUserData`'s `runCatching`/`supervisorScope` degrades it to a partial sync rather than a visible error. Restate the deliverable as an explicit out-of-repo action with a named owner and a manual post-deploy verification step (write one entry on a real account, confirm the document exists at `environments/{env}/users/{uid}/data/weight_entries`).

**Minor gaps (non-blocking, but should be closed in the plan text):**

- **`weightUnit` has no persistence owner.** `AppViewModel.weightUnit: StateFlow<String>` is specified, but no Component Impact Table row covers persisting the choice. The app's only preference store is the `crosstraining-prefs` `SharedPreferences` instantiated inside `DataModeManager` (`:30`); as specified, the kg/lbs selection is in-memory and resets on every process death.
- **`WeightSummaryCard.kt` appears only in Subtask 4** — no Component Impact Table row, no path, no contract.
- **V12 remains partly open.** The data-flow diagram shows "[Log Weight Action / FAB]", but the `ProgressScreen.kt` row lists no `floatingActionButton` change, and the `Scaffold` (`:100`) declares none while already consuming `outerPadding.calculateBottomPadding()` for the bottom nav. The entry point and its inset handling are still unbudgeted.
- **Repository row is internally inconsistent.** It calls `db.weightDao().getAllEntriesOnce()`, but `Repository` reaches DAOs as injected properties (`repMaxDao`, `cycleDao`, …), not via `db.`; and `getAllEntriesOnce()` is absent from the `WeightDao` row's method list.
- **V11 (1 MiB Firestore document ceiling) is still unrecorded.** Daily entries reach it only after ~15 years, so it is not urgent — but the bound and its retention consequence should be written down rather than left implicit, especially once tombstones (B2) add rows that are never removed.

### 🏁 Verdict

Round 2 is a substantial and honest advance. Every blocker I raised in Round 1 that concerned **storage and data integrity** is now correctly closed: the natural `date` primary key genuinely dissolves the `@Upsert` silent-drop (C1) rather than working around it; the schema-export harness makes the migration verifiable (C2); the `BackupData` / `BackupCsv` / `SEED_VERSION` integration closes both the silent-loss and the cross-profile-leak halves of C4/C5, and the CSV header bump is bidirectionally compatible as claimed; the Firestore path and both dual-read predicates are now correct (C6, C8); and the SMA-7 contract, the validation predicate, the analytics layering and the `ProgressScreen` gating are all pinned precisely (V1–V3, V9, C9, C10). The subtask graph now matches the real dependency order.

Two blockers stand. The M8 concession is the only one that was not verified against the component it constrains: `ChartPoint.value` is a non-nullable `Float`, so `Float.NaN` does not degrade the trendline — it propagates through `values.min()`/`max()` and blanks the entire chart on every athlete's first two entries, while the alternative of omitting points restores the exact index-stretch misalignment M8 was written to prevent, and the Component Impact Table's own `List<Double?>` signature can lower to neither (**B1**). Separately, conceding date-keyed union merge without a tombstone — a gap my own M6 wording introduced and this round inherited — makes deletion non-durable: a deleted weigh-in is restored by the next download and re-published by the next upload, falsifying BDD Scenario 4 on a single device and permanently on two (**B2**). Both change artifacts owned by Subtask 1 and Subtask 4 respectively, so neither can be deferred without a second migration or a second pass over the chart component. Ma1's unspecified decimation/SMA ordering silently empties the trendline on the 1Y and All timeframes, Ma2's bundled `exportSchema`/version edit forecloses the v5 baseline that M2 depends on, and Ma3 names a `firestore.rules` file that does not exist in this repository.

`VERDICT: DISAGREED`

---

## 🔍 Review Iteration 3: Gemini Response & Definitive Architectural Alignment (Round 3)

- **Date / Author:** 2026-09-07 | Antigravity AI Architect (Gemini)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ `efd0450`
- **Context:** Formal resolution of Claude Review Iteration 2. Both remaining blockers (**B1**, **B2**), all three major findings (**Ma1**, **Ma2**, **Ma3**), and the four minor gaps are evaluated, accepted, and resolved with concrete code contracts.

---

### 1. Definitive Resolution of Round 2 Blockers & Majors

| Finding | Severity | Resolution & Concrete Architecture Contract |
| :--- | :--- | :--- |
| **B1: `LineChart.kt` NaN failure & misalignment** | 🔴 **Blocker** | **Widen `ChartPoint.value` to `Float? = null` & gap-aware `ChartCanvas`.**<br>1. In `LineChart.kt:27`, change definition to: `data class ChartPoint(val label: String, val value: Float? = null)`. Existing call sites passing non-null values remain 100% source- and binary-compatible.<br>2. In `ChartCanvas` (`LineChart.kt:105-136`):<br>   - `val values = visible.flatMap { s -> s.points.mapNotNull { it.value } }`<br>   - If `values.isEmpty()`, draw empty axes and return early (zero NaN propagation).<br>   - `var minV = values.min(); var maxV = values.max()` (pure valid floats).<br>   - During polyline drawing, maintain `lastPoint: Offset?`. When `p.value != null`, draw line from `lastPoint` and set `lastPoint = currentOffset`. When `p.value == null`, set `lastPoint = null` (polyline breaks cleanly across gaps with no stray lines).<br>   - Draw circle dots only when `p.value != null`.<br>3. In `WeightAnalytics.kt`: Generate an SMA-7 series with **exact same length and labels as raw series**. For indices where $N < 3$ in $[t-6, t]$, emit `ChartPoint(label, value = null)`. Both series share `xCount = raw.size`, perfectly preserving temporal alignment with zero visual squishing and zero NaN crashes. |
| **B2: Union merge without tombstone resurrects deletes** | 🔴 **Blocker** | **Add `deletedAtMillis` tombstone field to entity, schema, CSV & sync merge.**<br>1. Entity: `@Entity(tableName = "weight_entries") data class WeightEntry(@PrimaryKey val date: LocalDate, val weightKg: Double, val notes: String = "", val updatedAtMillis: Long, val deletedAtMillis: Long? = null)`.<br>2. DDL in `MIGRATION_5_6`: `CREATE TABLE IF NOT EXISTS 'weight_entries' ('date' INTEGER NOT NULL, 'weightKg' REAL NOT NULL, 'notes' TEXT NOT NULL DEFAULT '', 'updatedAtMillis' INTEGER NOT NULL, 'deletedAtMillis' INTEGER, PRIMARY KEY('date'))`.<br>3. `WeightDao`: Add soft delete: `@Query("UPDATE weight_entries SET deletedAtMillis = :deletedAt, updatedAtMillis = :deletedAt WHERE date = :date") suspend fun markDeleted(date: LocalDate, deletedAt: Long = System.currentTimeMillis())`. Read queries filter: `WHERE deletedAtMillis IS NULL`.<br>4. Cloud merge resolver in `UserCloudSyncManager`: For each date $D$, compare `maxOf(local.updatedAtMillis, local.deletedAtMillis ?: 0L)` vs remote equivalent. If winning entry has `deletedAtMillis != null`, it persists as a tombstone locally and remotely. Deletions stick permanently across devices and syncs.<br>5. Purge policy: Tombstones older than 90 days are purged during periodic Room maintenance. |
| **Ma1: Decimation and SMA ordering** | 🟠 **Major** | **Mandate pipeline: Full-window SMA $\to$ Paired index decimation.**<br>1. Query active entries in timeframe $[T_{\text{start}}, T_{\text{end}}]$.<br>2. Calculate SMA-7 over the full chronological list ($N \ge 3$ in $[t-6, t]$).<br>3. If count $> 120$, select a uniform stride of shared indices and decimate both the raw and SMA lists synchronously (`decimatePairedSeries`).<br>4. Unit tests assert `raw.size == sma.size` both before and after decimation. |
| **Ma2: Generating v5 baseline schema** | 🟠 **Major** | **Split into explicit Subtask 0 (v5 export) and Subtask 1 (v6 bump).**<br>1. Subtask 0: Set `exportSchema = true` in `AppDatabase.kt` while still at `version = 5`. Configure `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Add `androidx-room-testing` and `androidx.test:runner` to `libs.versions.toml`. Build project to generate and commit `app/schemas/.../5.json`.<br>2. Subtask 1: Bump to `version = 6`, add `MIGRATION_5_6`, build to generate `6.json`, and run `AppDatabaseMigrationTest.kt` verifying migration from `5.json` to `6.json`. |
| **Ma3: External Firestore rules handling** | 🟠 **Major** | **Acknowledge external rules management & provide staging verification.**<br>Remove bogus local `firestore.rules` repository file from deliverables. Provide the exact rule definition in `docs/draft-requisites/firestore-rules.md` for Firebase Console deployment:<br>`match /environments/{env}/users/{userId}/data/weight_entries { allow read, write: if request.auth != null && request.auth.uid == userId; }`<br>Include manual staging verification on real authenticated account in Subtask 3. |
| **Minor Gaps** | 🟡 **Minor** | - **Unit persistence:** In `DataModeManager.kt`, persist `KEY_WEIGHT_UNIT` in `crosstraining-prefs` `SharedPreferences`.<br>- **`WeightSummaryCard.kt`:** Explicitly added to Component Impact Table under `ui/screens/weight/`.<br>- **FAB entry point:** Added to `ProgressScreen.kt` `Scaffold` (`floatingActionButton = { if (progressMode == ProgressMode.BODY_WEIGHT) ... }`) with `Modifier.padding(bottom = outerPadding.calculateBottomPadding())`.<br>- **Repository DAOs:** Injected property `private val weightDao = db.weightDao()`.<br>- **Firestore 1 MiB bound:** Recorded (~60 B/entry $\implies$ 5,000 entries = ~300 KB $\ll$ 1 MiB limit). |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete using CrossTraining
I want to log and track my daily body weight, visualize trends with index-parallel 7-day rolling averages and percentage changes, and back up my weight history to the cloud with tombstone-durable multi-device synchronization
So that I can correlate my body mass fluctuations with my strength performance and conditioning cycles over time without data loss, chart glitches, or UI friction.
```

---

### Architecture & Data Flow

```
+---------------------------------------------------------------------------------------------------------------+
|                                                ProgressScreen                                                 |
|                                                                                                               |
|  [Filter Chips: By Exercise | By Routine | Cycle Goals | Body Weight (Unconditional)]                         |
|                                         │                                                                     |
|                                         ▼ (progressMode == BODY_WEIGHT via rememberSaveable)                  |
|  +─────────────────────────────────────────────────────────────────────────────────────────────────────────+  |
|  |  WeightOverviewContent                                                                                  |  |
|  |  ├── WeightSummaryCard (Current Weight, Delta %, SMA-7, Unit Toggle [kg/lbs])                           |  |
|  |  ├── Timeframe Chips (7D | 30D | 90D | 1Y | All - Decimated <= 120 points)                              |  |
|  |  ├── MultiLineChart (Canvas: Daily Points [Accent] + Gap-Aware Index-Parallel SMA-7 Trendline [Primary]) |  |
|  |  └── Chronological Weight History List with Soft-Delete Actions                                         |  |
|  +─────────────────────────────────────────────────────────────────────────────────────────────────────────+  |
|                                         │                                                                     |
|                                         ├──► [Floating Action Button: "Log Weight"]                           |
|                                         │         │                                                           |
|                                         │         ▼                                                           |
|                                         │    WeightEntryBottomSheet                                           |
|                                         │    - Numeric Decimal Input (Pre-fills latest)                       |
|                                         │    - Date Selector (Today | Yesterday | Custom)                     |
|                                         │    - Canonical kg validation [20.0 - 350.0 kg]                      |
|                                         ▼                                                                     |
|  +─────────────────────────────────────────────────────────────────────────────────────────────────────────+  |
|  |  AppViewModel / DataModeManager                                                                         |  |
|  |  - weightEntries: StateFlow<List<WeightEntry>> (active, non-tombstone)                                  |  |
|  |  - weightUnit: StateFlow<String> (persisted in crosstraining-prefs)                                       |  |
|  |  - saveWeightEntry(weightKg, date, notes) ──► Repository.saveWeightEntry(...)                           |  |
|  |  - deleteWeightEntry(date) ───────────────► Repository.deleteWeightEntry(date) [Tombstone]              |  |
|  +─────────────────────────────────────────────────────────────────────────────────────────────────────────+  |
|                                         │                                                                     |
|                  ┌──────────────────────┴──────────────────────┐                                              |
|                  ▼                                             ▼                                              |
|       [Local Database (Room)]                       [Cloud Sync (Firestore)]                                  |
|       - Table: weight_entries                       - environments/{env}/users/{uid}/data/                    |
|       - Natural @PrimaryKey: date (INTEGER)           weight_entries                                          |
|       - Tombstone: deletedAtMillis (INTEGER)        - Tombstone-aware merge (last-write-wins)                 |
|       - MIGRATION_5_6 in build() & demo()           - Protected in supervisorScope                            |
|       - BackupData & BackupCsv integration          - Verified against Firestore security rule                |
+---------------------------------------------------------------------------------------------------------------+
```

---

### BDD Acceptance Criteria

#### Scenario 1: Log Daily Body Weight (Metric & Imperial)
```gherkin
Given an athlete is on the Progress screen under the "Body weight" tab
When they tap the "Log weight" FAB and enter "78.4" with unit "kg" for Today
And tap "Save entry"
Then the entry is persisted to the local database with natural primary key date set to Today
And the bottom sheet dismisses
And the summary card immediately updates the current weight to "78.4 kg"
And the chart displays the new point in the daily series
```

#### Scenario 2: Overwrite / Update Same-Day Weigh-In (Natural PK Idempotence)
```gherkin
Given an athlete already logged "78.4 kg" for Today
When they open the "Log weight" sheet for Today and update the value to "78.1 kg"
And tap "Save entry"
Then Room executes an @Upsert matching on the natural primary key date
And the existing record for Today is updated in-place without silent drops or surrogate key churn
And the updated value "78.1 kg" is reflected across the summary card, chart, and history list
```

#### Scenario 3: Gap-Aware Index-Parallel 7-Day Rolling Average
```gherkin
Given an athlete has recorded daily weight entries across 45 consecutive days
When they select the "30D" timeframe filter
Then the chart bounds the view to entries within the last 30 calendar days
And renders a dual-series line chart with gap-aware index-parallel points:
  | Series 1 | Raw daily weigh-ins plotted as discrete connected dots |
  | Series 2 | Index-parallel SMA-7 trendline where N >= 3 in [t-6, t], emitting null where N < 3 |
And ChartCanvas renders no NaN labels and connects lines cleanly without drawing across null gaps
And displays the total delta and percentage change between the 30-day baseline and current weight
```

#### Scenario 4: Durable Deletion with Tombstone Preservation
```gherkin
Given an athlete views an entry for date D in the weight history list
When they tap the delete action on that entry
Then the entry is soft-deleted by setting deletedAtMillis = currentTime
And disappears immediately from the active UI and history list
And a snackbar appears: "Weight entry deleted" with an "Undo" action
And if Undo is tapped, the entry is restored with a fresh updatedAtMillis that supersedes the tombstone;
Otherwise, the deletion decision is durable and never resurrects across cloud syncs or devices
```

#### Scenario 5: Multi-Device Cloud Synchronization with Tombstone-Aware Merge
```gherkin
Given an authenticated athlete in Real Data mode with local weight entries and tombstones
When cloud backup executes
Then UserCloudSyncManager unions local and remote entries at environments/{currentEnv}/users/{uid}/data/weight_entries
And any date collisions are resolved by the later timestamp: maxOf(updatedAtMillis, deletedAtMillis ?: 0L)
And downloadUserData applies the winning records (including tombstones) to Room
And no deleted entries are resurrected and no offline logs are overwritten
```

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`libs.versions.toml`** | `gradle/libs.versions.toml` | - Add `androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }`.<br>- Add `androidx-test-runner = { group = "androidx.test", name = "runner", version = "1.6.2" }`. |
| **`build.gradle.kts`** | `app/build.gradle.kts` | - Add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.<br>- Add `androidTestImplementation(libs.androidx.room.testing)`.<br>- Add `androidTestImplementation(libs.androidx.test.runner)`. |
| **`AppDatabase.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt` | - Set `exportSchema = true`.<br>- Bump version from `5` to `6`.<br>- Register `WeightEntry::class`.<br>- Add `abstract fun weightDao(): WeightDao`.<br>- Add `MIGRATION_5_6`: `CREATE TABLE IF NOT EXISTS 'weight_entries' ('date' INTEGER NOT NULL, 'weightKg' REAL NOT NULL, 'notes' TEXT NOT NULL DEFAULT '', 'updatedAtMillis' INTEGER NOT NULL, 'deletedAtMillis' INTEGER, PRIMARY KEY('date'))`.<br>- Register `MIGRATION_5_6` in `build(context)` (`:188`) and `demo(context)` (`:132`). |
| **`AppDatabaseMigrationTest.kt`** | `app/src/androidTest/java/com/fractanomics/crosstraining/data/AppDatabaseMigrationTest.kt` | - **[NEW]** Instrumented migration test using `MigrationTestHelper` verifying migration from `5.json` to `6.json` with table validation and read/write integrity. |
| **`WeightEntry.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/model/WeightEntry.kt` | - **[NEW]** Entity: `@Entity(tableName = "weight_entries") data class WeightEntry(@PrimaryKey val date: LocalDate, val weightKg: Double, val notes: String = "", val updatedAtMillis: Long, val deletedAtMillis: Long? = null)`. |
| **`WeightDao.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/dao/WeightDao.kt` | - **[NEW]** Room DAO:<br>  - `@Upsert suspend fun upsert(entry: WeightEntry): Long`<br>  - `@Upsert suspend fun upsertAll(entries: List<WeightEntry>)`<br>  - `@Query("SELECT * FROM weight_entries WHERE deletedAtMillis IS NULL ORDER BY date DESC") fun getAllActiveEntries(): Flow<List<WeightEntry>>`<br>  - `@Query("SELECT * FROM weight_entries ORDER BY date DESC") suspend fun getAllEntriesIncludingTombstones(): List<WeightEntry>`<br>  - `@Query("UPDATE weight_entries SET deletedAtMillis = :deletedAt, updatedAtMillis = :deletedAt WHERE date = :date") suspend fun markDeleted(date: LocalDate, deletedAt: Long)`<br>  - `@Query("DELETE FROM weight_entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis") suspend fun purgeOldTombstones(cutoffMillis: Long)`<br>  - `@Query("DELETE FROM weight_entries") suspend fun deleteAll()`. |
| **`LineChart.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/components/LineChart.kt` | - Modify `data class ChartPoint(val label: String, val value: Float? = null)`.<br>- In `ChartCanvas`: mapNotNull valid values for min/max calculation, return early if empty, and connect polyline segments across non-null values without drawing lines across null gaps. |
| **`Backup.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Backup.kt` | - Add `weightEntries: List<WeightEntry> = emptyList()` to `BackupData`.<br>- Update `BackupCsv`: encode/decode `#weightEntries` section and bump header to `#crosstraining-backup-v3`. |
| **`Repository.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` | - Add injected `private val weightDao = db.weightDao()`.<br>- In `importSnapshot()`: Add `weightDao.deleteAll()` to clear phase, and `weightDao.upsertAll(data.weightEntries)` to insertion phase.<br>- In `exportSnapshot()`: Include `weightDao.getAllEntriesIncludingTombstones()`.<br>- Expose `getWeightEntries()`, `saveWeightEntry()`, `deleteWeightEntry(date)`, and `purgeOldWeightTombstones(cutoffMillis)`. |
| **`DemoData.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/DemoData.kt` | - Bump `SEED_VERSION` from `3` to `4`.<br>- In `DemoData.Builder.build()`: Populate `weightEntries` with 30-day realistic bodyweight progression. |
| **`DataModeManager.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/DataModeManager.kt` | - Add `getWeightUnit(): String` and `setWeightUnit(unit: String)` backed by `crosstraining-prefs` `SharedPreferences`. |
| **`WeightAnalytics.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/analytics/WeightAnalytics.kt` | - **[NEW]** Pure Kotlin domain analytics in `data/analytics/`:<br>  - `data class WeightSeriesPoint(val label: String, val rawValue: Double, val smaValue: Double?)`<br>  - `prepareChartSeries(entries: List<WeightEntry>, timeframe: Timeframe, maxPoints: Int = 120): List<WeightSeriesPoint>` (mapped to UI `ChartPoint` in Composable layer).<br>  - Calculates SMA-7 over full series with $N \ge 3$ in $[t-6, t]$, emitting `null` where $N < 3$.<br>  - Decimates paired series synchronously when count $> 120$.<br>  - Validates canonical $20.0 \le \text{weightKg} \le 350.0$. |
| **`UserCloudSyncManager.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt` | - Target path: `environments/{currentEnv}/users/{uid}/data/weight_entries`.<br>- Pre-flight: Call `repo.purgeOldWeightTombstones(cutoff)` alongside `cleanupDuplicateRoutines()`.<br>- In `uploadUserData`: Publish merged union of local and remote weight records including tombstones, with `isLocallyEmpty = payload.isEmpty()`.<br>- In `downloadUserData`: Merge remote and local weight records keyed on `date` using `maxOf(updatedAtMillis, deletedAtMillis ?: 0L)` tiebreaker. In legacy dual-read swap, reassign `weightList = legacyWeight`.<br>- Add `weightList` to both `isNewUidEmpty` and `hasLegacyData` predicates. |
| **`firestore-rules.md`** | `docs/draft-requisites/firestore-rules.md` | - **[NEW]** Document operational security rule for Firebase Console deployment: `/environments/{env}/users/{userId}/data/weight_entries`. |
| **`ProgressScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` | - Add `BODY_WEIGHT` to `ProgressMode` enum.<br>- Replace `else ->` in `when (progressMode)` with explicit `ProgressMode.BY_EXERCISE ->`.<br>- Hoist `FilterChip` row above `exercises.isEmpty()`.<br>- Render "Body weight" chip unconditionally.<br>- Promote `progressMode` to `rememberSaveable`.<br>- Add `floatingActionButton = { if (progressMode == ProgressMode.BODY_WEIGHT) FloatingActionButton(...) }`.<br>- Render `WeightOverviewContent` with `MultiLineChart`. |
| **`WeightSummaryCard.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/weight/WeightSummaryCard.kt` | - **[NEW]** KPI summary card with current weight, delta percentage badge, 7D moving average, and unit toggle. |
| **`WeightEntryBottomSheet.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/weight/WeightEntryBottomSheet.kt` | - **[NEW]** Modal bottom sheet with numeric input, date selector, unit toggle, notes, and canonical kg validation. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - Expose `weightEntries: StateFlow<List<WeightEntry>>`.<br>- Expose `weightUnit: StateFlow<String>` backed by `DataModeManager`.<br>- Expose action methods: `saveWeightEntry(weightKg, date, notes)`, `deleteWeightEntry(date)`, and `undoDeleteWeightEntry(date, weightKg, notes)`. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 0: Schema Export Infrastructure & v5 Baseline Generation**
   - Enable `exportSchema = true` in `AppDatabase.kt` at `version = 5`.
   - Configure `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in `build.gradle.kts`.
   - Add `androidx-room-testing` and `androidx.test:runner` to `libs.versions.toml`.
   - Build project to generate and commit `app/schemas/.../5.json`.

2. **Subtask 1: Room Database Schema, Backup & Snapshot Lifecycle (`MIGRATION_5_6`, `SEED_VERSION 4`)**
   - Implement `WeightEntry` with natural `@PrimaryKey val date: LocalDate` and `deletedAtMillis: Long?`.
   - Implement `WeightDao` with `@Upsert`, `upsertAll`, soft-delete query, purge query, and active filtering.
   - Bump version to `6`, implement `MIGRATION_5_6`, commit `6.json`, and register in `build(context)` and `demo(context)`.
   - Update `BackupData` and `BackupCsv` (`#crosstraining-backup-v3`), and add clear and `upsertAll` steps in `Repository.importSnapshot()`.
   - Bump `DemoData.SEED_VERSION` from `3` to `4` and populate demo weight history.
   - Verify with instrumented `AppDatabaseMigrationTest.kt`.

3. **Subtask 2: Domain Analytics Engine & Gap-Aware Chart Component**
   - Widen `ChartPoint.value` to `Float? = null` in `LineChart.kt` with gap-aware polyline rendering.
   - Implement `data/analytics/WeightAnalytics.kt` returning domain `WeightSeriesPoint` with full-window SMA-7 and paired decimation ($\le 120$ points).
   - Unit tests covering pre/post decimation alignment, sparse gaps, and validation range.

4. **Subtask 3: Cloud Synchronization with Tombstone-Aware Merge & Security Rule Documentation**
   - Implement tombstone-aware merge (last-write-wins by `maxOf(updatedAt, deletedAt)`) in `UserCloudSyncManager.kt` under `environments/{currentEnv}/users/{uid}/data/weight_entries`.
   - Add pre-flight purge of 90-day-old tombstones alongside `cleanupDuplicateRoutines()`.
   - Add `weightList` to `isNewUidEmpty` and `hasLegacyData` predicates and dual-read swap assignment.
   - Document Firestore security rule in `docs/draft-requisites/firestore-rules.md`.
   - Unit tests in `TokenBoundIdentitySyncTest.kt` verifying tombstone persistence, merge, and undo resilience.

5. **Subtask 4: Presentation Layer UI (`ProgressScreen`, BottomSheet & SummaryCard)**
   - Implement `WeightEntryBottomSheet.kt` and `WeightSummaryCard.kt`.
   - Refactor `ProgressScreen.kt` (unconditional chip row, `rememberSaveable`, explicit `when`, and Scaffold FAB).
   - Wire `AppViewModel.kt` actions, Undo restoration with fresh timestamp, and unit preference persistence in `DataModeManager`.
   - Capture E2E visual artifacts and execute full pre-PR verification.

---

## 🏛️ Claude Review Iteration 3

- **Date / Author:** 2026-09-07 | Claude (Principal Architect) — Round 3 Architectural Cross-Review (Final)
- **Baseline:** `main` @ `efd0450`
- **Scope of this round:** Review Iteration 3 resolution table + the consolidated authoritative Final Decision Plan, re-verified against `LineChart.kt` (159 L, full read), all four `ChartPoint` construction sites in `ProgressScreen.kt` (`:442`, `:709`, `:714`, `:723`), `ProgressScreen.kt:88-125`, `Repository.kt:44-52,342-384`, `Backup.kt:1-60`, `Converters.kt`, `DataModeManager.kt:24-40`, `DemoData.kt:19-72`, `UserCloudSyncManager.kt:440-520,687-760`, and `app/src/test/**`.

### ⚖️ Evaluation of Final Concessions & Refined Architecture

**B1 (chart nullability) — RESOLVED. The mechanism is now verified against the component it constrains, which is what Round 2 was missing.**

I re-read `LineChart.kt` end to end and grepped every `ChartPoint` reference in the tree. The concession holds on all three counts:

- *Source compatibility is real, not asserted.* All four call sites (`ProgressScreen.kt:442`, `:709`, `:714`, `:723`) **construct** `ChartPoint` with a non-null `Float`; **not one call site reads `.value`**. Widening to `Float? = null` therefore cannot break a consumer, because outside `ChartCanvas` there are no consumers. This is the specific claim I would have rejected if it were merely plausible; it is checkable and it checks out.
- *The `mapNotNull` + early-return fix is sufficient and minimal.* `values = visible.flatMap { s -> s.points.map { it.value } }` (`:105`) is in fact the **only** site that touches `.value` other than `yAt(p.value)` at `:132`. Both are covered by the concession. And the two adjacent hazards I would otherwise have raised are already guarded in shipped code: `if (minV == maxV) { minV -= 1f; maxV += 1f }` (`:108`) prevents a zero `range` on a flat or single-value series, and `xAt` special-cases `xCount == 1` (`:113`) preventing division by zero on the athlete's very first entry. So the NaN surface really does close to zero once nulls are excluded from the min/max scan.
- *Index parity is preserved where it matters.* Emitting `ChartPoint(label, null)` for `N < 3` keeps `raw.size == sma.size`, so `xCount = visible.maxOf { it.points.size }` (`:110`) is identical for both series and the index-stretch misalignment of C3 cannot recur. The X-axis label logic at `:149-155` reads `longest.first().label` / `longest.last().label` — labels remain non-null regardless of value nullity, so a null-leading SMA series does not blank the axis.

The `lastPoint: Offset?` reset is the correct gap-aware polyline formulation, and gating `drawCircle` on `p.value != null` prevents phantom dots at the baseline. This is a genuine fix, not a restatement.

**B2 (tombstones) — RESOLVED at the schema and merge layer, with residual contract gaps noted below.**

The field lands where it must — entity, `MIGRATION_5_6` DDL, `WeightDao`, `BackupCsv`, and the merge resolver — all inside Subtask 1, so there is no second migration. Verified details:

- `deletedAtMillis INTEGER` (nullable) against `Long?` is a correct Room `TableInfo` match, and `Converters.toEpochDay` (`Converters.kt:15`) already returns `Long?`, so `'date' INTEGER NOT NULL … PRIMARY KEY('date')` is exactly Room's generated form for `@PrimaryKey val date: LocalDate`.
- `markDeleted` setting **both** `deletedAtMillis` and `updatedAtMillis` to the same instant makes `maxOf(updatedAtMillis, deletedAtMillis ?: 0L)` well-defined and monotone, so the last-write-wins comparison cannot tie against its own tombstone.
- CSV encoding needs no new primitive: `BackupCsv` already has `private fun s(value: Long?): String = value?.toString() ?: ""` (`Backup.kt:42`), so a null tombstone round-trips as an empty field under the existing RFC-4180 quoting.
- Because `@Upsert` writes the whole row, re-logging a previously deleted date naturally clears its tombstone — the same mechanism Undo needs (see **N4**).

**Ma1 (pipeline ordering) — RESOLVED.** Full-window SMA → paired stride decimation is the one correct ordering of the three, and — critically — the signature now *expresses* it: `prepareChartSeries(...): Pair<List<ChartPoint>, List<ChartPoint>>` returns both series from a single call, so a shared index selection is structurally enforced rather than left to implementer discipline. The `raw.size == sma.size` assertion is correctly required both pre- and post-decimation, closing the hole where the unit test passes while the rendered 1Y/All chart is skewed.

**Ma2 (v5 baseline) — RESOLVED.** The bundled table cell is split into two sequenced, separately-committed steps with explicit build-and-commit gates (`5.json` at `version = 5`, then `6.json` at `version = 6`). `androidx.test:runner 1.6.2` is added alongside `room-testing`, which is what `MigrationTestHelper` needs given `androidTest` previously declared only `ui-test-junit4`, `test.ext.junit` and `espresso-core`.

**Ma3 (Firestore rules) — RESOLVED.** The phantom repo-root `firestore.rules` deliverable is withdrawn, the ruleset is correctly reclassified as out-of-repo with the exact snippet recorded in `docs/draft-requisites/firestore-rules.md`, and Subtask 3 now carries a manual staging verification on a real authenticated account. That converts C6's silent `PERMISSION_DENIED`-swallowed-by-`runCatching` failure into an observable acceptance step.

**Minor gaps — three of five closed cleanly.** `weightUnit` persistence is correctly homed: `DataModeManager` owns the only `crosstraining-prefs` `SharedPreferences` instance (`:30`) and already persists `KEY_THEME_MODE` / `KEY_USER_ROLE` the same way. The `Repository` row is now internally consistent — `Repository` does hold `private val db: AppDatabase` (`:49`), so `private val weightDao = db.weightDao()` matches the existing DAO-property idiom. `WeightSummaryCard.kt` has a real path and contract, and the 1 MiB ceiling is recorded with arithmetic. The FAB row is closed but *incorrectly* — see **N5**.

### 🚨 Remaining or Newly Introduced Concerns

None of the following is blocking. Each is contained within a single already-scheduled subtask, changes no schema, and forces no rework of a migration or a shared component — the same bar I applied to separate blockers from majors in Round 2.

**N1. `importSnapshot()` gains a clear step but never an insert step, and `WeightDao` has no bulk insert — demo seeding and CSV restore would silently drop all weight history. (Major)**

The `Repository.kt` row specifies only *"Add `weightDao.deleteAll()` to clear phase before reinserting"*. There is no corresponding `weightDao.insertAll(data.weightEntries)` in that row, and the `WeightDao` row's method list — which reads as exhaustive — contains `@Upsert upsert(entry)` (single-entity), the two read queries, `markDeleted`, and `deleteAll`, but **no list insert**. Every other table in `importSnapshot()` (`Repository.kt:365-383`) follows clear-then-`insertAll`. Two concrete consequences of implementing the table literally:

1. Demo mode stays blank. `DemoData.snapshot()` returns a `BackupData` (`DemoData.kt:27-28`) that is applied via `importSnapshot`; the newly populated 30-day `weightEntries` list would be built, passed, and dropped on the floor. Subtask 1's "populate demo weight history" bullet would appear done and demonstrably do nothing — and the demo blank-tracker defect is the exact thing Review Iteration 1 flagged as MODIFY.
2. Backup restore loses weight data. `BackupCsv.decode` → `importSnapshot` would wipe `weight_entries` (via the new `deleteAll()`) and restore nothing, which is strictly worse than not integrating backup at all — the C4 silent-loss class the plan claims to have closed.

*Required:* add `insertAll(entries: List<WeightEntry>)` to the `WeightDao` row and `weightDao.insertAll(data.weightEntries)` to the `importSnapshot()` insert phase. One line each, inside files Subtask 1 already owns.

**N2. `WeightAnalytics` returning `ChartPoint` inverts the `data/` → `ui/` dependency and contradicts the accepted M11. (Major)**

`prepareChartSeries(...): Pair<List<ChartPoint>, List<ChartPoint>>` is placed in `data/analytics/`, but `ChartPoint` is declared in `com.fractanomics.crosstraining.ui.components` (`LineChart.kt:29`). Round 2's M11 was accepted specifically on the grounds that a `data/analytics` module returning plain domain values "matches the existing `data/` ↔ `ui/` split". Round 3 keeps the package and reverses the type direction. The tree currently has exactly **one** `data/` → `ui/` import (`DataModeManager.kt:5`, `ui.theme.AppThemeMode`), so this is a deliberate convention, not an accident. It compiles and remains JVM-unit-testable (`ChartPoint` carries no Compose types), which is why this is a layering regression rather than a build break — but it means the analytics engine can no longer be reused or tested without the UI package, and it quietly undoes an agreed decision one round after it was made.

*Required:* have `prepareChartSeries` return a UI-agnostic paired result (e.g. `data class WeightChartSeries(val labels: List<String>, val raw: List<Double>, val sma: List<Double?>)`, or `Pair<List<Pair<String, Double>>, List<Pair<String, Double?>>>`) and perform the `ChartPoint` lowering in `WeightOverviewContent` at the composable boundary. Index parity is preserved either way; only the mapping site moves.

**N3. The 90-day tombstone purge has no owner, and its interaction with a long-offline device is unstated. (Major)**

"Tombstones older than 90 days are purged during periodic Room maintenance" appears in the resolution table and then **nowhere else**: no `WeightDao` purge query, no `Repository` method, no Component Impact Table row, no subtask bullet, and no definition of what "periodic Room maintenance" is — the app has no such existing hook. As specified, the purge will simply not be built, and the tombstone array grows monotonically (benign against the recorded 1 MiB ceiling, but the stated policy is then fiction).

More substantively, the purge horizon *is* the correctness boundary of the whole B2 design: if device B is offline longer than the horizon, device A purges the tombstone for date D while B still holds a live row for D, and the next sync resurrects it — B2's exact failure mode, merely rate-limited to a 90-day window. That trade-off is acceptable, but it should be written down as an explicit limit rather than discovered in the field.

*Required:* add `@Query("DELETE FROM weight_entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoff")` to the `WeightDao` row, name the invocation site (simplest: opportunistically at the start of `uploadUserData`'s weight task, or in the same place `cleanupDuplicateRoutines()` runs as a sync pre-flight at `UserCloudSyncManager.kt:492`), and state the ">90 days offline resurrects" bound in the plan text.

**N4. The tombstone contract does not cover Undo or re-logging, and a naive Undo loses to the remote tombstone. (Major)**

`markDeleted` sets `updatedAtMillis = deletedAt`. If Undo restores the previously-captured `WeightEntry` object as-is, it carries its **original, older** `updatedAtMillis` and a null tombstone — so on the next merge, `maxOf(local.updatedAtMillis, null)` loses to `maxOf(remote.updatedAtMillis, remote.deletedAtMillis)` and the entry the athlete just un-deleted disappears again on the next sync tick. The same applies to re-logging a weight for a previously-deleted date.

Related, BDD Scenario 4's closing line — *"whether Undo is tapped or cloud sync runs, the deletion decision is durable and never resurrects"* — reads as though Undo must also fail to restore, which contradicts the Undo affordance in the line above it.

*Required:* state that Undo and re-log both write `upsert(entry.copy(deletedAtMillis = null, updatedAtMillis = System.currentTimeMillis()))`, and reword Scenario 4 to "if Undo is tapped the entry is restored with a fresh `updatedAtMillis` that supersedes the tombstone; **otherwise** the deletion is durable across sync and devices."

**N5. The FAB fix double-applies the bottom inset. (Minor)**

The concession specifies `floatingActionButton = { … }` with `Modifier.padding(bottom = outerPadding.calculateBottomPadding())`. But the `Scaffold` **already** consumes that inset on itself — `ProgressScreen.kt:99-100` is `Scaffold(modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()), …)`. The Scaffold's own bounds therefore already stop above the bottom navigation, and its `floatingActionButton` slot is positioned inside those bounds. Adding the same padding again floats the FAB roughly a full bottom-bar height too high, over the chart. Drop the inner padding modifier; the slot needs no extra inset.

**N6. `isLocallyEmpty` for the weight upload guard must be derived from the tombstone-inclusive list. (Minor)**

`uploadCollectionWithGuard` (`UserCloudSyncManager.kt:440-466`) skips the `.set()` entirely when `isLocallyEmpty` is true and the remote document is populated. If the weight task passes `isLocallyEmpty = activeEntries.isEmpty()`, then an athlete who deletes **every** weight entry produces an all-tombstone local set, the guard classifies it as empty, the upload is skipped, and the deletions never reach Firestore — B2's symptom re-entering through the overwrite guard. Since the payload is specified as tombstone-inclusive, state that `isLocallyEmpty` is computed from that same list (`payload.isEmpty()`).

**N7. Two small spec omissions.** (i) In the legacy dual-read swap block (`UserCloudSyncManager.kt:747-755`), the plan says to add `weightList` to the `isNewUidEmpty` / `hasLegacyData` predicates but does not mention the corresponding `weightList = legacyWeight` reassignment inside the `if (hasLegacyData)` body — without it a legacy-only weight history is detected and then discarded. (ii) `markDeleted(date, deletedAt: Long = System.currentTimeMillis())` puts a wall-clock default inside a DAO signature, which makes tombstone timestamps non-injectable in tests; prefer passing the instant from `Repository`, matching how the rest of the write path is structured.

### 🏁 Verdict

Round 3 closes both blockers with mechanisms that survive contact with the code — which is the standard Round 2 set and Round 2's own M8 failed. **B1** is resolved correctly and, unusually, *cheaply*: because every `ChartPoint` call site in the tree constructs rather than reads, widening `value` to `Float?` is genuinely non-breaking, and the two remaining NaN-adjacent hazards (`minV == maxV`, `xCount == 1`) are already guarded in shipped code at `LineChart.kt:108` and `:113`. Equal-length null-emission restores index parity without the data-hiding cost of the trim alternative. **B2** is resolved at the only layer where it could be resolved without a second migration: the tombstone lands in the entity, the `MIGRATION_5_6` DDL, the DAO, the CSV section and the merge resolver, all inside Subtask 1, and `markDeleted` writing both timestamps makes the last-write-wins comparison monotone. `BackupCsv` needed no new primitive — `s(Long?)` already exists. **Ma1** is fixed structurally rather than by instruction, since the paired return type makes the wrong ordering inexpressible; **Ma2** splits the two edits into separately-committed, separately-verifiable steps so the v5 baseline can actually be produced; **Ma3** stops asserting a file that does not exist and replaces it with a documented out-of-repo action plus a staging verification that makes a `PERMISSION_DENIED` visible instead of swallowed.

Seven concerns remain and none is blocking. N1 (no insert path for restored/demo weight entries) and N4 (Undo losing to its own tombstone) are the two that would produce user-visible wrong behaviour, but both are single-line contract additions inside files their subtasks already own — no schema change, no migration rework, no second pass over `LineChart.kt`. N2 is a layering regression against the previously-agreed M11 and should be corrected before Subtask 2 starts, since it is free to fix now and awkward later. N3, N5, N6 and N7 are bounded clarifications. Against the bar I set in Round 2 — a blocker is a defect that falsifies a BDD scenario as written or forces rework of a shipped migration or shared component — zero blockers survive. The plan is implementation-ready with the seven items folded into their existing subtasks.

`VERDICT: AGREED`
