package com.enil.logez.feature.onboarding

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.TipId
import com.enil.logez.fakes.FakeFirstRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * First-run plan (O1g): tips show only on an install that passed through setup, or after "Show tips
 * again", and each is recorded as seen once it has been shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TipsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val allTips = setOf(TipId.PICKER, TipId.LOGGER, TipId.BUILDER)

    @Test
    fun `an install that passed through setup gets every tip`() {
        val vm = TipsViewModel(FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP), AppLogger.NoOp)
        assertEquals(allTips, vm.unseen.value)
    }

    @Test
    fun `an install that skipped setup silently gets no tips`() {
        val vm = TipsViewModel(FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING), AppLogger.NoOp)
        assertEquals(emptySet<TipId>(), vm.unseen.value)
    }

    @Test
    fun `an install restored from setup gets no tips`() {
        val vm = TipsViewModel(FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.RESTORE), AppLogger.NoOp)
        assertEquals(emptySet<TipId>(), vm.unseen.value)
    }

    @Test
    fun `an install with no first-run path, or one this build does not know, gets no tips`() {
        val vm = TipsViewModel(FakeFirstRunStore(doneAt = 1L, storedPath = null), AppLogger.NoOp)
        assertEquals(emptySet<TipId>(), vm.unseen.value)
    }

    @Test
    fun `a tip already seen does not show again`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP).apply { seenTips += TipId.PICKER }
        val vm = TipsViewModel(store, AppLogger.NoOp)
        assertEquals(setOf(TipId.LOGGER, TipId.BUILDER), vm.unseen.value)
    }

    @Test
    fun `markSeen records the tip and removes it from the tips that may show`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val vm = TipsViewModel(store, AppLogger.NoOp)

        vm.markSeen(TipId.LOGGER)

        assertEquals(setOf(TipId.LOGGER), store.seenTips)
        assertEquals(setOf(TipId.PICKER, TipId.BUILDER), vm.unseen.value)
        // A second view builds a new ViewModel: the tip stays gone.
        assertEquals(setOf(TipId.PICKER, TipId.BUILDER), TipsViewModel(store, AppLogger.NoOp).unseen.value)
    }

    @Test
    fun `marking a tip seen twice writes it once`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val vm = TipsViewModel(store, AppLogger.NoOp)

        vm.markSeen(TipId.PICKER)
        vm.markSeen(TipId.PICKER)

        assertEquals(1, store.markTipSeenCallCount)
    }

    @Test
    fun `markSeen writes nothing on an install that gets no tips`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        TipsViewModel(store, AppLogger.NoOp).markSeen(TipId.BUILDER)
        assertEquals(0, store.markTipSeenCallCount)
    }

    @Test
    fun `Show tips again brings every tip back on an install that skipped setup`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        val vm = TipsViewModel(store, AppLogger.NoOp)
        assertFalse(vm.tipsShownAgain.value)

        vm.showTipsAgain()

        assertEquals(allTips, vm.unseen.value)
        assertTrue(vm.tipsShownAgain.value)
        assertTrue(store.reenabled)
        // Setup itself is not touched.
        assertEquals(1L, store.doneAt)
        assertEquals(FirstRunPath.EXISTING, store.storedPath)
    }

    @Test
    fun `Show tips again clears tips seen on a setup install, and each then shows once more`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
            .apply { seenTips += setOf(TipId.PICKER, TipId.LOGGER, TipId.BUILDER) }
        val vm = TipsViewModel(store, AppLogger.NoOp)
        assertEquals(emptySet<TipId>(), vm.unseen.value)

        vm.showTipsAgain()
        vm.markSeen(TipId.PICKER)

        assertEquals(setOf(TipId.LOGGER, TipId.BUILDER), vm.unseen.value)
        assertEquals(setOf(TipId.PICKER), store.seenTips)
    }

    @Test
    fun `refresh picks up Show tips again made from another screen`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        val logger = TipsViewModel(store, AppLogger.NoOp)
        val settings = TipsViewModel(store, AppLogger.NoOp)

        settings.showTipsAgain()
        assertEquals(emptySet<TipId>(), logger.unseen.value)
        logger.refresh()

        assertEquals(allTips, logger.unseen.value)
    }
}
