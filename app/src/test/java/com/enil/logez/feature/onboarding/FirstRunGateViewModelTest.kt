package com.enil.logez.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.RegionDefaults
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.data.backup.RestoreLock
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeRegionDefaults
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeUserDataProbe
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.feature.settings.RestoreOutcome
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class RecordingLogger : AppLogger {
        val messages = mutableListOf<String>()
        override fun e(tag: String, message: String, throwable: Throwable?) {
            messages += message
        }
    }

    private val store = FakeFirstRunStore()
    private val probe = FakeUserDataProbe()
    private val settings = FakeSettingsRepository()
    private val region = FakeRegionDefaults()
    private val widget = FakeWidgetRefresher()
    private val clock = FakeClock()
    private val logger = RecordingLogger()
    private val restoreLock = RestoreLock()
    private val health = FakeHealthMetricsSource()
    private var savedState = SavedStateHandle()

    private fun newViewModel(sdkInt: Int = 34) =
        FirstRunGateViewModel(store, probe, settings, region, widget, restoreLock, clock, logger, health, savedState, sdkInt)

    private val usSuggestion = RegionSuggestion(
        weightUnit = WeightUnit.LB,
        distanceUnit = DistanceUnit.MILES,
        lengthUnit = LengthUnit.IN,
        firstDayOfWeek = DayOfWeek.SUNDAY,
        weekStartClamped = false,
    )

    // ---- resolve ----

    @Test
    fun `with the flag set the first value is ShowApp and the probe is never called`() {
        store.doneAt = 1L
        store.storedPath = FirstRunPath.SETUP

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, probe.callCount)
        assertEquals(0, store.markDoneCallCount)
    }

    @Test
    fun `without the flag the state starts at Loading until the probe answers`() = runTest {
        probe.neverReturns = true
        val vm = newViewModel()
        assertEquals(FirstRunGateState.Loading, vm.state.value)
    }

    @Test
    fun `user content found opens the app and writes the flag with path existing`() {
        probe.hasContent = true

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
        assertEquals(FakeClock.EPOCH_MILLIS, store.doneAt)
        assertTrue(settings.appliedSetupChoices.isEmpty())
    }

    @Test
    fun `user content found but the flag write fails still opens the app`() {
        probe.hasContent = true
        store.markDoneSucceeds = false

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
    }

    @Test
    fun `no content shows setup with the region's suggestion and the region note`() {
        region.suggestion = usSuggestion

        val vm = newViewModel()

        assertEquals(
            FirstRunGateState.ShowSetup(
                preselected = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY),
                regionNoteVisible = true,
            ),
            vm.state.value,
        )
        assertFalse(store.isDone())
    }

    @Test
    fun `stored values win over the region one by one, and the note hides when they differ`() {
        region.suggestion = usSuggestion
        settings.storedSetupValues = StoredSetupValues(weightUnit = WeightUnit.KG, firstDayOfWeek = DayOfWeek.MONDAY)

        val vm = newViewModel()

        assertEquals(
            FirstRunGateState.ShowSetup(
                preselected = SetupChoices(WeightUnit.KG, DistanceUnit.MILES, DayOfWeek.MONDAY),
                regionNoteVisible = false,
            ),
            vm.state.value,
        )
    }

    @Test
    fun `stored values that match the region keep the region note`() {
        region.suggestion = usSuggestion
        settings.storedSetupValues = StoredSetupValues(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY)

        val state = newViewModel().state.value as FirstRunGateState.ShowSetup

        assertTrue(state.regionNoteVisible)
    }

    @Test
    fun `a clamped region week start preselects Monday and hides the region note`() {
        region.suggestion = usSuggestion.copy(firstDayOfWeek = DayOfWeek.MONDAY, weekStartClamped = true)

        val state = newViewModel().state.value as FirstRunGateState.ShowSetup

        assertEquals(DayOfWeek.MONDAY, state.preselected.firstDayOfWeek)
        assertFalse(state.regionNoteVisible)
    }

    @Test
    fun `a stored week start the app does not offer gives way to the region`() {
        region.suggestion = usSuggestion
        settings.storedSetupValues = StoredSetupValues(firstDayOfWeek = DayOfWeek.TUESDAY)

        val state = newViewModel().state.value as FirstRunGateState.ShowSetup

        assertEquals(DayOfWeek.SUNDAY, state.preselected.firstDayOfWeek)
    }

    @Test
    fun `a probe that throws opens the app and leaves the flag unwritten`() {
        probe.error = IllegalStateException("database is locked")

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(0, store.markDoneCallCount)
        assertEquals(listOf("First-run check failed; opening the app"), logger.messages)
    }

    @Test
    fun `a settings read that throws opens the app and leaves the flag unwritten`() {
        settings.readStoredSetupValuesError = java.io.IOException("corrupt preferences")

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
    }

    @Test
    fun `a probe that never returns opens the app after the timeout, flag unwritten`() = runTest {
        probe.neverReturns = true
        val vm = newViewModel()

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf("First-run check took longer than 3000 ms; opening the app"), logger.messages)
    }

    @Test
    fun `a probe blocked past the timeout still opens the app at the timeout, flag unwritten`() = runTest {
        // Ignores cancellation, like a Room read waiting on the seed transaction on its thread.
        probe.blockMillis = 10_000L
        probe.hasContent = true
        val vm = newViewModel()

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf("First-run check took longer than 3000 ms; opening the app"), logger.messages)

        // The late answer is dropped: nothing more is written and the app stays open.
        advanceTimeBy(8_000L)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, store.markDoneCallCount)
    }

    @Test
    fun `a restore holding the lock keeps the gate in Loading, then the probe runs once it ends`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        probe.hasContent = true
        val vm = newViewModel()

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)
        assertEquals(0, probe.callCount)

        restoreLock.release(restore)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(1, probe.callCount)
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
    }

    // ---- a restore still running at the timeout (P-229, FX1) ----

    @Test
    fun `a restore that outlasts the timeout shows RestoreBusy, not the app, without probing or writing`() = runTest {
        restoreLock.tryAcquire(Any())
        val vm = newViewModel()

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)
        assertEquals(0, probe.callCount)
        assertFalse(store.isDone())
        assertEquals(0, store.markDoneCallCount)
        // Not a failure: nothing is logged.
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `RestoreBusy has no timeout of its own, as while the other window's confirm dialog stays open`() = runTest {
        restoreLock.tryAcquire(Any())
        val vm = newViewModel()

        advanceTimeBy(600_000L)
        runCurrent()

        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)
        assertEquals(0, probe.callCount)
        assertFalse(store.isDone())
    }

    @Test
    fun `once the restore ends, its restored rows open the app without writing the flag`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)

        probe.hasContent = true
        restoreLock.release(restore)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(1, probe.callCount)
        // The restoring window writes path restore; this one leaves the flag to it.
        assertFalse(store.isDone())
        assertEquals(0, store.markDoneCallCount)
        assertNull(vm.message.value)
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `the restoring window's resume stays on setup and hands its restore over after the second window opened`() = runTest {
        // Window 1: setup on an empty install, then its restore takes the lock.
        val window1 = newViewModel()
        assertEquals(enPhSetup, window1.state.value)
        restoreLock.tryAcquire(window1)

        // Window 2, from a widget tap: busy, then the restored rows once the lock is free.
        val window2 = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, window2.state.value)
        probe.hasContent = true
        restoreLock.release(window1)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, window2.state.value)

        // Window 1 comes back before its restore has been handed to the gate: setup stays.
        window1.onResume()
        assertEquals(enPhSetup, window1.state.value)

        val onScreen = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY)
        window1.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false), onScreen)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, window1.state.value)
        assertEquals(listOf(onScreen), settings.appliedSetupChoices)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(1, store.markDoneCallCount)
        assertEquals(FirstRunMessage(R.string.data_restore_done), window1.message.value)
    }

    @Test
    fun `with the flag written a held restore lock opens the app at once, without waiting or probing`() = runTest {
        store.doneAt = 1L
        store.storedPath = FirstRunPath.SETUP
        restoreLock.tryAcquire(Any())

        val vm = newViewModel()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        advanceTimeBy(600_000L)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, probe.callCount)
        assertEquals(0, store.markDoneCallCount)
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `once the restore ends, a flag the restoring window already wrote opens the app without probing`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()

        store.doneAt = 7L
        store.storedPath = FirstRunPath.RESTORE
        restoreLock.release(restore)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, probe.callCount)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(0, store.markDoneCallCount)
    }

    @Test
    fun `once a restore that failed before its commit ends, an empty database shows setup`() = runTest {
        region.suggestion = usSuggestion
        val restore = Any()
        restoreLock.tryAcquire(restore)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()

        restoreLock.release(restore)
        runCurrent()

        assertEquals(
            FirstRunGateState.ShowSetup(
                preselected = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY),
                regionNoteVisible = true,
            ),
            vm.state.value,
        )
        assertFalse(store.isDone())
    }

    @Test
    fun `a flag read that throws after the restore still decides from the database`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()

        store.isDoneError = IllegalStateException("preferences failed to load")
        probe.hasContent = true
        restoreLock.release(restore)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(1, probe.callCount)
        assertEquals(
            listOf("Could not read the first-run flag after a restore; deciding again from the database"),
            logger.messages,
        )
    }

    @Test
    fun `the re-check after a restore gets its own timeout and still fails open when the probe hangs`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()

        probe.neverReturns = true
        restoreLock.release(restore)
        runCurrent()
        // The busy message stays up during the re-check, rather than a blank cover.
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf("First-run check took longer than 3000 ms; opening the app"), logger.messages)
    }

    @Test
    fun `a second restore that takes the lock during the re-check keeps RestoreBusy until it ends too`() = runTest {
        val first = Any()
        restoreLock.tryAcquire(first)
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()

        // Freed, then taken by a second restore before the gate's re-check reaches the lock: the
        // gate reads the flag first, and the fake takes the lock right there.
        val second = Any()
        store.onIsDone = {
            store.onIsDone = {}
            restoreLock.tryAcquire(second)
        }
        restoreLock.release(first)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)
        advanceTimeBy(3_001L)
        runCurrent()
        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)
        assertEquals(0, probe.callCount)

        probe.hasContent = true
        restoreLock.release(second)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(1, probe.callCount)
    }

    @Test
    fun `a probe that hangs after the lock was freed in time still fails open, not RestoreBusy`() = runTest {
        val restore = Any()
        restoreLock.tryAcquire(restore)
        probe.neverReturns = true
        val vm = newViewModel()

        advanceTimeBy(1_000L)
        restoreLock.release(restore)
        runCurrent()
        advanceTimeBy(2_001L)
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(1, probe.callCount)
        assertEquals(listOf("First-run check took longer than 3000 ms; opening the app"), logger.messages)
    }

    @Test
    fun `setup's actions and resume do nothing while RestoreBusy shows`() = runTest {
        restoreLock.tryAcquire(Any())
        val vm = newViewModel()
        advanceTimeBy(3_001L)
        runCurrent()
        val choices = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.MONDAY)

        vm.complete(choices)
        vm.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false), choices)
        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = false, choices = choices)
        vm.onResume()
        runCurrent()

        assertEquals(FirstRunGateState.RestoreBusy, vm.state.value)
        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(0, store.markDoneCallCount)
        assertEquals(0, probe.callCount)
    }

    @Test
    fun `a flag read that throws opens the app without asking Room or writing the flag`() {
        store.isDoneError = IllegalStateException("preferences failed to load")

        val vm = newViewModel()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, probe.callCount)
        assertEquals(0, store.markDoneCallCount)
        assertEquals(listOf("Could not read the first-run flag; opening the app"), logger.messages)
    }

    // ---- complete / hand-off ----

    @Test
    fun `complete writes the four settings, refreshes the widget, writes the flag last, then hands off`() {
        val vm = newViewModel()
        val choices = SetupChoices(WeightUnit.LB, DistanceUnit.KM, DayOfWeek.SATURDAY)
        var appliedWhenFlagWritten: List<SetupChoices>? = null
        var refreshesWhenFlagWritten = -1
        store.onMarkDone = {
            appliedWhenFlagWritten = settings.appliedSetupChoices.toList()
            refreshesWhenFlagWritten = widget.refreshCount
        }

        vm.complete(choices)

        assertEquals(listOf(choices), appliedWhenFlagWritten)
        assertEquals(1, refreshesWhenFlagWritten)
        assertEquals(FirstRunPath.SETUP, store.storedPath)
        assertEquals(FakeClock.EPOCH_MILLIS, store.doneAt)
        assertEquals(LengthUnit.IN, settings.settings.value.lengthUnit)
        // The hand-off carries the setup that was showing (en-PH's, from the fake region), so the
        // overlay can draw it through the hand-off even in a recreated Activity.
        assertEquals(
            FirstRunGateState.HandOff(
                FirstRunGateState.ShowSetup(
                    preselected = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY),
                    regionNoteVisible = true,
                ),
            ),
            vm.state.value,
        )

        vm.handoffDone()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
    }

    @Test
    fun `complete after another window finished first run opens the app and writes nothing over it`() = runTest {
        val vm = newViewModel()
        assertEquals(enPhSetup, vm.state.value)
        // Another window's restore (settings only, which the content check doesn't count) wrote the flag.
        store.doneAt = 7L
        store.storedPath = FirstRunPath.RESTORE

        vm.complete(SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY))
        runCurrent()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(0, widget.refreshCount)
        assertEquals(0, store.markDoneCallCount)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(7L, store.doneAt)
    }

    @Test
    fun `complete still saves setup when the flag can't be read`() = runTest {
        val vm = newViewModel()
        store.isDoneError = IllegalStateException("preferences failed to load")
        val choices = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY)

        vm.complete(choices)
        runCurrent()

        assertEquals(listOf(choices), settings.appliedSetupChoices)
        assertEquals(FirstRunPath.SETUP, store.storedPath)
        assertTrue(vm.state.value is FirstRunGateState.HandOff)
        assertEquals(listOf("Could not read the first-run flag before saving setup; saving it"), logger.messages)
    }

    @Test
    fun `complete is ignored unless setup is showing`() {
        probe.hasContent = true
        val vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY))

        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
    }

    @Test
    fun `a failed flag write still hands off to the app`() {
        store.markDoneSucceeds = false
        val vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY))

        assertTrue(vm.state.value is FirstRunGateState.HandOff)
        assertFalse(store.isDone())
        assertNull(store.storedPath)
    }

    @Test
    fun `a settings write that throws still hands off, with the flag unwritten`() {
        settings.applySetupChoicesError = java.io.IOException("disk full")
        val vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY))

        assertTrue(vm.state.value is FirstRunGateState.HandOff)
        assertEquals(0, store.markDoneCallCount)
        assertEquals(0, widget.refreshCount)
        assertEquals(listOf("Saving the setup choices failed; opening the app"), logger.messages)
    }

    @Test
    fun `a widget refresh that throws still writes the flag and hands off`() {
        widget.error = IllegalStateException("no widget host")
        val vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY))

        assertEquals(FirstRunPath.SETUP, store.storedPath)
        assertTrue(vm.state.value is FirstRunGateState.HandOff)
        assertEquals(listOf("Widget refresh after setup failed"), logger.messages)
    }

    @Test
    fun `a second Continue while the first is being written is ignored`() {
        val gate = CompletableDeferred<Unit>()
        settings.applySetupChoicesGate = gate
        region.suggestion = usSuggestion
        val vm = newViewModel()
        val first = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY)

        vm.complete(first)
        assertEquals(
            FirstRunGateState.ShowSetup(preselected = first, regionNoteVisible = true, working = true),
            vm.state.value,
        )
        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.MONDAY))
        assertEquals(listOf(first), settings.appliedSetupChoices)

        gate.complete(Unit)
        assertTrue(vm.state.value is FirstRunGateState.HandOff)
        assertEquals(1, store.markDoneCallCount)
    }

    @Test
    fun `handoffDone does nothing before a hand-off`() {
        val vm = newViewModel()
        val before = vm.state.value

        vm.handoffDone()

        assertEquals(before, vm.state.value)
    }

    // ---- resume re-check ----

    @Test
    fun `a resume after another window finished setup opens the app`() {
        val vm = newViewModel()
        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)

        store.doneAt = 5L
        vm.onResume()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
    }

    @Test
    fun `a resume while this window's Continue is being written does not open the app early`() {
        lateinit var vm: FirstRunGateViewModel
        var stateAtResume: FirstRunGateState? = null
        store.onMarkDone = {
            // The flag has reached disk (isDone() is true) but the hand-off hasn't happened yet.
            store.doneAt = 9L
            vm.onResume()
            stateAtResume = vm.state.value
        }
        vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.MONDAY))

        assertTrue((stateAtResume as FirstRunGateState.ShowSetup).working)
        assertTrue(vm.state.value is FirstRunGateState.HandOff)
    }

    @Test
    fun `a resume whose flag read throws keeps setup`() {
        val vm = newViewModel()
        val before = vm.state.value
        store.isDoneError = IllegalStateException("preferences failed to load")

        vm.onResume()

        assertEquals(before, vm.state.value)
    }

    @Test
    fun `a resume after the region changed keeps the preselection but hides the region note`() {
        val vm = newViewModel()
        assertEquals(
            FirstRunGateState.ShowSetup(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY), true),
            vm.state.value,
        )

        // The phone switched from English (Philippines) to English (United Kingdom) while setup showed.
        region.suggestion = RegionSuggestion(
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.MILES,
            lengthUnit = LengthUnit.CM,
            firstDayOfWeek = DayOfWeek.MONDAY,
            weekStartClamped = false,
        )
        vm.onResume()

        assertEquals(
            FirstRunGateState.ShowSetup(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY), false),
            vm.state.value,
        )

        // And back again: the preselection is the region's once more.
        region.suggestion = region.suggestion.copy(distanceUnit = DistanceUnit.KM, firstDayOfWeek = DayOfWeek.SUNDAY)
        vm.onResume()
        assertEquals(
            FirstRunGateState.ShowSetup(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY), true),
            vm.state.value,
        )
    }

    @Test
    fun `a resume whose region lookup throws keeps the region note as it was`() {
        var lookupThrows = false
        val switchable = object : RegionDefaults {
            override fun suggest(): RegionSuggestion =
                if (lookupThrows) throw IllegalStateException("no locale") else region.suggestion
        }
        val vm = FirstRunGateViewModel(store, probe, settings, switchable, widget, restoreLock, clock, logger, health, savedState, 34)
        val before = vm.state.value
        assertTrue(before is FirstRunGateState.ShowSetup)

        lookupThrows = true
        vm.onResume()

        assertEquals(before, vm.state.value)
        assertEquals("Region lookup on resume failed; keeping the region note as it was", logger.messages.last())
    }

    @Test
    fun `a resume with the flag still absent keeps setup`() {
        val vm = newViewModel()
        val before = vm.state.value

        vm.onResume()

        assertEquals(before, vm.state.value)
    }

    // ---- restore from setup (O1e) ----

    private val enPhSetup = FirstRunGateState.ShowSetup(
        preselected = SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY),
        regionNoteVisible = true,
    )

    /** Every message the gate leaves for the app's snackbar from now on, in order. */
    private fun TestScope.messagesOf(vm: FirstRunGateViewModel): List<FirstRunMessage> {
        val messages = mutableListOf<FirstRunMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { message -> message?.let { messages += it } }
        }
        return messages
    }

    @Test
    fun `restored with the backup's settings writes the flag with path restore and no setup choices`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)

        vm.restored(
            RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = true),
            SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY),
        )

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(FakeClock.EPOCH_MILLIS, store.doneAt)
        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(0, widget.refreshCount)
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_done)), messages)
    }

    @Test
    fun `restored from a backup without settings writes the screen's choices before the flag`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        val onScreen = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY)
        var appliedWhenFlagWritten: List<SetupChoices>? = null
        var refreshesWhenFlagWritten = -1
        store.onMarkDone = {
            appliedWhenFlagWritten = settings.appliedSetupChoices.toList()
            refreshesWhenFlagWritten = widget.refreshCount
        }

        vm.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false), onScreen)

        assertEquals(listOf(onScreen), appliedWhenFlagWritten)
        assertEquals(1, refreshesWhenFlagWritten)
        assertEquals(LengthUnit.IN, settings.settings.value.lengthUnit)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_done)), messages)
    }

    @Test
    fun `restored with unfinished workouts left out says how many`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)

        vm.restored(
            RestoreOutcome(unfinishedWorkoutsLeftOut = 2, backupHadSettings = true),
            enPhSetup.preselected,
        )

        assertEquals(listOf(FirstRunMessage(R.plurals.data_restore_done_left_out, count = 2)), messages)
    }

    @Test
    fun `restored still opens the app when the setup choices or the flag can't be written`() = runTest {
        settings.applySetupChoicesError = java.io.IOException("disk full")
        store.markDoneSucceeds = false
        val vm = newViewModel()
        val messages = messagesOf(vm)

        vm.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false), enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_done)), messages)
        assertEquals(
            listOf("Saving the setup choices after a restore failed", "Could not record first run after a restore"),
            logger.messages,
        )
    }

    @Test
    fun `restored is ignored unless setup is showing, and a second call while writing is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.applySetupChoicesGate = gate
        val vm = newViewModel()
        val messages = messagesOf(vm)
        val outcome = RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false)

        vm.restored(outcome, enPhSetup.preselected)
        assertEquals(enPhSetup.copy(working = true), vm.state.value)
        vm.restored(outcome, enPhSetup.preselected)
        vm.complete(enPhSetup.preselected)
        gate.complete(Unit)

        assertEquals(listOf(enPhSetup.preselected), settings.appliedSetupChoices)
        assertEquals(1, store.markDoneCallCount)
        assertEquals(1, messages.size)

        vm.restored(outcome, enPhSetup.preselected)
        assertEquals(1, store.markDoneCallCount)
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
    }

    @Test
    fun `a restore that failed after the commit opens History with path restore, never Continue`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        // The backup's rows are in the database now.
        probe.hasContent = true

        vm.restoreFailed(R.string.data_restore_incomplete, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(0, widget.refreshCount)
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_incomplete)), messages)
    }

    @Test
    fun `a failure after the commit opens History even when the content check would find nothing`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        // A backup of daily step totals and settings only: rows the content check does not count.
        probe.hasContent = false

        vm.restoreFailed(R.string.data_restore_incomplete, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_incomplete)), messages)
        // Only the check that resolved setup: the database is known to hold the backup.
        assertEquals(1, probe.callCount)
    }

    @Test
    fun `a failure after the commit of a backup without settings writes the screen's choices before the flag`() = runTest {
        val vm = newViewModel()
        val onScreen = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY)
        var appliedWhenFlagWritten: List<SetupChoices>? = null
        var refreshesWhenFlagWritten = -1
        store.onMarkDone = {
            appliedWhenFlagWritten = settings.appliedSetupChoices.toList()
            refreshesWhenFlagWritten = widget.refreshCount
        }

        vm.restoreFailed(R.string.data_restore_incomplete, backupHadSettings = false, choices = onScreen)

        assertEquals(listOf(onScreen), appliedWhenFlagWritten)
        assertEquals(1, refreshesWhenFlagWritten)
        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
    }

    @Test
    fun `a failure before the commit never writes the screen's choices, even for a backup without settings`() = runTest {
        val vm = newViewModel()
        probe.hasContent = true

        vm.restoreFailed(
            R.string.data_restore_blocked_in_progress,
            backupHadSettings = false,
            choices = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY),
        )

        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
    }

    @Test
    fun `a failed restore that left no content keeps setup, with the flag unwritten and no message`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)

        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(enPhSetup, vm.state.value)
        assertFalse(store.isDone())
        assertTrue(messages.isEmpty())
        assertEquals(2, probe.callCount)
    }

    @Test
    fun `a failed restore is re-checked before setup takes Continue again`() = runTest {
        val vm = newViewModel()
        probe.blockMillis = 1_000L

        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = true, choices = enPhSetup.preselected)
        assertEquals(enPhSetup.copy(working = true), vm.state.value)
        vm.complete(enPhSetup.preselected)
        assertTrue(settings.appliedSetupChoices.isEmpty())

        advanceTimeBy(1_001L)
        runCurrent()
        assertEquals(enPhSetup, vm.state.value)
    }

    @Test
    fun `a failure before the commit with content from elsewhere opens the app with its own message and path existing`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        // A second window started a workout while the confirm was open.
        probe.hasContent = true

        vm.restoreFailed(R.string.data_restore_blocked_in_progress, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_blocked_in_progress)), messages)
    }

    @Test
    fun `a failure before the commit does not overwrite a flag another window already wrote`() = runTest {
        val vm = newViewModel()
        store.doneAt = 7L
        store.storedPath = FirstRunPath.SETUP
        probe.hasContent = true

        vm.restoreFailed(R.string.data_restore_blocked_in_progress, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(FirstRunPath.SETUP, store.storedPath)
        assertEquals(0, store.markDoneCallCount)
    }

    @Test
    fun `a failed restore whose content check throws opens the app, flag unwritten`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        probe.error = IllegalStateException("database is locked")

        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = true, choices = enPhSetup.preselected)

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_failed)), messages)
        assertEquals(listOf("Content check after a failed restore failed; opening the app"), logger.messages)
    }

    @Test
    fun `a failed restore whose content check hangs opens the app at the timeout, flag unwritten`() = runTest {
        val vm = newViewModel()
        val messages = messagesOf(vm)
        probe.neverReturns = true

        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = true, choices = enPhSetup.preselected)
        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(enPhSetup.copy(working = true), vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertFalse(store.isDone())
        assertEquals(listOf(FirstRunMessage(R.string.data_restore_failed)), messages)
        assertEquals(
            listOf("Content check after a failed restore took longer than 3000 ms; opening the app"),
            logger.messages,
        )
    }

    @Test
    fun `restoreFailed is ignored unless setup is showing`() = runTest {
        probe.hasContent = true
        val vm = newViewModel()
        val calls = probe.callCount
        val writes = store.markDoneCallCount

        vm.restoreFailed(R.string.data_restore_failed, backupHadSettings = true, choices = enPhSetup.preselected)
        vm.restoreFailed(R.string.data_restore_incomplete, backupHadSettings = false, choices = enPhSetup.preselected)

        assertEquals(calls, probe.callCount)
        assertEquals(writes, store.markDoneCallCount)
        assertTrue(settings.appliedSetupChoices.isEmpty())
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
    }

    @Test
    fun `the restore message is held until messageShown clears it`() = runTest {
        val vm = newViewModel()

        vm.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = true), enPhSetup.preselected)
        assertEquals(FirstRunMessage(R.string.data_restore_done), vm.message.value)

        vm.messageShown()
        assertNull(vm.message.value)
    }

    @Test
    fun `restored records path restore even when another window wrote the flag meanwhile`() = runTest {
        val vm = newViewModel()
        // A second window found the restored rows before this one handed the result over.
        store.doneAt = 7L
        store.storedPath = FirstRunPath.EXISTING
        val onScreen = SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.MONDAY)

        vm.restored(RestoreOutcome(unfinishedWorkoutsLeftOut = 0, backupHadSettings = false), onScreen)

        assertEquals(FirstRunPath.RESTORE, store.storedPath)
        assertEquals(FakeClock.EPOCH_MILLIS, store.doneAt)
        assertEquals(listOf(onScreen), settings.appliedSetupChoices)
        assertEquals(FirstRunMessage(R.string.data_restore_done), vm.message.value)
    }

    // ---- Health Connect section (O1f) ----

    @Test
    fun `setup's first value comes with Connect when Health Connect is usable and nothing is granted`() {
        health.availabilityValue = HealthConnectAvailability.Available

        val vm = newViewModel()

        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)
        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `a partial grant made before setup shows as readable, with no action`() {
        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesOverride = setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE)

        val vm = newViewModel()

        assertEquals(SetupHealth.Readable(setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE)), vm.health.value)
    }

    @Test
    fun `Health Connect needing an update shows the update state`() {
        health.availabilityValue = HealthConnectAvailability.UpdateRequired

        val vm = newViewModel()

        assertEquals(SetupHealth.UpdateRequired, vm.health.value)
        assertEquals(0, health.grantedTypesCallCount)
    }

    @Test
    fun `with the flag set Health Connect is never read`() {
        store.doneAt = 1L
        health.availabilityValue = HealthConnectAvailability.Available

        val vm = newViewModel()
        vm.onResume()

        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
        assertEquals(0, health.grantedTypesCallCount)
        assertEquals(SetupHealth.Hidden, vm.health.value)
    }

    @Test
    fun `a Health Connect that never answers still shows setup in time, with the section hidden`() = runTest {
        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesNeverReturns = true
        val vm = newViewModel()

        advanceTimeBy(1_001L)
        runCurrent()

        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)
        assertEquals(SetupHealth.Hidden, vm.health.value)
    }

    @Test
    fun `a slow probe and a Health Connect that never answers still show setup at the gate's timeout`() = runTest {
        // A first launch: the probe waits behind the seed, and Health Connect's process is cold.
        probe.blockMillis = 2_500L
        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesNeverReturns = true
        val vm = newViewModel()

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)

        advanceTimeBy(2L)
        runCurrent()
        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)
        assertEquals(SetupHealth.Hidden, vm.health.value)
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `Health Connect is read alongside a slow probe, so setup shows with it as soon as the probe answers`() = runTest {
        probe.blockMillis = 2_500L
        health.availabilityValue = HealthConnectAvailability.Available
        val answer = CompletableDeferred<Set<HealthDataType>>()
        health.pendingGrantedTypes.addLast(answer)
        val vm = newViewModel()

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(FirstRunGateState.Loading, vm.state.value)
        assertEquals(1, health.grantedTypesCallCount)
        answer.complete(setOf(HealthDataType.STEPS))
        advanceTimeBy(1_501L)
        runCurrent()

        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)
        assertEquals(SetupHealth.Readable(setOf(HealthDataType.STEPS)), vm.health.value)
    }

    @Test
    fun `a Connect with nothing granted shows the refusal at once`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        // The re-read that follows never answers, so only the at-once refusal can show it.
        health.grantedTypesNeverReturns = true

        vm.onHealthConnectResult(anyGranted = false)

        assertEquals(SetupHealth.Refused, vm.health.value)
        assertEquals(0, health.regrantedCallCount)
    }

    @Test
    fun `a refusal survives a resume re-check that still finds nothing granted`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        vm.onHealthConnectResult(anyGranted = false)
        val readsBefore = health.grantedTypesCallCount

        vm.onResume()

        assertEquals(readsBefore + 1, health.grantedTypesCallCount)
        assertEquals(SetupHealth.Refused, vm.health.value)
    }

    @Test
    fun `a refusal survives process death through saved state`() {
        health.availabilityValue = HealthConnectAvailability.Available
        newViewModel().onHealthConnectResult(anyGranted = false)

        // The same saved state, handed to the ViewModel the recreated Activity gets.
        val restored = newViewModel()

        assertEquals(SetupHealth.Refused, restored.health.value)
    }

    @Test
    fun `a grant made in Health Connect's settings clears the refusal on resume`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        vm.onHealthConnectResult(anyGranted = false)

        health.grantedTypesOverride = setOf(HealthDataType.CALORIES)
        vm.onResume()
        assertEquals(SetupHealth.Readable(setOf(HealthDataType.CALORIES)), vm.health.value)

        // The refusal is gone, not just outranked: withdrawn again, the section offers Connect.
        health.grantedTypesOverride = emptySet()
        vm.onResume()
        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `a Connect that grants something ends a same-session disconnect and reads the grants again`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()

        health.permissionsGranted = true
        vm.onHealthConnectResult(anyGranted = true)

        assertEquals(1, health.regrantedCallCount)
        assertEquals(SetupHealth.Readable(HealthDataType.entries.toSet()), vm.health.value)
    }

    @Test
    fun `a Connect that grants something after a refusal clears it`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        vm.onHealthConnectResult(anyGranted = false)

        health.grantedTypesOverride = setOf(HealthDataType.STEPS)
        vm.onHealthConnectResult(anyGranted = true)
        assertEquals(SetupHealth.Readable(setOf(HealthDataType.STEPS)), vm.health.value)

        health.grantedTypesOverride = emptySet()
        vm.onResume()
        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `returning from Google Play after an update re-checks and offers Connect`() {
        health.availabilityValue = HealthConnectAvailability.UpdateRequired
        val vm = newViewModel()
        assertEquals(SetupHealth.UpdateRequired, vm.health.value)

        health.availabilityValue = HealthConnectAvailability.Available
        vm.onResume()

        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `a Health Connect read that fails on resume keeps the section as it was`() {
        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesOverride = setOf(HealthDataType.STEPS)
        val vm = newViewModel()

        health.grantedTypesReadFails = true
        vm.onResume()

        assertEquals(SetupHealth.Readable(setOf(HealthDataType.STEPS)), vm.health.value)
        assertEquals(listOf("Reading Health Connect for setup failed"), logger.messages)
    }

    @Test
    fun `back from installing Health Connect, a failed grants read still offers Connect instead of the install`() {
        health.availabilityValue = HealthConnectAvailability.Unavailable
        val vm = newViewModel(sdkInt = 33)
        assertEquals(SetupHealth.NotInstalled, vm.health.value)

        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesReadFails = true
        vm.onResume()

        assertEquals(SetupHealth.CanConnect, vm.health.value)
        assertEquals(listOf("Reading Health Connect for setup failed"), logger.messages)
    }

    @Test
    fun `back from updating Health Connect, a failed grants read keeps an earlier refusal`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        vm.onHealthConnectResult(anyGranted = false)
        health.availabilityValue = HealthConnectAvailability.UpdateRequired
        vm.onResume()
        assertEquals(SetupHealth.UpdateRequired, vm.health.value)

        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesReadFails = true
        vm.onResume()

        assertEquals(SetupHealth.Refused, vm.health.value)
    }

    @Test
    fun `a usable Health Connect whose first grants read fails still shows the section with Connect`() {
        health.availabilityValue = HealthConnectAvailability.Available
        health.grantedTypesReadFails = true

        val vm = newViewModel()

        assertTrue(vm.state.value is FirstRunGateState.ShowSetup)
        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `an older read that finishes after a newer one can't overwrite it`() {
        health.availabilityValue = HealthConnectAvailability.Available
        val vm = newViewModel()
        val older = CompletableDeferred<Set<HealthDataType>>()
        val newer = CompletableDeferred<Set<HealthDataType>>()
        health.pendingGrantedTypes.addLast(older)
        health.pendingGrantedTypes.addLast(newer)

        vm.onResume()
        vm.onHealthConnectResult(anyGranted = true)
        newer.complete(setOf(HealthDataType.STEPS))
        older.complete(emptySet())

        assertEquals(SetupHealth.Readable(setOf(HealthDataType.STEPS)), vm.health.value)
    }

    @Test
    fun `on Android 9 to 13 a missing Health Connect offers the install, then Connect once it is installed`() {
        health.availabilityValue = HealthConnectAvailability.Unavailable
        val vm = newViewModel(sdkInt = 33)
        assertEquals(SetupHealth.NotInstalled, vm.health.value)

        health.availabilityValue = HealthConnectAvailability.Available
        vm.onResume()

        assertEquals(SetupHealth.CanConnect, vm.health.value)
    }

    @Test
    fun `on Android 8 and on an unsupported Android 14 device the section is hidden`() {
        health.availabilityValue = HealthConnectAvailability.Unavailable

        assertEquals(SetupHealth.Hidden, newViewModel(sdkInt = 26).health.value)
        assertEquals(SetupHealth.Hidden, newViewModel(sdkInt = 34).health.value)
    }

    @Test
    fun `resume does not read Health Connect while Continue is being written`() = runTest {
        health.availabilityValue = HealthConnectAvailability.Available
        settings.applySetupChoicesGate = CompletableDeferred()
        val vm = newViewModel()
        val setup = vm.state.value as FirstRunGateState.ShowSetup
        vm.complete(setup.preselected)
        val readsBefore = health.grantedTypesCallCount

        vm.onResume()

        assertEquals(readsBefore, health.grantedTypesCallCount)
    }

    @Test
    fun `the section's state maps from availability, grants, the refusal and the API level`() {
        val available = HealthConnectAvailability.Available
        val unavailable = HealthConnectAvailability.Unavailable
        val some = setOf(HealthDataType.HEART_RATE)

        assertEquals(SetupHealth.CanConnect, setupHealthFor(available, emptySet(), refused = false, sdkInt = 34))
        assertEquals(SetupHealth.Refused, setupHealthFor(available, emptySet(), refused = true, sdkInt = 34))
        assertEquals(SetupHealth.Readable(some), setupHealthFor(available, some, refused = true, sdkInt = 34))
        assertEquals(SetupHealth.UpdateRequired, setupHealthFor(HealthConnectAvailability.UpdateRequired, emptySet(), refused = true, sdkInt = 26))
        // Android 8.x: no Health Connect app exists for it.
        assertEquals(SetupHealth.Hidden, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 26))
        assertEquals(SetupHealth.Hidden, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 27))
        // Android 9-13: the app can be installed from Google Play.
        assertEquals(SetupHealth.NotInstalled, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 28))
        assertEquals(SetupHealth.NotInstalled, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 33))
        // Android 14+: built in, so unavailable means unsupported.
        assertEquals(SetupHealth.Hidden, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 34))
        assertEquals(SetupHealth.Hidden, setupHealthFor(unavailable, emptySet(), refused = false, sdkInt = 35))
    }
}
