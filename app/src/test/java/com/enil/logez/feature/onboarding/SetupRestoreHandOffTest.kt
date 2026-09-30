package com.enil.logez.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.enil.logez.R
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.StagedBackup
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.settings.DataJob
import com.enil.logez.feature.settings.RestoreOutcome
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The bridge from setup's restore job to the gate (O1e): what is handed over, when, and only once.
 * The job flow stands in for the Activity-scoped DataViewModel; its handled callbacks do what the
 * ViewModel's dismissJob and restoreFailureHandled do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupRestoreHandOffTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val jobs = MutableStateFlow<DataJob>(DataJob.Idle)
    private val choices = SetupChoicesState(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY))
    private val restored = mutableListOf<Pair<RestoreOutcome, SetupChoices>>()
    private val failed = mutableListOf<Triple<Int, Boolean, SetupChoices>>()
    private var abandoned = 0
    private var changingConfigurations = false
    private var composed by mutableStateOf(true)

    private fun show() {
        rule.setContent {
            if (composed) {
                SetupRestoreHandOff(
                    jobs = jobs,
                    choices = choices,
                    onRestored = { outcome, onScreen -> restored += outcome to onScreen },
                    onRestoreFailed = { message, hadSettings, onScreen -> failed += Triple(message, hadSettings, onScreen) },
                    onRestoredHandled = { jobs.value = DataJob.Idle },
                    onFailureHandled = {
                        val job = jobs.value
                        if (job is DataJob.Failed) jobs.value = job.copy(afterConfirm = false)
                    },
                    onConfirmAbandoned = { abandoned++ },
                    changingConfigurations = { changingConfigurations },
                )
            }
        }
        rule.waitForIdle()
    }

    private val lbMilesMonday = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY)

    @Test
    fun `a finished restore is handed over once, with the choices on screen when it finished`() {
        show()
        choices.choices = lbMilesMonday
        val outcome = RestoreOutcome(unfinishedWorkoutsLeftOut = 1, backupHadSettings = false)

        jobs.value = DataJob.Done(R.plurals.data_restore_done_left_out, count = 1, restore = outcome)
        rule.waitForIdle()

        assertEquals(listOf(outcome to lbMilesMonday), restored)
        assertEquals(DataJob.Idle, jobs.value)

        // Setup composed again (a recreated window) finds nothing left to hand over.
        composed = false
        rule.waitForIdle()
        composed = true
        rule.waitForIdle()
        assertEquals(1, restored.size)
    }

    @Test
    fun `a failed confirmed restore is handed over once, with whether the backup had settings`() {
        show()
        choices.choices = lbMilesMonday

        jobs.value = DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = true, backupHadSettings = false)
        rule.waitForIdle()

        assertEquals(listOf(Triple(R.string.data_restore_incomplete, false, lbMilesMonday)), failed)
        // The message stays for the screen; only the after-confirm mark goes.
        assertEquals(
            DataJob.Failed(R.string.data_restore_incomplete, afterConfirm = false, backupHadSettings = false),
            jobs.value,
        )

        composed = false
        rule.waitForIdle()
        composed = true
        rule.waitForIdle()
        assertEquals(1, failed.size)
    }

    @Test
    fun `other jobs are not handed over`() {
        show()

        jobs.value = DataJob.Failed(R.string.data_restore_unreadable)
        rule.waitForIdle()
        jobs.value = DataJob.Done(R.string.data_export_done)
        rule.waitForIdle()
        jobs.value = DataJob.Working(R.string.data_restore_reading)
        rule.waitForIdle()

        assertTrue(restored.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `a restore that finishes while the app is in the background is handed over at once`() {
        show()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val outcome = RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = true)

        jobs.value = DataJob.Done(R.string.data_restore_done, restore = outcome)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(outcome to SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY)), restored)
        assertEquals(DataJob.Idle, jobs.value)
    }

    @Test
    fun `setup leaving the screen with the confirm open cancels it, but a configuration change does not`() {
        show()
        jobs.value = DataJob.ConfirmRestore(StagedBackup(BackupManifest(), unfinishedWorkoutCount = 0, hasSettings = true))
        rule.waitForIdle()

        changingConfigurations = true
        composed = false
        rule.waitForIdle()
        assertEquals(0, abandoned)

        changingConfigurations = false
        composed = true
        rule.waitForIdle()
        composed = false
        rule.waitForIdle()
        assertEquals(1, abandoned)
    }

    @Test
    fun `setup leaving the screen with no confirm open cancels nothing`() {
        show()
        jobs.value = DataJob.Failed(R.string.data_restore_unreadable)
        rule.waitForIdle()

        composed = false
        rule.waitForIdle()

        assertEquals(0, abandoned)
    }
}
