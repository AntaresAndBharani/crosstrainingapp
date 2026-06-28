package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import java.time.LocalDate

/** A full in-memory snapshot of the database used for export/import. */
data class BackupData(
    val cycles: List<Cycle> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val blocks: List<SessionBlock> = emptyList(),
    val sets: List<BlockSet> = emptyList(),
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
    private fun s(value: Int?): String = value?.toString() ?: ""
    private fun s(value: Double?): String = value?.toString() ?: ""
    private fun s(date: LocalDate?): String = date?.toEpochDay()?.toString() ?: ""
    private fun b(value: Boolean): String = if (value) "1" else "0"

    fun encode(data: BackupData): String {
        val sb = StringBuilder()
        sb.append("#crosstraining-backup-v2\n")

        sb.append("#cycles\n")
        sb.append(row(listOf("id", "name", "startDate", "endDate", "goal", "isActive")))
        data.cycles.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.name, s(it.startDate), s(it.endDate), it.goal, b(it.isActive)
            )))
        }

        sb.append("#exercises\n")
        sb.append(row(listOf("id", "name", "category", "metricType", "unit", "tracksRepMax", "notes")))
        data.exercises.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.name, it.category.name, it.metricType.name, it.unit,
                b(it.tracksRepMax), it.notes
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
        sb.append(row(listOf("id", "cycleId", "date", "title", "notes")))
        data.sessions.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.cycleId.toString(), s(it.date), it.title, it.notes
            )))
        }

        sb.append("#blocks\n")
        sb.append(row(listOf(
            "id", "sessionId", "position", "name", "kind", "format", "scheme",
            "mainExerciseId", "routineId", "description", "resultText", "resultValue", "notes"
        )))
        data.blocks.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.sessionId.toString(), it.position.toString(), it.name,
                it.kind.name, it.format, it.scheme, s(it.mainExerciseId), s(it.routineId),
                it.description, it.resultText, s(it.resultValue), it.notes
            )))
        }

        sb.append("#sets\n")
        sb.append(row(listOf(
            "id", "blockId", "position", "groupIndex", "reps", "weight", "metricValue",
            "isWarmup", "isFailed", "notes"
        )))
        data.sets.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.blockId.toString(), it.position.toString(), s(it.groupIndex),
                it.reps.toString(), s(it.weight), s(it.metricValue), b(it.isWarmup), b(it.isFailed),
                it.notes
            )))
        }

        sb.append("#repMaxes\n")
        sb.append(row(listOf("id", "exerciseId", "reps", "weight", "date", "cycleId", "sessionId", "blockId")))
        data.repMaxes.forEach {
            sb.append(row(listOf(
                it.id.toString(), it.exerciseId.toString(), it.reps.toString(), it.weight.toString(),
                s(it.date), s(it.cycleId), s(it.sessionId), s(it.blockId)
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
        val blocks = mutableListOf<SessionBlock>()
        val sets = mutableListOf<BlockSet>()
        val repMaxes = mutableListOf<RepMax>()

        var section = ""
        var skipHeader = false
        for (rec in records) {
            if (rec.isEmpty() || (rec.size == 1 && rec[0].isBlank())) continue
            val first = rec[0]
            if (first.startsWith("#")) {
                val name = first.removePrefix("#")
                if (name.startsWith("crosstraining-backup")) {
                    section = ""
                    skipHeader = false
                } else {
                    section = name
                    skipHeader = true // the next record is this table's header row
                }
                continue
            }
            if (skipHeader) { skipHeader = false; continue }
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
                    date = rec.date(2) ?: LocalDate.now(),
                    title = rec.str(3),
                    notes = rec.str(4)
                )
                "blocks" -> blocks += SessionBlock(
                    id = rec.lng(0),
                    sessionId = rec.lng(1),
                    position = rec.int(2),
                    name = rec.str(3),
                    kind = runCatching { BlockKind.valueOf(rec.str(4)) }.getOrDefault(BlockKind.OTHER),
                    format = rec.str(5),
                    scheme = rec.str(6),
                    mainExerciseId = rec.lngOrNull(7),
                    routineId = rec.lngOrNull(8),
                    description = rec.str(9),
                    resultText = rec.str(10),
                    resultValue = rec.dblOrNull(11),
                    notes = rec.str(12)
                )
                "sets" -> sets += BlockSet(
                    id = rec.lng(0),
                    blockId = rec.lng(1),
                    position = rec.int(2),
                    groupIndex = rec.intOrNull(3),
                    reps = rec.int(4),
                    weight = rec.dblOrNull(5),
                    metricValue = rec.dblOrNull(6),
                    isWarmup = rec.str(7) == "1",
                    isFailed = rec.str(8) == "1",
                    notes = rec.str(9)
                )
                "repMaxes" -> repMaxes += RepMax(
                    id = rec.lng(0),
                    exerciseId = rec.lng(1),
                    reps = rec.int(2),
                    weight = rec.dbl(3),
                    date = rec.date(4) ?: LocalDate.now(),
                    cycleId = rec.lngOrNull(5),
                    sessionId = rec.lngOrNull(6),
                    blockId = rec.lngOrNull(7)
                )
            }
        }
        return BackupData(cycles, exercises, routines, sessions, blocks, sets, repMaxes)
    }

    // --- field accessors (tolerant of short rows) -----------------------------
    private fun List<String>.str(i: Int): String = getOrNull(i)?.trim() ?: ""
    private fun List<String>.lng(i: Int): Long = str(i).toLongOrNull() ?: 0L
    private fun List<String>.lngOrNull(i: Int): Long? = str(i).toLongOrNull()
    private fun List<String>.int(i: Int): Int = str(i).toIntOrNull() ?: 0
    private fun List<String>.intOrNull(i: Int): Int? = str(i).toIntOrNull()
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
