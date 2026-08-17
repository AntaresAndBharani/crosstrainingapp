package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.dao.CycleDao
import com.fractanomics.crosstraining.data.dao.CycleGoalDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.util.RepScheme
import java.time.LocalDate

/** Starter library inserted on first launch. */
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
        weighted("Thruster"),
        weighted("Pull-ups", ExerciseCategory.GYMNASTICS),
        weighted("Push-ups", ExerciseCategory.GYMNASTICS),
        weighted("Air Squat", ExerciseCategory.GYMNASTICS),
        weighted("Toes to Bar", ExerciseCategory.GYMNASTICS),
        weighted("Box Jumps", ExerciseCategory.GYMNASTICS),
        machine("Air Bike", MetricType.CALORIES),
        machine("Rower", MetricType.CALORIES),
        machine("SkiErg", MetricType.CALORIES),
        machine("Run", MetricType.DISTANCE)
    )

    suspend fun populate(
        exerciseDao: ExerciseDao,
        routineDao: RoutineDao,
        cycleDao: CycleDao,
        cycleGoalDao: CycleGoalDao? = null,
        force: Boolean = false,
        isProduction: Boolean = false
    ) {
        if (force || exerciseDao.count() == 0) {
            exerciseDao.insertAll(defaults)
        }

        if (isProduction) return

        val allExercises = exerciseDao.getAllOnce()
        val exMap: Map<String, Exercise> = allExercises.associateBy { it.name }

        if (force || cycleDao.getAllOnce().isEmpty()) {
            val defaultCycle = Cycle(
                name = "Olympic Lifting & Strength Block",
                startDate = LocalDate.now().minusWeeks(4),
                endDate = LocalDate.now().plusWeeks(4),
                goal = "Peaking Snatch & Clean & Jerk 1RM while building front squat stability and threshold capacity.",
                isActive = true
            )
            val cycleId = cycleDao.insert(defaultCycle)
            if (cycleGoalDao != null) {
                val snatchId = exMap["Snatch"]?.id
                val cleanJerkId = exMap["Clean & Jerk"]?.id
                val backSquatId = exMap["Back Squat"]?.id
                val frontSquatId = exMap["Front Squat"]?.id

                val sampleGoals = listOfNotNull(
                    snatchId?.let { CycleGoal(cycleId = cycleId, exerciseId = it, targetReps = 1, startWeight = 70.0, targetWeight = 85.0, notes = "Full snatch form") },
                    cleanJerkId?.let { CycleGoal(cycleId = cycleId, exerciseId = it, targetReps = 1, startWeight = 90.0, targetWeight = 105.0, notes = "Solid split jerk") },
                    backSquatId?.let { CycleGoal(cycleId = cycleId, exerciseId = it, targetReps = 1, startWeight = 120.0, targetWeight = 140.0, notes = "Heavy leg drive") },
                    frontSquatId?.let { CycleGoal(cycleId = cycleId, exerciseId = it, targetReps = 3, startWeight = 100.0, targetWeight = 120.0, notes = "Clean recovery stability") }
                )
                cycleGoalDao.insertAll(sampleGoals)
            }
        }

        if (force || routineDao.getAllOnce().isEmpty()) {
            // 1. Fran
            val franEx = exMap["Thruster"]
            val franId = routineDao.insert(
                Routine(
                    name = "Fran",
                    description = "21-15-9 reps for time of 43kg Thrusters and Pull-ups.",
                    mainExerciseId = franEx?.id,
                    defaultFormat = "21-15-9 For Time"
                )
            )
            routineDao.insertBlocks(
                listOf(
                    RoutineBlock(
                        routineId = franId,
                        position = 0,
                        name = "Fran Metcon",
                        kind = BlockKind.METABOLIC,
                        format = "21-15-9 For Time",
                        setsCount = 3,
                        targetRepsScheme = "21-15-9",
                        exerciseIdsCsv = listOfNotNull(exMap["Thruster"]?.id, exMap["Pull-ups"]?.id).joinToString(","),
                        notes = "43 kg (95 lbs) Thrusters & Pull-ups"
                    )
                )
            )

            // 2. 5x5 Back Squat Strength
            val squatEx = exMap["Back Squat"]
            val squatId = routineDao.insert(
                Routine(
                    name = "5x5 Back Squat Heavy",
                    description = "5 sets of 5 reps heavy working sets with 3 min rest.",
                    mainExerciseId = squatEx?.id,
                    defaultFormat = "5x5 Heavy"
                )
            )
            routineDao.insertBlocks(
                listOf(
                    RoutineBlock(
                        routineId = squatId,
                        position = 0,
                        name = "5x5 Squat Block",
                        kind = BlockKind.STRENGTH,
                        format = "Rest 3m",
                        setsCount = 5,
                        targetRepsScheme = "5",
                        exerciseIdsCsv = listOfNotNull(squatEx?.id).joinToString(","),
                        notes = "Build to a heavy 5x5 working weight"
                    )
                )
            )

            // 3. Heavy Snatch Wave 3-2-1
            val snatchEx = exMap["Snatch"]
            val snatchId = routineDao.insert(
                Routine(
                    name = "Snatch Wave 3-2-1",
                    description = "12 sets wave progression: 3-2-1-3-2-1-3-2-1-1-1-1.",
                    mainExerciseId = snatchEx?.id,
                    defaultFormat = "E2MOM 12 Sets"
                )
            )
            routineDao.insertBlocks(
                listOf(
                    RoutineBlock(
                        routineId = snatchId,
                        position = 0,
                        name = "Snatch Wave Block",
                        kind = BlockKind.WEIGHTLIFTING,
                        format = "E2MOM",
                        setsCount = 12,
                        targetRepsScheme = RepScheme.WAVE_321,
                        exerciseIdsCsv = listOfNotNull(snatchEx?.id).joinToString(","),
                        notes = "Increase load on each 3-2-1 wave"
                    )
                )
            )

            // 4. Tabata Engine
            val bikeEx = exMap["Air Bike"]
            val tabataId = routineDao.insert(
                Routine(
                    name = "Tabata Air Bike & Rower",
                    description = "8 rounds of 20s sprint / 10s rest per machine.",
                    mainExerciseId = bikeEx?.id,
                    defaultFormat = "Tabata (20s / 10s)"
                )
            )
            routineDao.insertBlocks(
                listOf(
                    RoutineBlock(
                        routineId = tabataId,
                        position = 0,
                        name = "Air Bike Tabata",
                        kind = BlockKind.METABOLIC,
                        format = "Tabata",
                        setsCount = 8,
                        targetRepsScheme = "20s/10s",
                        exerciseIdsCsv = listOfNotNull(bikeEx?.id).joinToString(","),
                        notes = "Max calories per 20s sprint"
                    )
                )
            )
        }
    }
}
