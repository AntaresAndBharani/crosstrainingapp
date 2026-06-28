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
