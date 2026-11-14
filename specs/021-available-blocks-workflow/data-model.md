# Data Model: Available Blocks Workflow (`SPEC-021`)

## 1. Domain Models (`AvailableBlockCodec.kt`)

- **`TrackedPrSlot`**:
  - `movementId: Long` (nullable-safe lookup via `movementName` fallback when resolved lazily)
  - `movementName: String` (canonical trimmed display name, URL-encoded inside `[ab_prs=...]`)
  - `rmReps: Int` (`1..20`, UI quick options `1, 2, 3, 5, 10`)
- **`AvailableBlockExercise`**:
  - `id: Long`
  - `name: String`
  - `tracksRepMax: Boolean`
- **`AvailableBlock`**:
  - `id: Long` (maps to `Routine.id`)
  - `title: String` (maps to `Routine.name`)
  - `instructions: String` (maps to clean `Routine.notes` + `RoutineBlock.notes` without `[ab_prs=...]` / `[rb_v2=...]`)
  - `exercises: List<AvailableBlockExercise>` (ordered movement list encoded into `RoutineBlock` rows with `subBlock = title`)
  - `trackedPrs: List<TrackedPrSlot>` (deduplicated `(movementName.lowercase(), rmReps)` pairs serialized into first `RoutineBlock.notes` `[ab_prs=...]` header)
- **`LastBlockSessionSummary`**:
  - `dateIso: String`
  - `exerciseWeightsById: Map<Long, List<Double?>>` (plus name fallback map for cross-device sync)
  - `summaryLine: String` (e.g. `"80 kg · 85 kg · 90 kg"` formatted in active display unit)
- **`EvaluatedPrChip`**:
  - `movementId: Long`
  - `movementName: String`
  - `rmReps: Int`
  - `candidateWeight: Double`
  - `storedBestWeight: Double`
  - `isNewPr: Boolean` (`candidateWeight > storedBestWeight && candidateWeight > 0.0`)

## 2. Wire / Storage Mapping

### Room & Firestore `routines` Collection
- `Routine.defaultFormat = "AVAILABLE_BLOCK"`
- `Routine.mainExerciseId = exercises.firstOrNull()?.id`
- `Routine.notes = instructions`
- `RoutineBlock` per exercise (`position = index`, `subBlock = title`, `exerciseIdsCsv = "$exId"`, `notes = "[ab_prs=...]\n$instructions"` on block `0` when `trackedPrs` non-empty, or `instructions` otherwise).

### Room & Firestore `sessions` Collection
- `SessionBlock.routineId = availableBlock.id` (safely resolved to local ID or `null` during cloud download)
- `SessionBlock.subBlock = availableBlock.title`
- `SessionBlock.name = availableBlock.title`
- `SessionBlock.exerciseIdsCsv = comma-separated exercise IDs`
- `WorkoutSet` per weight tile: `reps = 1`, `weight = parsedWeightOrNull`, `position = setIndex`.

### Room & Firestore `rep_maxes` Collection
- Multiple `RepMax` rows supported per `BlockInsert` via `newRepMaxes: List<RepMax>`.
- Firestore `rep_maxes` serializer enriches outbound entries with `sourceBlockTitle` and `sessionTitle` resolved via `blockId` / `sessionId` for readable attribution across MCP and multi-device downloads.
