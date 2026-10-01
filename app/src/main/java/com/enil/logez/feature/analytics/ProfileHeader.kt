package com.enil.logez.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SectionLabel
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.WeightUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/*
 * The top of the Profile tab (docs/mockups/profile-2026-10-01): the This week card and the scorecards
 * grid. The grid is list-driven ([ScoreTileSpec] list in, [MeasuredTwoColumnGrid] out), so another
 * tile is one more list entry. The destination grid (ProfileDestinations.kt) uses the same grid.
 */

/** The text styles the header and the grids share, built once so the fit checks measure what is drawn. */
internal class ProfileStyles(
    val heroNumber: TextStyle,
    val heroOf: TextStyle,
    val meta: TextStyle,
    val tileValue: TextStyle,
    val tileValueSuffix: SpanStyle,
    val tileLabel: TextStyle,
    val tileSupporting: TextStyle,
    val destLabel: TextStyle,
    /** Mono Medium in the surrounding text colour: the figure inside a quiet grey line ('Longest 13'). */
    val monoQuiet: SpanStyle,
    /** Mono Medium in the bright text colour: the 'So far' figures, the one place a figure outranks its line. */
    val monoEmphasis: SpanStyle,
)

@Composable
internal fun rememberProfileStyles(): ProfileStyles {
    val typography = MaterialTheme.typography
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(typography, onSurface, muted) {
        ProfileStyles(
            heroNumber = LogEzMono.dataLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
            heroOf = typography.bodyLarge,
            meta = LogEzMono.dataSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            tileValue = LogEzMono.dataLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
            tileValueSuffix = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = muted),
            tileLabel = typography.bodyMedium,
            tileSupporting = typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            destLabel = typography.labelLarge,
            monoQuiet = SpanStyle(fontFamily = LogEzMono.dataMedium.fontFamily, fontWeight = FontWeight.Medium),
            monoEmphasis = SpanStyle(fontFamily = LogEzMono.dataMedium.fontFamily, fontWeight = FontWeight.Medium, color = onSurface),
        )
    }
}

/** [text] with the first occurrence of [number] set in [style] (the mono figure inside a sentence). */
internal fun withEmphasis(text: String, number: String, style: SpanStyle): AnnotatedString {
    val start = text.indexOf(number)
    return if (start < 0) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            addStyle(style, start, start + number.length)
        }
    }
}

/**
 * Placeholder look for a value that has not loaded: the real text keeps its place in the layout
 * (so the reserved space follows the font size) but is drawn transparent over a [surfaceVariant]
 * block, at least [minWidth] wide (the mockup's placeholder widths). Static, no shimmer (the app has
 * no new motion).
 */
@Composable
internal fun Modifier.loadingBlock(loading: Boolean, minWidth: Dp = 0.dp): Modifier {
    if (!loading) return this
    val color = MaterialTheme.colorScheme.surfaceVariant
    val radius = with(LocalDensity.current) { 6.dp.toPx() }
    return widthIn(min = minWidth).drawBehind { drawRoundRect(color, cornerRadius = CornerRadius(radius)) }
}

private fun Color.orTransparent(loading: Boolean): Color = if (loading) Color.Transparent else this

/** [text] with every span's colour made transparent, so a placeholder's styled figures hide with the rest. */
internal fun AnnotatedString.hiddenIf(loading: Boolean): AnnotatedString {
    if (!loading) return this
    return AnnotatedString(
        text,
        spanStyles = spanStyles.map { it.copy(item = it.item.copy(color = Color.Transparent)) },
        paragraphStyles = paragraphStyles,
    )
}

/**
 * Semantics for a tappable card: one button node when loaded (TalkBack: "double tap to activate"), none
 * while loading (the placeholder has nothing to read and nothing to announce).
 */
internal fun Modifier.cardSemantics(loading: Boolean): Modifier =
    if (loading) clearAndSetSemantics { } else semantics { role = Role.Button }

// ---------------------------------------------------------------------------------------------
// This week
// ---------------------------------------------------------------------------------------------

