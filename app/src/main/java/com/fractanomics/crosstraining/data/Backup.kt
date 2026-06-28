package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionSet
import java.time.LocalDate

/** A full in-memory snapshot of the database used for export/import. */
data class BackupData(
    val cycles: List<Cycle> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val sets: List<SessionSet> = emptyList(),
    val repMaxes: List<RepMax> = emptyList()
)

/**
 * Serialises a [BackupData] to a single CSV file (and back). The file holds one
 * section per table, each marked by a `#table` line followed by a header row and
 * its data rows. Every value is RFC-4180 quoted, so the parser round-trips text
 * containing commas, quotes or newlines (e.g. session notes). IDs and foreign
 * keys are preserved so relationships survive a restore.
 */
object BackupCsv {

    private fun enc(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun row(values: List<String>): String = values.joinToString(",", postfix = "\n") { enc(it) }

    private fun s(value: Long?): String = value?.toString() ?: ""
    private fun s(value: Double?): String = value?.toString() ?: ""
    private fun s(date: LocalDate?): String = date?.toEpochDay()?.toString() ?: ""

    fun encode(data: BackupData): String {
        val sb = StringBuilder()
        sb.append("#crosstraining-backup-v1\n")

        sb.append("#cycles\n")
        sb.append(row(listOf("id", "name", "startDate", "endDate", "goal", "isActive")))
        data.cycles.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.name, s(it.startDate), s(it.endDate), it.goal,
                if (it.isActive) "1" else "0"
            )))
        }

        sb.append("#exercises\n")
        sb.append(row(listOf("id", "name", "category", "metricType", "unit", "tracksRepMax", "notes")))
        data.exercises.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.name, it.category.name, it.metricType.name, it.unit,
                if (it.tracksRepMax) "1" else "0", it.notes
            )))
        }

        sb.append("#routines\n")
        sb.append(row(listOf("id", "name", "mainExerciseId", "description", "defaultFormat")))
        data.routines.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.name, s(it.mainExerciseId), it.description, it.defaultFormat
            )))
        }

        sb.append("#sessions\n")
        sb.append(row(listOf("id", "cycleId", "routineId", "mainExerciseId", "date", "format", "repScheme", "notes")))
        data.sessions.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.cycleId.toString(), s(it.routineId), s(it.mainExerciseId),
                s(it.date), it.format, it.repScheme, it.notes
            )))
        }

        sb.append("#sets\n")
        sb.append(row(listOf("id", "sessionId", "position", "reps", "weight", "metricValue", "notes")))
        data.sets.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.sessionId.toString(), it.position.toString(), it.reps.toString(),
                s(it.weight), s(it.metricValue), it.notes
            )))
        }

        sb.append("#repMaxes\n")
        sb.append(row(listOf("id", "exerciseId", "reps", "weight", "date", "cycleId", "sessionId")))
        data.repMaxes.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.exerciseId.toString(), it.reps.toString(), it.weight.toString(),
                s(it.date), s(it.cycleId), s(it.sessionId)
            )))
        }

        return sb.toString()
    }

    fun decode(text: String): BackupData {
        val records = parseCsv(text)
        val cycles = mutableListOf<Cycle>()
        val exercises = mutableListOf<Exercise>()
        val routines = mutableListOf<Routine>()
        val sessions = mutableListOf<Session>()
        val sets = mutableListOf<SessionSet>()
        val repMaxes = mutableListOf<RepMax>()

        var section = ""
        var skipHeader = false
        for (rec in records) {
            if (rec.isEmpty() || (rec.size == 1 && rec[0].isBlank())) continue
            val first = rec[0]
            if (first.startsWith("#")) {
                val name = first.removePrefix("#")
                if (name.startsWith("crosstraining-backup")) {
                    // File version marker — not a table section.
                    section = ""
                    skipHeader = false
                } else {
                    section = name
                    skipHeader = true // the next record is this table's header row
                }
                continue
            }
            if (skipHeader) { skipHeader = false; continue } // this record is the header row
            when (section) {
                "cycles" -> cycles += Cycle(
                    id = rec.lng(0),
                    name = rec.str(1),
                    startDate = rec.date(2) ?: LocalDate.now(),
                    endDate = rec.date(3),
                    goal = rec.str(4),
                    isActive = rec.str(5) == "1"
                )
                "exercises" -> exercises += Exercise(
                    id = rec.lng(0),
                    name = rec.str(1),
                    category = runCatching { ExerciseCategory.valueOf(rec.str(2)) }.getOrDefault(ExerciseCategory.OTHER),
                    metricType = runCatching { MetricType.valueOf(rec.str(3)) }.getOrDefault(MetricType.WEIGHT),
                    unit = rec.str(4),
                    tracksRepMax = rec.str(5) == "1",
                    notes = rec.str(6)
                )
                "routines" -> routines += Routine(
                    id = rec.lng(0),
                    name = rec.str(1),
                    mainExerciseId = rec.lngOrNull(2),
                    description = rec.str(3),
                    defaultFormat = rec.str(4)
                )
                "sessions" -> sessions += Session(
                    id = rec.lng(0),
                    cycleId = rec.lng(1),
                    routineId = rec.lngOrNull(2),
                    mainExerciseId = rec.lngOrNull(3),
                    date = rec.date(4) ?: LocalDate.now(),
                    format = rec.str(5),
                    repScheme = rec.str(6),
                    notes = rec.str(7)
                )
                "sets" -> sets += SessionSet(
                    id = rec.lng(0),
                    sessionId = rec.lng(1),
                    position = rec.int(2),
                    reps = rec.int(3),
                    weight = rec.dblOrNull(4),
                    metricValue = rec.dblOrNull(5),
                    notes = rec.str(6)
                )
                "repMaxes" -> repMaxes += RepMax(
                    id = rec.lng(0),
                    exerciseId = rec.lng(1),
                    reps = rec.int(2),
                    weight = rec.dbl(3),
                    date = rec.date(4) ?: LocalDate.now(),
                    cycleId = rec.lngOrNull(5),
                    sessionId = rec.lngOrNull(6)
                )
            }
        }
        return BackupData(cycles, exercises, routines, sessions, sets, repMaxes)
    }

    // --- field accessors (tolerant of short rows) -----------------------------
    private fun List<String>.str(i: Int): String = getOrNull(i)?.trim() ?: ""
    private fun List<String>.lng(i: Int): Long = str(i).toLongOrNull() ?: 0L
    private fun List<String>.lngOrNull(i: Int): Long? = str(i).toLongOrNull()
    private fun List<String>.int(i: Int): Int = str(i).toIntOrNull() ?: 0
    private fun List<String>.dbl(i: Int): Double = str(i).toDoubleOrNull() ?: 0.0
    private fun List<String>.dblOrNull(i: Int): Double? = str(i).toDoubleOrNull()
    private fun List<String>.date(i: Int): LocalDate? =
        str(i).toLongOrNull()?.let { LocalDate.ofEpochDay(it) }

    /** Minimal RFC-4180 CSV tokenizer returning records of fields. */
    private fun parseCsv(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        var sawAny = false

        fun endField() { fields.add(field.toString()); field.setLength(0) }
        fun endRecord() { endField(); records.add(fields); fields = ArrayList(); sawAny = false }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { field.append('"'); i += 2 }
                    else { inQuotes = false; i++ }
                } else { field.append(c); i++ }
            } else when (c) {
                '"' -> { inQuotes = true; sawAny = true; i++ }
                ',' -> { endField(); sawAny = true; i++ }
                '\r' -> i++
                '\n' -> { endRecord(); i++ }
                else -> { field.append(c); sawAny = true; i++ }
            }
        }
        if (sawAny || field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
