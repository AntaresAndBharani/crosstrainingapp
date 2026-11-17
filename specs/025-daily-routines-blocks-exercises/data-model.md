# Data Model: Daily Routines, Unified Session Tracking & Progress (`SPEC-025`)

## 1. Domain Entities (Pure Kotlin / TypeScript Parity)

### `DailyRoutine` (`DailyRoutineCodec.kt` & `mcp-server/src/store.ts`)
- `id: Long` (or `number` in MCP) — Room `routines.id`
- `title: String` — non-blank display name (`Routine.name`)
- `notes: String` — optional human-readable routine description (`Routine.description`)
- `items: List<DailyRoutineItem>` — ordered active items (`RoutineBlock` rows sorted by `position`)
- `missingBlockCount: Int` — count of `DR_BLOCK` references whose underlying `AvailableBlock` no longer exists (omitted from `items`)
- `rawRoutine: RoutineWithBlocks?` — lossless reference for Undo restore / sync
- Persisted marker: `Routine.defaultFormat = "DAILY_ROUTINE"`

### `DailyRoutineItem` (Ordered Constituent Items)
1. **`DailyRoutineItem.BlockRef`** (`format = "DR_BLOCK"`):
   - `blockId: Long`
   - `blockTitle: String`
   - `resolvedBlock: AvailableBlock?`
   - Header in `RoutineBlock.notes`: `[dr_item=BLOCK:id=<blockId>:title=<urlEncodedTitle>]`
   - `RoutineBlock.name = blockTitle`, `RoutineBlock.subBlock = blockTitle`, `RoutineBlock.exerciseIdsCsv = resolvedBlock?.exercises?.joinToString(",") { it.id }`
2. **`DailyRoutineItem.StandaloneExercise`** (`format = "DR_EXERCISE"`):
   - `exerciseId: Long`
   - `exerciseName: String`
   - `note: String` (e.g., `"4x8 @ RPE 8"`)
   - `defaultTrackedPrs: List<TrackedPrSlot>`
   - `resolvedExercise: Exercise?`
   - Header in `RoutineBlock.notes`: `[dr_item=EXERCISE:id=<exerciseId>:name=<urlEncodedName>]` + optional `[ab_prs=...]` + clean `note`
   - `RoutineBlock.name = exerciseName`, `RoutineBlock.exerciseIdsCsv = exerciseId.toString()`

### Standalone Exercise Session Block (`SessionBlock`)
- `format = "STANDALONE_EXERCISE"`
- `kind = BlockKind.STRENGTH`
- `name = exerciseName`
- `mainExerciseId = exerciseId`
- `exerciseIdsCsv = exerciseId.toString()`
- `routineId = null` (so standalone logs do not hijack `AvailableBlock` routine matching; history is resolved by `mainExerciseId` / `exerciseIdsCsv` / name)
- `subBlock = ""`
- `description = routineNote` (clean note from Daily Routine or `+ Add Exercise`)
- `sets`: ordered `BlockSet` entries (`weight > 0.0`, `reps = 1`)

## 2. Analytics & View State Projections

### `LastExerciseSessionSummary`
- `dateIso: String`, `dateFormatted: String`
- `weights: List<Double?>`
- `topWeight: Double?`, `averageWeight: Double?`, `setWeightsSummary: String`

### `ConstituentProgressSummary` (`DailyRoutineCodec.kt`)
- `itemType: DailyRoutineItemType` (`BLOCK` | `EXERCISE`)
- `key: String` (unique chip/selection key, e.g., `block:<id>:<idx>` or `ex:<id>:<idx>`)
- `title: String`
- `subtitle: String` (block instructions or exercise routine note)
- `allTimeTop: Double?`
- `lastTop: Double?`, `lastTopDateFormatted: String?`
- `lastAverage: Double?`, `lastVolume: Double?`
- `topDelta: Double?`, `trendDirection: ConstituentTrendDirection` (`UP`, `FLAT`, `DOWN`, `NONE`)
- `lastSetsSummary: String`
- `bestPrBadges: List<String>` (e.g. `["🔥 2RM Snatch: 35 kg"]`)
- `unit: String`
- `performances: List<BlockPerformance>`
- `dayPerformances: List<DayPerformance>`

## 3. UI Draft & Sync State Additions

### `LoadedAvailableBlockState` (`SimplifiedBlockLogComponents.kt`)
- `itemKind: SessionItemKind = SessionItemKind.BLOCK` (`BLOCK` | `EXERCISE`)
- Standalone exercise cards use `itemKind = SessionItemKind.EXERCISE`, `routineNote` stored in `block.instructions`, `exerciseSections = [single exercise]`, and `toBlockDrafts(...)` emits `format = "STANDALONE_EXERCISE"` and `subBlock = ""` when `itemKind == SessionItemKind.EXERCISE`.

### `SessionDraft` (`SessionDraft.kt`)
- `syncRoutineTemplateId: Long? = null`
- `syncRoutineTemplateItems: List<DailyRoutineItem>? = null`

## 4. Validation & State Transitions
- `DailyRoutine` save blocked unless `title.trim().isNotEmpty() && items.isNotEmpty()`.
- Deleting a `DailyRoutine` from `Library -> Routines` removes the `DAILY_ROUTINE` row (4s `[Undo]` restores exact `rawRoutine`).
- Deleting an `AvailableBlock` leaves `DailyRoutine` intact; `fromRoutineWithBlocks` skips unresolved `DR_BLOCK`s and increments `missingBlockCount`.
- Loading a `DailyRoutine` in `Log Session` records `loadedDailyRoutine` and `initialRoutineSignature`; adding/removing cards updates `hasStructuralDiffFromRoutine`. Only when `alsoUpdateRoutineTemplate == true` on save does `AppViewModel` persist the updated item sequence via `repo.updateDailyRoutineItems`.