/**
 * "3 of 4 days", the Monday-to-Sunday strip, records (gold) and the volume and sets so far. The
 * whole card opens the Calendar. [week] is null only while loading; placeholders keep the layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThisWeekCard(
    week: ProfileWeek?,
    hasWorkouts: Boolean,
    weightUnit: WeightUnit,
    styles: ProfileStyles,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = week ?: PlaceholderWeek
    val locale = currentLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val range = stringResource(
        R.string.profile_week_range,
        dateFormat.format(shown.start),
        dateFormat.format(shown.end),
    )

    LogEzCard(onClick = onClick, modifier = modifier.fillMaxWidth().cardSemantics(loading)) {
        Box(modifier = if (loading) Modifier.clearAndSetSemantics { } else Modifier) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.profile_week_title))
                    Text(
                        range,
                        style = styles.meta,
                        color = muted.orTransparent(loading),
                        modifier = Modifier.loadingBlock(loading),
                    )
                }

                // The count and the records chip share one text baseline (the mockup's `align-items:
                // baseline`); the chip drops under the count only when the two do not fit one line.
                HeroRow(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                    Text(
                        shown.activeDays.toString(),
                        style = styles.heroNumber,
                        color = onSurface.orTransparent(loading),
                        modifier = Modifier.layoutId(HeroSlot.Lead).loadingBlock(loading, 48.dp),
                    )
                    Text(
                        heroOfText(shown, loading),
                        style = styles.heroOf,
                        color = onSurface.orTransparent(loading),
                        modifier = Modifier.layoutId(HeroSlot.Lead).loadingBlock(loading, 80.dp),
                    )
                    if (!loading && shown.recordsThisWeek > 0) {
                        RecordsLine(shown.recordsThisWeek, Modifier.layoutId(HeroSlot.Trail))
                    }
                }

                WeekStrip(shown, loading, locale)

                when {
                    !loading && !hasWorkouts -> WeekNote(stringResource(R.string.profile_week_new_user))
                    !loading && shown.setsSoFar == 0 && shown.activeDays == 0 -> WeekNote(stringResource(R.string.profile_week_nothing_yet))
                    // A week of walks and runs has workouts but no sets; there is nothing to total.
                    !loading && shown.setsSoFar == 0 -> Unit
                    else -> SoFarRow(shown, weightUnit, styles, loading)
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = if (loading) Color.Transparent else muted,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.md, end = Spacing.sm).size(20.dp),
            )
        }
    }
}

/** A week that has not loaded: seven upcoming days, never drawn as data. */
private val PlaceholderWeek = ProfileWeek(
    start = LocalDate.of(2000, 1, 3),
    activeDays = 0,
    targetDays = 4,
    days = (0L..6L).map { WeekDay(LocalDate.of(2000, 1, 3).plusDays(it), WeekDayMark.UPCOMING) },
    todayIndex = 0,
    volumeKgSoFar = 0.0,
    setsSoFar = 1,
    recordsThisWeek = 0,
)

/**
 * "5 days · target 4 met" past the target, "of 4 days · target met" on it, "of 4 days" short of it.
 * The tail is lime (progress). While [loading] it is plain text: the placeholder is drawn invisible.
 */
@Composable
private fun heroOfText(week: ProfileWeek, loading: Boolean): AnnotatedString {
    val target = week.targetDays
    val active = week.activeDays
    if (loading || target <= 0 || active < target) {
        return AnnotatedString(pluralStringResource(R.plurals.profile_week_target, target, target))
    }
    val tail = if (active == target) stringResource(R.string.profile_week_target_met) else pluralStringResource(R.plurals.profile_week_over_target, target, target)
    val full = if (active == target) {
        pluralStringResource(R.plurals.profile_week_target_reached, target, target, tail)
    } else {
        pluralStringResource(R.plurals.profile_week_over, active, tail)
    }
    return withEmphasis(full, tail, SpanStyle(color = MaterialTheme.colorScheme.primary))
}

private enum class HeroSlot { Lead, Trail }

/**
 * The This week hero row: the [HeroSlot.Lead] children in a row from the start (8 dp apart), the one
 * [HeroSlot.Trail] child (optional) at the end, all on a shared first baseline. When the trail child
 * does not fit beside the leads with a 16 dp gap it wraps under them, start-aligned.
 */
