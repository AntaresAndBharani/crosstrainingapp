package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * An athlete's body weight log for a calendar date.
 * Uses natural primary key [date] to guarantee idempotence and avoid surrogate key churn.
 * Supports soft-deletion via [deletedAtMillis] for tombstone-durable cloud sync.
 */
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey val date: LocalDate,
    val weightKg: Double,
    val notes: String = "",
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null
)
