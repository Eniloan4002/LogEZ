package com.enil.logez.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.PlateCalculator
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.setFor
import com.enil.logez.core.domain.model.standardBar
import com.skydoves.flexible.bottomsheet.material3.FlexibleBottomSheet
import com.skydoves.flexible.core.FlexibleSheetSize
import com.skydoves.flexible.core.rememberFlexibleBottomSheetState
import com.enil.logez.core.designsystem.parseDecimalInput

/**
 * §5.1.5 Plate Calculator sheet (M17). Deliberately thin — every solve goes through the tested
 * [PlateCalculator]; this composable only converts between the display unit and canonical kg and
 * renders the result.
 *
 * F9: the solve runs in the user's weight unit with that unit's own equipment set. A pounds user
 * types pounds and loads a 45 lb bar with pound plates; the target, bars, plates and totals are
 * all pounds, and only [onApply] converts, handing back the canonical kg total. (Before F9 the
 * solve ran in kg for everyone, so a pounds user saw a 44.09 lb bar and 22.05 lb plates.)
 *
 * §5.1.5's Canvas bar-loading diagram is deliberately not built (M17 keeps the text list + totals;
 * the diagram is pure decoration over the same numbers and can land later without data changes).
 *
 * M20d: rebuilt on FlexibleBottomSheet, hoisted to screen level ([WorkoutLoggerScreen]'s
 * `plateTarget`) so there is exactly one sheet instance shared by every row, in both the regular
 * and circuit tables — replacing the old per-row `ModalBottomSheet`. Non-modal (`isModal = false`,
 * the milestone's actual goal): the set list stays interactive behind the sheet, verified
 * on-device — completing a set behind the open sheet registered correctly, and the sheet reopened
 * pre-filled from the newly-applied weight.
 *
 * A suspected IME rendering bug ("field focuses, `mInputShown=true`, but no on-screen keyboard
 * ever renders") led to a brief detour to `isModal = true` mid-milestone -- a follow-up control
 * test then reproduced the *identical* symptom on the app's own, unrelated, pre-existing
 * `ExercisePickerSheet` (a plain Material3 `ModalBottomSheet`, `feature/exercises/
 * ExercisePickerSheet.kt`) under the same host-load-starved emulator session (`uptime` load
 * average 12-14 on an 8-core host), and force-stopping/restarting the Gboard IME process did not
 * clear it either. That rules out FlexibleBottomSheet specifically -- reverted back to non-modal.
 *
 * Non-modal cuts both ways: because the set list behind the sheet stays tappable, a user can
 * retarget it to a *different* set's calculator icon without dismissing the open one first. The
 * sheet stays mounted across that (`WorkoutLoggerScreen`'s `plateTarget?.let { ... }` recomposes
 * the same call site rather than tearing it down), so [targetText] is keyed on [setId] to reseed
 * from the new set's weight — found retargeting without dismissing during the M20a-h code audit
 * (2026-09-08); before this it kept the previous set's prefilled number and "Use X" would have
 * applied it to the newly-targeted set. [selectedBar] is not keyed on the set on purpose: which bar
 * you're loading plates onto is a per-session choice, not a per-set one, so it should carry over.
 * Both are keyed on the unit (F9), because a unit change mid-session swaps the equipment set and
 * makes a typed number mean something else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlateCalculatorSheet(
    setId: String,
    initialWeightKg: Double?,
    weightUnit: WeightUnit,
    equipment: PlateEquipment,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // F9: the bars and plates for the user's unit, in that unit's own numbers.
    val plateSet = equipment.setFor(weightUnit)
    // Defensive: the equipment editor never persists zero bars, but an empty list here would make
    // every solve meaningless — fall back to the unit's standard bar (20 kg or 45 lb).
    val bars = plateSet.bars.ifEmpty { listOf(standardBar(weightUnit)) }
    var selectedBar by remember(weightUnit) { mutableStateOf(bars.first()) }
    // A bar removed in Settings while the sheet is open falls back to the first one.
    val activeBar = selectedBar.takeIf { it in bars } ?: bars.first()
    var targetText by remember(setId, weightUnit) {
        mutableStateOf(initialWeightKg?.let { formatWeightNumber(WeightDisplay.toDisplay(it, weightUnit)) }.orEmpty())
    }

    FlexibleBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlexibleBottomSheetState(
            isModal = false,
            skipSlightlyExpanded = false,
            flexibleSheetSize = FlexibleSheetSize(fullyExpanded = 0.85f, intermediatelyExpanded = 0.45f, slightlyExpanded = 0.15f),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md).padding(bottom = Spacing.lg)) {
            Text(
                stringResource(R.string.workout_plate_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text(stringResource(R.string.workout_plate_target_label)) },
                suffix = { Text(weightUnit.shortLabel()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            if (bars.size > 1) {
                Text(
                    stringResource(R.string.workout_plate_bar_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                LazyRow(modifier = Modifier.padding(top = Spacing.xxs)) {
                    // Index in the key: a hand-edited backup could carry the same bar twice.
                    itemsIndexed(items = bars, key = { index, bar -> "${index}_$bar" }) { _, bar ->
                        FilterChip(
                            selected = activeBar == bar,
                            onClick = { selectedBar = bar },
                            label = { Text(formatWeight(bar, weightUnit)) },
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                    }
                }
            }

            // The typed target is already in the solve's unit, so it is solved as typed.
            val target = parseDecimalInput(targetText)
            if (target == null || target <= 0.0) {
                Text(
                    stringResource(R.string.workout_plate_enter_target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            } else {
                // Live solve: recomputed only when target, bar, or equipment actually changes.
                val result = remember(target, activeBar, plateSet.plates) {
                    PlateCalculator.solve(target, activeBar, plateSet.plates)
                }
                PlateSolveResult(
                    result = result,
                    weightUnit = weightUnit,
                    platesEmpty = plateSet.plates.isEmpty(),
                    onApply = { achieved -> onApply(WeightDisplay.toKg(achieved, weightUnit)) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** [result] and the value handed to [onApply] are in [weightUnit], not kg. */
