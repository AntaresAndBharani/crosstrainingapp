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

### Component Impact Table

| Component File | Location | Concrete Changes |
| :--- | :--- | :--- |
| **`DataModeManager.kt`** | `app/.../data/DataModeManager.kt` | - Remove `KEY_DEMO_MODE` persistence from `SharedPreferences`.<br>- Make `_demoMode` strictly in-memory: `MutableStateFlow(false)`.<br>- Expose public `val realRepository: Repository` as `testRepository ?: realRepository` for JVM testability and cloud sync isolation. |
| **`AppNavigation.kt`** | `app/.../ui/navigation/AppNavigation.kt` | - Remove `viewModel.setDemoMode(true)` from `onContinueAsGuest`.<br>- Add `DataModeDrawerRow` with an interactive switch ("Real Data" vs "Demo Data") in `AppDrawerContent`. |
| **`ProfileScreen.kt`** | `app/.../ui/screens/ProfileScreen.kt` | - Add stateless `DataModeCard(demoMode: Boolean, onToggle: (Boolean) -> Unit)` adhering to `.agents/rules/03_compose_ui_standards.md` §1.<br>- Update Cloud Backup & Sync card with guest login prompt, clean status badge, and user-friendly error mapping. |
| **`AppViewModel.kt`** | `app/.../ui/AppViewModel.kt` | - **Phase 1:** Route the 3 buttonless background cycle sync sites (`saveCycle:223`, `saveCycleWithGoals:229`, `deleteCycleGoal:234`) to `data.realRepository`.<br>- **Phase 2:** Route remaining 7 call sites (`triggerCloudSync:155,156`, `signUp:88`, `logInEmail:102`, `logInGoogle:116`, `logInGoogleAccount:130`, `recoverCloudRoutines:168`) to `data.realRepository`.<br>- Separate upload and download error reporting. |
| **`SeedData.kt` / `AppDatabase.kt`** | `app/.../data/` | - Add startup / migration check: if `cycleDao.getAllOnce().isEmpty()`, insert a default initial cycle so both fresh and upgraded production installs can immediately save sessions. |
| **`UserCloudSyncManager.kt`** | `app/.../data/firebase/UserCloudSyncManager.kt` | - Check non-anonymous identity before `ensureAuthenticated()`, failing closed if `auth.currentUser` is null/anonymous.<br>- Add injectable seam `internal var authUidProviderForTesting: (() -> String?)?`.<br>- Add per-document overwrite guard (skip `.set()` if local is empty and remote is populated).<br>- Wire Credential Manager to `signInWithGoogleCredential`.<br>- Scope `recoverAllCloudRoutines` to `userDoc(currentUserId)`. |
| **`LibraryScreen.kt`** | `app/.../ui/screens/LibraryScreen.kt` | - Remove redundant "Try demo data / Switch to my data" dropdown item, rehoming demo switching into `DataModeCard` and Drawer while preserving "Reset demo data". |
| **`e2e/flows/03_history_and_search_flow.yaml`** | `e2e/flows/` | - Make flow 03 self-contained by creating a session prior to asserting on workout history. |

---

### Phased INVEST Subtask Execution Plan

#### Phase 1: Real Data Default & Session-Scoped Demo Switch (Immediate Delivery)
1. **In-Memory Demo Mode:** Make `demoMode` strictly in-memory in `DataModeManager.kt` (defaults to `false` on launch; remove `KEY_DEMO_MODE` reading/writing). Expose `realRepository = testRepository ?: realRepository`.
2. **Remove Forced Guest Demo Mode:** Remove `viewModel.setDemoMode(true)` from `AppNavigation.kt:163`.
3. **Background Cycle Sync Protection:** Update `saveCycle`, `saveCycleWithGoals`, and `deleteCycleGoal` in `AppViewModel.kt` to pass `data.realRepository`.
4. **Stateless UI Components:** Implement stateless `DataModeCard` in `ProfileScreen.kt` and `DataModeDrawerRow` in `AppNavigation.kt`. Remove redundant toggle from `LibraryScreen.kt`. Add Compose test in `androidTest`.
5. **Production Cycle Provisioning:** Implement startup check in `AppDatabase.kt` / `Repository.kt` inserting a default cycle if `cycleDao.getAllOnce().isEmpty()`.
6. **E2E Test Flow 03 Isolation:** Update `03_history_and_search_flow.yaml` to create a session first.
7. **Verification & CI Gate:**
   - Run unit tests: `.\gradlew.bat testDebugUnitTest --no-daemon`.
   - Run CI-parity snapshot build: `.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon`.
   - Run script regression tests: `.\scripts\tests\Invoke-ScriptTests.ps1`.
   - Capture E2E visual artifacts: `.\scripts\run-e2e-tests.ps1 -CaptureArtifacts -Version "latest" -PushArtifacts`.

#### Phase 2: Cloud Sync Hardening, Identity Alignment & Overwrite Protection
1. **Remaining Sync Site Routing:** Route all remaining sync call sites in `AppViewModel.kt` strictly to `data.realRepository`.
2. **Token-Bound Identity & Auth Seam:** Implement pre-auth session check in `UserCloudSyncManager.kt` and injectable `authUidProviderForTesting`.
3. **Per-Document Overwrite Guard:** Add per-collection check in `uploadUserData` preventing locally empty collections from wiping remote Firestore documents.
4. **Credential Manager Integration:** Wire `signInWithGoogleCredential` to active Google auth launchers.
5. **Scope Recovery Query:** Scope `recoverAllCloudRoutines` to `userDoc(currentUserId)`.
6. **Test Contract Update:** Update `CrossAuthSignInTest` replacement assertions with operator approval.
7. **Production Verification:** Confirm "Sync Now" resolution on release APK `v3.0.147+`.