@Composable
private fun HeroRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leads = measurables.filter { it.layoutId == HeroSlot.Lead }.map { it.measure(loose) }
        val trail = measurables.firstOrNull { it.layoutId == HeroSlot.Trail }?.measure(loose)
        val leadGap = Spacing.xs.roundToPx()
        val trailGap = Spacing.md.roundToPx()
        val width = constraints.maxWidth

        fun baselineOf(p: Placeable): Int {
            val line = p[FirstBaseline]
            return if (line == AlignmentLine.Unspecified) p.height else line
        }

        val leadBase = leads.maxOfOrNull { baselineOf(it) } ?: 0
        val leadWidth = leads.sumOf { it.width } + leadGap * (leads.size - 1).coerceAtLeast(0)
        val sameLine = trail != null && leadWidth + trailGap + trail.width <= width
        val trailBase = trail?.let { baselineOf(it) } ?: 0
        // On one line everything hangs from the lower of the two baselines; wrapped, the leads keep their own.
        val lineBase = if (sameLine) maxOf(leadBase, trailBase) else leadBase
        val leadBottom = leads.maxOfOrNull { lineBase - baselineOf(it) + it.height } ?: 0
        val height = when {
            trail == null -> leadBottom
            sameLine -> maxOf(leadBottom, lineBase - trailBase + trail.height)
            else -> leadBottom + Spacing.xxs.roundToPx() + trail.height
        }
        layout(width, height) {
            var x = 0
            leads.forEach { p ->
                p.placeRelative(x, lineBase - baselineOf(p))
                x += p.width + leadGap
            }
            if (trail != null) {
                if (sameLine) {
                    trail.placeRelative(width - trail.width, lineBase - trailBase)
                } else {
                    trail.placeRelative(0, leadBottom + Spacing.xxs.roundToPx())
                }
            }
        }
    }
}

/** History's records chip pattern: gold trophy and gold text, no fill. Gold marks records only. */
@Composable
private fun RecordsLine(count: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.profile_week_records, count, count.toString())
    val emphasis = SpanStyle(fontFamily = LogEzMono.dataMedium.fontFamily, fontWeight = FontWeight.SemiBold)
    // The trophy is drawn behind the text rather than laid out beside it, so the whole chip is one Text
    // and exposes the text baseline the row aligns to.
    val trophy = rememberVectorPainter(Icons.Outlined.EmojiEvents)
    val iconSize = 20.dp
    val iconGap = 24.dp
    Text(
        withEmphasis(label, count.toString(), emphasis),
        style = MaterialTheme.typography.labelLarge,
        color = Gold500,
        modifier = modifier
            .drawBehind {
                val side = iconSize.toPx()
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - side else 0f
                translate(left = left, top = (size.height - side) / 2f) {
                    with(trophy) { draw(Size(side, side), colorFilter = ColorFilter.tint(Gold500)) }
                }
            }
            .padding(start = iconGap),
    )
}

@Composable
private fun WeekStrip(week: ProfileWeek, loading: Boolean, locale: Locale) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val letterStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val pill = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        if (loading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Spacing.xxs)
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(pill, RoundedCornerShape(16.dp)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            week.days.forEachIndexed { index, day ->
                val isToday = index == week.todayIndex
                val name = day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale)
                val spoken = when (day.mark) {
                    WeekDayMark.TRAINED -> stringResource(R.string.profile_day_cd_trained, name)
                    WeekDayMark.TODAY -> stringResource(R.string.profile_day_cd_today, name)
                    WeekDayMark.MISSED -> stringResource(R.string.profile_day_cd_missed, name)
                    WeekDayMark.UPCOMING -> stringResource(R.string.profile_day_cd_upcoming, name)
                }
                Column(
                    modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, locale),
                        style = letterStyle,
                        fontWeight = if (isToday && !loading) FontWeight.SemiBold else FontWeight.Medium,
                        // The letters follow the user's first day of week, which is unknown until the load
                        // lands: hidden while loading rather than drawn in a guessed order.
                        color = if (loading) Color.Transparent else if (isToday) primary else muted,
                    )
                    DayDot(day.mark, hidden = loading)
                }
            }
        }
    }
}

