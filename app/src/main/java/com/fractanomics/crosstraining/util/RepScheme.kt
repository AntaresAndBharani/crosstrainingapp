package com.fractanomics.crosstraining.util

/** Preset rep scheme constants. */
object RepScheme {
    const val WAVE_321 = "3-2-1-3-2-1-3-2-1-1-1-1"
    const val WAVE_221 = "2-2-1-2-2-1-2-2-1-1-1-1"
    val FIXED_REPS_PRESETS = listOf(2, 3, 4, 5, 6, 8, 10, 12, 15)

    /**
     * Parses a rep scheme string into a list of reps per set.
     * E.g. "3-2-1-3-2-1" -> [3, 2, 1, 3, 2, 1]
     * "5" with setsCount=4 -> [5, 5, 5, 5]
     * "5x3" -> [3, 3, 3, 3, 3]
     */
    fun parse(scheme: String, defaultSetsCount: Int = 1): List<Int> {
        val trimmed = scheme.trim()
        if (trimmed.isBlank()) {
            return List(defaultSetsCount.coerceAtLeast(1)) { 5 }
        }

        // Check for dash or comma separated sequence (e.g. 3-2-1-3-2-1 or 3,2,1)
        if (trimmed.contains("-") || trimmed.contains(",")) {
            val parts = trimmed.split(Regex("[-,]")).mapNotNull { it.trim().toIntOrNull() }
            if (parts.isNotEmpty()) return parts
        }

        // Check for NxM format (e.g. 5x3 -> 5 sets of 3 reps)
        if (trimmed.lowercase().contains("x")) {
            val parts = trimmed.lowercase().split("x")
            if (parts.size == 2) {
                val sets = parts[0].trim().toIntOrNull()
                val reps = parts[1].trim().toIntOrNull()
                if (sets != null && reps != null && sets > 0) {
                    return List(sets) { reps }
                }
            }
        }

        // Single integer (e.g. "5" -> repeat 5 for defaultSetsCount sets)
        val singleRep = trimmed.toIntOrNull()
        if (singleRep != null) {
            return List(defaultSetsCount.coerceAtLeast(1)) { singleRep }
        }

        return List(defaultSetsCount.coerceAtLeast(1)) { 5 }
    }
}
