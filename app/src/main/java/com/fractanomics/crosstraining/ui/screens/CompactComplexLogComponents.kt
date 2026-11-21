package com.fractanomics.crosstraining.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fractanomics.crosstraining.R
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.TrackedPrSlot
import kotlinx.coroutines.launch

private val ComplexCardSurfaceLight = Color(0xFFF8F9FA)
private val ComplexCardSurfaceDark = Color(0xFF1C2226)
private val ComplexHeaderLight = Color(0xFF0369A1)
private val ComplexHeaderDark = Color(0xFF7DD3FC)
private val ComplexPrescriptionLight = Color(0xFFF1F5F9)
private val ComplexPrescriptionDark = Color(0xFF242C31)
private val ComplexLegendLight = Color(0xFF374146)
private val ComplexLegendDark = Color(0xFFCBD5E1)
private val ComplexRowBgLight = Color(0xFFFFFFFF)
private val ComplexRowBgDark = Color(0xFF171C20)
private val ComplexRowBorderLight = Color(0xFFE2E8F0)
private val ComplexRowBorderDark = Color(0xFF2D3748)
private val ComplexActivePillBgLight = Color(0xFFE0F2FE)
private val ComplexActivePillTextLight = Color(0xFF0369A1)
private val ComplexActivePillBgDark = Color(0xFF1E3A5F)
private val ComplexActivePillTextDark = Color(0xFF7DD3FC)
private val ComplexSkippedPillBgLight = Color(0xFFF1F5F9)
private val ComplexSkippedPillBorderLight = Color(0xFFCBD5E1)
private val ComplexSkippedPillTextLight = Color(0xFF64748B)
private val ComplexSkippedPillBgDark = Color(0xFF20272C)
private val ComplexSkippedPillBorderDark = Color(0xFF475569)
private val ComplexSkippedPillTextDark = Color(0xFF94A3B8)
private val ComplexWeightLinkedBgLight = Color(0xFFEEF7F2)
private val ComplexWeightLinkedBorderLight = Color(0xFF68B684)
private val ComplexWeightLinkedBgDark = Color(0xFF1A2E24)
private val ComplexWeightLinkedBorderDark = Color(0xFF2ECC71)
private val ComplexWeightMixedBgLight = Color(0xFFFFFBEB)
private val ComplexWeightMixedBorderLight = Color(0xFFD97706)
private val ComplexWeightMixedTextLight = Color(0xFFB45309)
private val ComplexWeightMixedBgDark = Color(0xFF3B2810)
private val ComplexWeightMixedBorderDark = Color(0xFFF59E0B)
private val ComplexWeightMixedTextDark = Color(0xFFFBD38D)
private val ComplexSubDrawerBgLight = Color(0xFFF8FAFC)
private val ComplexSubDrawerBgDark = Color(0xFF1E252A)
private val ComplexActiveSwitchBgLight = Color(0xFFE3F5EC)
private val ComplexActiveSwitchTextLight = Color(0xFF0B6E3F)
private val ComplexActiveSwitchBgDark = Color(0xFF133826)
private val ComplexActiveSwitchTextDark = Color(0xFF2ECC71)

internal fun sanitizeCompactWeightInput(raw: String): String {
    val cleaned = buildString {
        var hasDecimal = false
        for (ch in raw.trim()) {
            when {
                ch.isDigit() -> append(ch)
                (ch == '.' || ch == ',') && !hasDecimal -> {
                    append('.')
                    hasDecimal = true
                }
            }
        }
    }.take(8)
    if (cleaned.isEmpty()) return ""
    val numeric = cleaned.toDoubleOrNull()
    if (numeric != null && numeric > 9999.0) {
        return "9999"
    }
    return cleaned
}

internal fun circledMovementIndex(oneBasedIndex: Int): String =
    if (oneBasedIndex in 1..20) {
        Char(0x2460 + oneBasedIndex - 1).toString()
    } else {
        "$oneBasedIndex."
    }

internal fun shouldReflowComplexSetRow(fontScale: Float): Boolean = fontScale >= 1.4f

internal fun shouldShowPillExerciseNames(availableWidthDp: Int): Boolean = availableWidthDp >= 520

