package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A movement you want to track progress on. This is the "main exercise" a
 * routine aims to improve (e.g. Squat Snatch) or a machine you score in WODs
 * (e.g. Air Bike). [tracksRepMax] mirrors [MetricType.tracksRepMax] but is
 * stored so it can be overridden per exercise if ever needed.
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val metricType: MetricType,
    val unit: String,
    val tracksRepMax: Boolean,
    val notes: String = ""
)
