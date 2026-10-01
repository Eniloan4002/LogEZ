package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.PlateCalculator
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.setFor
import com.enil.logez.core.designsystem.parseDecimalInput

/** Which add dialog is open, if any. */
private enum class PlateEquipmentDialog { ADD_BAR, ADD_PLATE }

/**
 * M17 Plate Equipment sub-page (§5.1.5's "Manage", relocated into the Settings tree): the bars and
 * plate denominations the Plate Calculator can load. Every add/remove persists instantly through
 * [SettingsViewModel] — no Save button, back = done, matching the rest of the tree. Adds are
 * validated (> 0), rounded to the calculator's quarter-unit grid, and deduplicated; the last bar is
 * not removable (its remove control disappears — a calculator without a bar is meaningless).
 *
 * F9: the editor shows and edits the set for the current weight unit, in that unit (pound plates
 * for a pounds user), and says so at the top. The other unit's set is kept untouched, so switching
 * units and back finds the custom equipment as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateEquipmentScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var openDialog by remember { mutableStateOf<PlateEquipmentDialog?>(null) }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = logEzTopAppBarColors(),
            title = { ScreenTitle(stringResource(R.string.settings_plate_equipment_row)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        )
    }
    // F9: wait for the stored settings. The defaults say KG, so showing them first would flash
    // the kg set to a pounds user, and a tap in that moment would edit the wrong set.
    val settings = viewModel.loadedSettings.collectAsStateWithLifecycle().value
    if (settings == null) {
        Scaffold(topBar = topBar) { padding -> Box(Modifier.fillMaxSize().padding(padding)) }
        return
    }
    val unit = settings.weightUnit
    val plateSet = settings.plateEquipment.setFor(unit)
    val unitSymbol = stringResource(if (unit == WeightUnit.LB) R.string.unit_symbol_lb else R.string.unit_symbol_kg)

    Scaffold(topBar = topBar) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "unit_note") {
                Text(
                    stringResource(if (unit == WeightUnit.LB) R.string.settings_plate_unit_note_lb else R.string.settings_plate_unit_note_kg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            // Keys carry the index: nothing tidies a restored list, so a hand-edited backup with a
            // duplicate weight must not crash the list with a duplicate key.
            item(key = "section_bars") { SettingsSectionHeader(stringResource(R.string.settings_plate_bars_section)) }
            items(count = plateSet.bars.size, key = { "bar_${unit}_${it}_${plateSet.bars[it]}" }) { index ->
                val bar = plateSet.bars[index]
                EquipmentWeightRow(
                    label = formatEquipmentWeight(bar, unitSymbol),
                    removable = plateSet.bars.size > 1,
                    onRemove = { viewModel.removeBar(unit, bar) },
                )
            }
            item(key = "add_bar") {
                TextButton(onClick = { openDialog = PlateEquipmentDialog.ADD_BAR }, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text(stringResource(R.string.settings_plate_add_bar))
                }
            }
            item(key = "last_bar_note") {
                Text(
                    stringResource(R.string.settings_plate_last_bar_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            item(key = "section_plates") { SettingsSectionHeader(stringResource(R.string.settings_plate_plates_section)) }
            items(count = plateSet.plates.size, key = { "plate_${unit}_${it}_${plateSet.plates[it]}" }) { index ->
                val plate = plateSet.plates[index]
                EquipmentWeightRow(
                    label = formatEquipmentWeight(plate, unitSymbol),
                    removable = true,
                    onRemove = { viewModel.removePlate(unit, plate) },
                )
            }
            item(key = "add_plate") {
                TextButton(onClick = { openDialog = PlateEquipmentDialog.ADD_PLATE }, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text(stringResource(R.string.settings_plate_add_plate))
                }
            }
            item(key = "rounding_note") {
                Text(
                    stringResource(roundingNoteRes(unit)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }

    when (openDialog) {
        PlateEquipmentDialog.ADD_BAR -> AddWeightDialog(
            title = stringResource(R.string.settings_plate_add_bar),
            unit = unit,
            existing = plateSet.bars,
            onConfirm = { viewModel.addBar(unit, it); openDialog = null },
            onDismiss = { openDialog = null },
        )
        PlateEquipmentDialog.ADD_PLATE -> AddWeightDialog(
            title = stringResource(R.string.settings_plate_add_plate),
            unit = unit,
            existing = plateSet.plates,
            onConfirm = { viewModel.addPlate(unit, it); openDialog = null },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

/** One bar/plate row: the weight, plus a remove control unless the invariant forbids it. */
@Composable
private fun EquipmentWeightRow(label: String, removable: Boolean, onRemove: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(label) },
        trailingContent = {
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_plate_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

/**
 * Numeric-input dialog for a new bar or plate. Validation mirrors what the persist path enforces
 * (positive, rounded to the quarter of [unit], no duplicates) so Add is only enabled when the
 * write would actually change the list — a disabled Add plus the inline reason beats a silent
 * no-op. [existing] and the confirmed value are in [unit].
 */
@Composable
private fun AddWeightDialog(
    title: String,
    unit: WeightUnit,
    existing: List<Double>,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val rounded = parseDecimalInput(text)?.let { PlateCalculator.roundToQuarterUnit(it) }
    val error = when {
        text.isEmpty() -> null
        rounded == null || rounded <= 0.0 -> stringResource(R.string.settings_plate_invalid_weight)
        rounded in existing -> stringResource(R.string.settings_plate_duplicate_weight)
        else -> null
    }
    val valid = rounded != null && rounded > 0.0 && rounded !in existing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(if (unit == WeightUnit.LB) R.string.settings_plate_weight_label_lb else R.string.settings_plate_weight_label_kg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: stringResource(roundingNoteRes(unit))) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { rounded?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun roundingNoteRes(unit: WeightUnit): Int =
    if (unit == WeightUnit.LB) R.string.settings_plate_rounding_note_lb else R.string.settings_plate_rounding_note_kg

/** "20 kg", "1.25 kg", "45 lb" — the weight is already in the unit [unitSymbol] names. */
private fun formatEquipmentWeight(weight: Double, unitSymbol: String): String =
    "${WeightDisplay.format(weight)} $unitSymbol"
