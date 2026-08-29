package com.fractanomics.crosstraining.ui.navigation

import android.content.Intent
import com.fractanomics.crosstraining.ui.timer.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles incoming navigation intents from foreground service notifications,
 * deep-links, and activity lifecycle events (onCreate / onNewIntent).
 */
class NavigationIntentHandler(
    initialDestination: String? = null
) {
    private val _pendingDestination = MutableStateFlow<String?>(initialDestination)
    val pendingDestination: StateFlow<String?> = _pendingDestination.asStateFlow()

    fun handleIntent(intent: Intent?) {
        val dest = extractDestination(intent)
        if (dest != null) {
            _pendingDestination.value = dest
        }
    }

    fun handleDestination(destination: String?) {
        if (destination != null) {
            _pendingDestination.value = destination
        }
    }

    fun onDestinationHandled() {
        _pendingDestination.value = null
    }

    companion object {
        fun extractDestination(intent: Intent?): String? {
            return try {
                intent?.getStringExtra(TimerService.EXTRA_NAVIGATE_TO)
            } catch (_: Exception) {
                null
            }
        }
    }
}
