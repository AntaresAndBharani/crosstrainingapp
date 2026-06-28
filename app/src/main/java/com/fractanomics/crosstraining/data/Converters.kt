package com.fractanomics.crosstraining.data

import androidx.room.TypeConverter
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import java.time.LocalDate

/** Room type converters for [LocalDate] and the model enums. */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromCategory(value: String?): ExerciseCategory? =
        value?.let { ExerciseCategory.valueOf(it) }

    @TypeConverter
    fun toCategory(value: ExerciseCategory?): String? = value?.name

    @TypeConverter
    fun fromMetricType(value: String?): MetricType? = value?.let { MetricType.valueOf(it) }

    @TypeConverter
    fun toMetricType(value: MetricType?): String? = value?.name
}