internal fun formatCompactMovementPillText(
    oneBasedIndex: Int,
    exerciseName: String,
    metric: ExerciseTargetMetric,
    targetDisplay: String,
    isSkipped: Boolean,
    showExerciseName: Boolean
): String {
    val badge = circledMovementIndex(oneBasedIndex)
    val shortName = if (showExerciseName && exerciseName.isNotBlank()) {
        val trimmed = exerciseName.trim()
        if (trimmed.length > 10) trimmed.take(9) + "…" else trimmed
    } else {
        null
    }
    val valueToken = if (isSkipped) {
        "—"
    } else {
        when (metric) {
            ExerciseTargetMetric.REPS -> targetDisplay.ifBlank { "1" }
            ExerciseTargetMetric.CALORIES -> "${targetDisplay.ifBlank { metric.defaultFixedValue.toString() }}c"
            ExerciseTargetMetric.TIME_SECONDS -> {
                val secs = targetDisplay.toIntOrNull()
                if (secs != null && secs >= 60 && secs % 60 == 0) {
                    "${secs / 60}m"
                } else {
                    "${targetDisplay.ifBlank { metric.defaultFixedValue.toString() }}s"
                }
            }
            ExerciseTargetMetric.METERS -> {
                val meters = targetDisplay.toIntOrNull()
                if (meters != null && meters >= 1000 && meters % 1000 == 0) {
                    "${meters / 1000}km"
                } else {
                    "${targetDisplay.ifBlank { metric.defaultFixedValue.toString() }}m"
                }
            }
        }
    }
    return if (shortName != null) "$badge $shortName · $valueToken" else "$badge $valueToken"
}

private fun movementTargetSpokenLabel(
    exState: LoadedAvailableBlockExerciseState,
    setIdx: Int
): String {
    val metric = exState.activeMetric
    val rawVal = if (metric == ExerciseTargetMetric.REPS) {
        exState.repsInputs.getOrNull(setIdx)?.toIntOrNull()
            ?: exState.perSetReps.getOrNull(setIdx)
            ?: exState.defaultReps.coerceAtLeast(1)
    } else {
        exState.metricInputs.getOrNull(setIdx)?.toIntOrNull()
            ?: metric.defaultFixedValue
    }
    val unitWord = if (rawVal == 1) metric.spokenUnitSingular else metric.spokenUnitPlural
    return "${exState.exerciseName} $rawVal $unitWord"
}

