package com.enil.logez.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.RpeScale

/**
 * P-211 (Owner, 2026-09-30): the words for an effort value, per [EffortScale]. [RpeScale] does
 * the numbers ("8.5", "1–2", "4+"); this file maps a scale or a value to its strings.xml copy, so
 * the logger, both pickers, the explainer, the legend and History all say one effort one way.
 */

/** "RPE" / "RIR": column headers, the Settings row value, the legend key, PREVIOUS line 2. */
@StringRes
fun EffortScale.labelRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_scale_rpe
    EffortScale.RIR -> R.string.effort_scale_rir
}

/** The legend's plain-words line: "How hard it felt · 10 = no reps left". */
@StringRes
fun EffortScale.legendRes(): Int = when (this) {
    EffortScale.RPE -> R.string.legend_effort_rpe
    EffortScale.RIR -> R.string.legend_effort_rir
}

/** "Log Set RPE" / "Log Set RIR". */
@StringRes
fun EffortScale.pickerTitleRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_picker_title_rpe
    EffortScale.RIR -> R.string.effort_picker_title_rir
}

/** The picker's caption while nothing is selected: "How many more reps could you have done?". */
@StringRes
fun EffortScale.pickerAskRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_picker_ask_rpe
    EffortScale.RIR -> R.string.effort_picker_ask_rir
}

/** The picker's "What's RIR?" link, which opens [EffortExplainerSheet]. */
@StringRes
fun EffortScale.pickerLinkRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_picker_link_rpe
    EffortScale.RIR -> R.string.effort_picker_link_rir
}

@StringRes
internal fun EffortScale.explainerTitleRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_explainer_title_rpe
    EffortScale.RIR -> R.string.effort_explainer_title_rir
}

@StringRes
internal fun EffortScale.explainerBodyRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_explainer_body_rpe
    EffortScale.RIR -> R.string.effort_explainer_body_rir
}

/** "Prefer RIR? …" in RPE mode and the other way round: it names the scale the user isn't on. */
@StringRes
internal fun EffortScale.explainerSwitchHintRes(): Int = when (this) {
    EffortScale.RPE -> R.string.effort_explainer_switch_to_rir
    EffortScale.RIR -> R.string.effort_explainer_switch_to_rpe
}

/**
 * The plain words for [rpe] (small fix 10j), the same on both scales: RPE 9 and RIR 1 both read
 * "Could have done 1 more rep". Defined for any stored value via [RpeScale.captionStep].
 */
@StringRes
fun effortCaptionRes(rpe: Double): Int = when (RpeScale.captionStep(rpe)) {
    10.0 -> R.string.effort_caption_rpe_10
    9.5 -> R.string.effort_caption_rpe_9_5
    9.0 -> R.string.effort_caption_rpe_9
    8.5 -> R.string.effort_caption_rpe_8_5
    8.0 -> R.string.effort_caption_rpe_8
    7.5 -> R.string.effort_caption_rpe_7_5
    7.0 -> R.string.effort_caption_rpe_7
    6.5 -> R.string.effort_caption_rpe_6_5
    else -> R.string.effort_caption_rpe_6
}

/** "RIR 1–2" / "RPE 8.5": PREVIOUS line 2 and screen-reader text. Always formatted when drawn, from the raw RPE. */
@Composable
fun effortValueLine(rpe: Double, scale: EffortScale): String =
    stringResource(R.string.effort_value_line, stringResource(scale.labelRes()), RpeScale.format(rpe, scale))

/**
 * The picker's caption: "RIR 1 — Could have done 1 more rep" for a pending value, or the
 * question ("How hard was it? 10 = no reps left.") while nothing is selected.
 */
@Composable
fun effortPickerCaption(rpe: Double?, scale: EffortScale): String =
    if (rpe == null) {
        stringResource(scale.pickerAskRes())
    } else {
        stringResource(R.string.effort_picker_caption, stringResource(scale.labelRes()), RpeScale.format(rpe, scale), stringResource(effortCaptionRes(rpe)))
    }
