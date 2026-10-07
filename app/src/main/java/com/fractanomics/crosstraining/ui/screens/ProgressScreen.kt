package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.BlockPerformance
import com.fractanomics.crosstraining.ui.DayPerformance
import com.fractanomics.crosstraining.ui.blockPerformances
import com.fractanomics.crosstraining.ui.byDay
import com.fractanomics.crosstraining.ui.components.ChartPoint
import com.fractanomics.crosstraining.ui.components.ChartSeries
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.LineChart
import com.fractanomics.crosstraining.ui.components.MultiLineChart
import com.fractanomics.crosstraining.ui.components.SectionCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.formatShort
import com.fractanomics.crosstraining.ui.routineBlockPerformances
import com.fractanomics.crosstraining.ui.trimmed
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.fractanomics.crosstraining.data.analytics.DayOfWeekCardio
import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.analytics.PaceForecast
import com.fractanomics.crosstraining.data.analytics.PaceRateResult
import com.fractanomics.crosstraining.data.analytics.PaceTrend
import com.fractanomics.crosstraining.data.analytics.Timeframe
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.components.DailyCheckInBottomSheet
import com.fractanomics.crosstraining.ui.components.DailyCheckInCard
import com.fractanomics.crosstraining.ui.screens.weight.WeightEntryBottomSheet
import com.fractanomics.crosstraining.ui.screens.weight.WeightSummaryCard
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.abs

enum class ProgressMode { BY_EXERCISE, BY_ROUTINE, CYCLE_GOALS, BODY_WEIGHT, FAT_LOSS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routinesWithBlocks by viewModel.routinesWithBlocks.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val cycleGoals by viewModel.cycleGoals.collectAsStateWithLifecycle()
    val repMaxes by viewModel.repMaxes.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val weightEntries by viewModel.weightEntries.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val dailyLogs by viewModel.dailyLogs.collectAsStateWithLifecycle()

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var selectedRoutine by remember { mutableStateOf<Routine?>(null) }
    var selectedCycleGoalCycle by remember { mutableStateOf<Cycle?>(null) }
    var selectedFatLossCycle by remember { mutableStateOf<Cycle?>(null) }
    var showDailyCheckInSheet by remember { mutableStateOf(false) }
    val progressMode by viewModel.progressMode.collectAsStateWithLifecycle()

    // Bottom sheet & Snackbar state for Weight Logging
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showWeightSheet by remember { mutableStateOf(false) }
    var editingWeightEntry by remember { mutableStateOf<WeightEntry?>(null) }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Progress & Goals") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTimer) {
                        Icon(Icons.Filled.Timer, contentDescription = "Quick Timer")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (progressMode == ProgressMode.BODY_WEIGHT || progressMode == ProgressMode.FAT_LOSS) {
                FloatingActionButton(
                    onClick = {
                        editingWeightEntry = null
                        showWeightSheet = true
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Log weight")
                }
            }
        }
    ) { pad ->
        val availableModes = remember(routines.isNotEmpty(), cycles.isNotEmpty()) {
            listOfNotNull(
                ProgressMode.BY_EXERCISE,
                if (routines.isNotEmpty()) ProgressMode.BY_ROUTINE else null,
                if (cycles.isNotEmpty()) ProgressMode.CYCLE_GOALS else null,
                if (cycles.isNotEmpty()) ProgressMode.FAT_LOSS else null,
                ProgressMode.BODY_WEIGHT
            )
        }
        val lazyListState = rememberLazyListState()

        LaunchedEffect(progressMode, availableModes) {
            val idx = availableModes.indexOf(progressMode)
            if (idx >= 0) {
                lazyListState.animateScrollToItem(idx)
            }
        }

        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Filter Chip Row hoisted unconditionally above mode empty states
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableModes) { mode ->
                    FilterChip(
                        selected = progressMode == mode,
                        onClick = { viewModel.setProgressMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ProgressMode.BY_EXERCISE -> "By exercise"
                                    ProgressMode.BY_ROUTINE -> "By routine"
                                    ProgressMode.CYCLE_GOALS -> "Cycle goals"
                                    ProgressMode.FAT_LOSS -> "Fat loss"
                                    ProgressMode.BODY_WEIGHT -> "Body weight"
                                }
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (progressMode) {
                    ProgressMode.BY_ROUTINE -> {
                        RoutineProgress(
                            routines = routines,
                            routinesWithBlocks = routinesWithBlocks,
                            exercises = exercises,
                            sessions = sessions,
                            current = selectedRoutine ?: routines.firstOrNull(),
                            onSelect = { selectedRoutine = it }
                        )
                    }
                    ProgressMode.CYCLE_GOALS -> {
                        CycleGoalsProgress(
                            cycles = cycles,
                            currentCycle = selectedCycleGoalCycle ?: activeCycle ?: cycles.firstOrNull(),
                            cycleGoals = cycleGoals,
                            exercises = exercises,
                            sessions = sessions,
                            repMaxes = repMaxes,
                            weightEntries = weightEntries,
                            weightUnit = weightUnit,
                            onSelectCycle = { selectedCycleGoalCycle = it },
                            onLogWeight = {
                                editingWeightEntry = null
                                showWeightSheet = true
                            }
                        )
                    }
                    ProgressMode.FAT_LOSS -> {
                        val currentFatLossCycle = selectedFatLossCycle
                            ?: activeCycle?.takeIf { it.type == CycleType.FAT_LOSS_BODYBUILDING }
                            ?: cycles.firstOrNull { it.type == CycleType.FAT_LOSS_BODYBUILDING }
                            ?: activeCycle
                            ?: cycles.firstOrNull()

                        FatLossProgress(
                            cycles = cycles,
                            currentCycle = currentFatLossCycle,
                            sessions = sessions,
                            exercises = exercises,
                            dailyLogs = dailyLogs,
                            weightEntries = weightEntries,
                            weightUnit = weightUnit,
                            onToggleWeightUnit = { viewModel.setWeightUnit(it) },
                            onSelectCycle = { selectedFatLossCycle = it },
                            onLogWeight = {
                                editingWeightEntry = null
                                showWeightSheet = true
                            },
                            onSaveDailyLog = { log ->
                                viewModel.saveDailyLog(log)
                            },
                            onOpenCheckInSheet = {
                                showDailyCheckInSheet = true
                            }
                        )
                    }
                    ProgressMode.BODY_WEIGHT -> {
                        WeightOverviewContent(
                            entries = weightEntries,
                            unit = weightUnit,
                            onToggleUnit = { viewModel.setWeightUnit(it) },
                            onEditEntry = { entry ->
                                editingWeightEntry = entry
                                showWeightSheet = true
                            },
                            onDeleteEntry = { entry ->
                                viewModel.deleteWeightEntry(entry.date)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Weight entry deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDeleteWeightEntry(
                                            date = entry.date,
                                            weightKg = entry.weightKg,
                                            notes = entry.notes
                                        )
                                    }
                                }
                            }
                        )
                    }
                    ProgressMode.BY_EXERCISE -> {
                        if (exercises.isEmpty()) {
                            EmptyState("Add exercises and log sessions to see progress here.")
                        } else {
                            ExerciseProgress(
                                exercises = exercises,
                                repMaxes = repMaxes,
                                sessions = sessions,
                                current = selectedExercise ?: exercises.firstOrNull(),
                                onSelect = { selectedExercise = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWeightSheet) {
        val activeSorted = weightEntries.filter { it.deletedAtMillis == null }.sortedBy { it.date }
        val latest = activeSorted.lastOrNull()

        WeightEntryBottomSheet(
            sheetState = sheetState,
            initialEntry = editingWeightEntry,
            latestEntry = latest,
            unit = weightUnit,
            onDismiss = { showWeightSheet = false },
            onSave = { weightKg, date, notes ->
                viewModel.saveWeightEntry(weightKg = weightKg, date = date, notes = notes)
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    showWeightSheet = false
                }
            },
            onDelete = { date ->
                val targetEntry = editingWeightEntry ?: activeSorted.find { it.date == date }
                viewModel.deleteWeightEntry(date)
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    showWeightSheet = false
                }
                if (targetEntry != null) {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Weight entry deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDeleteWeightEntry(
                                date = targetEntry.date,
                                weightKg = targetEntry.weightKg,
                                notes = targetEntry.notes
                            )
                        }
                    }
                }
            }
        )
    }

    if (showDailyCheckInSheet) {
        val today = LocalDate.now()
        val currentFatLossCycle = selectedFatLossCycle
            ?: activeCycle?.takeIf { it.type == CycleType.FAT_LOSS_BODYBUILDING }
            ?: cycles.firstOrNull { it.type == CycleType.FAT_LOSS_BODYBUILDING }
            ?: activeCycle
            ?: cycles.firstOrNull()
        val isFast = currentFatLossCycle?.let {
            FatLossAnalytics.isScheduledFastDay(today, it.fastDaysOfWeek)
        } ?: false
        val isRest = currentFatLossCycle?.let {
            FatLossAnalytics.isScheduledRestDay(today, it.restDaysOfWeek)
        } ?: false

        DailyCheckInBottomSheet(
            date = today,
            dailyLog = dailyLogs.firstOrNull { it.date == today && it.deletedAtMillis == null },
            isScheduledFastDay = isFast,
            isScheduledRestDay = isRest,
            onDismiss = { showDailyCheckInSheet = false },
            onSave = {
                viewModel.saveDailyLog(it)
                showDailyCheckInSheet = false
            },
            onDelete = {
                viewModel.deleteDailyLog(it)
                showDailyCheckInSheet = false
            }
        )
    }
}

