package com.enil.logez.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.ChoiceSegmentRow
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.settings.SettingsSectionHeader
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Stable tags for first-run setup's buttons, exposed as resource ids (`testTagsAsResourceId` on the
 * app root) so a Play pre-launch Robo script and adb UI dumps can find them in any language.
 * [RESTORE] is the Restore button (O1e); [CONNECT] is reserved for Health Connect's Connect (O1f),
 * so the names never change once scripts use them.
 */
object FirstRunTestTags {
    const val CONTINUE = "firstrun_continue"
    const val RESTORE = "firstrun_restore"
    const val CONNECT = "firstrun_connect"
}

/**
 * "Before you start": the one screen a fresh install shows (first-run plan, O1c). It asks only for
 * the units and the first day of the week, all preselected, so Continue works without touching
 * anything. Nothing here requests a permission or moves, except the progress bar during a restore.
 *
 * It draws above the app, outside the Scaffold, so it pads itself for the system bars. The column
 * scrolls; only the bottom area is pinned: Continue, and Restore from a backup under it (O1e). The
 * Health Connect section (O1f) goes between the week start and the backup note.
 *
 * The choices live in [choicesState] (saveable enums, so they survive rotation and process death);
 * the preselected values are only the starting point. There is no BackHandler except while a
 * restore runs or a result is being written: otherwise system Back leaves the app, nothing is
 * written, and setup shows again at the next launch.
 *
 * Restore (O1e): its status and failures sit at the top of the column, under the intro, and the
 * column jumps to the top without animation when one appears, so the pinned area stays two buttons
 * tall even at 200% font in landscape.
 *
 * @param regionNoteVisible whether the preselected values are all the region's suggestion. It
 *   describes the preselection, so it stays put when the user changes a value.
 * @param working Continue or the end of a restore is being written: the choices, Continue and
 *   Restore are disabled.
 */
