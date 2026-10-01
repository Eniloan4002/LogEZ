package com.enil.logez.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.enil.logez.R
import androidx.compose.ui.res.pluralStringResource

/** One bar: [label] names the bucket (shown for the first/last bar), [value] is the raw height. */
data class BarChartEntry(val label: String, val value: Double)

/**
 * Dependency-free Compose Canvas bar chart — LineChart's sibling for the §5.2 dashboard's
 * weekly-bucketed training charts and the Monthly Report's 6-month comparison (M6a decision:
 * no third-party chart library — M20c/ADR-0009 carves out a narrow, radar-plot-only exception
 * via KoalaPlot for the Statistics screen's muscle-balance wheel; this chart is unaffected).
 * Y-axis renders max/mid/zero gridline labels via [yLabel]; the
 * x-axis labels the first and last bar. Tapping selects the nearest bar by x.
 */
@Composable
fun BarChart(
    entries: List<BarChartEntry>,
    yLabel: (Double) -> String,
    selectedIndex: Int?,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
    /**
     * Draws the last bar at [PARTIAL_BAR_ALPHA] of its colour: it is a period still in progress (the
     * current week), so it reads as "so far" rather than as a finished, lower week.
     */
    partialLastBar: Boolean = false,
    /**
     * Grows [chartHeight] by twice the axis label's line-height growth at large font sizes, so the
     * plot keeps its height while the labels above and below it get taller (0 at the default size).
     */
    growWithFontScale: Boolean = false,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle: TextStyle = LogEzMono.dataSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    val fontScaleGrowth = if (growWithFontScale) {
        with(LocalDensity.current) { ((16.sp.toDp() - 16.dp) * 2f).coerceAtLeast(0.dp) }
    } else {
        0.dp
    }
    // A Canvas is invisible to TalkBack, so the chart speaks a one-line summary of its data
    // (2026-09-25 accessibility pass); the per-bar tap readout stays a sighted-touch extra.
    val peak = entries.maxByOrNull { it.value }
    val summary = if (entries.isEmpty() || peak == null) {
        stringResource(R.string.chart_empty_summary)
    } else {
        stringResource(
            R.string.chart_bar_summary,
            pluralStringResource(R.plurals.chart_bar_count, entries.size, entries.size), entries.first().label, entries.last().label,
            yLabel(peak.value), peak.label, yLabel(entries.last().value),
        )
    }

    // Bars grow from zero — a bar chart with a non-zero baseline misleads (§5.2's honest-stats
    // rule); yHigh pads 10% above the max so the tallest bar never touches the top edge.
    val maxY = entries.maxOf { it.value }
    val yHigh = if (maxY > 0.0) maxY * 1.1 else 1.0

    val gridLines = barChartGridLines(maxY, yLabel)
    val leftPadding = gridLines.maxOf { textMeasurer.measure(it.second, labelStyle).size.width } + 12
    val bottomPadding = textMeasurer.measure("0", labelStyle).size.height + 8

    fun barCenterX(index: Int, width: Float): Float {
        val plotWidth = width - leftPadding
        return leftPadding + plotWidth * (index + 0.5f) / entries.size
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = summary }
            .fillMaxWidth()
            .height(chartHeight + fontScaleGrowth)
            .pointerInput(entries) {
                detectTapGestures { tap ->
                    val nearest = entries.indices.minByOrNull { i ->
                        kotlin.math.abs(barCenterX(i, size.width.toFloat()) - tap.x)
                    }
                    nearest?.let(onBarTap)
                }
            },
    ) {
        val plotHeight = size.height - bottomPadding
        val plotWidth = size.width - leftPadding
        val slot = plotWidth / entries.size
        // Clamped at zero because inset() below rejects a negative-size region: a canvas narrower
        // than its own y-axis labels makes plotWidth (and so slot) negative, which the old flat
        // drawRect absorbed silently by drawing nothing.
        val barWidth = (slot * 0.65f).coerceAtMost(48.dp.toPx()).coerceAtLeast(0f)

        gridLines.forEach { (value, label) ->
            val y = plotHeight * (1f - (value / yHigh).toFloat())
            drawLine(gridColor, Offset(leftPadding.toFloat(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height)))
        }

        val topRadius = CornerRadius(4.dp.toPx())
        val bottomRadius = CornerRadius(2.dp.toPx())

        entries.forEachIndexed { i, entry ->
            val barHeight = (plotHeight * (entry.value / yHigh).toFloat()).coerceAtLeast(0f)
            // An empty bucket has no bar to draw, and a flat drawRect of zero height drew nothing.
            if (barHeight <= 0f) return@forEachIndexed

            val isSelected = i == selectedIndex
            val baseColor = if (isSelected) selectedColor else barColor
            val color = if (partialLastBar && i == entries.lastIndex && !isSelected) baseColor.copy(alpha = PARTIAL_BAR_ALPHA) else baseColor
            val barTopLeft = Offset(barCenterX(i, size.width) - barWidth / 2f, plotHeight - barHeight)
            val barSize = Size(barWidth, barHeight)

            // inset() rather than a plain topLeft/size pair: barPath() is defined in bar-local
            // coordinates (its RoundRect starts at Offset.Zero), so the DrawScope's origin has to
            // be translated to this bar's position for the path to land in the right place.
            inset(
                left = barTopLeft.x,
                top = barTopLeft.y,
                right = size.width - barTopLeft.x - barSize.width,
                bottom = size.height - barTopLeft.y - barSize.height,
            ) {
                drawPath(barPath(barSize, topRadius, bottomRadius), color = color)
            }
        }

        val firstLabel = textMeasurer.measure(entries.first().label, labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPadding.toFloat(), size.height - firstLabel.size.height))
        if (entries.size > 1) {
            val lastLabel = textMeasurer.measure(entries.last().label, labelStyle)
            drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, size.height - lastLabel.size.height))
        }
    }
}

