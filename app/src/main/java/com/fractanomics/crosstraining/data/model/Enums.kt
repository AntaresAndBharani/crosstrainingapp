package com.fractanomics.crosstraining.data.model

/**
 * Broad classification of a tracked movement. Drives default UI grouping.
 */
enum class ExerciseCategory(val label: String) {
    BARBELL("Barbell"),
    GYMNASTICS("Gymnastics"),
    MACHINE("Machine"),
    ACCESSORY("Accessory"),
    OTHER("Other")
}

/**
 * What we measure for an exercise.
 *
 * WEIGHT     -> loaded movements (snatch, clean & jerk, squats...). Tracks rep-maxes.
 * CALORIES   -> monostructural machines scored by calories (Air Bike, Rower...).
 * DISTANCE   -> machines scored by meters (SkiErg, Row, Run...).
 * TIME       -> efforts scored by elapsed time (e.g. a benchmark hold/sprint).
 * REPS       -> bodyweight / gymnastics scored by reps.
 */
enum class MetricType(val label: String, val defaultUnit: String, val tracksRepMax: Boolean) {
    WEIGHT("Weight", "kg", true),
    CALORIES("Calories", "cal", false),
    DISTANCE("Distance", "m", false),
    TIME("Time", "sec", false),
    REPS("Reps", "reps", false)
}

/**
 * The role a block plays within a session. WARMUP and METCON change how the UI
 * presents the block; STRENGTH/ACCESSORY are loaded work that can target a main
 * lift for progression.
 */
enum class BlockKind(val label: String, val shortLabel: String = label) {
    WEIGHTLIFTING("Weightlifting"),
    COMPLEX("Complex"),
    SUPERSET("Superset"),
    HYPERTROPHY("Hypertrophy"),
    ACCESSORY("Accessory"),
    METABOLIC("Metabolic"),
    CARDIO("Cardio"),
    WARMUP("Warm-up"),
    STRENGTH("Strength"),
    METCON("WOD / Metcon"),
    OTHER("Other")
}

/**
 * Training cycle macro-orientation.
 * STRENGTH_WEIGHTLIFTING -> Standard periodized strength & weightlifting training.
 * FAT_LOSS_BODYBUILDING  -> Bodybuilding hypertrophy with scheduled fast/rest days and cardio tracking.
 */
enum class CycleType {
    STRENGTH_WEIGHTLIFTING,
    FAT_LOSS_BODYBUILDING
}

/**
 * Active body-composition velocity regime (Subcycle Type) applicable to any training cycle.
 *
 * - [WEIGHT_LOSS]: Target weekly momentum corridor `-1.50%` to `-0.50%/wk`.
 * - [RECOMPOSITION]: Target weekly momentum corridor `-0.50%` to `+0.25%/wk`.
 * - [WEIGHT_GAIN]: Target weekly momentum corridor `+0.25%` to `+1.50%/wk`.
 */
enum class WeightSubcycleType(
    val label: String,
    val minWeeklyPct: Double,
    val maxWeeklyPct: Double,
    val description: String
) {
    WEIGHT_LOSS(
        label = "Weight Loss",
        minWeeklyPct = -1.50,
        maxWeeklyPct = -0.50,
        description = "Controlled caloric deficit for sustainable fat loss while preserving lean tissue."
    ),
    RECOMPOSITION(
        label = "Recomposition",
        minWeeklyPct = -0.50,
        maxWeeklyPct = 0.25,
        description = "Near-maintenance velocity for simultaneous fat loss and lean muscle retention/gain."
    ),
    WEIGHT_GAIN(
        label = "Weight Gain",
        minWeeklyPct = 0.25,
        maxWeeklyPct = 1.50,
        description = "Controlled lean mass accrual with bounded weekly surplus."
    )
}

/**
 * Source tag for a daily weight point in the continuous daily timeline.
 *
 * - [REAL]: Direct scale measurement logged by the athlete in `weight_entries`.
 * - [THEORETICAL]: Linearly interpolated value derived between consecutive [REAL] boundaries.
 */
enum class WeightMeasureSource(val label: String) {
    REAL("Real"),
    THEORETICAL("Theoretical")
}
