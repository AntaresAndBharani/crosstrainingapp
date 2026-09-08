package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind

/**
 * Represents a parsed set within a [ParsedDocumentBlock].
 *
 * @property reps Number of completed or target repetitions.
 * @property weight Load in kilograms (or pounds) if specified.
 * @property metricValue Non-weight metric value (e.g. calories, meters, seconds).
 * @property isWarmup Whether this is marked as a warmup set.
 * @property isFailed Whether this set failed (e.g. "fail", "fallo").
 * @property notes Additional annotations or partial info (e.g. "Partial (target 4)").
 */
data class ParsedDocumentSet(
    val reps: Int,
    val weight: Double? = null,
    val metricValue: Double? = null,
    val isWarmup: Boolean = false,
    val isFailed: Boolean = false,
    val notes: String = ""
)

/**
 * Represents a parsed workout block within a [ParsedWorkoutDocument].
 *
 * @property name Cleaned block name or movement name (e.g. "Clean + Hang Clean + Front Squat + Push to OverHead").
 * @property section Macro-block section header (e.g. "Strength & Power block", "Accessories block").
 * @property kind Block classification ([BlockKind.COMPLEX], [BlockKind.SUPERSET], [BlockKind.STRENGTH], [BlockKind.METCON], etc.).
 * @property format Interval or timing format (e.g. "E3MOM", "E2.5MOM", "AMRAP", "FOR TIME").
 * @property scheme Scheme or cluster identifier (e.g. "TRISET_1", "TRISET_2", "4x1", "5x3").
 * @property movements Component movements for complexes or supersets/trisets.
 * @property sets Individual parsed sets.
 * @property targetReps Target rep count specified in the block header (if any).
 * @property rawText Raw block text before decomposition.
 */
data class ParsedDocumentBlock(
    val name: String,
    val section: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val scheme: String = "",
    val movements: List<String> = emptyList(),
    val sets: List<ParsedDocumentSet> = emptyList(),
    val targetReps: Int? = null,
    val rawText: String = ""
)

/**
 * Structured result of parsing a raw workout markdown or text document.
 *
 * @property routineTitle Detected routine title or weekly routine name (e.g. "Mondays", "Monday: Strength & Accessories").
 * @property isRepeatable Whether the routine was marked as repeatable / recurring template.
 * @property sections List of macro-block section names extracted in order.
 * @property blocks List of structured [ParsedDocumentBlock]s.
 * @property rawText Original text input.
 */
data class ParsedWorkoutDocument(
    val routineTitle: String = "",
    val isRepeatable: Boolean = false,
    val sections: List<String> = emptyList(),
    val blocks: List<ParsedDocumentBlock> = emptyList(),
    val rawText: String = ""
)

/**
 * Pure Kotlin, deterministic, zero-latency document parser for workout notes and markdown.
 * Runs locally on [kotlinx.coroutines.Dispatchers.Default] (< 15ms) with zero Android platform dependencies.
 *
 * Features:
 * - Digit-bounded lookaround regex `(?<=\d),(?=\d)` for European decimal comma normalization without corrupting notes/lists.
 * - Extraction of routine titles, repeatable tags, and macro-block section headers (`... block`).
 * - Detection of barbell complexes (`Clean + Hang Clean...`), trisets / supersets (`E3MOM Trisets`, `E2,5MOM Trisets`).
 * - Attribution of triset cluster schemes (`scheme = "TRISET_N"`) and shared format (`E3MOM`).
 * - Shorthand set grammar: `0(4)`, `0(5 reps)`, `60(1 rep)`, `60(fail)`, `not_done`.
 * - Round-count inheritance for unnumbered accessories from anchor movements in interval trisets.
 */
object WorkoutDocumentParser {

    // Strict digit-bounded lookaround regex: replaces comma with dot ONLY between digits
    private val DECIMAL_COMMA_REGEX = Regex("""(?<=\d),(?=\d)""")

    // Shorthand set token regex: matches weight and optional parenthesized annotation
    // e.g. "57.5", "60", "60(1 rep)", "60(fail)", "0(4)", "0(5 reps)", "100(3)", "60 (fail)"
    private val SET_TOKEN_REGEX = Regex(
        """^(\d+(?:\.\d+)?)\s*(?:\((?:(\d+)\s*(?:reps?|rep)?|fail|fallo)\))?$""",
        RegexOption.IGNORE_CASE
    )

