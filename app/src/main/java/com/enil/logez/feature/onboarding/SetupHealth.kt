package com.enil.logez.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.canInstallOrUpdate
import com.enil.logez.core.wellness.openHealthConnectInPlayStore
import com.enil.logez.core.wellness.openHealthConnectSettings
import com.enil.logez.core.wellness.rememberRequestHealthConnectPermissions

/**
 * What setup's optional Health Connect section shows (first-run plan, O1f, the six states). Nothing
 * here asks for anything by itself: every request starts from a tap.
 */
sealed interface SetupHealth {
    /** Android 8.x, or an Android 14+ device that doesn't support Health Connect: no section. */
    data object Hidden : SetupHealth

    /** Health Connect is usable and nothing is granted yet: the short explanation and Connect. */
    data object CanConnect : SetupHealth

    /** Some or all types are granted: "LogEZ can read: …", and no action. */
    data class Readable(val granted: Set<HealthDataType>) : SetupHealth

    /**
     * A Connect came back with nothing granted. Health Connect can't be asked again from here: after
     * a refusal it answers at once without showing anything, so the section points to its settings.
     */
    data object Refused : SetupHealth

    /** Health Connect is installed but needs an update from Google Play. */
    data object UpdateRequired : SetupHealth

    /** Android 9-13 without the Health Connect app: it can be installed from Google Play. */
    data object NotInstalled : SetupHealth
}

/**
 * The section's state from what Health Connect reports. A refusal can't be read back from Health
 * Connect ([granted] is empty both before asking and after a refusal), so [refused] is LogEZ's own
 * record of it; any grant outranks it.
 */
internal fun setupHealthFor(
    availability: HealthConnectAvailability,
    granted: Set<HealthDataType>,
    refused: Boolean,
    sdkInt: Int,
): SetupHealth = when (availability) {
    HealthConnectAvailability.Available -> when {
        granted.isNotEmpty() -> SetupHealth.Readable(granted)
        refused -> SetupHealth.Refused
        else -> SetupHealth.CanConnect
    }
    HealthConnectAvailability.UpdateRequired -> SetupHealth.UpdateRequired
    HealthConnectAvailability.Unavailable ->
        if (availability.canInstallOrUpdate(sdkInt)) SetupHealth.NotInstalled else SetupHealth.Hidden
}

/** Setup's Health Connect section: what it shows, and what its buttons do. */
class SetupHealthBinding(
    val health: SetupHealth,
    val onConnect: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenPlay: () -> Unit,
) {
    companion object {
        /** No section: previews and tests that are not about Health Connect. */
        val Inert = SetupHealthBinding(SetupHealth.Hidden, onConnect = {}, onOpenSettings = {}, onOpenPlay = {})
    }
}

/**
 * The gate's Health Connect state, with Connect wired to the same request Profile makes (steps,
 * calories burned and heart rate), and the two ways out to Health Connect's settings and to Google
 * Play. The launcher is remembered with saveable state, so a result that arrives after the Activity
 * was recreated still reaches the gate.
 */
@Composable
fun rememberSetupHealth(gate: FirstRunGateViewModel): SetupHealthBinding {
    val health by gate.health.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val connect = rememberRequestHealthConnectPermissions(
        source = gate.healthMetricsSource,
        onResult = gate::onHealthConnectResult,
    )
    return SetupHealthBinding(
        health = health,
        onConnect = connect,
        onOpenSettings = { openHealthConnectSettings(context) },
        onOpenPlay = { openHealthConnectInPlayStore(context) },
    )
}
