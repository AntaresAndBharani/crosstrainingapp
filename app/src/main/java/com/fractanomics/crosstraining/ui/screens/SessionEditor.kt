package com.fractanomics.crosstraining.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.SessionDraft
import com.fractanomics.crosstraining.ui.SetDraft
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import com.fractanomics.crosstraining.ui.components.AppNumericTextField
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.QuickAddWorkoutDialog
import com.fractanomics.crosstraining.ui.components.WorkoutJourneyAssistantSheet
import com.fractanomics.crosstraining.ui.trimmed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.derivedStateOf
import com.fractanomics.crosstraining.ui.components.InSessionTimerBar
import com.fractanomics.crosstraining.ui.components.InSessionTimerSheet
import com.fractanomics.crosstraining.ui.components.SubBlockInlineTimer
import com.fractanomics.crosstraining.ui.timer.NotificationPermissionHelper
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerEngineProvider
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerService
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfigParser
import com.fractanomics.crosstraining.util.RepScheme
import kotlinx.coroutines.launch
import java.time.LocalDate

// --- Plain seeds used to (re)initialise the editor form ----------------------

data class SetSeed(
    val reps: String = "",
    val value: String = "",
    val group: String = "",
    val warm: Boolean = false,
    val failed: Boolean = false
)

data class BlockSeed(
    val name: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val scheme: String = "",
    val exerciseId: Long? = null,
    val routineId: Long? = null,
    val sequenceExerciseIds: List<Long> = emptyList(),
    val description: String = "",
    val resultText: String = "",
    val resultValue: String = "",
    val sets: List<SetSeed> = listOf(SetSeed()),
    val section: String = "",
    val exerciseIdsCsv: String = "",
    val subBlock: String = ""
)

data class SessionSeed(
    val cycleId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val notes: String = "",
    val blocks: List<BlockSeed> = listOf(BlockSeed())
)

fun emptySessionSeed(): SessionSeed = SessionSeed()

fun sessionSeed(s: SessionWithBlocks, dateOverride: LocalDate? = null): SessionSeed =
    SessionSeed(
        cycleId = s.session.cycleId,
        date = dateOverride ?: s.session.date,
        title = s.session.title,
        notes = s.session.notes,
        blocks = s.blocks.sortedBy { it.block.position }.map { bws ->
            BlockSeed(
                name = bws.block.name,
                kind = bws.block.kind,
                format = bws.block.format,
                scheme = bws.block.scheme,
                exerciseId = bws.block.mainExerciseId,
                routineId = bws.block.routineId,
                description = bws.block.description,
                resultText = bws.block.resultText,
                resultValue = bws.block.resultValue?.trimmed() ?: "",
                sets = bws.sets.sortedBy { it.position }.map { st ->
                    SetSeed(
                        reps = st.reps.toString(),
                        value = (st.weight ?: st.metricValue)?.trimmed() ?: "",
                        group = st.groupIndex?.toString() ?: "",
                        warm = st.isWarmup,
                        failed = st.isFailed
                    )
                }.ifEmpty { listOf(SetSeed()) },
                section = bws.block.section,
                exerciseIdsCsv = bws.block.exerciseIdsCsv,
                subBlock = bws.block.subBlock
            )
        }.ifEmpty { listOf(BlockSeed()) }
    )

// --- Mutable form state holders ----------------------------------------------

internal class SetState(
    reps: String = "", value: String = "", group: String = "",
    warm: Boolean = false, failed: Boolean = false
) {
    var reps by mutableStateOf(reps)
    var value by mutableStateOf(value)
    var group by mutableStateOf(group)
    var isWarmup by mutableStateOf(warm)
    var isFailed by mutableStateOf(failed)
}

