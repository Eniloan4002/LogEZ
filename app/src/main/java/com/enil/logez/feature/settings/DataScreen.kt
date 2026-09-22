package com.enil.logez.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → Data. The app's privacy policy has pointed users here since it was written; this is
 * the screen it meant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingKind by remember { mutableStateOf<ExportKind?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val kind = pendingKind
        pendingKind = null
        if (uri != null && kind != null) viewModel.export(kind, uri) else viewModel.exportCancelled()
    }

    fun startExport(kind: ExportKind, fileName: String) {
        pendingKind = kind
        createDocument.launch(fileName)
    }

    // Resolved in composition: stringResource cannot be called from inside a LaunchedEffect.
    val doneMessage = stringResource(R.string.data_export_done)
    val failedMessage = stringResource(R.string.data_export_failed)
    LaunchedEffect(uiState.job) {
        when (uiState.job) {
            is DataJob.Done -> {
                snackbarHostState.showSnackbar(doneMessage)
                viewModel.dismissJob()
            }
            is DataJob.Failed -> {
                snackbarHostState.showSnackbar(failedMessage)
                viewModel.dismissJob()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.settings_section_data)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.job is DataJob.Working) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md))
                }
            }

            item(key = "section_export") { SettingsSectionHeader(stringResource(R.string.data_section_export)) }

            item(key = "export_workouts") {
                SettingsValueRow(
                    title = stringResource(R.string.data_export_workouts),
                    subtitle = stringResource(R.string.data_export_workouts_subtitle),
                    value = uiState.workoutSetCount?.let {
                        pluralStringResource(R.plurals.data_set_count, it, it)
                    }.orEmpty(),
                    onClick = { startExport(ExportKind.WORKOUTS_CSV, fileName("logez_workouts", "csv")) },
                )
            }

            item(key = "export_measurements") {
                SettingsValueRow(
                    title = stringResource(R.string.data_export_measurements),
                    subtitle = stringResource(R.string.data_export_measurements_subtitle),
                    value = uiState.measurementCount?.let {
                        pluralStringResource(R.plurals.data_entry_count, it, it)
                    }.orEmpty(),
                    onClick = { startExport(ExportKind.MEASUREMENTS_CSV, fileName("logez_measurements", "csv")) },
                )
            }

            item(key = "privacy_note") {
                Text(
                    text = stringResource(R.string.data_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                )
            }
        }
    }
}

private fun fileName(prefix: String, extension: String): String {
    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
    return "${prefix}_$stamp.$extension"
}
