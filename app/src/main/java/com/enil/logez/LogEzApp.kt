package com.enil.logez

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.enil.logez.app.navigation.LogEzDestination
import com.enil.logez.app.navigation.LogEzNavHost
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.feature.activity.ActivityTrackingRoutes
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.onboarding.FirstRunGateState
import com.enil.logez.feature.onboarding.FirstRunGateViewModel
import com.enil.logez.feature.onboarding.FirstRunHost
import com.enil.logez.feature.workout.LocalOpenNotificationPrompts
import com.enil.logez.feature.workout.OpenNotificationPrompts
import com.enil.logez.feature.workout.WorkoutMiniBar
import com.enil.logez.feature.workout.WorkoutRoutes
import com.enil.logez.feature.workout.rememberStartWorkoutSession

/**
 * App root: theme + the 3-tab Scaffold, with first-run setup drawn above it when a fresh install
 * needs it. Theme is dark-only (Owner directive) — no mode switching.
 */
@Composable
fun LogEzApp() {
    LogEzTheme {
        // One registry of open notification prompts per window, so cold-start recovery and a
        // restored screen never both ask about the same workout (see rememberStartWorkoutSession).
        CompositionLocalProvider(LocalOpenNotificationPrompts provides remember { OpenNotificationPrompts() }) {
            val navController = rememberNavController()
            // First-run plan O1c: an overlay above the app, never a replacement for it. The NavHost
            // stays composed underneath, so the recovery effect below always has a graph to navigate.
            val gateViewModel: FirstRunGateViewModel = hiltViewModel()
            val gate by gateViewModel.state.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val resumedSnackbarMessage = stringResource(R.string.workout_mini_bar_resumed_snackbar)

            // §9.5 cold-start recovery: process/service died (swipe-from-recents, force-stop, crash)
            // while a workout was IN_PROGRESS -- Room already has the data; this restarts the service
            // and lands the user back in the right screen, exactly once per cold start.
            val startupViewModel: AppStartupViewModel = hiltViewModel()
            val recovery by startupViewModel.recovery.collectAsStateWithLifecycle()
            val trackingResumedMessage = stringResource(R.string.activity_tracking_resumed_snackbar)
            var interruptedRun by remember { mutableStateOf<StartupRecovery.InterruptedRun?>(null) }
            val resumeRecoveredSession = rememberStartWorkoutSession(onNavigateToLogger = { workoutId ->
                navController.navigate(WorkoutRoutes.logger(workoutId))
            })
            LaunchedEffect(recovery) {
                // Only the strength branch may go through rememberStartWorkoutSession -- that is what
                // starts WorkoutSessionService, and doing it for a GPS run puts a second foreground
                // service alongside the location one that is already running.
                when (val r = recovery) {
                    is StartupRecovery.None -> Unit
                    is StartupRecovery.ResumeStrength -> {
                        // Let the restored screens compose first. A Workout tab restored with its
                        // notification prompt still open for this workout registers it, and then this
                        // call leaves it to that prompt (OpenNotificationPrompts): one dialog, one logger.
                        // Waited before consumeRecovery(), which restarts this effect.
                        navController.awaitGraph()
                        withFrameNanos { }
                        startupViewModel.consumeRecovery()
                        resumeRecoveredSession(r.workoutId)
                        snackbarHostState.showSnackbar(resumedSnackbarMessage)
                    }
                    is StartupRecovery.ResumeLiveTracking -> {
                        startupViewModel.consumeRecovery()
                        // Start nothing: ActivityTrackingService is already foregrounded and collecting.
                        navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING)
                        snackbarHostState.showSnackbar(trackingResumedMessage)
                    }
                    is StartupRecovery.InterruptedRun -> {
                        startupViewModel.consumeRecovery()
                        interruptedRun = r
                    }
                }
            }

            interruptedRun?.let { run ->
                InterruptedTrackingDialog(
                    workoutId = run.workoutId,
                    startedAt = run.startedAt,
                    onKeptTime = { workoutId ->
                        interruptedRun = null
                        navController.navigate(WorkoutRoutes.finish(workoutId))
                    },
                    onDiscarded = { interruptedRun = null },
                )
            }

            // A NavHost restored after process death can hold several screens (Delete all data, then
            // the process died on Export & backup). Under setup it goes back to History alone, with no
            // saved tab stacks: system Back then leaves the app (predictive back plays back-to-home)
            // instead of popping screens under the overlay, and the hand-off can't restore a screen of
            // erased data.
            val showingSetup = gate is FirstRunGateState.ShowSetup
            LaunchedEffect(showingSetup) {
                if (showingSetup) {
                    navController.awaitGraph()
                    navController.resetUnderFirstRunSetup()
                }
            }
            // "Fully drawn" once setup or the app is on screen, not the Loading cover, so logcat's
            // "Fully drawn com.enil.logez/.MainActivity" line measures the time to setup (plan: O1c
            // measures the gate's timeout rather than assuming it).
            ReportDrawnWhen { gate != FirstRunGateState.Loading }
            // The tab the bottom bar last composed as selected. The bar reads the back stack through
            // its own flow collection, which can land a frame after the NavHost has switched; the
            // hand-off waits on this so the first frame without the overlay never has History lit.
            val barSelectedTab = remember { mutableStateOf<String?>(null) }

            FirstRunHost(
                state = gate,
                onContinue = gateViewModel::complete,
                // Continue lands on the Workout tab, where Start Empty Workout lives. The overlay stays up
                // while the tab is built underneath, then goes: History never flashes.
                onHandOff = {
                    runFirstRunHandOff(navController, barSelectedTab = { barSelectedTab.value }, gateViewModel::handoffDone)
                },
                onResume = gateViewModel::onResume,
            ) { hiddenWhileFirstRun ->
                Scaffold(
                    modifier = hiddenWhileFirstRun,
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
                            val selectedTab = LogEzDestination.entries.firstOrNull { destination ->
                                currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            }?.route
                            ReportSelectedTab(barSelectedTab, selectedTab)
                            Column {
                                WorkoutMiniBar(
                                    onExpand = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                                    onExpandLiveTracking = { navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING) },
                                    // Same dialog state the startup path raises, so there is one dialog
                                    // and one resolution path regardless of how the run was reached.
                                    onInterruptedRun = { workoutId, startedAt ->
                                        interruptedRun = StartupRecovery.InterruptedRun(workoutId, startedAt)
                                    },
                                )
                                // Owner, 2026-09-26: the bar is page black, not the grey card tone.
                                // NavigationBar defaults to surfaceContainer, which Theme.kt maps to the
                                // card colour, so a grey block sat under every tab. The app draws edge to
                                // edge, so this colour also fills the area behind the gesture handle.
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    tonalElevation = 0.dp,
                                ) {
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
                                            onClick = { navController.navigateToTab(destination.route) },
                                            icon = {
                                                // Null: the visible label already names the tab, and a
                                                // description here made TalkBack read it twice.
                                                Icon(imageVector = destination.icon, contentDescription = null)
                                            },
                                            label = { Text(stringResource(destination.labelRes)) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    // The workout summary draws behind the status bar so a walk/run's route map can run up
                    // to the top edge (2026-09-26); it pads its own text back down. Every other screen
                    // keeps the full inset.
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val drawsBehindStatusBar = backStackEntry?.destination?.route == WorkoutRoutes.SUMMARY
                    val layoutDirection = LocalLayoutDirection.current
                    val contentPadding = if (drawsBehindStatusBar) {
                        PaddingValues(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = innerPadding.calculateBottomPadding(),
                        )
                    } else {
                        innerPadding
                    }
                    // Tablets, foldables and landscape (2026-09-25, Play-readiness audit): the phone layout
                    // used to stretch edge to edge, so cards and set tables spread across a whole tablet.
                    // Content is capped at a readable width and centred; the bottom bar stays full width.
                    // A phone is narrower than the cap, so nothing changes there.
                    Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LogEzNavHost(
                            navController = navController,
                            modifier = Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Tells the first-run hand-off which tab the bottom bar has composed as selected. */
@Composable
private fun ReportSelectedTab(holder: MutableState<String?>, selectedTab: String?) {
    SideEffect { holder.value = selectedTab }
}

/**
 * First-run setup's hand-off (first-run plan, "Gate placement"): with the overlay still up, the app
 * underneath goes to the Workout tab with the bottom bar's own options, and [onDone] removes the
 * overlay only once [barSelectedTab] says the bottom bar has composed Workout as selected, plus one
 * frame to draw it. So neither History's screen nor its highlighted tab ever shows. The wait is
 * bounded by [HAND_OFF_BAR_WAIT_MS]: a bar that never reports can't keep the user on a disabled
 * setup screen. It first resets the app to History alone ([resetUnderFirstRunSetup]), and it is safe
 * to run again in an Activity recreated mid-hand-off.
 */
internal suspend fun runFirstRunHandOff(
    navController: NavController,
    barSelectedTab: () -> String?,
    onDone: () -> Unit,
) {
    navController.awaitGraph()
    navController.resetUnderFirstRunSetup()
    navController.navigateToTab(LogEzDestination.Workout.route)
    withTimeoutOrNull(HAND_OFF_BAR_WAIT_MS) {
        snapshotFlow(barSelectedTab).first { it == LogEzDestination.Workout.route }
    }
    withFrameNanos { }
    onDone()
}

/** How long the hand-off waits for the bottom bar to show the Workout tab before it opens the app anyway. */
internal const val HAND_OFF_BAR_WAIT_MS = 1_000L

/**
 * Puts the app under first-run setup back to its start screen (History) alone, and drops every
 * tab's saved stack, which a NavController restored after process death can still hold. Does
 * nothing on a fresh launch, whose stack is already History alone.
 */
internal fun NavController.resetUnderFirstRunSetup() {
    val start = graph.findStartDestination()
    popBackStack(start.id, inclusive = false)
    LogEzDestination.entries.forEach { clearBackStack(it.route) }
    // Clearing the start tab's own saved stack pops the start screen too; put a fresh one back.
    if (currentBackStackEntry == null) navigate(requireNotNull(start.route))
}

/**
 * Waits for the NavHost to set its graph. The Scaffold composes its content while it is laid out,
 * after the first composition, so an effect that starts with the Activity can run before it.
 */
internal suspend fun NavController.awaitGraph() {
    while (currentBackStackEntry == null) withFrameNanos { }
}

/**
 * Switches to a bottom-bar tab the way the bar itself does: one copy of each tab, each tab's own
 * stack saved and restored. First-run setup's hand-off uses the same options.
 */
internal fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** The widest the content column grows on a large screen: comfortable for reading and for the set table. */
private val MAX_CONTENT_WIDTH = 720.dp
