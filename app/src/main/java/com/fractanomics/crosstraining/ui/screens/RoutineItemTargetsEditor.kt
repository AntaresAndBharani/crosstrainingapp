package com.fractanomics.crosstraining.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import com.fractanomics.crosstraining.util.TargetMode

const val DAILY_ROUTINE_ALL_ZERO_TARGET_ERROR =
    "Set a target above 0 on at least one set, or clear targets"

internal fun isDailyRoutineItemTargetValid(item: DailyRoutineItem): Boolean = when (item) {
    is DailyRoutineItem.StandaloneExercise -> {
        if (!item.hasConfiguredTarget) true
        else !(item.targetSpec?.isAllZeroInvalid ?: false)
    }
    is DailyRoutineItem.BlockRef -> {
        if (!item.hasConfiguredTargets) true
        else item.exerciseTargets.values.none { it.isAllZeroInvalid } &&
            item.exerciseTargetsByName.values.none { it.isAllZeroInvalid }
    }
}

internal fun defaultTargetSpecForMetric(
    metric: ExerciseTargetMetric,
    setsCount: Int
): RoutineExerciseTargetSpec {
    val cleanSets = setsCount.coerceAtLeast(1)
    return RoutineExerciseTargetSpec(
        metric = metric,
        mode = TargetMode.FIXED,
        fixedValue = metric.defaultFixedValue,
        perSetValues = List(cleanSets) { metric.defaultFixedValue }
    )
}

internal fun switchSpecMetricWithCache(
    current: RoutineExerciseTargetSpec,
    newMetric: ExerciseTargetMetric,
    setsCount: Int,
    cache: MutableMap<ExerciseTargetMetric, MetricValueCache>
): RoutineExerciseTargetSpec {
    if (current.metric == newMetric) return current
    val cleanSets = setsCount.coerceAtLeast(1)
    cache[current.metric] = MetricValueCache(
        fixedValue = current.fixedValue,
        perSetValues = RoutineTargetCodec.resizePerSetValues(current.perSetValues, current.fixedValue, cleanSets)
    )
    val cached = cache[newMetric]
    val nextFixed = cached?.fixedValue ?: newMetric.defaultFixedValue
    val nextPerSet = if (cached != null) {
        RoutineTargetCodec.resizePerSetValues(cached.perSetValues, nextFixed, cleanSets)
    } else {
        List(cleanSets) { nextFixed }
    }
    return RoutineExerciseTargetSpec(
        metric = newMetric,
        mode = current.mode,
        fixedValue = nextFixed,
        perSetValues = nextPerSet
    )
}

internal fun switchSpecMode(
    current: RoutineExerciseTargetSpec,
    newMode: TargetMode,
    setsCount: Int
): RoutineExerciseTargetSpec {
    if (current.mode == newMode) return current
    val cleanSets = setsCount.coerceAtLeast(1)
    return if (newMode == TargetMode.PER_SET) {
        val seedValues = if (current.perSetValues.size == cleanSets &&
            current.perSetValues.any { it != current.fixedValue }
        ) {
            current.perSetValues
        } else {
            List(cleanSets) { current.fixedValue }
        }
        current.copy(mode = TargetMode.PER_SET, perSetValues = seedValues)
    } else {
        val nextFixed = current.perSetValues.firstOrNull { it > 0 }
            ?: current.fixedValue.takeIf { it > 0 }
            ?: current.metric.defaultFixedValue
        current.copy(mode = TargetMode.FIXED, fixedValue = nextFixed)
    }
}

internal fun updateSpecFixedValue(
    current: RoutineExerciseTargetSpec,
    newValue: Int,
    setsCount: Int
): RoutineExerciseTargetSpec {
    val cleanSets = setsCount.coerceAtLeast(1)
    val clamped = newValue.coerceAtLeast(0)
    return current.copy(
        fixedValue = clamped,
        perSetValues = if (current.mode == TargetMode.FIXED) List(cleanSets) { clamped } else current.perSetValues
    )
}

internal fun updateSpecPerSetValue(
    current: RoutineExerciseTargetSpec,
    setIndex: Int,
    newValue: Int,
    setsCount: Int
): RoutineExerciseTargetSpec {
    val cleanSets = setsCount.coerceAtLeast(1)
    val resized = RoutineTargetCodec.resizePerSetValues(current.perSetValues, current.fixedValue, cleanSets)
        .toMutableList()
    if (setIndex !in resized.indices) return current
    resized[setIndex] = newValue.coerceAtLeast(0)
    return current.copy(mode = TargetMode.PER_SET, perSetValues = resized)
}

