package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import java.util.Locale

/**
 * Sample dataset backing demo mode: two cycles, eight weeks of snatch/clean
 * sessions with progressive loading (warm-ups, waves, the odd missed lift),
 * machine intervals and a rep-max history — enough to light up every screen.
 * Dates are relative to [snapshot]'s `today` so charts always look current.
 * Ids are hand-assigned; they only ever live in the separate demo database.
 */
object DemoData {

    /**
     * Bump when the generated dataset changes; [DataModeManager] re-seeds any
     * demo database created from an older version.
     */
    const val SEED_VERSION = 3

    fun snapshot(today: java.time.LocalDate = java.time.LocalDate.now()): BackupData =
        Builder(today).build()

    private class Builder(private val today: java.time.LocalDate) {

        private val exercises = SeedData.defaults.mapIndexed { i, e -> e.copy(id = i + 1L) }
        private fun exerciseId(name: String) = exercises.first { it.name == name }.id

        private val snatch = exerciseId("Snatch")
        private val cleanJerk = exerciseId("Clean & Jerk")
        private val clean = exerciseId("Clean")
        private val frontSquat = exerciseId("Front Squat")
        private val backSquat = exerciseId("Back Squat")
        private val airBike = exerciseId("Air Bike")

        private val cycles = listOf(
            Cycle(
                id = 1, name = "Foundation block",
                startDate = today.minusWeeks(16), endDate = today.minusWeeks(9),
                goal = "Rebuild the strength base"
            ),
            Cycle(
                id = 2, name = "Olympic Lifting & Strength Block",
                startDate = today.minusWeeks(4), endDate = today.plusWeeks(4),
                goal = "Peaking Snatch & Clean & Jerk 1RM while building front squat stability and threshold capacity.", isActive = true
            )
        )

        private val routines = listOf(
            Routine(
                id = 1, name = "3-Position Snatch", mainExerciseId = snatch,
                description = "High hang + hang + floor", defaultFormat = "E2MOM"
            ),
            Routine(
                id = 2, name = "Clean + Front Squat + Jerk", mainExerciseId = cleanJerk,
                description = "1 clean + 2 front squats + 1 jerk", defaultFormat = "E3MOM"
            )
        )

        private val sessions = mutableListOf<Session>()
        private val blocks = mutableListOf<SessionBlock>()
        private val sets = mutableListOf<BlockSet>()
        private val repMaxes = mutableListOf<RepMax>()

        fun build(): BackupData {
            (0..7).forEach { week ->
                snatchDay(week)
                cleanDay(week)
            }
            repMaxHistory()
            return BackupData(cycles, exercises, routines, sessions, blocks, sets, repMaxes)
        }

        // Two sessions a week, ending today: snatch day, then clean day 3 days later.
        private fun snatchDate(week: Int) = today.minusDays(((7 - week) * 7 + 3).toLong())
        private fun cleanDate(week: Int) = today.minusDays(((7 - week) * 7).toLong())

        private fun snatchDay(week: Int) {
            val s = session(snatchDate(week), "Snatch day")

            block(
                s, "Warm-up", BlockKind.WARMUP,
                description = "3 rounds: 10 cal Air Bike · 10 PVC pass-throughs · 5 OHS with empty bar"
            )

            // Snatch waves: two 3-2-1 waves, second slightly heavier. The single
            // of wave 2 is missed on a couple of weeks so charts show real dips.
            val waves = block(
                s, "Snatch waves", BlockKind.STRENGTH,
                format = "Wave", scheme = "3-2-1 · 3-2-1", exerciseId = snatch
            )
            val top = 66.0 + week * 2 // 66 → 80 kg over the cycle
            set(waves, reps = 3, weight = 40.0, warmup = true)
            set(waves, reps = 3, weight = 50.0, warmup = true)
            set(waves, reps = 3, weight = top - 8, group = 1)
            set(waves, reps = 2, weight = top - 4, group = 1)
            set(waves, reps = 1, weight = top - 2, group = 1)
            set(waves, reps = 3, weight = top - 6, group = 2)
            set(waves, reps = 2, weight = top - 2, group = 2)
            set(waves, reps = 1, weight = top, group = 2, failed = week == 2 || week == 5)

            // Positional work performed as the saved 3-Position Snatch routine.
            val complex = block(
                s, "3-Position Snatch", BlockKind.STRENGTH,
                format = "E2MOM", scheme = "6x1", exerciseId = snatch, routineId = 1
            )
            repeat(6) { i ->
                set(complex, reps = 1, weight = 48.0 + week * 1.5 + if (i >= 3) 2 else 0)
            }

            val squat = block(
                s, "Back Squat", BlockKind.ACCESSORY,
                format = "Sets x Reps", scheme = "5x5", exerciseId = backSquat
            )
            val squatWeight = 96.0 + week * 3 - if (week == 4) 9 else 0 // deload week 4
            repeat(5) { set(squat, reps = 5, weight = squatWeight) }

            block(
                s, "Conditioning", BlockKind.METCON, format = "AMRAP",
                description = "12' AMRAP: 12 cal Air Bike · 10 wall balls (9 kg) · 8 toes-to-bar",
                resultText = "${4 + week / 3} rounds + ${5 + week} reps"
            )
        }

