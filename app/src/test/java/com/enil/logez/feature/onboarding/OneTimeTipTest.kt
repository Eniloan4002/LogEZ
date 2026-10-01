package com.enil.logez.feature.onboarding

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.HintCard
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.repository.TipId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g, Decision 12): a tip is marked seen when "Got it" is tapped or once it has
 * really been on screen, never merely because the list built it; once seen it stays for the visit,
 * including through recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneTimeTipTest {
    @get:Rule val rule = createComposeRule()

    private var unseen by mutableStateOf(setOf(TipId.LOGGER))
    private val marked = mutableListOf<TipId>()
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun markSeen(tip: TipId) {
        marked += tip
        unseen = unseen - tip
    }

    /** A 400dp list: [fillerRows] 100dp rows, then the tip as the last item, like the logger's card. */
    private fun setList(fillerRows: Int, restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            LogEzTheme {
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                val tip = rememberOneTimeTip(TipId.LOGGER, unseen, ::markSeen)
                LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                    items(count = fillerRows, key = { "row_$it" }) { Text("Row $it", modifier = Modifier.height(100.dp)) }
                    if (tip.canShow) {
                        item(key = "tip") { HintCard(lines = listOf("Tip text"), onGotIt = tip.gotIt, modifier = Modifier.height(100.dp)) }
                    }
                }
                if (tip.canShow) MarkTipSeenWhenOnScreen(listState, "tip", tip.onScreen)
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
    }

    @Test
    fun `a tip on screen is marked seen once`() {
        setList(fillerRows = 1)
        rule.waitForIdle()

        rule.onNodeWithText("Tip text").assertIsDisplayed()
        assertEquals(listOf(TipId.LOGGER), marked)
    }

    @Test
    fun `a tip with only a sliver showing is not marked seen until it is scrolled into view`() {
        // Rows end at 400dp in a 400dp list; a 10dp scroll leaves a 10dp sliver of the tip showing.
        setList(fillerRows = 4)
        rule.waitForIdle()
        val tenDp = with(rule.density) { 10.dp.toPx() }
        rule.runOnIdle { scope.launch { listState.scrollBy(tenDp) } }
        rule.waitForIdle()
        assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.key == "tip" })
        assertEquals(emptyList<TipId>(), marked)

        rule.runOnIdle { scope.launch { listState.scrollToItem(4) } }
        rule.waitForIdle()

        assertEquals(listOf(TipId.LOGGER), marked)
    }

    @Test
    fun `a seen tip stays for the rest of the visit`() {
        setList(fillerRows = 1)
        rule.waitForIdle()

        assertFalse(TipId.LOGGER in unseen)
        rule.onNodeWithText("Tip text").assertIsDisplayed()
    }

    @Test
    fun `Got it removes the tip and marks it seen`() {
        setList(fillerRows = 30)
        rule.waitForIdle()
        assertEquals(emptyList<TipId>(), marked)
        rule.runOnIdle { scope.launch { listState.scrollToItem(30) } }
        rule.waitForIdle()

        rule.onNodeWithText("Got it").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Tip text").assertDoesNotExist()
        assertEquals(listOf(TipId.LOGGER, TipId.LOGGER), marked)
    }

    @Test
    fun `a tip that is not unseen never shows`() {
        unseen = emptySet()
        setList(fillerRows = 1)
        rule.waitForIdle()

        rule.onNodeWithText("Tip text").assertDoesNotExist()
        assertEquals(emptyList<TipId>(), marked)
    }

    @Test
    fun `a tip shown before recreation is still shown after it, though already seen`() {
        val restoration = StateRestorationTester(rule)
        setList(fillerRows = 1, restoration = restoration)
        rule.waitForIdle()
        assertEquals(emptySet<TipId>(), unseen)

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("Tip text").assertIsDisplayed()
        assertEquals(listOf(TipId.LOGGER), marked)
    }

    @Test
    fun `a dismissed tip stays dismissed after recreation`() {
        val restoration = StateRestorationTester(rule)
        setList(fillerRows = 1, restoration = restoration)
        rule.onNodeWithText("Got it").performClick()
        rule.waitForIdle()
        // Even if the store had not kept the write, the visit remembers the dismissal.
        unseen = setOf(TipId.LOGGER)

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("Tip text").assertDoesNotExist()
    }

    // --- isItemOnScreen, on hand-built layout info ---

    @Test
    fun `an item at least half inside the viewport is on screen`() {
        assertTrue(layout(itemOffset = 350, itemSize = 100).isItemOnScreen("tip"))
    }

    @Test
    fun `a sliver of an item is not on screen`() {
        assertFalse(layout(itemOffset = 390, itemSize = 100).isItemOnScreen("tip"))
    }

    @Test
    fun `an item taller than the viewport is on screen once it fills half of it`() {
        assertTrue(layout(itemOffset = 200, itemSize = 900).isItemOnScreen("tip"))
        assertFalse(layout(itemOffset = 250, itemSize = 900).isItemOnScreen("tip"))
    }

    @Test
    fun `an item the list has not laid out is not on screen`() {
        assertFalse(layout(itemOffset = 0, itemSize = 100, key = "row_1").isItemOnScreen("tip"))
    }

    private fun layout(itemOffset: Int, itemSize: Int, key: Any = "tip") = object : LazyListLayoutInfo {
        override val visibleItemsInfo: List<LazyListItemInfo> = listOf(
            object : LazyListItemInfo {
                override val index = 0
                override val key: Any = key
                override val offset = itemOffset
                override val size = itemSize
            },
        )
        override val viewportStartOffset = 0
        override val viewportEndOffset = 400
        override val totalItemsCount = 1
        override val viewportSize = IntSize(300, 400)
        override val orientation = Orientation.Vertical
    }
}
