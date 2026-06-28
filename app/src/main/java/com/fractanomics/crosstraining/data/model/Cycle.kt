package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A training block. The [endDate] is intentionally mutable so a cycle can be
 * extended or shortened mid-way. Only one cycle is [isActive] at a time.
 */
@Entity(tableName = "cycles")
data class Cycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val goal: String = "",
    val isActive: Boolean = false
)
