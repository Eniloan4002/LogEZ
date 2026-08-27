package com.enil.logez

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enil.logez.app.navigation.LogEzDestination
import com.enil.logez.app.navigation.LogEzNavHost
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.feature.workout.WorkoutMiniBar
import com.enil.logez.feature.workout.WorkoutRoutes
import com.enil.logez.feature.workout.rememberStartWorkoutSession

/** App root: theme + the 3-tab Scaffold. Theme is dark-only (Owner directive) — no mode switching. */
@Composable
fun LogEzApp() {
    LogEzTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val resumedSnackbarMessage = stringResource(R.string.workout_mini_bar_resumed_snackbar)

        // §9.5 cold-start recovery: process/service died (swipe-from-recents, force-stop, crash)
        // while a workout was IN_PROGRESS -- Room already has the data; this restarts the service
        // and lands the user back in the Logger, exactly once per cold start.
        val startupViewModel: AppStartupViewModel = hiltViewModel()
        val recoveredWorkoutId by startupViewModel.recoveredWorkoutId.collectAsStateWithLifecycle()
        val resumeRecoveredSession = rememberStartWorkoutSession(onNavigateToLogger = { workoutId ->
            navController.navigate(WorkoutRoutes.logger(workoutId))
        })
        LaunchedEffect(recoveredWorkoutId) {
            val workoutId = recoveredWorkoutId ?: return@LaunchedEffect
            startupViewModel.consumeRecovery()
            resumeRecoveredSession(workoutId)
            snackbarHostState.showSnackbar(resumedSnackbarMessage)
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                // Spine navigation rule: only the three tab roots ever show the bottom bar —
                // every sub-screen (exercise library/detail/editor, and more as later
                // milestones land) hides it.
                val onTabRoot = LogEzDestination.entries.any { destination ->
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                }

                if (onTabRoot) {
                    Column {
                        WorkoutMiniBar(onExpand = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) })
                        NavigationBar {
                            LogEzDestination.entries.forEach { destination ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == destination.route
                                } == true

                                NavigationBarItem(
                                    selected = selected,
                                    // Material3's default selected color is `secondary` (DeepGreen
                                    // -- deliberately muted, see ADR-0003), which read as dull next
                                    // to the vibrant primary used everywhere else. The active tab
                                    // should be the SAME vibrant green as the CTAs and chart fills.
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = stringResource(destination.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            LogEzNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
