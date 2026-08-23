package com.enil.logez.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.enil.logez.core.common.ThemeMode
import com.enil.logez.feature.analytics.ProfileScreen
import com.enil.logez.feature.history.HistoryScreen
import com.enil.logez.feature.routines.WorkoutTabScreen

@Composable
fun LogEzNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LogEzDestination.History.route,
        modifier = modifier,
    ) {
        composable(LogEzDestination.History.route) { HistoryScreen() }
        composable(LogEzDestination.Workout.route) { WorkoutTabScreen() }
        composable(LogEzDestination.Profile.route) {
            ProfileScreen(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        }
    }
}
