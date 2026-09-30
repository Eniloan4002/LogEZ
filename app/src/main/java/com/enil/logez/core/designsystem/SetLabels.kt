package com.enil.logez.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.domain.model.SetDisplayLabel
import com.enil.logez.core.domain.model.SetType

/**
 * P-211 decision 9 (Owner, 2026-09-30): the words and the read-only badge for a set's SET cell,
 * from [com.enil.logez.core.domain.model.SetNumbering]. The logger's tappable, field-height
 * `SetBadge` stays in feature/workout; this is the History-sized one.
 */

/** A set's own name for a screen reader: "Set 1", "Warm-up set", "Failure set", "Dropset". */
@Composable
fun setLabelA11y(label: SetDisplayLabel): String = when (label.setType) {
    SetType.NORMAL -> stringResource(R.string.set_label_numbered_a11y, label.number ?: 0)
    SetType.WARMUP -> stringResource(R.string.set_label_warmup_a11y)
    SetType.FAILURE -> stringResource(R.string.set_label_failure_a11y)
    SetType.DROPSET -> stringResource(R.string.set_label_dropset_a11y)
}

/**
 * The template for one field of a set, for a screen reader: `workout_set_field_a11y`
 * ("Set %1$d, %2$s") for a numbered set, "Warm-up set, %1$s" and so on for a lettered one.
 */
@StringRes
fun setFieldA11yRes(type: SetType): Int = when (type) {
    SetType.NORMAL -> R.string.workout_set_field_a11y
    SetType.WARMUP -> R.string.set_field_a11y_warmup
    SetType.FAILURE -> R.string.set_field_a11y_failure
    SetType.DROPSET -> R.string.set_field_a11y_dropset
}

/** One field of a set for a screen reader, e.g. [field] "reps": "Set 2, reps" / "Warm-up set, reps". */
@Composable
fun setFieldA11y(label: SetDisplayLabel, field: String): String =
    if (label.setType == SetType.NORMAL) {
        stringResource(setFieldA11yRes(label.setType), label.number ?: 0, field)
    } else {
        stringResource(setFieldA11yRes(label.setType), field)
    }

/**
 * The set-type colour: W / F / D each carry one, a normal set none (null). The badge fill is this
 * at 15%; the text is this too, except the warm-up "W", which uses [Warning300] for contrast
 * (small fix 10b: History now matches the logger).
 */
internal fun setTypeColor(type: SetType): Color? = when (type) {
    SetType.NORMAL -> null
    SetType.WARMUP -> Warning500
    SetType.FAILURE -> Danger500
    SetType.DROPSET -> SupersetPalette[4]
}

/**
 * The read-only badge History draws in its SET column: a 28dp rounded square holding "1" (no fill)
 * or "W" / "F" / "D" (the type's colour at 15%). [compact] is the legend's 20dp version. Both
 * are minimums, so the box grows with the letter at large font sizes.
 * Decorative for a screen reader: the row or legend item around it says what it is.
 */
@Composable
fun HistorySetBadge(label: SetDisplayLabel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = setTypeColor(label.setType)
    val textColor = when (label.setType) {
        SetType.NORMAL -> MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> Warning300
        else -> color ?: MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(if (compact) 5.dp else 6.dp),
        color = color?.copy(alpha = SET_TYPE_TINT_ALPHA) ?: Color.Transparent,
        modifier = modifier,
    ) {
        // A minimum, not a fixed size: at 200% font the letter outgrows 20dp and the box grows with it.
        val side = if (compact) 20.dp else 28.dp
        Box(modifier = Modifier.defaultMinSize(minWidth = side, minHeight = side), contentAlignment = Alignment.Center) {
            Text(
                label.text,
                color = textColor,
                style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The W / F / D badge tint, the same 15% the logger's badges and the old detail rows use. */
private const val SET_TYPE_TINT_ALPHA = 0.15f
