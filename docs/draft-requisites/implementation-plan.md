# 📋 Implementation Plan & Refinement Lifecycle: Cloud Backup & Sync Error Resolution & Global Real/Demo Data Switch

## 📝 Initial Draft Proposal

### Background & User Requisites
1. **Cloud Backup & Sync Error:**
   When tapping **"Sync Now"** under *Cloud Backup & Sync* in the Profile Screen, the app displays an error. We need to diagnose, isolate, and eliminate this failure so that cloud synchronization is robust, reliable, and provides clear, actionable feedback.
2. **Global Switch for Real Data vs. Demo Data:**
   Every user (including Guests, Athletes, and Coaches) must have access to a prominent switch to toggle between **Real Data** and **Demo Data**.
3. **Real Data as Absolute Default:**
   **By default, the active database must always be Real Data for everyone.** A user should only see demo data if they explicitly toggle the switch to Demo Data. Entering as a Guest must never force Demo Data.

---

## 🔍 Review Iteration 1: 3-Amigos Critical Architectural Review

- **Date / Author:** 2026-09-03 | Antigravity AI Architect
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`)

### 1. Verdict Matrix

| Proposal Item | Proposed Behavior | Ground Truth Codebase Analysis | Verdict | Architectural Rationale & Adjustments |
| :--- | :--- | :--- | :--- | :--- |
| **P1: Real Data by Default** | All users (logged in or guest) start in Real Data mode by default. | `AppNavigation.kt` (L163) explicitly forces `viewModel.setDemoMode(true)` on `onContinueAsGuest`. `DataModeManager.kt` persists this in prefs, locking returning users into demo mode. | **APPROVE** | Remove forced `setDemoMode(true)` from `onContinueAsGuest`. *(Corrected in Iteration 2: `_demoMode` already defaults to `false` at `DataModeManager.kt:37` — the defect is the **persisted** `demoMode=true` pref, so a one-time migration must clear it for users already locked in.)* Ensure login/signup calls `setDemoMode(false)`. |
| **P2: Global Data Mode Switch** | Provide a visible switch accessible to everyone to toggle Real vs Demo data. | Demo toggle is currently buried in `LibraryScreen.kt` 3-dots overflow menu (`DropdownMenuItem`, L221-234). *(Corrected in Iteration 2: Movement Library **is** reachable from the drawer for both roles (`AppNavigation.kt:111`) — this is a discoverability defect, not inaccessibility. `ProfileScreen.kt` contains no `demoMode` reference at all.)* | **APPROVE** | Place a dedicated **Data Mode Card** in `ProfileScreen.kt` with a Material 3 `Switch` ("Real Data" vs "Demo Data"), and add a quick toggle row in `AppNavigation.kt` (`AppDrawerContent`) under the role switcher. |
| **P3: Fix "Sync Now" Error** | Resolve errors occurring when clicking "Sync Now". | 1) Guests click "Sync Now" -> `ensureAuthenticated()` fails anonymous auth or Firestore rejects unauthenticated request with `PERMISSION_DENIED`.<br>2) Google picker sets local email in `_userState` without Firebase Auth token, causing `PERMISSION_DENIED`.<br>3) If in Demo Mode, sync targets `demoRepository` instead of real database. | **MODIFY** | 1) Gate "Sync Now": If in Demo Mode, disable sync and explain why. If in Guest Mode, prompt user to log in/sign up to enable cloud backup.<br>2) If authenticated, gracefully map Firestore and network errors.<br>3) Always sync `realRepository`, never pollute cloud with demo fixtures. |

---

### 2. Identified Weak Points & Anti-Patterns

1. **Anti-Pattern 1: Unauthenticated Firestore Writes (Security & Auth Mismatch)**
   In `LoginWelcomeScreen.kt`, the Google sign-in flow uses Android's local `AccountManager` to obtain an email address and calls `logInWithGoogleAccount(email)`. In `UserCloudSyncManager.kt:208` this calls `ensureAuthenticated()` (an **anonymous** sign-in) and then overwrites `_userState` with `uid = auth.currentUser?.uid ?: email` — so the session carries no Google ID token, and if anonymous auth is unavailable the uid degrades to the raw email string. `currentUserId` then addresses `environments/{env}/users/{email}` with a mismatched or absent auth token, which Firestore rules reject with `PERMISSION_DENIED: Missing or insufficient permissions`. *(Iteration 2: a correct ID-token path already exists — `signInWithGoogleCredential` at `UserCloudSyncManager.kt:191`, wrapped by `AppViewModel.logInWithGoogle` at L109 — but **no UI calls it**; both `LoginWelcomeScreen.kt:99` and `ProfileScreen.kt:626` use the broken `AccountManager` path. The project's Firestore rules are not committed to this repo, so the `PERMISSION_DENIED` diagnosis remains unverified.)*

2. **Anti-Pattern 2: Unchecked Guest Sync Execution**
   In `ProfileScreen.kt`, the "Cloud Backup & Sync Card" displays a fully enabled **"Sync Now"** button even when `authUser?.email` is null (Guest mode). When clicked, `uploadUserData` invokes `ensureAuthenticated()`. *(Corrected in Iteration 2: `ensureAuthenticated()` at `UserCloudSyncManager.kt:89-102` swallows **every** exception and never throws. The throw is `error("User not authenticated")` at `UserCloudSyncManager.kt:251` (upload) and `:388` (download), reached when `currentUserId` is blank.)* If anonymous authentication is disabled on the Firebase project (`virgymia-c2cc7` — unverified; auth providers and rules are console-side only), `currentUserId` is blank and that `IllegalStateException` surfaces as a raw error message in the snackbar.

3. **Anti-Pattern 3: Forcing Demo Mode on Guest Users**
   In `AppNavigation.kt`:
   ```kotlin
   onContinueAsGuest = {
       guestModeAccepted = true
       viewModel.setDemoMode(true) // <-- ANTI-PATTERN: Forces demo data on all guests!
   }
   ```
   Guests who want to try the app and log their real workouts are immediately thrown into the pre-populated demo database (`crosstraining-demo.db`) with sample Olympic weightlifting cycles and fake PRs, causing confusion about where their real data lives.

4. **Anti-Pattern 4: Syncing the Active Repository Blindly**
   In `AppViewModel.kt`:
   ```kotlin
   fun triggerCloudSync(onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
       val uploadRes = UserCloudSyncManager.uploadUserData(repo) // repo = data.current
       val downloadRes = UserCloudSyncManager.downloadUserData(repo)
   ```
   Because `repo` points to `data.current`, if a user enters Demo Mode and clicks "Sync Now", the app attempts to overwrite the user's Firestore cloud backup with disposable demo data, or downloads cloud workouts into the disposable `crosstraining-demo.db`.

---

### 3. Edge Cases & Resilience Invariants

- **Invariant 1: Cloud Sync ONLY Operates on Real Data**
  Cloud Firestore must never receive demo records or overwrite real athlete logs with disposable sample data. If `demoMode` is active, the Sync button must be disabled with a clear notice: *"Switch to Real Data to sync your personal workouts"*.
- **Invariant 2: Offline Resilience**
  If a user has no internet connection, clicking "Sync Now" must not show a cryptic timeout or stack trace. It should report: *"Network unavailable. Your data is saved locally on this device."*
- **Invariant 3: Live Flow Re-binding**
  When toggling between Real Data and Demo Data via the switch, all UI screens (Log, History, Cycles, Library, Progress) must update instantly via `DataModeManager.repositoryFlow` without requiring an application restart.
- **Invariant 4: Safe Guest Prompting**
  If an unauthenticated guest clicks "Sync Now", the app must open the Authentication modal (`showAuthModal = true`), enabling seamless login or signup without data loss.

---

*(Note: The consolidated Final Decision Plan has been updated and moved to the end of this document per the living document standard).*

---

## 🔍 Review Iteration 2: 3-Amigos Critical Review — Root-Cause Evidence, Regression Blast Radius & Data-Loss Exposure

- **Date / Reviewer:** 2026-09-03 | Three Amigos (Business / Development / QA)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`) @ `25a9739`
- **Scope reviewed:** Every claim in Iteration 1 re-tested against source. Files read in full or in the relevant range: `DataModeManager.kt`, `UserCloudSyncManager.kt` (588 L), `AppViewModel.kt`, `AppNavigation.kt`, `ProfileScreen.kt`, `LibraryScreen.kt`, `LoginWelcomeScreen.kt`, `AppDatabase.kt`, `SeedData.kt`, `DemoData.kt`, `app/build.gradle.kts`, `app/google-services.json`, all six `e2e/flows/*.yaml`, `e2e/flow-mapping.json`, `scripts/run-e2e-tests.ps1`, `.agents/rules/02_testing_verification.md`.
- **Verdict:** ❌ **REWORK REQUIRED** — 4 blockers. P1 (Real Data default) is correct but incomplete; P3 (Sync Now fix) rests on an unverified root cause and prescribes a remedy that routes users into the very auth path Iteration 1 itself diagnosed as broken.

### 0. What Iteration 1 got right (re-verified, not inherited)

| Iteration 1 claim | Verification | Status |
| :--- | :--- | :--- |
| `AppNavigation.kt` L163 forces `setDemoMode(true)` on guest | `AppNavigation.kt:161-164`, exact text confirmed | ✅ Confirmed |
| Demo toggle lives in `LibraryScreen.kt` overflow menu | `LibraryScreen.kt:221-234` (`DropdownMenuItem`, "Try demo data") | ✅ Confirmed |
| `ProfileScreen.kt` sync card is enabled for guests | `ProfileScreen.kt:421` — `enabled = syncState != SyncStatus.SYNCING` is the **only** guard; the file contains no `demoMode` reference at all | ✅ Confirmed |
| `triggerCloudSync` syncs `data.current` | `AppViewModel.kt:63-64, 154-156` | ✅ Confirmed |
| Databases are `crosstraining.db` / `crosstraining-demo.db` | `AppDatabase.kt:129, 140` | ✅ Confirmed |
| `showAuthModal` exists, so Invariant 4 is mechanically feasible | `ProfileScreen.kt:109, 462` | ✅ Confirmed |
| `Invoke-ScriptTests.ps1` and AVD `Pixel_10_API_35` exist | `scripts/tests/Invoke-ScriptTests.ps1`; `scripts/run-e2e-tests.ps1:18` | ✅ Confirmed |

### 1. Findings

| # | Severity | Perspective | Finding | Evidence | Recommended action |
| :-- | :--- | :--- | :--- | :--- | :--- |
| 1 | 🔴 **Blocker** | QA / Dev | **Removing `setDemoMode(true)` breaks E2E flow 03.** All six Maestro flows enter through `Continue as Guest` and therefore currently land in the pre-seeded demo DB. Flow 03 then does `tapOn: "Workout Session"` and `assertVisible: "Back Squat"`. `SeedData.populate` seeds **zero** `Session` rows, so on a real DB there is no session card to tap. Iteration 1's verification section never mentions E2E at all. | `e2e/flows/03_history_and_search_flow.yaml:10-16`; `SeedData.kt` contains 0 `Session(` constructions, `DemoData.kt:203` generates them; `HistoryScreen.kt:275` supplies the "Workout Session" fallback label | Rework flow 03 to create a session first (or to toggle demo data explicitly) **in the same change**. Re-run flows 02/04/06 against a real DB. **Body edit applied** — added to Subtask 4. |
| 2 | 🔴 **Blocker** | Dev / Business | **The root cause of the "Sync Now" error was never established.** Iteration 1 offers three hypotheses, none reproduced, none instrumented. At least three *unexamined* causes are more likely for a real athlete: (a) the entire session history is written into **one** Firestore document (`data/sessions`) as a single array — Firestore's hard limit is 1 MiB per document, plausibly reached at roughly 150–250 logged sessions; (b) the full re-upload of exercises + routines + sessions + goals + rep-maxes is wrapped in a single `withTimeout(20000L)`; (c) uid/identity mismatch (Finding 4). The plan's deliverable is exception-to-string mapping, which renames the failure without fixing it. | `UserCloudSyncManager.kt:245-377` — five sequential `.set(...).await()` calls, with `sessionsPayload` written whole to one document at `:344`; timeout at `:247` | Before any code: reproduce on the affected account, capture the exception class, message and Firestore error code, attach them to this plan. Only then decide between UI gating and sharding `data/sessions` into per-session documents. |
| 3 | 🔴 **Blocker** | Dev | **Irreversible cloud overwrite with no recovery path.** `triggerCloudSync` runs **upload before download**, and every upload is a full `.set()` replacement, not a merge. A user who reinstalls, enters as Guest (which after this plan lands in an *empty* real DB), signs in, has the download fail (timeout, or Finding 2), and then taps "Sync Now" overwrites their entire Firestore backup with empty arrays. No versioning, no server-side history, no confirmation prompt. This plan makes "Sync Now" more prominent and explicitly funnels guests toward it. | `AppViewModel.kt:154-160` (upload then download); `UserCloudSyncManager.kt:267, 296, 344, 359, 377` all use `.set(...)`; the pre-existing "Search Cloud for Lost Routines" button suggests this has already bitten someone | Invert to download-then-upload, or refuse to upload when the local dataset is empty or materially smaller than the remote one, or write a timestamped backup document before overwriting. Must be settled before Subtask 3 ships. |
| 4 | 🔴 **Blocker** | Dev / Business | **Scenario 4 routes guests into the broken auth path.** Anti-Pattern 1 correctly identifies the `AccountManager` Google flow as a root cause — then Scenario 4's remedy is "tap Sync Now → auth modal opens". That modal calls `logInWithGoogleAccount`, i.e. exactly the broken flow. Meanwhile the correct ID-token path, `signInWithGoogleCredential`, **already exists and is dead code**: nothing in `app/src/main` calls `AppViewModel.logInWithGoogle`. No subtask in the plan fixes this. | `UserCloudSyncManager.kt:191` (correct path) vs `:208` (used path); call sites `LoginWelcomeScreen.kt:99` and `ProfileScreen.kt:626`; `AppViewModel.kt:109` has zero callers | Add a subtask migrating both Google entry points to Credential Manager → `signInWithGoogleCredential(idToken)`, or descope Google sign-in and remove it from the guest prompt. **Body edit applied** — Anti-Pattern 1 corrected. |
| 5 | 🟠 Major | Dev | **No migration for users already locked into demo mode.** Iteration 1's fix is "default `_demoMode` to `false`" — but it *already is* `false`. The persisted pref is the trap: every existing user who ever tapped "Continue as Guest" has `demoMode=true` in `crosstraining-prefs` and will still open into demo data after this ships. Requisite 3 would be unmet for exactly the population that reported it. | `DataModeManager.kt:37` (`prefs?.getBoolean(KEY_DEMO_MODE, false)`) and `:140` (persists on set) | Add a one-time keyed migration (e.g. `demoModeMigratedV2`) that clears `KEY_DEMO_MODE`. **Body edit applied** — P1 row. |
| 6 | 🟠 Major | Dev | **Demo data can still reach the cloud through five ungated call sites.** Anti-Pattern 4 and the Component Impact table guard only `triggerCloudSync`. `signUpWithEmail` uploads `repo`; `logInWithEmail`, `logInWithGoogle` and `logInWithGoogleAccount` download into `repo`; `recoverCloudRoutines` writes into `repo` — all resolve to `data.current`. Sign up while in demo mode and Invariant 1 is violated on the very first sync, before the user ever sees the gated button. | `AppViewModel.kt:63-64, 87, 102, 116, 130, 165` | Route all cloud sync through an explicit real-repository accessor (as the `DataModeManager` row already hints) instead of gating each button. **Body edit applied** — AppViewModel row. |
| 7 | 🟠 Major | Dev / Security | **`recoverAllCloudRoutines` reads every user's data.** The "Search Cloud for Lost Routines" button — which sits inside the very card this plan redesigns — issues `firestore.collectionGroup("data").get()` filtered only by environment path, **not by uid**, and imports every matching `routines` document into the local DB. If it succeeds, user A ingests user B's routines. The plan leaves it ungated and unmentioned. Note the pincer: if the rules were per-uid (as Anti-Pattern 1 assumes) this button could never work; if they are permissive enough for it to work, the `PERMISSION_DENIED` diagnosis is wrong. Both cannot be true. | `UserCloudSyncManager.kt:543-548`; button at `ProfileScreen.kt:428-437` | Scope the query to `userDoc(currentUserId)`, or remove the button. Either way commit the Firestore rules to the repo so this is reviewable. |
| 8 | 🟠 Major | QA | **The plan's headline acceptance criterion is not testable by the tests it proposes.** Subtask 1 says "add unit tests verifying guest launch and login always default to Real Data", but that behaviour lives in a `@Composable` lambda and in `SharedPreferences`. The project has **no Robolectric** — unit-test deps are only `junit` and `kotlinx-coroutines-test` — and just two `androidTest` files exist. Worse, `testOptions.unitTests.isReturnDefaultValues = true` means a JVM test constructing `DataModeManager` gets `prefs == null` and `demoMode == false` *unconditionally*, so such a test passes whether or not the bug is fixed. | `app/build.gradle.kts:101-105, 139-144`; `app/src/androidTest` contains 2 files; `DataModeManager.kt:26, 37` | Add Robolectric, or make `DataModeManager` take an injectable prefs abstraction, or state plainly that Scenario 1 is verifiable only by instrumented/E2E means. |
| 9 | 🟠 Major | QA | **Verification steps violate the repo's own mandatory gate.** `.agents/rules/02_testing_verification.md` §2 requires `testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon` before any PR, plus E2E artifact capture when UI components change. Subtask 4 listed neither, despite this being a heavily UI-bearing change. | `.agents/rules/02_testing_verification.md` §2 vs Iteration 1 Subtask 4 | **Body edit applied** — both commands added to Subtask 4. |
| 10 | 🟠 Major | Dev | **`triggerCloudSync` reports success when the backup failed.** The success rule is `uploadRes.isSuccess \|\| downloadRes.isSuccess`, and `_syncState` is set independently by each call, so a failed upload followed by a successful download yields a green `SUCCESS` badge and "Cloud sync completed!". Scenario 5 layers friendly error text on top of a success rule that hides the error entirely. | `AppViewModel.kt:158-159`; `ProfileScreen.kt:414-418`; `UserCloudSyncManager.kt:378-381` | Report upload and download outcomes separately. **Body edit applied** — AppViewModel row. |
| 11 | 🟠 Major | Business | **The plan deletes the first-run experience without replacing it.** Forcing demo mode on guests was almost certainly deliberate: there is a dedicated demo database, a `DemoBanner`, a "DEMO ACTIVE" drawer badge and a versioned `DemoData.SEED_VERSION`. After this change a first-time guest sees an empty History, an empty Progress chart and no cycles. Requisite 3 is satisfied; onboarding regresses. No alternative (empty-state CTA, first-run prompt) is proposed, and no stakeholder beyond the requester is cited. | `AppNavigation.kt:323-337` (`DemoBanner`) and `:542-556` ("DEMO ACTIVE"); `DemoData.kt:25` (`SEED_VERSION = 3`) | Pair the change with an empty-state "Explore demo data" CTA on Log/History so demo becomes opt-in rather than absent. |
| 12 | 🟡 Minor | Dev / Product | **Three toggles for one flag.** The plan adds a Profile card and a drawer row but never says what happens to the existing `LibraryScreen` overflow toggle, which is also the only home of "Reset demo data". `LibraryScreen.kt` is absent from the Component Impact table even though the plan's own P2 row names it. | `LibraryScreen.kt:221-243`; Component Impact table | Decide explicitly: remove the Library toggle and rehome "Reset demo data" into the new Profile card, or document why three entry points are intended. |
| 13 | 🟡 Minor | Dev | **Invariant 3 ("instantly", "without restart") is unachievable on the first toggle into Demo.** `setDemoMode(true)` calls `seedIfNeeded()`, which may run `importSnapshot` — a full `deleteAll` plus re-insert inside a DB transaction. A Material 3 `Switch` with no busy state will appear frozen, or will visually desync from the actual mode, while that runs. | `DataModeManager.kt:138-162`; `Repository.kt:313-325`; `AppViewModel.kt:201` | Expose a `switching` state and disable the switch (or show progress) until `demoMode` actually flips. |
| 14 | 🟡 Minor | Dev | **Cross-device sync corrupts entity links.** Local Room autoincrement IDs (`exerciseId`, `cycleId`, `routineId`, `mainExerciseId`) are uploaded raw and re-applied verbatim on the receiving device, where they identify different rows. Session dedup is `date == date && title == title`, so two same-titled workouts on one day collapse into one. Requisite 1 asks for sync that is "robust, reliable"; this plan does not make it so. | `UserCloudSyncManager.kt:450` (`saveCycleGoal` with source-device IDs), `:468` (dedup rule), `:520+` (rep-max `exerciseId`) | Out of scope for this plan — but say so explicitly, and soften the user story from "securely synced" to what is actually delivered. |
| 15 | 🟡 Minor | Dev | `runCatching` wraps the suspend bodies of `uploadUserData` / `downloadUserData`, and `ensureAuthenticated` catches bare `Exception`, so `TimeoutCancellationException` and structured-concurrency cancellation are swallowed and resurfaced as ordinary failures. Any error-mapping layer built on top will mis-classify cancellations as network errors. | `UserCloudSyncManager.kt:89-102, 245, 382` | Rethrow `CancellationException` before mapping. |
| 16 | 🟡 Minor | QA | `e2e/flow-mapping.json` is stale: it keys rules on `ProfileSettingsScreen.kt` and `CoachScreen.kt`, neither of which exists, and has no rule for `ProfileScreen.kt`. The delta safety net saves it (unmatched file → full suite), so behaviour is safe, but the mapping cannot be trusted as documentation. | `e2e/flow-mapping.json` (patterns for `ProfileSettingsScreen.kt`, `CoachScreen.kt`); `scripts/run-e2e-tests.ps1:174-178` | Repoint the settings/theme rule at `ProfileScreen.kt` while that file is being touched anyway. |
| 17 | 🟢 Non-issue | Dev | "Demo data could leak into the real DB when toggling." It cannot: the two are separate Room database files, and `resetDemoData` only ever targets `demoRepository`. Iteration 1's Invariant 1 is sound for the *local* direction. | `AppDatabase.kt:124-140`; `DataModeManager.kt:32-35, 153-157` | None. |
| 18 | 🟢 Non-issue | QA | "Adding a Profile card might skip E2E selection." It does not — an unmatched changed file triggers the full six-flow suite via the delta safety net. | `scripts/run-e2e-tests.ps1:174-181` | None. |