    // Format regex matching EMOM, EXMOM, EX.YMOM, AMRAP, FOR TIME, TABATA, REST
    private val FORMAT_REGEX = Regex(
        """\b(E\d+(?:\.\d+)?MOM|EMOM(?:\s+\d+)?|AMRAP(?:\s+\d+)?|FOR TIME|FT|TABATA|REST(?:\s+\d+(?:\s*min|\s*sec|\s*s)?)?)\b""",
        RegexOption.IGNORE_CASE
    )

    // Section header regex: lines ending with or containing "block" or markdown headers
    private val SECTION_HEADER_REGEX = Regex(
        """^(?:#{1,6}\s*)?([^\n:]*?\bblock\b[^\n:]*):?$""",
        RegexOption.IGNORE_CASE
    )

    // Repeatable routine pattern: e.g. "Mondays --- repeateble", "Monday -- repeatable", "Tuesdays - repeatable"
    private val REPEATABLE_ROUTINE_REGEX = Regex(
        """^(?:#{1,6}\s*)?([A-Za-z0-9\s]+?)\s*[-—–]{1,3}\s*repeat[ae]ble.*$""",
        RegexOption.IGNORE_CASE
    )

    // Title header pattern: markdown # Title
    private val TITLE_HEADER_REGEX = Regex("""^#\s+([^\n]+)$""")

    // Complex indicator regex
    private val COMPLEX_HEADER_REGEX = Regex("""\bCOMPLEX\b""", RegexOption.IGNORE_CASE)

    // Triset / Superset indicator regex
    private val TRISET_HEADER_REGEX = Regex("""\b(?:TRISETS?|SUPERSETS?|BISETS?)\b""", RegexOption.IGNORE_CASE)

    // Target reps extraction in block line: e.g. "4 Front Squats", "x15", "x 15", "15 reps", "10 Cal"
    private val TARGET_REPS_PREFIX_REGEX = Regex("""\b(\d+)\s+([A-Za-z]+.*)""")
    private val TARGET_REPS_SUFFIX_REGEX = Regex("""(?:x\s*(\d+)|\b(\d+)\s*reps?\b)""", RegexOption.IGNORE_CASE)
    private val CALORIE_METRIC_REGEX = Regex("""\b(\d+)\s*Cal(?:ories)?\b""", RegexOption.IGNORE_CASE)

    /**
     * Normalizes European decimal commas using strict digit-bounded lookaround regex `(?<=\d),(?=\d)`.
     * Preserves textual commas in notes, lists, and prose.
     */
    fun normalizeDecimalCommas(text: String): String {
        return text.replace(DECIMAL_COMMA_REGEX, ".")
    }

