package com.enil.logez.feature.onboarding

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.enil.logez.R

/**
 * Shows the gate's [message] in the app's [snackbarHostState] once the app opens after a restore
 * from setup (O1e), then calls [onShown].
 *
 * [onShown] runs only after the snackbar has gone, never before it shows: a rotation while it is up
 * cancels this effect with the old window, and the message, still held by the gate, shows again in
 * the new one.
 *
 * It shows only while the window is started. A restore can finish while this window is hidden,
 * behind a second window a widget tap opened or with the app in the background; a snackbar timed
 * out there would use the message up unseen. It waits for the window to come back instead, and a
 * window that is hidden mid-show shows it again from the start when it returns.
 *
 * Plain "Restored" is short. Anything else says the restore left something undone or did not
 * happen ("Restore did not finish…", the left-out count, a failure), so it stays up longer.
 */
@Composable
fun FirstRunMessageSnackbar(
    message: FirstRunMessage?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
) {
    val resources = LocalResources.current
    val latestOnShown by rememberUpdatedState(onShown)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(message, lifecycle) {
        val shown = message ?: return@LaunchedEffect
        val text = shown.count
            ?.let { resources.getQuantityString(shown.messageRes, it, it) }
            ?: resources.getString(shown.messageRes)
        var done = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (done) return@repeatOnLifecycle
            snackbarHostState.showSnackbar(text, duration = firstRunMessageDuration(shown))
            done = true
            latestOnShown()
        }
    }
}

/** How long [message] stays up: Short only for plain "Restored". */
internal fun firstRunMessageDuration(message: FirstRunMessage): SnackbarDuration =
    if (message.messageRes == R.string.data_restore_done && message.count == null) {
        SnackbarDuration.Short
    } else {
        SnackbarDuration.Long
    }
