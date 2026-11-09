package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A training block. The [endDate] is intentionally mutable so a cycle can be
 * extended or shortened mid-way. Only one cycle is [isActive] at a time.
 *
 * Architecture Note: Cycle configuration and bitmask fields ([fastDaysOfWeek], [restDaysOfWeek])
 * are strictly local-only and intentionally excluded from Cloud Firestore synchronization.
 */
@Entity(tableName = "cycles")
data class Cycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val goal: String = "",
    val isActive: Boolean = false,
    val type: CycleType = CycleType.STRENGTH_WEIGHTLIFTING,
    /** Local-only bitmask for scheduled fast days (Monday=bit 0 .. Sunday=bit 6). Not synced to cloud. */
    val fastDaysOfWeek: Int = 0,
    /** Local-only bitmask for scheduled rest days (Monday=bit 0 .. Sunday=bit 6). Not synced to cloud. */
    val restDaysOfWeek: Int = 0,
    val startingWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val isBaselineAutoDerived: Boolean = false
) {
    /**
     * Target body weight in kilograms. Canonical alias for [targetWeightKg].
     */
    @get:androidx.room.Ignore
    val targetBodyWeightKg: Double?
        get() = targetWeightKg

    /**
     * Whether an explicit `[subcycle=...]` metadata header is encoded in [goal].
     */
    @get:androidx.room.Ignore
    val hasExplicitSubcycleType: Boolean
        get() = parseSubcycleMetadata(goal).first != null

    /**
     * Active [WeightSubcycleType] for this cycle (`WEIGHT_LOSS`, `RECOMPOSITION`, or `WEIGHT_GAIN`).
     * Uses the explicitly saved metadata header when present; otherwise infers a sensible default
     * from [startingWeightKg], [targetWeightKg], and [type].
     */
    @get:androidx.room.Ignore
    val subcycleType: WeightSubcycleType
        get() = parseSubcycleMetadata(goal).first ?: inferDefaultSubcycleType(
            type = type,
            startingWeightKg = startingWeightKg,
            targetWeightKg = targetWeightKg
        )

    /**
     * User-visible cycle focus / notes with any leading `[subcycle=...]` metadata header stripped.
     */
    @get:androidx.room.Ignore
    val cleanGoal: String
        get() = parseSubcycleMetadata(goal).second

    /**
     * Returns a copy of this [Cycle] with [newSubcycleType] explicitly encoded in [goal]
     * while preserving [cleanGoal].
     */
    fun withSubcycleType(newSubcycleType: WeightSubcycleType): Cycle =
        copy(goal = encodeSubcycleMetadata(cleanGoal, newSubcycleType))

    @androidx.room.Ignore
    constructor(
        id: Long = 0,
        name: String,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        goal: String = "",
        isActive: Boolean = false,
        type: CycleType = CycleType.STRENGTH_WEIGHTLIFTING,
        fastDaysOfWeek: Int = 0,
        restDaysOfWeek: Int = 0,
        startingWeightKg: Double? = null,
        targetWeightKg: Double? = null,
        isBaselineAutoDerived: Boolean = false,
        targetBodyWeightKg: Double? = null,
        subcycleType: WeightSubcycleType? = null
    ) : this(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        goal = if (subcycleType != null) encodeSubcycleMetadata(goal, subcycleType) else goal,
        isActive = isActive,
        type = type,
        fastDaysOfWeek = fastDaysOfWeek,
        restDaysOfWeek = restDaysOfWeek,
        startingWeightKg = startingWeightKg,
        targetWeightKg = targetWeightKg ?: targetBodyWeightKg,
        isBaselineAutoDerived = isBaselineAutoDerived
    )

    companion object {
        private val SUBCYCLE_METADATA_REGEX =
            Regex("""^\[subcycle=(WEIGHT_LOSS|RECOMPOSITION|WEIGHT_GAIN)\]\s*""", RegexOption.IGNORE_CASE)

        fun inferDefaultSubcycleType(
            type: CycleType,
            startingWeightKg: Double?,
            targetWeightKg: Double?
        ): WeightSubcycleType {
            if (startingWeightKg != null && targetWeightKg != null && startingWeightKg > 0.0 && targetWeightKg > 0.0) {
                return when {
                    targetWeightKg < startingWeightKg -> WeightSubcycleType.WEIGHT_LOSS
                    targetWeightKg > startingWeightKg -> WeightSubcycleType.WEIGHT_GAIN
                    else -> WeightSubcycleType.RECOMPOSITION
                }
            }
            return if (type == CycleType.FAT_LOSS_BODYBUILDING) {
                WeightSubcycleType.WEIGHT_LOSS
            } else {
                WeightSubcycleType.RECOMPOSITION
            }
        }

        fun encodeSubcycleMetadata(rawGoal: String, subcycleType: WeightSubcycleType?): String {
            val stripped = rawGoal.replace(SUBCYCLE_METADATA_REGEX, "").trim()
            if (subcycleType == null) return stripped
            val header = "[subcycle=${subcycleType.name}]"
            return if (stripped.isEmpty()) header else "$header $stripped"
        }

        fun parseSubcycleMetadata(rawGoal: String): Pair<WeightSubcycleType?, String> {
            val match = SUBCYCLE_METADATA_REGEX.find(rawGoal)
                ?: return Pair(null, rawGoal)
            val token = match.groupValues[1].uppercase()
            val parsed = runCatching { WeightSubcycleType.valueOf(token) }.getOrNull()
            val stripped = rawGoal.substring(match.range.last + 1).trim()
            return Pair(parsed, stripped)
        }
    }
}