        private fun cleanDay(week: Int) {
            val s = session(cleanDate(week), "Clean & Jerk day")

            block(
                s, "Warm-up", BlockKind.WARMUP,
                description = "500 m row · hip openers · 3x3 muscle cleans with empty bar"
            )

            val cj = block(
                s, "Clean & Jerk", BlockKind.STRENGTH,
                format = "E2MOM", scheme = "5x(1+1)", exerciseId = cleanJerk
            )
            val base = 78.0 + week * 2.5 // top 84 → 101.5 kg over the cycle
            set(cj, reps = 2, weight = 50.0, warmup = true)
            set(cj, reps = 2, weight = 60.0, warmup = true)
            listOf(base, base + 2, base + 4, base + 5, base + 6).forEachIndexed { i, w ->
                set(cj, reps = 2, weight = w, failed = week == 6 && i == 4)
            }

            // The saved Clean + Front Squat + Jerk complex, building to a heavy single.
            val complex = block(
                s, "Clean + Front Squat + Jerk", BlockKind.STRENGTH,
                format = "E3MOM", scheme = "5x1", exerciseId = cleanJerk, routineId = 2
            )
            listOf(0.0, 2.0, 4.0, 4.0, 6.0).forEach { d ->
                set(complex, reps = 1, weight = 66.0 + week * 2 + d)
            }

            val fs = block(
                s, "Front Squat", BlockKind.ACCESSORY,
                format = "Sets x Reps", scheme = "4x4", exerciseId = frontSquat
            )
            repeat(4) { set(fs, reps = 4, weight = 84.0 + week * 2.5) }

            val bike = block(
                s, "Air Bike intervals", BlockKind.OTHER,
                format = "EMOM", scheme = "5x40s", exerciseId = airBike
            )
            repeat(5) { i -> set(bike, reps = 1, metric = 10.5 + week * 0.5 + (i % 3) * 0.5) }

            block(
                s, "Conditioning", BlockKind.METCON, format = "For Time",
                description = "21-15-9: thrusters (42.5 kg) · pull-ups",
                resultText = String.format(Locale.US, "%d:%02d", 7, 52 - week * 4)
            )
        }

        private fun repMaxHistory() {
            // Older marks from the foundation block, so charts span both cycles.
            repMax(snatch, 1, 70.0, today.minusWeeks(10), cycleId = 1)
            repMax(cleanJerk, 1, 88.0, today.minusWeeks(10), cycleId = 1)
            repMax(backSquat, 1, 135.0, today.minusWeeks(11), cycleId = 1)

            repMax(snatch, 1, 74.0, snatchDate(1))
            repMax(snatch, 1, 78.0, snatchDate(4))
            repMax(snatch, 1, 81.0, snatchDate(7))
            repMax(snatch, 3, 66.0, snatchDate(2))
            repMax(snatch, 3, 70.0, snatchDate(6))
            repMax(cleanJerk, 1, 92.0, cleanDate(2))
            repMax(cleanJerk, 1, 97.5, cleanDate(7))
            repMax(clean, 1, 95.0, cleanDate(3))
            repMax(clean, 1, 100.0, cleanDate(6))
            repMax(backSquat, 1, 142.5, snatchDate(3))
            repMax(backSquat, 1, 148.0, snatchDate(6))
            repMax(backSquat, 5, 120.0, snatchDate(2))
            repMax(backSquat, 5, 126.0, snatchDate(6))
            repMax(frontSquat, 1, 118.0, cleanDate(5))
        }

        // --- Entity helpers with hand-assigned ids ---------------------------
        private fun session(date: java.time.LocalDate, title: String): Long {
            val id = sessions.size + 1L
            sessions += Session(id = id, cycleId = 2, date = date, title = title)
            return id
        }

        private fun block(
            sessionId: Long,
            name: String,
            kind: BlockKind,
            format: String = "",
            scheme: String = "",
            exerciseId: Long? = null,
            routineId: Long? = null,
            description: String = "",
            resultText: String = ""
        ): Long {
            val id = blocks.size + 1L
            blocks += SessionBlock(
                id = id,
                sessionId = sessionId,
                position = blocks.count { it.sessionId == sessionId },
                name = name,
                kind = kind,
                format = format,
                scheme = scheme,
                mainExerciseId = exerciseId,
                routineId = routineId,
                description = description,
                resultText = resultText
            )
            return id
        }

        private fun set(
            blockId: Long,
            reps: Int,
            weight: Double? = null,
            metric: Double? = null,
            warmup: Boolean = false,
            failed: Boolean = false,
            group: Int? = null
        ) {
            sets += BlockSet(
                id = sets.size + 1L,
                blockId = blockId,
                position = sets.count { it.blockId == blockId },
                groupIndex = group,
                reps = reps,
                weight = weight,
                metricValue = metric,
                isWarmup = warmup,
                isFailed = failed
            )
        }

        private fun repMax(
            exerciseId: Long,
            reps: Int,
            weight: Double,
            date: java.time.LocalDate,
            cycleId: Long = 2
        ) {
            repMaxes += RepMax(
                id = repMaxes.size + 1L,
                exerciseId = exerciseId,
                reps = reps,
                weight = weight,
                date = date,
                cycleId = cycleId
            )
        }
    }
}
