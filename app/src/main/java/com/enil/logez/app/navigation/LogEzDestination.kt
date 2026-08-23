package com.enil.logez.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.enil.logez.R

/**
 * The three bottom-tab roots (PHASE2_PLAN.md §4). Sub-screens reached from any tab are separate
 * routes that hide the bottom bar — only these three ever show it.
 */
enum class LogEzDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    History(route = "history", labelRes = R.string.nav_history, icon = Icons.Filled.History),
    Workout(route = "workout", labelRes = R.string.nav_workout, icon = Icons.Filled.FitnessCenter),
    Profile(route = "profile", labelRes = R.string.nav_profile, icon = Icons.Filled.Person),
}
