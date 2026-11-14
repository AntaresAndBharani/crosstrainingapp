package com.fractanomics.crosstraining.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.firebase.FirebaseSyncManager
import com.fractanomics.crosstraining.data.firebase.SharedWorkoutPayload
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.components.WorkoutJourneyAssistantSheet
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {},
    onLogBlock: (Long) -> Unit = {}
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val availableBlocks by viewModel.availableBlocks.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var showExerciseEditor by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showQuickBlockSheet by remember { mutableStateOf(false) }
    var editingAvailableBlock by remember { mutableStateOf<AvailableBlock?>(null) }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(context.contentResolver, it) { ok ->
                scope.launch { snackbar.showSnackbar(if (ok) "Backup exported" else "Export failed") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    var exerciseSearch by remember { mutableStateOf("") }
    var exerciseCategoryFilter by remember { mutableStateOf<ExerciseCategory?>(null) }
    var blockSearch by remember { mutableStateOf("") }

    var activeShareCode by remember { mutableStateOf<String?>(null) }
    var showImportModal by remember { mutableStateOf(false) }
    var showCommunityModal by remember { mutableStateOf(false) }
    var showWorkoutAssistantSheet by remember { mutableStateOf(false) }

    val filteredExercises = remember(exercises, exerciseSearch, exerciseCategoryFilter) {
        exercises.filter { ex ->
            val matchesCategory = exerciseCategoryFilter == null || ex.category == exerciseCategoryFilter
            val matchesSearch = exerciseSearch.isBlank() || ex.name.lowercase().contains(exerciseSearch.trim().lowercase())
            matchesCategory && matchesSearch
        }
    }

    val filteredAvailableBlocks = remember(availableBlocks, blockSearch) {
        AvailableBlockCodec.filterAvailableBlocks(availableBlocks, blockSearch)
    }

    val userRole by viewModel.userRole.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Library", fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (userRole.isCoach) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                if (userRole.isCoach) "COACH" else "ATHLETE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTimer) {
                        Icon(Icons.Filled.Timer, contentDescription = "Quick Timer")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import from Notes") },
                            leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            onClick = { showMenu = false; showWorkoutAssistantSheet = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Code") },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                            onClick = { showMenu = false; showImportModal = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Community Library") },
                            leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                            onClick = { showMenu = false; showCommunityModal = true }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Export backup (CSV)") },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("crosstraining-backup-${LocalDate.now()}.csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup (CSV)") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                            }
                        )
                        if (demoMode) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Reset demo data") },
                                onClick = {
                                    showMenu = false
                                    viewModel.resetDemoData()
                                    scope.launch { snackbar.showSnackbar("Demo data reset") }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tab == 0) {
                    editingAvailableBlock = null
                    showQuickBlockSheet = true
                } else {
                    editingExercise = null
                    showExerciseEditor = true
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            "Available Blocks (${availableBlocks.size})",
                            fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            "Exercises (${exercises.size})",
                            fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            if (tab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = blockSearch,
                        onValueChange = { blockSearch = it },
                        placeholder = {
                            Text(
                                "Search available blocks or exercises...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (blockSearch.isNotEmpty()) {
                                IconButton(onClick = { blockSearch = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredAvailableBlocks.isEmpty()) {
                    EmptyState(
                        if (blockSearch.isNotBlank()) "No available blocks match '$blockSearch'."
                        else "No available blocks yet. Sync via MCP or tap + to add one."
                    )
                } else {
                    ScreenList {
                        items(filteredAvailableBlocks, key = { it.id }) { block ->
                            val lastSummary = remember(block.id, block.title, sessions, exercises, weightUnit) {
                                AvailableBlockCodec.resolveLastBlockSession(block, sessions, exercises, weightUnit)
                            }
                            AvailableBlockCard(
                                block = block,
                                lastSessionSummary = lastSummary,
                                onLogBlock = { onLogBlock(block.id) },
                                onEdit = {
                                    editingAvailableBlock = block
                                    showQuickBlockSheet = true
                                },
                                onDuplicate = { viewModel.duplicateAvailableBlock(block) },
                                onDelete = {
                                    viewModel.deleteAvailableBlock(block)
                                    scope.launch {
                                        val res = snackbar.showSnackbar(
                                            message = "Block removed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreAvailableBlock(block)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = exerciseSearch,
                        onValueChange = { exerciseSearch = it },
                        placeholder = { Text("Search exercises...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = exerciseCategoryFilter == null,
                            onClick = { exerciseCategoryFilter = null },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                        )
                        ExerciseCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = exerciseCategoryFilter == cat,
                                onClick = {
                                    exerciseCategoryFilter = if (exerciseCategoryFilter == cat) null else cat
                                },
                                label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (filteredExercises.isEmpty()) {
                    EmptyState(
                        if (exerciseSearch.isNotBlank() || exerciseCategoryFilter != null) "No exercises match your filter."
                        else "No exercises yet. Tap + to add one."
                    )
                } else {
                    ScreenList {
                        items(filteredExercises, key = { it.id }) { ex ->
                            CompactExerciseCard(
                                exercise = ex,
                                onEdit = { editingExercise = ex; showExerciseEditor = true },
                                onDelete = { viewModel.deleteExercise(ex) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeShareCode != null) {
        ShareWorkoutModalDialog(
            shareCode = activeShareCode!!,
            onDismiss = { activeShareCode = null }
        )
    }

    if (showImportModal) {
        ImportWorkoutModalDialog(
            onDismiss = { showImportModal = false },
            onImportCode = { code ->
                showImportModal = false
                scope.launch {
                    val payload = FirebaseSyncManager.getSharedWorkout(code)
                    if (payload != null) {
                        viewModel.importSharedWorkout(payload) { ok ->
                            scope.launch {
                                snackbar.showSnackbar(if (ok) "Imported '${payload.routineName}'!" else "Import failed")
                            }
                        }
                    } else {
                        snackbar.showSnackbar("Invalid or expired share code '$code'")
                    }
                }
            }
        )
    }

    if (showCommunityModal) {
        CommunityWorkoutsModalDialog(
            viewModel = viewModel,
            onDismiss = { showCommunityModal = false },
            onImportPayload = { payload ->
                viewModel.importSharedWorkout(payload) { ok ->
                    scope.launch {
                        snackbar.showSnackbar(if (ok) "Imported '${payload.routineName}' to library!" else "Import failed")
                    }
                }
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
                        if (routine != null) append(" (Block '${routine.name}')")
                        if (session != null) append(" (Session recorded)")
                    }
                    snackbar.showSnackbar(msg)
                }
            }
        )
    }

    if (showExerciseEditor) {
        ExerciseEditorDialog(
            original = editingExercise,
            onDismiss = { showExerciseEditor = false },
            onSave = { ex -> viewModel.saveExercise(ex); showExerciseEditor = false }
        )
    }
    if (showQuickBlockSheet) {
        QuickCreateBlockSheet(
            original = editingAvailableBlock,
            exercises = exercises,
            onCreateExercise = { name, onCreated ->
                viewModel.getOrCreateExerciseForRoutine(
                    name = name,
                    category = ExerciseCategory.BARBELL,
                    metricType = MetricType.WEIGHT,
                    onCreated = onCreated
                )
            },
            onDismiss = { showQuickBlockSheet = false },
            onSave = { title, instructions, selectedExerciseIds ->
                viewModel.saveAvailableBlock(
                    title = title,
                    instructions = instructions,
                    exerciseIds = selectedExerciseIds,
                    existingId = editingAvailableBlock?.id ?: 0L,
                    trackedPrs = editingAvailableBlock?.trackedPrs.orEmpty()
                )
                showQuickBlockSheet = false
            }
        )
    }
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false; pendingImportUri = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Importing this backup will replace all current cycles, exercises, " +
                        "routines and sessions on this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    showImportConfirm = false
                    pendingImportUri = null
                    if (uri != null) {
                        viewModel.importBackup(context.contentResolver, uri) { ok ->
                            scope.launch {
                                snackbar.showSnackbar(if (ok) "Backup imported" else "Import failed — check the file")
                            }
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false; pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CompactExerciseCard(exercise: Exercise, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Exercise Category Icon Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Exercise Title & Details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        exercise.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "${exercise.metricType.label} (${exercise.unit})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (exercise.tracksRepMax) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            "Tracks PR 🔥",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Actions
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal sealed class RoutineBlockItem {
    data class Standalone(val index: Int, val block: RoutineBlock) : RoutineBlockItem()
    data class SubBlockGroup(
        val subBlockName: String,
        val format: String,
        val items: List<Pair<Int, RoutineBlock>>
    ) : RoutineBlockItem() {
        val totalSetsCount: Int
            get() = items.maxOfOrNull { it.second.setsCount } ?: 0
    }
}

internal fun groupRoutineBlocks(blocks: List<RoutineBlock>): List<RoutineBlockItem> {
    val result = mutableListOf<RoutineBlockItem>()
    var i = 0
    while (i < blocks.size) {
        val currentBlock = blocks[i]
        val trimmedSub = currentBlock.subBlock.trim()
        val trimmedSection = currentBlock.section.trim()
        val trimmedScheme = currentBlock.targetRepsScheme.trim()

        val effectiveSub = if (trimmedSub.isNotBlank()) {
            trimmedSub
        } else if (trimmedScheme.startsWith("TRISET", ignoreCase = true) ||
            trimmedScheme.startsWith("SUPERSET", ignoreCase = true) ||
            trimmedScheme.startsWith("BISET", ignoreCase = true)
        ) {
            trimmedScheme
        } else {
            ""
        }

        if (effectiveSub.isBlank()) {
            result.add(RoutineBlockItem.Standalone(i, currentBlock))
            i++
        } else {
            val groupItems = mutableListOf<Pair<Int, RoutineBlock>>()
            groupItems.add(i to currentBlock)
            var j = i + 1
            while (j < blocks.size) {
                val nextBlock = blocks[j]
                val nextSub = nextBlock.subBlock.trim()
                val nextSection = nextBlock.section.trim()
                val nextScheme = nextBlock.targetRepsScheme.trim()

                val nextEffectiveSub = if (nextSub.isNotBlank()) {
                    nextSub
                } else if (nextScheme.startsWith("TRISET", ignoreCase = true) ||
                    nextScheme.startsWith("SUPERSET", ignoreCase = true) ||
                    nextScheme.startsWith("BISET", ignoreCase = true)
                ) {
                    nextScheme
                } else {
                    ""
                }

                if (nextEffectiveSub.equals(effectiveSub, ignoreCase = true) &&
                    nextSection.equals(trimmedSection, ignoreCase = true)
                ) {
                    groupItems.add(j to nextBlock)
                    j++
                } else {
                    break
                }
            }
            val groupFormat = groupItems.firstOrNull { it.second.format.isNotBlank() }?.second?.format ?: ""
            result.add(
                RoutineBlockItem.SubBlockGroup(
                    subBlockName = effectiveSub,
                    format = groupFormat,
                    items = groupItems
                )
            )
            i = j
        }
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableBlockCard(
    block: AvailableBlock,
    lastSessionSummary: com.fractanomics.crosstraining.util.LastBlockSessionSummary?,
    onLogBlock: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var showOverflow by remember(block.id) { mutableStateOf(false) }
    var instructionsExpanded by remember(block.id) { mutableStateOf(false) }
    var hasInstructionsOverflow by remember(block.id, block.instructions) {
        mutableStateOf(block.instructions.lines().size > 4 || block.instructions.length > 160)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onLogBlock()
                    },
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        "Log Block ▸",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Block options for ${block.title}"
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { showOverflow = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { showOverflow = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { showOverflow = false; onDelete() }
                        )
                    }
                }
            }

            if (block.instructions.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (instructionsExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layoutResult ->
                            if (layoutResult.hasVisualOverflow) {
                                hasInstructionsOverflow = true
                            }
                        }
                    )
                    if (hasInstructionsOverflow || instructionsExpanded) {
                        Text(
                            text = if (instructionsExpanded) "Show less" else "Show more",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { instructionsExpanded = !instructionsExpanded }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            if (block.exercises.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    block.exercises.forEach { ex ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = ex.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lastSessionSummary != null) {
                        "Last: ${lastSessionSummary.setWeightsSummary}"
                    } else {
                        "Not logged yet"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (lastSessionSummary != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (block.trackedPrs.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🔥 ${block.trackedPrs.size} tracked PR(s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickCreateBlockSheet(
    original: AvailableBlock?,
    exercises: List<Exercise>,
    onCreateExercise: (String, (Exercise) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, instructions: String, selectedExerciseIds: List<Long>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var instructions by remember(original?.id) { mutableStateOf(original?.instructions.orEmpty()) }
    var movementQuery by remember(original?.id) { mutableStateOf("") }
    var newMovementName by remember(original?.id) { mutableStateOf("") }
    val selectedIds = remember(original?.id) {
        mutableStateListOf<Long>().apply {
            addAll(original?.exercises?.map { it.id }.orEmpty())
        }
    }

    val filteredMovements = remember(exercises, movementQuery) {
        val needle = movementQuery.trim().lowercase()
        val sorted = exercises.sortedBy { it.name.lowercase() }
        if (needle.isEmpty()) sorted
        else sorted.filter { it.name.lowercase().contains(needle) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (original == null) "New Available Block" else "Edit Available Block",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Block Name") },
                placeholder = { Text("e.g. Clean Complex") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Instructions") },
                placeholder = { Text("e.g. Every 90s x 6 sets: 1 Clean Pull + 1 Clean + 1 Jerk") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Exercises (${selectedIds.size} selected)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = movementQuery,
                onValueChange = { movementQuery = it },
                placeholder = { Text("Filter movements...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredMovements.forEach { ex ->
                    val isSelected = ex.id in selectedIds
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedIds.remove(ex.id) else selectedIds.add(ex.id)
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(ex.name) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newMovementName,
                    onValueChange = { newMovementName = it },
                    placeholder = { Text("New movement name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = newMovementName.trim().isNotEmpty(),
                    onClick = {
                        val nameToCreate = newMovementName.trim()
                        newMovementName = ""
                        onCreateExercise(nameToCreate) { created ->
                            if (created.id !in selectedIds) {
                                selectedIds.add(created.id)
                            }
                        }
                    }
                ) {
                    Text("+ Add")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = title.trim().isNotEmpty(),
                    onClick = {
                        onSave(
                            title.trim(),
                            instructions.trim(),
                            selectedIds.toList()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (original == null) "Create Block" else "Save Block")
                }
            }
        }
    }
}

internal fun formatRoutineBlockCardExercises(
    blk: RoutineBlock,
    exercises: List<Exercise>
): List<String> {
    val exercisesById = exercises.associateBy { it.id }
    return RoutineTargetCodec.decodeBlockExercises(blk, exercisesById).map { (exId, spec) ->
        val exName = exercisesById[exId]?.name ?: "Exercise #$exId"
        "$exName · ${RoutineTargetCodec.formatExerciseTargetBadge(spec, blk.setsCount)}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineBlockCard(
    idx: Int,
    blk: RoutineBlock,
    exercises: List<Exercise>
) {
    val isStructuredV2 = RoutineTargetCodec.hasStructuredHeader(blk.notes)
    val cleanNotes = RoutineTargetCodec.extractCleanNotes(blk.notes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${idx + 1}. ${blk.name.ifBlank { blk.kind.label }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    blk.kind.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        val detailLine = if (isStructuredV2) {
            listOfNotNull(
                blk.format.takeIf { it.isNotBlank() }?.let { "Format: $it" },
                "${blk.setsCount} ${if (blk.setsCount == 1) "set" else "sets"}"
            ).joinToString(" · ")
        } else {
            listOfNotNull(
                blk.format.takeIf { it.isNotBlank() }?.let { "Format: $it" },
                if (blk.targetRepsScheme.isNotBlank()) "Reps: ${blk.targetRepsScheme}" else null,
                if (blk.setsCount > 1) "${blk.setsCount} sets" else null
            ).joinToString(" · ")
        }

        if (detailLine.isNotBlank()) {
            Text(detailLine, style = MaterialTheme.typography.bodySmall)
        }

        val (wInt, rInt, sMetric) = parseMetabolicNotes(cleanNotes)
        if (wInt.isNotBlank() || rInt.isNotBlank()) {
            val metaSummary = listOfNotNull(
                if (wInt.isNotBlank()) "Work: $wInt" else null,
                if (rInt.isNotBlank()) "Rest: $rInt" else null,
                "Score: ${sMetric.label}"
            ).joinToString("  ·  ")
            Text(
                text = metaSummary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (isStructuredV2) {
            val formattedPills = remember(blk, exercises) { formatRoutineBlockCardExercises(blk, exercises) }
            if (formattedPills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    formattedPills.forEach { badgeText ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            if (cleanNotes.isNotBlank() && wInt.isBlank() && rInt.isBlank()) {
                Text(
                    text = cleanNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            val targetExNames = blk.exerciseIdsCsv.split(",")
                .mapNotNull { idStr -> idStr.trim().toLongOrNull() }
                .mapNotNull { id -> exercises.firstOrNull { it.id == id }?.name }

            if (targetExNames.isNotEmpty()) {
                if (targetExNames.size > 1 || blk.kind == BlockKind.COMPLEX || blk.kind == BlockKind.SUPERSET) {
                    val seqText = formatExerciseSequence(targetExNames, blk.targetRepsScheme)
                    Text(
                        text = "Sequence: $seqText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        targetExNames.forEach { exName ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(exName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ExerciseEditorDialog(
    original: Exercise?,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var category by remember { mutableStateOf(original?.category ?: ExerciseCategory.BARBELL) }
    var metric by remember { mutableStateOf(original?.metricType ?: MetricType.WEIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Dropdown(
                    label = "Category",
                    options = ExerciseCategory.entries,
                    selected = category,
                    labelOf = { it.label },
                    onSelect = { category = it }
                )
                Dropdown(
                    label = "Tracked metric",
                    options = MetricType.entries,
                    selected = metric,
                    labelOf = { "${it.label} (${it.defaultUnit})" + if (it.tracksRepMax) " · RM" else "" },
                    onSelect = { metric = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (original ?: Exercise(
                            name = "",
                            category = category,
                            metricType = metric,
                            unit = metric.defaultUnit,
                            tracksRepMax = metric.tracksRepMax
                        )).copy(
                            name = name.trim(),
                            category = category,
                            metricType = metric,
                            unit = metric.defaultUnit,
                            tracksRepMax = metric.tracksRepMax
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatExerciseSequence(exNames: List<String>, repScheme: String): String {
    if (exNames.isEmpty()) return ""
    if (exNames.size == 1) return exNames.first()
    val repParts = repScheme.split(Regex("[+/\\-,]")).map { it.trim() }.filter { it.isNotBlank() }
    return exNames.mapIndexed { idx, name ->
        val reps = repParts.getOrNull(idx) ?: repParts.lastOrNull()
        if (reps != null && reps.isNotBlank()) "$name ($reps reps)" else name
    }.joinToString("  ➔  ")
}

fun parseMetabolicNotes(notes: String): Triple<String, String, MetricType> {
    var work = ""
    var rest = ""
    var metric = MetricType.TIME
    notes.lines().firstOrNull()?.split("|")?.forEach { part ->
        val trimmed = part.trim()
        when {
            trimmed.startsWith("Work:", ignoreCase = true) -> work = trimmed.substringAfter("Work:").trim()
            trimmed.startsWith("Rest:", ignoreCase = true) -> rest = trimmed.substringAfter("Rest:").trim()
            trimmed.startsWith("Score:", ignoreCase = true) -> {
                val mStr = trimmed.substringAfter("Score:").trim()
                metric = runCatching { MetricType.valueOf(mStr.uppercase()) }.getOrDefault(MetricType.TIME)
            }
        }
    }
    return Triple(work, rest, metric)
}

fun formatMetabolicNotes(work: String, rest: String, metric: MetricType, notes: String): String {
    val metaParts = mutableListOf<String>()
    if (work.isNotBlank()) metaParts.add("Work: ${work.trim()}")
    if (rest.isNotBlank()) metaParts.add("Rest: ${rest.trim()}")
    metaParts.add("Score: ${metric.name}")
    val metaHeader = metaParts.joinToString(" | ")
    val userCustomNotes = notes.lines().filter { !it.contains("Score:") && !it.contains("Work:") && !it.contains("Rest:") }.joinToString("\n").trim()
    return if (userCustomNotes.isNotBlank()) "$metaHeader\n$userCustomNotes" else metaHeader
}

@Composable
private fun ShareWorkoutModalDialog(shareCode: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareLink = "https://crosstraining.app/w/$shareCode"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Share this 6-character code with gym partners to let them import your workout:")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        shareCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text("Or send the share link via WhatsApp, Email, or SMS:", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out my workout in CrossTraining! Share Code: $shareCode\nLink: $shareLink")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Workout via")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share via App...")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ImportWorkoutModalDialog(
    onDismiss: () -> Unit,
    onImportCode: (String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Shared Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the 6-character Share Code provided by a gym partner or coach:")
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase() },
                    label = { Text("Share Code (e.g. FRAN21)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = codeInput.trim().length >= 4,
                onClick = { onImportCode(codeInput.trim()) }
            ) { Text("Import Workout") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommunityWorkoutsModalDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onImportPayload: (SharedWorkoutPayload) -> Unit
) {
    val communityWorkouts by viewModel.communityWorkouts.collectAsStateWithLifecycle()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.fetchCommunityWorkouts()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community Shared Workouts") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Text("Loading community workouts...", style = MaterialTheme.typography.bodyMedium)
                } else if (communityWorkouts.isEmpty()) {
                    Text(
                        "No community workouts shared yet. Be the first to share your daily routine!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    communityWorkouts.forEach { workout ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(workout.routineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(workout.shareCode, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (workout.description.isNotBlank()) {
                                    Text(workout.description, style = MaterialTheme.typography.bodySmall)
                                }

                                Text(
                                    "Blocks: ${workout.blocks.size} · Format: ${workout.defaultFormat.ifBlank { "Custom" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )

                                Button(
                                    onClick = { onImportPayload(workout) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import to My Library")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