/** The strength of a [BarChart]'s in-progress last bar (the mockup's "primary 42%"). */
const val PARTIAL_BAR_ALPHA = 0.42f

/**
 * The y-axis gridlines, as (value, label) pairs from top to bottom.
 *
 * Extracted from [BarChart] unchanged so the rule can be tested; the dedupe itself predates the
 * Profile redesign. Gridlines are deduplicated by their rendered label, but anchored on the
 * endpoints, never first-wins: with a rounding yLabel the MID label can collide with zero's
 * ("0.5" -> "0") or the max's, and keeping mid's line would float a repeated label at mid-height.
 * So the zero baseline always survives, the max line survives unless its label collapsed to zero's
 * (an all-zero or all-truncated series), and the mid line only survives when its label differs from
 * both endpoints. A series whose maximum is 1 is the common case: the top reads "1", the mid value
 * 0.5 also rounds to "1", so only "1 / 0" is drawn.
 *
 * Known, and shared with Statistics: for an odd integer maximum the mid line sits at a half value
 * (2.5) whose rounded label ("3") reads slightly high.
 */
internal fun barChartGridLines(maxY: Double, yLabel: (Double) -> String): List<Pair<Double, String>> {
    val maxLine = maxY to yLabel(maxY)
    val midLine = (maxY / 2.0) to yLabel(maxY / 2.0)
    val zeroLine = 0.0 to yLabel(0.0)
    return buildList {
        if (maxLine.second != zeroLine.second) add(maxLine)
        if (midLine.second != maxLine.second && midLine.second != zeroLine.second) add(midLine)
        add(zeroLine)
    }
}

/**
 * A bar's silhouette in bar-local coordinates. The mockup's corners are asymmetric — rounded at the
 * lit top cap, near-square at the baseline so the bar still sits flush on the axis — and drawRoundRect
 * rounds all four equally, so the shape goes through an explicit [RoundRect] path instead.
 */
private fun barPath(size: Size, topRadius: CornerRadius, bottomRadius: CornerRadius): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = topRadius,
                topRight = topRadius,
                bottomRight = bottomRadius,
                bottomLeft = bottomRadius,
            ),
        )
    }