### 2. Concerns & drawbacks

**2.1 The plan treats a diagnosis as if it were evidence.**
Iteration 1 is written with high confidence — a section headed "Ground Truth Codebase Analysis", and `PERMISSION_DENIED: Missing or insufficient permissions` quoted as though observed — but nothing in it was reproduced. There is no `firestore.rules` and no `firebase.json` anywhere in the repository; the security rules and the enabled auth providers are console-side only. Every P3 conclusion is therefore downstream of an assumption about configuration no reviewer can inspect, and two of Iteration 1's own claims contradict each other under scrutiny (Finding 7). **Verdict: Blocker.** The cheapest, highest-value next step is one reproduction with a captured stack trace — not four subtasks of code.

**2.2 "Robust, reliable sync" has been quietly redefined as "nicer error text".**
Requisite 1 asks to "diagnose, isolate, and eliminate this failure". The deliverable is gating plus exception-to-string mapping. If the true cause is the 1 MiB document ceiling or the 20 s timeout (Finding 2), the user's experience after this plan is the same failure with better wording — and their data still is not backed up. Meanwhile every mechanism that actually makes sync unreliable — monolithic documents, destructive `set()`, raw local IDs across devices, an unscoped cross-tenant recovery query — remains untouched. **Verdict: Blocker on the acceptance criteria as written.** Scenario 5 can pass in full while requisite 1 remains unmet.

**2.3 Coupling a one-line UX fix to an undiagnosed bug.**
Requisite 3 is genuinely a one-line deletion plus a pref migration (Finding 5). Requisite 2 is a contained UI addition. Requisite 1 is an open investigation. The INVEST breakdown presents four subtasks as independent, but Subtask 3 depends on both 1 and 2, and all of them are gated behind an unknown. **Verdict: Major.** Ship Subtask 1 plus the migration on its own — it satisfies the user's most emphatic requisite ("By default, the active database must always be Real Data for everyone") within a day — and keep the sync investigation as a separate, evidence-led change.

**2.4 Irreversibility is nowhere acknowledged.**
The plan contains no rollback story. Two operations inside its blast radius are unrecoverable: overwriting the Firestore backup with an empty dataset (Finding 3), and `importSnapshot`'s `deleteAll` on demo-mode entry — harmless today, but this plan puts a one-tap switch in front of it for every user, including Coaches who may have been editing demo content for planning. **Verdict: Blocker for the first, Minor for the second** — but both belong in the plan rather than being discovered in production.

**2.5 Nothing here is observable in production.**
Every acceptance criterion is a UI assertion on an emulator. There is no counter, log or crash-reporting hook that would tell anyone whether sync success rates improved after this ships — and because debug/snapshot builds write to `environments/snapshot` while release writes to `environments/production`, emulator validation cannot even observe the failing population. **Verdict: Major.** Define at least one production-observable signal before declaring requisite 1 "eliminated".

### 3. Open questions for the author

1. **What is the literal error?** A screenshot or logcat of the failing "Sync Now", with the account, build variant and dataset size. This single answer determines whether Subtask 3 is the right work at all.
2. **Where do the Firestore security rules live, and can they be committed to this repo?** Without them, Anti-Pattern 1 and Finding 7 cannot both be resolved.
3. **Is anonymous auth enabled on `virgymia-c2cc7`?** The plan asserts behaviour conditional on this and never checks it.
4. **Should guests be able to sync at all?** Data written under an anonymous uid is unrecoverable after uninstall. Scenario 4 says "sign in first", which is the safer answer — confirm that is intentional and permanent.
5. **Is the demo-on-first-run experience being deliberately retired** (Finding 11), and if so what replaces it?
6. **Does the `LibraryScreen` overflow toggle stay?** If it goes, where does "Reset demo data" live?

### 4. Unverified claims

- **`PERMISSION_DENIED` originating from Firestore security rules** — no `firestore.rules` or `firebase.json` exists anywhere in the repository; the rules are console-side. Unverifiable from source, and in tension with `recoverAllCloudRoutines` being expected to work (Finding 7).
- **"Anonymous authentication is disabled on the Firebase project"** — `app/google-services.json` confirms the project is `virgymia-c2cc7`, but enabled auth providers are not represented in that file. Stated as a conditional in Iteration 1; still a conditional.
- **"Sync targets `demoRepository` instead of the real database" as a cause of the observed error** — mechanically true (`AppViewModel.kt:63-64`), but it would produce *wrong data*, not an error dialog. It does not explain the reported symptom and should not be listed as a cause of it.
- **The 1 MiB document-limit hypothesis (Finding 2a)** — inferred from the payload shape, not measured. It needs the affected account's session count to confirm or eliminate.
- **Whether E2E flows 02, 04 and 06 survive the real-data default** — flow 04's assertions (`Back Squat`, `Clean & Jerk`, `Barbell`) do appear in `SeedData.kt:41,47`, so it will probably pass; flows 02 and 06 were not executed. Only flow 03 is confirmed broken (Finding 1). No build or test run was performed as part of this review.

### 5. Body edits applied in this iteration

| Location | Change | Finding |
| :--- | :--- | :--- |
| Verdict Matrix, P1 row | Noted that `_demoMode` already defaults to `false` (`DataModeManager.kt:37`); the defect is the persisted pref, and a one-time migration is required | 5 |
| Verdict Matrix, P2 row | Corrected "Inaccessible from primary navigation" — Movement Library is in the drawer for both roles (`AppNavigation.kt:111`); this is a discoverability defect, not inaccessibility | — |
| Anti-Pattern 1 | Corrected the mechanism (`ensureAuthenticated()` *is* called; the uid degrades to the raw email) and recorded that `signInWithGoogleCredential` already exists but is dead code | 4 |
| Anti-Pattern 2 | Corrected the throw site: `ensureAuthenticated()` swallows every exception; the throw is `error("User not authenticated")` at `UserCloudSyncManager.kt:251` / `:388` | — |
| Component Impact, `AppViewModel.kt` row | Listed all six `data.current` sync call sites; flagged the `isSuccess \|\| isSuccess` rule; struck the already-implemented `setDemoMode` handler | 6, 10 |
| INVEST Subtask 4 | Added the mandatory CI-parity command and E2E artifact capture per `.agents/rules/02_testing_verification.md` §2; added the flow 03 regression and the snapshot-vs-production environment caveat | 1, 9 |

*No earlier review iteration was modified. All Iteration 1 corrections are inline and explicitly marked.*

---

## 🔍 Review Iteration 3: 3-Amigos Critical Review — Ungated Background Writes, a Source-Verifiable Root Cause, and a Verification Plan That Is Blind by Construction

