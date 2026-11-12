# Contract: Remote MCP Routine Tools (`list_routines`, `upsert_routine`)

**Feature**: `019-routine-builder-redesign`  
**Server**: `mcp-server/` (`src/schemas.ts`, `src/store.ts`, `src/mcp.ts`)

## Connector Schema Compatibility Invariants (Constitution Principle VII)
1. **Zero `$ref`**: Every nested Zod schema (`makeRoutineBlockExerciseInputSchema()`, `makeRoutineBlockInputSchema()`) MUST be instantiated via factory functions so `zod-to-json-schema` never emits `"$ref"`.
2. **Zero `exclusiveMinimum`**: All numeric bounds use inclusive `.min(0)` or `.min(1)` (never `.gt()` or `.positive()`).
3. **Backward Compatibility**: Existing callers passing `exerciseNames: string[]` and `targetRepsScheme: string` without `exercises` continue to work unchanged.

---

## 1. `upsert_routine` Input Schema

```json
{
  "name": "Monday Mixed Conditioning",
  "description": "Upper push + cyclical engine pacing",
  "defaultFormat": "E3MOM",
  "blocks": [
    {
      "name": "Mixed Conditioning",
      "kind": "METCON",
      "format": "E3MOM",
      "setsCount": 4,
      "notes": "Keep transitions under 10s",
      "exercises": [
        {
          "name": "Bench Press",
          "metric": "REPS",
          "mode": "FIXED",
          "fixedValue": 8
        },
        {
          "name": "Air Bike",
          "metric": "CALORIES",
          "mode": "PER_SET",
          "perSetValues": [15, 15, 12, 0]
        },
        {
          "name": "Concept2 Row",
          "metric": "METERS",
          "mode": "PER_SET",
          "perSetValues": [250, 250, 200, 0]
        }
      ]
    }
  ]
}
```

### Normalization & Validation Rules on `upsert_routine`
1. **`BlockKindValues`**: Includes `"GYMNASTICS"` in addition to `"WEIGHTLIFTING"`, `"COMPLEX"`, `"SUPERSET"`, `"HYPERTROPHY"`, `"ACCESSORY"`, `"METABOLIC"`, `"CARDIO"`, `"WARMUP"`, `"STRENGTH"`, `"METCON"`, `"OTHER"`.
2. **When `block.exercises` is provided and non-empty**:
   - Resolves `setsCount`: uses explicit `block.setsCount`, or max `perSetValues.length` across exercises, or format-driven default (`1` / `8` / `4`).
   - Normalizes each exercise:
     - Auto-creates missing exercises in `"exercises"` collection with inferred `metricType` (`CALORIES` → `"CALORIES"`, `TIME_SECONDS` → `"TIME"`, `METERS` → `"DISTANCE"`, `REPS` → `"WEIGHT"` or `"REPS"`).
     - Defaults `metric` from the resolved `ExerciseRecord.metricType` if omitted.
     - Defaults `mode` to `"PER_SET"` if `perSetValues` is provided, else `"FIXED"`.
     - Normalizes `perSetValues` to length `setsCount` (copying last element when shorter, slicing when longer).
     - Rejects all-zero exercises (`mode === "FIXED" && fixedValue <= 0` or `mode === "PER_SET" && perSetValues.every(v => v <= 0)`) with a descriptive validation error.
   - Populates `exerciseIdsCsv` and `exerciseNamesCsv` preserving exact sequence order (including intentional repeated movements).
   - Encodes `[rb_v2=...]` header into `RoutineBlockRecord.notes` alongside clean `block.notes`, and generates human-readable `targetRepsScheme`.
3. **When `block.exercises` is omitted (Legacy Input)**:
   - Resolves `exerciseNames` and `targetRepsScheme` as before, preserving 100% backward compatibility.

---

## 2. `list_routines` & `upsert_routine` Output Block Shape

Every returned block includes both raw Firestore compatibility fields and decoded structured exercise targets:

```json
{
  "id": 1,
  "routineId": 1,
  "position": 0,
  "name": "Mixed Conditioning",
  "kind": "METCON",
  "format": "E3MOM",
  "setsCount": 4,
  "targetRepsScheme": "8 reps + 15-15-12-0 cal + 250-250-200-0m",
  "exerciseIdsCsv": "1,2,3",
  "exerciseNamesCsv": "Bench Press,Air Bike,Concept2 Row",
  "notes": "[rb_v2=REPS:FIXED:8:8.8.8.8;CALORIES:PER_SET:15:15.15.12.0;METERS:PER_SET:250:250.250.200.0]\nKeep transitions under 10s",
  "cleanNotes": "Keep transitions under 10s",
  "exercises": [
    {
      "exerciseId": 1,
      "name": "Bench Press",
      "metric": "REPS",
      "mode": "FIXED",
      "fixedValue": 8,
      "perSetValues": [8, 8, 8, 8],
      "summary": "8 reps / set"
    },
    {
      "exerciseId": 2,
      "name": "Air Bike",
      "metric": "CALORIES",
      "mode": "PER_SET",
      "fixedValue": 15,
      "perSetValues": [15, 15, 12, 0],
      "summary": "15 • 15 • 12 • 0 cal"
    },
    {
      "exerciseId": 3,
      "name": "Concept2 Row",
      "metric": "METERS",
      "mode": "PER_SET",
      "fixedValue": 250,
      "perSetValues": [250, 250, 200, 0],
      "summary": "250 • 250 • 200 • 0m"
    }
  ]
}
```
