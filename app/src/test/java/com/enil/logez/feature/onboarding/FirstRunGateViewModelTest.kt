package com.enil.logez.feature.onboarding

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeRegionDefaults
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeUserDataProbe
import com.enil.logez.fakes.FakeWidgetRefresher
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun newViewModel() = FirstRunGateViewModel(store, probe, settings, region, widget, clock, logger)

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
        assertEquals(FirstRunGateState.HandOff, vm.state.value)

        vm.handoffDone()
        assertEquals(FirstRunGateState.ShowApp, vm.state.value)
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

        assertEquals(FirstRunGateState.HandOff, vm.state.value)
        assertFalse(store.isDone())
        assertNull(store.storedPath)
    }

    @Test
    fun `a settings write that throws still hands off, with the flag unwritten`() {
        settings.applySetupChoicesError = java.io.IOException("disk full")
        val vm = newViewModel()

        vm.complete(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.SUNDAY))

        assertEquals(FirstRunGateState.HandOff, vm.state.value)
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
        assertEquals(FirstRunGateState.HandOff, vm.state.value)
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
        assertEquals(FirstRunGateState.HandOff, vm.state.value)
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
        assertEquals(FirstRunGateState.HandOff, vm.state.value)
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
    fun `a resume with the flag still absent keeps setup`() {
        val vm = newViewModel()
        val before = vm.state.value

        vm.onResume()

        assertEquals(before, vm.state.value)
    }
}
