package com.enil.logez

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enil.logez.app.navigation.LogEzDestination
import com.enil.logez.app.navigation.LogEzNavHost
import com.enil.logez.core.designsystem.LogEzTheme

/** App root: theme + the 3-tab Scaffold. Theme is dark-only (Owner directive) — no mode switching. */
@Composable
fun LogEzApp() {
    LogEzTheme {
        val navController = rememberNavController()

        Scaffold(
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
                    NavigationBar {
                        LogEzDestination.entries.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == destination.route
                            } == true

                            NavigationBarItem(
                                selected = selected,
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
            },
        ) { innerPadding ->
            LogEzNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
