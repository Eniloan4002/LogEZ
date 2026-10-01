package com.enil.logez.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One destination tile. The grid is list-driven, so a later destination is one more entry. */
internal data class DestinationSpec(
    val key: String,
    val icon: ImageVector,
    val label: String,
    /** A quiet second line (Measurements' latest weight), or null. */
    val supporting: AnnotatedString?,
    val onClick: () -> Unit,
)

private val DestSidePadding = 12.dp + 8.dp
private val DestIconAndGap = 24.dp + 8.dp

/**
 * The six destinations as filled, clickable cards (today's lime icon and label), two to a row while
 * every label word fits beside its icon, otherwise one per row. Same measured rule as the scorecards.
 */
@Composable
internal fun ProfileDestinationGrid(
    destinations: List<DestinationSpec>,
    styles: ProfileStyles,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    MeasuredTwoColumnGrid(
        items = destinations,
        itemKey = { it.key },
        gap = Spacing.xs,
        modifier = modifier,
        fitsInColumn = { dest, columnPx, measurer ->
            val inner = columnPx - with(density) { (DestSidePadding + DestIconAndGap).toPx() }
            measurer.wordsFit(AnnotatedString(dest.label), styles.destLabel, inner) &&
                (dest.supporting == null || measurer.wordsFit(dest.supporting, styles.tileSupporting, inner))
        },
    ) { dest, _, cellModifier ->
        DestinationTile(dest, styles, cellModifier)
    }
}

@Composable
private fun DestinationTile(spec: DestinationSpec, styles: ProfileStyles, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    LogEzCard(onClick = spec.onClick, modifier = modifier.cardSemantics(loading = false)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, top = Spacing.xs, end = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Every destination icon is lime (Owner, 2026-09-30).
            Icon(spec.icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.label, style = styles.destLabel, color = scheme.onSurface)
                if (spec.supporting != null) {
                    Text(spec.supporting, style = styles.tileSupporting, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The six destinations in today's order: Statistics, Achievements, Calendar, Measurements, Exercises, Settings. */
@Composable
internal fun rememberDestinations(
    latestWeight: LatestWeight?,
    weightUnit: WeightUnit,
    styles: ProfileStyles,
    onStatistics: () -> Unit,
    onAchievements: () -> Unit,
    onCalendar: () -> Unit,
    onMeasurements: () -> Unit,
    onExercises: () -> Unit,
    onSettings: () -> Unit,
): List<DestinationSpec> {
    val locale = currentLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val latest: AnnotatedString? = latestWeight?.let { weight ->
        val formatted = AnalyticsFormatters.volume(weight.kg, weightUnit)
        val number = formatted.substringBefore(' ')
        withEmphasis(stringResource(R.string.profile_measurements_latest, formatted, dateFormat.format(weight.date)), number, styles.monoQuiet)
    }
    return listOf(
        DestinationSpec("statistics", Icons.Outlined.BarChart, stringResource(R.string.profile_nav_statistics), null, onStatistics),
        DestinationSpec("achievements", Icons.Outlined.EmojiEvents, stringResource(R.string.profile_nav_achievements), null, onAchievements),
        DestinationSpec("calendar", Icons.Outlined.CalendarMonth, stringResource(R.string.profile_calendar_row), null, onCalendar),
        DestinationSpec("measurements", Icons.Outlined.Straighten, stringResource(R.string.profile_measurements_row), latest, onMeasurements),
        DestinationSpec("exercises", LogEzIcons.Workout, stringResource(R.string.profile_nav_exercises), null, onExercises),
        DestinationSpec("settings", Icons.Outlined.Settings, stringResource(R.string.settings_title), null, onSettings),
    )
}

internal fun LocalDate.formatShort(locale: java.util.Locale, includeYear: Boolean): String =
    DateTimeFormatter.ofPattern(if (includeYear) "d MMM yyyy" else "d MMM", locale).format(this)
