package com.enil.logez.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzIcons

/**
 * The three bottom-tab roots (PHASE2_PLAN.md §4). Sub-screens reached from any tab are separate
 * routes that hide the bottom bar — only these three ever show it.
 */
enum class LogEzDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    History(route = "history", labelRes = R.string.nav_history, icon = LogEzIcons.History),
    Workout(route = "workout", labelRes = R.string.nav_workout, icon = LogEzIcons.Workout),
    Profile(route = "profile", labelRes = R.string.nav_profile, icon = LogEzIcons.Profile),
}