    /**
     * Parses raw workout markdown or free-text document into structured [ParsedWorkoutDocument].
     */
    fun parseDocument(rawText: String): ParsedWorkoutDocument {
        if (rawText.isBlank()) return ParsedWorkoutDocument()

        val normalizedText = normalizeDecimalCommas(rawText)
        val lines = normalizedText.lines()

        var detectedTitle = ""
        var isRepeatable = false
        val sections = mutableListOf<String>()
        val parsedBlocks = mutableListOf<ParsedDocumentBlock>()

        var currentSection = ""
        var trisetClusterCounter = 0

        var lineIndex = 0
        while (lineIndex < lines.size) {
            val rawLine = lines[lineIndex].trim()
            lineIndex++

            if (rawLine.isBlank()) continue

            // 1. Check for repeatable routine header (e.g. "Mondays --- repeateble")
            val repMatch = REPEATABLE_ROUTINE_REGEX.matchEntire(rawLine)
            if (repMatch != null) {
                val titlePart = repMatch.groupValues[1].trim()
                if (detectedTitle.isBlank()) {
                    detectedTitle = titlePart
                }
                isRepeatable = true
                continue
            }

            // 2. Check for markdown title # Title
            val titleMatch = TITLE_HEADER_REGEX.matchEntire(rawLine)
            if (titleMatch != null && detectedTitle.isBlank()) {
                detectedTitle = titleMatch.groupValues[1].trim()
                continue
            }

            // 3. Check for section header (e.g. "Strengh & Power block", "Accessories block")
            val sectionMatch = SECTION_HEADER_REGEX.matchEntire(rawLine)
            if (sectionMatch != null) {
                currentSection = sectionMatch.groupValues[1].trim()
                    .replace(Regex("""^#+\s*"""), "")
                    .trim()
                if (currentSection.isNotBlank() && !sections.contains(currentSection)) {
                    sections.add(currentSection)
                }
                continue
            }

            // 4. If line contains Triset / Superset cluster definition (e.g. "E3MOM Trisets", "E2.5MOM Trisets")
            if (TRISET_HEADER_REGEX.containsMatchIn(rawLine)) {
                trisetClusterCounter++
                val clusterScheme = "TRISET_$trisetClusterCounter"
                val formatMatch = FORMAT_REGEX.find(rawLine)
                val format = formatMatch?.value?.trim() ?: "E3MOM"

                // Collect cluster exercises
                val clusterItems = mutableListOf<String>()
                while (lineIndex < lines.size) {
                    val nextLine = lines[lineIndex].trim()
                    if (nextLine.isBlank()) {
                        lineIndex++
                        continue
                    }
                    // Break if another section, triset, or title starts
                    if (SECTION_HEADER_REGEX.matches(nextLine) ||
                        REPEATABLE_ROUTINE_REGEX.matches(nextLine) ||
                        TRISET_HEADER_REGEX.containsMatchIn(nextLine) ||
                        TITLE_HEADER_REGEX.matches(nextLine)
                    ) {
                        break
                    }
                    clusterItems.add(nextLine)
                    lineIndex++
                }

                val parsedClusterBlocks = parseTrisetCluster(
                    clusterItems = clusterItems,
                    format = format,
                    scheme = clusterScheme,
                    section = currentSection
                )
                parsedBlocks.addAll(parsedClusterBlocks)
                continue
            }

            // 5. Standard or Complex Block line
            // Check if next line contains sets or if this line contains name & sets
            val blockLine = rawLine
            val nextLine = if (lineIndex < lines.size) lines[lineIndex].trim() else ""

            val hasSetsOnNextLine = nextLine.isNotBlank() &&
                    !SECTION_HEADER_REGEX.matches(nextLine) &&
                    !TRISET_HEADER_REGEX.containsMatchIn(nextLine) &&
                    !REPEATABLE_ROUTINE_REGEX.matches(nextLine) &&
                    isProbableSetsLine(nextLine)

            val setsLineToUse: String
            if (hasSetsOnNextLine) {
                setsLineToUse = nextLine
                lineIndex++ // consume sets line
            } else {
                setsLineToUse = ""
            }

            val parsedBlock = parseSingleBlock(
                blockLine = blockLine,
                setsLine = setsLineToUse,
                section = currentSection
            )
            parsedBlocks.add(parsedBlock)
        }

        // Refine routine title if empty or synthesize from first section
        if (detectedTitle.isBlank() && sections.isNotEmpty()) {
            detectedTitle = "Workout: " + sections.joinToString(" & ")
        }

        return ParsedWorkoutDocument(
            routineTitle = detectedTitle,
            isRepeatable = isRepeatable,
            sections = sections,
            blocks = parsedBlocks,
            rawText = rawText
        )
    }

    /**
     * Parses a Triset/Superset cluster into individual [ParsedDocumentBlock] items,
     * resolving accessory round counts by inheriting from anchor movements in the triset.
     */
    private fun parseTrisetCluster(
        clusterItems: List<String>,
        format: String,
        scheme: String,
        section: String
    ): List<ParsedDocumentBlock> {
        val rawItemPairs = mutableListOf<Pair<String, String>>() // (header, setsLine)
        var i = 0
        while (i < clusterItems.size) {
            val item = clusterItems[i]
            val nextItem = if (i + 1 < clusterItems.size) clusterItems[i + 1] else ""
            if (nextItem.isNotBlank() && isProbableSetsLine(nextItem)) {
                rawItemPairs.add(Pair(item, nextItem))
                i += 2
            } else {
                rawItemPairs.add(Pair(item, ""))
                i += 1
            }
        }

        // First pass: parse each movement
        val temporaryBlocks = rawItemPairs.map { (header, setsLine) ->
            parseSingleBlock(
                blockLine = header,
                setsLine = setsLine,
                section = section,
                defaultKind = BlockKind.SUPERSET,
                defaultFormat = format,
                defaultScheme = scheme
            )
        }

        // Find anchor movement with highest non-empty set count (e.g. Romanian Deadlift with 4 sets)
        val anchorSetCount = temporaryBlocks.map { it.sets.size }.maxOrNull()?.coerceAtLeast(1) ?: 1

        // Second pass: inherit set counts for unnumbered accessories
        val finalBlocks = temporaryBlocks.map { block ->
            if (block.sets.isEmpty()) {
                val inheritedReps = block.targetReps ?: 1
                val metricVal = block.targetReps?.toDouble()
                val inheritedSets = (0 until anchorSetCount).map {
                    ParsedDocumentSet(
                        reps = inheritedReps,
                        weight = null,
                        metricValue = metricVal,
                        notes = if (block.rawText.contains("not needed", ignoreCase = true)) "Tracking not needed" else ""
                    )
                }
                block.copy(
                    scheme = scheme,
                    format = format,
                    kind = BlockKind.SUPERSET,
                    sets = inheritedSets
                )
            } else {
                block.copy(
                    scheme = scheme,
                    format = format,
                    kind = BlockKind.SUPERSET
                )
            }
        }

        return finalBlocks
    }

