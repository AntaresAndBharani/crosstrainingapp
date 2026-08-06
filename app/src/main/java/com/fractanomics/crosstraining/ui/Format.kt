package com.fractanomics.crosstraining.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

fun LocalDate.formatLong(): String = format(dateFormatter)

fun LocalDate.formatShort(): String = format(shortDateFormatter)

/** Trim trailing ".0" from whole numbers, keep up to 1 decimal otherwise. */
fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toLong().toString()
    else String.format(Locale.getDefault(), "%.1f", this)

/** Common workout formats offered as quick-pick chips. */
val WORKOUT_FORMATS = listOf(
    "EMOM", "E2MOM", "E3MOM", "AMRAP", "Calorie Sprints", "Work/Rest Intervals", "Distance Sprints", "Rest 90s", "Rest 2 min", "Death by", "Time cap", "For Time", "Tabata", "Chipper", "Rounds for Time", "Sets x Reps", "Wave", "Tempo"
)