// --- Fat Loss & Bodybuilding Progress View ----------------------------------

@Composable
private fun FatLossProgress(
    cycles: List<Cycle>,
    currentCycle: Cycle?,
    sessions: List<SessionWithBlocks>,
    exercises: List<Exercise>,
    dailyLogs: List<DailyLog>,
    weightEntries: List<WeightEntry>,
    weightUnit: String,
    onToggleWeightUnit: (String) -> Unit,
    onSelectCycle: (Cycle) -> Unit,
    onLogWeight: () -> Unit,
    onSaveDailyLog: (DailyLog) -> Unit,
    onOpenCheckInSheet: () -> Unit
) {
    Dropdown(
        label = "Training cycle",
        options = cycles,
        selected = currentCycle,
        labelOf = {
            val typeSuffix = if (it.type == CycleType.FAT_LOSS_BODYBUILDING) " (Fat Loss)" else ""
            if (it.isActive) "${it.name} (Active)$typeSuffix" else "${it.name}$typeSuffix"
        },
        onSelect = onSelectCycle
    )

    if (currentCycle == null) {
        SectionCard(title = "Fat Loss & Bodybuilding") {
            EmptyState("No training cycles found.\nCreate a cycle in the Cycles tab to track fat loss progress.")
        }
        return
    }

    val isFatLossCycle = currentCycle.type == CycleType.FAT_LOSS_BODYBUILDING

    // Cycle Overview & Scheduling Chips
    SectionCard(title = "Cycle Overview (${currentCycle.name})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val startDate = currentCycle.startDate
            val endDate = currentCycle.endDate
            val dateRangeStr = if (endDate != null) {
                "${startDate.formatShort()} – ${endDate.formatShort()}"
            } else {
                "Started ${startDate.formatShort()} (Ongoing)"
            }

            Text(
                text = dateRangeStr,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val typeBadge = if (isFatLossCycle) "Fat Loss & Bodybuilding" else "Strength & Weightlifting"
            Text(
                text = "Cycle Type: $typeBadge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isFatLossCycle) {
                val fastDays = remember(currentCycle.fastDaysOfWeek) {
                    FatLossAnalytics.maskToDayOfWeekSet(currentCycle.fastDaysOfWeek)
                }
                val restDays = remember(currentCycle.restDaysOfWeek) {
                    FatLossAnalytics.maskToDayOfWeekSet(currentCycle.restDaysOfWeek)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fastDaysText = if (fastDays.isNotEmpty()) {
                        fastDays.joinToString { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                    } else {
                        "None"
                    }
                    Text(
                        text = "Fast Days: $fastDaysText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    val restDaysText = if (restDays.isNotEmpty()) {
                        restDays.joinToString { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                    } else {
                        "None"
                    }
                    Text(
                        text = "Rest Days: $restDaysText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    // 1. Daily Check-In Card for Today (if Fat Loss cycle)
    if (isFatLossCycle) {
        val today = LocalDate.now()
        val todayLog = remember(dailyLogs, today) {
            dailyLogs.firstOrNull { it.date == today && it.deletedAtMillis == null }
        }
        val isFast = FatLossAnalytics.isScheduledFastDay(today, currentCycle.fastDaysOfWeek)
        val isRest = FatLossAnalytics.isScheduledRestDay(today, currentCycle.restDaysOfWeek)

        DailyCheckInCard(
            date = today,
            dailyLog = todayLog,
            isScheduledFastDay = isFast,
            isScheduledRestDay = isRest,
            onSaveDailyLog = onSaveDailyLog,
            onOpenFullCheckIn = onOpenCheckInSheet
        )
    }

    // 2. Adherence KPI Cards (Block Completion & Fasting Adherence)
    val cycleSessions = remember(sessions, currentCycle) {
        sessions.filter { s ->
            val date = s.session.date
            val afterStart = !date.isBefore(currentCycle.startDate)
            val beforeEnd = currentCycle.endDate == null || !date.isAfter(currentCycle.endDate)
            val matchesCycle = s.session.cycleId == currentCycle.id
            afterStart && beforeEnd && matchesCycle
        }
    }
    val allCycleBlocks = remember(cycleSessions) {
        cycleSessions.flatMap { it.blocks.map { bws -> bws.block } }
    }
    val blockAdherence = remember(allCycleBlocks) {
        FatLossAnalytics.computeDailyBlockAdherence(allCycleBlocks)
    }

    val dailyLogsMap = remember(dailyLogs) {
        dailyLogs.filter { it.deletedAtMillis == null }.associateBy { it.date }
    }
    val fastingAdherence = remember(currentCycle, dailyLogsMap) {
        FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = currentCycle.startDate,
            fastDaysOfWeek = currentCycle.fastDaysOfWeek,
            dailyLogs = dailyLogsMap,
            referenceDate = LocalDate.now(),
            cycleEndDate = currentCycle.endDate
        )
    }

    SectionCard(title = "Adherence & Completion") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                label = "Block Completion",
                value = blockAdherence.displayText,
                sub = {
                    if (blockAdherence.percentage != null) {
                        SubText("${blockAdherence.completedBlocks} of ${blockAdherence.totalBlocks} completed")
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (blockAdherence.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        SubText("0 blocks logged")
                    }
                }
            )

            KpiCard(
                label = "Fasting Adherence",
                value = fastingAdherence.displayText,
                sub = {
                    if (currentCycle.fastDaysOfWeek == 0) {
                        SubText("No scheduled fast days")
                    } else if (fastingAdherence.percentage != null) {
                        SubText("${fastingAdherence.completedFastDays} of ${fastingAdherence.totalEligibleFastDays} fast days completed")
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (fastingAdherence.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        SubText("0 fast days elapsed")
                    }
                }
            )
        }
    }

    // 3. Cardio Day/Week & Mon–Sun Averages Bar Chart
    val exerciseMap = remember(exercises) { exercises.associateBy { it.id } }
    val dailyCardioMinutes = remember(cycleSessions, exerciseMap) {
        FatLossAnalytics.computeDailyCardioMinutes(cycleSessions, exerciseMap)
    }
    val dayOfWeekCardioList = remember(currentCycle, dailyCardioMinutes) {
        FatLossAnalytics.computeDayOfWeekCardioList(
            cycleStartDate = currentCycle.startDate,
            referenceDate = LocalDate.now(),
            cycleEndDate = currentCycle.endDate,
            dailyCardioMinutes = dailyCardioMinutes
        )
    }
    val totalCardioMinutes = remember(dailyCardioMinutes) {
        dailyCardioMinutes.values.sum()
    }
    val today = LocalDate.now()
    val elapsedDays = remember(currentCycle.startDate, currentCycle.endDate, today) {
        val windowEnd = if (currentCycle.endDate != null && currentCycle.endDate.isBefore(today)) {
            currentCycle.endDate
        } else {
            today
        }
        java.time.temporal.ChronoUnit.DAYS.between(currentCycle.startDate, windowEnd).coerceAtLeast(0) + 1
    }
    val dailyAvgCardio = if (elapsedDays > 0) totalCardioMinutes / elapsedDays else 0.0
    val weeklyAvgCardio = dailyAvgCardio * 7.0

    SectionCard(title = "Cardio Analytics") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    label = "Total Cardio",
                    value = "${totalCardioMinutes.toInt()} min",
                    sub = { SubText("${String.format(Locale.US, "%.1f", totalCardioMinutes / 60.0)} hrs") }
                )
                KpiCard(
                    label = "Daily Average",
                    value = "${dailyAvgCardio.toInt()} min",
                    sub = { SubText("${weeklyAvgCardio.toInt()} min / wk") }
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Mon–Sun Cardio Averages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Average cardio minutes accumulated per weekday across cycle weeks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CardioDayOfWeekBarChart(dayOfWeekCardioList)
        }
    }

    // 4. Integrated Weight Trend
    val activeWeightEntries = remember(weightEntries) {
        weightEntries.filter { it.deletedAtMillis == null }.sortedBy { it.date }
    }
    val cycleWeightEntries = remember(activeWeightEntries, currentCycle) {
        activeWeightEntries.filter { entry ->
            val afterStart = !entry.date.isBefore(currentCycle.startDate)
            val beforeEnd = currentCycle.endDate == null || !entry.date.isAfter(currentCycle.endDate)
            afterStart && beforeEnd
        }
    }
    var selectedTimeframe by rememberSaveable { mutableStateOf(Timeframe.THIRTY_DAYS) }

    SectionCard(title = "Integrated Weight Trend") {
        if (activeWeightEntries.isEmpty()) {
            EmptyState("No weight entries logged yet.\nTap 'Log Weigh-In' below to start tracking your weight.")
        } else {
            val isImperial = weightUnit.equals("lbs", ignoreCase = true)
            val startEntry = cycleWeightEntries.firstOrNull() ?: activeWeightEntries.firstOrNull()
            val latestEntry = activeWeightEntries.lastOrNull()

            val startDisplay = startEntry?.let { if (isImperial) WeightAnalytics.kgToLbs(it.weightKg) else it.weightKg }
            val latestDisplay = latestEntry?.let { if (isImperial) WeightAnalytics.kgToLbs(it.weightKg) else it.weightKg }
            val deltaDisplay = if (startDisplay != null && latestDisplay != null) latestDisplay - startDisplay else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    label = "Cycle Start",
                    value = startDisplay?.let { "${String.format(Locale.US, "%.1f", it)} ${weightUnit.lowercase()}" } ?: "—",
                    sub = { SubText(startEntry?.date?.formatShort() ?: "No start log") }
                )
                KpiCard(
                    label = "Latest",
                    value = latestDisplay?.let { "${String.format(Locale.US, "%.1f", it)} ${weightUnit.lowercase()}" } ?: "—",
                    sub = { SubText(latestEntry?.date?.formatShort() ?: "No weigh-in") }
                )
                KpiCard(
                    label = "Cycle Delta",
                    value = deltaDisplay?.let {
                        val sign = if (it > 0) "+" else ""
                        "$sign${String.format(Locale.US, "%.1f", it)} ${weightUnit.lowercase()}"
                    } ?: "—",
                    sub = {
                        when {
                            deltaDisplay == null -> SubText("baseline")
                            deltaDisplay < 0.0 -> Text(
                                "▼ ${String.format(Locale.US, "%.1f", abs(deltaDisplay))} lost",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            deltaDisplay > 0.0 -> Text(
                                "▲ +${String.format(Locale.US, "%.1f", deltaDisplay)} gained",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> SubText("= no change")
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Timeframe Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = selectedTimeframe == tf,
                        onClick = { selectedTimeframe = tf },
                        label = { Text(tf.label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Weight Chart Series
            val chartPoints = remember(activeWeightEntries, selectedTimeframe, isImperial) {
                val seriesPoints = WeightAnalytics.prepareChartSeries(activeWeightEntries, selectedTimeframe)
                val rawChart = seriesPoints.map { p ->
                    val displayVal = if (isImperial) WeightAnalytics.kgToLbs(p.rawValue) else p.rawValue
                    ChartPoint(p.label, displayVal.toFloat())
                }
                val smaChart = seriesPoints.map { p ->
                    val displayVal = p.smaValue?.let {
                        if (isImperial) WeightAnalytics.kgToLbs(it) else it
                    }
                    ChartPoint(p.label, displayVal?.toFloat())
                }
                Pair(rawChart, smaChart)
            }

            val chartSeries = listOf(
                ChartSeries(
                    name = "Daily (${weightUnit.lowercase()})",
                    points = chartPoints.first,
                    color = MaterialTheme.colorScheme.secondary
                ),
                ChartSeries(
                    name = "7D Average",
                    points = chartPoints.second,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            MultiLineChart(series = chartSeries)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                val nextUnit = if (weightUnit.equals("kg", ignoreCase = true)) "lbs" else "kg"
                onToggleWeightUnit(nextUnit)
            }) {
                Text("Unit: ${weightUnit.uppercase()}")
            }
            androidx.compose.material3.OutlinedButton(onClick = onLogWeight) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log Weigh-In")
            }
        }
    }
}

/**
 * Bar chart rendering Day-of-Week cardio averages (Monday through Sunday) across cycle weeks.
 */
@Composable
private fun CardioDayOfWeekBarChart(
    items: List<DayOfWeekCardio>,
    modifier: Modifier = Modifier
) {
    val maxAvg = (items.maxOfOrNull { it.averageMinutes } ?: 0.0).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { dayCardio ->
                val dayLabel = when (dayCardio.dayOfWeek) {
                    DayOfWeek.MONDAY -> "Mon"
                    DayOfWeek.TUESDAY -> "Tue"
                    DayOfWeek.WEDNESDAY -> "Wed"
                    DayOfWeek.THURSDAY -> "Thu"
                    DayOfWeek.FRIDAY -> "Fri"
                    DayOfWeek.SATURDAY -> "Sat"
                    DayOfWeek.SUNDAY -> "Sun"
                }
                val avg = dayCardio.averageMinutes
                val heightFraction = if (maxAvg > 0) (avg / maxAvg).toFloat().coerceIn(0f, 1f) else 0f
                val valueText = if (avg > 0.0) {
                    if (avg >= 10.0) "${avg.toInt()}m" else String.format(Locale.US, "%.1fm", avg)
                } else {
                    "—"
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (avg > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (avg > 0.0) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height((80 * heightFraction).coerceAtLeast(if (avg > 0.0) 6f else 2f).dp)
                            .background(
                                color = if (avg > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp))
    }
}

// --- Body Weight Overview View ----------------------------------------------

@Composable
private fun WeightOverviewContent(
    entries: List<WeightEntry>,
    unit: String,
    onToggleUnit: (String) -> Unit,
    onEditEntry: (WeightEntry) -> Unit,
    onDeleteEntry: (WeightEntry) -> Unit
) {
    val activeEntries = entries.filter { it.deletedAtMillis == null }.sortedBy { it.date }
    var selectedTimeframe by rememberSaveable { mutableStateOf(Timeframe.THIRTY_DAYS) }

    WeightSummaryCard(
        entries = activeEntries,
        unit = unit,
        onToggleUnit = onToggleUnit
    )

    if (activeEntries.isEmpty()) {
        EmptyState("No weight entries logged yet.\nTap the '+' button below to log your first weigh-in.")
        return
    }

    // Timeframe Filter Chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Timeframe.entries.forEach { tf ->
            FilterChip(
                selected = selectedTimeframe == tf,
                onClick = { selectedTimeframe = tf },
                label = { Text(tf.label) }
            )
        }
    }

    // Weight Chart Section
    val isImperial = unit.equals("lbs", ignoreCase = true)
    val chartPoints = remember(activeEntries, selectedTimeframe, isImperial) {
        val seriesPoints = WeightAnalytics.prepareChartSeries(activeEntries, selectedTimeframe)
        val rawChart = seriesPoints.map { p ->
            val displayVal = if (isImperial) WeightAnalytics.kgToLbs(p.rawValue) else p.rawValue
            ChartPoint(p.label, displayVal.toFloat())
        }
        val smaChart = seriesPoints.map { p ->
            val displayVal = p.smaValue?.let {
                if (isImperial) WeightAnalytics.kgToLbs(it) else it
            }
            ChartPoint(p.label, displayVal?.toFloat())
        }
        Pair(rawChart, smaChart)
    }

    val chartSeries = listOf(
        ChartSeries(
            name = "Daily (${unit.lowercase()})",
            points = chartPoints.first,
            color = MaterialTheme.colorScheme.secondary
        ),
        ChartSeries(
            name = "7D Average",
            points = chartPoints.second,
            color = MaterialTheme.colorScheme.primary
        )
    )

    SectionCard(title = "Weight Trend (${selectedTimeframe.label})") {
        MultiLineChart(series = chartSeries)
    }

    // Weight History List (Chronological descending)
    SectionCard(title = "Weight History") {
        val descending = remember(activeEntries) { activeEntries.sortedByDescending { it.date } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            descending.forEach { entry ->
                val displayWeight = if (isImperial) WeightAnalytics.kgToLbs(entry.weightKg) else entry.weightKg
                val weightStr = String.format(java.util.Locale.getDefault(), "%.1f", displayWeight)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditEntry(entry) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = entry.date.formatLong(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (entry.notes.isNotBlank()) {
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$weightStr ${unit.lowercase()}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { onDeleteEntry(entry) },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

// --- Weight Progression View ---------------------------------------------------

@Composable
fun WeightProgressionCard(
    cycle: Cycle,
    weightEntries: List<WeightEntry>,
    weightUnit: String,
    onLogWeight: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    if (cycle.type != CycleType.FAT_LOSS_BODYBUILDING) return

    val isImperial = weightUnit.equals("lbs", ignoreCase = true)
    val unitLabel = if (isImperial) "lbs" else "kg"

    val activeEntries = remember(weightEntries, cycle.startDate, cycle.endDate, today) {
        weightEntries
            .filter { it.deletedAtMillis == null && !it.date.isBefore(cycle.startDate) && !it.date.isAfter(today) }
            .sortedBy { it.date }
    }

    if (activeEntries.isEmpty()) {
        SectionCard(
            title = "Weight Progression (${cycle.name})",
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No in-cycle weight entries logged yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Check in to track your pace and goal forecast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onLogWeight) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Log weight",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log weight")
                }
            }
        }
        return
    }

    val startingKg = cycle.startingWeightKg ?: activeEntries.firstOrNull()?.weightKg
    val latestEntry = activeEntries.lastOrNull()
    val current7DayAvgKg = remember(weightEntries, activeEntries, cycle, today) {
        FatLossAnalytics.computeCurrent7DayAverageWeight(
            entries = weightEntries,
            cycle = cycle,
            referenceDate = latestEntry?.date ?: today
        )
    }
    val currentKg = current7DayAvgKg ?: latestEntry?.weightKg
    val targetKg = cycle.targetWeightKg ?: cycle.targetBodyWeightKg

    val startingDisplay = startingKg?.let { if (isImperial) WeightAnalytics.kgToLbs(it) else it }
    val currentDisplay = currentKg?.let { if (isImperial) WeightAnalytics.kgToLbs(it) else it }
    val targetDisplay = targetKg?.let { if (isImperial) WeightAnalytics.kgToLbs(it) else it }

    val startSubtitle = if (cycle.startingWeightKg != null) cycle.startDate.formatShort() else (activeEntries.firstOrNull()?.date?.formatShort() ?: "No log")

    val pace7 = remember(activeEntries, cycle, today) {
        FatLossAnalytics.compute7DayPace(entries = weightEntries, cycle = cycle, today = today)
    }
    val pace14 = remember(activeEntries, cycle, today) {
        FatLossAnalytics.compute14DayPace(entries = weightEntries, cycle = cycle, today = today)
    }

    val forecast = remember(activeEntries, cycle, today) {
        FatLossAnalytics.computePaceForecast(
            entries = weightEntries,
            cycle = cycle,
            today = today
        )
    }

    SectionCard(
        title = "Weight Progression (${cycle.name})",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header KPIs: Starting, Current, Goal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    label = "Starting",
                    value = startingDisplay?.let { "${String.format(Locale.US, "%.1f", it)} $unitLabel" } ?: "—",
                    valueStyle = MaterialTheme.typography.titleMedium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    sub = { SubText(startSubtitle) }
                )
                KpiCard(
                    label = "Current",
                    value = currentDisplay?.let { "${String.format(Locale.US, "%.1f", it)} $unitLabel" } ?: "—",
                    valueStyle = MaterialTheme.typography.titleMedium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    sub = { SubText("7d avg • ${latestEntry?.date?.formatShort() ?: "—"}") }
                )
                KpiCard(
                    label = "Goal",
                    value = targetDisplay?.let { "${String.format(Locale.US, "%.1f", it)} $unitLabel" } ?: "—",
                    valueStyle = MaterialTheme.typography.titleMedium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    sub = {
                        if (targetDisplay != null && currentDisplay != null) {
                            val remaining = currentDisplay - targetDisplay
                            if (remaining > 0.05) {
                                SubText("${String.format(Locale.US, "%.1f", remaining)} $unitLabel left")
                            } else {
                                Text(
                                    "Achieved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        } else {
                            SubText(if (targetDisplay == null) "No target" else "")
                        }
                    }
                )
            }

            // Side-by-side Pace Chips: 7-Day & 14-Day
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaceChip(
                    title = "7-Day Pace",
                    paceResult = pace7,
                    unit = weightUnit,
                    modifier = Modifier.weight(1f)
                )
                PaceChip(
                    title = "14-Day Pace",
                    paceResult = pace14,
                    unit = weightUnit,
                    modifier = Modifier.weight(1f)
                )
            }

            // Respective Forecast Banner
            if (forecast != null) {
                ForecastBanner(forecast = forecast)
            }

            // Check-in action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onLogWeight) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Log weigh-in")
                }
            }
        }
    }
}

@Composable
private fun PaceChip(
    title: String,
    paceResult: PaceRateResult?,
    unit: String,
    modifier: Modifier = Modifier
) {
    val trend = paceResult?.trend
    val icon = when (trend) {
        PaceTrend.LOSS -> "▼"
        PaceTrend.GAIN -> "▲"
        PaceTrend.NEUTRAL -> "—"
        null -> null
    }
    val trendColor = when (trend) {
        PaceTrend.LOSS -> MaterialTheme.colorScheme.tertiary
        PaceTrend.GAIN -> MaterialTheme.colorScheme.error
        PaceTrend.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
                Text(
                    text = paceResult?.formatDisplay(unit) ?: "Not enough data yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (paceResult != null) trendColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForecastBanner(
    forecast: PaceForecast,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (forecast) {
        is PaceForecast.GoalReached -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "🎉"
        )
        is PaceForecast.CycleEnded -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "🏁"
        )
        is PaceForecast.NotEnoughData -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "ℹ️"
        )
        is PaceForecast.Stalled -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠️"
        )
        is PaceForecast.Projected -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "🎯"
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = forecast.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                if (forecast is PaceForecast.Projected && forecast.targetDate != null) {
                    Text(
                        text = "Projected arrival: ${forecast.targetDate.formatLong()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// --- Cycle Goals View --------------------------------------------------------

@Composable
private fun CycleGoalsProgress(
    cycles: List<Cycle>,
    currentCycle: Cycle?,
    cycleGoals: List<CycleGoal>,
    exercises: List<Exercise>,
    sessions: List<SessionWithBlocks>,
    repMaxes: List<RepMax>,
    weightEntries: List<WeightEntry> = emptyList(),
    weightUnit: String = "kg",
    onSelectCycle: (Cycle) -> Unit,
    onLogWeight: () -> Unit = {}
) {
    Dropdown(
        label = "Training cycle",
        options = cycles,
        selected = currentCycle,
        labelOf = { if (it.isActive) "${it.name} (Active)" else it.name },
        onSelect = onSelectCycle
    )

    if (currentCycle == null) return

    if (currentCycle.type == CycleType.FAT_LOSS_BODYBUILDING) {
        WeightProgressionCard(
            cycle = currentCycle,
            weightEntries = weightEntries,
            weightUnit = weightUnit,
            onLogWeight = onLogWeight
        )
    }

    val goals = remember(cycleGoals, currentCycle.id) {
        cycleGoals.filter { it.cycleId == currentCycle.id }
    }

    if (goals.isEmpty()) {
        SectionCard(title = "Cycle Goals Target Tracking") {
            EmptyState("No basic movement goals defined for ${currentCycle.name} yet.\nAdd goals in the Cycles tab to track progress against your targets.")
        }
        return
    }

    SectionCard(title = "Cycle Goals Target Tracking (${currentCycle.name})") {
        val startDate = currentCycle.startDate
        val endDate = currentCycle.endDate
        val today = LocalDate.now()

        val weeksTotal = if (endDate != null) ChronoUnit.WEEKS.between(startDate, endDate).coerceAtLeast(1) else 8L
        val weeksElapsed = ChronoUnit.WEEKS.between(startDate, today).coerceIn(0L, weeksTotal)
        val weeksRemaining = (weeksTotal - weeksElapsed).coerceAtLeast(0L)

        Text(
            "Cycle timeframe: ${startDate.formatShort()} → ${endDate?.formatShort() ?: "open"} (${weeksRemaining} weeks remaining)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        goals.forEach { goal ->
            val exercise = exercises.firstOrNull { it.id == goal.exerciseId }
            val exName = exercise?.name ?: "Basic Movement"
            val unit = exercise?.unit ?: "kg"

            // 1. Prior Rep Max before or at start date of cycle
            val priorRm = repMaxes.filter {
                it.exerciseId == goal.exerciseId &&
                it.reps == goal.targetReps &&
                it.date <= currentCycle.startDate
            }.maxByOrNull { it.date }?.weight

            // 2. Cycle sessions in chronological order
            val cycleSessions = sessions.filter {
                it.session.date >= currentCycle.startDate &&
                (currentCycle.endDate == null || it.session.date <= currentCycle.endDate)
            }.sortedBy { it.session.date }

            val firstLoggedWeightInCycle = cycleSessions.flatMap { swb ->
                swb.blocks.filter { it.block.mainExerciseId == goal.exerciseId }.flatMap { b ->
                    b.sets.filter { !it.isWarmup && !it.isFailed && it.reps == goal.targetReps }
                        .mapNotNull { it.weight ?: it.metricValue }
                }
            }.firstOrNull()

            val baselineStartWeight = priorRm ?: firstLoggedWeightInCycle ?: 0.0

            val cycleRms = repMaxes.filter {
                it.exerciseId == goal.exerciseId &&
                it.reps == goal.targetReps &&
                it.date >= currentCycle.startDate &&
                (currentCycle.endDate == null || it.date <= currentCycle.endDate)
            }

            val loggedRmBest = cycleRms.maxOfOrNull { it.weight }
            val blockWorkingBest = cycleSessions.flatMap { swb ->
                swb.blocks.filter { it.block.mainExerciseId == goal.exerciseId }.flatMap { b ->
                    b.sets.filter { !it.isWarmup && !it.isFailed && it.reps == goal.targetReps }
                        .mapNotNull { it.weight ?: it.metricValue }
                }
            }.maxOrNull()

            val currentBest = maxOf(baselineStartWeight, loggedRmBest ?: 0.0, blockWorkingBest ?: 0.0)

            val totalSpan = (goal.targetWeight - baselineStartWeight).coerceAtLeast(0.1)
            val achievedGain = (currentBest - baselineStartWeight).coerceAtLeast(0.0)
            val remainingGap = (goal.targetWeight - currentBest).coerceAtLeast(0.0)
            val progressFraction = (achievedGain / totalSpan).coerceIn(0.0, 1.0).toFloat()
            val progressPercent = (progressFraction * 100).toInt()

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$exName (${goal.targetReps}RM Goal)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (progressPercent >= 100) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (progressPercent >= 100) "Goal Reached! 🎉" else "$progressPercent% Reached",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (progressPercent >= 100) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Target line stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (baselineStartWeight > 0.0) "Start: ${baselineStartWeight.trimmed()} $unit" else "Start: --",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Current Best: ${if (currentBest > 0.0) "${currentBest.trimmed()} $unit" else "--"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Goal: ${goal.targetWeight.trimmed()} $unit", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    // Gap Analysis & Action Recommendations
                    val weeklyReqRate = if (weeksRemaining > 0 && remainingGap > 0) remainingGap / weeksRemaining else 0.0
                    val recText = when {
                        remainingGap <= 0.0 -> "Congratulations! Goal achieved in this cycle."
                        weeklyReqRate > 0.0 -> "Remaining gap: ${remainingGap.trimmed()} $unit. Target weekly progression: +${weeklyReqRate.trimmed()} $unit/week."
                        else -> "Remaining gap: ${remainingGap.trimmed()} $unit to reach goal."
                    }

                    Text(
                        recText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Exercise view ------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgress(
    exercises: List<Exercise>,
    repMaxes: List<RepMax>,
    sessions: List<SessionWithBlocks>,
    current: Exercise?,
    onSelect: (Exercise) -> Unit
) {
    Dropdown(
        label = "Exercise",
        options = exercises,
        selected = current,
        labelOf = { it.name },
        onSelect = onSelect
    )

    if (current == null) return

    val allBlocks = remember(sessions, current.id) {
        sessions.blockPerformances(current.id)
    }

    // --- Block filter (compare repeated blocks like-for-like) ------------------
    var blockFilter by remember(current.id) { mutableStateOf<String?>(null) }
    val namedBlocks = allBlocks.mapNotNull { it.block.name.ifBlank { null } }.distinct()
    val showFilter = namedBlocks.size >= 2 ||
        (namedBlocks.size == 1 && allBlocks.any { it.block.name.isBlank() })
    if (showFilter) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = blockFilter == null,
                onClick = { blockFilter = null },
                label = { Text("All blocks") }
            )
            namedBlocks.forEach { name ->
                FilterChip(
                    selected = blockFilter == name,
                    onClick = { blockFilter = if (blockFilter == name) null else name },
                    label = { Text(name) }
                )
            }
        }
    }
    val blocks =
        if (blockFilter == null) allBlocks
        else allBlocks.filter { it.block.name == blockFilter }
    val days = blocks.byDay()

    KpiRows(blocks, days, current.unit)
    EvolutionCards(days, current.unit)

    // --- Rep-max progression ----------------------------------------------------
    if (current.tracksRepMax) {
        val rmSeries = repMaxes.filter { it.exerciseId == current.id }
        val repOptions = rmSeries.map { it.reps }.distinct().sorted()
        if (repOptions.isNotEmpty()) {
            var selectedReps by remember(current.id) { mutableStateOf<Int?>(null) }
            val reps = selectedReps ?: if (repOptions.contains(1)) 1 else repOptions.first()
            SectionCard(title = "Rep-max progression") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repOptions.forEach { r ->
                        FilterChip(
                            selected = r == reps,
                            onClick = { selectedReps = r },
                            label = { Text("${r}RM") }
                        )
                    }
                }
                val series = rmSeries.filter { it.reps == reps }
                    .sortedBy { it.date }
                    .map { ChartPoint(it.date.formatShort(), it.weight.toFloat()) }
                LineChart(points = series)
            }

            SectionCard(title = "Personal records") {
                TableHeader("RM", "Weight", "", "Date")
                val bestByReps = rmSeries.groupBy { it.reps }
                    .mapValues { (_, list) -> list.maxBy { it.weight } }
                    .toSortedMap()
                bestByReps.forEach { (r, rm) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("${r}RM", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${rm.weight.trimmed()} kg",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("", Modifier.weight(1f))
                        Text(
                            rm.date.formatShort(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }

    BlockHistoryCard(
        blocks = blocks,
        unit = current.unit,
        selectionKey = "exercise-${current.id}",
        emptyMessage = "No sessions logged for this exercise yet."
    )
}

// --- Routine view ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineProgress(
    routines: List<Routine>,
    routinesWithBlocks: List<RoutineWithBlocks>,
    exercises: List<Exercise>,
    sessions: List<SessionWithBlocks>,
    current: Routine?,
    onSelect: (Routine) -> Unit
) {
    Dropdown(
        label = "Routine / complex",
        options = routines,
        selected = current,
        labelOf = { it.name },
        onSelect = onSelect
    )

    if (current == null) return

    val target = exercises.firstOrNull { it.id == current.mainExerciseId }
    val meta = listOfNotNull(
        target?.let { "Improves: ${it.name}" },
        current.defaultFormat.ifBlank { null }
    ).joinToString(" · ")
    if (meta.isNotBlank()) {
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val allRoutineBlocks = remember(sessions, current.id) {
        sessions.routineBlockPerformances(current.id, current)
    }

    val currentRwb = routinesWithBlocks.firstOrNull { it.routine.id == current.id }
    val definedBlocks = currentRwb?.blocks?.sortedBy { it.position } ?: emptyList()

    fun matchesRoutineBlock(pb: BlockPerformance, rBlk: RoutineBlock, totalDefined: Int): Boolean {
        val bName = rBlk.name.ifBlank { rBlk.kind.label }
        val pbName = pb.block.name.ifBlank { pb.block.kind.label }
        val isSupersetKindMatch = pb.block.kind == BlockKind.SUPERSET && rBlk.kind == BlockKind.SUPERSET
        return pbName.equals(bName, ignoreCase = true) ||
            (rBlk.name.isNotBlank() && pb.block.name.equals(rBlk.name, ignoreCase = true)) ||
            (rBlk.targetRepsScheme.isNotBlank() && pb.block.scheme.equals(rBlk.targetRepsScheme, ignoreCase = true)) ||
            (isSupersetKindMatch && (pb.block.name.isBlank() || rBlk.name.isBlank())) ||
            (pb.block.kind == rBlk.kind && totalDefined == 1)
    }

    // --- Block Filter Chips ---------------------------------------------------
    var selectedBlockFilter by remember(current.id) { mutableStateOf<String?>(null) }

    if (definedBlocks.isNotEmpty()) {
        Text(
            "Filter Performance by Block:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedBlockFilter == null,
                onClick = { selectedBlockFilter = null },
                label = { Text("All Blocks (Global)") }
            )
            definedBlocks.forEach { rBlk ->
                val bName = rBlk.name.ifBlank { rBlk.kind.label }
                val matchCount = allRoutineBlocks.count { matchesRoutineBlock(it, rBlk, definedBlocks.size) }
                FilterChip(
                    selected = selectedBlockFilter == bName,
                    onClick = { selectedBlockFilter = if (selectedBlockFilter == bName) null else bName },
                    label = { Text("$bName ($matchCount done)") }
                )
            }
        }
    }

    val activeBlocks = if (selectedBlockFilter == null) {
        allRoutineBlocks
    } else {
        val targetRBlk = definedBlocks.firstOrNull { (it.name.ifBlank { it.kind.label }) == selectedBlockFilter }
        if (targetRBlk != null) {
            allRoutineBlocks.filter { pb -> matchesRoutineBlock(pb, targetRBlk, definedBlocks.size) }
        } else {
            allRoutineBlocks.filter { pb ->
                val pbName = pb.block.name.ifBlank { pb.block.kind.label }
                pbName.equals(selectedBlockFilter, ignoreCase = true)
            }
        }
    }

    val days = activeBlocks.byDay()
    val unit = target?.unit ?: "kg"

    KpiRows(activeBlocks, days, unit)
    EvolutionCards(days, unit, titlePrefix = selectedBlockFilter ?: "All Blocks")

    // --- Per-Block Routine Breakdown Card --------------------------------------
    if (definedBlocks.isNotEmpty()) {
        SectionCard(title = "Per-Block Routine Breakdown") {
            val totalRoutineSessions = allRoutineBlocks.map { it.sessionId }.distinct().size
            if (totalRoutineSessions > 0) {
                Text(
                    "Breakdown of defined blocks executed in $totalRoutineSessions routine sessions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            definedBlocks.forEach { rBlk ->
                val bName = rBlk.name.ifBlank { rBlk.kind.label }
                val blockPerfList = allRoutineBlocks.filter { matchesRoutineBlock(it, rBlk, definedBlocks.size) }
                val performedTimes = blockPerfList.map { it.sessionId }.distinct().size
                val skippedTimes = (totalRoutineSessions - performedTimes).coerceAtLeast(0)

                val bestTop = blockPerfList.mapNotNull { it.top }.maxOrNull()
                val avgTop = blockPerfList.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()
                val totalVol = blockPerfList.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.sum()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val badgeText = when {
                            totalRoutineSessions == 0 -> "Not logged yet"
                            skippedTimes > 0 -> "$performedTimes/$totalRoutineSessions done · $skippedTimes skipped"
                            else -> "$performedTimes/$totalRoutineSessions done"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (performedTimes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    if (performedTimes > 0) {
                        val statParts = listOfNotNull(
                            bestTop?.let { "Best Top: ${it.trimmed()} $unit" },
                            avgTop?.let { "Avg: ${it.trimmed()} $unit" },
                            totalVol?.let { "Vol: ${it.trimmed()} kg" }
                        ).joinToString("  ·  ")
                        Text(
                            text = statParts.ifBlank { "Executed with recorded reps" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Skipped or omitted in routine sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    BlockHistoryCard(
        blocks = activeBlocks,
        unit = unit,
        selectionKey = "routine-${current.id}-${selectedBlockFilter ?: "all"}",
        emptyMessage = "No logged blocks match this selection yet."
    )
}

// --- Shared sections ------------------------------------------------------------

/** The four KPI stat cards with deltas vs the previous training day. */
@Composable
private fun KpiRows(blocks: List<BlockPerformance>, days: List<DayPerformance>, unit: String) {
    val latest = days.lastOrNull() ?: return
    val previous = days.getOrNull(days.size - 2)
    val bestBlock = blocks.filter { it.top != null }.maxByOrNull { it.top!! }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard(
            label = "All-time top",
            value = bestBlock?.top?.let { "${it.trimmed()} $unit" } ?: "—",
            sub = { SubText(bestBlock?.date?.formatShort() ?: "") }
        )
        KpiCard(
            label = "Last top",
            value = "${latest.top.trimmed()} $unit",
            sub = { DeltaText(previous?.let { latest.top - it.top }, unit) }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard(
            label = "Last average",
            value = "${latest.average.trimmed()} $unit",
            sub = { DeltaText(previous?.let { latest.average - it.average }, unit) }
        )
        KpiCard(
            label = "Last volume",
            value = latest.volume?.let { "${it.trimmed()} kg" } ?: "—",
            sub = {
                DeltaText(
                    latest.volume?.let { v -> previous?.volume?.let { v - it } },
                    "kg"
                )
            }
        )
    }
}

/** Top & average evolution chart plus the volume-per-session chart. */
@Composable
private fun EvolutionCards(days: List<DayPerformance>, unit: String, titlePrefix: String = "") {
    val chartTitle = if (titlePrefix.isNotBlank()) "Evolution: $titlePrefix ($unit)" else "Evolution per session ($unit)"
    if (days.isNotEmpty()) {
        SectionCard(title = chartTitle) {
            MultiLineChart(
                series = listOf(
                    ChartSeries(
                        name = "Top",
                        points = days.map { ChartPoint(it.date.formatShort(), it.top.toFloat()) },
                        color = MaterialTheme.colorScheme.primary
                    ),
                    ChartSeries(
                        name = "Average",
                        points = days.map { ChartPoint(it.date.formatShort(), it.average.toFloat()) },
                        color = MaterialTheme.colorScheme.tertiary
                    )
                )
            )
        }
    }

    val volumePoints = days.mapNotNull { d ->
        d.volume?.let { ChartPoint(d.date.formatShort(), it.toFloat()) }
    }
    if (volumePoints.size >= 2) {
        SectionCard(title = "Volume per session (kg)") {
            LineChart(points = volumePoints, lineColor = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Expandable table of every block occurrence, newest first. */
@Composable
private fun BlockHistoryCard(
    blocks: List<BlockPerformance>,
    unit: String,
    selectionKey: String,
    emptyMessage: String
) {
    SectionCard(title = "Block history") {
        if (blocks.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Tap a row to see every set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TableHeader("Date", "Block", "Avg", "Top")
            HorizontalDivider()
            var expandedKey by remember(selectionKey) { mutableStateOf<Long?>(null) }
            blocks.asReversed().forEach { bp ->
                BlockHistoryRow(
                    bp = bp,
                    unit = unit,
                    expanded = expandedKey == bp.block.id,
                    onToggle = {
                        expandedKey = if (expandedKey == bp.block.id) null else bp.block.id
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

// --- Small building blocks --------------------------------------------------

@Composable
private fun RowScope.KpiCard(
    label: String,
    value: String,
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    sub: @Composable () -> Unit
) {
    OutlinedCard(modifier = Modifier.weight(1f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            sub()
        }
    }
}

@Composable
private fun SubText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Signed change vs the previous session, colored by direction. */
@Composable
private fun DeltaText(delta: Double?, unit: String) {
    when {
        delta == null -> SubText("first session")
        delta > 0.0 -> Text(
            "▲ +${delta.trimmed()} $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        delta < 0.0 -> Text(
            "▼ ${delta.trimmed()} $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        else -> SubText("= no change")
    }
}

@Composable
private fun TableHeader(c1: String, c2: String, c3: String, c4: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(c1, Modifier.weight(1f), style = style, color = color)
        Text(c2, Modifier.weight(1.2f), style = style, color = color)
        Text(c3, Modifier.weight(0.7f), style = style, color = color, textAlign = TextAlign.End)
        Text(c4, Modifier.weight(0.7f), style = style, color = color, textAlign = TextAlign.End)
    }
}

/** "60×3" with optional warm-up prefix and failed mark. */
private fun setToken(set: BlockSet): String {
    val v = set.weight ?: set.metricValue
    val core = if (v != null) "${v.trimmed()}×${set.reps}" else "${set.reps}"
    val prefix = if (set.isWarmup) "wu " else ""
    val suffix = if (set.isFailed) " ✗" else ""
    return "$prefix$core$suffix"
}

@Composable
private fun BlockHistoryRow(
    bp: BlockPerformance,
    unit: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val b = bp.block
    val label = b.name.ifBlank { b.scheme.ifBlank { b.format.ifBlank { "Block" } } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                bp.date.formatShort(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                bp.average?.trimmed() ?: "—",
                Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
            Text(
                bp.top?.trimmed() ?: "—",
                Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
        }
        if (expanded) {
            val meta = listOfNotNull(
                bp.sessionTitle.ifBlank { null },
                b.kind.takeIf { it != BlockKind.STRENGTH }?.label,
                b.format.ifBlank { null },
                b.scheme.ifBlank { null }
            ).joinToString(" · ")
            if (meta.isNotBlank()) SubText(meta)
            if (bp.sets.isNotEmpty()) {
                Text(
                    "Sets: " + bp.sets.joinToString("   ") { setToken(it) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val detail = listOfNotNull(
                bp.average?.let { "avg ${it.trimmed()} $unit" },
                bp.top?.let { "top ${it.trimmed()} $unit" },
                bp.volume?.let { "volume ${it.trimmed()} kg" }
            ).joinToString(" · ")
            if (detail.isNotBlank()) SubText(detail)
        }
    }
}
