package com.enil.logez.feature.onboarding

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.core.domain.repository.TipId
import kotlinx.coroutines.flow.first

/**
 * One tip's state on one screen visit.
 *
 * @property canShow whether the screen may draw the tip (it still adds its own conditions, such
 *   as "at least one exercise").
 * @property onScreen call once the tip has actually been on screen: it is then marked seen, and
 *   stays for the rest of this visit.
 * @property gotIt "Got it": marks it seen and removes it for this visit.
 */
class OneTimeTip internal constructor(
    val canShow: Boolean,
    val onScreen: () -> Unit,
    val gotIt: () -> Unit,
)

/** [rememberOneTimeTip] over a [TipsViewModel]. */
@Composable
fun rememberOneTimeTip(tip: TipId, tips: TipsViewModel, saveable: Boolean = true): OneTimeTip {
    val unseen by tips.unseen.collectAsStateWithLifecycle()
    return rememberOneTimeTip(tip, unseen, tips::markSeen, saveable)
}

/**
 * First-run plan (O1g, Decision 12): a tip shows while it is unseen, and once it has been on
 * screen it stays for the rest of the visit, through rotation and process death
 * (`rememberSaveable`), even though it is already recorded as seen. "Got it" removes it for good.
 * A tip is never marked seen merely because a lazy list composed it off screen: the screen calls
 * [OneTimeTip.onScreen] only when it is really visible ([MarkTipSeenWhenOnScreen]).
 *
 * Pass [saveable] false when the tip's host does not itself survive recreation, such as the
 * exercise picker, which its screens open from plain `remember` state. A saved "shown this visit"
 * would then outlive the visit: nothing reads it back on return (the picker is closed), so it waits
 * in the saved state and the next, unrelated open would take it and show the seen tip again.
 */
@Composable
fun rememberOneTimeTip(tip: TipId, unseen: Set<TipId>, markSeen: (TipId) -> Unit, saveable: Boolean = true): OneTimeTip {
    val shownState = if (saveable) rememberSaveable(tip) { mutableStateOf(false) } else remember(tip) { mutableStateOf(false) }
    val dismissedState = if (saveable) rememberSaveable(tip) { mutableStateOf(false) } else remember(tip) { mutableStateOf(false) }
    var shownThisVisit by shownState
    var dismissed by dismissedState
    return OneTimeTip(
        canShow = !dismissed && (shownThisVisit || tip in unseen),
        onScreen = {
            if (!shownThisVisit) {
                shownThisVisit = true
                markSeen(tip)
            }
        },
        gotIt = {
            dismissed = true
            markSeen(tip)
        },
    )
}

/**
 * Calls [onScreen] once the list item with [key] is at least half on screen (or, for an item taller
 * than the list, fills at least half of it). A lazy list also composes items just past its edge to
 * prefetch them, so being composed is not proof of being seen.
 */
@Composable
fun MarkTipSeenWhenOnScreen(listState: LazyListState, key: Any, onScreen: () -> Unit) {
    val currentOnScreen by rememberUpdatedState(onScreen)
    LaunchedEffect(listState, key) {
        snapshotFlow { listState.layoutInfo.isItemOnScreen(key) }.first { it }
        currentOnScreen()
    }
}

/** Visible means at least half of the item, or half of the viewport for an item taller than it. */
internal fun LazyListLayoutInfo.isItemOnScreen(key: Any): Boolean {
    val item = visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    val viewport = viewportEndOffset - viewportStartOffset
    if (viewport <= 0 || item.size <= 0) return false
    val top = maxOf(item.offset, viewportStartOffset)
    val bottom = minOf(item.offset + item.size, viewportEndOffset)
    val visible = bottom - top
    return visible > 0 && visible * 2 >= minOf(item.size, viewport)
}