    /**
     * Parses an individual block header line and sets line.
     */
    fun parseSingleBlock(
        blockLine: String,
        setsLine: String = "",
        section: String = "",
        defaultKind: BlockKind? = null,
        defaultFormat: String = "",
        defaultScheme: String = ""
    ): ParsedDocumentBlock {
        val cleanLine = blockLine.replace(Regex("""^[-*+]\s*"""), "").trim()

        // Extract format (e.g. E3MOM, E2.5MOM)
        val formatMatch = FORMAT_REGEX.find(cleanLine)
        val format = formatMatch?.value?.trim() ?: defaultFormat

        var textWithoutFormat = if (formatMatch != null) {
            cleanLine.removeRange(formatMatch.range).trim()
        } else {
            cleanLine
        }

        // Check if complex
        val isComplex = COMPLEX_HEADER_REGEX.containsMatchIn(textWithoutFormat) ||
                textWithoutFormat.contains("+")
        textWithoutFormat = textWithoutFormat.replace(COMPLEX_HEADER_REGEX, "").trim()
        textWithoutFormat = textWithoutFormat.replace(Regex("""^:\s*"""), "").trim()

        // Check for calorie metric first (e.g. "10 Cal SkiErg")
        var targetReps: Int? = null
        val calMatch = CALORIE_METRIC_REGEX.find(textWithoutFormat)
        if (calMatch != null) {
            targetReps = calMatch.groupValues[1].toIntOrNull()
            textWithoutFormat = textWithoutFormat.removeRange(calMatch.range).trim()
        }

        // Check for target reps prefix (e.g. "4 Front Squats")
        if (targetReps == null) {
            val prefixRepMatch = TARGET_REPS_PREFIX_REGEX.matchEntire(textWithoutFormat)
            if (prefixRepMatch != null) {
                val repCandidate = prefixRepMatch.groupValues[1].toIntOrNull()
                val movementCandidate = prefixRepMatch.groupValues[2].trim()
                if (repCandidate != null && repCandidate <= 100) {
                    targetReps = repCandidate
                    textWithoutFormat = movementCandidate
                }
            }
        }

        // Check for target reps suffix (e.g. "Banded Reverse Flys x15", "Calves Raises x 15")
        val suffixRepMatch = TARGET_REPS_SUFFIX_REGEX.find(textWithoutFormat)
        if (suffixRepMatch != null) {
            val rVal = suffixRepMatch.groupValues[1].ifBlank { suffixRepMatch.groupValues[2] }.toIntOrNull()
            if (rVal != null) {
                targetReps = rVal
                textWithoutFormat = textWithoutFormat.removeRange(suffixRepMatch.range).trim()
            }
        }

        // Extract inline notes or sets if colon present
        var inlineSetsStr = setsLine
        if (inlineSetsStr.isBlank() && textWithoutFormat.contains(":")) {
            val parts = textWithoutFormat.split(":", limit = 2)
            textWithoutFormat = parts[0].trim()
            inlineSetsStr = parts[1].trim()
        }

        // Extract movement name and clean annotations like "(tracking not needed)"
        val cleanName = textWithoutFormat
            .replace(Regex("""\([^\)]*not needed[^\)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[,\s:–-]+|[-,\s:–]+$"""), "")
            .trim()

        // Components for complex
        val movements = if (cleanName.contains("+")) {
            cleanName.split("+").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf(cleanName)
        }

        val kind = when {
            defaultKind != null -> defaultKind
            isComplex -> BlockKind.COMPLEX
            format.contains("AMRAP", ignoreCase = true) || format.contains("FOR TIME", ignoreCase = true) -> BlockKind.METCON
            cleanName.contains("curl", ignoreCase = true) || cleanName.contains("raises", ignoreCase = true) || cleanName.contains("flys", ignoreCase = true) -> BlockKind.ACCESSORY
            else -> BlockKind.STRENGTH
        }

        // Parse sets
        val effectiveTargetReps = targetReps ?: 1
        val parsedSets = parseSetsString(inlineSetsStr, defaultTargetReps = effectiveTargetReps)

        val scheme = when {
            defaultScheme.isNotBlank() -> defaultScheme
            parsedSets.isNotEmpty() -> "${parsedSets.size}x$effectiveTargetReps"
            else -> ""
        }

        return ParsedDocumentBlock(
            name = cleanName,
            section = section,
            kind = kind,
            format = format,
            scheme = scheme,
            movements = movements,
            sets = parsedSets,
            targetReps = targetReps,
            rawText = blockLine
        )
    }

