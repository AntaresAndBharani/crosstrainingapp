# Data Model: Continuous Workout Timer & Live Stage Queue

No Room / database schema changes are introduced (offline-first in-memory & foreground-service state only, compliant with Constitution Principle V).

## 1. Domain Enums (`ui/timer/WorkoutTimer.kt`)

### `TimerMode`
| Enum Value | `label` | `defaultRounds` | `liveStepSeconds` | Base Work Source |
| :--- | :--- | :---: | :---: | :--- |
| `EMOM` | `"EMOM / ExMOM"` | `10` | `15` | `targetDurationSeconds ?: intervalSeconds` |
| `AMRAP` | `"AMRAP"` | `1` | `30` | `targetDurationSeconds ?: (targetMinutes * 60)` |
| `DEATH_BY` | `"Death By"` | `15` | `15` | `targetDurationSeconds ?: intervalSeconds` |
| `TIME_CAP` | `"Time Cap / For Time"` | `1` | `30` | `targetDurationSeconds ?: (targetMinutes * 60)` |
| `TABATA` | `"Tabata / Intervals"` | `8` | `5` | `workSeconds` (+ `restSeconds` rest phase) |
| `REST` | `"Rest Timer"` | `1` | `15` | `targetDurationSeconds ?: restSeconds` |

### `TimerPhase`
- `IDLE("Idle")`
- `PREP("Get Ready!")`
- `WORK("Work")`
- `REST("Rest")`
- `TRANSITION("Transition")` *(New — amber transition countdown between stages)*
- `FINISHED("Done!")`

## 2. Configuration & Queue Entities (`ui/timer/WorkoutTimer.kt`)

### `WorkoutTimerConfig`
- `mode: TimerMode = TimerMode.EMOM`
- `intervalSeconds: Int = 60`
- `workSeconds: Int = 20`
- `restSeconds: Int = 10`
- `totalRounds: Int = mode.defaultRounds`
- `targetMinutes: Int = 12`
- `targetDurationSeconds: Int? = null` *(Allows exact non-minute setup/presets, e.g. 90s AMRAP or 45s REST without losing `targetMinutes` compatibility)*
- `prepCountdownSeconds: Int = 10`
- `soundEnabled: Boolean = true`
- `vibrationEnabled: Boolean = true`
- `workoutLabel: String = ""`
- **Pure methods**:
  - `baseWorkDurationSeconds(): Int` (clamped `>= 5` for active work intervals)
  - `baseRestDurationSeconds(): Int` (`>= 0`, meaningful primarily for `TABATA`)
  - `singleRoundTotalSeconds(): Int` (`baseWorkDurationSeconds() + if (mode == TABATA) baseRestDurationSeconds() else 0`)
  - `stageTotalSeconds(): Int` (`singleRoundTotalSeconds() * totalRounds.coerceAtLeast(1)`)

### `QueuedTimerStage`
- `config: WorkoutTimerConfig`
- `transitionSeconds: Int = 60` *(Clamped `>= 0`; `0` triggers immediate jump without `TimerPhase.TRANSITION`)*

## 3. Runtime Snapshot Projection (`TimerSnapshot`)
- `phase: TimerPhase = TimerPhase.IDLE`
- `isRunning: Boolean = false`
- `currentRound: Int = 1`
- `totalRounds: Int = 10`
- `roundSecondsRemaining: Int = 0`
- `roundSecondsElapsed: Int = 0`
- `roundTotalSeconds: Int = 60`
- `totalSecondsElapsed: Int = 0`
- `totalSecondsRemaining: Int = 0`
- `targetRepsCurrentRound: Int = 1`
- `workoutLabel: String = ""`
- `mode: TimerMode = TimerMode.EMOM`
- `activeConfig: WorkoutTimerConfig = WorkoutTimerConfig()` *(Snapshot of currently executing stage config)*
- `activeRoundRestSeconds: Int = 0` *(Rest duration scheduled for `currentRound` in `TABATA`; consumed/set when entering each round)*
- `nextRoundOverrideSeconds: Int? = null` *(Single-round override for `currentRound + 1` work/round)*
- `nextRoundRestOverrideSeconds: Int? = null` *(Single-round override for `currentRound + 1` Tabata rest)*
- `queuedNextStage: QueuedTimerStage? = null` *(Current + 1 Next Slot queue)*
- `completedStagesCount: Int = 0` *(Number of stages completed in current continuous chain)*
