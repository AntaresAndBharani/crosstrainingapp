package com.fractanomics.crosstraining.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenEditor: (sessionId: Long, copy: Boolean) -> Unit,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()

    val exerciseNames = exercises.associate { it.id to it.name }
    val cycleNames = cycles.associate { it.id to it.name }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCycleFilter by remember { mutableStateOf<Long?>(null) }

    val filteredSessions = remember(sessions, searchQuery, selectedCycleFilter) {
        sessions.filter { s ->
            val matchesCycle = selectedCycleFilter == null || s.session.cycleId == selectedCycleFilter
            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isBlank() ||
                s.session.title.lowercase().contains(query) ||
                s.session.notes.lowercase().contains(query) ||
                s.blocks.any { b ->
                    b.block.name.lowercase().contains(query) ||
                    (b.block.mainExerciseId?.let { exerciseNames[it]?.lowercase() }?.contains(query) == true)
                }
            matchesCycle && matchesSearch
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Session History", fontWeight = FontWeight.Bold) },
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
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxWidth()
        ) {
            // Sticky Search & Filter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search workouts, lifts, or notes...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                if (cycles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedCycleFilter == null,
                            onClick = { selectedCycleFilter = null },
                            label = { Text("All Cycles", style = MaterialTheme.typography.labelSmall) }
                        )
                        cycles.forEach { cycle ->
                            FilterChip(
                                selected = selectedCycleFilter == cycle.id,
                                onClick = {
                                    selectedCycleFilter = if (selectedCycleFilter == cycle.id) null else cycle.id
                                },
                                label = { Text(cycle.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            if (filteredSessions.isEmpty()) {
                EmptyState(
                    if (searchQuery.isNotBlank() || selectedCycleFilter != null) "No workouts match your filter."
                    else "No sessions logged yet.\nUse the Log tab to add your first one.",
                    Modifier.fillMaxWidth()
                )
            } else {
                ScreenList {
                    items(filteredSessions, key = { it.session.id }) { item ->
                        CompactSessionCard(
                            item = item,
                            exerciseNames = exerciseNames,
                            cycleName = cycleNames[item.session.cycleId],
                            onEdit = { onOpenEditor(item.session.id, false) },
                            onCopy = { onOpenEditor(item.session.id, true) },
                            onDelete = { viewModel.deleteSession(item.session) }
                        )
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactSessionCard(
    item: SessionWithBlocks,
    exerciseNames: Map<Long, String>,
    cycleName: String?,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val s = item.session
    var isExpanded by remember { mutableStateOf(false) }

    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }
    val dayStr = remember(s.date) { s.date.format(dayFormatter) }
    val monthStr = remember(s.date) { s.date.format(monthFormatter).uppercase() }

    val totalSets = remember(item.blocks) { item.blocks.sumOf { it.sets.size } }

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
            // Collapsed Hero Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compact Date Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            monthStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            dayStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Title & Movement Summary
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            s.title.ifBlank { "Workout Session" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (cycleName != null) {
                            Text(
                                "· $cycleName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    // Summary chips / text of movements
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.blocks.sortedBy { it.block.position }.forEach { bws ->
                            val title = bws.block.name.ifBlank { bws.block.mainExerciseId?.let { exerciseNames[it] } ?: "Block" }
                            val maxWeight = bws.sets.mapNotNull { it.weight ?: it.metricValue }.maxOrNull()
                            val weightStr = if (maxWeight != null && maxWeight > 0.0) " (${maxWeight.trimmed()}kg)" else ""
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    "$title$weightStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Expand indicator
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    item.blocks.sortedBy { it.block.position }.forEachIndexed { idx, bws ->
                        val b = bws.block
                        val title = b.name.ifBlank { b.mainExerciseId?.let { exerciseNames[it] } ?: "Block ${idx + 1}" }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (b.kind != BlockKind.STRENGTH) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(b.kind.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            if (b.description.isNotBlank()) {
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                            }

                            // Sets Grid
                            if (bws.sets.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    bws.sets.sortedBy { it.position }.forEachIndexed { setIdx, st ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                st.isFailed -> MaterialTheme.colorScheme.errorContainer
                                                st.isWarmup -> Color(0xFFFEF3C7)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                "#${setIdx + 1}: ${setToken(st)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                color = when {
                                                    st.isFailed -> MaterialTheme.colorScheme.onErrorContainer
                                                    st.isWarmup -> Color(0xFFB45309)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Metcon Result
                            val result = listOfNotNull(
                                b.resultText.ifBlank { null },
                                b.resultValue?.let { "= ${it.trimmed()}" }
                            ).joinToString(" ")
                            if (result.isNotBlank()) {
                                Text(
                                    "Score: $result",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (s.notes.isNotBlank()) {
                        Text(
                            "Notes: ${s.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Action Buttons Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(onClick = onCopy) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Today")
                        }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
