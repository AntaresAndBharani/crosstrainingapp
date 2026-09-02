package com.fractanomics.crosstraining.data.ai

import java.util.Collections
import kotlin.math.min

/**
 * Phonetic candidate match containing matched vocabulary term and edit distance.
 */
data class TermCandidate(
    val term: String,
    val distance: Int
)

/**
 * Phonetic dictionary and speech-to-text (STT) artifact normalizer for fitness terminology.
 * Corrects acoustic misrecognitions and transforms spoken fitness jargon (EMOM, AMRAP, RPE, WOD,
 * barbell complexes, movement acronyms, and units) into standardized canonical forms.
 *
 * Requirements:
 * - Deterministic, pure functions with zero platform (Android) dependencies.
 * - Immutable dictionary maps for canonical fitness jargon.
 * - Phonetic correction engine with phrase-level structure normalization.
 * - Levenshtein edit distance & candidate ranking over 500+ fitness vocabulary terms.
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

        // 7. Standalone unit normalization (e.g. "kilograms", "kilos", "kgs", "k" after numbers -> "kg")
        result = UNITS_KG_REGEX.replace(result, "kg")
        result = UNITS_LBS_REGEX.replace(result, "lbs")

        // 8. Clean up any redundant whitespace
        result = MULTI_SPACE_REGEX.replace(result, " ").trim()

        return result
    }

    /**
     * Ranks vocabulary candidate terms by phonetic edit distance ascending.
     */
    fun rankCandidates(query: String, topN: Int = 3): List<TermCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return CANONICAL_FITNESS_TERMS
            .map { term ->
                TermCandidate(
                    term = term,
                    distance = phoneticDistance(trimmed.lowercase(), term.lowercase())
                )
            }
            .sortedWith(
                compareBy<TermCandidate> { it.distance }
                    .thenBy { it.term.length }
                    .thenBy { it.term }
            )
            .take(topN)
    }

    /**
     * Finds the best matching vocabulary term within [maxDistance] threshold, or null if none match.
     */
    fun bestMatch(query: String, maxDistance: Int = 2): String? {
        val candidates = rankCandidates(query, topN = 1)
        val best = candidates.firstOrNull() ?: return null
        return if (best.distance <= maxDistance) best.term else null
    }

    companion object {
        /**
         * Calculates Levenshtein edit distance between two strings.
         */
        fun phoneticDistance(s1: String, s2: String): Int {
            if (s1 == s2) return 0
            if (s1.isEmpty()) return s2.length
            if (s2.isEmpty()) return s1.length

            val d = Array(s1.length + 1) { IntArray(s2.length + 1) }

            for (i in 0..s1.length) {
                d[i][0] = i
            }
            for (j in 0..s2.length) {
                d[0][j] = j
            }

            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    d[i][j] = min(
                        min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost
                    )
                }
            }

            return d[s1.length][s2.length]
        }

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

        private val UNITS_KG_REGEX = Regex("""(?i)\b(?:kilograms|kilogram|kilos|kilo|kgs)\b|(?<=\d\s*)(?:K|k)\b""")
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

            // Speech errors & Slang
            "wreps" to "reps",
            "squads" to "Squats",
            "squad" to "Squat",

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
         * Offline dictionary of 500+ standard canonical fitness terms for phonetic candidate ranking.
         */
        val CANONICAL_FITNESS_TERMS: List<String> = listOf(
            // Primary Movements
            "Squat", "Back Squat", "Front Squat", "Overhead Squat", "Air Squat", "Zercher Squat",
            "Box Squat", "Goblet Squat", "Pistol Squat", "Cossack Squat", "Split Squat",
            "Bulgarian Split Squat", "Safety Bar Squat", "Pin Squat", "Pause Squat", "Tempo Squat",
            "Hack Squat", "Sissy Squat", "Belt Squat", "Landmine Squat", "Jump Squat",
            "Deadlift", "Sumo Deadlift", "Romanian Deadlift", "Stiff Leg Deadlift", "Deficit Deadlift",
            "Block Deadlift", "Trap Bar Deadlift", "Snatch Grip Deadlift", "Halting Deadlift",
            "Single Leg Deadlift", "Good Morning", "Rack Pull", "Clean Pull", "Snatch Pull",
            "Snatch", "Power Snatch", "Squat Snatch", "Hang Snatch", "Hang Power Snatch",
            "Hang Squat Snatch", "Muscle Snatch", "Split Snatch", "Dumbbell Snatch", "Kettlebell Snatch",
            "Clean", "Power Clean", "Squat Clean", "Hang Clean", "Hang Power Clean",
            "Hang Squat Clean", "Muscle Clean", "Dumbbell Clean", "Kettlebell Clean", "Sandbag Clean",
            "Clean & Jerk", "Jerk", "Split Jerk", "Push Jerk", "Power Jerk", "Squat Jerk",
            "Behind the Neck Jerk", "Behind the Neck Split Jerk", "Strict Press", "Overhead Press",
            "Shoulder Press", "Push Press", "Military Press", "Z Press", "Seated Press",
            "Bench Press", "Incline Bench Press", "Decline Bench Press", "Close Grip Bench Press",
            "Dumbbell Bench Press", "Incline Dumbbell Press", "Floor Press", "Dumbbell Floor Press",
            "Thruster", "Dumbbell Thruster", "Kettlebell Thruster", "Cluster", "Wall Ball Shots",
            "Pull-ups", "Strict Pull-ups", "Kipping Pull-ups", "Butterfly Pull-ups", "Chest to Bar",
            "Chin-ups", "Strict Chin-ups", "L-Sit Pull-ups", "Weighted Pull-ups", "Bar Muscle-ups",
            "Ring Muscle-ups", "Ring Dips", "Bar Dips", "Strict Dips", "Weighted Dips",
            "Push-ups", "Handstand Push-ups", "Strict Handstand Push-ups", "Deficit HSPU",
            "Diamond Push-ups", "Ring Push-ups", "Clapping Push-ups", "Pike Push-ups",
            "Handstand Walk", "Handstand Hold", "Wall Walk", "Wall Climb", "Bear Crawl",
            "Toes to Bar", "Knees to Elbows", "L-Sit", "L-Sit Hold", "V-ups", "Sit-ups",
            "GHD Sit-ups", "AbMat Sit-ups", "Hollow Hold", "Hollow Rock", "Arch Hold", "Arch Rock",
            "Plank", "Side Plank", "Superman", "Russian Twist", "Bicycle Crunches", "Leg Raises",
            "Rope Climb", "Legless Rope Climb", "Pegboard Ascent", "Box Jumps", "Box Jump Overs",
            "Burpees", "Burpee Box Jump Overs", "Burpee Pull-ups", "Bar Facing Burpees",
            "Target Burpees", "Double Unders", "Single Unders", "Triple Unders", "Crossover Single Unders",
            "Kettlebell Swings", "American KB Swings", "Russian KB Swings", "Dumbbell Swings",
            "Turkish Get Up", "Farmer Carry", "Suitcase Carry", "Overhead Carry", "Yoke Walk",
            "Sled Push", "Sled Pull", "Prowler Push", "Tire Flip", "D-Ball Over Shoulder",
            "Sandbag Carry", "Sandbag Over Bar", "Axle Bar Clean and Jerk", "Log Clean and Press",
            "Air Bike", "Assault Bike", "Echo Bike", "Rower", "SkiErg", "Treadmill", "Run",
            "Sprint", "Shuttle Run", "Swim", "Row", "Ski", "Bike",

            // Units, Terminology, Formats & Metrics
            "kg", "lbs", "reps", "sets", "rounds", "calories", "meters", "miles", "kilometers",
            "seconds", "minutes", "hours", "RPE", "WOD", "EMOM", "AMRAP", "METCON", "E2MOM",
            "E3MOM", "E4MOM", "E5MOM", "TABATA", "CHIPPER", "LADDER", "PYRAMID", "COMPLEX",
            "RX", "SCALED", "PR", "1RM", "2RM", "3RM", "5RM", "10RM", "PERCENTAGE", "LOAD",
            "REST", "INTERVAL", "TEMPO", "WARMUP", "COOLDOWN", "TIME CAP", "FOR TIME",

            // Variations & Modifiers
            "Barbell", "Dumbbell", "Kettlebell", "Plate", "Band", "Chain", "Medicine Ball",
            "Slam Ball", "Sandbag", "Atlas Stone", "D-Ball", "Jump Rope", "Speed Rope", "Rings",
            "Parallettes", "Plyo Box", "Bench", "Incline", "Decline", "Flat", "Strict", "Kipping",
            "Butterfly", "Deadstop", "Touch and Go", "Unbroken", "Deficit", "Elevated", "Banded",
            "Chained", "Eccentric", "Concentric", "Isometric", "Dynamic", "Max Effort", "Submaximal",
            "Warmup Set", "Work Set", "Drop Set", "Super Set", "Giant Set", "Rest Pause", "Cluster Set",
            "Heavy", "Light", "Moderate", "High Volume", "Low Volume", "Density", "Pacing",
            "Heart Rate", "Zone 2", "Zone 3", "Zone 4", "Zone 5", "VO2 Max", "Anaerobic", "Aerobic",
            "Lactate Threshold", "Cadence", "Pace", "Split", "Power", "Watts", "RPM", "BPM",

            // Additional Strength, Conditioning & Auxiliary Exercises
            "Barbell Row", "Pendlay Row", "Bent Over Row", "T-Bar Row", "Chest Supported Row",
            "Dumbbell Row", "Single Arm Row", "Meadows Row", "Seal Row", "Inverted Row",
            "Face Pull", "Band Pull Apart", "Rear Delt Fly", "Lateral Raise", "Front Raise",
            "Arnold Press", "Upright Row", "Shrug", "Barbell Shrug", "Dumbbell Shrug",
            "Biceps Curl", "Barbell Curl", "Dumbbell Curl", "Hammer Curl", "Preacher Curl",
            "Spider Curl", "Incline Curl", "Concentration Curl", "Cable Curl", "EZ Bar Curl",
            "Triceps Extension", "Skull Crusher", "Overhead Triceps Extension", "Triceps Pushdown",
            "Close Grip Push-up", "JM Press", "Tate Press", "Dips", "Bench Dips",
            "Leg Press", "Hack Squat Machine", "Leg Extension", "Leg Curl", "Lying Leg Curl",
            "Seated Leg Curl", "Nordic Curl", "Reverse Hyper", "Back Extension", "Hyperextension",
            "Glute Ham Raise", "Hip Thrust", "Barbell Hip Thrust", "Glute Bridge", "Single Leg Hip Thrust",
            "Cable Pull Through", "Monster Walk", "Clamshell", "Standing Calf Raise", "Seated Calf Raise",
            "Donkey Calf Raise", "Tibialis Raise", "Wrist Curl", "Reverse Wrist Curl", "Farmer Walk",
            "Overhead Walk", "Waiter Carry", "Front Rack Carry", "Zercher Carry", "Duck Walk",
            "Lunges", "Walking Lunges", "Reverse Lunges", "Forward Lunges", "Curtsy Lunges",
            "Jumping Lunges", "Overhead Lunges", "Front Rack Lunges", "Dumbbell Lunges", "Barbell Lunges",
            "Step Ups", "Dumbbell Step Ups", "Barbell Step Ups", "Box Step Overs", "Weighted Step Ups",
            "Broad Jump", "Vertical Jump", "Depth Jump", "Bounding", "Hurdle Jumps", "Tuck Jumps",
            "Skater Jumps", "Lateral Bounds", "Medicine Ball Slam", "Rotational Slam", "Wall Ball",
            "Chest Pass", "Overhead Slam", "Side Throw", "Scoop Toss", "Underhand Toss",
            "Battle Ropes", "Rope Waves", "Rope Slams", "Alternating Waves", "In-Out Waves",

            // Benchmark & Named Workout Movements & Slang
            "Fran", "Grace", "Isabel", "Helen", "Diane", "Elizabeth", "Cindy", "Mary", "Chelsea",
            "Annie", "Eva", "Kelly", "Lynne", "Nicole", "Angie", "Barbara", "Murph", "Chad",
            "Nate", "DT", "The Chief", "Fight Gone Bad", "Kalsu", "Seven", "Clovis", "Bull",
            "Badger", "Daniel", "Josh", "Jason", "Michael", "Tommy V", "Garrett", "Loredo",

            // Anatomical & Functional Fitness Terms
            "Core", "Abs", "Obliques", "Glutes", "Hamstrings", "Quadriceps", "Calves", "Adductors",
            "Abductors", "Lats", "Traps", "Rhomboids", "Deltoids", "Pectorals", "Biceps", "Triceps",
            "Forearms", "Lower Back", "Posterior Chain", "Rotator Cuff", "Scapular Pulls",
            "Mobility", "Flexibility", "Stretching", "Foam Rolling", "Dynamic Warmup", "Activation",
            "Banded Hip Distraction", "Couch Stretch", "Pigeon Pose", "Ankle Mobility", "Thoracic Extension",
            "Shoulder Dislocates", "Cat Cow", "World Greatest Stretch", "Hip Opener", "Wrist Mobility",
            "Wrist Stretch", "Iron Cross", "Scorpion Stretch", "Samson Stretch", "Hamstring Floss",

            // Programming, Periodization & Strategy Terms
            "Linear Periodization", "Undulating Periodization", "Conjugate", "Block Periodization",
            "Deload", "Taper", "Volume", "Intensity", "Frequency", "Overload", "Progressive Overload",
            "Hypertrophy", "Max Strength", "Power Development", "Endurance", "Speed", "Agility",
            "GPP", "SPP", "Peaking", "Accumulation", "Transmutation", "Realization",
            "Ramp Up", "Ascending Load", "Descending Load", "Wave Loading", "Straight Sets",
            "Drop Sets", "Rest Intervals", "Split Routine", "Full Body", "Upper Lower",
            "Push Pull Legs", "Olympic Lifting Block", "Strength Block", "Conditioning Block"
        ).distinct()

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