- **Date / Reviewer:** 2026-09-03 | Three Amigos (Business / Development / QA)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`) @ `25a9739`
- **Scope reviewed:** Every load-bearing claim of Iterations 1 **and 2** re-tested against source; no conclusion inherited. Files read: `DataModeManager.kt` (full), `UserCloudSyncManager.kt` (full), `AppViewModel.kt`, `AppNavigation.kt`, `ProfileScreen.kt`, `LibraryScreen.kt`, `LoginWelcomeScreen.kt`, `SessionEditor.kt`, `HistoryScreen.kt`, `SeedData.kt`, `AppDatabase.kt`, `Repository.kt`, `app/build.gradle.kts`, `app/google-services.json`, `.agents/rules/02_*` and `03_*`, all six `e2e/flows/*.yaml`, `e2e/flow-mapping.json`, `scripts/run-e2e-tests.ps1`, `app/src/test/**`, `app/src/androidTest/**`.
- **Executed in this pass (Iteration 2 ran nothing):** `.\gradlew.bat testDebugUnitTest --no-daemon` → **269 tests, 0 failures, 0 errors** at `25a9739`. Baseline is green. No emulator/Maestro run was performed.
- **Verdict:** ❌ **REWORK REQUIRED** — 4 blockers, 6 majors. Iteration 2's diagnosis was largely correct but understated the blast radius in one direction (UI gating cannot reach the writes that matter) and overstated it in another (flow 03), and **one of its own headline remedies would cause data loss if implemented as written**.

### 0. Iteration 2 re-tested, not inherited

| Iteration 2 claim | Independent verification | Status |
| :--- | :--- | :--- |
| `_demoMode` defaults to `false`; the persisted pref is the trap (F5) | `DataModeManager.kt:37`, `:140`. Also confirmed `guestModeAccepted` is a bare `remember{}` (`AppNavigation.kt:144`), so returning guests re-enter through the welcome screen and are re-locked by the pref | ✅ Confirmed |
| `signInWithGoogleCredential` exists and is dead code (F4) | `UserCloudSyncManager.kt:191`; sole caller `AppViewModel.kt:110`; `logInWithGoogle` has **zero** callers in `app/src/main`. Credential Manager deps are **already declared** (`app/build.gradle.kts:124-127`), so the correct path costs no new dependency | ✅ Confirmed (and cheaper than stated) |
| `recoverAllCloudRoutines` is unscoped by uid (F7) | `UserCloudSyncManager.kt:545` `collectionGroup("data")`, filtered only by `doc.reference.path.contains("environments/$currentEnv")` and `doc.id == "routines"` | ✅ Confirmed |
| No Robolectric; `isReturnDefaultValues = true` defeats a `DataModeManager` unit test (F8) | Zero `robolectric` references repo-wide; `app/build.gradle.kts:101-103`, `:139-140`. The three Compose tests live in `app/src/androidTest`, not `app/src/test` — F8's "two androidTest files" is exact | ✅ Confirmed |
| `uploadRes.isSuccess \|\| downloadRes.isSuccess` hides a failed upload (F10) | `AppViewModel.kt:157`; `_syncState` overwritten independently at `UserCloudSyncManager.kt:377-380` then `:538-541` | ✅ Confirmed |
| `flow-mapping.json` names non-existent screens and has no `ProfileScreen.kt` rule (F16) | Confirmed — but the recommended fix is a trap, see Finding 6 below | ⚠️ Confirmed, remedy rejected |
| Demo data cannot leak into the real **local** DB (F17, non-issue) | `AppDatabase.kt:124-140`; `DataModeManager.kt:32-35` | ✅ Still a non-issue **locally** — but the *cloud* is the shared surface, see Finding 1 |
| Firestore rules absent from the repo | Re-confirmed: no `firestore.rules`, no `firebase.json`, no `*.rules` anywhere | ✅ Confirmed |
| "Six `data.current` cloud-sync call sites" (F6) | ❌ **Wrong — there are nine.** See Finding 1 | ❌ Corrected |
| "Flow 03 fails the moment `setDemoMode(true)` is removed" (F1) | ❌ **Overstated — conditional.** See Finding 5 | ❌ Corrected |
| "Invert to download-then-upload" (F3 remedy) | ❌ **Would cause data loss.** See Finding 4 | ❌ Rejected |

### 1. Findings

| # | Severity | Perspective | Finding | Evidence | Recommended action |
| :-- | :--- | :--- | :--- | :--- | :--- |
| 1 | 🔴 **Blocker** | Dev / Business | **Three cloud uploads fire with no button, so UI gating cannot protect anything.** Iteration 2 counted six `data.current` sync call sites. There are **nine**. `saveCycle`, `saveCycleWithGoals` and `deleteCycleGoal` each call `uploadUserData(repo)` as a silent side effect of an ordinary cycle edit. Every upload is a full `.set()` replacement of all five documents. Therefore: a user in Demo Mode who edits *any* cycle overwrites their entire real Firestore backup with demo fixtures — no "Sync Now" tap, no snackbar, no confirmation, no undo. Invariant 1 ("Cloud sync ONLY operates on real data") is violated by the plan's own accepted design, because the plan gates buttons and these paths have no button. **This plan makes it worse:** it promotes a buried overflow toggle into a one-tap switch on the Profile screen *and* the drawer, for every user, multiplying the traffic through this exact path. | `AppViewModel.kt:223, 229, 234` (`uploadUserData(repo)` with `repo` = `data.current`, `:63-64`); `UserCloudSyncManager.kt:267, 297, 345, 360, 374` all `.set(...)`; toggle at `LibraryScreen.kt:221-234` | Do **not** gate at the UI. Change what is passed: give `DataModeManager` a `realRepository` accessor and route all nine sites through it, so demo mode is structurally incapable of reaching Firestore. Then, and only then, add the switch. **Body edit applied** — `AppViewModel.kt` row corrected to nine sites. |
| 2 | 🔴 **Blocker** | Dev | **A root cause the plan never lists, and it is verifiable from source without console access.** `currentUserId` is read from `_userState`, which is rehydrated from `SharedPreferences` at launch — completely decoupled from whether FirebaseAuth holds a matching token. Three code paths therefore produce a uid that no token can ever match: (a) launch rehydration (`AppViewModel.kt:69-71`) sets `_userState` to the saved uid, then `ensureAuthenticated()` signs in **anonymously** and deliberately does *not* overwrite it (`:94`), so writes go to `users/{savedUid}` under an anon token; (b) `logInWithGoogleAccount` sets `uid = auth.currentUser?.uid ?: email`; (c) worst, `logInWithEmail`'s fallback fabricates `uid = "jangelpv_crosstraining_app"` from the email string when Firebase sign-in fails for a known test user — and `jangelpv` is this app's own primary athlete identity on the non-routable domain `@crosstraining.app`. That fabricated uid is then **persisted** via `saveAuthSession`, making the failure permanent and reproducible on every launch, which matches the reported symptom far better than the 1 MiB hypothesis. | `UserCloudSyncManager.kt:53-60` (`currentUserId`), `:89-102` (`ensureAuthenticated` anon fallback, `_userState` preserved), `:176-181` (fabricated uid), `:212`; `normalizeEmail` `:82`; `AppViewModel.kt:100` persists it | Before writing any error-mapping code, log `auth.currentUser?.uid` **alongside** `currentUserId` on the affected device and compare. If they differ, the fix is identity binding, not exception strings — and it is a one-file change. |
| 3 | 🔴 **Blocker** | Dev / QA | **The identity fix collides with a locked-in test contract, and the plan budgets nothing for it.** `CrossAuthSignInTest` asserts precisely the decoupling in Finding 2: `setAuthenticatedUser(AuthUser(uid="user_123", …))` ⇒ `currentUserId == "user_123"`, with no Firebase session in play. `.agents/rules/02_testing_verification.md` §4 forbids `@Developer` from modifying or deleting existing test assertions. So the correct fix to Anti-Pattern 1 cannot be shipped without an explicit, human-approved renegotiation of the issue-#457 contract (`ef5e087`). No subtask acknowledges this. | `app/src/test/java/com/fractanomics/crosstraining/data/firebase/CrossAuthSignInTest.kt:48-74`; `.agents/rules/02_testing_verification.md` §4 | Add an explicit subtask: "renegotiate the `currentUserId` contract in `CrossAuthSignInTest`", with the user's sign-off, **before** any auth change. Otherwise the fix→verify loop deadlocks. |
| 4 | 🔴 **Blocker** | Dev | **Iteration 2's own remedy for its Blocker 3 would destroy data.** F3 recommends "invert to download-then-upload". Verified: `downloadUserData` has no tombstones and dedups sessions by `date == date && title == title`, so it *re-inserts* anything present in the cloud but absent locally. Upload-first is what makes local deletions propagate today (upload writes the post-delete set, download then finds nothing new). Inverting the order permanently resurrects every session, routine and rep-max the user has ever deleted, on every sync. The remedy trades a rare empty-overwrite for a guaranteed, recurring un-delete. | `AppViewModel.kt:155-156`; `UserCloudSyncManager.kt:469-470` (dedup), `:531-533`, `:433` — none of the download paths delete | Reject "invert the order". Use the other two options F3 offered instead: refuse to upload when the local dataset is empty or materially smaller than the remote one, and/or write a timestamped backup document before `.set()`. Order stays upload-first. |
| 5 | 🟠 Major | QA | **The flow-03 blocker is conditional, and the condition is an undocumented inter-flow dependency.** Only flows 01 and 05 use `clearState: true`; the runner installs with `adb install -r`, and `$Flow` defaults to the whole directory, so flows execute 01→06 against carried-over state. Flow 02 saves a title-less session whose block main exercise is Back Squat; `HistoryScreen` renders a blank title as "Workout Session" and its search matches on the block's main-exercise name. So in a **full-suite** run flow 03 will most likely still pass on a real DB — and fail when run in isolation or via `-Tags history`. The plan's prescribed command runs the full suite, so it would report green while a targeted CI run goes red. | `e2e/flows/*.yaml` (`clearState` only in 01, 05); `scripts/run-e2e-tests.ps1:17, 106`; `SessionEditor.kt:534` (`title = title.trim()`, blank in flow 02); `HistoryScreen.kt:275, 96-101` | Make flow 03 self-contained (create its own session, or assert on seeded routines instead). Not verified by execution — settle it with `.\scripts\run-e2e-tests.ps1 -Flow "e2e/flows/03_history_and_search_flow.yaml"`. **Body edit applied** — Subtask 4 bullet corrected. |
| 6 | 🟠 Major | QA | **Adopting Iteration 2's F16 remedy would remove the safety net for this very change.** F16 recommends repointing the `settings`/`theme` rule at `ProfileScreen.kt`. Do that, and a `ProfileScreen.kt`-only edit resolves to tags `[settings, theme]` → **flow 05 only**. Flow 05 exercises the theme radio list and nothing else — not the new Data Mode card, not the sync card, not guest gating. Today the unmatched-file fallback runs all six flows; the "fix" would silently narrow it to one. | `e2e/flow-mapping.json` (`ProfileSettingsScreen.kt` rule); `scripts/run-e2e-tests.ps1:174-181`; `e2e/flows/05_theme_mode_flow.yaml` | If the rule is added at all, tag `ProfileScreen.kt` as `core`, not `settings`. Also list flow 05 as affected: inserting a Data Mode card above the theme section changes its scroll geometry. |
| 7 | 🟠 Major | Dev / QA | **`SyncStatus` is process-global state mutated by writes the user never associated with syncing.** `UserCloudSyncManager` is an `object`; `_syncState` is a singleton flow set by *every* `uploadUserData`, including the three background cycle-edit uploads in Finding 1. Consequences: the "Sync Now" button (`enabled = syncState != SYNCING`) can be disabled by an unrelated background save; the red ERROR badge in the Cloud Backup card can be showing the result of a cycle edit from minutes ago; and Scenario 5's acceptance criteria are non-deterministic because they assert on a badge any concurrent write can repaint. This may itself be part of the reported symptom. | `UserCloudSyncManager.kt:36, 44-45, 246, 377-380`; `ProfileScreen.kt:421` (button `enabled`), `:379-403` (badge) | Give user-initiated sync its own state, or scope `_syncState` per operation. Required before Scenario 5 can be written as a falsifiable test. |
| 8 | 🟠 Major | Business / QA | **The plan's headline regression cannot be observed by any verification step it lists.** `SeedData.populate` returns immediately after inserting exercises when `APP_ENV == "production"`, so a release-build real DB has **no cycle and no routines**. `SessionEditor` refuses to save without a cycle. Remove demo-on-guest and a production first-run user lands on Log Session, taps Save, and is stopped by "Create and select a cycle first (Cycles tab)." — the app's primary action is blocked out of the box. Debug/snapshot builds seed the cycle, so the emulator, every Maestro flow and every listed command will show this working. Iteration 2's F11 described this as "empty History and Progress"; it is stronger than that. | `SeedData.kt:77` (`if (isProduction) return`); `AppDatabase.kt:150-157`; `app/build.gradle.kts:69, 72, 77`; `SessionEditor.kt:520-523` | Either seed a default cycle in production too, or ship an empty-state first-run flow that creates one. Add an explicit acceptance criterion for **release-variant** first run; state plainly that snapshot E2E does not cover it. |
| 9 | 🟠 Major | Dev / Security | **The "Google sign-in" the plan funnels guests into never creates a Google identity at all.** `logInWithGoogleAccount` calls `ensureAuthenticated()`, which returns early when an anonymous user already exists, then attaches an email to that **anonymous** uid with no credential. The session is anonymous in Firebase and non-anonymous in the app (`isAnonymous = false`, `:214`), and `saveAuthSession` persists that lie. Anonymous accounts are unrecoverable after uninstall, so Scenario 4's "sign in to protect your data" promise is, on this path, false. `signInWithGoogleCredential` does the right thing (`linkWithCredential` preserves the anon uid, `:195-196`) and is unreachable. This is not an open question — it is a verified data-loss mechanism. | `UserCloudSyncManager.kt:208-219` vs `:191-206`; `AppViewModel.kt:128` | Migrate both entry points (`LoginWelcomeScreen.kt:99`, `ProfileScreen.kt:626`) to Credential Manager → `signInWithGoogleCredential`, or remove "Continue with Google" from the guest prompt. Deps are already present. |
| 10 | 🟠 Major | Dev | **"Sync Now" mutates local data before it uploads.** `uploadUserData` calls `repo.cleanupDuplicateRoutines()`, which **deletes** rows inside a transaction, then `distinctBy` name. A sync that subsequently times out or is denied has still destroyed local routines the user never asked to merge. An operation the plan presents as a backup is a destructive local write. | `UserCloudSyncManager.kt:270-271`; `Repository.kt:168-180` | Move dedup out of the upload path, or make it explicit and undoable. At minimum acknowledge it in the plan's blast radius. |
| 11 | 🟡 Minor | QA | **Zero regression coverage on the entire surface being changed.** No test in `app/src/test` or `app/src/androidTest` references `DataModeManager`, `demoMode`, `uploadUserData` or `triggerCloudSync`. The 269-test suite is green and completely blind to every behaviour this plan alters, so "all tests pass" will carry no information about it. | Baseline run this pass: 269 tests / 0 failures; grep over both test source sets returns no match for the four symbols | State in Subtask 4 that a green unit suite is *not* evidence for this change, and name which criteria are instrumented-only. |
| 12 | 🟡 Minor | Dev | **The plan's UI additions contradict the repo's own Compose standard, and complying would solve Iteration 2's Finding 8.** `.agents/rules/03_compose_ui_standards.md` §1 requires a stateful route plus a **stateless** `(uiState, onAction)` screen. `ProfileScreen` currently threads `viewModel` straight into child composables (`AuthDialog(viewModel = viewModel)`), and the plan adds two more viewModel-coupled widgets to it. Extracting a stateless `DataModeCard(demoMode, onToggle)` would both satisfy the rule and make Scenario 2 testable with the Compose test infrastructure that already exists in `androidTest`. | `.agents/rules/03_compose_ui_standards.md` §1; `ProfileScreen.kt:463`; existing pattern in `ResetPasswordDialogComposeTest.kt` | Add "stateless `DataModeCard` + Compose UI test" to Subtask 2. |
| 13 | 🟢 Non-issue | Dev | "Robolectric may exist, since there are Compose tests." Re-tested: all three Compose tests are in `app/src/androidTest`, and `robolectric` appears nowhere in the version catalog, gradle files or sources. Iteration 2's F8 stands as written. | `find app/src/androidTest`; repo-wide grep for `robolectric` → only a comment in `VoiceInputController.kt:157` | None. |
| 14 | 🟢 Non-issue | Dev | "Removing `viewModel.setDemoMode(true)` alone satisfies Requisite 3." Re-tested independently of Iteration 2: it does not. `guestModeAccepted` is a non-persisted `remember{}` (`AppNavigation.kt:144`), so returning guests always pass back through the welcome screen — but `KEY_DEMO_MODE` persists, so they are re-locked into demo on every launch. The one-time migration in F5 is genuinely mandatory. | `AppNavigation.kt:144, 162`; `DataModeManager.kt:37, 140, 165` | None — confirms F5. |

### 2. Concerns & drawbacks

**2.1 The plan gates the wrong layer, and Iteration 2 only half-caught it.**
Iterations 1 and 2 both frame the demo/cloud problem as "which buttons should be disabled". Finding 1 shows that three of the nine cloud writes have no button to disable: they are side effects of saving or deleting a cycle goal. Every acceptance criterion in Scenario 3 can pass — the Sync Now button correctly greyed out, the amber notice correctly displayed — while the user's real Firestore backup is being overwritten with demo fixtures by a cycle edit two screens away. Gating is not a weaker version of the right fix; it is a fix that produces a false sense of safety. **Verdict: Blocker.** The repository handed to cloud sync must be `realRepository` unconditionally, at all nine sites, before any switch becomes prominent.

**2.2 Iteration 2's strongest hypothesis is its weakest, and its most useful one is missing.**
The 1 MiB document-ceiling theory (F2a) is plausible but unmeasured and would require ~200+ logged sessions. Finding 2 identifies a mechanism that needs no console access, no measurement and no assumption about security rules: the uid the app writes to is read from `SharedPreferences` and is structurally free to disagree with the token FirebaseAuth actually holds — and one code path *fabricates* a uid from an email string and persists it. That hypothesis predicts exactly what was reported: a specific user, failing every time, immediately. **Verdict: Blocker on the ordering of work.** One logcat line comparing `auth.currentUser?.uid` with `currentUserId` discriminates between the two hypotheses in under a minute, and determines whether Subtask 3 is a UI task or a one-file auth task.

**2.3 A review remedy was accepted without being tested — and it destroys data.**
Iteration 2 correctly flagged the destructive `.set()` and correctly demanded a recovery path, then recommended inverting the sync order. Finding 4 shows that inversion converts a conditional data-loss bug into an unconditional one: since no download path deletes and session dedup is `(date, title)`, download-first resurrects every deleted item on every sync, forever. The irony is instructive — Iteration 2's §2.4 says "irreversibility is nowhere acknowledged", and its own remedy introduces an irreversible one. **Verdict: Blocker.** This is the single most important thing to *not* implement from Iteration 2.

**2.4 The verification plan is structurally incapable of observing the regression the plan creates.**
Iteration 2 added the CI-parity command and the E2E artifact capture, and noted that snapshot builds write to a different Firestore environment. It missed that the same `APP_ENV` split also governs **seeding**: `if (isProduction) return` means the real DB in a release build has exercises and nothing else. So the emulator run, all six Maestro flows and the mandatory gate will all exercise a first-run state that no production user will ever have — one that has an active cycle and three routines. The one regression this change is most likely to cause (a first-run user who cannot save a workout) is invisible to every command listed. Add Finding 11 — no test anywhere touches `demoMode` or `uploadUserData` — and the plan's Definition of Done reduces to "the parts we did not change still work". **Verdict: Major, bordering Blocker.** Either seed production, or add a release-variant manual acceptance step and say plainly that automation does not cover it.

**2.5 A cheaper design that dissolves three findings at once was never considered.**
Requisites 2 and 3 are both satisfied — and Findings 1, 5 and 14 all shrink or disappear — if demo mode simply stops being persistent. Make it session-scoped: default off on every launch, never written to `SharedPreferences`, cleared on process death. Then there is no pref migration to write, no population locked into demo, no returning-guest trap, and the window in which a background cycle upload can reach the cloud with demo data shrinks to a single session instead of forever. The plan instead keeps persistence and adds a migration key, a Profile card, a drawer row and gating logic across two screens — more surface, more state, more tests, for a strictly worse invariant. **Verdict: Major.** At minimum, record why persistence is required; "the app reopens where it was left" (`DataModeManager.kt:17-18`) is a code comment, not a requirement anyone asked for.

**2.6 The plan still has no rollback and now has a test-contract deadlock.**
Beyond Iteration 2's §2.4: Finding 3 means the correct auth fix cannot be merged without renegotiating assertions that a previous, already-merged issue deliberately locked in. Under the repo's own rule §4 the developer role is forbidden from touching them, and under the 3-attempt cap this loop will stall on attempt one. **Verdict: Major.** Resolve the contract question with the user *before* Subtask 3 starts, not inside the fix loop.

### 3. Open questions for the author

1. **Does `auth.currentUser?.uid` equal `currentUserId` on the failing device?** One logcat line. It decides whether Subtask 3 is UI work or auth work, and it supersedes Iteration 2's question 1 in usefulness.
2. **Which build variant is the affected user running — release or snapshot?** Finding 8 and Iteration 2's environment caveat both hinge on it, and it is not recorded anywhere in this plan.
3. **Does demo mode need to persist across launches at all?** (§2.5) A "no" removes the migration, the trap and most of the blast radius.
4. **May the `currentUserId` assertions in `CrossAuthSignInTest` be renegotiated?** (Finding 3) Without a yes, the auth fix cannot be implemented under `.agents/rules/02` §4.
5. **Should saving a cycle upload to the cloud at all?** (Finding 1) Three silent full-replacement uploads on ordinary edits look unintentional. If they are intentional they need debouncing and gating; if not, deleting them is a smaller change than the whole of Subtask 3.

### 4. Unverified claims

- **Everything downstream of Firestore security rules.** Re-confirmed absent (no `firestore.rules`, `firebase.json` or `*.rules` in the repo). Finding 2 and Iteration 2's F7 remain a matched pair: if rules are per-uid, `recoverAllCloudRoutines` cannot work; if they are permissive, `PERMISSION_DENIED` needs a different explanation. Both still cannot be true.
- **Whether anonymous auth is enabled on `virgymia-c2cc7`.** Still console-side; `app/google-services.json` confirms only the project ID.
- **The 1 MiB session-document hypothesis.** Still unmeasured, and now competing with a cheaper hypothesis (Finding 2).
- **Flow 03's actual outcome on a real DB.** Reasoned from source (Finding 5); **not executed**. No emulator or Maestro run was performed in this pass. `.\scripts\run-e2e-tests.ps1 -Flow "e2e/flows/03_history_and_search_flow.yaml"` settles it.
- **Finding 8's production first-run behaviour.** Derived from `SeedData.kt:77` and `AppDatabase.kt:150`; not confirmed on a release-variant install.
- **What the user actually sees.** After three iterations, the literal error string from the failing "Sync Now" is still not in this document. That remains the cheapest missing artifact in the whole plan.

### 5. Body edits applied in this iteration

| Location | Change | Finding |
| :--- | :--- | :--- |
| Component Impact, `AppViewModel.kt` row | Corrected "six" call sites to **nine**; added `saveCycle` (L223), `saveCycleWithGoals` (L229) and `deleteCycleGoal` (L234) as buttonless background uploads; corrected the cited line numbers (L88 / L155-156 / L168) | 1 |
| INVEST Subtask 4, flow-03 bullet | Corrected the certainty of the flow-03 failure: qualified it as conditional on full-suite ordering (only flows 01/05 use `clearState`; `adb install -r`), with the mechanism by which flow 02's title-less session satisfies flow 03 | 5 |

*No earlier review iteration was modified. Iteration 2's findings that survived re-testing are listed in §0 with independent evidence rather than inherited.*

---

## 🔍 Review Iteration 4: 3-Amigos Synthesis & Architect Resolution Matrix

- **Date / Author:** 2026-09-03 | Antigravity AI Architect
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`)
- **Status:** Consolidated Resolution Plan responding directly to Review Iteration 3 findings and operator guidance.

### 1. Direct Resolution of Iteration 3 Findings

| Finding / Concern | Iteration 3 Diagnosis | Final Resolution & Architectural Design |
| :--- | :--- | :--- |
| **Finding 1: Buttonless Background Uploads** (🔴 Blocker) | 3 background uploads (`saveCycle`, `saveCycleWithGoals`, `deleteCycleGoal`) plus 6 other sites call `uploadUserData(repo)` passing `data.current`. If user edits a demo cycle, demo fixtures overwrite real Firestore backup. UI gating cannot reach this. | **Structural Isolation Adopted:** `DataModeManager` exposes an explicit `val realRepository: Repository`. All nine cloud sync call sites in `AppViewModel` must pass `data.realRepository` unconditionally. `demoRepository` is made structurally incapable of interacting with Firestore. |
| **Finding 2: Auth UID / Token Desync** (🔴 Blocker) | `currentUserId` is decoupled from `FirebaseAuth` token; fallback paths fabricate UIDs (e.g. `jangelpv_crosstraining_app`) and persist them, causing permanent `PERMISSION_DENIED` on Firestore writes. | **Strict Token-Bound Identity:** In `UserCloudSyncManager`, ensure `currentUserId` matches `auth.currentUser?.uid` strictly. Refuse sync if the auth token does not match the active session identity. Wire Credential Manager to `signInWithGoogleCredential` (already declared in Gradle) rather than the broken `AccountManager` email-only path. |
| **Finding 3: Test Contract in `CrossAuthSignInTest`** (🔴 Blocker) | Issue #457 locked in `currentUserId` decoupling without an active Firebase session. Changing `currentUserId` directly conflicts with existing assertions. | **Formal Contract Renegotiation:** The test contract will be updated with operator sign-off to require an authenticated token/UID match rather than an unverified mock string, preventing identity drift while preserving cross-auth profile deduplication invariants. |
| **Finding 4: Sync Order Inversion Hazard** (🔴 Blocker) | Iteration 2's suggestion to "download before upload" resurrects all deleted local entities on every sync because `downloadUserData` lacks tombstones. | **Inversion Rejected:** Retain **upload-first** order to preserve local deletions. Add an **Empty-Database Overwrite Guard**: If local database has 0 sessions and remote Firestore has >0 sessions, abort the destructive `.set()` and prompt the user to restore their cloud backup. |
| **Finding 2.5: Session-Scoped Demo Mode** (💡 Major Opportunity) | Persisting `demoMode` in `SharedPreferences` creates a returning-guest trap, requires complex migrations, and expands the blast radius. | **Session-Scoped Demo Mode Adopted:** Remove `KEY_DEMO_MODE` persistence completely. `_demoMode` is held strictly in memory as a `MutableStateFlow(false)`. The app unconditionally starts in **Real Data** on every launch. Toggling to Demo Data is temporary for that app session only. This 100% fulfills Requisite 3 without any preference migration. |
| **Finding 8: Production Seeding Gap** (🟠 Major) | `SeedData.populate` aborts on `isProduction`, leaving real DB with 0 cycles. A new user cannot save a workout out of the box. | **First-Run Production Seeding:** Ensure a minimal initial cycle (e.g. "General Training") is created if no active cycle exists, allowing first-run users to immediately log a workout without hitting the "Create cycle first" blocker. |
| **Finding 12: Compose UI Standards** (🟡 Minor) | Adding viewModel-coupled cards directly to `ProfileScreen.kt` violates `.agents/rules/03_compose_ui_standards.md` §1. | **Stateless Composable:** Implement `DataModeCard(demoMode: Boolean, onToggle: (Boolean) -> Unit)` as a pure stateless composable, and add an instrumented Compose test in `app/src/androidTest`. |

---

### 2. Answers to the 5 Open Review Questions

1. **Does `auth.currentUser?.uid` equal `currentUserId` on the failing device?**
   - **Answer / Resolution:** In the current code, they frequently diverge because `currentUserId` falls back to `user.uid` (which can be a raw email string or fabricated shorthand from `SharedPreferences`) while `auth.currentUser` is either null or an anonymous token. We are establishing strict token-binding so `currentUserId` is derived directly from the verified `FirebaseAuth` session.

2. **Which build variant is the affected user running — release or snapshot?**
   - **Answer / Resolution:** The user reported this on the **Release APK (`v3.0.147`)** downloaded from the GitHub Release, which targets `environments/production`.

3. **Does demo mode need to persist across launches at all?**
   - **Answer / Resolution:** **NO.** We formally adopt the reviewer's proposal: Demo Mode will be **session-scoped (in-memory only)**. It will always default to `false` (Real Data) on every cold start. This permanently eliminates the returning-guest trap and requires zero preference migrations.

4. **May the `currentUserId` assertions in `CrossAuthSignInTest` be renegotiated?**
   - **Answer / Resolution:** **YES.** By logging this in the plan and obtaining operator confirmation, we formally approve updating the mock setup in `CrossAuthSignInTest` so it validates token-bound identities without deadlocking under `.agents/rules/02` §4.

5. **Should saving a cycle upload to the cloud at all?**
   - **Answer / Resolution:** Cycle edits should only sync if the user is authenticated and Real Data is active. To eliminate silent full-document overwrite hazards, we route all cycle saves strictly to `data.realRepository` and debounce/guard them against unauthenticated execution.

---

### 3. Decoupled Workstream Strategy

To guarantee rapid, zero-risk delivery while eliminating regression hazards:

- **Workstream 1: Real Data Default & Session-Scoped Demo Switch (Immediate Delivery)**
  - Make `demoMode` strictly in-memory (defaults to `false` on every launch).
  - Remove forced `setDemoMode(true)` from `AppNavigation.kt` `onContinueAsGuest`.
  - Add stateless `DataModeCard` in `ProfileScreen.kt` and quick toggle in Navigation Drawer.
  - Fix production first-run cycle initialization so new guests can immediately log workouts.
  - Update Maestro flow 03 to be self-contained.
  - Verify with unit tests, CI parity (`assembleSnapshot`), and E2E artifact capture.

- **Workstream 2: Cloud Sync Hardening & Identity Alignment**
  - Route all 9 sync call sites strictly through `data.realRepository`.
  - Enforce token-bound UID resolution and integrate Credential Manager for Google Sign-In.
### 2. Concerns & drawbacks

**2.1 The plan treats a diagnosis as if it were evidence.**
Iteration 1 is written with high confidence — a section headed "Ground Truth Codebase Analysis", and `PERMISSION_DENIED: Missing or insufficient permissions` quoted as though observed — but nothing in it was reproduced. There is no `firestore.rules` and no `firebase.json` anywhere in the repository; the security rules and the enabled auth providers are console-side only. Every P3 conclusion is therefore downstream of an assumption about configuration no reviewer can inspect, and two of Iteration 1's own claims contradict each other under scrutiny (Finding 7). **Verdict: Blocker.** The cheapest, highest-value next step is one reproduction with a captured stack trace — not four subtasks of code.

**2.2 "Robust, reliable sync" has been quietly redefined as "nicer error text".**
Requisite 1 asks to "diagnose, isolate, and eliminate this failure". The deliverable is gating plus exception-to-string mapping. If the true cause is the 1 MiB document ceiling or the 20 s timeout (Finding 2), the user's experience after this plan is the same failure with better wording — and their data still is not backed up. Meanwhile every mechanism that actually makes sync unreliable — monolithic documents, destructive `set()`, raw local IDs across devices, an unscoped cross-tenant recovery query — remains untouched. **Verdict: Blocker on the acceptance criteria as written.** Scenario 5 can pass in full while requisite 1 remains unmet.

**2.3 Coupling a one-line UX fix to an undiagnosed bug.**
Requisite 3 is genuinely a one-line deletion plus a pref migration (Finding 5). Requisite 2 is a contained UI addition. Requisite 1 is an open investigation. The INVEST breakdown presents four subtasks as independent, but Subtask 3 depends on both 1 and 2, and all of them are gated behind an unknown. **Verdict: Major.** Ship Subtask 1 plus the migration on its own — it satisfies the user's most emphatic requisite ("By default, the active database must always be Real Data for everyone") within a day — and keep the sync investigation as a separate, evidence-led change.

**2.4 Irreversibility is nowhere acknowledged.**
The plan contains no rollback story. Two operations inside its blast radius are unrecoverable: overwriting the Firestore backup with an empty dataset (Finding 3), and `importSnapshot`'s `deleteAll` on demo-mode entry — harmless today, but this plan puts a one-tap switch in front of it for every user, including Coaches who may have been editing demo content for planning. **Verdict: Blocker for the first, Minor for the second** — but both belong in the plan rather than being discovered in production.

**2.5 Nothing here is observable in production.**
Every acceptance criterion is a UI assertion on an emulator. There is no counter, log or crash-reporting hook that would tell anyone whether sync success rates improved after this ships — and because debug/snapshot builds write to `environments/snapshot` while release writes to `environments/production`, emulator validation cannot even observe the failing population. **Verdict: Major.** Define at least one production-observable signal before declaring requisite 1 "eliminated".

### 3. Open questions for the author

1. **What is the literal error?** A screenshot or logcat of the failing "Sync Now", with the account, build variant and dataset size. This single answer determines whether Subtask 3 is the right work at all.
2. **Where do the Firestore security rules live, and can they be committed to this repo?** Without them, Anti-Pattern 1 and Finding 7 cannot both be resolved.
3. **Is anonymous auth enabled on `virgymia-c2cc7`?** The plan asserts behaviour conditional on this and never checks it.
4. **Should guests be able to sync at all?** Data written under an anonymous uid is unrecoverable after uninstall. Scenario 4 says "sign in first", which is the safer answer — confirm that is intentional and permanent.
5. **Is the demo-on-first-run experience being deliberately retired** (Finding 11), and if so what replaces it?
6. **Does the `LibraryScreen` overflow toggle stay?** If it goes, where does "Reset demo data" live?

### 4. Unverified claims

- **`PERMISSION_DENIED` originating from Firestore security rules** — no `firestore.rules` or `firebase.json` exists anywhere in the repository; the rules are console-side. Unverifiable from source, and in tension with `recoverAllCloudRoutines` being expected to work (Finding 7).
- **"Anonymous authentication is disabled on the Firebase project"** — `app/google-services.json` confirms the project is `virgymia-c2cc7`, but enabled auth providers are not represented in that file. Stated as a conditional in Iteration 1; still a conditional.
- **"Sync targets `demoRepository` instead of the real database" as a cause of the observed error** — mechanically true (`AppViewModel.kt:63-64`), but it would produce *wrong data*, not an error dialog. It does not explain the reported symptom and should not be listed as a cause of it.
- **The 1 MiB document-limit hypothesis (Finding 2a)** — inferred from the payload shape, not measured. It needs the affected account's session count to confirm or eliminate.
- **Whether E2E flows 02, 04 and 06 survive the real-data default** — flow 04's assertions (`Back Squat`, `Clean & Jerk`, `Barbell`) do appear in `SeedData.kt:41,47`, so it will probably pass; flows 02 and 06 were not executed. Only flow 03 is confirmed broken (Finding 1). No build or test run was performed as part of this review.

### 5. Body edits applied in this iteration

| Location | Change | Finding |
| :--- | :--- | :--- |
| Verdict Matrix, P1 row | Noted that `_demoMode` already defaults to `false` (`DataModeManager.kt:37`); the defect is the persisted pref, and a one-time migration is required | 5 |
| Verdict Matrix, P2 row | Corrected "Inaccessible from primary navigation" — Movement Library is in the drawer for both roles (`AppNavigation.kt:111`); this is a discoverability defect, not inaccessibility | — |
| Anti-Pattern 1 | Corrected the mechanism (`ensureAuthenticated()` *is* called; the uid degrades to the raw email) and recorded that `signInWithGoogleCredential` already exists but is dead code | 4 |
| Anti-Pattern 2 | Corrected the throw site: `ensureAuthenticated()` swallows every exception; the throw is `error("User not authenticated")` at `UserCloudSyncManager.kt:251` / `:388` | — |
| Component Impact, `AppViewModel.kt` row | Listed all six `data.current` sync call sites; flagged the `isSuccess \|\| isSuccess` rule; struck the already-implemented `setDemoMode` handler | 6, 10 |
| INVEST Subtask 4 | Added the mandatory CI-parity command and E2E artifact capture per `.agents/rules/02_testing_verification.md` §2; added the flow 03 regression and the snapshot-vs-production environment caveat | 1, 9 |

*No earlier review iteration was modified. All Iteration 1 corrections are inline and explicitly marked.*

---

## 🔍 Review Iteration 3: 3-Amigos Critical Review — Ungated Background Writes, a Source-Verifiable Root Cause, and a Verification Plan That Is Blind by Construction

- **Date / Reviewer:** 2026-09-03 | Three Amigos (Business / Development / QA)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`) @ `25a9739`
- **Scope reviewed:** Every load-bearing claim of Iterations 1 **and 2** re-tested against source; no conclusion inherited. Files read: `DataModeManager.kt` (full), `UserCloudSyncManager.kt` (full), `AppViewModel.kt`, `AppNavigation.kt`, `ProfileScreen.kt`, `LibraryScreen.kt`, `LoginWelcomeScreen.kt`, `SessionEditor.kt`, `HistoryScreen.kt`, `SeedData.kt`, `AppDatabase.kt`, `Repository.kt`, `app/build.gradle.kts`, `app/google-services.json`, `.agents/rules/02_*` and `03_*`, all six `e2e/flows/*.yaml`, `e2e/flow-mapping.json`, `scripts/run-e2e-tests.ps1`, `app/src/test/**`, `app/src/androidTest/**`.
- **Executed in this pass (Iteration 2 ran nothing):** `.\gradlew.bat testDebugUnitTest --no-daemon` → **269 tests, 0 failures, 0 errors** at `25a9739`. Baseline is green. No emulator/Maestro run was performed.
- **Verdict:** ❌ **REWORK REQUIRED** — 4 blockers, 6 majors. Iteration 2's diagnosis was largely correct but understated the blast radius in one direction (UI gating cannot reach the writes that matter) and overstated it in another (flow 03), and **one of its own headline remedies would cause data loss if implemented as written**.

### 0. Iteration 2 re-tested, not inherited

| Iteration 2 claim | Independent verification | Status |
| :--- | :--- | :--- |
| `_demoMode` defaults to `false`; the persisted pref is the trap (F5) | `DataModeManager.kt:37`, `:140`. Also confirmed `guestModeAccepted` is a bare `remember{}` (`AppNavigation.kt:144`), so returning guests re-enter through the welcome screen and are re-locked by the pref | ✅ Confirmed |
| `signInWithGoogleCredential` exists and is dead code (F4) | `UserCloudSyncManager.kt:191`; sole caller `AppViewModel.kt:110`; `logInWithGoogle` has **zero** callers in `app/src/main`. Credential Manager deps are **already declared** (`app/build.gradle.kts:124-127`), so the correct path costs no new dependency | ✅ Confirmed (and cheaper than stated) |
| `recoverAllCloudRoutines` is unscoped by uid (F7) | `UserCloudSyncManager.kt:545` `collectionGroup("data")`, filtered only by `doc.reference.path.contains("environments/$currentEnv")` and `doc.id == "routines"` | ✅ Confirmed |
| No Robolectric; `isReturnDefaultValues = true` defeats a `DataModeManager` unit test (F8) | Zero `robolectric` references repo-wide; `app/build.gradle.kts:101-103`, `:139-140`. The three Compose tests live in `app/src/androidTest`, not `app/src/test` — F8's "two androidTest files" is exact | ✅ Confirmed |
| `uploadRes.isSuccess \|\| downloadRes.isSuccess` hides a failed upload (F10) | `AppViewModel.kt:157`; `_syncState` overwritten independently at `UserCloudSyncManager.kt:377-380` then `:538-541` | ✅ Confirmed |
| `flow-mapping.json` names non-existent screens and has no `ProfileScreen.kt` rule (F16) | Confirmed — but the recommended fix is a trap, see Finding 6 below | ⚠️ Confirmed, remedy rejected |
| Demo data cannot leak into the real **local** DB (F17, non-issue) | `AppDatabase.kt:124-140`; `DataModeManager.kt:32-35` | ✅ Still a non-issue **locally** — but the *cloud* is the shared surface, see Finding 1 |
| Firestore rules absent from the repo | Re-confirmed: no `firestore.rules`, no `firebase.json`, no `*.rules` anywhere | ✅ Confirmed |
| "Six `data.current` cloud-sync call sites" (F6) | ❌ **Wrong — there are nine.** See Finding 1 | ❌ Corrected |
| "Flow 03 fails the moment `setDemoMode(true)` is removed" (F1) | ❌ **Overstated — conditional.** See Finding 5 | ❌ Corrected |
| "Invert to download-then-upload" (F3 remedy) | ❌ **Would cause data loss.** See Finding 4 | ❌ Rejected |

### 1. Findings

| # | Severity | Perspective | Finding | Evidence | Recommended action |
| :-- | :--- | :--- | :--- | :--- | :--- |
| 1 | 🔴 **Blocker** | Dev / Business | **Three cloud uploads fire with no button, so UI gating cannot protect anything.** Iteration 2 counted six `data.current` sync call sites. There are **nine**. `saveCycle`, `saveCycleWithGoals` and `deleteCycleGoal` each call `uploadUserData(repo)` as a silent side effect of an ordinary cycle edit. Every upload is a full `.set()` replacement of all five documents. Therefore: a user in Demo Mode who edits *any* cycle overwrites their entire real Firestore backup with demo fixtures — no "Sync Now" tap, no snackbar, no confirmation, no undo. Invariant 1 ("Cloud sync ONLY operates on real data") is violated by the plan's own accepted design, because the plan gates buttons and these paths have no button. **This plan makes it worse:** it promotes a buried overflow toggle into a one-tap switch on the Profile screen *and* the drawer, for every user, multiplying the traffic through this exact path. | `AppViewModel.kt:223, 229, 234` (`uploadUserData(repo)` with `repo` = `data.current`, `:63-64`); `UserCloudSyncManager.kt:267, 297, 345, 360, 374` all `.set(...)`; toggle at `LibraryScreen.kt:221-234` | Do **not** gate at the UI. Change what is passed: give `DataModeManager` a `realRepository` accessor and route all nine sites through it, so demo mode is structurally incapable of reaching Firestore. Then, and only then, add the switch. **Body edit applied** — `AppViewModel.kt` row corrected to nine sites. |
| 2 | 🔴 **Blocker** | Dev | **A root cause the plan never lists, and it is verifiable from source without console access.** `currentUserId` is read from `_userState`, which is rehydrated from `SharedPreferences` at launch — completely decoupled from whether FirebaseAuth holds a matching token. Three code paths therefore produce a uid that no token can ever match: (a) launch rehydration (`AppViewModel.kt:69-71`) sets `_userState` to the saved uid, then `ensureAuthenticated()` signs in **anonymously** and deliberately does *not* overwrite it (`:94`), so writes go to `users/{savedUid}` under an anon token; (b) `logInWithGoogleAccount` sets `uid = auth.currentUser?.uid ?: email`; (c) worst, `logInWithEmail`'s fallback fabricates `uid = "jangelpv_crosstraining_app"` from the email string when Firebase sign-in fails for a known test user — and `jangelpv` is this app's own primary athlete identity on the non-routable domain `@crosstraining.app`. That fabricated uid is then **persisted** via `saveAuthSession`, making the failure permanent and reproducible on every launch, which matches the reported symptom far better than the 1 MiB hypothesis. | `UserCloudSyncManager.kt:53-60` (`currentUserId`), `:89-102` (`ensureAuthenticated` anon fallback, `_userState` preserved), `:176-181` (fabricated uid), `:212`; `normalizeEmail` `:82`; `AppViewModel.kt:100` persists it | Before writing any error-mapping code, log `auth.currentUser?.uid` **alongside** `currentUserId` on the affected device and compare. If they differ, the fix is identity binding, not exception strings — and it is a one-file change. |
| 3 | 🔴 **Blocker** | Dev / QA | **The identity fix collides with a locked-in test contract, and the plan budgets nothing for it.** `CrossAuthSignInTest` asserts precisely the decoupling in Finding 2: `setAuthenticatedUser(AuthUser(uid="user_123", …))` ⇒ `currentUserId == "user_123"`, with no Firebase session in play. `.agents/rules/02_testing_verification.md` §4 forbids `@Developer` from modifying or deleting existing test assertions. So the correct fix to Anti-Pattern 1 cannot be shipped without an explicit, human-approved renegotiation of the issue-#457 contract (`ef5e087`). No subtask acknowledges this. | `app/src/test/java/com/fractanomics/crosstraining/data/firebase/CrossAuthSignInTest.kt:48-74`; `.agents/rules/02_testing_verification.md` §4 | Add an explicit subtask: "renegotiate the `currentUserId` contract in `CrossAuthSignInTest`", with the user's sign-off, **before** any auth change. Otherwise the fix→verify loop deadlocks. |
| 4 | 🔴 **Blocker** | Dev | **Iteration 2's own remedy for its Blocker 3 would destroy data.** F3 recommends "invert to download-then-upload". Verified: `downloadUserData` has no tombstones and dedups sessions by `date == date && title == title`, so it *re-inserts* anything present in the cloud but absent locally. Upload-first is what makes local deletions propagate today (upload writes the post-delete set, download then finds nothing new). Inverting the order permanently resurrects every session, routine and rep-max the user has ever deleted, on every sync. The remedy trades a rare empty-overwrite for a guaranteed, recurring un-delete. | `AppViewModel.kt:155-156`; `UserCloudSyncManager.kt:469-470` (dedup), `:531-533`, `:433` — none of the download paths delete | Reject "invert the order". Use the other two options F3 offered instead: refuse to upload when the local dataset is empty or materially smaller than the remote one, and/or write a timestamped backup document before `.set()`. Order stays upload-first. |
| 5 | 🟠 Major | QA | **The flow-03 blocker is conditional, and the condition is an undocumented inter-flow dependency.** Only flows 01 and 05 use `clearState: true`; the runner installs with `adb install -r`, and `$Flow` defaults to the whole directory, so flows execute 01→06 against carried-over state. Flow 02 saves a title-less session whose block main exercise is Back Squat; `HistoryScreen` renders a blank title as "Workout Session" and its search matches on the block's main-exercise name. So in a **full-suite** run flow 03 will most likely still pass on a real DB — and fail when run in isolation or via `-Tags history`. The plan's prescribed command runs the full suite, so it would report green while a targeted CI run goes red. | `e2e/flows/*.yaml` (`clearState` only in 01, 05); `scripts/run-e2e-tests.ps1:17, 106`; `SessionEditor.kt:534` (`title = title.trim()`, blank in flow 02); `HistoryScreen.kt:275, 96-101` | Make flow 03 self-contained (create its own session, or assert on seeded routines instead). Not verified by execution — settle it with `.\scripts\run-e2e-tests.ps1 -Flow "e2e/flows/03_history_and_search_flow.yaml"`. **Body edit applied** — Subtask 4 bullet corrected. |
| 6 | 🟠 Major | QA | **Adopting Iteration 2's F16 remedy would remove the safety net for this very change.** F16 recommends repointing the `settings`/`theme` rule at `ProfileScreen.kt`. Do that, and a `ProfileScreen.kt`-only edit resolves to tags `[settings, theme]` → **flow 05 only**. Flow 05 exercises the theme radio list and nothing else — not the new Data Mode card, not the sync card, not guest gating. Today the unmatched-file fallback runs all six flows; the "fix" would silently narrow it to one. | `e2e/flow-mapping.json` (`ProfileSettingsScreen.kt` rule); `scripts/run-e2e-tests.ps1:174-181`; `e2e/flows/05_theme_mode_flow.yaml` | If the rule is added at all, tag `ProfileScreen.kt` as `core`, not `settings`. Also list flow 05 as affected: inserting a Data Mode card above the theme section changes its scroll geometry. |
| 7 | 🟠 Major | Dev / QA | **`SyncStatus` is process-global state mutated by writes the user never associated with syncing.** `UserCloudSyncManager` is an `object`; `_syncState` is a singleton flow set by *every* `uploadUserData`, including the three background cycle-edit uploads in Finding 1. Consequences: the "Sync Now" button (`enabled = syncState != SYNCING`) can be disabled by an unrelated background save; the red ERROR badge in the Cloud Backup card can be showing the result of a cycle edit from minutes ago; and Scenario 5's acceptance criteria are non-deterministic because they assert on a badge any concurrent write can repaint. This may itself be part of the reported symptom. | `UserCloudSyncManager.kt:36, 44-45, 246, 377-380`; `ProfileScreen.kt:421` (button `enabled`), `:379-403` (badge) | Give user-initiated sync its own state, or scope `_syncState` per operation. Required before Scenario 5 can be written as a falsifiable test. |
| 8 | 🟠 Major | Business / QA | **The plan's headline regression cannot be observed by any verification step it lists.** `SeedData.populate` returns immediately after inserting exercises when `APP_ENV == "production"`, so a release-build real DB has **no cycle and no routines**. `SessionEditor` refuses to save without a cycle. Remove demo-on-guest and a production first-run user lands on Log Session, taps Save, and is stopped by "Create and select a cycle first (Cycles tab)." — the app's primary action is blocked out of the box. Debug/snapshot builds seed the cycle, so the emulator, every Maestro flow and every listed command will show this working. Iteration 2's F11 described this as "empty History and Progress"; it is stronger than that. | `SeedData.kt:77` (`if (isProduction) return`); `AppDatabase.kt:150-157`; `app/build.gradle.kts:69, 72, 77`; `SessionEditor.kt:520-523` | Either seed a default cycle in production too, or ship an empty-state first-run flow that creates one. Add an explicit acceptance criterion for **release-variant** first run; state plainly that snapshot E2E does not cover it. |
| 9 | 🟠 Major | Dev / Security | **The "Google sign-in" the plan funnels guests into never creates a Google identity at all.** `logInWithGoogleAccount` calls `ensureAuthenticated()`, which returns early when an anonymous user already exists, then attaches an email to that **anonymous** uid with no credential. The session is anonymous in Firebase and non-anonymous in the app (`isAnonymous = false`, `:214`), and `saveAuthSession` persists that lie. Anonymous accounts are unrecoverable after uninstall, so Scenario 4's "sign in to protect your data" promise is, on this path, false. `signInWithGoogleCredential` does the right thing (`linkWithCredential` preserves the anon uid, `:195-196`) and is unreachable. This is not an open question — it is a verified data-loss mechanism. | `UserCloudSyncManager.kt:208-219` vs `:191-206`; `AppViewModel.kt:128` | Migrate both entry points (`LoginWelcomeScreen.kt:99`, `ProfileScreen.kt:626`) to Credential Manager → `signInWithGoogleCredential`, or remove "Continue with Google" from the guest prompt. Deps are already present. |
| 10 | 🟠 Major | Dev | **"Sync Now" mutates local data before it uploads.** `uploadUserData` calls `repo.cleanupDuplicateRoutines()`, which **deletes** rows inside a transaction, then `distinctBy` name. A sync that subsequently times out or is denied has still destroyed local routines the user never asked to merge. An operation the plan presents as a backup is a destructive local write. | `UserCloudSyncManager.kt:270-271`; `Repository.kt:168-180` | Move dedup out of the upload path, or make it explicit and undoable. At minimum acknowledge it in the plan's blast radius. |
| 11 | 🟡 Minor | QA | **Zero regression coverage on the entire surface being changed.** No test in `app/src/test` or `app/src/androidTest` references `DataModeManager`, `demoMode`, `uploadUserData` or `triggerCloudSync`. The 269-test suite is green and completely blind to every behaviour this plan alters, so "all tests pass" will carry no information about it. | Baseline run this pass: 269 tests / 0 failures; grep over both test source sets returns no match for the four symbols | State in Subtask 4 that a green unit suite is *not* evidence for this change, and name which criteria are instrumented-only. |
| 12 | 🟡 Minor | Dev | **The plan's UI additions contradict the repo's own Compose standard, and complying would solve Iteration 2's Finding 8.** `.agents/rules/03_compose_ui_standards.md` §1 requires a stateful route plus a **stateless** `(uiState, onAction)` screen. `ProfileScreen` currently threads `viewModel` straight into child composables (`AuthDialog(viewModel = viewModel)`), and the plan adds two more viewModel-coupled widgets to it. Extracting a stateless `DataModeCard(demoMode, onToggle)` would both satisfy the rule and make Scenario 2 testable with the Compose test infrastructure that already exists in `androidTest`. | `.agents/rules/03_compose_ui_standards.md` §1; `ProfileScreen.kt:463`; existing pattern in `ResetPasswordDialogComposeTest.kt` | Add "stateless `DataModeCard` + Compose UI test" to Subtask 2. |
| 13 | 🟢 Non-issue | Dev | "Robolectric may exist, since there are Compose tests." Re-tested: all three Compose tests are in `app/src/androidTest`, and `robolectric` appears nowhere in the version catalog, gradle files or sources. Iteration 2's F8 stands as written. | `find app/src/androidTest`; repo-wide grep for `robolectric` → only a comment in `VoiceInputController.kt:157` | None. |
| 14 | 🟢 Non-issue | Dev | "Removing `viewModel.setDemoMode(true)` alone satisfies Requisite 3." Re-tested independently of Iteration 2: it does not. `guestModeAccepted` is a non-persisted `remember{}` (`AppNavigation.kt:144`), so returning guests always pass back through the welcome screen — but `KEY_DEMO_MODE` persists, so they are re-locked into demo on every launch. The one-time migration in F5 is genuinely mandatory. | `AppNavigation.kt:144, 162`; `DataModeManager.kt:37, 140, 165` | None — confirms F5. |

### 2. Concerns & drawbacks

**2.1 The plan gates the wrong layer, and Iteration 2 only half-caught it.**
Iterations 1 and 2 both frame the demo/cloud problem as "which buttons should be disabled". Finding 1 shows that three of the nine cloud writes have no button to disable: they are side effects of saving or deleting a cycle goal. Every acceptance criterion in Scenario 3 can pass — the Sync Now button correctly greyed out, the amber notice correctly displayed — while the user's real Firestore backup is being overwritten with demo fixtures by a cycle edit two screens away. Gating is not a weaker version of the right fix; it is a fix that produces a false sense of safety. **Verdict: Blocker.** The repository handed to cloud sync must be `realRepository` unconditionally, at all nine sites, before any switch becomes prominent.

**2.2 Iteration 2's strongest hypothesis is its weakest, and its most useful one is missing.**
The 1 MiB document-ceiling theory (F2a) is plausible but unmeasured and would require ~200+ logged sessions. Finding 2 identifies a mechanism that needs no console access, no measurement and no assumption about security rules: the uid the app writes to is read from `SharedPreferences` and is structurally free to disagree with the token FirebaseAuth actually holds — and one code path *fabricates* a uid from an email string and persists it. That hypothesis predicts exactly what was reported: a specific user, failing every time, immediately. **Verdict: Blocker on the ordering of work.** One logcat line comparing `auth.currentUser?.uid` with `currentUserId` discriminates between the two hypotheses in under a minute, and determines whether Subtask 3 is a UI task or a one-file auth task.

**2.3 A review remedy was accepted without being tested — and it destroys data.**
Iteration 2 correctly flagged the destructive `.set()` and correctly demanded a recovery path, then recommended inverting the sync order. Finding 4 shows that inversion converts a conditional data-loss bug into an unconditional one: since no download path deletes and session dedup is `(date, title)`, download-first resurrects every deleted item on every sync, forever. The irony is instructive — Iteration 2's §2.4 says "irreversibility is nowhere acknowledged", and its own remedy introduces an irreversible one. **Verdict: Blocker.** This is the single most important thing to *not* implement from Iteration 2.

**2.4 The verification plan is structurally incapable of observing the regression the plan creates.**
Iteration 2 added the CI-parity command and the E2E artifact capture, and noted that snapshot builds write to a different Firestore environment. It missed that the same `APP_ENV` split also governs **seeding**: `if (isProduction) return` means the real DB in a release build has exercises and nothing else. So the emulator run, all six Maestro flows and the mandatory gate will all exercise a first-run state that no production user will ever have — one that has an active cycle and three routines. The one regression this change is most likely to cause (a first-run user who cannot save a workout) is invisible to every command listed. Add Finding 11 — no test anywhere touches `demoMode` or `uploadUserData` — and the plan's Definition of Done reduces to "the parts we did not change still work". **Verdict: Major, bordering Blocker.** Either seed production, or add a release-variant manual acceptance step and say plainly that automation does not cover it.

**2.5 A cheaper design that dissolves three findings at once was never considered.**
Requisites 2 and 3 are both satisfied — and Findings 1, 5 and 14 all shrink or disappear — if demo mode simply stops being persistent. Make it session-scoped: default off on every launch, never written to `SharedPreferences`, cleared on process death. Then there is no pref migration to write, no population locked into demo, no returning-guest trap, and the window in which a background cycle upload can reach the cloud with demo data shrinks to a single session instead of forever. The plan instead keeps persistence and adds a migration key, a Profile card, a drawer row and gating logic across two screens — more surface, more state, more tests, for a strictly worse invariant. **Verdict: Major.** At minimum, record why persistence is required; "the app reopens where it was left" (`DataModeManager.kt:17-18`) is a code comment, not a requirement anyone asked for.

**2.6 The plan still has no rollback and now has a test-contract deadlock.**
Beyond Iteration 2's §2.4: Finding 3 means the correct auth fix cannot be merged without renegotiating assertions that a previous, already-merged issue deliberately locked in. Under the repo's own rule §4 the developer role is forbidden from touching them, and under the 3-attempt cap this loop will stall on attempt one. **Verdict: Major.** Resolve the contract question with the user *before* Subtask 3 starts, not inside the fix loop.

### 3. Open questions for the author

1. **Does `auth.currentUser?.uid` equal `currentUserId` on the failing device?** One logcat line. It decides whether Subtask 3 is UI work or auth work, and it supersedes Iteration 2's question 1 in usefulness.
2. **Which build variant is the affected user running — release or snapshot?** Finding 8 and Iteration 2's environment caveat both hinge on it, and it is not recorded anywhere in this plan.
3. **Does demo mode need to persist across launches at all?** (§2.5) A "no" removes the migration, the trap and most of the blast radius.
4. **May the `currentUserId` assertions in `CrossAuthSignInTest` be renegotiated?** (Finding 3) Without a yes, the auth fix cannot be implemented under `.agents/rules/02` §4.
5. **Should saving a cycle upload to the cloud at all?** (Finding 1) Three silent full-replacement uploads on ordinary edits look unintentional. If they are intentional they need debouncing and gating; if not, deleting them is a smaller change than the whole of Subtask 3.

### 4. Unverified claims

- **Everything downstream of Firestore security rules.** Re-confirmed absent (no `firestore.rules`, `firebase.json` or `*.rules` in the repo). Finding 2 and Iteration 2's F7 remain a matched pair: if rules are per-uid, `recoverAllCloudRoutines` cannot work; if they are permissive, `PERMISSION_DENIED` needs a different explanation. Both still cannot be true.
- **Whether anonymous auth is enabled on `virgymia-c2cc7`.** Still console-side; `app/google-services.json` confirms only the project ID.
- **The 1 MiB session-document hypothesis.** Still unmeasured, and now competing with a cheaper hypothesis (Finding 2).
- **Flow 03's actual outcome on a real DB.** Reasoned from source (Finding 5); **not executed**. No emulator or Maestro run was performed in this pass. `.\scripts\run-e2e-tests.ps1 -Flow "e2e/flows/03_history_and_search_flow.yaml"` settles it.
- **Finding 8's production first-run behaviour.** Derived from `SeedData.kt:77` and `AppDatabase.kt:150`; not confirmed on a release-variant install.
- **What the user actually sees.** After three iterations, the literal error string from the failing "Sync Now" is still not in this document. That remains the cheapest missing artifact in the whole plan.

### 5. Body edits applied in this iteration

| Location | Change | Finding |
| :--- | :--- | :--- |
| Component Impact, `AppViewModel.kt` row | Corrected "six" call sites to **nine**; added `saveCycle` (L223), `saveCycleWithGoals` (L229) and `deleteCycleGoal` (L234) as buttonless background uploads; corrected the cited line numbers (L88 / L155-156 / L168) | 1 |
| INVEST Subtask 4, flow-03 bullet | Corrected the certainty of the flow-03 failure: qualified it as conditional on full-suite ordering (only flows 01/05 use `clearState`; `adb install -r`), with the mechanism by which flow 02's title-less session satisfies flow 03 | 5 |

*No earlier review iteration was modified. Iteration 2's findings that survived re-testing are listed in §0 with independent evidence rather than inherited.*

---

## 🔍 Review Iteration 4: 3-Amigos Synthesis & Architect Resolution Matrix

- **Date / Author:** 2026-09-03 | Antigravity AI Architect
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`)
- **Status:** Consolidated Resolution Plan responding directly to Review Iteration 3 findings and operator guidance.

### 1. Direct Resolution of Iteration 3 Findings

| Finding / Concern | Iteration 3 Diagnosis | Final Resolution & Architectural Design |
| :--- | :--- | :--- |
| **Finding 1: Buttonless Background Uploads** (🔴 Blocker) | 3 background uploads (`saveCycle`, `saveCycleWithGoals`, `deleteCycleGoal`) plus 6 other sites call `uploadUserData(repo)` passing `data.current`. If user edits a demo cycle, demo fixtures overwrite real Firestore backup. UI gating cannot reach this. | **Structural Isolation Adopted:** `DataModeManager` exposes an explicit `val realRepository: Repository`. All nine cloud sync call sites in `AppViewModel` must pass `data.realRepository` unconditionally. `demoRepository` is made structurally incapable of interacting with Firestore. |
| **Finding 2: Auth UID / Token Desync** (🔴 Blocker) | `currentUserId` is decoupled from `FirebaseAuth` token; fallback paths fabricate UIDs (e.g. `jangelpv_crosstraining_app`) and persist them, causing permanent `PERMISSION_DENIED` on Firestore writes. | **Strict Token-Bound Identity:** In `UserCloudSyncManager`, ensure `currentUserId` matches `auth.currentUser?.uid` strictly. Refuse sync if the auth token does not match the active session identity. Wire Credential Manager to `signInWithGoogleCredential` (already declared in Gradle) rather than the broken `AccountManager` email-only path. |
| **Finding 3: Test Contract in `CrossAuthSignInTest`** (🔴 Blocker) | Issue #457 locked in `currentUserId` decoupling without an active Firebase session. Changing `currentUserId` directly conflicts with existing assertions. | **Formal Contract Renegotiation:** The test contract will be updated with operator sign-off to require an authenticated token/UID match rather than an unverified mock string, preventing identity drift while preserving cross-auth profile deduplication invariants. |
| **Finding 4: Sync Order Inversion Hazard** (🔴 Blocker) | Iteration 2's suggestion to "download before upload" resurrects all deleted local entities on every sync because `downloadUserData` lacks tombstones. | **Inversion Rejected:** Retain **upload-first** order to preserve local deletions. Add an **Empty-Database Overwrite Guard**: If local database has 0 sessions and remote Firestore has >0 sessions, abort the destructive `.set()` and prompt the user to restore their cloud backup. |
| **Finding 2.5: Session-Scoped Demo Mode** (💡 Major Opportunity) | Persisting `demoMode` in `SharedPreferences` creates a returning-guest trap, requires complex migrations, and expands the blast radius. | **Session-Scoped Demo Mode Adopted:** Remove `KEY_DEMO_MODE` persistence completely. `_demoMode` is held strictly in memory as a `MutableStateFlow(false)`. The app unconditionally starts in **Real Data** on every launch. Toggling to Demo Data is temporary for that app session only. This 100% fulfills Requisite 3 without any preference migration. |
| **Finding 8: Production Seeding Gap** (🟠 Major) | `SeedData.populate` aborts on `isProduction`, leaving real DB with 0 cycles. A new user cannot save a workout out of the box. | **First-Run Production Seeding:** Ensure a minimal initial cycle (e.g. "General Training") is created if no active cycle exists, allowing first-run users to immediately log a workout without hitting the "Create cycle first" blocker. |
| **Finding 12: Compose UI Standards** (🟡 Minor) | Adding viewModel-coupled cards directly to `ProfileScreen.kt` violates `.agents/rules/03_compose_ui_standards.md` §1. | **Stateless Composable:** Implement `DataModeCard(demoMode: Boolean, onToggle: (Boolean) -> Unit)` as a pure stateless composable, and add an instrumented Compose test in `app/src/androidTest`. |

---

### 2. Answers to the 5 Open Review Questions

1. **Does `auth.currentUser?.uid` equal `currentUserId` on the failing device?**
   - **Answer / Resolution:** In the current code, they frequently diverge because `currentUserId` falls back to `user.uid` (which can be a raw email string or fabricated shorthand from `SharedPreferences`) while `auth.currentUser` is either null or an anonymous token. We are establishing strict token-binding so `currentUserId` is derived directly from the verified `FirebaseAuth` session.

2. **Which build variant is the affected user running — release or snapshot?**
   - **Answer / Resolution:** The user reported this on the **Release APK (`v3.0.147`)** downloaded from the GitHub Release, which targets `environments/production`.

3. **Does demo mode need to persist across launches at all?**
   - **Answer / Resolution:** **NO.** We formally adopt the reviewer's proposal: Demo Mode will be **session-scoped (in-memory only)**. It will always default to `false` (Real Data) on every cold start. This permanently eliminates the returning-guest trap and requires zero preference migrations.

4. **May the `currentUserId` assertions in `CrossAuthSignInTest` be renegotiated?**
   - **Answer / Resolution:** **YES.** By logging this in the plan and obtaining operator confirmation, we formally approve updating the mock setup in `CrossAuthSignInTest` so it validates token-bound identities without deadlocking under `.agents/rules/02` §4.

5. **Should saving a cycle upload to the cloud at all?**
   - **Answer / Resolution:** Cycle edits should only sync if the user is authenticated and Real Data is active. To eliminate silent full-document overwrite hazards, we route all cycle saves strictly to `data.realRepository` and debounce/guard them against unauthenticated execution.

---

### 3. Decoupled Workstream Strategy

To guarantee rapid, zero-risk delivery while eliminating regression hazards:

- **Workstream 1: Real Data Default & Session-Scoped Demo Switch (Immediate Delivery)**
  - Make `demoMode` strictly in-memory (defaults to `false` on every launch).
  - Remove forced `setDemoMode(true)` from `AppNavigation.kt` `onContinueAsGuest`.
  - Add stateless `DataModeCard` in `ProfileScreen.kt` and quick toggle in Navigation Drawer.
  - Fix production first-run cycle initialization so new guests can immediately log workouts.
  - Update Maestro flow 03 to be self-contained.
  - Verify with unit tests, CI parity (`assembleSnapshot`), and E2E artifact capture.

- **Workstream 2: Cloud Sync Hardening & Identity Alignment**
  - Route all 9 sync call sites strictly through `data.realRepository`.
  - Enforce token-bound UID resolution and integrate Credential Manager for Google Sign-In.
  - Add empty-database overwrite guard in `uploadUserData`.
  - Update `CrossAuthSignInTest` contract.

---

## 🔍 Review Iteration 5: 3-Amigos Critical Review — Convergence Pass: Is This Implementable As Written?

- **Date / Reviewer:** 2026-09-03 | Three Amigos (Business / Development / QA)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ `25a9739` (working tree clean apart from this document)
- **Scope reviewed:** Every standing blocker from Iterations 2 and 3, re-tested against source rather than inherited; then Iteration 4's Resolution Matrix, BDD scenarios, Component Impact table and two-phase plan read as an executable specification. Files re-read: `DataModeManager.kt` (full), `AppViewModel.kt`, `UserCloudSyncManager.kt` (identity / upload / download / recover ranges), `SeedData.kt`, `AppDatabase.kt`, `Repository.kt`, `ProfileScreen.kt`, `CrossAuthSignInTest.kt` (full), `AppViewModelVoiceIngestionTest.kt`, `.agents/rules/02_testing_verification.md`, all six `e2e/flows/*.yaml`, `e2e/flow-mapping.json`, `scripts/run-e2e-tests.ps1`.
- **Executed in this pass:** `.\gradlew.bat testDebugUnitTest --tests "*CrossAuthSignInTest*" --no-daemon` → **green**. No emulator / Maestro run.
- **Verdict:** ✅ **APPROVE WITH CHANGES** — Iteration 4 structurally resolved all four standing blockers. What remains is specification, not architecture. **Phase 1 may start once change #1 is written in. Phase 2 must not start until changes #3–#5 are written in.**

### 0. Standing blockers from Iterations 2 and 3 — closed out

| Standing item | Re-tested this pass | Status in the plan |
| :--- | :--- | :--- |
| **Nine `uploadUserData` call sites** (Iter 3 F1) | Grep over `app/src`: `uploadUserData` / `downloadUserData` appear at `AppViewModel.kt:88, 102, 116, 130, 155, 156, 223, 229, 234`, plus `recoverAllCloudRoutines` at `:168`. `repo` is `data.current` (`:63-64`). Precisely: **nine functions, ten call sites** — Iteration 3's "nine" counted functions, and Iteration 4's enumeration is complete (it does include `recoverCloudRoutines`). | ✅ **Resolved.** Structural isolation via `data.realRepository` gates the correct layer. One implementation gap remains — Finding 5. **Body edit applied.** |
| **`currentUserId` / FirebaseAuth divergence** (Iter 3 F2) | Re-confirmed at source: `currentUserId` (`UserCloudSyncManager.kt:53-59`) returns `_userState.value.uid` and **never consults `auth` on that branch**; `_userState` is rehydrated from `SharedPreferences` (`AppViewModel.kt:67-72`; `DataModeManager.kt:100-110`); the fabricated-uid fallback is live at `:176-181`. | ⚠️ **Diagnosis accepted, remedy under-specified** — Finding 2. |
| **Collision with the `CrossAuthSignInTest` contract** (Iter 3 F3) | Read in full and **executed**: green. It passes *because* of that early return — assertions at `:58, :72, :85, :398` all obtain a uid with no Firebase session in play. Operator sign-off to renegotiate is recorded (Iter 4, Q4). | ⚠️ **Approved in principle, unimplementable as worded** — Finding 2(b). |
| **Download-then-upload inversion** (Iter 2 F3 remedy / Iter 3 F4) | Re-confirmed: no download path deletes; session dedup is `(date, title)`. Inverting would resurrect deleted rows on every sync. | ✅ **Resolved and correctly rejected.** Upload-first retained. Guard scope needs widening — Finding 3. |
| **E2E flow 03** (Iter 2 F1 / Iter 3 F5) | `e2e/flows/03_history_and_search_flow.yaml` has no `clearState` and depends on flow 02's session. Iteration 4 makes it self-contained. | ✅ **Resolved.** Drop the alternative it offers ("or toggle demo mode") — that would stop flow 03 exercising the real-data default which is the whole point of Phase 1. |
| **Demo-mode pref migration** (Iter 2 F5 / Iter 3 F14) | `KEY_DEMO_MODE` is read at exactly one place (`DataModeManager.kt:37`) and written at one (`:140`). Deleting persistence means the stale `true` is simply never read again. | ✅ **Resolved and dissolved.** Session-scoping removes the migration entirely; the claim checks out. |
| **`isSuccess \|\| isSuccess` masks a failed upload** (Iter 2 F10) | Still live at `AppViewModel.kt:157`. | ✅ **Resolved in plan** (Component Impact: "Separate upload and download error reporting"). |

### 1. Findings

| # | Severity | Perspective | Finding | Evidence | Recommended action |
| :-- | :--- | :--- | :--- | :--- | :--- |
| 1 | 🔴 **Blocker** | Dev | **The production-seeding fix as specified cannot reach the population this change creates.** On the production path `SeedData.populate` is invoked from exactly one place: the `onCreate` callback in `AppDatabase.build()`, which fires **once**, when `crosstraining.db` is first created. Every existing `v3.0.147` install already has that file — exercises only, no cycle (`SeedData.kt:77`). Phase 1 is precisely what drops those users out of demo mode into that cycle-less DB, so a fix in `SeedData` / `onCreate` reaches only *fresh* installs and leaves the actual affected users unable to save a workout. Scenario 4's "Given a fresh production install" encodes the same blind spot. | `AppDatabase.kt:145-160` (`addCallback(onCreate)`); `SeedData.kt:77`; `Repository.kt:334-336` — `reseedDefaults` forwards `isProduction=false` and is the only existing production path that creates a cycle, but it is `force=true` and sits behind the "restore defaults" button at `ProfileScreen.kt:443` | Provision at start-up or via a Room migration: if `cycleDao.getAllOnce().isEmpty()`, insert one minimal cycle. Extend Scenario 4 with a second Given: *an existing production install upgraded to this build*. **Body edits applied** — Component Impact `SeedData`/`AppDatabase` row and Phase 1 step 4. |
| 2 | 🔴 **Blocker** | Dev / QA | **"`currentUserId` matches `auth.currentUser?.uid` strictly" is unimplementable as worded, in two independent ways.** **(a) Ordering makes the guard vacuous.** `uploadUserData` calls `ensureAuthenticated()` *before* reading `currentUserId`, and `ensureAuthenticated()` signs in anonymously. After that, strict equality is trivially satisfied by the anonymous uid — so a returning real user whose Firebase session is gone silently backs up to an orphan anonymous document while the card reports SUCCESS. That is a *new* silent-failure path introduced by the fix. **(b) There is no JVM seam.** `currentUserId`'s `_userState` branch returns before touching `auth`, which is the only reason `CrossAuthSignInTest` passes on the JVM (executed this pass: green). Bind it strictly and `FirebaseAuth.getInstance()` throws in unit tests, the existing `runCatching` swallows it, and every assertion collapses to `""` — including `:85`, which tests slash sanitisation, a behaviour unrelated to identity binding. "Update the test contract" would then mean deleting coverage, which `.agents/rules/02` §4 forbids. | `UserCloudSyncManager.kt:53-59, 89-101, 248-250, 385-387`; `CrossAuthSignInTest.kt:48-86, 394-403`; `.agents/rules/02_testing_verification.md` §4 | Specify the guard precisely: *before* `ensureAuthenticated()`, if `_userState` holds a non-anonymous identity and `auth.currentUser` is null or anonymous, fail closed with a "please sign in again" result. Add an injectable seam (e.g. `internal var authUidProviderForTesting: (() -> String?)?`) so the renegotiated assertions can assert something, and name the replacement assertions in Phase 2 step 4. |
| 3 | 🟠 Major | Dev | **The overwrite guard is narrower than the destructive surface it protects.** Upload `.set()`s five separate documents; the guard is specified on session count alone. Local routines, exercises, cycle goals or rep-maxes can each be empty while their remote counterpart is populated — e.g. a login where `downloadUserData` failed — and those documents are still wiped. The guard also introduces a remote **read** that `uploadUserData` does not currently perform, inside the existing `withTimeout(20000L)`, and it will now execute on the three buttonless cycle-edit uploads. | `UserCloudSyncManager.kt:248` (timeout), `:267, 297, 345, 360, 374` (five `.set()`s); `AppViewModel.kt:223, 229, 234` | Make it per-document: skip the `.set()` for any collection that is locally empty while remotely non-empty, instead of aborting the whole sync on sessions alone. State the added read cost and whether the 20 s budget still holds. |
| 4 | 🟠 Major | Business / QA | **Requisite 1 — the requirement that opened this document — has no acceptance criterion.** Scenarios 1–5 cover cold start, toggle, isolation, first-run logging and the overwrite guard. **None asserts that "Sync Now" succeeds, or that a failure produces a specific message.** The `ProfileScreen.kt` row promises "friendly error presentation" with nothing testable behind it, and Iteration 1's Invariant 2 (offline message) and Invariant 4 (guest prompt) have silently disappeared from the final plan without being marked out of scope. Guest sync is now *ungated*: a guest tapping "Sync Now" still reaches `ensureAuthenticated()` and, under Finding 2(a), uploads successfully to an anonymous document that is unrecoverable after uninstall. After five iterations the literal error string is still not in this document, so the only possible evidence that Requisite 1 is met is the original reporter on a release build. | BDD section, Scenarios 1–5; Component Impact `ProfileScreen.kt` row; `UserCloudSyncManager.kt:208-219`; `ProfileScreen.kt:412-425` (`enabled = syncState != SYNCING` is still the only guard) | Add a Scenario 6: a successful sync plus at least one mapped-error case; and a release-build acceptance step, *"confirmed by the original reporter on `v3.0.147`+"*. Either restore Invariants 2 and 4 or record them as descoped. |
| 5 | 🟠 Major | Dev / QA | **Routing sync to `data.realRepository` makes the isolation rule impossible to unit-test.** `current` honours `setRepositoryForTesting`; `realRepository` is `by lazy { … error("Context required for real repository") }`. Expose it as-is and every JVM test that constructs `DataModeManager(null)` throws the moment a sync path executes — so Scenario 3, the acceptance criterion for the plan's own headline blocker, becomes device-only. | `DataModeManager.kt:28-31, 122-131`; `AppViewModelVoiceIngestionTest.kt:75-77` | Expose it as `testRepository ?: realRepository`. One line, and it makes Scenario 3 assertable in `app/src/test`. **Body edit applied** — noted on the `AppViewModel.kt` row. |
| 6 | 🟡 Minor | Dev / Product | **Three toggles, still unanswered.** `LibraryScreen.kt:226` keeps its own `setDemoMode`, and `:240` remains the only home of "Reset demo data". Iteration 2 F12 asked this; Iteration 4 does not answer it, and `LibraryScreen.kt` is still absent from the Component Impact table. | `LibraryScreen.kt:221-243`; Component Impact table | Decide in Phase 1: remove the Library toggle and rehome "Reset demo data" into the new `DataModeCard`, or state that three entry points are intended. |
| 7 | 🟡 Minor | Dev / Security | **`recoverAllCloudRoutines` is still unscoped, and Iteration 4 dropped it silently.** It remains a `collectionGroup("data")` query filtered only by environment path, and `recoverCloudRoutines` is one of the nine routed functions, with its button inside the very card being redesigned. Routing it to `realRepository` fixes *which local DB it writes to*, not *whose cloud data it reads*. | `UserCloudSyncManager.kt:545`; `ProfileScreen.kt:428-440` | Scope to `userDoc(currentUserId)` in Phase 2, or record it as knowingly deferred with a rationale. |
| 8 | 🟢 Non-issue | QA | **E2E flow selection needs no action** (closing Iter 2 F16 / Iter 3 F6). Re-tested in the runner: any rule tagged `core` sets `$TriggerFullSuite`, and so does an unmatched file. `DataModeManager.kt`, `AppViewModel.kt`, `SeedData.kt`, `AppDatabase.kt` and `ui/navigation/**` are all tagged `core`; `ProfileScreen.kt` matches no rule. Both phases therefore run all six flows regardless. Iteration 4 was right to drop the mapping edit — and Iteration 3 was right that *making* it would have narrowed coverage. | `scripts/run-e2e-tests.ps1:163-166, 174-176`; `e2e/flow-mapping.json` | None. |

### 2. The two trade-offs the plan still does not acknowledge

**2.1 Phase 1 ships the regression that Phase 2 is supposed to prevent.**
The phases are presented as independently deliverable, but Phase 1 makes the demo toggle prominent on two screens while the three buttonless `uploadUserData(repo)` calls at `AppViewModel.kt:223, 229, 234` still pass `data.current`. Between Phase 1 and Phase 2, a user who flips the new switch to Demo Data and edits a cycle overwrites their real Firestore backup with demo fixtures — the exact hazard Iteration 3 F1 raised, made *more* likely by Phase 1's own UX improvement. **Verdict: an ordering defect, not a design defect.** Move the one-line `repo` → `data.realRepository` change at those three sites into Phase 1, ahead of the switch. It depends on neither identity binding nor the overwrite guard.

**2.2 Session-scoped demo mode is the right call, and it costs something nobody has priced.**
Adopting it dissolves the migration, the returning-guest trap and most of the blast radius — this reviewer agrees it is the best decision in the document. But a coach who spends a session building demo content for planning loses the mode on every cold start, and `refreshDemoIfStale()` at `AppViewModel.kt:195` becomes a permanent no-op (`_demoMode` is always `false` at start-up), so stale demo data is refreshed only by the `seedIfNeeded()` call inside `setDemoMode(true)` — still correct, but now the only path. **Verdict: Minor, accept and record.** One line stating that demo content is explicitly disposable per session closes it.

### 3. The changes required, in execution order

1. **(Phase 1, Blocker)** Respecify production cycle provisioning as a start-up / migration check on `cycleDao.getAllOnce().isEmpty()`, not `onCreate` seeding; extend Scenario 4 to the upgraded-existing-install case. *(Body edits already applied — see §5.)*
2. **(Phase 1, Major)** Move the three buttonless `uploadUserData` sites (`AppViewModel.kt:223, 229, 234`) to `data.realRepository` **in Phase 1**, before the switch becomes prominent (§2.1); and expose `realRepository` as `testRepository ?: realRepository` (Finding 5).
3. **(Phase 2, Blocker)** Rewrite the token-binding rule: check the persisted non-anonymous identity against `auth.currentUser` **before** `ensureAuthenticated()` and fail closed with a re-sign-in prompt; add an injectable auth-uid seam and name the replacement `CrossAuthSignInTest` assertions (Finding 2).
4. **(Phase 2, Major)** Widen the overwrite guard from "sessions == 0" to per-document "locally empty, remotely non-empty" (Finding 3).
5. **(Both phases, Major)** Add Scenario 6 for Requisite 1 — a successful sync plus one mapped-error case — and a release-build acceptance step confirmed by the original reporter; explicitly descope or restore Invariants 2 and 4 (Finding 4).
6. **(Minor)** Decide the `LibraryScreen` toggle's fate (Finding 6) and record `recoverAllCloudRoutines` as fixed or deferred (Finding 7).

With items 1–5 written into the body, this plan is implementable as specified, and this reviewer would sign it off without a further iteration.

### 4. Unverified claims

- **Everything downstream of the Firestore security rules.** Re-confirmed absent from the repo for a third consecutive iteration. Finding 2's remedy is correct regardless of what the rules say, so this no longer blocks — but the `recoverAllCloudRoutines` pincer (Iter 2 F7) still cannot be resolved from source.
- **The literal "Sync Now" error.** Still not in this document. Iteration 4 answered the *variant* question (release `v3.0.147`) but not the *error* question. Finding 4's acceptance step is a substitute, not a resolution.
- **Flow 03 and the full E2E suite against a real-data default.** Reasoned from source; **not executed**. No emulator run was performed in this pass.
- **Finding 1's production first-run behaviour.** Derived from `AppDatabase.kt:145-160` and `SeedData.kt:77`; not confirmed on a release-variant install.

### 5. Body edits applied in this iteration

| Location | Change | Finding |
| :--- | :--- | :--- |
| Component Impact, `AppViewModel.kt` row | Corrected "nine cloud sync calls" to **nine functions across ten call sites**, with exact line numbers; noted the `realRepository` test-seam requirement | §0 row 1, 5 |
| Component Impact, `SeedData.kt` / `AppDatabase.kt` row | Corrected "provisioned on production first-run": `onCreate` fires once at DB creation and never reaches existing installs, so provisioning must be a start-up / migration check | 1 |
| Phased INVEST plan, Phase 1 step 4 | The same correction, stated as the executable instruction | 1 |

*No earlier review iteration was modified. Every Iteration 2 and 3 conclusion reported in §0 was re-derived from source in this pass; one was re-scoped (nine functions / ten call sites) and one was executed (`CrossAuthSignInTest`).*

---

## 🎯 Final Decision Plan & User Story Specification

### User Story
```gherkin
As an athlete or coach using CrossTraining
I want the application to unconditionally start in my real, personal database across every app launch with an intuitive switch to preview demo data
And I want cloud synchronization to be strictly isolated to my real database with token-bound identity, empty-collection overwrite protection, and clear status feedback
So that my workout logs and PR history are protected, unambiguous, and securely synced without risk of corruption from demo fixtures or empty overwrites.
```

---

### Architecture & Data Flow

```
+-----------------------------------------------------------------------------------------------+
|                                     AppNavigation Shell                                       |
|                                                                                               |
|  [Cold Launch / Guest Mode] --------> Unconditionally Defaults to REAL DATA (crosstraining.db)|
|                                       (demoMode is session-scoped, in-memory MutableStateFlow)|
|                                                                                               |
|  +------------------------+             +--------------------------------------------------+  |
|  |  Navigation Drawer     |             |  Profile Screen                                  |  |
|  |  [DataModeDrawerRow]   | <---------> |  [Stateless DataModeCard]                        |  |
|  |  - Real Data (Default) |             |  - "My Real Data (Default)" vs "Demo Data"       |  |
|  |  - Demo Data           |             +--------------------------------------------------+  |
|  +-----------+------------+                                      |                            |
|              | (toggles in-memory StateFlow)                     |                            |
|              v                                                   v                            |
|  +-----------------------------------------------------------------------------------------+  |
|  |                                    DataModeManager                                      |  |
|  |  - demoMode: StateFlow<Boolean> (In-memory ONLY, Default = FALSE, never saved to prefs)  |  |
|  |  - current: Repository (switches UI live between realRepository and demoRepository)     |  |
|  |  - realRepository: Repository (testRepository ?: realRepository, exposed for sync)     |  |
|  +-----------------------------------------------------------------------------------------+  |
|                                         |                                                     |
|                                         | (UI flows rebind live)                              |
|                                         v                                                     |
|  +-----------------------------------------------------------------------------------------+  |
|  |                                       UI Screens                                        |  |
|  |   (Log, History, Cycles, Library, Progress rebind to active repository)                 |  |
|  +-----------------------------------------------------------------------------------------+  |
|                                                                                               |
|  +-----------------------------------------------------------------------------------------+  |
|  |                         Cloud Sync Engine (AppViewModel & SyncManager)                  |  |
|  |                                                                                         |  |
|  |  STRUCTURAL ISOLATION RULE:                                                             |  |
|  |  All 9 sync functions (10 call sites):                                                  |  |
|  |  - Phase 1: saveCycle, saveCycleWithGoals, deleteCycleGoal                              |  |
|  |  - Phase 2: triggerCloudSync (upload & download), signUp, logIn, logInGoogle, recover   |  |
|  |  pass `data.realRepository` UNCONDITIONALLY. Demo fixtures cannot reach Firestore.      |  |
|  |                                                                                         |  |
|  |  PER-DOCUMENT OVERWRITE GUARD:                                                          |  |
|  |  Before .set(), uploadUserData verifies remote collection state. If a local collection |  |
|  |  is empty while the remote collection is non-empty, .set() is skipped for that doc.    |  |
|  |                                                                                         |  |
|  |  TOKEN-BOUND IDENTITY & SEAM:                                                           |  |
|  |  Before ensureAuthenticated(), if _userState is non-anonymous and auth.currentUser is   |  |
|  |  null or anonymous, fails closed with re-authentication prompt.                         |  |
|  |  Injectable authUidProviderForTesting enables JVM test coverage without Firebase Auth. |  |
|  |                                                                                         |  |
|  |  PRODUCTION STARTUP CYCLE PROVISIONING:                                                 |  |
|  |  On app launch, if cycleDao.getAllOnce().isEmpty(), inserts a default training cycle,   |  |
|  |  protecting both fresh installs and upgraded v3.0.147 installs from logging blocks.    |  |
|  +-----------------------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------------------+
```

---

### BDD Acceptance Criteria

#### Scenario 1: Cold Start & Guest Launch Always Opens in Real Data
```gherkin
Given a user launches the app or taps "Continue as Guest" on the Welcome Screen
When the main screen mounts
Then the active data mode is "Real Data" (demoMode is false in-memory)
And no yellow "Demo data" banner is displayed
And the database points to the athlete's personal Room database (crosstraining.db)
And closing the app and reopening it always resets to Real Data
```

#### Scenario 2: Dynamic Live Toggle via Stateless DataModeCard & Drawer
```gherkin
Given any user on the Profile Screen or in the Navigation Drawer
When they locate the "Data Mode" section
Then they see a switch clearly indicating "Real Data (Default)" is active
When the user flips the switch to "Demo Data"
Then the UI instantly re-binds to crosstraining-demo.db and the yellow demo banner appears
When the user flips the switch back to "Real Data"
Then the yellow banner disappears and their personal real database is restored
```

#### Scenario 3: Structural Isolation of Cloud Sync from Demo Fixtures
```gherkin
Given the user has toggled Data Mode to "Demo Data"
When they edit or save a training cycle, or add/delete cycle goals
Then the local demo cycle is updated in crosstraining-demo.db
And the background cloud sync task passes realRepository, preventing demo fixtures from reaching Firestore
And tapping "Sync Now" in Profile Screen informs the user: "Cloud Sync operates on your Real Data"
```

#### Scenario 4: First-Run Workout Logging on Fresh and Upgraded Production Installs
```gherkin
Given a production install with an existing or fresh cycle-less database (upgraded from v3.0.147 or fresh install)
When the application launches and the athlete navigates to the Log Session screen
Then a default initial training cycle is automatically provisioned
And the athlete can log and save their workout immediately without hitting "Create and select a cycle first"
```

#### Scenario 5: Per-Document Cloud Backup Overwrite Protection
```gherkin
Given an athlete signs into an existing cloud account on a new device with partially populated local tables
When cloud sync is triggered
Then uploadUserData inspects remote collection state before uploading
And for any document that is locally empty but remotely populated, the destructive .set() overwrite is skipped
And the remote cloud data is preserved intact
```

#### Scenario 6: Robust "Sync Now" Execution and Error Feedback (Requisite 1)
```gherkin
Given an authenticated user in Real Data mode with a matching Firebase token
When they tap "Sync Now" in the Profile Screen
Then uploadUserData and downloadUserData execute against realRepository
And upon completion, the sync state updates to SUCCESS with "Cloud sync completed!"
When an unauthenticated guest taps "Sync Now"
Then the authentication modal opens with "Please sign in to back up workouts to the cloud"
When network connectivity is unavailable
Then the snackbar displays "Network unavailable. Your data is saved locally on this device"
And no unhandled runtime exceptions or raw Firestore error codes are displayed
And verified on release build v3.0.147+ by the original reporter
```

---

## 🔍 Review Iteration 6: Post-Implementation Forensic Analysis — The Literal "Sync Now" ERROR Diagnosis & Cloud Auth Seam Disconnect

- **Date / Reviewer:** 2026-09-03 | Three Amigos (Business / Development / QA)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ `2cdca7f`
- **Triggering Event / Evidence:**
  - Operator provided screenshot `media_1788451689183.png` capturing the **Cloud Backup & Sync** card in `ERROR` status (`syncState == SyncStatus.ERROR`, rendering `[ (!) ERROR ]` badge) on a live Android installation following commits `3fa05ab` through `2cdca7f`.
  - Operator stated: *"There is an error when I try to sychronize the data with the account."*
- **Scope reviewed:**
  - `UserCloudSyncManager.kt` (`verifyTokenBinding`, `uploadUserData`, `downloadUserData`, `currentUserId`).
  - `AppViewModel.kt` (`triggerCloudSync`, session rehydration).
  - `DataModeManager.kt` (`getPersistedAuthUser`, `saveAuthSession`).
  - `ProfileScreen.kt` (Cloud Backup Card, Auth Modal, error snackbars).
  - `app/google-services.json` (OAuth configuration).
  - `app/build.gradle.kts` (`APP_ENV = "production"`).

---

### 1. Root Cause Analysis: Why "Sync Now" Produces `ERROR`

#### Root Cause 1: Token-Binding Deadlock on Upgraded / Persisted Sessions (🔴 Blocker)
In commit `2cdca7f`, `verifyTokenBinding()` was introduced:
```kotlin
val currentUser = runCatching { auth.currentUser }.getOrNull()
if (currentUser == null || currentUser.isAnonymous || currentUser.uid != user.uid) {
    return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
}
```
1. On app launch, `AppViewModel.init` reads `data.getPersistedAuthUser()` from `SharedPreferences`.
2. For all installs upgraded from earlier versions or where login occurred prior to `2cdca7f`, `user.uid` in `SharedPreferences` was stored as the email address (e.g. `"pv.joseangel@gmail.com"` via `logInWithGoogleAccount:251` or `"jangelpv_crosstraining_app"`).
3. In contrast, `FirebaseAuth.getInstance().currentUser?.uid` is a 28-character Firebase alphanumeric UID (e.g. `"w7X2yZ8abc..."`).
4. Because `currentUser.uid != user.uid` (`"w7X2yZ8abc..." != "pv.joseangel@gmail.com"`), `verifyTokenBinding()` **fails closed unconditionally** before `ensureAuthenticated()` or Firestore network traffic can run!
5. Furthermore, if `FirebaseAuth`'s asynchronous token restoration from disk has not completed by the time `Sync Now` is tapped, `currentUser` is `null`, producing the same immediate failure.
6. In `AppViewModel.kt:174`, `uploadRes.isSuccess` is `false`, immediately executing:
   `UserCloudSyncManager.setSyncStatus(SyncStatus.ERROR)`
   which sets the persistent red `[ (!) ERROR ]` badge shown in the user's screenshot.

#### Root Cause 2: Missing OAuth Web Client ID in `google-services.json` (🔴 Blocker)
1. In `app/google-services.json`, `"oauth_client": []` is empty.
2. The Google Services Gradle plugin does not generate `@string/default_web_client_id`.
3. `LoginWelcomeScreen.kt:348` and `ProfileScreen.kt:756` fall back to `"384900244521.apps.googleusercontent.com"`, which is **not** a valid Web Client ID (Google OAuth Web Client IDs require the full `<project_number>-<hash>.apps.googleusercontent.com` client format).
4. Calling Credential Manager with this invalid Client ID causes Google Play Services to fail with `ApiException: 10` (Developer Error), preventing genuine Google OAuth token generation and forcing fallback paths.

#### Root Cause 3: Card-Level Error Invisibility & Lack of Recovery UX (🟠 Major)
1. When `triggerCloudSync` fails, `ProfileScreen.kt` fires a transient snackbar via `snackbar.showSnackbar(message)`.
2. Once the snackbar dismisses (after 4 seconds), the card displays only `[ (!) ERROR ]` with zero explanation of why it failed (e.g. session expired, token mismatch, offline, permission denied).
3. The user has no contextual action button inside the card (such as `[Re-authenticate]` or `[Sign In to Restore]`) to resolve the state.

#### Root Cause 4: Sequential Write Latency Exposure (🟡 Minor)
1. `uploadUserData` performs sequential `.set()` calls across 5 documents plus potential remote reads inside a `withTimeout(20000L)` coroutine. On high-latency mobile connections, sequential network round-trips can exceed the 20-second timeout.

---

### 2. Concrete Architectural Remediation

| Issue Area | Flaw in Code | Targeted Remediation |
| :--- | :--- | :--- |
| **Self-Healing Token Alignment** | `verifyTokenBinding` rejects valid users whose persisted UID is an email address. | If `currentUser != null` and `currentUser.email == user.email`, automatically self-heal: update `_userState.value` to match `currentUser.uid` and persist via `data.saveAuthSession(...)`, allowing sync to proceed smoothly. |
| **Asynchronous Auth Wait** | `currentUser` can be transiently null on cold start while Firebase Auth loads tokens. | In `verifyTokenBinding`, if `auth.currentUser == null`, await the initial auth state resolution (up to 3 seconds) before failing closed. |
| **Inline Error Feedback in Card** | User only sees `[ (!) ERROR ]` badge with no error reason or remedy. | In `ProfileScreen.kt`, add an inline error container inside `Cloud Backup & Sync` when `syncState == SyncStatus.ERROR` showing the mapped error message and a dedicated `[Sign In Again]` button if re-authentication is required. |
| **Parallelized Firestore Writes** | 5 sequential `.set()` calls risk network timeouts. | Execute document uploads concurrently using `coroutineScope` and `async`. |

---

## 🎯 Final Decision Plan & User Story Specification

### User Story
```gherkin
As an athlete or coach using CrossTraining
I want the application to automatically align my persisted login session with my active Firebase Auth token, parallelize cloud backups, and display clear inline error feedback if re-authentication is needed
So that tapping "Sync Now" reliably synchronizes my personal data without false token-mismatch rejections or unexplained error badges.
```

---

### BDD Acceptance Criteria

#### Scenario 1: Self-Healing Token Alignment for Persisted Email Sessions
```gherkin
Given a user whose local session has persisted user.uid as their email address
And Firebase Auth holds a valid authenticated session for that same email with a Firebase UID
When they tap "Sync Now" in Cloud Backup & Sync
Then verifyTokenBinding detects the email match, updates the local UID to the Firebase UID, and persists it
And the upload and download proceed without throwing GUEST_AUTH_PROMPT
And the sync status badge transitions from SYNCING to SUCCESS ("Cloud sync completed!")
```

#### Scenario 2: Asynchronous Firebase Auth Cold-Start Resilience
```gherkin
Given the application was cold-started and FirebaseAuth token restoration is in progress
When the user taps "Sync Now" immediately
Then verifyTokenBinding awaits auth state resolution rather than instantly rejecting with a null user
And once the authenticated user resolves, synchronization proceeds to SUCCESS
```

#### Scenario 3: Actionable Inline Error Card on Genuine Auth Expiry
```gherkin
Given an athlete whose Firebase session has expired or been revoked in the console
When they tap "Sync Now"
Then the Cloud Backup & Sync card displays the ERROR badge
And an inline banner explains: "Session expired. Please sign in again to back up your workouts"
And a prominent [Sign In Again] button is provided directly inside the card to restore access
```

#### Scenario 4: Concurrent Cloud Backup Execution
```gherkin
Given an authenticated user in Real Data mode with exercises, routines, cycles, and sessions
When cloud sync upload is executed
Then the collections are uploaded concurrently via coroutineScope
And the total upload time is reduced, preventing 20-second timeout cancellations
```

---

### Component Impact Table

| Component File | Location | Concrete Changes |
| :--- | :--- | :--- |
| **`UserCloudSyncManager.kt`** | `app/.../data/firebase/UserCloudSyncManager.kt` | - Update `verifyTokenBinding`: if `currentUser != null` and `currentUser.email == user.email`, self-heal UID alignment and save session.<br>- Await auth state resolution if `currentUser` is null on cold start.<br>- Execute collection uploads concurrently using `coroutineScope { awaitAll(...) }`. |
| **`ProfileScreen.kt`** | `app/.../ui/screens/ProfileScreen.kt` | - Add inline error banner inside `Cloud Backup & Sync` card showing error detail and a `[Sign In Again]` button when authentication is required.<br>- Expose the latest sync error message in UI state so it remains visible after snackbar dismisses. |
| **`AppViewModel.kt`** | `app/.../ui/AppViewModel.kt` | - Expose `lastSyncError: StateFlow<String?>` to allow UI to render persistent error context.<br>- Clear `lastSyncError` on successful sync. |

---

### INVEST Subtask Breakdown

1. **Subtask 1: Self-Healing Identity Alignment & Cold-Start Auth Await**
   - Enhance `verifyTokenBinding()` to reconcile email-matched sessions and await Firebase Auth initialization.
   - Add unit tests verifying that email-matched users with disparate UIDs are automatically self-healed and synced.

2. **Subtask 2: Concurrent Cloud Uploads & Timeout Resilience**
   - Refactor `uploadUserData` to upload documents concurrently with `awaitAll`.
   - Add unit tests verifying parallel upload behavior and error isolation.

3. **Subtask 3: Actionable Card-Level Error Presentation & Re-Auth Button**
   - Add inline error container to `Cloud Backup & Sync` card in `ProfileScreen.kt`.
   - Add `[Sign In Again]` action triggering the auth modal directly when token is expired.
   - Verify with Compose UI instrumented tests.

---

## 🏛️ Claude Review Iteration 1

- **Date / Reviewer:** 2026-09-03 | Principal Architect (Claude Sonnet — Thinking)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ post-`2cdca7f` working tree
- **Scope:** Ground-truth inspection of `UserCloudSyncManager.kt` (774 L, full), `DataModeManager.kt` (185 L, full), `AppViewModel.kt` (753 L, full), `ProfileScreen.kt` (lines 95–500), `docs/draft-requisites/implementation-plan.md` (all 6 review iterations + Final Decision Plan). Every claim below was verified directly against source, not inherited from prior iterations.

---

### ⚖️ Critical Architecture & Drawbacks Critique

#### Critique 1: Self-Healing by Email Match Is a Security Regression Masquerading as a Hotfix (🔴 Blocker)

The proposed remedy for Root Cause 1 reads:

> *"If `currentUser != null` and `currentUser.email == user.email`, automatically self-heal: update `_userState.value` to match `currentUser.uid` and persist via `data.saveAuthSession(…)`"*

**Ground-truth verification of `verifyTokenBinding()` (`UserCloudSyncManager.kt:312–336`):**

The current `verifyTokenBinding` already performs the uid-match check correctly: line 332 evaluates `currentUser.uid != user.uid` and fails closed. The proposed self-heal replaces this hard gate with an email-equality bypass. This is architecturally dangerous for three compounding reasons:

1. **Email is not a unique identity key in Firebase.** A user can change their email in the Firebase console. The persisted `savedUserEmail` in `SharedPreferences` reflects the email at login time; `auth.currentUser.email` reflects the current email. After an email-change, `user.email == currentUser.email` is `true`, `user.uid != currentUser.uid` is `false` (same user, same uid) — so the self-heal is a no-op. However, the persisted uid is `"pv.joseangel@gmail.com"` (an email string), meaning `user.uid != currentUser.uid` is `true` while `user.email == currentUser.email` is also `true`. The self-heal fires, promotes the Firebase UID to `_userState`, and calls `saveAuthSession` — so far correct. But the guard at `currentUser == null || currentUser.isAnonymous || currentUser.uid != user.uid` was **already** doing exactly the right thing for legitimate users: rejecting mismatches. The real problem is that the *persisted uid* is a malformed email string, not that the guard is too strict. The fix should sanitise what is written into `saveAuthSession`, not weaken what is read back.

2. **The self-heal path silently grants Firestore access under the wrong uid on first attempt.** After self-healing, `currentUserId` returns `currentUser.uid.replace("/","_")` (line 65). The document being written to is `environments/{env}/users/{newUid}`. If the user already has data under `environments/{env}/users/{oldEmailStringUid}` (which any user who logged in before `2cdca7f` would), the self-heal writes to a *different Firestore document* than their historical backup. The old document is orphaned silently with no migration, no notification, and no merge. Their cloud backup is effectively invisible until the next sync under the corrected uid — at which point the overwrite guard (per-document empty check) has no awareness of the orphaned document and will `.set()` whatever is local, potentially destroying partially synced cloud state.

3. **Cold-start race condition is not actually fixed by the proposed await.** The plan proposes awaiting auth state resolution up to 3 seconds. In `ensureAuthenticated()` (`UserCloudSyncManager.kt:97–113`) there is already a `withTimeout(5000L)` anonymous sign-in path. The proposed "await up to 3 seconds" is a separate, additive delay. The combined worst-case latency before `verifyTokenBinding` even completes is now 3 s (await) + potential 5 s (anonymous sign-in) = 8 s before the 20 s upload timeout even starts. On a high-latency connection the total budget is: 8 s pre-flight + 5 sequential Firestore reads (overwrite guard) + 5 sequential `.set()` writes = easily > 20 s, making `TimeoutCancellationException` the dominant failure mode after this change ships — worse than baseline.

**Required change:** Instead of self-healing in `verifyTokenBinding`, detect the "persisted uid looks like an email" at `saveAuthSession` time (`DataModeManager.kt:78–91`) and refuse to persist non-UID strings. Add a one-time startup migration in `AppViewModel.init` that clears `KEY_SAVED_USER_UID` if it contains an `@` character, forcing re-authentication once. This is a single-launch prompt rather than a silently mutating identity.

---

#### Critique 2: `coroutineScope { awaitAll(...) }` Parallelisation Has Unmodelled Failure Semantics (🔴 Blocker)

The plan specifies:

> *"Execute document uploads concurrently using `coroutineScope` and `async`."*

**Ground-truth verification of `uploadUserData` (`UserCloudSyncManager.kt:376–543`):**

The five `uploadCollectionWithGuard` calls are currently sequential. Each call conditionally performs a remote Firestore **read** (to check if remote is populated) followed by a remote **write** (`.set()`). Parallelising them with `coroutineScope { awaitAll(...) }` introduces two architectural hazards:

1. **`coroutineScope` propagates the first child failure to all siblings via structured concurrency cancellation.** If the `sessions` document upload fails with a `FirebaseFirestoreException` (e.g., permission denied), `coroutineScope` cancels the remaining in-flight writes. The result is a **partial upload**: exercises may be uploaded, sessions may not be, and the Firestore state is now inconsistent. The current sequential model has the same partial-upload risk, but the failure stops at the failing document rather than racing to cancel siblings mid-write. With parallelisation, partial state is more likely and harder to characterise.

2. **The `withTimeout(20000L)` is already shared across all five operations.** Sequential execution already allows each operation its full budget within the overall timeout. Parallel execution reduces the *per-document* time available to the timeout's full 20 s, which is net positive for latency — but the overwrite-guard reads (five remote Firestore GET calls) now also run in parallel. Firestore's [concurrent read limits](https://firebase.google.com/docs/firestore/quotas) are not modelled anywhere in this plan. Under a poor connection, five concurrent reads may each timeout individually, triggering five individual `runCatching` swallowed errors, yielding the same `TimeoutCancellationException` at the outer scope but with no actionable signal as to which collection failed.

3. **The `cleanupDuplicateRoutines()` side effect at line 412 is a destructive local write that precedes the routines upload.** Parallelising means `cleanupDuplicateRoutines()` could run concurrently with the exercises upload. `cleanupDuplicateRoutines` executes a `deleteAll` + re-insert inside a Room transaction (`Repository.kt:168–180`). While Room transactions are serialised at the SQLite level, the **exercises upload reads via `getAllExercisesOnce()`** before the dedup runs. If exercise IDs referenced by routines are deleted by the dedup during a parallel exercises read, the uploaded exercises payload may reference routines that no longer exist after the dedup. This is a pre-existing ordering hazard that parallelisation makes reachable in new interleavings.

**Required change:** If parallelisation is adopted, use a `supervisorScope` instead of `coroutineScope` to prevent sibling cancellation on single-document failures. Collect each `Deferred` result individually, accumulate per-document errors, and report them in aggregate. Also move `cleanupDuplicateRoutines()` to a pre-flight step before any parallel upload is launched.

---

#### Critique 3: `verifyTokenBinding` Is Called After `_syncState` Is Set to `SYNCING` — Error State Is Sticky and Unresettable (🟠 Major)

**Ground-truth verification (`UserCloudSyncManager.kt:382–542`):**

`uploadUserData` sets `_syncState.value = SyncStatus.SYNCING` at line 382, *then* calls `verifyTokenBinding().getOrThrow()` at line 385. If `verifyTokenBinding` fails, the `runCatching` block's `.onFailure` handler at line 540 sets `_syncState.value = SyncStatus.ERROR`. **There is no path in the current or proposed code that resets `_syncState` to `IDLE`.**

Consequences:
- The `ProfileScreen.kt` "Sync Now" button guard (`enabled = syncState != SyncStatus.SYNCING`) at line 463 does not prevent retries in `ERROR` state — that is correct design. But the plan's proposed inline error banner persists until the user taps "Sign In Again" and auth succeeds, which triggers `triggerCloudSync` again, setting `SYNCING` before the new `verifyTokenBinding` runs. If that also fails, `ERROR` is re-set. There is no `IDLE` reset between attempts. A user who is repeatedly failing (e.g., wrong password, server outage) sees `ERROR → SYNCING → ERROR` in a tight loop with no timeout or back-off, and the singleton `_syncState` on `UserCloudSyncManager` (an `object`) means any concurrent background upload (from `saveCycle`, `saveCycleWithGoals`, `deleteCycleGoal`) will overwrite the ERROR badge with `SYNCING` at an arbitrary time.

**Required change:** Introduce an explicit `reset()` call that sets `_syncState` to `IDLE` before each user-initiated sync attempt in `triggerCloudSync`. Add exponential back-off or a minimum cooldown (e.g., 5 s) between retry attempts in the `ProfileScreen` "Sync Now" handler to prevent error-loop hammering of Firestore.

---

#### Critique 4: Self-Healing Persists via `data.saveAuthSession(...)` — But `saveAuthSession` Has a `remember: Boolean` Parameter That Defaults to `true` (🟠 Major)

The proposed self-heal writes: `data.saveAuthSession(email, correctedUid, isAnon, remember = true)`. Verified in `DataModeManager.kt:78`:

```kotlin
fun saveAuthSession(email: String?, uid: String?, isAnon: Boolean = false, remember: Boolean = true) {
    if (!remember || email.isNullOrBlank() || uid.isNullOrBlank()) {
        clearAuthSession()
        return
    }
```

The self-heal unconditionally persists the corrected uid with `remember = true`, bypassing the user's original "Remember Me" preference. A user who explicitly opted out of session persistence (called `saveAuthSession` with `remember = false` at login, resulting in a cleared `KEY_REMEMBER_ME`) will have their preference silently overridden by the self-heal. After this change, every user who has a persisted email-string uid — including those who signed in without "Remember Me" — will have a permanent session written to `SharedPreferences` during the self-heal, even if they never intended that. This is a privacy regression.

Furthermore, the plan does not specify how `verifyTokenBinding` accesses `DataModeManager` to call `saveAuthSession`. `UserCloudSyncManager` is currently a standalone `object` with no reference to `DataModeManager`. Injecting this dependency requires either: (a) passing `DataModeManager` as a parameter to `verifyTokenBinding` (which changes the `uploadUserData` and `downloadUserData` signatures), or (b) passing a `saveSession: (email: String, uid: String) -> Unit` lambda. Neither approach is specified in the plan, and both have testability implications for the `CrossAuthSignInTest` contract that is currently being renegotiated.

---

#### Critique 5: Scenario 2 (Cold-Start Auth Await) Has No Testable BDD Criterion and the Wait Mechanism Is Unspecified (🟠 Major)

**Scenario 2:**
```
Given the application was cold-started and FirebaseAuth token restoration is in progress
When the user taps "Sync Now" immediately
Then verifyTokenBinding awaits auth state resolution rather than instantly rejecting with a null user
And once the authenticated user resolves, synchronization proceeds to SUCCESS
```

This scenario is not testable as written. `FirebaseAuth` token restoration is an async Firebase internal process with no observable deterministic signal exposed to the application. The plan does not specify:
- Whether "awaiting" means polling `auth.currentUser` in a loop, subscribing to `auth.addAuthStateListener`, or using a `CompletableDeferred`.
- What happens if `auth.currentUser` resolves to `null` (no previously authenticated user) vs. an anonymous uid vs. a Google uid — these are three different outcomes that each require a different response.
- Whether the 3-second wait is a hard timeout or a cooperative cancellation. If `triggerCloudSync` is launched via `viewModelScope.launch` and the user navigates away, the coroutine should be cancelled; but a blocking 3-second wait inside `verifyTokenBinding` prevents cancellation unless `withTimeout` or `delay` is used (both of which are cooperatively cancellable).

The `authUidProviderForTesting` seam (`UserCloudSyncManager.kt:54`) exists and is already wired in the unit tests. However, simulating "Firebase Auth is still loading" requires the test to control the timing of when `authUidProviderForTesting` returns a value, which the current `() -> String?` return type (synchronous lambda) cannot express. The testability seam is insufficient for Scenario 2's coverage.

---

### 🚨 Unresolved Concerns & Edge Case Vulnerabilities

**Concern A: Orphaned Firestore Documents After Self-Heal (Data Loss, unacknowledged)**

Every user who logged in via `logInWithGoogleAccount` before commit `2cdca7f` has their cloud data stored under `environments/production/users/{emailString}` (e.g., `/users/pv.joseangel@gmail.com`). After self-heal, future syncs write to `environments/production/users/{firebaseUid}` (e.g., `/users/w7X2yZ8abc`). The old document is never migrated, merged, or deleted. This plan does not provision a Firestore migration, a one-time cloud-side read from the old path, or any notification to the user. If the user's existing workout data is in the old document and the self-heal succeeds without migrating it, the next `downloadUserData` finds the new document empty, the overwrite guard sees `local sessions > 0`, and the upload proceeds — writing the user's locally cached data back to the new path. This is the *best case*. If the user reinstalled between the old-uid write and the self-heal (losing local data), they now have an empty local database, an empty new-uid document, and their data is permanently inaccessible in the old-uid document with no recovery path.

**Concern B: `ensureAuthenticated()` Anonymous Sign-In Still Runs After `verifyTokenBinding` Succeeds (Architecture Smell)**

At `UserCloudSyncManager.kt:386`, `ensureAuthenticated()` runs *after* `verifyTokenBinding().getOrThrow()` succeeds. If `verifyTokenBinding` succeeds because the user has a valid non-anonymous Firebase session, `ensureAuthenticated()` checks `if (_userState.value != null && _userState.value?.uid?.isNotBlank() == true) { return }` (line 98) and returns immediately — correct. However, if `verifyTokenBinding` succeeds via the self-heal path (after correcting the uid), `_userState.value` is now set to the corrected user, so `ensureAuthenticated()` returns early. But `auth.currentUser` already holds the Google token (that's what `verifyTokenBinding` found). The anonymous sign-in is therefore dead code for this path, which is correct behaviour — but the plan does not explain this, and a future developer might misread `ensureAuthenticated()` as the authentication step and delete `verifyTokenBinding`, reintroducing the original defect.

**Concern C: `recoverAllCloudRoutines` Still Queries `userDoc(currentUserId)` — Now Reads From Potentially Wrong Path (Pre-Existing + Worsened)**

After self-heal, `currentUserId` returns the corrected Firebase UID. `recoverAllCloudRoutines` (`UserCloudSyncManager.kt:713–766`) reads from `userDoc(currentUserId).collection("data").document("routines")`. For users whose historical routines are stored under the old email-string uid, this query returns empty. The user then sees "No previous routines found in cloud." when their routines exist but at a different Firestore path. This is a silent data-invisible failure that the plan classifies as a "recoverable" fallback but is actually a permanently broken experience for the target population (all pre-`2cdca7f` users).

**Concern D: The Final Decision Plan's Component Impact Table Is Truncated — It Ends Mid-Table**

Verified at line 719–720 of the implementation plan:
```
| Component File | Location | Concrete Changes |
---
```
The table header is present but the rows are absent. The Component Impact table from Review Iteration 5's Final Decision Plan was never completed before Review Iteration 6 was appended. The document therefore has two Final Decision Plan sections (one post-Iteration 5, one post-Iteration 6), both incomplete, and the second supersedes the first without explicitly saying so. An implementer reading this document cannot determine the complete set of concrete changes required.

**Concern E: Scenario 4 (Concurrent Upload) Does Not Assert Error Isolation**

Scenario 4 asserts only that "total upload time is reduced" and "preventing 20-second timeout cancellations." It does not assert what happens when one of the concurrent uploads fails. With `coroutineScope`, a single failure cancels all siblings. With `supervisorScope`, failures are isolated. The scenario's definition of "success" is ambiguous: does a partial upload (4/5 documents succeeded) count as `SyncStatus.SUCCESS` or `SyncStatus.ERROR`? The current `_syncState` model is binary and cannot express partial success. This gap means the acceptance criterion can be satisfied by either a `coroutineScope` or a `supervisorScope` implementation, with radically different failure characteristics, and the test suite will not distinguish between them.

---

### 🛠️ Mandatory Architectural Safeguards & Required Changes

| Priority | Component | Required Change | Rationale |
| :--- | :--- | :--- | :--- |
| **🔴 Blocker** | `DataModeManager.saveAuthSession` | Validate that `uid` parameter is not an email string before persisting. Reject and log a warning if `uid.contains("@")`. | Prevents the email-as-uid defect from re-entering `SharedPreferences` after any future auth code change. |
| **🔴 Blocker** | `AppViewModel.init` | Add a one-time startup migration: if `KEY_SAVED_USER_UID` contains `@`, call `clearAuthSession()` and set a flag prompting the user to re-sign-in, rather than silently self-healing. Present a "Please sign in again to restore your cloud connection" bottom sheet or card. | Eliminates orphaned-document risk. Forces a clean re-authentication that establishes the Firebase UID from a real token, not from an email match. |
| **🔴 Blocker** | `uploadUserData` parallelisation | Use `supervisorScope` + `async` + per-document result collection, not `coroutineScope { awaitAll(...) }`. Aggregate per-document errors and surface them. Move `cleanupDuplicateRoutines()` to a pre-flight step before any `async` block. | Prevents sibling cancellation on single-document failures and removes the dedup/read interleaving hazard. |
| **🟠 Major** | `verifyTokenBinding` cold-start wait | Use `auth.addAuthStateListener` + a `CompletableDeferred<FirebaseUser?>` with a 3 s timeout to observe the first auth state event, rather than polling or a bare `delay`. Handle the three outcomes (null → anonymous user; anonymous uid → fail closed with re-auth prompt; real uid → proceed) explicitly. | The proposed "await" mechanism is unspecified; this makes it implementable and cancellable within `viewModelScope`. |
| **🟠 Major** | `UserCloudSyncManager._syncState` | Add a `resetSyncState()` method (or expose `IDLE` reset in `triggerCloudSync` before each attempt). Add a minimum 5 s cooldown in `ProfileScreen` between "Sync Now" taps. | Prevents ERROR→SYNCING→ERROR hammering and background-write badge pollution. |
| **🟠 Major** | `ProfileScreen.kt` inline error | The proposed "Sign In Again" button must invoke `logInWithGoogle` via Credential Manager → `signInWithGoogleCredential`, not `logInWithGoogleAccount`. The broken `AccountManager` path (`ProfileScreen.kt:626`) must be migrated in the same PR as the inline error button; otherwise the button leads users into the path that produced the original bug. | Avoids recreating Root Cause 2 (invalid OAuth Web Client ID + anonymous fallback) via the new re-auth entry point. |
| **🟠 Major** | Complete the Component Impact Table | The Final Decision Plan (post-Iteration 6) must include a complete Component Impact table enumerating all concrete changes per file. The truncated table from the post-Iteration 5 plan must either be completed or explicitly superseded. | An implementer cannot determine the full scope of changes from the current document. |
| **🟡 Minor** | `Scenario 2` BDD | Rewrite to assert a specific observable outcome: "the `authUser` StateFlow transitions from `null` to a non-null value, and `syncState` transitions from `IDLE` to `SYNCING` to `SUCCESS`." Remove the un-testable implementation detail ("awaits auth state resolution") from the Given/Then clauses. | BDD scenarios must be testable against observables, not implementation internals. |
| **🟡 Minor** | `recoverAllCloudRoutines` orphaned-path | After self-heal corrects the uid, `recoverAllCloudRoutines` should attempt to read from both the corrected uid path and the email-string uid path (if the old path is detectable from `SharedPreferences`), merge results, and trigger a one-time migration write to the corrected path. Or, document explicitly that routines stored under the old uid are permanently inaccessible and the user must manually re-enter them. | Silent data-invisible failure is worse than an explicit "we could not find your old cloud routines, please contact support." |

---

### 🏁 Verdict

**Summary of Principal Architectural Objections:**

1. **The self-heal-by-email-match mechanism replaces one identity defect with another.** Correcting the uid in `_userState` at sync time without validating uid format at write time guarantees the defect re-enters `SharedPreferences` on the next login via any broken code path. The correct fix is upstream sanitisation at `saveAuthSession`, not downstream self-healing at `verifyTokenBinding`.

2. **Self-healing creates orphaned Firestore documents for the entire pre-`2cdca7f` user population.** Their historical cloud data sits under email-string uid paths; the self-heal silently redirects future writes to Firebase-uid paths. Without a documented Firestore migration strategy, this is a silent data-loss mechanism for exactly the users the fix is meant to serve.

3. **`coroutineScope { awaitAll(...) }` parallelisation without `supervisorScope` converts five sequential partial-failure risks into a single all-or-nothing failure with unpredictable sibling cancellation.** The `cleanupDuplicateRoutines()` pre-upload side effect creates a new data-hazard interleaving that is only safe if it is moved to a pre-flight step.

4. **The cold-start auth wait is unspecified, and the testability seam (`authUidProviderForTesting: () -> String?`) is synchronous and cannot simulate asynchronous token resolution.** Scenario 2 is currently unverifiable.

5. **The Final Decision Plan is structurally incomplete** — the Component Impact table is truncated, two competing "Final Decision Plan" sections exist in the document, and the implementer cannot determine the authoritative scope from the current document.

`VERDICT: DISAGREED`

---

## 🔍 Review Iteration 7: Gemini Response & Consensus Alignment (Round 2)

- **Date / Reviewer:** 2026-09-03 | Antigravity AI Architect (Gemini)
- **Target Repository:** `AntaresAndBharani/crosstrainingapp` @ post-`2cdca7f` working tree
- **Context:** Formal response to Claude Review Iteration 1 (Sonnet Thinking). All 5 architectural objections and safeguards are evaluated, accepted, and incorporated into the consolidated design.

---

### 1. Architectural Consensus & Concession Matrix

| Claude Objection / Finding | Severity | Gemini Assessment | Architectural Concession & Concrete Design Decision |
| :--- | :--- | :--- | :--- |
| **Objection 1: Downstream Email Self-Heal creates orphaned cloud documents and masks bad writes.** | 🔴 **Blocker** | **FULL CONCESSION** | **Eliminate downstream self-heal in `verifyTokenBinding`.**<br>1. **Upstream Sanitization:** Add assertion in `DataModeManager.saveAuthSession`: require `!uid.contains("@")` and `uid.isNotBlank()`. Any attempt to pass an email string as UID fails fast.<br>2. **One-Time Startup Migration:** In `AppViewModel.init`, if `persisted.uid.contains("@")`, call `data.clearAuthSession()`, clear memory state, and set observable `_legacySessionRequiresReauth.value = true`. ProfileScreen renders an actionable banner: *"Security update: Please sign in again to connect your cloud account"*. Forces clean Firebase UID establishment.<br>3. **Dual-Read Migration Fallback:** In `downloadUserData` and `recoverAllCloudRoutines`: If the Firebase UID document (`users/{uid}`) is empty, check legacy paths (`users/{email}` and `users/{email_escaped}`). If found, import records into Room and immediately upload to `users/{uid}`, ensuring historical workouts are never orphaned. |
| **Objection 2: `coroutineScope { awaitAll }` causes all-or-nothing cancellation and races with routine dedup.** | 🔴 **Blocker** | **FULL CONCESSION** | **Replace with `supervisorScope` + Pre-flight Dedup.**<br>1. Run `repo.cleanupDuplicateRoutines()` as an explicit synchronous pre-flight step **before** starting uploads.<br>2. Use `supervisorScope` with independent `async` blocks. Wrap each document upload in individual `runCatching` blocks so a failure in `sessions` does not cancel `exercises` or `cycles`.<br>3. Aggregate per-document results into `CloudSyncResult(uploadErrors: Map<String, String>)` for granular reporting. |
| **Objection 3: `_syncState` is sticky on ERROR with no IDLE reset; rapid retries loop with no cooldown.** | 🟠 **Major** | **FULL CONCESSION** | **Implement explicit reset & UI debouncing.**<br>1. Add `UserCloudSyncManager.resetSyncStatus()` which sets `_syncState.value = SyncStatus.IDLE`.<br>2. In `AppViewModel.triggerCloudSync`, invoke `resetSyncStatus()` at the outset.<br>3. In `ProfileScreen.kt`, add a 5-second cooldown debounce state (`isCooldownActive`) following any sync failure to prevent UI hammering and Firebase rate-limiting. |
| **Objection 4: Cold-start auth wait is unspecified; Scenario 2 not testable with synchronous test seam.** | 🟠 **Major** | **FULL CONCESSION** | **Specify `CompletableDeferred` wait and observable BDD.**<br>1. In `UserCloudSyncManager`, implement `suspend fun awaitAuthState(timeoutMs: Long = 3000L): FirebaseUser?` using `auth.addAuthStateListener` + `CompletableDeferred` + `withTimeoutOrNull`.<br>2. Provide asynchronous test seam `internal var asyncAuthUidProviderForTesting: (suspend () -> String?)?`.<br>3. Rewrite BDD Scenario 2 to assert on observable StateFlow transitions (`authUser` transitions from null to valid user, `syncState` from IDLE to SYNCING to SUCCESS). |
| **Objection 5: Structural incompleteness and competing Final Decision Plan sections.** | 🟠 **Major** | **FULL CONCESSION** | **Unify into single authoritative Final Decision Plan.**<br>All previous intermediate draft plans are explicitly superseded. The authoritative, complete Final Decision Plan below contains the full, unabridged Component Impact table, BDD criteria, and INVEST breakdown. |

---

## 🎯 Final Decision Plan & User Story Specification (Consolidated & Authoritative)

### User Story
```gherkin
As an athlete or coach using CrossTraining
I want the application to automatically validate my login credentials against Firebase Auth, safely migrate legacy cloud data, parallelize cloud backups with fault isolation, and provide actionable recovery prompts
So that tapping "Sync Now" reliably synchronizes my personal data without false rejections, orphaned records, or unexplained error badges.
```

---

### BDD Acceptance Criteria

#### Scenario 1: Clean Startup Migration for Legacy Email UIDs
```gherkin
Given a user installation from an earlier version with persisted user.uid containing "@" (email string)
When the application completes cold launch
Then AppViewModel detects the legacy UID format, clears the malformed session from SharedPreferences
And the Profile screen displays an inline banner: "Security update: Please sign in again to connect your cloud account"
And verifyTokenBinding does not fail closed with unhandled exceptions
```

#### Scenario 2: Observable Cold-Start Auth State Await
```gherkin
Given an authenticated user whose Firebase Auth token restoration is actively pending on cold start
When the user triggers "Sync Now" immediately upon Profile screen entry
Then awaitAuthState observes the AuthStateListener until a valid non-anonymous FirebaseUser resolves
And authUser StateFlow emits the authenticated user
And syncState transitions from IDLE -> SYNCING -> SUCCESS with snackbar "Cloud sync completed!"
```

#### Scenario 3: Fault-Isolated Concurrent Cloud Upload (`supervisorScope`)
```gherkin
Given an authenticated user with local exercises, routines, cycles, and sessions
When uploadUserData executes
Then repo.cleanupDuplicateRoutines runs as a synchronous pre-flight step
And each document collection is uploaded concurrently inside a supervisorScope
And if one document upload encounters a transient network timeout, sibling document uploads are NOT cancelled
And the overall sync outcome reflects the aggregated document status
```

#### Scenario 4: Dual-Read Legacy Cloud Data Migration
```gherkin
Given an existing user who previously backed up workouts under legacy path users/{email}
When they sign in with a new Firebase UID and trigger cloud sync
Then downloadUserData checks users/{newUid}
And upon finding it empty, queries users/{email}
And upon detecting legacy routines/sessions, imports them into Room and syncs them to users/{newUid}
And no historical athlete data is orphaned or lost
```

#### Scenario 5: Actionable Inline Error Card & Debounced Retry
```gherkin
Given an athlete whose Firebase session has expired or is invalid
When they tap "Sync Now"
Then the Cloud Backup & Sync card transitions to ERROR badge
And an inline error container displays: "Session expired. Please sign in again to back up your workouts"
And a prominent [Sign In Again] button opens the Credential Manager auth sheet directly
And the "Sync Now" button enters a 5-second cooldown state to prevent hammering
```

---

### Component Impact Table

| Component File | Exact File Path | Concrete Changes Required |
| :--- | :--- | :--- |
| **`DataModeManager.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/DataModeManager.kt` | - In `saveAuthSession`: Add guard `if (uid.contains("@") \|\| uid.isBlank()) { return }` to gracefully reject email strings from being stored as UIDs without crashing in production.<br>- Make `_demoMode` strictly in-memory (`MutableStateFlow(false)`).<br>- Expose `val realRepository: Repository = testRepository ?: realRepository`. |
| **`UserCloudSyncManager.kt`** | `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt` | - Add `suspend fun awaitAuthState(timeoutMs: Long = 3000L): FirebaseUser?` via `AuthStateListener` + `CompletableDeferred`.<br>- In `verifyTokenBinding`: Call `awaitAuthState` if `auth.currentUser` is null; fail closed only if unauthenticated or mismatched.<br>- In `uploadUserData`: Move `cleanupDuplicateRoutines()` to pre-flight; wrap uploads in `supervisorScope` with independent `async` and per-document error tracking.<br>- In `downloadUserData` / `recoverAllCloudRoutines`: Add dual-read fallback to legacy `userDoc(user.email)` if `userDoc(uid)` is empty.<br>- Add `fun resetSyncStatus()` to set `_syncState.value = SyncStatus.IDLE`.<br>- Add `internal var asyncAuthUidProviderForTesting: (suspend () -> String?)?`. |
| **`AppViewModel.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` | - In `init`: Check if `persisted.uid.contains("@")`; if true, clear session and expose `legacySessionRequiresReauth: StateFlow<Boolean>`.<br>- Reset `legacySessionRequiresReauth = false` upon successful login or `SyncStatus.SUCCESS`.<br>- In `triggerCloudSync`: Call `UserCloudSyncManager.resetSyncStatus()` before launching sync.<br>- Expose `lastSyncError: StateFlow<String?>` for persistent UI error rendering.<br>- Route all sync calls to `data.realRepository`. |
| **`ProfileScreen.kt`** | `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProfileScreen.kt` | - Add inline error container in `Cloud Backup & Sync` card showing `lastSyncError` and `[Sign In Again]` button.<br>- Add legacy re-auth security banner if `legacySessionRequiresReauth` is true.<br>- Add 5-second cooldown timer state disabling `Sync Now` after failure.<br>- Wire Google sign-in exclusively through Credential Manager `signInWithGoogleCredential`. |
| **`TokenBoundIdentitySyncTest.kt`** | `app/src/test/java/com/fractanomics/crosstraining/data/firebase/TokenBoundIdentitySyncTest.kt` | - Add unit tests for `awaitAuthState` resolution and timeout.<br>- Add unit tests for `supervisorScope` partial-failure isolation.<br>- Add unit tests for dual-read legacy routine migration.<br>- Add unit tests asserting `DataModeManager.saveAuthSession` rejects `@` in UIDs gracefully. |

---

### Phased INVEST Subtask Breakdown

1. **Subtask 1: Upstream UID Sanitization & Legacy Re-Auth Startup Migration**
   - Add strict non-email UID validation to `DataModeManager.saveAuthSession`.
   - Add startup check in `AppViewModel.init` clearing legacy email UIDs and emitting `legacySessionRequiresReauth`.
   - Unit tests covering legacy session clearing and UID rejection.

2. **Subtask 2: Cold-Start Auth Await & Dual-Read Cloud Migration**
   - Implement `awaitAuthState` with `CompletableDeferred` in `UserCloudSyncManager.kt`.
   - Add dual-read fallback in `downloadUserData` and `recoverAllCloudRoutines` migrating legacy email documents to the new UID.
   - Unit tests verifying async auth wait and zero data loss on migration.

3. **Subtask 3: Fault-Isolated Concurrent Upload (`supervisorScope`) & Error Reporting**
   - Refactor `uploadUserData` to run pre-flight routine dedup followed by concurrent `supervisorScope` document uploads.
   - Aggregate per-document results and expose `lastSyncError` in `AppViewModel`.
   - Unit tests verifying that failure in one document does not cancel sibling uploads.

4. **Subtask 4: Actionable Card-Level Recovery UI & Debounced Retry**
   - Update `ProfileScreen.kt` with inline error banner, `[Sign In Again]` button, and 5-second cooldown debounce.
   - Add Compose UI tests for the error and re-auth states.
   - Execute full Pre-PR Verification Suite and E2E visual artifact capture.

---

## 🏛️ Claude Review Iteration 2

- **Date / Reviewer:** 2026-09-03 | Principal Architect (Claude Sonnet — Thinking)
- **Target:** Review Iteration 7 (Gemini Response & Consensus Alignment) + consolidated Final Decision Plan
- **Scope:** Full re-read of the implementation plan from Review Iteration 6 onward; independent evaluation of all five concessions and their concrete design outcomes in the updated Component Impact Table, BDD criteria, and INVEST breakdown. No claim inherited from Round 1.

---

### ⚖️ Evaluation of Concessions & Refined Architecture

#### Blocker 1 (Round 1 Critique 1 + Concern A): Self-Heal Replaced with Upstream Sanitization + Dual-Read Migration

**Round 1 position:** The email-match self-heal in `verifyTokenBinding` creates orphaned Firestore documents for all pre-`2cdca7f` users and introduces a new silent data-loss path. The correct fix is upstream UID sanitization at `saveAuthSession` write time and a one-time forced re-authentication startup migration — not a downstream email-equality bypass.

**Gemini's concession (Iteration 7, Objection 1):**
- Downstream self-heal **eliminated**.
- `DataModeManager.saveAuthSession`: adds `require(!uid.contains("@") && uid.isNotBlank())` — upstream sanitization accepted verbatim.
- `AppViewModel.init`: detects `persisted.uid.contains("@")`, calls `data.clearAuthSession()`, emits `legacySessionRequiresReauth: StateFlow<Boolean>`, renders a re-auth banner in `ProfileScreen.kt`.
- **Dual-read migration fallback** added: `downloadUserData` and `recoverAllCloudRoutines` check `userDoc(uid)` first, and if empty fall back to `userDoc(user.email)` (legacy path), import records to Room, and upload to `userDoc(uid)`. This directly addresses Concern C (orphaned routines path) and Concern A (orphaned document data loss) from Round 1.

**Evaluation — ACCEPTED with one residual note:**

The core architectural correction is correct and complete. The upstream sanitization prevents future email-as-UID pollution; the startup migration forces a clean re-authentication rather than silently mutating identity; the dual-read fallback migrates orphaned documents rather than losing them.

One implementation detail in the Component Impact Table warrants a note for the developer, but does not rise to a blocker: the `require(!uid.contains("@"))` assertion in `saveAuthSession` will throw `IllegalArgumentException` at any call site that passes a malformed uid. The existing `logInWithEmail` fabricated-uid fallback path (`uid = "jangelpv_crosstraining_app"` derived from email — not an `@` string) will *not* be caught by this guard because the fabricated string contains no `@`. Subtask 1's unit tests must explicitly cover the fabricated-uid case (e.g., `uid = "jangelpv_crosstraining_app"`) and verify it is rejected or that the fabrication code path is eliminated. This is a **minor observation** — the main architectural fix is sound.

---

#### Blocker 2 (Round 1 Critique 2 + Concern E): `supervisorScope` + Pre-flight Dedup + Per-Document Error Aggregation

**Round 1 position:** `coroutineScope { awaitAll(...) }` converts five sequential partial-failure risks into all-or-nothing sibling cancellation. `cleanupDuplicateRoutines()` must be a pre-flight step. Partial upload success is architecturally ambiguous with a binary `_syncState`.

**Gemini's concession (Iteration 7, Objection 2):**
- `coroutineScope` replaced with `supervisorScope` with independent `async` blocks — accepted verbatim.
- `cleanupDuplicateRoutines()` moved to a synchronous pre-flight step before any `async` block is launched — accepted verbatim.
- Per-document results aggregated into `CloudSyncResult(uploadErrors: Map<String, String>)`.
- Scenario 3 (BDD) now explicitly asserts: *"if one document upload encounters a transient network timeout, sibling document uploads are NOT cancelled"* — the previously ambiguous Scenario 4 gap (Concern E) is addressed.
- `lastSyncError: StateFlow<String?>` exposed from `AppViewModel` for persistent error rendering.

**Evaluation — ACCEPTED.** All three sub-concerns from Round 1 are structurally resolved. The `supervisorScope` choice eliminates sibling cancellation; pre-flight dedup removes the SQLite-interleaving hazard; the `CloudSyncResult` map enables granular error reporting that the binary `_syncState` alone could not express. Scenario 3's explicit "siblings NOT cancelled" assertion makes this falsifiable in unit tests.

---

#### Major 1 (Round 1 Critique 3): Sticky ERROR State with No IDLE Reset and No Retry Cooldown

**Round 1 position:** `_syncState` has no path back to `IDLE`; ERROR→SYNCING→ERROR loops with no back-off; singleton `_syncState` is polluted by background cycle-edit uploads.

**Gemini's concession (Iteration 7, Objection 3):**
- `UserCloudSyncManager.resetSyncStatus()` introduced, setting `_syncState.value = SyncStatus.IDLE`.
- `AppViewModel.triggerCloudSync` calls `resetSyncStatus()` at the outset of each user-initiated sync.
- `ProfileScreen.kt` adds a 5-second cooldown debounce state (`isCooldownActive`) after any sync failure.
- Scenario 5 (BDD) shows the cooldown in the acceptance criteria.

**Evaluation — ACCEPTED.** The `resetSyncStatus()` call at the top of `triggerCloudSync` ensures IDLE is re-established before each attempt, breaking the ERROR→SYNCING→ERROR loop. The 5-second UI cooldown prevents hammering. The background-upload badge pollution concern is ameliorated by the `supervisorScope` isolation (Blocker 2 fix) which makes cycle-edit uploads independent of the user-sync state path. One note: the `resetSyncStatus()` method resets the **singleton** `_syncState` on the `object`. If a background cycle-edit upload is in-flight when `triggerCloudSync` resets to IDLE, the reset will clobber the background `SYNCING` state — but this is a pre-existing architectural smell of the singleton (raised as Iteration 3 F7 / Iteration 5 F7) that falls outside the scope of this round's blockers. The `resetSyncStatus()` + cooldown debounce satisfactorily resolve the specific sticky-ERROR concern raised in Round 1.

---

#### Major 2 (Round 1 Critique 4 + Privacy Concern): `remember = true` Override + `saveAuthSession` Dependency Injection Gap

**Round 1 position:** The self-heal unconditionally persisted with `remember = true`, silently overriding the user's "Remember Me" preference. Additionally, `UserCloudSyncManager` (a standalone `object`) had no access to `DataModeManager.saveAuthSession`, making the injection pathway unspecified and untestable.

**Gemini's concession (Iteration 7):**
- The downstream self-heal in `verifyTokenBinding` is **completely eliminated** (Objection 1 concession). Because the self-heal is gone, the `remember = true` override problem is moot — there is no longer a code path in `verifyTokenBinding` that calls `saveAuthSession` at all. The `UserCloudSyncManager` → `DataModeManager` injection gap is also moot for the same reason.
- The `saveAuthSession` call now only happens at legitimate login time (where the `remember` parameter is already correctly threaded through `logInWithEmail`, `logInWithGoogleAccount`, etc.) and at the startup migration (where `clearAuthSession()` is called, not `saveAuthSession`).
- The Component Impact Table specifies `ProfileScreen.kt` wires Google sign-in exclusively through Credential Manager `signInWithGoogleCredential` — addressing the broken `AccountManager` path that was the root cause of the privacy concern.

**Evaluation — ACCEPTED.** By eliminating the downstream self-heal rather than patching it, the entire class of `saveAuthSession`-injection and `remember`-override problems is structurally dissolved. This is the architecturally correct response.

---

#### Major 3 (Round 1 Critique 5 + Concern D): Cold-Start Auth Await Unspecified + Untestable BDD + Document Incompleteness

**Round 1 position:** The "await up to 3 seconds" mechanism was unspecified (polling vs. listener vs. `CompletableDeferred`). The synchronous `authUidProviderForTesting: () -> String?` seam could not simulate async token restoration. Scenario 2 was untestable. The Final Decision Plan had a truncated Component Impact table and two competing superseding sections.

**Gemini's concession (Iteration 7, Objections 4 and 5):**
- `awaitAuthState(timeoutMs: Long = 3000L): FirebaseUser?` using `auth.addAuthStateListener` + `CompletableDeferred` + `withTimeoutOrNull` — specified verbatim as required in Round 1's mandatory safeguards table.
- Asynchronous test seam: `internal var asyncAuthUidProviderForTesting: (suspend () -> String?)?` — the seam signature is upgraded from synchronous `() -> String?` to `suspend () -> String?`, enabling timing control in unit tests.
- Scenario 2 rewritten to assert observable StateFlow transitions (`authUser` from null → valid user; `syncState` from IDLE → SYNCING → SUCCESS) rather than implementation internals.
- The two competing Final Decision Plan sections are superseded by a single authoritative consolidated plan at the end of the document (post-Iteration 7). The Component Impact Table is **complete** — five files, full concrete change specifications per file, no truncation.

**Evaluation — ACCEPTED.** All four sub-concerns are resolved:
- Mechanism: `CompletableDeferred` + `AuthStateListener` + `withTimeoutOrNull` is the correct cancellation-safe implementation.
- Seam: `suspend () -> String?` enables `delay()` or `CompletableDeferred` control in tests.
- BDD: Scenario 2 is now observable-driven.
- Document: A single authoritative Final Decision Plan with a complete Component Impact Table exists and explicitly supersedes prior sections.

One implementation note: the `awaitAuthState` function handles the null-resolved case (no prior authentication) correctly only if the `verifyTokenBinding` caller interprets a `null` return as "no authenticated user → fail closed with re-auth prompt." The Component Impact Table says "fail closed only if unauthenticated or mismatched" — which is the correct interpretation, but the developer must ensure the three outcomes (null → re-auth prompt; anonymous → re-auth prompt; real uid match → proceed) are all covered explicitly in `TokenBoundIdentitySyncTest.kt`. This is a **minor verification note**, not a blocker.

---

### 🚨 Remaining or Newly Introduced Concerns (if any)

#### Concern I: Budget Arithmetic for the Combined Pre-Flight Sequence (Observation, Not a New Blocker)

Round 1 Critique 1 raised the combined worst-case latency: 3 s (`awaitAuthState`) + 5 s (`ensureAuthenticated` anonymous sign-in timeout) + 5 sequential Firestore reads (overwrite guard) + 5 concurrent writes — approaching or exceeding the 20 s `withTimeout`. In the updated plan, `awaitAuthState` is called inside `verifyTokenBinding` **before** `ensureAuthenticated`. If `verifyTokenBinding` succeeds (real UID match), `ensureAuthenticated` returns early (line 98 fast-path). The 3 s await + fast-path `ensureAuthenticated` + 5 concurrent writes is well within 20 s for normal connections.

However, if `awaitAuthState` times out (returns null after 3 s) and `verifyTokenBinding` fails closed with a re-auth prompt, the `withTimeout(20000L)` coroutine is still running and must cancel cleanly. The updated Component Impact Table does not specify whether `verifyTokenBinding`'s failure throws a `CancellationException`-safe result or a naked `IllegalStateException`. The `runCatching` wrapper in `uploadUserData` will catch it either way, but `CancellationException` must not be swallowed by `runCatching` (Iteration 2 F15 / Iteration 3 §4). **Recommendation:** `TokenBoundIdentitySyncTest.kt` must include a test that verifies a `verifyTokenBinding` failure from `awaitAuthState` timeout does not swallow `CancellationException`. This is a **minor test-coverage note** that does not block implementation.

#### Concern II: Dual-Read Fallback Inside `withTimeout(20000L)` — Budget Impact (Observation)

Subtask 2's dual-read fallback adds two additional Firestore reads (check `userDoc(uid)`, then check `userDoc(email)`) to `downloadUserData` and `recoverAllCloudRoutines`. These reads now also share the 20 s `withTimeout` budget alongside the `awaitAuthState` (up to 3 s) and the concurrent document reads in the overwrite guard (already modelled in the main upload path). For `downloadUserData`, this is the first invocation in a new session, so there is no overwrite guard overhead there — acceptable. For `recoverAllCloudRoutines`, the legacy fallback is only triggered when `userDoc(uid)` is empty, which is the one-time migration scenario. After migration, subsequent calls hit only `userDoc(uid)`, incurring no overhead. **No blocker.** The one-time nature of the dual-read is architecturally sound.

#### Concern III: `DataModeManager.saveAuthSession` `require()` Throws in Production — Error Handling Gap (Minor)

The Component Impact Table specifies adding `require(!uid.contains("@") && uid.isNotBlank())`. Kotlin's `require()` throws `IllegalArgumentException` if the condition is false. In production, if any code path (today or in future feature branches) passes an email-as-uid to `saveAuthSession`, the app will throw an uncaught `IllegalArgumentException` at the call site. If this call site is not wrapped in a `try/catch`, it surfaces as a crash rather than a graceful degradation. **Recommendation:** Use a logging-and-return-early guard (`if (uid.contains("@")) { Log.e(...); return }`) rather than `require()` in production builds, or wrap the `require()` in a `try { ... } catch (e: IllegalArgumentException) { clearAuthSession(); return }`. A crash at login is worse than a failed sync. This is a **minor** concern — not a blocker, but the test for this in `TokenBoundIdentitySyncTest.kt` must assert the graceful degradation path, not just that the invalid uid is rejected.

#### Concern IV: `legacySessionRequiresReauth` Banner Dismissal Lifecycle (Minor)

The `AppViewModel.init` startup migration emits `legacySessionRequiresReauth: StateFlow<Boolean>`. Once the user taps `[Sign In Again]` and completes authentication, the `StateFlow` must be reset to `false` to dismiss the banner. The Component Impact Table does not specify what event clears `legacySessionRequiresReauth` after successful re-authentication. If `triggerCloudSync` sets `legacySessionRequiresReauth = false` on SUCCESS, the banner disappears after sync — correct. If it is never cleared, the banner persists forever on the screen even after the user has successfully re-authenticated. **Recommendation:** Add one line to the Component Impact `AppViewModel.kt` row: *"Clear `legacySessionRequiresReauth` on successful login or on `SyncStatus.SUCCESS`."* This is a **minor specification gap** — not a blocker, but must be specified before implementation.

---

### 🏁 Verdict

All five architectural blockers and majors from Round 1 are **substantively and structurally resolved** in the consolidated Final Decision Plan:

| Round 1 Finding | Resolution | Status |
| :--- | :--- | :--- |
| **Blocker 1:** Email self-heal creates orphaned documents | Upstream `saveAuthSession` sanitization + startup re-auth migration + dual-read Firestore fallback | ✅ **Resolved** |
| **Blocker 2:** `coroutineScope` all-or-nothing cancellation + dedup interleaving | `supervisorScope` + pre-flight dedup + per-document aggregation | ✅ **Resolved** |
| **Major 1:** Sticky ERROR state, no IDLE reset, no cooldown | `resetSyncStatus()` + `triggerCloudSync` reset + 5 s UI debounce | ✅ **Resolved** |
| **Major 2:** `remember = true` override + `saveAuthSession` injection gap | Self-heal eliminated entirely; concern is structurally dissolved | ✅ **Resolved** |
| **Major 3:** Unspecified await mechanism + untestable BDD + truncated document | `CompletableDeferred` + `AuthStateListener` + `suspend` seam + observable BDD + single authoritative plan | ✅ **Resolved** |

Remaining concerns (I–IV) are implementation-level observations — test coverage notes, a production error-handling style preference, and a minor lifecycle specification gap. None of them introduce a new architectural hazard, data-loss risk, or security regression that the plan has not either addressed or structurally pre-empted. They are suitable for resolution during code review on the implementing PRs without requiring another plan iteration.

`VERDICT: AGREED`
