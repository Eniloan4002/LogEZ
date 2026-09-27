package com.enil.logez

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enil.logez.app.navigation.LogEzDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What first-run setup does to the app's navigation underneath it: the reset to History alone
 * (so Back leaves the app and no erased screen comes back) and the hand-off to the Workout tab.
 * A real NavHost on the app's tab routes, with stand-ins for the sub-screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunNavigationTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private var runHandOff by mutableStateOf(false)
    private val doneWithStack = mutableListOf<List<String>>()

    /** The tab the stand-in bottom bar last composed as selected. */
    private val barSelectedTab = mutableStateOf<String?>(null)
    private val doneWithBarTab = mutableListOf<String?>()

    /** When set, the stand-in bar reports this instead of following the back stack. */
    private var barStuckOn by mutableStateOf<String?>(null)

    private fun showApp() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = LogEzDestination.History.route) {
                composable(LogEzDestination.History.route) { Text("History") }
                composable(LogEzDestination.Workout.route) { Text("Workout") }
                composable(LogEzDestination.Profile.route) { Text("Profile") }
                composable(WORKOUT_DETAIL) { Text("Workout detail") }
                composable(ROUTINE_DETAIL) { Text("Routine detail") }
                composable(SETTINGS) { Text("Settings") }
                composable(EXPORT_BACKUP) { Text("Export & backup") }
            }
            // A stand-in for the app's bottom bar: it reads the back stack the same way and reports
            // the tab it composed as selected.
            val entry by nav.currentBackStackEntryAsState()
            val followed = LogEzDestination.entries.firstOrNull { destination ->
                entry?.destination?.hierarchy?.any { it.route == destination.route } == true
            }?.route
            val shown = barStuckOn ?: followed
            SideEffect { barSelectedTab.value = shown }
            LaunchedEffect(runHandOff) {
                if (runHandOff) {
                    runFirstRunHandOff(nav, barSelectedTab = { barSelectedTab.value }) {
                        doneWithStack += routes()
                        doneWithBarTab += barSelectedTab.value
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** The screens on the back stack, bottom first, without the graph itself. */
    private fun routes(): List<String> =
        nav.currentBackStack.value.filterNot { it.destination is NavGraph }.mapNotNull { it.destination.route }

    /**
     * The state Delete all data leaves behind when the process then dies on Export & backup: the
     * History and Workout tabs each have a saved stack of screens, and the Profile tab is open three
     * screens deep.
     */
    private fun buildRestoredLookingStack() {
        rule.runOnIdle {
            nav.navigate(WORKOUT_DETAIL)
            nav.navigateToTab(LogEzDestination.Workout.route)
            nav.navigate(ROUTINE_DETAIL)
            nav.navigateToTab(LogEzDestination.Profile.route)
            nav.navigate(SETTINGS)
            nav.navigate(EXPORT_BACKUP)
        }
        rule.runOnIdle {
            assertEquals(listOf("history", "profile", SETTINGS, EXPORT_BACKUP), routes())
        }
    }

    @Test
    fun `the reset leaves History alone on the back stack`() {
        showApp()
        buildRestoredLookingStack()

        rule.runOnIdle { nav.resetUnderFirstRunSetup() }

        rule.runOnIdle { assertEquals(listOf("history"), routes()) }
    }

    @Test
    fun `after the reset no tab brings back a screen saved before it`() {
        showApp()
        buildRestoredLookingStack()

        rule.runOnIdle { nav.resetUnderFirstRunSetup() }
        rule.runOnIdle { nav.navigateToTab(LogEzDestination.Workout.route) }
        rule.runOnIdle { assertEquals(listOf("history", "workout"), routes()) }
        rule.runOnIdle { nav.navigateToTab(LogEzDestination.Profile.route) }
        rule.runOnIdle { assertEquals(listOf("history", "profile"), routes()) }
        rule.runOnIdle { nav.navigateToTab(LogEzDestination.History.route) }

        rule.runOnIdle { assertEquals(listOf("history"), routes()) }
    }

    @Test
    fun `without the reset the tabs do bring those screens back (control)`() {
        showApp()
        buildRestoredLookingStack()

        rule.runOnIdle { nav.navigateToTab(LogEzDestination.Workout.route) }

        rule.runOnIdle { assertEquals(listOf("history", "workout", ROUTINE_DETAIL), routes()) }
    }

    @Test
    fun `the reset changes nothing on a fresh launch`() {
        showApp()

        rule.runOnIdle { nav.resetUnderFirstRunSetup() }

        rule.runOnIdle { assertEquals(listOf("history"), routes()) }
    }

    @Test
    fun `the hand-off lands on the Workout tab above History, then removes the overlay`() {
        showApp()

        runHandOff = true
        rule.waitForIdle()

        // onDone ran once, and only after the Workout tab was on the stack and lit in the bar.
        assertEquals(listOf(listOf("history", "workout")), doneWithStack)
        assertEquals(listOf<String?>("workout"), doneWithBarTab)
        rule.runOnIdle {
            assertEquals("workout", nav.currentDestination?.route)
            assertEquals("history", nav.graph.findStartDestinationRoute())
        }
    }

    @Test
    fun `the hand-off from a restored stack lands on a bare Workout tab`() {
        showApp()
        buildRestoredLookingStack()

        runHandOff = true
        rule.waitForIdle()

        assertEquals(listOf(listOf("history", "workout")), doneWithStack)
    }

    @Test
    fun `the overlay goes only once the bottom bar shows Workout selected`() {
        showApp()
        // The bar lags the NavHost, as the app's bar did on emulator-5556 (History lit for a frame).
        barStuckOn = LogEzDestination.History.route
        rule.mainClock.autoAdvance = false

        startHandOffWithPausedClock()
        rule.mainClock.advanceTimeBy(500)

        rule.runOnIdle {
            assertEquals("workout", nav.currentDestination?.route)
            assertEquals(emptyList<List<String>>(), doneWithStack)
        }

        barStuckOn = null
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf(listOf("history", "workout")), doneWithStack)
        assertEquals(listOf<String?>("workout"), doneWithBarTab)
    }

    @Test
    fun `a bar that follows the back stack lets the overlay go within a few frames, not at the wait's end`() {
        showApp()
        rule.mainClock.autoAdvance = false

        startHandOffWithPausedClock()
        rule.mainClock.advanceTimeBy(100)

        rule.runOnIdle {
            assertEquals(listOf(listOf("history", "workout")), doneWithStack)
            assertEquals(listOf<String?>("workout"), doneWithBarTab)
        }
    }

    @Test
    fun `a bar that never shows Workout can't keep the overlay up past the wait`() {
        showApp()
        barStuckOn = LogEzDestination.History.route
        rule.mainClock.autoAdvance = false

        startHandOffWithPausedClock()
        rule.mainClock.advanceTimeBy(HAND_OFF_BAR_WAIT_MS - 100)
        rule.runOnIdle { assertEquals(emptyList<List<String>>(), doneWithStack) }

        rule.mainClock.advanceTimeBy(200)
        rule.runOnIdle { assertEquals(listOf(listOf("history", "workout")), doneWithStack) }
    }

    /** Starts the hand-off with the test clock paused, so each frame and each millisecond is the test's to give. */
    private fun startHandOffWithPausedClock() {
        rule.runOnIdle { runHandOff = true }
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun NavGraph.findStartDestinationRoute(): String? = findNode(startDestinationId)?.route

    private companion object {
        const val WORKOUT_DETAIL = "history/detail"
        const val ROUTINE_DETAIL = "routines/detail"
        const val SETTINGS = "settings"
        const val EXPORT_BACKUP = "settings/data"
    }
}
