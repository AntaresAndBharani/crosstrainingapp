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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.analytics.BodyWeightMatchKind
import com.fractanomics.crosstraining.data.analytics.RelativeStrengthAnalytics
import com.fractanomics.crosstraining.data.analytics.ResolvedBodyWeight
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatBlockSetToken
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
    val weightEntries by viewModel.weightEntries.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()

    val exerciseNames = exercises.associate { it.id to it.name }
    val exercisesById = exercises.associateBy { it.id }
    val cycleNames = cycles.associate { it.id to it.name }
    val activeWeightIndex = remember(weightEntries) {
        RelativeStrengthAnalytics.buildActiveWeightIndex(weightEntries)
    }

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
                        val resolvedBw = remember(item.session.date, activeWeightIndex) {
                            RelativeStrengthAnalytics.resolveBodyWeightForDate(
                                targetDate = item.session.date,
                                weightIndex = activeWeightIndex
                            )
                        }
                        CompactSessionCard(
                            item = item,
                            exerciseNames = exerciseNames,
                            exercisesById = exercisesById,
                            cycleName = cycleNames[item.session.cycleId],
                            resolvedBw = resolvedBw,
                            weightUnit = weightUnit,
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

internal sealed class HistoryBlockItem {
    data class Standalone(val index: Int, val blockWithSets: BlockWithSets) : HistoryBlockItem()
    data class SubBlockGroup(
        val subBlockName: String,
        val format: String,
        val items: List<Pair<Int, BlockWithSets>>
    ) : HistoryBlockItem() {
        val totalRounds: Int
            get() = items.maxOfOrNull { it.second.sets.size } ?: 0
    }
}

internal fun groupHistoryBlocks(blocks: List<BlockWithSets>): List<HistoryBlockItem> {
    val result = mutableListOf<HistoryBlockItem>()
    var i = 0
    while (i < blocks.size) {
        val currentBlock = blocks[i]
        val trimmedSub = currentBlock.block.subBlock.trim()
        val trimmedSection = currentBlock.block.section.trim()

        if (trimmedSub.isBlank()) {
            result.add(HistoryBlockItem.Standalone(i, currentBlock))
            i++
        } else {
            val groupItems = mutableListOf<Pair<Int, BlockWithSets>>()
            groupItems.add(i to currentBlock)
            var j = i + 1
            while (j < blocks.size) {
                val nextBlock = blocks[j]
                if (nextBlock.block.subBlock.trim().equals(trimmedSub, ignoreCase = true) &&
                    nextBlock.block.section.trim().equals(trimmedSection, ignoreCase = true)
                ) {
                    groupItems.add(j to nextBlock)
                    j++
                } else {
                    break
                }
            }
            val groupFormat = groupItems.firstOrNull { it.second.block.format.isNotBlank() }?.second?.block?.format ?: ""
            result.add(
                HistoryBlockItem.SubBlockGroup(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactSessionCard(
    item: SessionWithBlocks,
    exerciseNames: Map<Long, String>,
    exercisesById: Map<Long, Exercise>,
    cycleName: String?,
    resolvedBw: ResolvedBodyWeight = ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null),
    weightUnit: String = "kg",
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
                            val blockEx = bws.block.mainExerciseId?.let { exercisesById[it] }
                            val title = bws.block.name.ifBlank { blockEx?.name ?: bws.block.mainExerciseId?.let { exerciseNames[it] } ?: "Block" }
                            val blockUnit = blockEx?.unit?.ifBlank { weightUnit } ?: weightUnit
                            val workingWeights = bws.sets
                                .filter { !it.isWarmup && !it.isFailed }
                                .mapNotNull { it.weight?.takeIf { w -> w > 0.0 } }
                            val isWeightedBlock = RelativeStrengthAnalytics.isWeightedUnit(blockUnit) && workingWeights.isNotEmpty()
                            val weightStr = if (isWeightedBlock) {
                                val topW = workingWeights.max()
                                val topPct = RelativeStrengthAnalytics.calculatePercentBw(topW, blockUnit, resolvedBw)
                                val pctText = RelativeStrengthAnalytics.formatPercentBw(topPct, resolvedBw.isEstimated)
                                " (${topW.trimmed()} $blockUnit · $pctText)"
                            } else {
                                val maxWeight = bws.sets.mapNotNull { it.weight ?: it.metricValue }.maxOrNull()
                                if (maxWeight != null && maxWeight > 0.0) " (${maxWeight.trimmed()}kg)" else ""
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    "$title$weightStr",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
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

                    val hasWeightedBlock = remember(item.blocks, exercisesById, weightUnit) {
                        item.blocks.any { bws ->
                            val ex = bws.block.mainExerciseId?.let { exercisesById[it] }
                            val u = ex?.unit?.ifBlank { weightUnit } ?: weightUnit
                            RelativeStrengthAnalytics.isWeightedUnit(u) &&
                                bws.sets.any { !it.isWarmup && !it.isFailed && (it.weight ?: 0.0) > 0.0 }
                        }
                    }
                    if (hasWeightedBlock) {
                        Text(
                            text = RelativeStrengthAnalytics.formatBodyWeightAnchor(resolvedBw, weightUnit),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    val historyItems = remember(item.blocks) {
                        groupHistoryBlocks(item.blocks.sortedBy { it.block.position })
                    }
                    var prevSection: String? = null
                    historyItems.forEach { hItem ->
                        val currentSection = when (hItem) {
                            is HistoryBlockItem.Standalone -> hItem.blockWithSets.block.section.trim()
                            is HistoryBlockItem.SubBlockGroup -> hItem.items.first().second.block.section.trim()
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

                        when (hItem) {
                            is HistoryBlockItem.Standalone -> {
                                HistoryBlockCard(
                                    idx = hItem.index,
                                    bws = hItem.blockWithSets,
                                    exerciseNames = exerciseNames,
                                    exercisesById = exercisesById,
                                    resolvedBw = resolvedBw,
                                    weightUnit = weightUnit
                                )
                            }
                            is HistoryBlockItem.SubBlockGroup -> {
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
                                                    text = hItem.subBlockName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (hItem.format.isNotBlank()) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = hItem.format,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                val rounds = hItem.totalRounds
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "$rounds ${if (rounds == 1) "Round" else "Rounds"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Child blocks inside sub-block container
                                        hItem.items.forEach { (childIdx, childBws) ->
                                            HistoryBlockCard(
                                                idx = childIdx,
                                                bws = childBws,
                                                exerciseNames = exerciseNames,
                                                exercisesById = exercisesById,
                                                resolvedBw = resolvedBw,
                                                weightUnit = weightUnit
                                            )
                                        }
                                    }
                                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryBlockCard(
    idx: Int,
    bws: BlockWithSets,
    exerciseNames: Map<Long, String>,
    exercisesById: Map<Long, Exercise>,
    resolvedBw: ResolvedBodyWeight = ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null),
    weightUnit: String = "kg"
) {
    val b = bws.block
    val blockEx = b.mainExerciseId?.let { exercisesById[it] }
    val title = b.name.ifBlank { blockEx?.name ?: b.mainExerciseId?.let { exerciseNames[it] } ?: "Block ${idx + 1}" }
    val blockUnit = blockEx?.unit?.ifBlank { weightUnit } ?: weightUnit
    val workingWeightedSets = remember(bws.sets, blockUnit) {
        if (RelativeStrengthAnalytics.isWeightedUnit(blockUnit)) {
            bws.sets.filter { !it.isWarmup && !it.isFailed && (it.weight ?: 0.0) > 0.0 }
        } else {
            emptyList()
        }
    }

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

        // Sets Grid (raw set tokens without per-set % BW clutter)
        if (bws.sets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bws.sets.sortedBy { it.position }.forEachIndexed { setIdx, st ->
                    val setLabelNum = st.groupIndex?.takeIf { it > 0 } ?: (setIdx + 1)
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
                            "#${setLabelNum}: ${formatBlockSetToken(st, b.notes, blockEx)}",
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

        if (workingWeightedSets.isNotEmpty()) {
            val weights = workingWeightedSets.mapNotNull { it.weight }
            val topWeight = weights.max()
            val avgWeight = weights.average()
            val totalVol = workingWeightedSets.sumOf { (it.weight ?: 0.0) * it.reps.coerceAtLeast(1) }
            val topPct = RelativeStrengthAnalytics.calculatePercentBw(topWeight, blockUnit, resolvedBw)
            val avgPct = RelativeStrengthAnalytics.calculatePercentBw(avgWeight, blockUnit, resolvedBw)
            val volPct = RelativeStrengthAnalytics.calculatePercentBw(totalVol, blockUnit, resolvedBw)
            val summaryText = listOf(
                "Top ${topWeight.trimmed()} $blockUnit (${RelativeStrengthAnalytics.formatPercentBw(topPct, resolvedBw.isEstimated)})",
                "Avg ${avgWeight.trimmed()} $blockUnit (${RelativeStrengthAnalytics.formatPercentBw(avgPct, resolvedBw.isEstimated)})",
                "Vol ${totalVol.trimmed()} $blockUnit (${RelativeStrengthAnalytics.formatPercentBw(volPct, resolvedBw.isEstimated)})"
            ).joinToString(" · ")
            val summaryTalkBack = listOf(
                "Top ${topWeight.trimmed()} $blockUnit, ${RelativeStrengthAnalytics.formatTalkBackPercentBw(topPct, resolvedBw)}",
                "average ${avgWeight.trimmed()} $blockUnit, ${RelativeStrengthAnalytics.formatTalkBackPercentBw(avgPct, resolvedBw)}",
                "volume ${totalVol.trimmed()} $blockUnit, ${RelativeStrengthAnalytics.formatTalkBackPercentBw(volPct, resolvedBw)}"
            ).joinToString("; ")
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = summaryTalkBack
                }
            )
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
