package com.enil.logez.feature.wellness

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.health.connect.client.PermissionController

/**
 * M21e. Unlike [com.enil.logez.feature.activity.rememberRequestLocationForTracking], no rationale
 * dialog gates the launch here -- steps/calories are a "nice to have" Profile-tab stat, not a
 * feature that can't function at all without the grant (Health Connect's own system permission
 * screen already explains what's being requested), so the simpler direct-launch is a deliberate
 * scope choice, not an oversight.
 */
@Composable
fun rememberRequestHealthConnectPermissions(
    source: HealthMetricsSource,
    onResult: (allGranted: Boolean) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        onResult(granted.containsAll(source.requiredPermissions))
    }
    return { launcher.launch(source.requiredPermissions) }
}