@Composable
private fun PlateSolveResult(
    result: PlateCalculator.Result,
    weightUnit: WeightUnit,
    platesEmpty: Boolean,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    if (result.belowBar) {
        // §5.1.5 edge case: "Target < bar weight → 'Bar alone weighs Y'."
        Text(
            stringResource(R.string.workout_plate_bar_alone, formatWeight(result.achieved, weightUnit)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.md),
        )
        return
    }

    val perSideLabel = if (result.perSide.isEmpty()) {
        "—"
    } else {
        result.perSide.joinToString(" · ") { formatWeightNumber(it) }
    }
    Text(
        stringResource(R.string.workout_plate_per_side, perSideLabel),
        style = LogEzMono.dataMedium,
        modifier = Modifier.padding(top = Spacing.md),
    )
    Text(
        stringResource(R.string.workout_plate_total, formatWeight(result.achieved, weightUnit)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xxs),
    )

    if (!result.exact) {
        if (platesEmpty) {
            // §5.1.5 edge case: no plates at all — say why the solve can only offer the bar.
            Text(
                stringResource(R.string.workout_plate_no_plates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        // §5.1.5 fallback: closest banner + "Use X" writing the achieved total into the cell (the
        // caller converts it to kg).
        Text(
            stringResource(R.string.workout_plate_closest, formatWeight(result.achieved, weightUnit)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Button(onClick = { onApply(result.achieved); onDismiss() }) {
                Text(stringResource(R.string.workout_plate_use, formatWeight(result.achieved, weightUnit)))
            }
        }
    }
}

private fun WeightUnit.shortLabel(): String = if (this == WeightUnit.KG) "kg" else "lb"

// M18: the sheet's kg ↔ display conversions delegate to WeightDisplay — the same single boundary
// the weight cells use — so the two surfaces can never disagree about what "100 lb" means. F9:
// the solve itself is in the display unit, so the only conversions left are the prefilled target
// (kg → unit) and the applied total (unit → kg, in the sheet's onApply wrapper above).

/** A weight already in [unit] (a bar, plate or solved total), with its unit symbol. */
private fun formatWeight(weight: Double, unit: WeightUnit): String =
    "${formatWeightNumber(weight)} ${unit.shortLabel()}"

/**
 * Whole numbers bare, otherwise up to two decimals with trailing zeros trimmed ("1.25", "2.5").
 * Locale.ROOT keeps the decimal separator a dot on comma-decimal locales — the pre-filled target
 * text must round-trip through [String.toDoubleOrNull], which only parses dots.
 */
private fun formatWeightNumber(value: Double): String = WeightDisplay.format(value)