internal fun formatCompactSetAccessibilityLabel(
    setNumber: Int,
    blockState: LoadedAvailableBlockState,
    summary: SharedSetWeightSummary,
    weightUnit: String,
    isExpanded: Boolean
): String {
    val setIdx = setNumber - 1
    val activeParts = mutableListOf<String>()
    val skippedParts = mutableListOf<String>()
    blockState.exerciseSections.forEach { exState ->
        if (exState.skippedFlags.getOrElse(setIdx) { false }) {
            skippedParts += "${exState.exerciseName} skipped"
        } else {
            activeParts += movementTargetSpokenLabel(exState, setIdx)
        }
    }
    val movementSentence = buildString {
        append("Set $setNumber: ")
        if (activeParts.isNotEmpty()) {
            append(activeParts.joinToString(", "))
            append(".")
        }
        if (skippedParts.isNotEmpty()) {
            if (activeParts.isNotEmpty()) append(" ")
            append(skippedParts.joinToString(", "))
            append(".")
        }
    }
    val spokenUnit = if (weightUnit.equals("lbs", ignoreCase = true)) "pounds" else "kilograms"
    val weightSentence = when (summary.kind) {
        SharedSetWeightStateKind.ALL_SKIPPED -> "All movements skipped."
        SharedSetWeightStateKind.NON_WEIGHTED_ONLY -> "Bodyweight or conditioning set."
        SharedSetWeightStateKind.MIXED -> {
            val minT = summary.minWeight?.trimmed()
            val maxT = summary.maxWeight?.trimmed()
            if (minT != null && maxT != null) {
                "Mixed weights from $minT to $maxT $spokenUnit."
            } else {
                "Mixed weights."
            }
        }
        SharedSetWeightStateKind.LINKED -> {
            if (summary.uniformWeightText.isBlank()) {
                "Shared weight empty, $spokenUnit."
            } else {
                "Shared weight ${summary.uniformWeightText} $spokenUnit."
            }
        }
    }
    val stateSentence = if (isExpanded) {
        "Expanded. Double-tap to collapse."
    } else {
        "Collapsed. Double-tap to customize individual exercises."
    }
    return "$movementSentence $weightSentence $stateSentence"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CompactComplexLogCard(
    blockState: LoadedAvailableBlockState,
    exercises: List<Exercise>,
    repMaxes: List<RepMax>,
    weightUnit: String,
    onRemoveBlock: () -> Unit,
    onLaunchTimer: () -> Unit,
    onOpenPrPicker: () -> Unit,
    onRemoveTrackedPr: (TrackedPrSlot) -> Unit,
    itemNumber: Int? = null,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val view = LocalView.current
    val context = LocalContext.current
    val evaluatedChips = blockState.evaluatePrChips(exercises, repMaxes)
    val headerAccent = if (isDark) ComplexHeaderDark else ComplexHeaderLight
    val cardColor = if (isDark) ComplexCardSurfaceDark else ComplexCardSurfaceLight
    val headerText = buildString {
        if (itemNumber != null) append("$itemNumber  ")
        append("COMPLEX: ${blockState.block.title.ifBlank { "Untitled Complex" }}")
    }

    var showByExerciseView by remember(blockState.uiKey) { mutableStateOf(false) }
    val expandedSetRows = remember(blockState.uiKey) { mutableStateMapOf<Int, Boolean>() }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SessionItemHeaderRow(
                    label = headerText,
                    titleColor = headerAccent,
                    timerContentDesc = "Start timer for ${blockState.block.title}",
                    removeContentDesc = "Remove complex ${blockState.block.title}",
                    onLaunchTimer = onLaunchTimer,
                    onRemove = onRemoveBlock
                )

                if (blockState.block.instructions.isNotBlank()) {
                    ComplexPrescriptionBox(
                        instructions = blockState.block.instructions,
                        isDark = isDark
                    )
                }

                MovementLegendRow(
                    exerciseSections = blockState.exerciseSections,
                    showByExerciseView = showByExerciseView,
                    isDark = isDark,
                    onToggleView = { showByExerciseView = !showByExerciseView }
                )

                if (showByExerciseView) {
                    CompactComplexByExerciseView(
                        blockState = blockState,
                        weightUnit = weightUnit,
                        isDark = isDark
                    )
                } else {
                    val setCount = blockState.complexSetCount
                    val editableIndices = (0 until setCount).filter { idx ->
                        val kind = blockState.resolveSharedSetWeight(idx).kind
                        kind == SharedSetWeightStateKind.LINKED || kind == SharedSetWeightStateKind.MIXED
                    }
                    val focusRequesters = remember(setCount) { List(setCount) { FocusRequester() } }
                    val focusManager = LocalFocusManager.current

                    ComplexSetTableHeader(weightUnit = weightUnit)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (setIdx in 0 until setCount) {
                            val isExpanded = expandedSetRows[setIdx] == true
                            val nextEditableIdx = editableIndices.firstOrNull { it > setIdx }
                            ComplexSetRow(
                                setIdx = setIdx,
                                blockState = blockState,
                                weightUnit = weightUnit,
                                isDark = isDark,
                                isExpanded = isExpanded,
                                canRemoveSet = setCount > 1,
                                focusRequester = focusRequesters[setIdx],
                                imeAction = if (nextEditableIdx != null) ImeAction.Next else ImeAction.Done,
                                onImeNextOrDone = {
                                    if (nextEditableIdx != null) {
                                        runCatching { focusRequesters[nextEditableIdx].requestFocus() }
                                    } else {
                                        focusManager.clearFocus()
                                    }
                                },
                                onToggleExpand = {
                                    expandedSetRows[setIdx] = !isExpanded
                                },
                                onRemoveSet = {
                                    val snapshot = blockState.removeComplexSet(setIdx)
                                    if (snapshot != null) {
                                        expandedSetRows.clear()
                                        onShowUndoSnackbar(
                                            context.getString(R.string.complex_set_removed, setIdx + 1)
                                        ) {
                                            blockState.restoreRemovedComplexSet(snapshot)
                                        }
                                    }
                                },
                                onShowUndoSnackbar = onShowUndoSnackbar
                            )
                        }
                    }

                    ComplexTableFooterRow(
                        lastSessionDateFormatted = if (!blockState.isEditMode && blockState.hasPreFilledHistory) {
                            blockState.lastSessionDateFormatted
                        } else {
                            null
                        },
                        onAddSet = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            blockState.addComplexSet()
                        }
                    )
                }

                TrackedPrChipsFlowSection(
                    blockState = blockState,
                    evaluatedChips = evaluatedChips,
                    weightUnit = weightUnit,
                    isDark = isDark,
                    onOpenPrPicker = onOpenPrPicker,
                    onRemoveTrackedPr = onRemoveTrackedPr
                )
            }
        }
    }
}