    /**
     * Parses space/comma separated set tokens into a list of [ParsedDocumentSet].
     * Handles:
     * - "52.5 55 57.5"
     * - "57.5 60 60(1 rep) 60(fail) not_done"
     * - "0(5 reps) 0(4) 0(4) 0(4)"
     * - "60(fail)" -> reps = 0, isFailed = true
     * - "60(1 rep)" (with defaultTargetReps = 4) -> reps = 1, notes = "Partial (target 4)"
     * - "not_done" -> skipped entirely
     */
    fun parseSetsString(setsString: String, defaultTargetReps: Int = 1): List<ParsedDocumentSet> {
        val normalized = normalizeDecimalCommas(setsString).trim()
        if (normalized.isBlank()) return emptyList()

        // Tokenize by whitespace, preserving parenthesized tokens like "60(1 rep)" or "60 (fail)"
        val rawTokens = tokenizeSetString(normalized)
        val result = mutableListOf<ParsedDocumentSet>()

        for (token in rawTokens) {
            val t = token.trim()
            if (t.isBlank()) continue

            // Check for skipped set
            if (t.equals("not_done", ignoreCase = true) || t.equals("skipped", ignoreCase = true)) {
                continue
            }

            val set = parseSingleSetToken(t, defaultTargetReps)
            if (set != null) {
                result.add(set)
            }
        }

        return result
    }

    /**
     * Tokenizes a set string into individual set tokens, handling optional spaces inside parentheses.
     * e.g. "57.5 60 60(1 rep) 60(fail) not_done" -> ["57.5", "60", "60(1 rep)", "60(fail)", "not_done"]
     */
    private fun tokenizeSetString(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inParen = false

        for (ch in input) {
            when (ch) {
                '(' -> {
                    inParen = true
                    current.append(ch)
                }
                ')' -> {
                    inParen = false
                    current.append(ch)
                }
                ' ', '\t', '\n', ',' -> {
                    if (inParen) {
                        current.append(ch)
                    } else {
                        if (current.isNotBlank()) {
                            tokens.add(current.toString().trim())
                            current.clear()
                        }
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) {
            tokens.add(current.toString().trim())
        }
        return tokens
    }

    /**
     * Parses an individual set token according to the grammar:
     * `(\d+(?:\.\d+)?)\s*(?:\((?:(\d+)\s*(?:reps?|rep)?|fail)\))?`
     */
    private fun parseSingleSetToken(token: String, defaultTargetReps: Int): ParsedDocumentSet? {
        val match = SET_TOKEN_REGEX.matchEntire(token)
        if (match != null) {
            val weightVal = match.groupValues[1].toDoubleOrNull() ?: return null
            val isExplicitFail = token.contains("fail", ignoreCase = true) || token.contains("fallo", ignoreCase = true)

            if (isExplicitFail) {
                return ParsedDocumentSet(
                    reps = 0,
                    weight = weightVal,
                    isFailed = true,
                    notes = "Failed attempt"
                )
            }

            val repStr = match.groupValues[2]
            if (repStr.isNotBlank()) {
                val repsVal = repStr.toIntOrNull() ?: defaultTargetReps
                val isPartial = repsVal < defaultTargetReps
                val notes = if (isPartial) "Partial (target $defaultTargetReps)" else ""
                return ParsedDocumentSet(
                    reps = repsVal,
                    weight = weightVal,
                    notes = notes
                )
            } else {
                return ParsedDocumentSet(
                    reps = defaultTargetReps,
                    weight = weightVal
                )
            }
        }

        // Check if token is just a bare weight number e.g. "52.5"
        val weightOnly = token.toDoubleOrNull()
        if (weightOnly != null) {
            return ParsedDocumentSet(
                reps = defaultTargetReps,
                weight = weightOnly
            )
        }

        return null
    }

    /**
     * Helper to detect if a line looks like set loads or tokens rather than movement headers.
     */
    private fun isProbableSetsLine(line: String): Boolean {
        val tokens = line.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        val numericCount = tokens.count { token ->
            token.matches(Regex("""^[\d\.,]+(?:\([^\)]+\))?$""")) ||
                    token.equals("not_done", ignoreCase = true)
        }
        return numericCount > 0 && (numericCount.toDouble() / tokens.size) >= 0.5
    }
}
