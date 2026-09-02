package com.fractanomics.crosstraining.data.ai

import java.util.Collections

/**
 * Phonetic dictionary and speech-to-text (STT) artifact normalizer for fitness terminology.
 * Corrects acoustic misrecognitions and transforms spoken fitness jargon (EMOM, AMRAP, RPE, WOD,
 * barbell complexes, movement acronyms, and units) into standardized canonical forms.
 *
 * Requirements:
 * - Deterministic, pure functions with zero platform (Android) dependencies.
 * - Immutable dictionary maps for canonical fitness jargon.
 * - Phonetic correction engine with phrase-level structure normalization.
 */
class FitnessSpeechLexicon(
    customMappings: Map<String, String> = emptyMap()
) {

    /**
     * Immutable dictionary of common fitness speech artifacts and their canonical replacements.
     */
    val canonicalDictionary: Map<String, String>

    private val sortedEntries: List<Map.Entry<String, String>>

    init {
        val base = LinkedHashMap<String, String>()
        base.putAll(DEFAULT_LEXICON)
        base.putAll(customMappings)
        canonicalDictionary = Collections.unmodifiableMap(base)
        sortedEntries = canonicalDictionary.entries.sortedByDescending { it.key.length }
    }

    /**
     * Looks up a raw spoken term or artifact and returns its canonical form if known.
     */
    fun lookup(term: String): String? {
        val normalized = term.trim().lowercase()
        return canonicalDictionary[normalized]
    }

    /**
     * Checks if the given text contains any known speech artifact or requires phonetic normalization.
     */
    fun containsArtifact(text: String): Boolean {
        if (text.isBlank()) return false
        val corrected = correct(text)
        return !corrected.equals(text, ignoreCase = false)
    }

    /**
     * Corrects speech-to-text artifacts, phonetic misrecognitions, and format regularizations
     * across the input transcript.
     */
    fun correct(input: String): String {
        if (input.isBlank()) return input

        var result = input

        // 1. Structure normalizations: Timed EMOM variants (e.g. "12 min a mom of 15 wall balls" -> "EMOM 12 of 15 wall balls")
        result = TIMED_EMOM_REGEX.replace(result) { match ->
            val minutes = match.groupValues[1]
            val trailing = match.groupValues[2].trim()
            if (trailing.isNotBlank()) "EMOM $minutes $trailing" else "EMOM $minutes"
        }

        // 1b. Trailing EMOM time specifications (e.g. "a mom 14 min" -> "EMOM 14", "emom 10" -> "EMOM 10")
        result = TRAILING_EMOM_REGEX.replace(result) { match ->
            val minutes = match.groupValues[1]
            "EMOM $minutes"
        }

        // 2. Structure normalizations: Timed AMRAP variants (e.g. "20 min am rap of 10 pull ups" -> "AMRAP 20 of 10 pull ups")
        result = TIMED_AMRAP_REGEX.replace(result) { match ->
            val minutes = match.groupValues[1]
            val trailing = match.groupValues[2].trim()
            if (trailing.isNotBlank()) "AMRAP $minutes $trailing" else "AMRAP $minutes"
        }

        // 2b. Trailing AMRAP time specifications (e.g. "am rap 20 min" -> "AMRAP 20", "amrap 15" -> "AMRAP 15")
        result = TRAILING_AMRAP_REGEX.replace(result) { match ->
            val minutes = match.groupValues[1]
            "AMRAP $minutes"
        }

        // 3. Every X minutes on the minute (EXMOM) phrases
        result = EXMOM_LONG_REGEX.replace(result) { match ->
            val numStr = match.groupValues[1].lowercase()
            when (numStr) {
                "", "1", "one" -> "EMOM"
                "2", "two" -> "E2MOM"
                "3", "three" -> "E3MOM"
                "4", "four" -> "E4MOM"
                "5", "five" -> "E5MOM"
                else -> "E${numStr}MOM"
            }
        }

        // 3b. Short EXMOM phonetic variants (e.g. "e 2 mom", "e-2-mom", "e four mom" -> "E2MOM", "E4MOM")
        result = EXMOM_SHORT_REGEX.replace(result) { match ->
            val interval = match.groupValues[1].lowercase()
            when (interval) {
                "2", "two" -> "E2MOM"
                "3", "three" -> "E3MOM"
                "4", "four" -> "E4MOM"
                "5", "five" -> "E5MOM"
                else -> "E${interval}MOM"
            }
        }

        // 4. Rate of Perceived Exertion (RPE) normalization (e.g. "r pay 8", "r p e 9", "rpm 9" -> "RPE 8", "RPE 9")
        result = RPE_VALUE_REGEX.replace(result) { match ->
            val score = match.groupValues[1]
            "RPE $score"
        }

        result = RPE_STANDALONE_REGEX.replace(result, "RPE")

        // 5. Dictionary-based phrase and token replacements (sorted longest-first for greedy matching)
        for ((artifact, canonical) in sortedEntries) {
            val pattern = Regex("(?i)\\b" + Regex.escape(artifact) + "\\b")
            result = pattern.replace(result, canonical)
        }

        // 6. Normalization of Barbell Complex syntax ("plus" -> " + ")
        result = COMPLEX_PLUS_REGEX.replace(result, " + ")

        // 7. Standalone unit normalization (e.g. "kilograms", "kilos", "kgs" -> "kg")
        result = UNITS_KG_REGEX.replace(result, "kg")
        result = UNITS_LBS_REGEX.replace(result, "lbs")

        // 8. Clean up any redundant whitespace
        result = MULTI_SPACE_REGEX.replace(result, " ").trim()

        return result
    }

    companion object {
        private val TIMED_EMOM_REGEX = Regex(
            """(?i)\b(\d+)\s*(?:min|mins|minute|minutes)\s+(?:a\s+mom|an\s+mom|e\s+mom|e-mom|ee\s+mom|emom|a\s+mum|imam|emam|e\s+mum)\b(?:\s+(.*))?"""
        )

        private val TRAILING_EMOM_REGEX = Regex(
            """(?i)\b(?:a\s+mom|an\s+mom|e\s+mom|e-mom|ee\s+mom|emom|a\s+mum|imam|emam|e\s+mum)\s+(\d+)(?:\s+(?:min|mins|minute|minutes))?\b"""
        )

        private val TIMED_AMRAP_REGEX = Regex(
            """(?i)\b(\d+)\s*(?:min|mins|minute|minutes)\s+(?:am\s*rap|am-rap|i'm\s*rap|um\s*rap|a\s+m\s+r\s+a\s+p|as\s+many\s+rounds\s+as\s+possible|as\s+many\s+reps\s+as\s+possible|amrap)\b(?:\s+(.*))?"""
        )

        private val TRAILING_AMRAP_REGEX = Regex(
            """(?i)\b(?:am\s*rap|am-rap|i'm\s*rap|um\s*rap|a\s+m\s+r\s+a\s+p|amrap)\s+(\d+)(?:\s+(?:min|mins|minute|minutes))?\b"""
        )

        private val EXMOM_LONG_REGEX = Regex(
            """(?i)\bevery\s+(?:(\d+|two|three|four|five)\s+)?(?:min|mins|minute|minutes)\s+on\s+the\s+(?:min|mins|minute|minutes)\b"""
        )

        private val EXMOM_SHORT_REGEX = Regex(
            """(?i)\be[-\s]*(2|two|3|three|4|four|5|five)[-\s]*mom\b"""
        )

        private val RPE_VALUE_REGEX = Regex(
            """(?i)\b(?:r\s+p\s+e|r-p-e|r\.p\.e\.|r\s+pay|rate\s+of\s+perceived\s+exertion|rpm)\s*(?:@|\bat\b)?\s*(\d+(?:\.\d+)?)\b"""
        )

        private val RPE_STANDALONE_REGEX = Regex(
            """(?i)\b(?:r\s+p\s+e|r-p-e|r\.p\.e\.|r\s+pay|rate\s+of\s+perceived\s+exertion)\b"""
        )

        private val COMPLEX_PLUS_REGEX = Regex("""(?i)\s+\bplus\b\s+""")

        private val UNITS_KG_REGEX = Regex("""(?i)\b(?:kilograms|kilogram|kilos|kilo|kgs)\b""")
        private val UNITS_LBS_REGEX = Regex("""(?i)\b(?:pounds|pound)\b""")
        private val MULTI_SPACE_REGEX = Regex("""[ \t]+""")

        private val DEFAULT_LEXICON: Map<String, String> = mapOf(
            // Format & Acronyms
            "a mom" to "EMOM",
            "an mom" to "EMOM",
            "e mom" to "EMOM",
            "e-mom" to "EMOM",
            "ee mom" to "EMOM",
            "e mum" to "EMOM",
            "a mum" to "EMOM",
            "imam" to "EMOM",
            "iman" to "EMOM",
            "emam" to "EMOM",
            "emom" to "EMOM",
            "am rap" to "AMRAP",
            "am-rap" to "AMRAP",
            "i'm rap" to "AMRAP",
            "um rap" to "AMRAP",
            "a m r a p" to "AMRAP",
            "amrap" to "AMRAP",
            "as many rounds as possible" to "AMRAP",
            "as many reps as possible" to "AMRAP",
            "as many reps or rounds as possible" to "AMRAP",
            "w o d" to "WOD",
            "w-o-d" to "WOD",
            "what of the day" to "WOD",
            "workout of the day" to "WOD",
            "wod" to "WOD",
            "met con" to "METCON",
            "met-con" to "METCON",
            "metabolic conditioning" to "METCON",
            "metcon" to "METCON",
            "r pay" to "RPE",
            "r p e" to "RPE",
            "r-p-e" to "RPE",
            "r.p.e." to "RPE",
            "rpe" to "RPE",

            // Olympic & Barbell Movements
            "c and j" to "C&J",
            "c & j" to "C&J",
            "c plus j" to "C&J",
            "o h s" to "OHS",

            // Gymnastics & Bodyweight
            "toast to bar" to "T2B",
            "toes 2 bar" to "T2B",
            "toes to bar" to "T2B",
            "t to b" to "T2B",
            "t 2 b" to "T2B",
            "chest 2 bar" to "C2B",
            "chest to bar" to "C2B",
            "c to b" to "C2B",
            "c 2 b" to "C2B",
            "b m u" to "BMU",
            "r m u" to "RMU",
            "h s p u" to "HSPU",
            "hand stand push up" to "HSPU",
            "h s w" to "HSW",
            "g h d" to "GHD",
            "double-unders" to "Double Unders",
            "dubs" to "Double Unders",

            // Equipment & Accessories
            "k b s" to "KB Swings",
            "b j o" to "BJO"
        )

        /**
         * Global default singleton instance.
         */
        val DEFAULT = FitnessSpeechLexicon()

        /**
         * Static utility method correcting speech-to-text artifacts using standard lexicon.
         */
        fun correct(input: String): String = DEFAULT.correct(input)
    }
}
