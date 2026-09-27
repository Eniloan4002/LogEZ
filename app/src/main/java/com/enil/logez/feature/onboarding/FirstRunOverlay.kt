package com.enil.logez.feature.onboarding

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.domain.model.SetupChoices

/**
 * The app root's first-run layer (first-run plan, "Gate placement"): [app] is always composed, so
 * cold-start recovery can navigate at any time, and [FirstRunOverlay] is drawn above it. While the
 * gate is anything but [FirstRunGateState.ShowApp], [app] gets a modifier that hides it from
 * TalkBack and keyboard focus ([hiddenBehindFirstRun]); the overlay takes the touches.
 *
 * `testTagsAsResourceId` is set here, at the root, so setup's tags (`firstrun_continue`, …) show up
 * as resource ids for UI Automator, adb UI dumps and a Play pre-launch Robo script.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FirstRunHost(
    state: FirstRunGateState,
    onContinue: (SetupChoices) -> Unit,
    onHandOff: suspend () -> Unit,
    onResume: () -> Unit,
    app: @Composable (Modifier) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        app(Modifier.hiddenBehindFirstRun(hidden = state != FirstRunGateState.ShowApp))
        FirstRunOverlay(state = state, onContinue = onContinue, onHandOff = onHandOff, onResume = onResume)
    }
}

/**
 * What first run draws above the app.
 *
 * - [FirstRunGateState.Loading]: an opaque page-black cover that takes touches and draws nothing,
 *   bounded by the gate's timeout.
 * - [FirstRunGateState.ShowSetup]: [FirstRunSetupScreen].
 * - [FirstRunGateState.HandOff]: setup stays on screen, disabled and with the user's choices, while
 *   [onHandOff] moves the app to the Workout tab underneath, so History never flashes. [onHandOff]
 *   must end by removing the overlay ([FirstRunGateViewModel.handoffDone]); it runs again if the
 *   Activity is recreated mid-hand-off, so it must be safe to repeat.
 * - [FirstRunGateState.ShowApp]: nothing.
 *
 * [onResume] runs on every resume while setup shows (a second window may have finished setup).
 */
@Composable
fun FirstRunOverlay(
    state: FirstRunGateState,
    onContinue: (SetupChoices) -> Unit,
    onHandOff: suspend () -> Unit,
    onResume: () -> Unit,
) {
    val setup = when (state) {
        is FirstRunGateState.ShowSetup -> state
        // The hand-off carries the setup that was showing, so a recreated Activity draws it too.
        is FirstRunGateState.HandOff -> state.setup.copy(working = true)
        else -> null
    }

    // One call site for both ShowSetup and HandOff, so the screen's saved choices carry across.
    if (setup != null) {
        FirstRunSetupScreen(
            preselected = setup.preselected,
            regionNoteVisible = setup.regionNoteVisible,
            working = setup.working,
            onContinue = onContinue,
        )
    } else if (state != FirstRunGateState.ShowApp) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {}
    }

    if (state is FirstRunGateState.ShowSetup) {
        RefreshOnResume(onResume)
    }

    val currentOnHandOff by rememberUpdatedState(onHandOff)
    LaunchedEffect(state) {
        if (state is FirstRunGateState.HandOff) currentOnHandOff()
    }
}

/**
 * Hides the app while first run covers it: TalkBack can't reach it (`clearAndSetSemantics`), and
 * keyboard or D-pad focus can't enter it, since clearing semantics does not stop focus and the
 * bottom bar's items are focusable. With [hidden] false it changes nothing.
 */
fun Modifier.hiddenBehindFirstRun(hidden: Boolean): Modifier =
    if (hidden) {
        this
            .clearAndSetSemantics {}
            .focusProperties { onEnter = { cancelFocusChange() } }
            .focusGroup()
    } else {
        this
    }
