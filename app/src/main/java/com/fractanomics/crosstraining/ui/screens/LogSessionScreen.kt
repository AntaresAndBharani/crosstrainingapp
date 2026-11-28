package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.AppViewModel

/** The Log tab: a persistent active workout draft that clears itself after save or explicit discard. */
@Composable
fun LogSessionScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val draftRevision by viewModel.activeWorkoutDraftRevision.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    // The persisted draft is only a hydration source for SessionEditorBody's remember(key) state,
    // so snapshot it once per (mode, revision) / screen entry instead of collecting it. Collecting
    // would recompose the entire editor on every keystroke and every scroll frame, because each
    // editor change round-trips through updateActiveWorkoutDraft() as a new snapshot.
    val activeWorkoutDraft = remember(demoMode, draftRevision) { viewModel.activeWorkoutDraft.value }
    // Plain value (not the State delegate) so callbacks captured by the editor's effects keep the
    // mode they were composed for, even when invoked after a Real/Demo toggle.
    val composedDemoMode: Boolean = demoMode

    SessionEditorBody(
        viewModel = viewModel,
        outerPadding = outerPadding,
        screenTitle = "Log Session",
        seed = remember { emptySessionSeed() },
        seedKey = "log-$demoMode-$draftRevision",
        saveLabel = "Save Session",
        clearAfterSave = true,
        onBack = null,
        onOpenDrawer = onOpenDrawer,
        onOpenTimer = onOpenTimer,
        isActiveLogTab = true,
        persistedDraft = activeWorkoutDraft,
        // demoMode is captured per seedKey, so stale emissions/dispose-flushes from the previous
        // mode's editor are rejected instead of overwriting the new mode's draft slot.
        onDraftChanged = { viewModel.updateActiveWorkoutDraft(it, expectedDemoMode = composedDemoMode) },
        onFlushDraft = { viewModel.flushActiveWorkoutDraftNow(it, expectedDemoMode = composedDemoMode) },
        onDiscardDraft = { viewModel.clearActiveWorkoutDraft() },
        onSubmit = { viewModel.saveSession(it) }
    )
}
