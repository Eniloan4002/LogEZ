package com.enil.logez.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.export.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportKind { WORKOUTS_CSV, MEASUREMENTS_CSV }

sealed interface DataJob {
    data object Idle : DataJob
    data class Working(val kind: ExportKind) : DataJob
    data class Done(val kind: ExportKind, val rowCount: Int) : DataJob
    data class Failed(val messageRes: Int) : DataJob
}

data class DataUiState(
    val workoutSetCount: Int? = null,
    val measurementCount: Int? = null,
    val job: DataJob = DataJob.Idle,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csvExporter: CsvExporter,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workoutSetCount = csvExporter.workoutSetCount(),
                    measurementCount = csvExporter.measurementCount(),
                )
            }
        }
    }

    fun export(kind: ExportKind, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(kind)) }
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("the chosen location is not writable")
                val rows = stream.use { out ->
                    when (kind) {
                        ExportKind.WORKOUTS_CSV -> csvExporter.exportWorkouts(out)
                        ExportKind.MEASUREMENTS_CSV -> csvExporter.exportMeasurements(out)
                    }
                }
                _uiState.update { it.copy(job = DataJob.Done(kind, rows)) }
            } catch (t: Throwable) {
                logger.e(TAG, "Export failed for $kind", t)
                // The picker created the document the moment it returned, so a failure here would
                // otherwise leave a broken empty file wherever the user chose to put it.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_export_failed)) }
            }
        }
    }

    /** The picker was dismissed without choosing anywhere — nothing was created, nothing to clean up. */
    fun exportCancelled() = _uiState.update { it.copy(job = DataJob.Idle) }

    fun dismissJob() = _uiState.update { it.copy(job = DataJob.Idle) }

    private companion object {
        const val TAG = "DataViewModel"
    }
}
