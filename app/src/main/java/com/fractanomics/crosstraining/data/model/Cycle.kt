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
        targetBodyWeightKg: Double? = null
    ) : this(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        goal = goal,
        isActive = isActive,
        type = type,
        fastDaysOfWeek = fastDaysOfWeek,
        restDaysOfWeek = restDaysOfWeek,
        startingWeightKg = startingWeightKg,
        targetWeightKg = targetWeightKg ?: targetBodyWeightKg,
        isBaselineAutoDerived = isBaselineAutoDerived
    )
}
