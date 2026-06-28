package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType

/** Starter exercise library inserted on first launch. */
object SeedData {

    private fun weighted(name: String, category: ExerciseCategory = ExerciseCategory.BARBELL) =
        Exercise(
            name = name,
            category = category,
            metricType = MetricType.WEIGHT,
            unit = MetricType.WEIGHT.defaultUnit,
            tracksRepMax = true
        )

    private fun machine(name: String, metric: MetricType) =
        Exercise(
            name = name,
            category = ExerciseCategory.MACHINE,
            metricType = metric,
            unit = metric.defaultUnit,
            tracksRepMax = false
        )

    val defaults: List<Exercise> = listOf(
        // Olympic + squat lifts (Clean = squat clean, Snatch = squat snatch).
        weighted("Snatch"),
        weighted("Clean & Jerk"),
        weighted("Clean"),
        weighted("Jerk"),
        weighted("Power Snatch"),
        weighted("Power Clean"),
        weighted("Front Squat"),
        weighted("Back Squat"),
        weighted("Overhead Squat"),
        weighted("Deadlift"),
        weighted("Strict Press"),
        weighted("Push Press"),
        weighted("Bench Press"),
        // Machines / monostructural.
        machine("Air Bike", MetricType.CALORIES),
        machine("Rower", MetricType.CALORIES),
        machine("SkiErg", MetricType.CALORIES),
        machine("Echo/Air Row", MetricType.CALORIES),
        machine("Run", MetricType.DISTANCE)
    )

    suspend fun populate(dao: ExerciseDao) {
        if (dao.count() == 0) {
            dao.insertAll(defaults)
        }
    }
}