@Composable
private fun ComplexPrescriptionBox(
    instructions: String,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) ComplexPrescriptionDark else ComplexPrescriptionLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = instructions,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementLegendRow(
    exerciseSections: List<LoadedAvailableBlockExerciseState>,
    showByExerciseView: Boolean,
    isDark: Boolean,
    onToggleView: () -> Unit
) {
    val legendColor = if (isDark) ComplexLegendDark else ComplexLegendLight
    val spokenLegend = remember(exerciseSections) {
        exerciseSections.mapIndexed { idx, ex -> "${idx + 1} ${ex.exerciseName}" }.joinToString(", ")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = exerciseSections.mapIndexed { idx, ex ->
                "${circledMovementIndex(idx + 1)} ${ex.exerciseName}"
            }.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = legendColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .semantics {
                    contentDescription = "Movements: $spokenLegend"
                }
        )
        TextButton(
            onClick = onToggleView,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Text(
                text = if (showByExerciseView) {
                    stringResource(R.string.complex_view_compact)
                } else {
                    stringResource(R.string.complex_view_by_exercise)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ComplexSetTableHeader(weightUnit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.complex_col_set),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = stringResource(R.string.complex_col_movements),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = stringResource(R.string.complex_col_weight, weightUnit.uppercase()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(94.dp)
        )
        Spacer(Modifier.width(48.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ComplexSetRow(
    setIdx: Int,
    blockState: LoadedAvailableBlockState,
    weightUnit: String,
    isDark: Boolean,
    isExpanded: Boolean,
    canRemoveSet: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeNextOrDone: () -> Unit,
    onToggleExpand: () -> Unit,
    onRemoveSet: () -> Unit,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit
) {
    val setNumber = setIdx + 1
    val summary = blockState.resolveSharedSetWeight(setIdx)
    val fontScale = LocalConfiguration.current.fontScale
    val reflowTwoLines = shouldReflowComplexSetRow(fontScale)
    val rowBg = if (isDark) ComplexRowBgDark else ComplexRowBgLight
    val rowBorder = if (isDark) ComplexRowBorderDark else ComplexRowBorderLight
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBg,
        border = BorderStroke(1.dp, rowBorder),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
            ) {
                val showNamesInPills = shouldShowPillExerciseNames(maxWidth.value.toInt())

                if (reflowTwoLines) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "S$setNumber",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(34.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            SharedSetWeightInput(
                                setIdx = setIdx,
                                blockState = blockState,
                                summary = summary,
                                weightUnit = weightUnit,
                                isDark = isDark,
                                focusRequester = focusRequester,
                                imeAction = imeAction,
                                onFocusGained = {
                                    scope.launch { bringIntoViewRequester.bringIntoView() }
                                },
                                onImeNextOrDone = onImeNextOrDone,
                                onShowUndoSnackbar = onShowUndoSnackbar
                            )
                            RemoveComplexSetButton(
                                setNumber = setNumber,
                                enabled = canRemoveSet,
                                onRemove = onRemoveSet
                            )
                        }
                        MovementStatusStrip(
                            setNumber = setNumber,
                            blockState = blockState,
                            summary = summary,
                            weightUnit = weightUnit,
                            isDark = isDark,
                            isExpanded = isExpanded,
                            showExerciseNames = showNamesInPills,
                            onToggle = onToggleExpand,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "S$setNumber",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp)
                        )
                        MovementStatusStrip(
                            setNumber = setNumber,
                            blockState = blockState,
                            summary = summary,
                            weightUnit = weightUnit,
                            isDark = isDark,
                            isExpanded = isExpanded,
                            showExerciseNames = showNamesInPills,
                            onToggle = onToggleExpand,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(2.dp))
                        SharedSetWeightInput(
                            setIdx = setIdx,
                            blockState = blockState,
                            summary = summary,
                            weightUnit = weightUnit,
                            isDark = isDark,
                            focusRequester = focusRequester,
                            imeAction = imeAction,
                            onFocusGained = {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            },
                            onImeNextOrDone = onImeNextOrDone,
                            onShowUndoSnackbar = onShowUndoSnackbar
                        )
                        RemoveComplexSetButton(
                            setNumber = setNumber,
                            enabled = canRemoveSet,
                            onRemove = onRemoveSet
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SetExerciseOverrideDrawer(
                    setIdx = setIdx,
                    blockState = blockState,
                    summary = summary,
                    weightUnit = weightUnit,
                    isDark = isDark,
                    onShowUndoSnackbar = onShowUndoSnackbar
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementStatusStrip(
    setNumber: Int,
    blockState: LoadedAvailableBlockState,
    summary: SharedSetWeightSummary,
    weightUnit: String,
    isDark: Boolean,
    isExpanded: Boolean,
    showExerciseNames: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val setIdx = setNumber - 1
    val a11yDescription = formatCompactSetAccessibilityLabel(
        setNumber = setNumber,
        blockState = blockState,
        summary = summary,
        weightUnit = weightUnit,
        isExpanded = isExpanded
    )
    val activeBg = if (isDark) ComplexActivePillBgDark else ComplexActivePillBgLight
    val activeText = if (isDark) ComplexActivePillTextDark else ComplexActivePillTextLight
    val skippedBg = if (isDark) ComplexSkippedPillBgDark else ComplexSkippedPillBgLight
    val skippedBorder = if (isDark) ComplexSkippedPillBorderDark else ComplexSkippedPillBorderLight
    val skippedText = if (isDark) ComplexSkippedPillTextDark else ComplexSkippedPillTextLight
    val amberDotColor = if (isDark) ComplexWeightMixedBorderDark else ComplexWeightMixedBorderLight

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            blockState.exerciseSections.forEachIndexed { exIdx, exState ->
                val isSkipped = exState.skippedFlags.getOrElse(setIdx) { false }
                val isDivergent = exIdx in summary.divergentExerciseIndices
                val targetDisplay = if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                    exState.repsInputs.getOrNull(setIdx)
                        ?.takeIf { it.isNotBlank() }
                        ?: exState.perSetReps.getOrNull(setIdx)?.toString()
                        ?: exState.defaultReps.toString()
                } else {
                    exState.metricInputs.getOrNull(setIdx).orEmpty()
                }
                val pillText = formatCompactMovementPillText(
                    oneBasedIndex = exIdx + 1,
                    exerciseName = exState.exerciseName,
                    metric = exState.activeMetric,
                    targetDisplay = targetDisplay,
                    isSkipped = isSkipped,
                    showExerciseName = showExerciseNames
                )

                if (isSkipped) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(skippedBg)
                            .drawBehind {
                                drawRoundRect(
                                    color = skippedBorder,
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                                    ),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = skippedText,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(activeBg)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = pillText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = activeText,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isDivergent) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(amberDotColor)
                                )
                            }
                        }
                    }
                }
            }
        }
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
        )
    }
}