internal fun applyPatternToTargetSpec(
    current: RoutineExerciseTargetSpec,
    pattern: ExerciseSetPattern,
    setsCount: Int
): RoutineExerciseTargetSpec {
    val cleanSets = setsCount.coerceAtLeast(1)
    val resized = RoutineTargetCodec.resizePerSetValues(current.perSetValues, current.fixedValue, cleanSets)
    val updated = when (pattern) {
        ExerciseSetPattern.COPY_FIRST ->
            RoutineTargetCodec.copyFirstSetToAll(resized, cleanSets, current.fixedValue)
        ExerciseSetPattern.DESCENDING ->
            RoutineTargetCodec.applyDescendingPattern(resized, current.metric, cleanSets, current.fixedValue)
        ExerciseSetPattern.ALTERNATE_ZEROS ->
            RoutineTargetCodec.applyAlternatingZerosPattern(resized, cleanSets, current.fixedValue)
    }
    return current.copy(mode = TargetMode.PER_SET, perSetValues = updated)
}

internal fun resizeTargetSpecSetsCount(
    current: RoutineExerciseTargetSpec,
    newSetsCount: Int
): RoutineExerciseTargetSpec {
    val cleanSets = newSetsCount.coerceAtLeast(1)
    val resized = if (current.mode == TargetMode.FIXED) {
        List(cleanSets) { current.fixedValue.coerceAtLeast(0) }
    } else {
        RoutineTargetCodec.resizePerSetValues(current.perSetValues, current.fixedValue, cleanSets)
    }
    return current.copy(perSetValues = resized)
}

internal fun formatDailyRoutineTargetSummary(
    setsCount: Int,
    spec: RoutineExerciseTargetSpec
): String {
    val cleanSets = setsCount.coerceAtLeast(1)
    val setWord = if (cleanSets == 1) "set" else "sets"
    return "$cleanSets $setWord · ${RoutineTargetCodec.formatExerciseTargetBadge(spec, cleanSets)}"
}

internal fun formatCompactChipTargetSummary(
    setsCount: Int,
    spec: RoutineExerciseTargetSpec
): String {
    val cleanSets = setsCount.coerceAtLeast(1)
    return when (spec.mode) {
        TargetMode.FIXED -> when (spec.metric) {
            ExerciseTargetMetric.REPS -> "${cleanSets}×${spec.fixedValue} reps"
            ExerciseTargetMetric.CALORIES -> "${cleanSets}×${spec.fixedValue} cal"
            ExerciseTargetMetric.TIME_SECONDS,
            ExerciseTargetMetric.METERS ->
                "${cleanSets}×${RoutineTargetCodec.formatCompactSetCellValue(spec.metric, spec.fixedValue)}"
        }
        TargetMode.PER_SET -> {
            val joined = spec.effectiveValues(cleanSets).joinToString("•")
            when (spec.metric) {
                ExerciseTargetMetric.REPS -> "$joined reps"
                ExerciseTargetMetric.CALORIES -> "$joined cal"
                ExerciseTargetMetric.TIME_SECONDS -> "${joined}s"
                ExerciseTargetMetric.METERS -> "${joined}m"
            }
        }
    }
}

internal fun resolveBlockExerciseTargetSpec(
    ex: AvailableBlockExercise,
    targetsById: Map<Long, RoutineExerciseTargetSpec>,
    targetsByName: Map<String, RoutineExerciseTargetSpec>
): RoutineExerciseTargetSpec? =
    targetsById[ex.id] ?: targetsByName[ex.name.trim().lowercase()]

internal fun formatBlockRoutineTargetsSummary(
    setsCount: Int,
    exercises: List<AvailableBlockExercise>,
    targetsById: Map<Long, RoutineExerciseTargetSpec>,
    targetsByName: Map<String, RoutineExerciseTargetSpec>
): String {
    val cleanSets = setsCount.coerceAtLeast(1)
    val setWord = if (cleanSets == 1) "set" else "sets"
    val specs = if (exercises.isNotEmpty()) {
        exercises.mapNotNull { ex ->
            resolveBlockExerciseTargetSpec(ex, targetsById, targetsByName)?.let { ex.name to it }
        }
    } else {
        targetsById.entries.map { "Exercise" to it.value }
    }
    if (specs.isEmpty()) return "$cleanSets $setWord"
    val parts = specs.take(2).map { (name, spec) ->
        "$name: ${formatCompactChipTargetSummary(cleanSets, spec)}"
    }
    val extra = if (specs.size > 2) " +${specs.size - 2} more" else ""
    return "$cleanSets $setWord · ${parts.joinToString(" · ")}$extra"
}

