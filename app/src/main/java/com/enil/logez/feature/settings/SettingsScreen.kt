package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Which selection dialog is open, if any. One at a time — each row opens its own. */
private enum class SettingsDialog { WEIGHT_UNIT, DISTANCE_UNIT, FIRST_DAY, REST_TIMER, PREVIOUS_VALUES }

/**
 * M16 Workout Settings (PHASE2_PLAN.md §5.2 Settings tree). Every editor writes through the
 * repository the moment it changes — no Save button, back = done. The Calculators section's two
 * toggles persist real fields today but gate nothing visible until M17 (plate calculator) and
 * M18 (warm-up generator) land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSoundsClick: () -> Unit,
    onPlateEquipmentClick: () -> Unit,
    onWarmupSetsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { ScreenTitle(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- PREFERENCES ---
            item(key = "section_preferences") { SettingsSectionHeader(stringResource(R.string.settings_section_preferences)) }
            item(key = "weight_unit") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_weight_unit),
                    value = weightUnitShortLabel(settings.weightUnit),
                    onClick = { openDialog = SettingsDialog.WEIGHT_UNIT },
                )
            }
            item(key = "distance_unit") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_distance_unit),
                    value = distanceUnitShortLabel(settings.distanceUnit),
                    onClick = { openDialog = SettingsDialog.DISTANCE_UNIT },
                )
            }
            item(key = "first_day") {
                SettingsValueRow(
                    title = stringResource(R.string.calendar_first_day_of_week),
                    value = settings.firstDayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    onClick = { openDialog = SettingsDialog.FIRST_DAY },
                )
            }

            // --- WORKOUTS ---
            item(key = "section_workouts") { SettingsSectionHeader(stringResource(R.string.settings_section_workouts)) }
            item(key = "rest_timer") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_rest_timer),
                    subtitle = stringResource(R.string.settings_rest_timer_subtitle),
                    value = restTimerLabel(settings.defaultRestTimerSeconds),
                    onClick = { openDialog = SettingsDialog.REST_TIMER },
                )
            }
            item(key = "previous_values") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_previous_values),
                    subtitle = stringResource(R.string.settings_previous_values_subtitle),
                    value = previousValuesLabel(settings.previousValuesMode),
                    onClick = { openDialog = SettingsDialog.PREVIOUS_VALUES },
                )
            }
            item(key = "keep_awake") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_awake),
                    subtitle = stringResource(R.string.settings_keep_awake_subtitle),
                    checked = settings.keepAwake,
                    onCheckedChange = viewModel::setKeepAwake,
                )
            }
            item(key = "smart_superset") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_smart_superset),
                    subtitle = stringResource(R.string.settings_smart_superset_subtitle),
                    checked = settings.smartSupersetScrolling,
                    onCheckedChange = viewModel::setSmartSupersetScrolling,
                )
            }
            item(key = "inline_timer") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_inline_timer),
                    subtitle = stringResource(R.string.settings_inline_timer_subtitle),
                    checked = settings.inlineTimerEnabled,
                    onCheckedChange = viewModel::setInlineTimerEnabled,
                )
            }
            item(key = "live_pr") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_live_pr),
                    subtitle = stringResource(R.string.settings_live_pr_subtitle),
                    checked = settings.livePrNotificationEnabled,
                    onCheckedChange = viewModel::setLivePrNotificationEnabled,
                )
            }
            item(key = "rpe_tracking") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_rpe),
                    subtitle = stringResource(R.string.settings_rpe_subtitle),
                    checked = settings.rpeTrackingEnabled,
                    onCheckedChange = viewModel::setRpeTrackingEnabled,
                )
            }
            item(key = "include_warmups") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_include_warmups),
                    subtitle = stringResource(R.string.settings_include_warmups_subtitle),
                    checked = settings.includeWarmupsInStats,
                    onCheckedChange = viewModel::setIncludeWarmupsInStats,
                )
            }
            item(key = "show_heatmap") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_heatmap),
                    subtitle = stringResource(R.string.settings_show_heatmap_subtitle),
                    checked = settings.showHeatmap,
                    onCheckedChange = viewModel::setShowHeatmap,
                )
            }
            item(key = "show_goals") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_goals),
                    subtitle = stringResource(R.string.settings_show_goals_subtitle),
                    checked = settings.showGoals,
                    onCheckedChange = viewModel::setShowGoals,
                )
            }

            // --- CALCULATORS (persist today; the visible affordances land in M17/M18) ---
            item(key = "section_calculators") { SettingsSectionHeader(stringResource(R.string.settings_section_calculators)) }
            item(key = "plate_calculator") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_plate_calculator),
                    subtitle = stringResource(R.string.settings_plate_calculator_subtitle),
                    checked = settings.plateCalculatorEnabled,
                    onCheckedChange = viewModel::setPlateCalculatorEnabled,
                )
            }
            item(key = "plate_equipment") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_plate_equipment_row),
                    subtitle = stringResource(R.string.settings_plate_equipment_subtitle),
                    value = stringResource(
                        R.string.settings_plate_equipment_value,
                        pluralStringResource(R.plurals.settings_plate_bar_count, settings.plateEquipment.barsKg.size, settings.plateEquipment.barsKg.size),
                        pluralStringResource(R.plurals.settings_plate_plate_count, settings.plateEquipment.platesKg.size, settings.plateEquipment.platesKg.size),
                    ),
                    onClick = onPlateEquipmentClick,
                )
            }
            item(key = "warmup_calculator") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_warmup_calculator),
                    subtitle = stringResource(R.string.settings_warmup_calculator_subtitle),
                    checked = settings.warmupCalculatorEnabled,
                    onCheckedChange = viewModel::setWarmupCalculatorEnabled,
                )
            }
            item(key = "warmup_method") {
                // M18: value-preview of the persisted ladder ("40% ×5 · 60% ×5 · 80% ×3") — percent
                // rows are unit-less by construction, so nothing here needs kg/lb conversion.
                SettingsValueRow(
                    title = stringResource(R.string.settings_warmup_method_row),
                    subtitle = stringResource(R.string.settings_warmup_method_subtitle),
                    value = settings.warmupMethod
                        .map { stringResource(R.string.settings_warmup_step_value, it.displayPercent(), it.reps) }
                        .joinToString(" · "),
                    onClick = onWarmupSetsClick,
                )
            }

            // --- SOUNDS ---
            item(key = "section_sounds") { SettingsSectionHeader(stringResource(R.string.settings_section_sounds)) }
            item(key = "sounds_nav") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_sounds_row),
                    subtitle = stringResource(R.string.settings_sounds_row_subtitle),
                    value = "",
                    onClick = onSoundsClick,
                )
            }
        }
    }

    when (openDialog) {
        SettingsDialog.WEIGHT_UNIT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_weight_unit),
            options = WeightUnit.entries,
            selected = settings.weightUnit,
            optionLabel = { weightUnitLabel(it) },
            onSelect = viewModel::setWeightUnit,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.DISTANCE_UNIT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_distance_unit),
            options = DistanceUnit.entries,
            selected = settings.distanceUnit,
            optionLabel = { distanceUnitLabel(it) },
            onSelect = viewModel::setDistanceUnit,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.FIRST_DAY -> SettingsRadioDialog(
            title = stringResource(R.string.calendar_first_day_of_week),
            // §5.2 lists exactly these three — the conventional week starts (same trio as the
            // Calendar screen's picker), not all seven days.
            options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            selected = settings.firstDayOfWeek,
            optionLabel = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
            onSelect = viewModel::setFirstDayOfWeek,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.REST_TIMER -> SettingsRadioDialog(
            title = stringResource(R.string.settings_rest_timer),
            // 0 = off (spine convention shared with RestTimerPickerSheet), then the common gym
            // rest lengths — not the routine picker's full 5s-step list, which would be 61 rows.
            options = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300),
            selected = settings.defaultRestTimerSeconds,
            optionLabel = { restTimerLabel(it) },
            onSelect = viewModel::setDefaultRestTimerSeconds,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.PREVIOUS_VALUES -> SettingsRadioDialog(
            title = stringResource(R.string.settings_previous_values),
            options = PreviousValuesMode.entries,
            selected = settings.previousValuesMode,
            optionLabel = { previousValuesLabel(it) },
            onSelect = viewModel::setPreviousValuesMode,
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun weightUnitLabel(unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> stringResource(R.string.settings_weight_unit_kg)
    WeightUnit.LB -> stringResource(R.string.settings_weight_unit_lb)
}

/** Matches the lowercase "kg"/"lb" used everywhere values render (PreviousValueFormatter etc.). */
private fun weightUnitShortLabel(unit: WeightUnit): String = if (unit == WeightUnit.KG) "kg" else "lb"

@Composable
private fun distanceUnitLabel(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KM -> stringResource(R.string.settings_distance_unit_km)
    DistanceUnit.MILES -> stringResource(R.string.settings_distance_unit_miles)
}

private fun distanceUnitShortLabel(unit: DistanceUnit): String = if (unit == DistanceUnit.KM) "km" else "mi"

@Composable
private fun previousValuesLabel(mode: PreviousValuesMode): String = when (mode) {
    PreviousValuesMode.ANY_WORKOUT -> stringResource(R.string.settings_previous_values_any)
    PreviousValuesMode.SAME_ROUTINE -> stringResource(R.string.settings_previous_values_same_routine)
}

/** "Off" for 0, otherwise m:ss — the same rendering the routine rest-timer picker uses. */
@Composable
internal fun restTimerLabel(totalSeconds: Int): String {
    if (totalSeconds == 0) return stringResource(R.string.rest_timer_off_option)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
