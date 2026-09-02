package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.model.BlockKind
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Result of AI-driven workout parsing and extraction.
 *
 * @property blocks The list of extracted [ParsedBlock]s.
 * @property confidence Overall extraction confidence score normalized between 0.0 and 1.0.
 * @property errors List of warning or error messages encountered during parsing, fallback, or extraction.
 */
data class WorkoutParseResult(
    val blocks: List<ParsedBlock> = emptyList(),
    val confidence: Float = 1.0f,
    val errors: List<String> = emptyList()
)

/**
 * Represents a structured workout block extracted from natural language speech or text.
 *
 * @property name Exercise or workout block name (e.g. "Back Squat", "Hang Power Snatch", "Fran").
 * @property kind Classification matching [BlockKind] (e.g. STRENGTH, METCON, COMPLEX, WARMUP).
 * @property format Workout timing format (e.g. "E2MOM", "EMOM 12", "AMRAP 20", "For Time").
 * @property repScheme Repetition and set pattern (e.g. "5x3", "21-15-9", "4 sets of 1").
 * @property sets Individual structured sets extracted or synthesized for this block.
 * @property movements Component movements for complexes or multi-exercise workouts/WODs.
 * @property description Full or raw description of the block.
 * @property rpe Optional Rate of Perceived Exertion score (e.g. 8.0f).
 */
data class ParsedBlock(
    val name: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val repScheme: String = "",
    val sets: List<ParsedBlockSet> = emptyList(),
    val movements: List<String> = emptyList(),
    val description: String = "",
    val rpe: Float? = null
)

/**
 * Individual set details within a [ParsedBlock].
 *
 * @property reps Number of repetitions in this set.
 * @property weight Load in kilograms (or pounds) if specified.
 * @property isWarmup Whether this is marked as a warmup set.
 * @property rpe Optional RPE rating for this specific set.
 * @property metricValue Non-weight metric value (e.g. calories, meters, seconds).
 */
data class ParsedBlockSet(
    val reps: Int = 1,
    val weight: Double? = null,
    val isWarmup: Boolean = false,
    val rpe: Float? = null,
    val metricValue: Double? = null
)

/**
 * Interface abstraction for on-device Gemini Nano inference (AICore / Google AI Edge).
 * Decouples [AiCoreManager] from platform hardware and enables deterministic mocking in unit tests.
 */
interface GeminiNanoClient {
    /**
     * Checks if Gemini Nano / AICore is downloaded, available, and ready for inference on this device.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Executes text inference with Gemini Nano given a prompt.
     */
    suspend fun generateText(prompt: String): String
}

/**
 * Default fallback client when on-device Gemini Nano / AICore is unavailable.
 */
object UnavailableGeminiNanoClient : GeminiNanoClient {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun generateText(prompt: String): String =
        throw IllegalStateException("Gemini Nano / AICore is not available on this device")
}

/**
 * Manager orchestrating on-device Gemini Nano AI inference for structured JSON workout extraction
 * from natural language dictations and text.
 *
 * Key Capabilities:
 * - Deterministic, testable interface with [GeminiNanoClient] dependency injection.
 * - Prompt engineering strictly commanding JSON output for BlockKind, format, rep-scheme, and weights.
 * - Resilient JSON parser capable of accepting malformed, partial, or markdown-wrapped LLM outputs.
 * - Graceful fallback when Gemini Nano / AICore is unavailable or errors out.
 * - High-speed processing (< 2000ms latency requirement).
 */
