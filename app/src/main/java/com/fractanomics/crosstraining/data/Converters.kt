package com.fractanomics.crosstraining.data

import androidx.room.TypeConverter
import com.fractanomics.crosstraining.data.model.BlockKind
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

    @TypeConverter
    fun fromBlockKind(value: String?): BlockKind? = value?.let { BlockKind.valueOf(it) }

    @TypeConverter
    fun toBlockKind(value: BlockKind?): String? = value?.name

    @TypeConverter
    fun fromCycleType(value: String?): com.fractanomics.crosstraining.data.model.CycleType? =
        value?.let { runCatching { com.fractanomics.crosstraining.data.model.CycleType.valueOf(it) }.getOrDefault(com.fractanomics.crosstraining.data.model.CycleType.STRENGTH_WEIGHTLIFTING) }

    @TypeConverter
    fun toCycleType(value: com.fractanomics.crosstraining.data.model.CycleType?): String? = value?.name
}