internal class BlockState(
    name: String = "", kind: BlockKind = BlockKind.STRENGTH, format: String = "", scheme: String = "",
    exercise: Exercise? = null, newExerciseName: String = "", routine: Routine? = null,
    sequenceExercises: List<Exercise> = emptyList(),
    description: String = "", resultText: String = "", resultValue: String = "",
    sets: List<SetState> = listOf(SetState()),
    section: String = "", exerciseIdsCsv: String = "", subBlock: String = ""
) {
    var name by mutableStateOf(name)
    var kind by mutableStateOf(kind)
    var format by mutableStateOf(format)
    var scheme by mutableStateOf(scheme)
    var exercise by mutableStateOf(exercise)
    var newExerciseName by mutableStateOf(newExerciseName)
    var routine by mutableStateOf(routine)
    val sequenceExercises: SnapshotStateList<Exercise> = sequenceExercises.toMutableStateList()
    var description by mutableStateOf(description)
    var resultText by mutableStateOf(resultText)
    var resultValue by mutableStateOf(resultValue)
    var recordRm by mutableStateOf(false)
    var rmReps by mutableStateOf("1")
    var rmWeight by mutableStateOf("")
    var isExpanded by mutableStateOf(false)
    val sets: SnapshotStateList<SetState> = sets.toMutableStateList()
    var section by mutableStateOf(section)
    var exerciseIdsCsv by mutableStateOf(exerciseIdsCsv)
    var subBlock by mutableStateOf(subBlock)
}

internal fun buildBlockState(seed: BlockSeed, exercises: List<Exercise>, routines: List<Routine>) =
    BlockState(
        name = seed.name,
        kind = seed.kind,
        format = seed.format,
        scheme = seed.scheme,
        exercise = exercises.firstOrNull { it.id == seed.exerciseId },
        routine = routines.firstOrNull { it.id == seed.routineId },
        sequenceExercises = seed.sequenceExerciseIds.mapNotNull { id -> exercises.firstOrNull { it.id == id } },
        description = seed.description,
        resultText = seed.resultText,
        resultValue = seed.resultValue,
        sets = seed.sets.map { SetState(it.reps, it.value, it.group, it.warm, it.failed) },
        section = seed.section,
        exerciseIdsCsv = seed.exerciseIdsCsv,
        subBlock = seed.subBlock
    )

internal fun BlockState.toDraftOrNull(): BlockDraft? {
    val metric = exercise?.metricType ?: MetricType.WEIGHT
    val setDrafts = sets.mapNotNull { ss ->
        val reps = ss.reps.toIntOrNull() ?: return@mapNotNull null
        val v = ss.value.replace(',', '.').toDoubleOrNull()
        SetDraft(
            reps = reps,
            weight = if (metric == MetricType.WEIGHT) v else null,
            metricValue = if (metric != MetricType.WEIGHT) v else null,
            groupIndex = ss.group.toIntOrNull(),
            isWarmup = ss.isWarmup,
            isFailed = ss.isFailed
        )
    }
    val hasContent = name.isNotBlank() || exercise != null || newExerciseName.isNotBlank() ||
        setDrafts.isNotEmpty() || description.isNotBlank() || resultText.isNotBlank()
    if (!hasContent) return null
    return BlockDraft(
        name = name.trim(),
        kind = kind,
        format = format.trim(),
        scheme = scheme.trim(),
        existingExerciseId = exercise?.id,
        newExerciseName = newExerciseName.ifBlank { null },
        routineId = routine?.id,
        description = description.trim(),
        resultText = resultText.trim(),
        resultValue = resultValue.replace(',', '.').toDoubleOrNull(),
        sets = setDrafts,
        newRepMaxReps = if (recordRm) rmReps.toIntOrNull() else null,
        newRepMaxWeight = if (recordRm) rmWeight.replace(',', '.').toDoubleOrNull() else null,
        section = section.trim(),
        exerciseIdsCsv = exerciseIdsCsv.trim(),
        subBlock = subBlock.trim()
    )
}

internal sealed class EditorBlockItem {
    data class Standalone(val index: Int, val block: BlockState) : EditorBlockItem()
    data class SubBlockGroup(
        val subBlockName: String,
        val format: String,
        val items: List<Pair<Int, BlockState>>
    ) : EditorBlockItem() {
        val totalRounds: Int
            get() = items.maxOfOrNull { it.second.sets.size } ?: 0
    }
}