@Composable
private fun DayDot(mark: WeekDayMark, hidden: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val base = Modifier.size(32.dp).clip(CircleShape)
    when {
        hidden -> Spacer(base)
        mark == WeekDayMark.TRAINED -> Box(base.background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        mark == WeekDayMark.MISSED -> Box(base.background(scheme.surfaceVariant))
        mark == WeekDayMark.TODAY -> Box(base.border(2.dp, scheme.primary, CircleShape))
        else -> Box(base.border(1.dp, scheme.outline, CircleShape))
    }
}

@Composable
private fun WeekNote(text: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = Spacing.md))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoFarRow(week: ProfileWeek, unit: WeightUnit, styles: ProfileStyles, loading: Boolean) {
    val locale = currentLocale()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = Spacing.md))
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            stringResource(R.string.profile_week_so_far),
            style = body,
            color = muted.orTransparent(loading),
            modifier = Modifier.loadingBlock(loading),
        )
        if (loading || week.volumeKgSoFar > 0.0) {
            val number = NumberFormat.getIntegerInstance(locale)
                .format(Math.round(WeightDisplay.toDisplay(week.volumeKgSoFar, unit)))
            val symbol = stringResource(if (unit == WeightUnit.KG) R.string.unit_symbol_kg else R.string.unit_symbol_lb)
            Text(
                if (loading) AnnotatedString("$number $symbol") else withEmphasis("$number $symbol", number, styles.monoEmphasis),
                style = body,
                color = muted.orTransparent(loading),
                modifier = Modifier.loadingBlock(loading),
            )
        }
        val sets = NumberFormat.getIntegerInstance(locale).format(week.setsSoFar)
        Text(
            pluralStringResource(R.plurals.profile_week_sets, week.setsSoFar, sets).let {
                if (loading) AnnotatedString(it) else withEmphasis(it, sets, styles.monoEmphasis)
            },
            style = body,
            color = muted.orTransparent(loading),
            modifier = Modifier.loadingBlock(loading),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Scorecards
// ---------------------------------------------------------------------------------------------

/** One scorecard. A later tile (Steps streak) is one more entry in the list the screen builds. */
internal data class ScoreTileSpec(
    val key: String,
    /** Numbers only; any suffix ("/17") is a styled span of the same string. */
    val value: AnnotatedString,
    val label: String,
    val supporting: AnnotatedString?,
    /** 0..1 progress bar drawn in the tile's bottom padding, or null. */
    val progress: Float?,
    /** What TalkBack reads for the whole tile, for example "Week streak, 13 weeks. Longest 13." */
    val description: String,
    val onClick: () -> Unit,
)

private val TileSidePadding = 16.dp
private val TileValueTrailing = 24.dp

/**
 * The scorecards: two columns while every tile's whole value and each single word of its label and
 * supporting line fit a column, otherwise one full-width row per tile. Measured, not a font-scale
 * breakpoint (mockup README, "Two columns while they fit"), so 1.3x stays two columns and 2.0x
 * switches to rows.
 */
@Composable
internal fun ScoreTileGrid(
    tiles: List<ScoreTileSpec>,
    styles: ProfileStyles,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    MeasuredTwoColumnGrid(
        items = tiles,
        itemKey = { it.key },
        gap = Spacing.sm,
        modifier = modifier,
        fitsInColumn = { tile, columnPx, measurer ->
            val inner = columnPx - with(density) { (TileSidePadding * 2).toPx() }
            val valueWidth = measurer.widthOf(tile.value, styles.tileValue) + with(density) { TileValueTrailing.toPx() }
            valueWidth <= inner &&
                measurer.wordsFit(AnnotatedString(tile.label), styles.tileLabel, inner) &&
                (tile.supporting == null || measurer.wordsFit(tile.supporting, styles.tileSupporting, inner))
        },
    ) { tile, singleColumn, cellModifier ->
        ScoreTile(tile, styles, singleColumn, loading, cellModifier)
    }
}

@Composable
private fun ScoreTile(spec: ScoreTileSpec, styles: ProfileStyles, singleColumn: Boolean, loading: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val semanticsModifier = if (loading) Modifier.clearAndSetSemantics { } else Modifier.clearAndSetSemantics { contentDescription = spec.description }
    LogEzCard(onClick = spec.onClick, modifier = modifier.cardSemantics(loading)) {
        Box(modifier = Modifier.fillMaxWidth().then(semanticsModifier)) {
            val valueText: @Composable (Modifier) -> Unit = { m ->
                Text(
                    spec.value.hiddenIf(loading),
                    style = styles.tileValue,
                    color = scheme.onSurface.orTransparent(loading),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = m.loadingBlock(loading, 56.dp),
                )
            }
            val labelAndSupport: @Composable () -> Unit = {
                Text(
                    spec.label,
                    style = styles.tileLabel,
                    color = scheme.onSurface.orTransparent(loading),
                    modifier = Modifier.loadingBlock(loading, 96.dp),
                )
                if (spec.supporting != null) {
                    Text(
                        spec.supporting.hiddenIf(loading),
                        style = styles.tileSupporting,
                        color = scheme.onSurfaceVariant.orTransparent(loading),
                        modifier = Modifier.padding(top = if (singleColumn) Spacing.xxs else Spacing.xs).loadingBlock(loading, 72.dp),
                    )
                }
            }
            if (singleColumn) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = TileSidePadding, top = Spacing.md, end = 40.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f)) { labelAndSupport() }
                    valueText(Modifier)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(start = TileSidePadding, top = Spacing.md, end = TileSidePadding, bottom = 20.dp)) {
                    valueText(Modifier.padding(end = TileValueTrailing))
                    Column(modifier = Modifier.padding(top = Spacing.xxs)) { labelAndSupport() }
                }
            }
            if (!loading) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(if (singleColumn) Alignment.CenterEnd else Alignment.TopEnd)
                        .padding(top = if (singleColumn) 0.dp else Spacing.sm, end = Spacing.xs)
                        .size(20.dp),
                )
                if (spec.progress != null) {
                    // Inside the tile's bottom padding, so it adds no height and the tile beside it
                    // keeps no empty band.
                    LinearProgressIndicator(
                        progress = { spec.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = TileSidePadding, end = if (singleColumn) 40.dp else TileSidePadding, bottom = Spacing.xs)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The measured grid, shared by the scorecards and the destinations
// ---------------------------------------------------------------------------------------------

/**
 * Lays [items] out two to a row while [fitsInColumn] says every item fits a column, otherwise one
 * item per full-width row. A single item is always a full-width row; a trailing odd item among
 * several keeps one column's width. Rows share the height of
 * their tallest cell.
 */
@Composable
internal fun <T> MeasuredTwoColumnGrid(
    items: List<T>,
    itemKey: (T) -> Any,
    gap: Dp,
    fitsInColumn: (item: T, columnWidthPx: Float, measurer: TextMeasurer) -> Boolean,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, singleColumn: Boolean, modifier: Modifier) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnPx = (constraints.maxWidth - with(density) { gap.toPx() }) / 2f
        // A lone item is a full-width row, not half a row with nothing beside it (the new-user Achievements
        // tile). A trailing odd item among several keeps one column's width.
        val twoColumns = items.size > 1 && items.all { fitsInColumn(it, columnPx, measurer) }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            if (twoColumns) {
                items.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        pair.forEach { item ->
                            key(itemKey(item)) { cell(item, false, Modifier.weight(1f).fillMaxHeight()) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items.forEach { item ->
                    key(itemKey(item)) { cell(item, true, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

internal fun TextMeasurer.widthOf(text: AnnotatedString, style: TextStyle): Float =
    measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()

/** True when each whitespace-separated word of [text] is no wider than [availablePx] on its own. */
internal fun TextMeasurer.wordsFit(text: AnnotatedString, style: TextStyle, availablePx: Float): Boolean {
    val plain = text.text
    var start = 0
    while (start < plain.length) {
        while (start < plain.length && plain[start] == ' ') start++
        var end = start
        while (end < plain.length && plain[end] != ' ') end++
        if (end > start && widthOf(text.subSequence(start, end), style) > availablePx + 0.5f) return false
        start = end
    }
    return true
}