@Composable
private fun SharedSetWeightInput(
    setIdx: Int,
    blockState: LoadedAvailableBlockState,
    summary: SharedSetWeightSummary,
    weightUnit: String,
    isDark: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onFocusGained: () -> Unit,
    onImeNextOrDone: () -> Unit,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var preMixedSnapshot by remember { mutableStateOf<ComplexSetWeightsSnapshot?>(null) }

    val isEnabled = summary.kind == SharedSetWeightStateKind.LINKED ||
        summary.kind == SharedSetWeightStateKind.MIXED

    val bgColor = when (summary.kind) {
        SharedSetWeightStateKind.LINKED -> if (isDark) ComplexWeightLinkedBgDark else ComplexWeightLinkedBgLight
        SharedSetWeightStateKind.MIXED -> if (isDark) ComplexWeightMixedBgDark else ComplexWeightMixedBgLight
        SharedSetWeightStateKind.ALL_SKIPPED,
        SharedSetWeightStateKind.NON_WEIGHTED_ONLY ->
            if (isDark) ComplexSkippedPillBgDark else ComplexSkippedPillBgLight
    }
    val borderColor = when (summary.kind) {
        SharedSetWeightStateKind.LINKED -> if (isDark) ComplexWeightLinkedBorderDark else ComplexWeightLinkedBorderLight
        SharedSetWeightStateKind.MIXED -> if (isDark) ComplexWeightMixedBorderDark else ComplexWeightMixedBorderLight
        SharedSetWeightStateKind.ALL_SKIPPED,
        SharedSetWeightStateKind.NON_WEIGHTED_ONLY ->
            if (isDark) ComplexSkippedPillBorderDark else ComplexSkippedPillBorderLight
    }
    val borderWidth = if (summary.kind == SharedSetWeightStateKind.MIXED) 1.5.dp else 1.dp

    val commitMixedUndoIfNeeded: () -> Unit = {
        val snap = preMixedSnapshot
        if (snap != null) {
            preMixedSnapshot = null
            val currentSummary = blockState.resolveSharedSetWeight(setIdx)
            if (currentSummary.kind == SharedSetWeightStateKind.LINKED &&
                currentSummary.uniformWeightText.isNotBlank()
            ) {
                val after = blockState.exerciseSections.map { it.weights.getOrNull(setIdx).orEmpty() }
                if (after != snap.previousWeightsByExercise) {
                    onShowUndoSnackbar(
                        context.getString(
                            R.string.complex_applied_shared_weight,
                            currentSummary.uniformWeightText,
                            weightUnit,
                            setIdx + 1
                        )
                    ) {
                        blockState.restoreComplexSetWeights(snap)
                    }
                }
            }
        }
    }

    val spokenFieldLabel = when (summary.kind) {
        SharedSetWeightStateKind.ALL_SKIPPED -> "Set ${setIdx + 1}, all movements skipped"
        SharedSetWeightStateKind.NON_WEIGHTED_ONLY -> "Set ${setIdx + 1}, bodyweight or conditioning set"
        SharedSetWeightStateKind.MIXED -> "Set ${setIdx + 1} mixed weights in $weightUnit. Enter weight to link all."
        SharedSetWeightStateKind.LINKED -> "Set ${setIdx + 1} shared weight in $weightUnit"
    }

    Box(
        modifier = Modifier
            .width(94.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = isEnabled) {
                runCatching { focusRequester.requestFocus() }
            }
            .semantics { contentDescription = spokenFieldLabel }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (summary.kind) {
            SharedSetWeightStateKind.ALL_SKIPPED -> {
                Text(
                    text = stringResource(R.string.complex_state_skipped),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) ComplexSkippedPillTextDark else ComplexSkippedPillTextLight,
                    maxLines = 1
                )
            }
            SharedSetWeightStateKind.NON_WEIGHTED_ONLY -> {
                Text(
                    text = stringResource(R.string.complex_state_bw),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) ComplexSkippedPillTextDark else ComplexSkippedPillTextLight,
                    maxLines = 1
                )
            }
            SharedSetWeightStateKind.LINKED,
            SharedSetWeightStateKind.MIXED -> {
                val displayText = if (summary.kind == SharedSetWeightStateKind.MIXED) "" else summary.uniformWeightText
                val mixedColor = if (isDark) ComplexWeightMixedTextDark else ComplexWeightMixedTextLight

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = displayText,
                        onValueChange = { raw ->
                            blockState.updateSharedSetWeight(setIdx, raw)
                        },
                        enabled = isEnabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = imeAction
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                commitMixedUndoIfNeeded()
                                onImeNextOrDone()
                            },
                            onDone = {
                                commitMixedUndoIfNeeded()
                                onImeNextOrDone()
                            }
                        ),
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum"
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                val wasFocused = isFocused
                                isFocused = focusState.isFocused
                                if (focusState.isFocused && !wasFocused) {
                                    if (blockState.resolveSharedSetWeight(setIdx).kind == SharedSetWeightStateKind.MIXED) {
                                        preMixedSnapshot = blockState.captureComplexSetWeights(setIdx)
                                    }
                                    onFocusGained()
                                } else if (!focusState.isFocused && wasFocused) {
                                    commitMixedUndoIfNeeded()
                                }
                            },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (displayText.isEmpty()) {
                                    if (summary.kind == SharedSetWeightStateKind.MIXED) {
                                        Text(
                                            text = stringResource(R.string.complex_state_mixed),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = mixedColor,
                                            maxLines = 1
                                        )
                                    } else {
                                        Text(
                                            text = "—",
                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (summary.kind == SharedSetWeightStateKind.LINKED) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = weightUnit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveComplexSetButton(
    setNumber: Int,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    IconButton(
        onClick = onRemove,
        enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Remove Set $setNumber",
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            },
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SetExerciseOverrideDrawer(
    setIdx: Int,
    blockState: LoadedAvailableBlockState,
    summary: SharedSetWeightSummary,
    weightUnit: String,
    isDark: Boolean,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val drawerBg = if (isDark) ComplexSubDrawerBgDark else ComplexSubDrawerBgLight
    val dividerColor = if (isDark) ComplexRowBorderDark else ComplexRowBorderLight
    val activeSwitchBg = if (isDark) ComplexActiveSwitchBgDark else ComplexActiveSwitchBgLight
    val activeSwitchText = if (isDark) ComplexActiveSwitchTextDark else ComplexActiveSwitchTextLight
    val skipSwitchBg = if (isDark) ComplexSkippedPillBgDark else ComplexSkippedPillBgLight
    val skipSwitchText = if (isDark) ComplexSkippedPillTextDark else ComplexSkippedPillTextLight
    val fieldBg = if (isDark) ComplexRowBgDark else ComplexRowBgLight
    val activeLabel = stringResource(R.string.complex_status_active)
    val skipLabel = stringResource(R.string.complex_status_skip)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(drawerBg)
    ) {
        HorizontalDivider(color = dividerColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (summary.kind == SharedSetWeightStateKind.ALL_SKIPPED) {
                Text(
                    text = stringResource(R.string.complex_all_skipped_hint, setIdx + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            blockState.exerciseSections.forEachIndexed { exIdx, exState ->
                val isSkipped = exState.skippedFlags.getOrElse(setIdx) { false }
                val isDivergent = exIdx in summary.divergentExerciseIndices

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${circledMovementIndex(exIdx + 1)} ${exState.exerciseName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSkipped) skipSwitchBg else activeSwitchBg,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 68.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Switch) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    blockState.toggleComplexExerciseSkipped(exState, setIdx)
                                }
                                .semantics {
                                    stateDescription = if (isSkipped) skipLabel else activeLabel
                                    contentDescription = "${exState.exerciseName} in Set ${setIdx + 1}"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isSkipped) skipLabel else activeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSkipped) skipSwitchText else activeSwitchText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (!isSkipped) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val (targetValue, targetPlaceholder) = compactTargetInputValueAndPlaceholder(exState, setIdx)
                            InlineCompactInputBox(
                                value = targetValue,
                                placeholder = targetPlaceholder,
                                unit = exState.activeMetric.compactUnit,
                                keyboardType = if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                    KeyboardType.Number
                                } else {
                                    KeyboardType.Decimal
                                },
                                backgroundColor = fieldBg,
                                borderColor = dividerColor,
                                spokenLabel = "${exState.exerciseName} Set ${setIdx + 1} ${exState.activeMetric.label}",
                                onValueChange = { raw ->
                                    if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                        blockState.updateSetReps(exState, setIdx, raw)
                                    } else {
                                        blockState.updateSetMetricValue(exState, setIdx, raw)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                val weightBorder = if (isDivergent) {
                                    if (isDark) ComplexWeightMixedBorderDark else ComplexWeightMixedBorderLight
                                } else {
                                    if (isDark) ComplexWeightLinkedBorderDark else ComplexWeightLinkedBorderLight
                                }
                                InlineCompactInputBox(
                                    value = exState.weights.getOrNull(setIdx).orEmpty(),
                                    unit = weightUnit,
                                    keyboardType = KeyboardType.Decimal,
                                    backgroundColor = if (isDivergent) {
                                        if (isDark) ComplexWeightMixedBgDark else ComplexWeightMixedBgLight
                                    } else {
                                        if (isDark) ComplexWeightLinkedBgDark else ComplexWeightLinkedBgLight
                                    },
                                    borderColor = weightBorder,
                                    spokenLabel = "${exState.exerciseName} Set ${setIdx + 1} weight in $weightUnit",
                                    onValueChange = { raw ->
                                        blockState.updateSetWeight(
                                            exState,
                                            setIdx,
                                            sanitizeCompactWeightInput(raw)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (summary.kind == SharedSetWeightStateKind.MIXED && summary.majorityWeightText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val snapshot = blockState.relinkComplexSetWeights(setIdx, summary.majorityWeightText)
                            if (snapshot != null) {
                                onShowUndoSnackbar(
                                    context.getString(
                                        R.string.complex_applied_shared_weight,
                                        summary.majorityWeightText,
                                        weightUnit,
                                        setIdx + 1
                                    )
                                ) {
                                    blockState.restoreComplexSetWeights(snapshot)
                                }
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.complex_relink_all,
                                summary.majorityWeightText,
                                weightUnit
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Editable value + placeholder for a movement's per-set target input. The raw input is
 * edited directly (so clearing the field is possible); the fallback rep count that
 * [LoadedAvailableBlockState.toBlockDrafts] would persist is surfaced as the placeholder.
 */
internal fun compactTargetInputValueAndPlaceholder(
    exState: LoadedAvailableBlockExerciseState,
    setIdx: Int
): Pair<String, String> =
    if (exState.activeMetric == ExerciseTargetMetric.REPS) {
        val raw = exState.repsInputs.getOrNull(setIdx).orEmpty()
        val fallback = (exState.perSetReps.getOrNull(setIdx) ?: exState.defaultReps).coerceAtLeast(1)
        raw to fallback.toString()
    } else {
        exState.metricInputs.getOrNull(setIdx).orEmpty() to "—"
    }

@Composable
private fun InlineCompactInputBox(
    value: String,
    placeholder: String = "—",
    unit: String,
    keyboardType: KeyboardType,
    backgroundColor: Color,
    borderColor: Color,
    spokenLabel: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputFocusRequester = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable {
                runCatching { inputFocusRequester.requestFocus() }
            }
            .semantics { contentDescription = spokenLabel }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum"
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(inputFocusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ComplexTableFooterRow(
    lastSessionDateFormatted: String?,
    onAddSet: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onAddSet,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.complex_action_add_set),
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (!lastSessionDateFormatted.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.complex_prefilled_from, lastSessionDateFormatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun CompactComplexByExerciseView(
    blockState: LoadedAvailableBlockState,
    weightUnit: String,
    isDark: Boolean
) {
    val expandedByEx = remember(blockState.uiKey) { mutableStateMapOf<Int, Boolean>() }
    val view = LocalView.current
    val rowBorder = if (isDark) ComplexRowBorderDark else ComplexRowBorderLight
    val fieldBg = if (isDark) ComplexRowBgDark else ComplexRowBgLight
    val activeSwitchBg = if (isDark) ComplexActiveSwitchBgDark else ComplexActiveSwitchBgLight
    val activeSwitchText = if (isDark) ComplexActiveSwitchTextDark else ComplexActiveSwitchTextLight
    val skipSwitchBg = if (isDark) ComplexSkippedPillBgDark else ComplexSkippedPillBgLight
    val skipSwitchText = if (isDark) ComplexSkippedPillTextDark else ComplexSkippedPillTextLight
    val activeLabel = stringResource(R.string.complex_status_active)
    val skipLabel = stringResource(R.string.complex_status_skip)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blockState.exerciseSections.forEachIndexed { exIdx, exState ->
            val isExExpanded = expandedByEx[exIdx] ?: (exIdx == 0)
            var showMetricMenu by remember(blockState.uiKey, exIdx) { mutableStateOf(false) }
            val setCount = blockState.complexSetCount
            val activeCount = (0 until setCount).count { !exState.skippedFlags.getOrElse(it) { false } }

            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, rowBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { expandedByEx[exIdx] = !isExExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${circledMovementIndex(exIdx + 1)} ${exState.exerciseName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$activeCount/$setCount sets active · ${exState.activeMetric.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showMetricMenu = true }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.complex_unit_label, exState.activeMetric.label),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showMetricMenu,
                                onDismissRequest = { showMetricMenu = false }
                            ) {
                                ExerciseTargetMetric.entries.forEach { metricOption ->
                                    DropdownMenuItem(
                                        text = { Text(metricOption.label) },
                                        onClick = {
                                            blockState.switchExerciseMetric(exState, metricOption)
                                            showMetricMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = if (isExExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!exState.targetBanner.isNullOrBlank()) {
                                Text(
                                    text = exState.targetBanner,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            for (setIdx in 0 until setCount) {
                                val isSkipped = exState.skippedFlags.getOrElse(setIdx) { false }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "S${setIdx + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(30.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSkipped) skipSwitchBg else activeSwitchBg,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 60.dp, minHeight = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(role = Role.Switch) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                blockState.toggleComplexExerciseSkipped(exState, setIdx)
                                            }
                                            .semantics {
                                                stateDescription = if (isSkipped) skipLabel else activeLabel
                                                contentDescription = "${exState.exerciseName} Set ${setIdx + 1}"
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isSkipped) skipLabel else activeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSkipped) skipSwitchText else activeSwitchText
                                            )
                                        }
                                    }
                                    if (!isSkipped) {
                                        val (targetVal, targetPlaceholder) =
                                            compactTargetInputValueAndPlaceholder(exState, setIdx)
                                        InlineCompactInputBox(
                                            value = targetVal,
                                            placeholder = targetPlaceholder,
                                            unit = exState.activeMetric.compactUnit,
                                            keyboardType = if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                                KeyboardType.Number
                                            } else {
                                                KeyboardType.Decimal
                                            },
                                            backgroundColor = fieldBg,
                                            borderColor = rowBorder,
                                            spokenLabel = "${exState.exerciseName} Set ${setIdx + 1} ${exState.activeMetric.label}",
                                            onValueChange = { raw ->
                                                if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                                    blockState.updateSetReps(exState, setIdx, raw)
                                                } else {
                                                    blockState.updateSetMetricValue(exState, setIdx, raw)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                            InlineCompactInputBox(
                                                value = exState.weights.getOrNull(setIdx).orEmpty(),
                                                unit = weightUnit,
                                                keyboardType = KeyboardType.Decimal,
                                                backgroundColor = fieldBg,
                                                borderColor = rowBorder,
                                                spokenLabel = "${exState.exerciseName} Set ${setIdx + 1} weight in $weightUnit",
                                                onValueChange = { raw ->
                                                    blockState.updateSetWeight(
                                                        exState,
                                                        setIdx,
                                                        sanitizeCompactWeightInput(raw)
                                                    )
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