internal fun groupEditorBlocks(blocks: List<BlockState>): List<EditorBlockItem> {
    val result = mutableListOf<EditorBlockItem>()
    var i = 0
    while (i < blocks.size) {
        val currentBlock = blocks[i]
        val trimmedSub = currentBlock.subBlock.trim()
        val trimmedSection = currentBlock.section.trim()

        if (trimmedSub.isBlank()) {
            result.add(EditorBlockItem.Standalone(i, currentBlock))
            i++
        } else {
            val groupItems = mutableListOf<Pair<Int, BlockState>>()
            groupItems.add(i to currentBlock)
            var j = i + 1
            while (j < blocks.size) {
                val nextBlock = blocks[j]
                if (nextBlock.subBlock.trim().equals(trimmedSub, ignoreCase = true) &&
                    nextBlock.section.trim().equals(trimmedSection, ignoreCase = true)
                ) {
                    groupItems.add(j to nextBlock)
                    j++
                } else {
                    break
                }
            }
            val groupFormat = groupItems.firstOrNull { it.second.format.isNotBlank() }?.second?.format ?: ""
            result.add(
                EditorBlockItem.SubBlockGroup(
                    subBlockName = trimmedSub,
                    format = groupFormat,
                    items = groupItems
                )
            )
            i = j
        }
    }
    return result
}