@Composable
fun FirstRunSetupScreen(
    preselected: SetupChoices,
    regionNoteVisible: Boolean,
    working: Boolean,
    onContinue: (SetupChoices) -> Unit,
    modifier: Modifier = Modifier,
    choicesState: SetupChoicesState = rememberSetupChoicesState(preselected),
    restore: SetupRestoreBinding = SetupRestoreBinding.Inert,
) {
    val choices = choicesState.choices
    val paneTitle = stringResource(R.string.first_run_pane_title)
    val restoreUi = restore.ui
    val buttonsEnabled = !working && !restoreUi.busy
    val scrollState = rememberScrollState()

    // Back is ignored while a restore is under way, as on Settings > Export & backup, and while the
    // gate writes its result or Continue's (plan table: `BackHandler(enabled = working)`). Before
    // API 31, Back finishes the root activity, which would cancel those writes partway: the flag,
    // or a settings-less backup's choices, would never be written. Enabled only while that lasts;
    // otherwise Back leaves the app. The confirm dialog handles its own Back (Cancel).
    BackHandler(enabled = restoreUi.busy || working) { }

    // A status or failure appears at the top of the column: show it, without animation.
    LaunchedEffect(restoreUi.status) {
        if (restoreUi.status != null) scrollState.scrollTo(0)
    }

    restoreUi.confirm?.let { staged ->
        SetupRestoreConfirmDialog(staged = staged, onConfirm = restore.onConfirm, onCancel = restore.onCancel)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .semantics { this.paneTitle = paneTitle },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Height first, then the cap, then the width: fillMaxSize before widthIn would fix the
            // width to the whole screen, and widthIn can't shrink it (LogEzApp uses the same order).
            Column(modifier = Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = Spacing.lg),
                ) {
                    SetupContent(
                        choices = choices,
                        // Tied to the preselection only (plan table), not to later taps: hiding it on a
                        // change moved everything under the pills, and at the bottom of the scroll the
                        // tapped row jumped.
                        regionNoteVisible = regionNoteVisible,
                        restoreStatus = restoreUi.status,
                        enabled = !working,
                        onWeightUnit = { choicesState.choices = choicesState.choices.copy(weightUnit = it) },
                        onDistanceUnit = { choicesState.choices = choicesState.choices.copy(distanceUnit = it) },
                        onFirstDayOfWeek = { choicesState.choices = choicesState.choices.copy(firstDayOfWeek = it) },
                    )
                }
                // Built like Start Empty Workout's pinned bar: page black, one full-width pill, then
                // Restore as a text button. Continue is the only solid green on the screen.
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { onContinue(choices) },
                            enabled = buttonsEnabled,
                            shape = RoundedCornerShape(Radius.pill),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md)
                                .testTag(FirstRunTestTags.CONTINUE),
                        ) {
                            Text(stringResource(R.string.first_run_continue))
                        }
                        TextButton(
                            onClick = restore.onRestore,
                            enabled = buttonsEnabled,
                            modifier = Modifier
                                .heightIn(min = MIN_TOUCH_HEIGHT)
                                .testTag(FirstRunTestTags.RESTORE),
                        ) {
                            Text(stringResource(R.string.data_restore))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupContent(
    choices: SetupChoices,
    regionNoteVisible: Boolean,
    restoreStatus: SetupRestoreStatus?,
    enabled: Boolean,
    onWeightUnit: (WeightUnit) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit,
    onFirstDayOfWeek: (DayOfWeek) -> Unit,
) {
    // Section headers carry their own 16 dp side padding (SettingsSectionHeader); everything else
    // takes the same inset here, so the whole column lines up on one edge.
    val inset = Modifier.padding(horizontal = Spacing.md)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = currentLocale()

    // The title sits in a 64 dp row, where the app's top-bar titles sit, and scrolls with the rest.
    Box(modifier = inset.heightIn(min = TITLE_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
        ScreenTitle(
            stringResource(R.string.first_run_title),
            modifier = Modifier.semantics { heading() },
        )
    }
    Text(
        stringResource(R.string.first_run_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = inset.padding(top = Spacing.xs),
    )
    // Reading order (plan, Accessibility): title, intro, restore status, units.
    restoreStatus?.let { SetupRestoreStatusBlock(it, inset) }

    SettingsSectionHeader(
        stringResource(R.string.first_run_section_units),
        modifier = Modifier.semantics { heading() },
    )
    RowLabel(stringResource(R.string.settings_weight_unit), inset)
    ChoiceSegmentRow(
        options = WeightUnit.entries,
        selected = choices.weightUnit,
        onSelect = onWeightUnit,
        label = { stringResource(it.symbolRes()) },
        description = { stringResource(it.nameRes()) },
        enabled = enabled,
        modifier = inset,
    )
    RowLabel(stringResource(R.string.settings_distance_unit), inset.padding(top = Spacing.xs))
    ChoiceSegmentRow(
        options = DistanceUnit.entries,
        selected = choices.distanceUnit,
        onSelect = onDistanceUnit,
        label = { stringResource(it.symbolRes()) },
        description = { stringResource(it.nameRes()) },
        enabled = enabled,
        modifier = inset,
    )
    Note(stringResource(R.string.first_run_length_note), secondary, inset)

    SettingsSectionHeader(
        stringResource(R.string.calendar_first_day_of_week),
        modifier = Modifier.semantics { heading() },
    )
    ChoiceSegmentRow(
        options = SetupChoices.OFFERED_FIRST_DAYS,
        selected = choices.firstDayOfWeek,
        onSelect = onFirstDayOfWeek,
        label = { it.getDisplayName(TextStyle.FULL, locale) },
        enabled = enabled,
        modifier = inset,
    )
    if (regionNoteVisible) {
        Note(stringResource(R.string.first_run_region_note), secondary, inset)
    }
    Note(stringResource(R.string.first_run_settings_note), secondary, inset)

    Text(
        stringResource(R.string.first_run_backup_note),
        style = MaterialTheme.typography.bodyMedium,
        color = secondary,
        modifier = inset.padding(top = Spacing.lg),
    )
}

@Composable
private fun RowLabel(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun Note(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier.padding(top = Spacing.xxs),
    )
}

private fun WeightUnit.symbolRes(): Int = when (this) {
    WeightUnit.KG -> R.string.unit_symbol_kg
    WeightUnit.LB -> R.string.unit_symbol_lb
}

private fun WeightUnit.nameRes(): Int = when (this) {
    WeightUnit.KG -> R.string.settings_weight_unit_kg
    WeightUnit.LB -> R.string.settings_weight_unit_lb
}

private fun DistanceUnit.symbolRes(): Int = when (this) {
    DistanceUnit.KM -> R.string.unit_symbol_km
    DistanceUnit.MILES -> R.string.unit_symbol_mi
}

private fun DistanceUnit.nameRes(): Int = when (this) {
    DistanceUnit.KM -> R.string.settings_distance_unit_km
    DistanceUnit.MILES -> R.string.settings_distance_unit_miles
}

/** The same cap as the app's content column (LogEzApp), so a tablet shows setup centred. */
private val MAX_CONTENT_WIDTH = 720.dp
private val TITLE_ROW_HEIGHT = 64.dp
private val MIN_TOUCH_HEIGHT = 48.dp
