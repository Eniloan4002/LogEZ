package com.enil.logez.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.BackupTables
import com.enil.logez.core.data.backup.StagedBackup
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore on the setup screen (first-run plan, O1e): the pinned Restore button, the status block at
 * the top of the column, Back while working, and "Restore this backup?". A short window, so the
 * column scrolls. Robolectric runs in en-US.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h560dp")
class FirstRunSetupRestoreTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val enPh = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY)
    private var restoreUi by mutableStateOf(SetupRestoreUi())
    private var working by mutableStateOf(false)
    private var restores = 0
    private var confirms = 0
    private var cancels = 0
    private val continued = mutableListOf<SetupChoices>()

    private fun show(choicesState: SetupChoicesState? = null) {
        rule.setContent {
            LogEzTheme {
                FirstRunSetupScreen(
                    preselected = enPh,
                    regionNoteVisible = true,
                    working = working,
                    onContinue = { continued += it },
                    choicesState = choicesState ?: rememberSetupChoicesState(enPh),
                    restore = SetupRestoreBinding(
                        ui = restoreUi,
                        onRestore = { restores++ },
                        onConfirm = { confirms++ },
                        onCancel = { cancels++ },
                    ),
                )
            }
        }
    }

    private fun backup(
        workouts: Int = 148,
        unfinished: Int = 0,
        hasSettings: Boolean = true,
        exportedAt: Long = 1_790_215_200_000L,
    ) = StagedBackup(
        manifest = BackupManifest(
            exportedAtEpochMillis = exportedAt,
            exportedAtZoneId = "Asia/Manila",
            tables = listOf(
                BackupManifest.TableEntry(BackupTables.WORKOUTS, workouts),
                BackupManifest.TableEntry(BackupTables.EXERCISES, 412),
                BackupManifest.TableEntry(BackupTables.ROUTINES, 6),
                BackupManifest.TableEntry(BackupTables.BODY_MEASUREMENTS, 31),
                BackupManifest.TableEntry(BackupTables.GOAL_DEFINITIONS, 2),
            ),
            mediaFileCount = 12,
        ),
        unfinishedWorkoutCount = unfinished,
        hasSettings = hasSettings,
    )

    private val backEnabled: Boolean
        get() {
            var enabled = false
            rule.activityRule.scenario.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
            return enabled
        }

    // ---- the Restore button ----

    @Test
    fun `Restore from a backup sits under Continue, tagged, 48 dp tall, and opens the picker`() {
        show()

        rule.onNodeWithTag("firstrun_restore")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Restore from a backup"))))
            .performClick()

        assertEquals(1, restores)
        assertTrue(continued.isEmpty())
    }

    @Test
    fun `while a backup is read, the label and progress bar show and both buttons are off`() {
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Working(R.string.data_restore_reading), busy = true)
        show()

        rule.onNodeWithText("Reading the backup…")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        rule.onNodeWithTag("firstrun_continue").assertIsNotEnabled()
        rule.onNodeWithTag("firstrun_restore").assertIsNotEnabled()
    }

    @Test
    fun `Back is blocked while a restore runs or its result is being written, and free after a failure`() {
        show()
        assertFalse(backEnabled)

        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Working(R.string.data_restore_restoring), busy = true)
        rule.waitForIdle()
        rule.onNodeWithText("Restoring…").assertIsDisplayed()
        assertTrue(backEnabled)

        // A confirmed restore failed; the gate is re-checking the database.
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_failed), busy = true)
        rule.waitForIdle()
        assertTrue(backEnabled)

        // The gate is writing the result (the job has gone back to Idle).
        restoreUi = SetupRestoreUi()
        working = true
        rule.waitForIdle()
        assertTrue(backEnabled)

        // The check found nothing: setup takes Continue again, and Back leaves the app.
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_failed))
        working = false
        rule.waitForIdle()
        assertFalse(backEnabled)
    }

    @Test
    fun `a status that appears while the column is scrolled down is brought into view`() {
        show()
        rule.onNodeWithText("Change these any time in Profile > Settings.", substring = true).performScrollTo()
        rule.onNodeWithText("BEFORE YOU START").assertIsNotDisplayed()

        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Working(R.string.data_restore_reading), busy = true)
        rule.waitForIdle()

        rule.onNodeWithText("Reading the backup…").assertIsDisplayed()
        rule.onNodeWithText("BEFORE YOU START").assertIsDisplayed()
    }

    // ---- failures ----

    @Test
    fun `too new says so, adds the install hint, and leaves Continue and Restore on`() {
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_too_new))
        show()

        rule.onNodeWithText("That backup was made by a newer version of LogEZ.").assertIsDisplayed()
        rule.onNodeWithText("Install a version of LogEZ at least as new as the one that made this backup.").assertIsDisplayed()
        rule.onNodeWithTag("firstrun_continue").assertIsEnabled()
        rule.onNodeWithTag("firstrun_restore").assertIsEnabled()
    }

    @Test
    fun `other failures show their own line without the install hint, as a polite live region`() {
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_unreadable))
        show()

        rule.onNodeWithText("That file isn't a LogEZ backup, or it's incomplete.", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("That file isn't a LogEZ backup, or it's incomplete.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText("Install a version of LogEZ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Continue after a failure reports the choices as usual`() {
        restoreUi = SetupRestoreUi(status = SetupRestoreStatus.Error(R.string.data_restore_too_new))
        show()

        rule.onNodeWithTag("firstrun_continue").performClick()

        assertEquals(listOf(enPh), continued)
    }

    // ---- the confirm ----

    @Test
    fun `the confirm names the date and what comes back, and its settings note, over the screen unchanged`() {
        restoreUi = SetupRestoreUi(confirm = backup())
        show()

        rule.onNodeWithText("Restore this backup?").assertIsDisplayed()
        rule.onNodeWithText(
            "Made on 24 Sep 2026. It has 148 workouts, 6 routines, 31 measurement entries, 2 goals and 12 photos.\n\n" +
                "Its settings replace the choices on this screen. Permissions are not part of a backup: LogEZ asks again " +
                "when a feature needs one, and Health Connect can be connected again from Profile.",
        ).assertExists()
        // No exercise count: it would include about 400 seeded exercises.
        rule.onNodeWithText("412", substring = true).assertDoesNotExist()
        // Under the modal dialog the screen is drawn unchanged, as in restore-confirm.png.
        rule.onNodeWithTag("firstrun_continue").assertIsEnabled()
        assertFalse(backEnabled)
    }

    @Test
    fun `the confirm leaves out unfinished workouts from the count and says so`() {
        restoreUi = SetupRestoreUi(confirm = backup(workouts = 149, unfinished = 1))
        show()

        rule.onNodeWithText(
            "Made on 24 Sep 2026. It has 148 workouts, 6 routines, 31 measurement entries, 2 goals and 12 photos.\n\n" +
                "It includes 1 unfinished workout, which will be left out.\n\n" +
                "Its settings replace the choices on this screen.",
            substring = true,
        ).assertExists()
    }

    @Test
    fun `a backup without settings says the screen's choices are kept`() {
        restoreUi = SetupRestoreUi(confirm = backup(hasSettings = false))
        show()

        rule.onNodeWithText(
            "Made on 24 Sep 2026. It has 148 workouts, 6 routines, 31 measurement entries, 2 goals and 12 photos.\n\n" +
                "This backup has no settings, so the choices on this screen are kept.",
        ).assertExists()
    }

    @Test
    fun `a backup with no export time leaves out the date`() {
        restoreUi = SetupRestoreUi(confirm = backup(workouts = 1, exportedAt = 0L))
        show()

        rule.onNodeWithText(
            "It has 1 workout, 6 routines, 31 measurement entries, 2 goals and 12 photos.",
            substring = true,
        ).assertExists()
        rule.onNodeWithText("Made on", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the confirm's Restore confirms and Cancel cancels, with no Replace everything`() {
        // The colour (primary, not red) is not read here; it is shown on emulator-5556 in
        // docs/verification/onboarding-2026-09-27/O1e/11-confirm-full-backup.png.
        restoreUi = SetupRestoreUi(confirm = backup())
        show()

        rule.onNodeWithText("Restore").performClick()
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, confirms)
        assertEquals(1, cancels)
        rule.onNodeWithText("Replace everything").assertDoesNotExist()
    }

    // ---- the choices a restore reads ----

    @Test
    fun `pill taps land in the shared choices state that a finished restore reads`() {
        val state = SetupChoicesState(enPh)
        show(choicesState = state)

        rule.onNodeWithContentDescription("Pounds (lb)").performClick()
        rule.onNodeWithText("Monday").performScrollTo().performClick()

        assertEquals(SetupChoices(WeightUnit.LB, DistanceUnit.KM, DayOfWeek.MONDAY), state.choices)
    }
}
