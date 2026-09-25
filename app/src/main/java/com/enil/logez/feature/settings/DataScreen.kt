package com.enil.logez.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.enil.logez.core.wellness.openHealthConnectSettings

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
    val resources = LocalResources.current
    val context = LocalContext.current
    // Saveable: the document picker is another activity, and a configuration change or process
    // death while it is open recreated this screen with the pending kind lost, so the chosen file
    // was created but never written.
    var pendingKind by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    var confirmHealthDisconnect by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val kind = pendingKind
        pendingKind = null
        if (uri != null && kind != null) viewModel.export(kind, uri) else viewModel.pickerCancelled()
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.prepareRestore(uri) else viewModel.pickerCancelled()
    }

    fun startExport(kind: ExportKind, fileName: String) {
        pendingKind = kind
        createDocument.launch(fileName)
    }

    // A job mid-write must not be cancelled by navigating away: the viewModelScope would die with
    // the screen and leave a truncated file that looks valid.
    BackHandler(enabled = uiState.job is DataJob.Working) { }

    LaunchedEffect(uiState.job) {
        val job = uiState.job
        val messageRes = when (job) {
            is DataJob.Done -> job.messageRes
            is DataJob.Failed -> job.messageRes
            else -> null
        }
        if (messageRes != null) {
            snackbarHostState.showSnackbar(resources.getString(messageRes))
            viewModel.dismissJob()
        }
    }

    (uiState.job as? DataJob.ConfirmRestore)?.let { confirm ->
        val m = confirm.manifest
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text(stringResource(R.string.data_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.data_restore_confirm_body,
                        m.workoutCount, m.exerciseCount, m.routineCount,
                        m.measurementCount, m.goalCount, m.mediaFileCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(m) }) {
                    Text(
                        text = stringResource(R.string.data_restore_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmHealthDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmHealthDisconnect = false },
            title = { Text(stringResource(R.string.data_health_disconnect_confirm_title)) },
            text = { Text(stringResource(R.string.data_health_disconnect_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmHealthDisconnect = false
                    viewModel.disconnectHealthConnect()
                }) {
                    Text(
                        text = stringResource(R.string.data_health_disconnect_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmHealthDisconnect = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.data_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllData()
                }) {
                    Text(
                        text = stringResource(R.string.data_delete_all_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
            (uiState.job as? DataJob.Working)?.let { working ->
                item(key = "progress") {
                    Text(
                        text = stringResource(working.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    )
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

            item(key = "export_backup") {
                SettingsValueRow(
                    title = stringResource(R.string.data_export_backup),
                    subtitle = stringResource(R.string.data_export_backup_subtitle),
                    value = "",
                    onClick = { startExport(ExportKind.BACKUP_ZIP, fileName("logez_backup", "zip")) },
                )
            }

            item(key = "section_restore") { SettingsSectionHeader(stringResource(R.string.data_section_restore)) }

            item(key = "restore") {
                SettingsValueRow(
                    title = stringResource(R.string.data_restore),
                    subtitle = stringResource(R.string.data_restore_subtitle),
                    value = "",
                    onClick = { openDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
            }

            item(key = "section_health") { SettingsSectionHeader(stringResource(R.string.data_section_health)) }

            if (uiState.healthConnectAvailable) {
                item(key = "health_manage") {
                    SettingsValueRow(
                        title = stringResource(R.string.data_health_manage),
                        subtitle = stringResource(R.string.data_health_manage_subtitle),
                        value = "",
                        onClick = { openHealthConnectSettings(context) },
                    )
                }
            }

            item(key = "health_disconnect") {
                SettingsValueRow(
                    title = stringResource(R.string.data_health_disconnect),
                    subtitle = stringResource(R.string.data_health_disconnect_subtitle),
                    value = "",
                    onClick = { confirmHealthDisconnect = true },
                )
            }

            item(key = "section_delete") { SettingsSectionHeader(stringResource(R.string.data_section_delete)) }

            item(key = "delete_all") {
                SettingsValueRow(
                    title = stringResource(R.string.data_delete_all),
                    subtitle = stringResource(R.string.data_delete_all_subtitle),
                    value = "",
                    onClick = { confirmDeleteAll = true },
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
