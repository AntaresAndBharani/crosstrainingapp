package com.fractanomics.crosstraining.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fractanomics.crosstraining.data.firebase.AuthUser
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.screens.CyclesScreen
import com.fractanomics.crosstraining.ui.screens.HistoryScreen
import com.fractanomics.crosstraining.ui.screens.LibraryScreen
import com.fractanomics.crosstraining.ui.screens.LogSessionScreen
import com.fractanomics.crosstraining.ui.screens.LoginWelcomeScreen
import com.fractanomics.crosstraining.ui.screens.ProfileScreen
import com.fractanomics.crosstraining.ui.screens.ProgressScreen
import com.fractanomics.crosstraining.ui.screens.SessionEditorScreen
import com.fractanomics.crosstraining.ui.screens.TimerScreen
import kotlinx.coroutines.launch

/** Primary daily driver destinations shown in the fixed bottom navigation bar. */
enum class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    LOG("log", "Log", Icons.Filled.FitnessCenter),
    HISTORY("history", "History", Icons.Filled.History),
    PROGRESS("progress", "Progress", Icons.Filled.BarChart)
}

/** Drawer destinations grouped by purpose. */
enum class DrawerItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val section: DrawerSection
) {
    // Workouts Section
    LOG("log", "Log Workout", "Active training session", Icons.Filled.FitnessCenter, DrawerSection.WORKOUTS),
    HISTORY("history", "Session History", "Past logged workouts", Icons.Filled.History, DrawerSection.WORKOUTS),
    PROGRESS("progress", "Progress & Analytics", "Charts, volume & PRs", Icons.Filled.BarChart, DrawerSection.WORKOUTS),

    // Tools & Planning Section
    TIMER("timer", "Workout Timers", "Stopwatch, EMOM, Tabata", Icons.Filled.Timer, DrawerSection.TOOLS),
    LIBRARY("library", "Movement Library", "Exercises & daily routines", Icons.AutoMirrored.Filled.MenuBook, DrawerSection.TOOLS),
    CYCLES("cycles", "Training Cycles", "Periodization & blocks", Icons.Filled.CalendarMonth, DrawerSection.TOOLS),

    // Account & Settings
    PROFILE("profile", "Profile & Sync", "Account & cloud backup", Icons.Filled.AccountCircle, DrawerSection.ACCOUNT)
}

enum class DrawerSection(val header: String) {
    WORKOUTS("Workouts"),
    TOOLS("Tools & Planning"),
    ACCOUNT("Account")
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var guestModeAccepted by remember { mutableStateOf(false) }
    val isAuthenticated = !authUser?.email.isNullOrBlank() || guestModeAccepted

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (!isAuthenticated) {
        LoginWelcomeScreen(
            viewModel = viewModel,
            snackbar = snackbarHostState,
            onContinueAsGuest = { guestModeAccepted = true }
        )
    } else {
        val backStackEntry = navController.currentBackStackEntryAsState().value
        val currentRoute = backStackEntry?.destination?.route

        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        val openTimer: () -> Unit = {
            navController.navigate(DrawerItem.TIMER.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(310.dp)
                ) {
                    AppDrawerContent(
                        authUser = authUser,
                        currentRoute = currentRoute,
                        demoMode = demoMode,
                        onNavigate = { route ->
                            scope.launch { drawerState.close() }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onCloseDrawer = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            Scaffold(
                bottomBar = {
                    Column {
                        if (demoMode) DemoBanner()
                        PrimaryNavigationBar(
                            destinations = BottomDestination.entries,
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
                    startDestination = BottomDestination.LOG.route
                ) {
                    composable(BottomDestination.LOG.route) {
                        LogSessionScreen(
                            viewModel = viewModel,
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
                        )
                    }
                    composable(BottomDestination.HISTORY.route) {
                        HistoryScreen(
                            viewModel = viewModel,
                            outerPadding = innerPadding,
                            onOpenEditor = { sessionId, copy ->
                                navController.navigate("sessionEditor/$sessionId/$copy")
                            },
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
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
                    composable(BottomDestination.PROGRESS.route) {
                        ProgressScreen(
                            viewModel = viewModel,
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
                        )
                    }
                    composable(DrawerItem.CYCLES.route) {
                        CyclesScreen(
                            viewModel = viewModel,
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
                        )
                    }
                    composable(DrawerItem.LIBRARY.route) {
                        LibraryScreen(
                            viewModel = viewModel,
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
                        )
                    }
                    composable(DrawerItem.TIMER.route) {
                        TimerScreen(
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer
                        )
                    }
                    composable(DrawerItem.PROFILE.route) {
                        ProfileScreen(
                            viewModel = viewModel,
                            snackbar = snackbarHostState,
                            outerPadding = innerPadding,
                            onOpenDrawer = openDrawer,
                            onOpenTimer = openTimer
                        )
                    }
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

/** Modern fixed 3-item Material 3 Bottom Navigation Bar. */
@Composable
private fun PrimaryNavigationBar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    onNavigate: (BottomDestination) -> Unit
) {
    NavigationBar(
        tonalElevation = 6.dp
    ) {
        destinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label
                    )
                },
                label = {
                    Text(
                        dest.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

/** Slide-out Navigation Drawer content with categorized sections. */
@Composable
private fun AppDrawerContent(
    authUser: AuthUser?,
    currentRoute: String?,
    demoMode: Boolean,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Drawer Header with User/App Branding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        "CrossTraining",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val email = authUser?.email
                    Text(
                        if (!email.isNullOrBlank()) email else "Guest Athlete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(Icons.Filled.Close, contentDescription = "Close Menu")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Render Categorized Drawer Sections
        DrawerSection.entries.forEach { section ->
            Text(
                text = section.header.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            val sectionItems = DrawerItem.entries.filter { it.section == section }
            sectionItems.forEach { item ->
                val selected = currentRoute == item.route
                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = {
                        Column {
                            Text(item.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "v2.4.2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (demoMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "DEMO ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
