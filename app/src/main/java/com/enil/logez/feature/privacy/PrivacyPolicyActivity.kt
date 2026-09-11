package com.enil.logez.feature.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing

/**
 * A deliberate, narrow exception to this app's single-Activity architecture (`MainActivity` hosts
 * every other screen via Compose Navigation, PHASE2_PLAN.md §2.1). Health Connect's own permission
 * screen needs to launch a real, independently-resolvable Activity component for its "why does
 * this app want this data" link -- on Android 13 and earlier via an intent-filter for
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, on Android 14+ via a `ViewPermissionUsageActivity`
 * alias handling `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` (both declared in AndroidManifest.xml,
 * per Android's official Health Connect integration guide) -- neither of which can target a
 * destination inside the app's own single Activity's nav graph.
 *
 * Without this, Health Connect's permission screen refuses to show the grant checkboxes at all --
 * confirmed on-device via logcat (`PermissionsActivity: App should support rationale intent,
 * finishing!`) -- which is exactly why tapping "Connect" on the Profile wellness card looked like a
 * dead button before this activity existed: the OS-owned permission screen opened and immediately
 * self-closed, with nothing this app's own code could catch or react to.
 *
 * Also reachable directly from Settings, as an ordinary privacy-policy screen -- the same content
 * either way.
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogEzTheme {
                PrivacyPolicyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { ScreenTitle(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.privacy_policy_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
