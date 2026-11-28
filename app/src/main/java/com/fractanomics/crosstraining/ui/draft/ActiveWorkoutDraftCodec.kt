package com.fractanomics.crosstraining.ui.draft

import java.time.LocalDate

/**
 * Pure-Kotlin JSON serializer/deserializer for [ActiveWorkoutDraftSnapshot].
 *
 * Intentionally avoids `org.json.JSONObject` / `JSONArray` so pure-JVM unit tests
 * (where Android `org.json` stubs return empty defaults) execute deterministically.
 */
object ActiveWorkoutDraftCodec {

    fun encode(snapshot: ActiveWorkoutDraftSnapshot): String {
        val sb = StringBuilder(2048)
        writeSnapshot(sb, snapshot)
        return sb.toString()
    }

    fun decode(raw: String?): ActiveWorkoutDraftSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val parsed = JsonParser(raw).parseRoot() as? Map<*, *> ?: return null
            val schemaVersion = (parsed["schemaVersion"] as? Number)?.toInt() ?: return null
            if (schemaVersion != ActiveWorkoutDraftSnapshot.CURRENT_SCHEMA_VERSION) return null
            readSnapshot(parsed)
        }.getOrNull()
    }

    // --- Writer ---------------------------------------------------------------

    private fun writeSnapshot(sb: StringBuilder, s: ActiveWorkoutDraftSnapshot) {
        sb.append('{')
        var first = true
        fun field(name: String, writer: () -> Unit) {
            if (!first) sb.append(',')
            first = false
            writeString(sb, name)
            sb.append(':')
            writer()
        }
        field("schemaVersion") { sb.append(s.schemaVersion) }
        field("selectedCycleId") { writeNullableLong(sb, s.selectedCycleId) }
        field("dateEpochDay") { sb.append(s.dateEpochDay) }
        field("title") { writeString(sb, s.title) }
        field("notes") { writeString(sb, s.notes) }
        field("showSessionMeta") { sb.append(s.showSessionMeta) }
        field("loadedRoutineId") { writeNullableLong(sb, s.loadedRoutineId) }
        field("loadedRoutineTitle") { writeString(sb, s.loadedRoutineTitle) }
        field("initialRoutineSignature") { writeString(sb, s.initialRoutineSignature) }
        field("syncRoutineTemplateChecked") { sb.append(s.syncRoutineTemplateChecked) }
        field("scrollOffsetPx") { sb.append(s.scrollOffsetPx) }
        field("updatedAtEpochMillis") { sb.append(s.updatedAtEpochMillis) }
        field("loadedBlocks") { writeArray(sb, s.loadedBlocks) { writeLoadedBlock(sb, it) } }
        field("legacyBlocks") { writeArray(sb, s.legacyBlocks) { writeLegacyBlock(sb, it) } }
        sb.append('}')
    }

    private fun writeLoadedBlock(sb: StringBuilder, b: ActiveLoadedBlockSnapshot) {
        sb.append('{')
        writeKV(sb, "uiKey", b.uiKey, first = true)
        writeKV(sb, "itemKind", b.itemKind)
        writeKVLong(sb, "blockId", b.blockId)
        writeKV(sb, "blockTitle", b.blockTitle)
        writeKV(sb, "blockInstructions", b.blockInstructions)
        writeKVRaw(sb, "blockExercises") {
            writeArray(sb, b.blockExercises) { ex ->
                sb.append('{')
                writeKVLong(sb, "id", ex.id, first = true)
                writeKV(sb, "name", ex.name)
                writeKVBool(sb, "tracksRepMax", ex.tracksRepMax)
                sb.append('}')
            }
        }
        writeKV(sb, "rawFormat", b.rawFormat)
        writeKV(sb, "rawScheme", b.rawScheme)
        writeKV(sb, "rawSection", b.rawSection)
        writeKV(sb, "rawKind", b.rawKind)
        writeKVBool(sb, "rawIsCompleted", b.rawIsCompleted)
        writeKVNullableString(sb, "lastSessionDateFormatted", b.lastSessionDateFormatted)
        writeKVBool(sb, "hasPreFilledHistory", b.hasPreFilledHistory)
        writeKVRaw(sb, "trackedPrSlots") {
            writeArray(sb, b.trackedPrSlots) { writePrSlot(sb, it) }
        }
        writeKVRaw(sb, "prWeightOverrides") { writeStringMap(sb, b.prWeightOverrides) }
        writeKVRaw(sb, "customWeightOverrideFlags") { writeBooleanMap(sb, b.customWeightOverrideFlags) }
        writeKVRaw(sb, "explicitlyAppliedPrKeys") { writeBooleanMap(sb, b.explicitlyAppliedPrKeys) }
        writeKVRaw(sb, "sourceRoutineItem") {
            if (b.sourceRoutineItem == null) sb.append("null") else writeRoutineItem(sb, b.sourceRoutineItem)
        }
        writeKVRaw(sb, "exerciseSections") {
            writeArray(sb, b.exerciseSections) { writeExerciseSection(sb, it) }
        }
        sb.append('}')
    }

    private fun writePrSlot(sb: StringBuilder, slot: ActiveTrackedPrSlotSnapshot) {
        sb.append('{')
        writeKVLong(sb, "movementId", slot.movementId, first = true)
        writeKV(sb, "movementName", slot.movementName)
        writeKVInt(sb, "rmReps", slot.rmReps)
        sb.append('}')
    }

    private fun writeRoutineTargetSpec(sb: StringBuilder, spec: ActiveRoutineTargetSpecSnapshot) {
        sb.append('{')
        writeKV(sb, "metric", spec.metric, first = true)
        writeKV(sb, "mode", spec.mode)
        writeKVInt(sb, "fixedValue", spec.fixedValue)
        writeKVRaw(sb, "perSetValues") {
            writeArray(sb, spec.perSetValues) { sb.append(it) }
        }
        sb.append('}')
    }

    private fun writeRoutineItem(sb: StringBuilder, item: ActiveRoutineItemSnapshot) {
        sb.append('{')
        writeKV(sb, "kind", item.kind, first = true)
        writeKVLong(sb, "blockId", item.blockId)
        writeKV(sb, "blockTitle", item.blockTitle)
        writeKVLong(sb, "exerciseId", item.exerciseId)
        writeKV(sb, "exerciseName", item.exerciseName)
        writeKV(sb, "note", item.note)
        writeKVRaw(sb, "defaultTrackedPrs") {
            writeArray(sb, item.defaultTrackedPrs) { writePrSlot(sb, it) }
        }
        writeKVRaw(sb, "targetSetsCount") {
            if (item.targetSetsCount == null) sb.append("null") else sb.append(item.targetSetsCount)
        }
        writeKVRaw(sb, "targetSpec") {
            if (item.targetSpec == null) sb.append("null") else writeRoutineTargetSpec(sb, item.targetSpec)
        }
        writeKVRaw(sb, "exerciseTargets") {
            sb.append('{')
            var first = true
            item.exerciseTargets.forEach { (k, v) ->
                if (!first) sb.append(',')
                first = false
                writeString(sb, k.toString())
                sb.append(':')
                writeRoutineTargetSpec(sb, v)
            }
            sb.append('}')
        }
        writeKVRaw(sb, "exerciseTargetsByName") {
            sb.append('{')
            var first = true
            item.exerciseTargetsByName.forEach { (k, v) ->
                if (!first) sb.append(',')
                first = false
                writeString(sb, k)
                sb.append(':')
                writeRoutineTargetSpec(sb, v)
            }
            sb.append('}')
        }
        sb.append('}')
    }

    private fun writeExerciseSection(sb: StringBuilder, sec: ActiveExerciseSectionSnapshot) {
        sb.append('{')
        writeKVLong(sb, "exerciseId", sec.exerciseId, first = true)
        writeKV(sb, "exerciseName", sec.exerciseName)
        writeKVNullableString(sb, "lastSessionDateLabel", sec.lastSessionDateLabel)
        writeKV(sb, "activeMetric", sec.activeMetric)
        writeKVRaw(sb, "weights") { writeArray(sb, sec.weights) { writeString(sb, it) } }
        writeKVRaw(sb, "perSetReps") { writeArray(sb, sec.perSetReps) { sb.append(it) } }
        writeKVRaw(sb, "repsInputs") { writeArray(sb, sec.repsInputs) { writeString(sb, it) } }
        writeKVRaw(sb, "metricInputs") { writeArray(sb, sec.metricInputs) { writeString(sb, it) } }
        writeKVRaw(sb, "groupIndices") {
            writeArray(sb, sec.groupIndices) { if (it == null) sb.append("null") else sb.append(it) }
        }
        writeKVRaw(sb, "skippedFlags") { writeArray(sb, sec.skippedFlags) { sb.append(it) } }
        writeKVInt(sb, "defaultReps", sec.defaultReps)
        writeKVRaw(sb, "sourceBlockId") { writeNullableLong(sb, sec.sourceBlockId) }
        writeKV(sb, "rawFormat", sec.rawFormat)
        writeKV(sb, "rawScheme", sec.rawScheme)
        writeKV(sb, "rawSection", sec.rawSection)
        writeKV(sb, "rawKind", sec.rawKind)
        writeKVBool(sb, "rawIsCompleted", sec.rawIsCompleted)
        writeKV(sb, "subBlockTitle", sec.subBlockTitle)
        writeKVNullableString(sb, "targetBanner", sec.targetBanner)
        writeKV(sb, "rawNotes", sec.rawNotes)
        writeKV(sb, "libraryMetricType", sec.libraryMetricType)
        sb.append('}')
    }

    private fun writeLegacyBlock(sb: StringBuilder, blk: ActiveLegacyBlockSnapshot) {
        sb.append('{')
        writeKV(sb, "name", blk.name, first = true)
        writeKV(sb, "kind", blk.kind)
        writeKV(sb, "format", blk.format)
        writeKV(sb, "scheme", blk.scheme)
        writeKVRaw(sb, "exerciseId") { writeNullableLong(sb, blk.exerciseId) }
        writeKV(sb, "newExerciseName", blk.newExerciseName)
        writeKVRaw(sb, "routineId") { writeNullableLong(sb, blk.routineId) }
        writeKVRaw(sb, "sequenceExerciseIds") { writeArray(sb, blk.sequenceExerciseIds) { sb.append(it) } }
        writeKV(sb, "description", blk.description)
        writeKV(sb, "resultText", blk.resultText)
        writeKV(sb, "resultValue", blk.resultValue)
        writeKVBool(sb, "recordRm", blk.recordRm)
        writeKV(sb, "rmReps", blk.rmReps)
        writeKV(sb, "rmWeight", blk.rmWeight)
        writeKVBool(sb, "isExpanded", blk.isExpanded)
        writeKV(sb, "section", blk.section)
        writeKV(sb, "exerciseIdsCsv", blk.exerciseIdsCsv)
        writeKV(sb, "subBlock", blk.subBlock)
        writeKVBool(sb, "isCompleted", blk.isCompleted)
        writeKVRaw(sb, "sets") {
            writeArray(sb, blk.sets) { st ->
                sb.append('{')
                writeKV(sb, "reps", st.reps, first = true)
                writeKV(sb, "value", st.value)
                writeKV(sb, "group", st.group)
                writeKVBool(sb, "isWarmup", st.isWarmup)
                writeKVBool(sb, "isFailed", st.isFailed)
                sb.append('}')
            }
        }
        sb.append('}')
    }

    private fun writeKV(sb: StringBuilder, key: String, value: String, first: Boolean = false) {
        writeKVRaw(sb, key, first) { writeString(sb, value) }
    }

    private fun writeKVNullableString(sb: StringBuilder, key: String, value: String?, first: Boolean = false) {
        writeKVRaw(sb, key, first) {
            if (value == null) sb.append("null") else writeString(sb, value)
        }
    }

    private fun writeKVLong(sb: StringBuilder, key: String, value: Long, first: Boolean = false) {
        writeKVRaw(sb, key, first) { sb.append(value) }
    }

    private fun writeKVInt(sb: StringBuilder, key: String, value: Int, first: Boolean = false) {
        writeKVRaw(sb, key, first) { sb.append(value) }
    }

    private fun writeKVBool(sb: StringBuilder, key: String, value: Boolean, first: Boolean = false) {
        writeKVRaw(sb, key, first) { sb.append(value) }
    }

    private inline fun writeKVRaw(sb: StringBuilder, key: String, first: Boolean = false, writer: () -> Unit) {
        if (!first) sb.append(',')
        writeString(sb, key)
        sb.append(':')
        writer()
    }

    private fun writeNullableLong(sb: StringBuilder, value: Long?) {
        if (value == null) sb.append("null") else sb.append(value)
    }

    private inline fun <T> writeArray(sb: StringBuilder, items: List<T>, writeItem: (T) -> Unit) {
        sb.append('[')
        items.forEachIndexed { idx, item ->
            if (idx > 0) sb.append(',')
            writeItem(item)
        }
        sb.append(']')
    }

    private fun writeStringMap(sb: StringBuilder, map: Map<String, String>) {
        sb.append('{')
        var first = true
        map.forEach { (k, v) ->
            if (!first) sb.append(',')
            first = false
            writeString(sb, k)
            sb.append(':')
            writeString(sb, v)
        }
        sb.append('}')
    }

    private fun writeBooleanMap(sb: StringBuilder, map: Map<String, Boolean>) {
        sb.append('{')
        var first = true
        map.forEach { (k, v) ->
            if (!first) sb.append(',')
            first = false
            writeString(sb, k)
            sb.append(':')
            sb.append(v)
        }
        sb.append('}')
    }

    private fun writeString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    // --- Reader ---------------------------------------------------------------

    private fun readSnapshot(m: Map<*, *>): ActiveWorkoutDraftSnapshot {
        val loadedList = (m["loadedBlocks"] as? List<*>)?.mapNotNull {
            (it as? Map<*, *>)?.let(::readLoadedBlock)
        }.orEmpty()
        val legacyList = (m["legacyBlocks"] as? List<*>)?.mapNotNull {
            (it as? Map<*, *>)?.let(::readLegacyBlock)
        }.orEmpty()
        return ActiveWorkoutDraftSnapshot(
            schemaVersion = (m["schemaVersion"] as? Number)?.toInt() ?: ActiveWorkoutDraftSnapshot.CURRENT_SCHEMA_VERSION,
            selectedCycleId = (m["selectedCycleId"] as? Number)?.toLong(),
            dateEpochDay = (m["dateEpochDay"] as? Number)?.toLong() ?: LocalDate.now().toEpochDay(),
            title = m["title"] as? String ?: "",
            notes = m["notes"] as? String ?: "",
            showSessionMeta = m["showSessionMeta"] as? Boolean ?: false,
            loadedRoutineId = (m["loadedRoutineId"] as? Number)?.toLong(),
            loadedRoutineTitle = m["loadedRoutineTitle"] as? String ?: "",
            initialRoutineSignature = m["initialRoutineSignature"] as? String ?: "",
            syncRoutineTemplateChecked = m["syncRoutineTemplateChecked"] as? Boolean ?: false,
            scrollOffsetPx = ((m["scrollOffsetPx"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            loadedBlocks = loadedList,
            legacyBlocks = legacyList,
            updatedAtEpochMillis = (m["updatedAtEpochMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun readLoadedBlock(m: Map<*, *>): ActiveLoadedBlockSnapshot {
        val uiKey = (m["uiKey"] as? String).orEmpty()
        require(uiKey.isNotBlank())
        val blockExercises = (m["blockExercises"] as? List<*>)?.mapNotNull { raw ->
            val em = raw as? Map<*, *> ?: return@mapNotNull null
            ActiveBlockExerciseSnapshot(
                id = (em["id"] as? Number)?.toLong() ?: 0L,
                name = em["name"] as? String ?: "",
                tracksRepMax = em["tracksRepMax"] as? Boolean ?: false
            )
        }.orEmpty()
        val trackedSlots = (m["trackedPrSlots"] as? List<*>)?.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let(::readPrSlot)
        }.orEmpty()
        val prOverrides = readStringMap(m["prWeightOverrides"] as? Map<*, *>)
        val customFlags = readBooleanMap(m["customWeightOverrideFlags"] as? Map<*, *>)
        val explicitFlags = readBooleanMap(m["explicitlyAppliedPrKeys"] as? Map<*, *>)
        val routineItem = (m["sourceRoutineItem"] as? Map<*, *>)?.let(::readRoutineItem)
        val sections = (m["exerciseSections"] as? List<*>)?.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let(::readExerciseSection)
        }.orEmpty()

        return ActiveLoadedBlockSnapshot(
            uiKey = uiKey,
            itemKind = m["itemKind"] as? String ?: "BLOCK",
            blockId = (m["blockId"] as? Number)?.toLong() ?: 0L,
            blockTitle = m["blockTitle"] as? String ?: "",
            blockInstructions = m["blockInstructions"] as? String ?: "",
            blockExercises = blockExercises,
            rawFormat = m["rawFormat"] as? String ?: "",
            rawScheme = m["rawScheme"] as? String ?: "",
            rawSection = m["rawSection"] as? String ?: "",
            rawKind = m["rawKind"] as? String ?: "STRENGTH",
            rawIsCompleted = m["rawIsCompleted"] as? Boolean ?: true,
            lastSessionDateFormatted = m["lastSessionDateFormatted"] as? String,
            hasPreFilledHistory = m["hasPreFilledHistory"] as? Boolean ?: false,
            trackedPrSlots = trackedSlots,
            prWeightOverrides = prOverrides,
            customWeightOverrideFlags = customFlags,
            explicitlyAppliedPrKeys = explicitFlags,
            sourceRoutineItem = routineItem,
            exerciseSections = sections
        )
    }

    private fun readPrSlot(m: Map<*, *>): ActiveTrackedPrSlotSnapshot =
        ActiveTrackedPrSlotSnapshot(
            movementId = (m["movementId"] as? Number)?.toLong() ?: 0L,
            movementName = m["movementName"] as? String ?: "",
            rmReps = (m["rmReps"] as? Number)?.toInt() ?: 1
        )

    private fun readRoutineTargetSpec(m: Map<*, *>): ActiveRoutineTargetSpecSnapshot =
        ActiveRoutineTargetSpecSnapshot(
            metric = m["metric"] as? String ?: "REPS",
            mode = m["mode"] as? String ?: "FIXED",
            fixedValue = (m["fixedValue"] as? Number)?.toInt() ?: 1,
            perSetValues = (m["perSetValues"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
        )

    private fun readRoutineItem(m: Map<*, *>): ActiveRoutineItemSnapshot {
        val defaultPrs = (m["defaultTrackedPrs"] as? List<*>)?.mapNotNull {
            (it as? Map<*, *>)?.let(::readPrSlot)
        }.orEmpty()
        val targetSpec = (m["targetSpec"] as? Map<*, *>)?.let(::readRoutineTargetSpec)
        val targetsById = linkedMapOf<Long, ActiveRoutineTargetSpecSnapshot>()
        (m["exerciseTargets"] as? Map<*, *>)?.forEach { (k, v) ->
            val id = (k as? String)?.toLongOrNull() ?: return@forEach
            val specMap = v as? Map<*, *> ?: return@forEach
            targetsById[id] = readRoutineTargetSpec(specMap)
        }
        val targetsByName = linkedMapOf<String, ActiveRoutineTargetSpecSnapshot>()
        (m["exerciseTargetsByName"] as? Map<*, *>)?.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val specMap = v as? Map<*, *> ?: return@forEach
            targetsByName[key] = readRoutineTargetSpec(specMap)
        }
        return ActiveRoutineItemSnapshot(
            kind = m["kind"] as? String ?: "BLOCK",
            blockId = (m["blockId"] as? Number)?.toLong() ?: 0L,
            blockTitle = m["blockTitle"] as? String ?: "",
            exerciseId = (m["exerciseId"] as? Number)?.toLong() ?: 0L,
            exerciseName = m["exerciseName"] as? String ?: "",
            note = m["note"] as? String ?: "",
            defaultTrackedPrs = defaultPrs,
            targetSetsCount = (m["targetSetsCount"] as? Number)?.toInt(),
            targetSpec = targetSpec,
            exerciseTargets = targetsById,
            exerciseTargetsByName = targetsByName
        )
    }

    private fun readExerciseSection(m: Map<*, *>): ActiveExerciseSectionSnapshot =
        ActiveExerciseSectionSnapshot(
            exerciseId = (m["exerciseId"] as? Number)?.toLong() ?: 0L,
            exerciseName = m["exerciseName"] as? String ?: "",
            lastSessionDateLabel = m["lastSessionDateLabel"] as? String,
            activeMetric = m["activeMetric"] as? String ?: "REPS",
            weights = readStringList(m["weights"] as? List<*>),
            perSetReps = (m["perSetReps"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
            repsInputs = readStringList(m["repsInputs"] as? List<*>),
            metricInputs = readStringList(m["metricInputs"] as? List<*>),
            groupIndices = (m["groupIndices"] as? List<*>)?.map { (it as? Number)?.toInt() }.orEmpty(),
            skippedFlags = (m["skippedFlags"] as? List<*>)?.map { it as? Boolean ?: false }.orEmpty(),
            defaultReps = (m["defaultReps"] as? Number)?.toInt() ?: 1,
            sourceBlockId = (m["sourceBlockId"] as? Number)?.toLong(),
            rawFormat = m["rawFormat"] as? String ?: "",
            rawScheme = m["rawScheme"] as? String ?: "",
            rawSection = m["rawSection"] as? String ?: "",
            rawKind = m["rawKind"] as? String ?: "STRENGTH",
            rawIsCompleted = m["rawIsCompleted"] as? Boolean ?: true,
            subBlockTitle = m["subBlockTitle"] as? String ?: "",
            targetBanner = m["targetBanner"] as? String,
            rawNotes = m["rawNotes"] as? String ?: "",
            libraryMetricType = m["libraryMetricType"] as? String ?: "WEIGHT"
        )

    private fun readLegacyBlock(m: Map<*, *>): ActiveLegacyBlockSnapshot {
        val sets = (m["sets"] as? List<*>)?.mapNotNull { raw ->
            val sm = raw as? Map<*, *> ?: return@mapNotNull null
            ActiveLegacySetSnapshot(
                reps = sm["reps"] as? String ?: "",
                value = sm["value"] as? String ?: "",
                group = sm["group"] as? String ?: "",
                isWarmup = sm["isWarmup"] as? Boolean ?: false,
                isFailed = sm["isFailed"] as? Boolean ?: false
            )
        }.orEmpty().ifEmpty { listOf(ActiveLegacySetSnapshot()) }

        return ActiveLegacyBlockSnapshot(
            name = m["name"] as? String ?: "",
            kind = m["kind"] as? String ?: "STRENGTH",
            format = m["format"] as? String ?: "",
            scheme = m["scheme"] as? String ?: "",
            exerciseId = (m["exerciseId"] as? Number)?.toLong(),
            newExerciseName = m["newExerciseName"] as? String ?: "",
            routineId = (m["routineId"] as? Number)?.toLong(),
            sequenceExerciseIds = (m["sequenceExerciseIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty(),
            description = m["description"] as? String ?: "",
            resultText = m["resultText"] as? String ?: "",
            resultValue = m["resultValue"] as? String ?: "",
            recordRm = m["recordRm"] as? Boolean ?: false,
            rmReps = m["rmReps"] as? String ?: "1",
            rmWeight = m["rmWeight"] as? String ?: "",
            isExpanded = m["isExpanded"] as? Boolean ?: false,
            section = m["section"] as? String ?: "",
            exerciseIdsCsv = m["exerciseIdsCsv"] as? String ?: "",
            subBlock = m["subBlock"] as? String ?: "",
            isCompleted = m["isCompleted"] as? Boolean ?: true,
            sets = sets
        )
    }

    private fun readStringList(raw: List<*>?): List<String> =
        raw?.map { it as? String ?: "" }.orEmpty()

    private fun readStringMap(raw: Map<*, *>?): Map<String, String> {
        if (raw == null) return emptyMap()
        val out = linkedMapOf<String, String>()
        raw.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val value = v as? String ?: return@forEach
            out[key] = value
        }
        return out
    }

    private fun readBooleanMap(raw: Map<*, *>?): Map<String, Boolean> {
        if (raw == null) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        raw.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val value = v as? Boolean ?: return@forEach
            out[key] = value
        }
        return out
    }

    // --- Minimal RFC-8259 Recursive-Descent Parser --------------------------

    private class JsonParser(private val src: String) {
        private var pos = 0

        fun parseRoot(): Any? {
            skipWhitespace()
            val v = parseValue()
            skipWhitespace()
            if (pos != src.length) error("Trailing characters at $pos")
            return v
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) error("Unexpected EOF")
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected char '$ch' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) error("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) error("Invalid unicode escape")
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            error("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                error("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (!src.startsWith("null", pos)) error("Invalid null at $pos")
            pos += 4
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                error("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}
