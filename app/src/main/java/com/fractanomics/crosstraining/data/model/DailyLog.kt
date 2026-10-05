package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * An athlete's daily logging record for fasting, nutrition macros, and rest days.
 * Uses natural primary key [date] to enforce calendar day idempotence.
 * Supports soft-deletion via [deletedAtMillis] for tombstone-durable cloud sync.
 */
@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: LocalDate,
    val fastCompleted: Boolean? = null,
    val isRestDay: Boolean = false,
    val caloriesKcal: Int? = null,
    val proteinGrams: Int? = null,
    val carbsGrams: Int? = null,
    val fatGrams: Int? = null,
    val notes: String = "",
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null
)