// --- Shared editor body ------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorBody(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    screenTitle: String,
    seed: SessionSeed,
    seedKey: Any,
    saveLabel: String,
    clearAfterSave: Boolean,
    onBack: (() -> Unit)?,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenTimer: (() -> Unit)? = null,
    onSubmit: (SessionDraft) -> Unit
) {
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routinesWithBlocks by viewModel.routinesWithBlocks.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var formKey by remember(seedKey) { mutableIntStateOf(0) }
    val key = seedKey to formKey

    var selectedCycleId by remember(key) { mutableStateOf(seed.cycleId) }
    var date by remember(key) { mutableStateOf(seed.date) }
    var title by remember(key) { mutableStateOf(seed.title) }
    var notes by remember(key) { mutableStateOf(seed.notes) }
    var showSessionMeta by remember { mutableStateOf(false) }

    val blocks = remember(key) {
        seed.blocks.map { buildBlockState(it, exercises, routines) }.toMutableStateList()
    }
    val effectiveCycleId = selectedCycleId ?: activeCycle?.id
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showWorkoutAssistantSheet by remember { mutableStateOf(false) }

    if (showQuickAddDialog) {
        QuickAddWorkoutDialog(
            exercises = exercises,
            routines = routines,
            onDismiss = { showQuickAddDialog = false },
            onConfirm = { parsed ->
                val newBlock = BlockState(
                    name = parsed.name,
                    kind = parsed.kind,
                    format = parsed.format,
                    scheme = parsed.scheme,
                    exercise = exercises.firstOrNull { it.id == parsed.existingExerciseId },
                    newExerciseName = parsed.newExerciseName ?: "",
                    routine = routines.firstOrNull { it.id == parsed.routineId },
                    sets = parsed.sets.map { s ->
                        SetState(
                            reps = s.reps.toString(),
                            value = s.weight?.trimmed() ?: "",
                            warm = s.isWarmup
                        )
                    }.ifEmpty { listOf(SetState()) }
                )
                blocks.add(newBlock)
            }
        )
    }

    if (showWorkoutAssistantSheet) {
        WorkoutJourneyAssistantSheet(
            viewModel = viewModel,
            onDismiss = { showWorkoutAssistantSheet = false },
            onSuccess = { routine, session ->
                scope.launch {
                    val msg = buildString {
                        append("Workout journey saved")
                        if (routine != null) append(" (Routine '${routine.name}')")
                        if (session != null) append(" (Session recorded)")
                    }
                    snackbar.showSnackbar(msg)
                }
            }
        )
    }

    val context = LocalContext.current
    val timerEngine = remember { TimerEngineProvider.get(context) }
    val timerSnapshot by timerEngine.snapshot.collectAsStateWithLifecycle()
    val isTimerActive by remember {
        derivedStateOf { timerSnapshot.phase != TimerPhase.IDLE }
    }

    var showTimerSheet by remember { mutableStateOf(false) }
    var timerSheetPrefillTitle by remember { mutableStateOf("") }
    var timerSheetPrefillRounds by remember { mutableIntStateOf(1) }
    var pendingReplaceConfig by remember { mutableStateOf<WorkoutTimerConfig?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            TimerService.startService(context)
        } else {
            scope.launch {
                snackbar.showSnackbar(
                    "Timer running in-session. Grant notification permission in Settings for lock-screen controls."
                )
            }
        }
    }

    val requestNotificationPermissionAndStartService: () -> Unit = {
        NotificationPermissionHelper.handleTimerStartWithPermission(
            context = context,
            onPermissionRequired = {
                permissionLauncher.launch(NotificationPermissionHelper.POST_NOTIFICATIONS)
            },
            onStartService = {
                TimerService.startService(context)
            }
        )
    }

    val launchOrReplaceTimer: (WorkoutTimerConfig) -> Unit = { config ->
        if (isTimerActive && timerSnapshot.phase != TimerPhase.FINISHED) {
            // Check if it's the exact same configuration/label running
            if (timerSnapshot.workoutLabel.isNotBlank() && timerSnapshot.workoutLabel == config.workoutLabel) {
                showTimerSheet = true
            } else {
                pendingReplaceConfig = config
                showConflictDialog = true
            }
        } else {
            timerEngine.configure(config)
            timerEngine.start()
            requestNotificationPermissionAndStartService()
        }
    }

    val launchTimingFromTokenOrOpenSheet: (String?, Int, String) -> Unit = { formatToken, roundCount, label ->
        val baseConfig = timerEngine.currentConfig
        val parsedConfig = WorkoutTimerConfigParser.parse(
            formatString = formatToken,
            roundCount = roundCount,
            workoutLabel = label,
            baseConfig = baseConfig
        )
        if (parsedConfig != null) {
            launchOrReplaceTimer(parsedConfig)
        } else {
            timerSheetPrefillTitle = label
            timerSheetPrefillRounds = roundCount.coerceAtLeast(1)
            showTimerSheet = true
        }
    }

    if (showConflictDialog && pendingReplaceConfig != null) {
        AlertDialog(
            onDismissRequest = {
                showConflictDialog = false
                pendingReplaceConfig = null
            },
            title = { Text("Replace active timer?") },
            text = {
                Text(
                    "A timer is currently running (${timerSnapshot.workoutLabel.ifBlank { timerSnapshot.mode.label }}). " +
                        "Do you want to replace it with '${pendingReplaceConfig?.workoutLabel?.ifBlank { pendingReplaceConfig?.mode?.label }}'?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val config = pendingReplaceConfig
                        showConflictDialog = false
                        pendingReplaceConfig = null
                        if (config != null) {
                            timerEngine.replaceTimer(config)
                            requestNotificationPermissionAndStartService()
                        }
                    }
                ) {
                    Text("Replace", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConflictDialog = false
                        pendingReplaceConfig = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTimerSheet) {
        InSessionTimerSheet(
            timerEngine = timerEngine,
            onDismiss = { showTimerSheet = false },
            prefillTitle = timerSheetPrefillTitle,
            prefillRounds = timerSheetPrefillRounds,
            onStartService = requestNotificationPermissionAndStartService
        )
    }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showWorkoutAssistantSheet = true }
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = "Paste Workout Notes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showQuickAddDialog = true }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Quick Add / Parse", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (onOpenTimer != null) {
                        IconButton(onClick = onOpenTimer) {
                            Icon(Icons.Filled.Timer, contentDescription = "Quick Timer")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTimerActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    InSessionTimerBar(
                        timerEngine = timerEngine,
                        onExpand = { showTimerSheet = true },
                        onStartService = requestNotificationPermissionAndStartService
                    )
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Session Header Strip (Cycle, Date & Routine Loader)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date picker in 45% width
                        DateField(
                            label = "Date",
                            date = date,
                            onDateChange = { date = it },
                            modifier = Modifier.weight(1f)
                        )

                        // Cycle Selector in 55% width
                        Dropdown(
                            label = "Cycle",
                            options = cycles,
                            selected = cycles.firstOrNull { it.id == effectiveCycleId },
                            labelOf = { it.name + if (it.isActive) " (active)" else "" },
                            onSelect = { selectedCycleId = it.id },
                            placeholder = "No cycle",
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Routine quick loader & Title toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Dropdown(
                            label = "Load Routine",
                            options = routinesWithBlocks,
                            selected = null,
                            labelOf = { it.routine.name },
                            onSelect = { rwb ->
                                if (title.isBlank()) title = rwb.routine.name
                                blocks.clear()
                                rwb.blocks.sortedBy { it.position }.forEach { blk ->
                                    val seqExList = blk.exerciseIdsCsv.split(",")
                                        .mapNotNull { idStr -> idStr.trim().toLongOrNull() }
                                        .mapNotNull { id -> exercises.firstOrNull { it.id == id } }

                                    val targetEx = seqExList.firstOrNull() ?: exercises.firstOrNull { it.id == rwb.routine.mainExerciseId }
                                    val parsedRepsList = RepScheme.parse(blk.targetRepsScheme, blk.setsCount)

                                    val newBlockState = BlockState(
                                        name = blk.name.ifBlank { blk.kind.label },
                                        kind = blk.kind,
                                        format = blk.format,
                                        scheme = blk.targetRepsScheme,
                                        exercise = targetEx,
                                        routine = rwb.routine,
                                        sequenceExercises = seqExList,
                                        description = blk.notes,
                                        sets = parsedRepsList.map { reps ->
                                            SetState(reps = reps.toString(), value = "")
                                        }.toMutableStateList(),
                                        section = blk.section,
                                        exerciseIdsCsv = blk.exerciseIdsCsv,
                                        subBlock = blk.subBlock
                                    )
                                    blocks.add(newBlockState)
                                }
                            },
                            placeholder = "Load Routine...",
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = { showSessionMeta = !showSessionMeta },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                if (showSessionMeta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showSessionMeta) "Hide Details" else "Title & Notes", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    AnimatedVisibility(visible = showSessionMeta) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Session title (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Session notes / feeling / PR notes") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Blocks List grouped by section header and sub-block containers
            val editorItems = groupEditorBlocks(blocks)
            var prevSection: String? = null
            editorItems.forEach { item ->
                val currentSection = when (item) {
                    is EditorBlockItem.Standalone -> item.block.section.trim()
                    is EditorBlockItem.SubBlockGroup -> item.items.first().second.section.trim()
                }

                if (currentSection.isNotBlank() && currentSection != prevSection) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (prevSection != null) 8.dp else 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentSection,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                prevSection = currentSection

                when (item) {
                    is EditorBlockItem.Standalone -> {
                        CompactBlockEditor(
                            index = item.index,
                            block = item.block,
                            blocks = blocks,
                            exercises = exercises,
                            routines = routines,
                            canRemove = blocks.size > 1,
                            onRemove = { blocks.removeAt(item.index) },
                            onLaunchTimer = {
                                val label = item.block.exercise?.name ?: item.block.name.ifBlank { "Movement ${item.index + 1}" }
                                val timingToken = item.block.format.ifBlank {
                                    if (item.block.description.contains("Rest", ignoreCase = true)) item.block.description else null
                                }
                                launchTimingFromTokenOrOpenSheet(timingToken, item.block.sets.size, label)
                            }
                        )
                    }
                    is EditorBlockItem.SubBlockGroup -> {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sub-Block Header Row: Title, Format Badge, Round Counter
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            Icons.Filled.ViewAgenda,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = item.subBlockName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val launchSubBlockTimer = {
                                            val timingToken = item.format.ifBlank { item.subBlockName }
                                            launchTimingFromTokenOrOpenSheet(
                                                timingToken,
                                                item.totalRounds,
                                                item.subBlockName
                                            )
                                        }

                                        if (item.format.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.clickable(onClick = launchSubBlockTimer)
                                            ) {
                                                Text(
                                                    text = item.format,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val rounds = item.totalRounds
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.clickable(onClick = launchSubBlockTimer)
                                        ) {
                                            Text(
                                                text = "$rounds ${if (rounds == 1) "Round" else "Rounds"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Sub-Block Timer Launcher Button
                                        IconButton(
                                            onClick = launchSubBlockTimer,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Timer,
                                                contentDescription = "Start timer for ${item.subBlockName}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Conditionally render SubBlockInlineTimer when active for this sub-block
                                if (timerSnapshot.workoutLabel == item.subBlockName && timerSnapshot.phase != TimerPhase.IDLE) {
                                    SubBlockInlineTimer(
                                        timerEngine = timerEngine,
                                        onStartService = requestNotificationPermissionAndStartService
                                    )
                                }

                                // Child blocks rendered inside container
                                item.items.forEach { (childIndex, childBlock) ->
                                    CompactBlockEditor(
                                        index = childIndex,
                                        block = childBlock,
                                        blocks = blocks,
                                        exercises = exercises,
                                        routines = routines,
                                        canRemove = blocks.size > 1,
                                        onRemove = { blocks.removeAt(childIndex) },
                                        onLaunchTimer = {
                                            val label = childBlock.exercise?.name ?: childBlock.name.ifBlank { "Movement ${childIndex + 1}" }
                                            val timingToken = childBlock.format.ifBlank {
                                                // Check if notes or description contain Rest or format
                                                if (childBlock.description.contains("Rest", ignoreCase = true)) childBlock.description else null
                                            }
                                            launchTimingFromTokenOrOpenSheet(timingToken, childBlock.sets.size, label)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Add Blocks Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { blocks.add(BlockState()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Block")
                }

                Button(
                    onClick = { showQuickAddDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Quick Parse")
                }
            }

            // Primary Save Action
            Button(
                onClick = {
                    val cycleId = effectiveCycleId
                    if (cycleId == null) {
                        scope.launch { snackbar.showSnackbar("Create and select a cycle first (Cycles tab).") }
                        return@Button
                    }
                    val blockDrafts = blocks.mapNotNull { it.toDraftOrNull() }
                    if (blockDrafts.isEmpty()) {
                        scope.launch { snackbar.showSnackbar("Add at least one block with content.") }
                        return@Button
                    }
                    onSubmit(
                        SessionDraft(
                            cycleId = cycleId,
                            date = date,
                            title = title.trim(),
                            notes = notes.trim(),
                            blocks = blockDrafts
                        )
                    )
                    if (clearAfterSave) {
                        formKey += 1
                        scope.launch { snackbar.showSnackbar("Session saved successfully!") }
                    } else {
                        onBack?.invoke()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(saveLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// --- Compact Block Editor with Spreadsheet Set Table -------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CompactBlockEditor(
    index: Int,
    block: BlockState,
    blocks: SnapshotStateList<BlockState>,
    exercises: List<Exercise>,
    routines: List<Routine>,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onLaunchTimer: (() -> Unit)? = null
) {
    val metric = block.exercise?.metricType ?: MetricType.WEIGHT
    val valueLabel = if (metric == MetricType.WEIGHT) "kg" else metric.defaultUnit
    val canRecordRm = block.exercise?.tracksRepMax ?: block.newExerciseName.isNotBlank()
    val isMetabolic = block.kind == BlockKind.METCON || block.kind == BlockKind.METABOLIC || block.kind == BlockKind.CARDIO

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Block Header Row: Title / Selector & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = block.exercise?.name ?: block.name.ifBlank { "Movement / Block ${index + 1}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Timer Launcher Button
                    if (onLaunchTimer != null) {
                        IconButton(
                            onClick = onLaunchTimer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = "Start timer for ${block.exercise?.name ?: block.name.ifBlank { "Block ${index + 1}" }}",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Type Badge Chip (suppressed if inside sub-block or rendered concisely)
                    AssistChip(
                        onClick = {
                            val nextIdx = (block.kind.ordinal + 1) % BlockKind.entries.size
                            block.kind = BlockKind.entries[nextIdx]
                        },
                        label = { Text(block.kind.shortLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    )

                    // Expand / Collapse Details
                    IconButton(
                        onClick = { block.isExpanded = !block.isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (block.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.Tune,
                            contentDescription = "Tune Block Details",
                            modifier = Modifier.size(18.dp),
                            tint = if (block.isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete Block
                    IconButton(
                        onClick = onRemove,
                        enabled = canRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove block",
                            modifier = Modifier.size(18.dp),
                            tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Main Exercise Quick Selector (if not selected)
            if (block.exercise == null && block.name.isBlank()) {
                Dropdown(
                    label = "Select Exercise",
                    options = exercises,
                    selected = block.exercise,
                    labelOf = { it.name },
                    onSelect = { block.exercise = it; block.name = it.name },
                    placeholder = "Choose Movement...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Collapsible Block Details (Routine, Format, Scheme, Interval notes)
            AnimatedVisibility(
                visible = block.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Dropdown(
                            label = "Exercise",
                            options = exercises,
                            selected = block.exercise,
                            labelOf = { it.name },
                            onSelect = { block.exercise = it; block.newExerciseName = "" },
                            placeholder = "Choose Exercise...",
                            modifier = Modifier.weight(1f)
                        )

                        Dropdown(
                            label = "Routine / Complex",
                            options = routines,
                            selected = block.routine,
                            labelOf = { it.name },
                            onSelect = { routine ->
                                block.routine = routine
                                if (block.name.isBlank()) block.name = routine.name
                                if (block.format.isBlank()) block.format = routine.defaultFormat
                                exercises.firstOrNull { it.id == routine.mainExerciseId }?.let {
                                    block.exercise = it
                                }
                            },
                            placeholder = "None",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = block.name,
                        onValueChange = { block.name = it },
                        label = { Text("Custom Block Name / Subtitle") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = block.section,
                        onValueChange = { block.section = it },
                        label = { Text("Macro-Block Section (e.g. Strength, Accessories)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = block.subBlock,
                        onValueChange = { block.subBlock = it },
                        label = { Text("Sub-Block / Triset (e.g. E3MOM Trisets, Superset A)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = block.format,
                            onValueChange = { block.format = it },
                            label = { Text("Format (e.g. EMOM)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = block.scheme,
                            onValueChange = { block.scheme = it },
                            label = { Text("Rep Scheme") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WORKOUT_FORMATS.take(6).forEach { f ->
                            FilterChip(
                                selected = block.format == f,
                                onClick = { block.format = f },
                                label = { Text(f, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (isMetabolic) {
                        OutlinedTextField(
                            value = block.description,
                            onValueChange = { block.description = it },
                            label = { Text("Intervals / Metcon Movements") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = block.resultText,
                            onValueChange = { block.resultText = it },
                            label = { Text("Result / Score (e.g. 4:15 or 150 cal)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // SPREADSHEET SET TABLE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "SET",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "REPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "WEIGHT ($valueLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "FLAGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(28.dp))
                }

                // Table Set Rows
                block.sets.forEachIndexed { setIdx, setState ->
                    SpreadsheetSetRow(
                        setIndex = setIdx + 1,
                        set = setState,
                        canRemove = block.sets.size > 1,
                        onRemove = { block.sets.removeAt(setIdx) }
                    )
                }
            }

            // Inline Set Helpers Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val last = block.sets.lastOrNull()
                        block.sets.add(SetState(reps = last?.reps ?: "", value = last?.value ?: ""))
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(
                        onClick = {
                            val first = block.sets.firstOrNull()?.value ?: ""
                            if (first.isNotBlank()) block.sets.forEach { it.value = first }
                        },
                        label = { Text("Copy 1st", style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = {
                            var cur = block.sets.firstOrNull()?.value?.toDoubleOrNull() ?: 0.0
                            block.sets.forEach { s ->
                                if (s.value.isBlank()) s.value = cur.trimmed()
                                cur += 2.5
                            }
                        },
                        label = { Text("+2.5kg", style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = {
                            var cur = block.sets.firstOrNull()?.value?.toDoubleOrNull() ?: 0.0
                            block.sets.forEach { s ->
                                if (s.value.isBlank()) s.value = cur.trimmed()
                                cur += 5.0
                            }
                        },
                        label = { Text("+5kg", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Rep Max Option
            if (canRecordRm) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { block.recordRm = !block.recordRm }
                    ) {
                        Checkbox(
                            checked = block.recordRm,
                            onCheckedChange = { block.recordRm = it }
                        )
                        Text(
                            "Record PR / Rep-Max",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (block.recordRm) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppNumericTextField(
                                value = block.rmReps,
                                onValueChange = { block.rmReps = it },
                                label = { Text("Reps") },
                                singleLine = true,
                                allowDecimals = false,
                                minValue = 1.0,
                                maxValue = 999.0,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(70.dp)
                            )
                            AppNumericTextField(
                                value = block.rmWeight,
                                onValueChange = { block.rmWeight = it },
                                label = { Text("kg") },
                                singleLine = true,
                                allowDecimals = true,
                                minValue = 0.0,
                                maxValue = 999.9,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(85.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Spreadsheet Set Row (Ultra Compact ~40dp) -------------------------------

@Composable
private fun SpreadsheetSetRow(
    setIndex: Int,
    set: SetState,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Set Badge
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$setIndex",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Reps Input Box
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            AppNumericTextField(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "reps_${setIndex - 1}" },
                value = set.reps,
                onValueChange = { set.reps = it },
                allowDecimals = false,
                isBasic = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        "0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        // Weight Input Box
        Box(
            modifier = Modifier
                .weight(1.3f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            AppNumericTextField(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "weight_${setIndex - 1}" },
                value = set.value,
                onValueChange = { set.value = it },
                allowDecimals = true,
                isBasic = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        "0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        // Warmup [W] and Failed [F] Flag Pills
        Row(
            modifier = Modifier.width(72.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // [W] Warm-up
            Box(
                modifier = Modifier
                    .size(34.dp, 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (set.isWarmup) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (set.isWarmup) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { set.isWarmup = !set.isWarmup },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "W",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (set.isWarmup) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // [F] Failed
            Box(
                modifier = Modifier
                    .size(34.dp, 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (set.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (set.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { set.isFailed = !set.isFailed },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "F",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (set.isFailed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Delete Row Button
        IconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove set",
                modifier = Modifier.size(16.dp),
                tint = if (canRemove) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

// --- Edit / Copy entry screen ------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    sessionId: Long,
    copy: Boolean,
    onBack: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val target = sessions.firstOrNull { it.session.id == sessionId }

    if (target == null) {
        Scaffold(
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
            topBar = {
                TopAppBar(
                    title = { Text(if (copy) "Copy Session" else "Edit Session") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            EmptyState("Session not available.", Modifier.padding(pad))
        }
        return
    }

    val seed = remember(sessionId, copy) {
        sessionSeed(target, dateOverride = if (copy) LocalDate.now() else null)
    }
    SessionEditorBody(
        viewModel = viewModel,
        outerPadding = outerPadding,
        screenTitle = if (copy) "Copy Session" else "Edit Session",
        seed = seed,
        seedKey = "${if (copy) "copy" else "edit"}-$sessionId",
        saveLabel = if (copy) "Save copy" else "Update session",
        clearAfterSave = false,
        onBack = onBack,
        onSubmit = { draft ->
            if (copy) viewModel.saveSession(draft) else viewModel.updateSession(sessionId, draft)
        }
    )
}