class AiCoreManager(
    val client: GeminiNanoClient = UnavailableGeminiNanoClient,
    private val lexicon: FitnessSpeechLexicon = FitnessSpeechLexicon.DEFAULT,
    private val grounder: ExerciseEntityGrounder = ExerciseEntityGrounder.DEFAULT
) {

    /**
     * Parses spoken or typed workout text into structured [WorkoutParseResult].
     *
     * @param text Raw or transcribed workout text.
     * @return [WorkoutParseResult] containing structured blocks, confidence score, and any warning/error notes.
     */
    suspend fun parseWorkoutText(text: String): WorkoutParseResult {
        if (text.isBlank()) {
            return WorkoutParseResult(
                blocks = emptyList(),
                confidence = 0.0f,
                errors = listOf("Input text is blank")
            )
        }

        // 1. Check client availability
        if (!client.isAvailable()) {
            return WorkoutParseResult(
                blocks = emptyList(),
                confidence = 0.0f,
                errors = listOf("Gemini Nano / AICore is not available on this device")
            )
        }

        // 2. Preprocess transcript with phonetic lexicon
        val sanitizedText = lexicon.correct(text)

        // 3. Build optimized prompt
        val prompt = buildPrompt(sanitizedText)

        // 4. Execute on-device LLM generation
        val rawResponse = try {
            client.generateText(prompt)
        } catch (e: Exception) {
            return WorkoutParseResult(
                blocks = emptyList(),
                confidence = 0.0f,
                errors = listOf("Gemini Nano inference failed: ${e.message ?: e::class.simpleName}")
            )
        }

        // 5. Resilient JSON extraction and parsing
        return parseJsonResponse(rawResponse, sanitizedText)
    }

    /**
     * Builds structured prompt instructing Gemini Nano to extract workout schema in JSON format.
     */
    fun buildPrompt(transcript: String): String {
        val blockKinds = BlockKind.values().joinToString(", ") { it.name }

        return """
You are an on-device AI for an athletic workout logger. Extract structured workout blocks from the speech transcript.

Valid BlockKinds:
$blockKinds

Output Schema:
Respond ONLY with a JSON object in this exact schema:
{
  "blocks": [
    {
      "name": "Exercise Name",
      "kind": "STRENGTH",
      "format": "E2MOM",
      "repScheme": "4x1",
      "movements": ["Movement 1", "Movement 2"],
      "description": "Optional description",
      "rpe": 8.0,
      "sets": [
        {
          "reps": 1,
          "weight": 60.0,
          "isWarmup": false,
          "rpe": 8.0,
          "metricValue": null
        }
      ]
    }
  ]
}

Instructions:
1. Map format to standard acronyms: E2MOM, E3MOM, EMOM, AMRAP, FOR TIME, TABATA, REST.
2. For barbell complexes, set kind to STRENGTH or COMPLEX and populate movements array.
3. For WODs / Metcons, set kind to METCON and populate format and movements.
4. Extract load in kilograms (kg) into weight field, and RPE (1-10) if present.
5. If reps are in wave format (e.g. 3-2-1-3-2-1), create matching sets with corresponding weights.

Transcript:
$transcript
""".trimIndent()
    }

    /**
     * Resiliently parses JSON from LLM output, recovering from markdown wrappers, trailing commas,
     * unquoted keys, single quotes, or partial malformed syntax.
     */
    fun parseJsonResponse(rawText: String, fallbackDescription: String = ""): WorkoutParseResult {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return WorkoutParseResult(
                blocks = emptyList(),
                confidence = 0.0f,
                errors = listOf("Empty response from AI model")
            )
        }

        val errors = mutableListOf<String>()
        val confidence = 1.0f

        // 1. Extract JSON payload from code fences or outermost brackets
        val jsonPayload = extractJsonPayload(trimmed)
        if (jsonPayload.isEmpty()) {
            // Attempt regex-based emergency field recovery
            val recoveredBlocks = recoverBlocksWithRegex(trimmed, fallbackDescription)
            return if (recoveredBlocks.isNotEmpty()) {
                WorkoutParseResult(
                    blocks = recoveredBlocks,
                    confidence = 0.60f,
                    errors = listOf("Recovered blocks via emergency regex extraction from non-JSON output")
                )
            } else {
                WorkoutParseResult(
                    blocks = emptyList(),
                    confidence = 0.0f,
                    errors = listOf("Unable to extract valid JSON from AI response: $trimmed")
                )
            }
        }

        // 2. Normalize and sanitize JSON quirks (single quotes, trailing commas, unquoted keys)
        val sanitizedJson = sanitizeJson(jsonPayload)

        // 3. Parse JSON structures
        try {
            val rootNode = parseDynamicJson(sanitizedJson)
            val rawBlocksNode = when {
                rootNode is Map<*, *> && rootNode.containsKey("blocks") -> rootNode["blocks"]
                rootNode is List<*> -> rootNode
                rootNode is Map<*, *> && (rootNode.containsKey("name") || rootNode.containsKey("exerciseName") || rootNode.containsKey("title")) -> listOf(rootNode)
                else -> emptyList<Any>()
            }

            val blocksList = (rawBlocksNode as? List<*>) ?: emptyList<Any>()
            if (blocksList.isEmpty()) {
                // Fallback check if single object was returned
                if (rootNode is Map<*, *> && rootNode.containsKey("name")) {
                    val singleBlock = parseBlockFromMap(rootNode, fallbackDescription)
                    return WorkoutParseResult(
                        blocks = listOf(singleBlock),
                        confidence = 0.95f,
                        errors = errors
                    )
                }
                return WorkoutParseResult(
                    blocks = emptyList(),
                    confidence = 0.5f,
                    errors = listOf("No workout blocks found in JSON payload")
                )
            }

            val parsedBlocks = mutableListOf<ParsedBlock>()
            for (item in blocksList) {
                if (item is Map<*, *>) {
                    parsedBlocks.add(parseBlockFromMap(item, fallbackDescription))
                }
            }

            if (parsedBlocks.isEmpty()) {
                val recovered = recoverBlocksWithRegex(jsonPayload, fallbackDescription)
                if (recovered.isNotEmpty()) {
                    return WorkoutParseResult(
                        blocks = recovered,
                        confidence = 0.65f,
                        errors = listOf("Parsed blocks with fallback recovery")
                    )
                }
            }

            return WorkoutParseResult(
                blocks = parsedBlocks,
                confidence = confidence,
                errors = errors
            )
        } catch (e: Exception) {
            errors.add("JSON parsing error: ${e.message ?: "malformed structure"}")
            // Fallback to regex token extraction
            val recovered = recoverBlocksWithRegex(jsonPayload, fallbackDescription)
            return if (recovered.isNotEmpty()) {
                WorkoutParseResult(
                    blocks = recovered,
                    confidence = 0.70f,
                    errors = errors
                )
            } else {
                WorkoutParseResult(
                    blocks = emptyList(),
                    confidence = 0.0f,
                    errors = errors
                )
            }
        }
    }

    private fun parseBlockFromMap(map: Map<*, *>, fallbackDescription: String): ParsedBlock {
        val nameRaw = (map["name"] ?: map["exerciseName"] ?: map["title"] ?: "").toString().trim()
        val kindStr = (map["kind"] ?: map["blockKind"] ?: map["type"] ?: "STRENGTH").toString().trim()
        val formatStr = (map["format"] ?: map["timing"] ?: "").toString().trim()
        val repSchemeStr = (map["repScheme"] ?: map["scheme"] ?: map["repsScheme"] ?: "").toString().trim()
        val descStr = (map["description"] ?: fallbackDescription).toString().trim()

        val rpeVal = map["rpe"]?.let { parseNumberAsFloat(it) }

        val movementsList = when (val mov = map["movements"]) {
            is List<*> -> mov.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
            is String -> mov.split(",", "+").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }

        // Parse sets array
        val setsList = mutableListOf<ParsedBlockSet>()
        val setsNode = map["sets"]
        if (setsNode is List<*>) {
            for (s in setsNode) {
                if (s is Map<*, *>) {
                    val reps = parseNumberAsInt(s["reps"] ?: s["repCount"], default = 1)
                    val weight = s["weight"]?.let { parseNumberAsDouble(it) }
                    val isWarmup = (s["isWarmup"] as? Boolean)
                        ?: (s["isWarmup"]?.toString()?.toBooleanStrictOrNull() ?: false)
                    val setRpe = s["rpe"]?.let { parseNumberAsFloat(it) } ?: rpeVal
                    val metricValue = s["metricValue"]?.let { parseNumberAsDouble(it) }
                    setsList.add(
                        ParsedBlockSet(
                            reps = reps,
                            weight = weight,
                            isWarmup = isWarmup,
                            rpe = setRpe,
                            metricValue = metricValue
                        )
                    )
                }
            }
        }

        // If setsList is empty, synthesize sets from repScheme / weight / count
        val finalSets = if (setsList.isEmpty()) {
            synthesizeSets(repSchemeStr, map["weight"], rpeVal)
        } else setsList

        val kind = parseBlockKind(kindStr, formatStr, nameRaw)

        return ParsedBlock(
            name = nameRaw.ifBlank { if (movementsList.isNotEmpty()) movementsList.joinToString(" + ") else "Workout Block" },
            kind = kind,
            format = formatStr,
            repScheme = repSchemeStr,
            sets = finalSets,
            movements = movementsList,
            description = descStr,
            rpe = rpeVal
        )
    }

    private fun synthesizeSets(repScheme: String, rawWeight: Any?, defaultRpe: Float?): List<ParsedBlockSet> {
        val weight = rawWeight?.let { parseNumberAsDouble(it) }
        val setsxRepsRegex = Regex("""(\d+)\s*[xX*]\s*(\d+)""")
        val match = setsxRepsRegex.find(repScheme)
        if (match != null) {
            val numSets = match.groupValues[1].toIntOrNull() ?: 1
            val numReps = match.groupValues[2].toIntOrNull() ?: 1
            return List(numSets) {
                ParsedBlockSet(reps = numReps, weight = weight, rpe = defaultRpe)
            }
        }

        val waveParts = repScheme.split("-", ",").mapNotNull { it.trim().toIntOrNull() }
        if (waveParts.size > 1) {
            return waveParts.map { reps ->
                ParsedBlockSet(reps = reps, weight = weight, rpe = defaultRpe)
            }
        }

        val setsOfRepsRegex = Regex("""(?i)(\d+)\s*sets(?:\s*of\s*(\d+))?""")
        val setsOfMatch = setsOfRepsRegex.find(repScheme)
        if (setsOfMatch != null) {
            val numSets = setsOfMatch.groupValues[1].toIntOrNull() ?: 1
            val numReps = setsOfMatch.groupValues[2].toIntOrNull() ?: 1
            return List(numSets) {
                ParsedBlockSet(reps = numReps, weight = weight, rpe = defaultRpe)
            }
        }

        return listOf(ParsedBlockSet(reps = 1, weight = weight, rpe = defaultRpe))
    }

    private fun parseBlockKind(kindStr: String, format: String, name: String): BlockKind {
        val norm = kindStr.uppercase().replace(" ", "_").replace("-", "_").replace("/", "_")
        for (k in BlockKind.values()) {
            if (k.name == norm) return k
        }

        return when {
            norm.contains("WARM") -> BlockKind.WARMUP
            norm.contains("COMPLEX") -> BlockKind.COMPLEX
            norm.contains("SUPER") -> BlockKind.SUPERSET
            norm.contains("CARDIO") || norm.contains("RUN") || norm.contains("ROW") || norm.contains("BIKE") -> BlockKind.CARDIO
            norm.contains("METCON") || norm.contains("WOD") || norm.contains("AMRAP") || format.contains("AMRAP", ignoreCase = true) -> BlockKind.METCON
            norm.contains("ACCESSORY") || norm.contains("HYPERTROPHY") -> BlockKind.ACCESSORY
            norm.contains("WEIGHTLIFT") || norm.contains("OLYMPIC") -> BlockKind.WEIGHTLIFTING
            else -> BlockKind.STRENGTH
        }
    }

    private fun parseNumberAsInt(value: Any?, default: Int = 1): Int {
        if (value == null) return default
        return when (value) {
            is Number -> value.toInt()
            is String -> {
                val cleaned = value.replace(Regex("""[^\d]"""), "")
                cleaned.toIntOrNull() ?: default
            }
            else -> default
        }
    }

    private fun parseNumberAsDouble(value: Any?): Double? {
        if (value == null) return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> {
                val cleaned = value.replace(",", ".").replace(Regex("""[^\d.]"""), "")
                cleaned.toDoubleOrNull()
            }
            else -> null
        }
    }

    private fun parseNumberAsFloat(value: Any?): Float? {
        return parseNumberAsDouble(value)?.toFloat()
    }

    private fun extractJsonPayload(input: String): String {
        // Check for markdown code fence ```json ... ``` or ``` ... ```
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(input)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Outermost JSON object or array
        val firstBrace = input.indexOf('{')
        val firstBracket = input.indexOf('[')

        val startIndex = when {
            firstBrace != -1 && firstBracket != -1 -> min(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            firstBracket != -1 -> firstBracket
            else -> -1
        }

        if (startIndex == -1) return ""

        val isObject = input[startIndex] == '{'
        val lastIndex = if (isObject) input.lastIndexOf('}') else input.lastIndexOf(']')

        return if (lastIndex > startIndex) {
            input.substring(startIndex, lastIndex + 1).trim()
        } else {
            // Truncated JSON without closing bracket
            input.substring(startIndex).trim()
        }
    }

    private fun sanitizeJson(input: String): String {
        var res = input.trim()

        // Replace single quotes around keys or strings with double quotes
        res = res.replace(Regex("""'([^'\\]*(?:\\.[^'\\]*)*)'"""), "\"$1\"")

        // Quote unquoted keys (e.g. { name: "val" } -> { "name": "val" })
        res = res.replace(Regex("""([{,]\s*)([a-zA-Z_][a-zA-Z0-9_]*)\s*:"""), "$1\"$2\":")

        // Remove trailing commas before } or ]
        res = res.replace(Regex(""",\s*([\]\}])"""), "$1")

        // Balance unclosed braces or brackets
        val openBraces = res.count { it == '{' } - res.count { it == '}' }
        val openBrackets = res.count { it == '[' } - res.count { it == ']' }

        if (openBrackets > 0) {
            res += "]".repeat(openBrackets)
        }
        if (openBraces > 0) {
            res += "}".repeat(openBraces)
        }

        return res
    }

    private fun recoverBlocksWithRegex(raw: String, fallbackDescription: String): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()

        val nameRegex = Regex("""(?i)"?(?:name|exerciseName|title)"?\s*:\s*"([^"]+)"""")
        val kindRegex = Regex("""(?i)"?(?:kind|blockKind|type)"?\s*:\s*"([^"]+)"""")
        val formatRegex = Regex("""(?i)"?(?:format|timing)"?\s*:\s*"([^"]+)"""")
        val schemeRegex = Regex("""(?i)"?(?:repScheme|scheme)"?\s*:\s*"([^"]+)"""")
        val weightRegex = Regex("""(?i)"?weight"?\s*:\s*(\d+(?:\.\d+)?)""")
        val repsRegex = Regex("""(?i)"?reps"?\s*:\s*(\d+)""")
        val rpeRegex = Regex("""(?i)"?rpe"?\s*:\s*(\d+(?:\.\d+)?)""")

        val nameMatch = nameRegex.find(raw)
        val kindMatch = kindRegex.find(raw)
        val formatMatch = formatRegex.find(raw)
        val schemeMatch = schemeRegex.find(raw)
        val weightMatch = weightRegex.find(raw)
        val repsMatch = repsRegex.find(raw)
        val rpeMatch = rpeRegex.find(raw)

        val name = nameMatch?.groupValues?.get(1)?.trim() ?: ""
        val kindStr = kindMatch?.groupValues?.get(1)?.trim() ?: "STRENGTH"
        val format = formatMatch?.groupValues?.get(1)?.trim() ?: ""
        val scheme = schemeMatch?.groupValues?.get(1)?.trim() ?: ""
        val weight = weightMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val reps = repsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val rpe = rpeMatch?.groupValues?.get(1)?.toFloatOrNull()

        if (name.isNotBlank() || format.isNotBlank() || scheme.isNotBlank() || weight != null) {
            val kind = parseBlockKind(kindStr, format, name)
            val sets = synthesizeSets(scheme.ifBlank { "${reps} reps" }, weight, rpe)
            blocks.add(
                ParsedBlock(
                    name = name.ifBlank { "Workout Block" },
                    kind = kind,
                    format = format,
                    repScheme = scheme,
                    sets = sets,
                    description = fallbackDescription,
                    rpe = rpe
                )
            )
        }

        return blocks
    }

    /**
     * Lightweight pure-Kotlin JSON parser without external dependencies.
     */
    private fun parseDynamicJson(json: String): Any? {
        val tokens = tokenizeJson(json)
        val (result, _) = parseValue(tokens, 0)
        return result
    }

    private fun tokenizeJson(json: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val len = json.length

        while (i < len) {
            val c = json[i]
            when {
                c.isWhitespace() -> i++
                c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' -> {
                    tokens.add(c.toString())
                    i++
                }
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    var escaped = false
                    while (i < len) {
                        val ch = json[i]
                        if (escaped) {
                            when (ch) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\\' -> sb.append('\\')
                                '"' -> sb.append('"')
                                else -> sb.append(ch)
                            }
                            escaped = false
                        } else if (ch == '\\') {
                            escaped = true
                        } else if (ch == '"') {
                            i++
                            break
                        } else {
                            sb.append(ch)
                        }
                        i++
                    }
                    tokens.add("\"" + sb.toString() + "\"")
                }
                else -> {
                    val sb = StringBuilder()
                    while (i < len && !json[i].isWhitespace() && json[i] !in "{}[],:") {
                        sb.append(json[i])
                        i++
                    }
                    tokens.add(sb.toString())
                }
            }
        }

        return tokens
    }

    private fun parseValue(tokens: List<String>, start: Int): Pair<Any?, Int> {
        if (start >= tokens.size) return Pair(null, start)
        val token = tokens[start]

        return when {
            token == "{" -> parseObject(tokens, start)
            token == "[" -> parseArray(tokens, start)
            token.startsWith("\"") && token.endsWith("\"") && token.length >= 2 -> {
                Pair(token.substring(1, token.length - 1), start + 1)
            }
            token.equals("true", ignoreCase = true) -> Pair(true, start + 1)
            token.equals("false", ignoreCase = true) -> Pair(false, start + 1)
            token.equals("null", ignoreCase = true) -> Pair(null, start + 1)
            else -> {
                val intVal = token.toIntOrNull()
                if (intVal != null) {
                    Pair(intVal, start + 1)
                } else {
                    val doubleVal = token.toDoubleOrNull()
                    Pair(doubleVal ?: token, start + 1)
                }
            }
        }
    }

    private fun parseObject(tokens: List<String>, start: Int): Pair<Map<String, Any?>, Int> {
        val map = mutableMapOf<String, Any?>()
        var curr = start + 1 // skip '{'

        while (curr < tokens.size && tokens[curr] != "}") {
            if (tokens[curr] == ",") {
                curr++
                continue
            }
            val keyToken = tokens[curr]
            val key = if (keyToken.startsWith("\"") && keyToken.endsWith("\"") && keyToken.length >= 2) {
                keyToken.substring(1, keyToken.length - 1)
            } else keyToken

            curr++
            if (curr < tokens.size && tokens[curr] == ":") {
                curr++
            }
            val (value, nextPos) = parseValue(tokens, curr)
            map[key] = value
            curr = nextPos

            if (curr < tokens.size && tokens[curr] == ",") {
                curr++
            }
        }

        if (curr < tokens.size && tokens[curr] == "}") {
            curr++
        }
        return Pair(map, curr)
    }

    private fun parseArray(tokens: List<String>, start: Int): Pair<List<Any?>, Int> {
        val list = mutableListOf<Any?>()
        var curr = start + 1 // skip '['

        while (curr < tokens.size && tokens[curr] != "]") {
            if (tokens[curr] == ",") {
                curr++
                continue
            }
            val (value, nextPos) = parseValue(tokens, curr)
            list.add(value)
            curr = nextPos

            if (curr < tokens.size && tokens[curr] == ",") {
                curr++
            }
        }

        if (curr < tokens.size && tokens[curr] == "]") {
            curr++
        }
        return Pair(list, curr)
    }

    companion object {
        /**
         * Global default instance configured with fallback client.
         */
        val DEFAULT = AiCoreManager()
    }
}
