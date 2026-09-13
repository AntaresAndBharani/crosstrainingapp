package com.fractanomics.crosstraining.ui.timer

import com.fractanomics.crosstraining.util.WorkoutDocumentParser

/**
 * Pure Kotlin parser extracting [WorkoutTimerConfig] from functional fitness format tokens,
 * sub-block titles, and round parameters.
 *
 * Reuses the canonical [WorkoutDocumentParser.FORMAT_REGEX] and [WorkoutDocumentParser.DECIMAL_COMMA_REGEX]
 * to prevent parser divergence between document ingestion and timer launching.
 */
object WorkoutTimerConfigParser {

    private val EXMOM_REGEX = Regex("""^E(\d+(?:\.\d+)?)MOM$""", RegexOption.IGNORE_CASE)
    private val EMOM_REGEX = Regex("""^EMOM(?:\s+(\d+))?$""", RegexOption.IGNORE_CASE)
    private val AMRAP_REGEX = Regex("""^AMRAP(?:\s+(\d+))?$""", RegexOption.IGNORE_CASE)
    private val TABATA_REGEX = Regex("""^TABATA$""", RegexOption.IGNORE_CASE)
    private val FOR_TIME_REGEX = Regex("""^(?:FOR TIME|FT)(?:\s+(\d+))?$""", RegexOption.IGNORE_CASE)
    private val REST_REGEX = Regex("""^REST(?:\s+(\d+)\s*(?:s|sec|min)?)?$""", RegexOption.IGNORE_CASE)

    /**
     * Parses format text and context into a configured [WorkoutTimerConfig].
     *
     * @param formatString Raw format string or text containing timing token (e.g., "E3MOM", "E2,5MOM", "EMOM 10", "AMRAP 12").
     * @param roundCount Number of sets or rounds associated with the block, defaulting to 1.
     * @param workoutLabel Movement name or sub-block title to enrich domain state.
     * @param baseConfig Optional base configuration to inherit active user sound, vibration, and prep preferences.
     * @return [WorkoutTimerConfig] if a valid timing format token is matched, or `null` if untimed / unparseable.
     */
    fun parse(
        formatString: String?,
        roundCount: Int = 1,
        workoutLabel: String = "",
        baseConfig: WorkoutTimerConfig = WorkoutTimerConfig()
    ): WorkoutTimerConfig? {
        if (formatString.isNullOrBlank()) return null

        // 1. Normalize European decimal commas (e.g. E2,5MOM -> E2.5MOM) using canonical regex
        val normalized = formatString.replace(WorkoutDocumentParser.DECIMAL_COMMA_REGEX, ".").trim()

        // 2. Extract canonical format token using WorkoutDocumentParser.FORMAT_REGEX
        val match = WorkoutDocumentParser.FORMAT_REGEX.find(normalized) ?: return null
        val token = match.value.trim()

        val effectiveRounds = roundCount.coerceAtLeast(1)

        // 3. Match against supported functional fitness timer modalities
        val exmomMatch = EXMOM_REGEX.matchEntire(token)
        if (exmomMatch != null) {
            val minutes = exmomMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val intervalSecs = (minutes * 60.0).toInt()
            return baseConfig.copy(
                mode = TimerMode.EMOM,
                intervalSeconds = intervalSecs,
                totalRounds = effectiveRounds,
                workoutLabel = workoutLabel
            )
        }

        val emomMatch = EMOM_REGEX.matchEntire(token)
        if (emomMatch != null) {
            val roundsFromToken = emomMatch.groupValues[1].toIntOrNull()
            return baseConfig.copy(
                mode = TimerMode.EMOM,
                intervalSeconds = 60,
                totalRounds = roundsFromToken ?: effectiveRounds,
                workoutLabel = workoutLabel
            )
        }

        val amrapMatch = AMRAP_REGEX.matchEntire(token)
        if (amrapMatch != null) {
            val minutesFromToken = amrapMatch.groupValues[1].toIntOrNull() ?: 12
            return baseConfig.copy(
                mode = TimerMode.AMRAP,
                targetMinutes = minutesFromToken,
                totalRounds = 1,
                workoutLabel = workoutLabel
            )
        }

        if (TABATA_REGEX.matches(token)) {
            return baseConfig.copy(
                mode = TimerMode.TABATA,
                workSeconds = 20,
                restSeconds = 10,
                totalRounds = if (roundCount > 1) roundCount else 8,
                workoutLabel = workoutLabel
            )
        }

        val ftMatch = FOR_TIME_REGEX.matchEntire(token)
        if (ftMatch != null) {
            val capMinutes = ftMatch.groupValues[1].toIntOrNull() ?: 20
            return baseConfig.copy(
                mode = TimerMode.TIME_CAP,
                targetMinutes = capMinutes,
                totalRounds = 1,
                workoutLabel = workoutLabel
            )
        }

        val restMatch = REST_REGEX.matchEntire(token)
        if (restMatch != null) {
            val rawNum = restMatch.groupValues[1].toIntOrNull() ?: 60
            val isMinutes = token.contains("min", ignoreCase = true)
            val restSecs = if (isMinutes) rawNum * 60 else rawNum
            return baseConfig.copy(
                mode = TimerMode.REST,
                restSeconds = restSecs,
                totalRounds = 1,
                workoutLabel = workoutLabel
            )
        }

        return null
    }
}