@Composable
internal fun SetsCountStepperRow(
    setsCount: Int,
    label: String = "Sets",
    onUpdateSetsCount: (Int) -> Unit
) {
    val cleanSets = setsCount.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { if (cleanSets > 1) onUpdateSetsCount(cleanSets - 1) },
                enabled = cleanSets > 1,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease sets")
            }
            Text(
                text = "$cleanSets ${if (cleanSets == 1) "set" else "sets"}",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onUpdateSetsCount((cleanSets + 1).coerceAtMost(50)) },
                enabled = cleanSets < 50,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase sets")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RoutineTargetCodec.BLOCK_SET_QUICK_PILLS) { pillCount ->
                FilterChip(
                    selected = cleanSets == pillCount,
                    onClick = { onUpdateSetsCount(pillCount) },
                    label = {
                        Text(
                            text = "$pillCount",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                )
            }
        }
    }
}

@Composable
internal fun MetricTargetSelectorStrip(
    selectedMetric: ExerciseTargetMetric,
    onSelectMetric: (ExerciseTargetMetric) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(ExerciseTargetMetric.entries) { metric ->
            FilterChip(
                selected = selectedMetric == metric,
                onClick = { onSelectMetric(metric) },
                label = {
                    Text(
                        text = metric.label,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InlineExerciseTargetConfigPanel(
    setsCount: Int,
    spec: RoutineExerciseTargetSpec,
    showSetsControl: Boolean = true,
    onUpdateSetsCount: (Int) -> Unit = {},
    onSelectMetric: (ExerciseTargetMetric) -> Unit,
    onSelectMode: (TargetMode) -> Unit,
    onUpdateFixed: (Int) -> Unit,
    onUpdatePerSet: (Int, Int) -> Unit,
    onApplyPattern: (ExerciseSetPattern) -> Unit,
    onClearTargets: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    val cleanSets = setsCount.coerceAtLeast(1)
    val fontScale = LocalConfiguration.current.fontScale
    var editingFixedDigits by remember(spec.metric, spec.mode) { mutableStateOf(false) }
    var fixedInputText by remember(spec.fixedValue, spec.metric) {
        mutableStateOf(spec.fixedValue.toString())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (spec.isAllZeroInvalid) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = DAILY_ROUTINE_ALL_ZERO_TARGET_ERROR,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (showSetsControl) {
            SetsCountStepperRow(
                setsCount = cleanSets,
                label = "Sets",
                onUpdateSetsCount = onUpdateSetsCount
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Metric",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MetricTargetSelectorStrip(
                selectedMetric = spec.metric,
                onSelectMetric = { metric ->
                    editingFixedDigits = false
                    onSelectMetric(metric)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TargetMode.FIXED to "Fixed",
                TargetMode.PER_SET to "Per-Set ($cleanSets)"
            ).forEach { (mode, label) ->
                val selected = spec.mode == mode
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            editingFixedDigits = false
                            onSelectMode(mode)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        if (spec.mode == TargetMode.FIXED) {
            val step = spec.metric.fixedStep
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        editingFixedDigits = false
                        onUpdateFixed((spec.fixedValue - step).coerceAtLeast(0))
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease ${spec.metric.label}")
                }

                if (editingFixedDigits) {
                    OutlinedTextField(
                        value = fixedInputText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(6)
                            fixedInputText = digits
                            digits.toIntOrNull()?.let { onUpdateFixed(it) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text(spec.metric.compactUnit) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                fixedInputText = spec.fixedValue.toString()
                                editingFixedDigits = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${RoutineTargetCodec.formatValueNumberAndUnit(spec.metric, spec.fixedValue)} / set",
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        editingFixedDigits = false
                        onUpdateFixed(spec.fixedValue + step)
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase ${spec.metric.label}")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                spec.metric.presets.forEach { preset ->
                    FilterChip(
                        selected = spec.fixedValue == preset.value,
                        onClick = {
                            editingFixedDigits = false
                            onUpdateFixed(preset.value)
                        },
                        label = { Text(preset.label, maxLines = 1, softWrap = false) },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }
            }
        } else {
            val normalizedSets = remember(spec.perSetValues, spec.fixedValue, cleanSets) {
                RoutineTargetCodec.resizePerSetValues(spec.perSetValues, spec.fixedValue, cleanSets)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = resolvePerSetGridColumns(maxWidth.value.toInt(), fontScale)
                val rows = normalizedSets.withIndex().chunked(cols)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (setIdx, setVal) ->
                                PerSetValueCard(
                                    setNumber = setIdx + 1,
                                    metric = spec.metric,
                                    value = setVal,
                                    fallbackPositive = spec.fixedValue.takeIf { it > 0 }
                                        ?: spec.metric.defaultFixedValue,
                                    onValueChange = { updated -> onUpdatePerSet(setIdx, updated) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (cleanSets > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExerciseSetPattern.entries.forEach { pattern ->
                        FilterChip(
                            selected = false,
                            onClick = { onApplyPattern(pattern) },
                            label = { Text(pattern.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }
        }

        if (onClearTargets != null || onDone != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClearTargets != null) {
                    TextButton(
                        onClick = onClearTargets,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Clear targets", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (onDone != null) {
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Done ▲", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineBlockTargetsConfigPanel(
    setsCount: Int,
    exercises: List<AvailableBlockExercise>,
    targetsById: Map<Long, RoutineExerciseTargetSpec>,
    targetsByName: Map<String, RoutineExerciseTargetSpec>,
    expandedExerciseKey: String?,
    onToggleExerciseExpanded: (String) -> Unit,
    onUpdateSetsCount: (Int) -> Unit,
    onSelectMetric: (Long, String, ExerciseTargetMetric) -> Unit,
    onSelectMode: (Long, String, TargetMode) -> Unit,
    onUpdateFixed: (Long, String, Int) -> Unit,
    onUpdatePerSet: (Long, String, Int, Int) -> Unit,
    onApplyPattern: (Long, String, ExerciseSetPattern) -> Unit,
    onClearTargets: () -> Unit,
    onDone: () -> Unit
) {
    val cleanSets = setsCount.coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SetsCountStepperRow(
            setsCount = cleanSets,
            label = "Complex Sets (this routine)",
            onUpdateSetsCount = onUpdateSetsCount
        )

        HorizontalDivider()

        exercises.forEach { ex ->
            val exKey = if (ex.id > 0L) "id:${ex.id}" else "name:${ex.name.trim().lowercase()}"
            val spec = resolveBlockExerciseTargetSpec(ex, targetsById, targetsByName)
                ?: RoutineExerciseTargetSpec(
                    metric = ExerciseTargetMetric.REPS,
                    mode = TargetMode.FIXED,
                    fixedValue = ExerciseTargetMetric.REPS.defaultFixedValue,
                    perSetValues = List(cleanSets) { ExerciseTargetMetric.REPS.defaultFixedValue }
                )
            val isExExpanded = expandedExerciseKey == exKey || spec.isAllZeroInvalid

            OutlinedCard(
                shape = RoundedCornerShape(10.dp),
                border = if (spec.isAllZeroInvalid) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onToggleExerciseExpanded(exKey) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ex.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = RoutineTargetCodec.formatExerciseTargetBadge(spec, cleanSets),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (spec.isAllZeroInvalid) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Icon(
                            imageVector = if (isExExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExExpanded) "Collapse ${ex.name}" else "Expand ${ex.name}"
                        )
                    }

                    AnimatedVisibility(
                        visible = isExExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            InlineExerciseTargetConfigPanel(
                                setsCount = cleanSets,
                                spec = spec,
                                showSetsControl = false,
                                onSelectMetric = { m -> onSelectMetric(ex.id, ex.name, m) },
                                onSelectMode = { mode -> onSelectMode(ex.id, ex.name, mode) },
                                onUpdateFixed = { v -> onUpdateFixed(ex.id, ex.name, v) },
                                onUpdatePerSet = { idx, v -> onUpdatePerSet(ex.id, ex.name, idx, v) },
                                onApplyPattern = { pat -> onApplyPattern(ex.id, ex.name, pat) }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onClearTargets,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Clear targets", color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Done ▲", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
