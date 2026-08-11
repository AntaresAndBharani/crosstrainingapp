package com.fractanomics.crosstraining.ui.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.screens.CyclesScreen
import com.fractanomics.crosstraining.ui.screens.HistoryScreen
import com.fractanomics.crosstraining.ui.screens.LibraryScreen
import com.fractanomics.crosstraining.ui.screens.LogSessionScreen
import com.fractanomics.crosstraining.ui.screens.ProgressScreen
import com.fractanomics.crosstraining.ui.screens.SessionEditorScreen

import androidx.compose.material.icons.filled.Timer
import com.fractanomics.crosstraining.ui.screens.TimerScreen

import androidx.compose.material.icons.filled.AccountCircle
import com.fractanomics.crosstraining.ui.screens.ProfileScreen
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.fractanomics.crosstraining.ui.screens.LoginWelcomeScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    LOG("log", "Log", Icons.Filled.FitnessCenter),
    HISTORY("history", "History", Icons.Filled.History),
    PROGRESS("progress", "Progress", Icons.Filled.BarChart),
    CYCLES("cycles", "Cycles", Icons.Filled.CalendarMonth),
    LIBRARY("library", "Library", Icons.Filled.MenuBook),
    TIMER("timer", "Timer", Icons.Filled.Timer),
    PROFILE("profile", "Profile", Icons.Filled.AccountCircle)
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val destinations = Destination.entries
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var guestModeAccepted by remember { mutableStateOf(false) }

    val isAuthenticated = !authUser?.email.isNullOrBlank() || guestModeAccepted

    if (!isAuthenticated) {
        LoginWelcomeScreen(
            viewModel = viewModel,
            snackbar = snackbarHostState,
            onContinueAsGuest = { guestModeAccepted = true }
        )
    } else {
        Scaffold(
            bottomBar = {
                Column {
                    if (demoMode) DemoBanner()
                    val backStackEntry = navController.currentBackStackEntryAsState().value
                    val currentRoute = backStackEntry?.destination?.route
                    ScrollableNavigationBar(
                        destinations = destinations,
                        currentRoute = currentRoute,
                        onNavigate = { dest ->
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.LOG.route
        ) {
            composable(Destination.LOG.route) {
                LogSessionScreen(viewModel, innerPadding)
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    outerPadding = innerPadding,
                    onOpenEditor = { sessionId, copy ->
                        navController.navigate("sessionEditor/$sessionId/$copy")
                    }
                )
            }
            composable(
                route = "sessionEditor/{sessionId}/{copy}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType },
                    navArgument("copy") { type = NavType.BoolType }
                )
            ) { entry ->
                SessionEditorScreen(
                    viewModel = viewModel,
                    outerPadding = innerPadding,
                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                    copy = entry.arguments?.getBoolean("copy") ?: false,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destination.PROGRESS.route) {
                ProgressScreen(viewModel, innerPadding)
            }
            composable(Destination.CYCLES.route) {
                CyclesScreen(viewModel, innerPadding)
            }
            composable(Destination.LIBRARY.route) {
                LibraryScreen(viewModel, innerPadding)
            }
            composable(Destination.TIMER.route) {
                TimerScreen(innerPadding)
            }
            composable(Destination.PROFILE.route) {
                ProfileScreen(viewModel, snackbarHostState)
            }
        }
    }
}
}

/** Persistent strip shown above the nav bar while demo data is active. */
@Composable
private fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Demo data — your real data is untouched",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScrollableNavigationBar(
    destinations: List<Destination>,
    currentRoute: String?,
    onNavigate: (Destination) -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { dest ->
                    val selected = currentRoute == dest.route
                    Surface(
                        selected = selected,
                        onClick = { onNavigate(dest) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                dest.icon,
                                contentDescription = dest.label,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                dest.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
