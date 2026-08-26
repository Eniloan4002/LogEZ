package com.enil.logez.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One bar: [label] names the bucket (shown for the first/last bar), [value] is the raw height. */
data class BarChartEntry(val label: String, val value: Double)

/**
 * Dependency-free Compose Canvas bar chart — LineChart's sibling for the §5.2 dashboard's
 * weekly-bucketed training charts and the Monthly Report's 6-month comparison (M6a decision:
 * no third-party chart library). Y-axis renders max/mid/zero gridline labels via [yLabel]; the
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
) {
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle: TextStyle = LogEzMono.dataSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    // Bars grow from zero — a bar chart with a non-zero baseline misleads (§5.2's honest-stats
    // rule); yHigh pads 10% above the max so the tallest bar never touches the top edge.
    val maxY = entries.maxOf { it.value }
    val yHigh = if (maxY > 0.0) maxY * 1.1 else 1.0

    // Deduplicate gridlines by their rendered label — but anchored on the endpoints, never
    // first-wins: with a truncating yLabel (toInt, integer division) the MID label can collide
    // with zero's ("0.5" -> "0") or the max's, and keeping mid's line would float a "0" label at
    // mid-height with no true baseline. So: the zero baseline always survives, the max line
    // survives unless its label collapsed to zero's (all-zero/all-truncated series), and the mid
    // line only survives when its label differs from both endpoints.
    val maxLine = maxY to yLabel(maxY)
    val midLine = (maxY / 2.0) to yLabel(maxY / 2.0)
    val zeroLine = 0.0 to yLabel(0.0)
    val gridLines = buildList {
        if (maxLine.second != zeroLine.second) add(maxLine)
        if (midLine.second != maxLine.second && midLine.second != zeroLine.second) add(midLine)
        add(zeroLine)
    }
    val leftPadding = gridLines.maxOf { textMeasurer.measure(it.second, labelStyle).size.width } + 12
    val bottomPadding = textMeasurer.measure("0", labelStyle).size.height + 8

    fun barCenterX(index: Int, width: Float): Float {
        val plotWidth = width - leftPadding
        return leftPadding + plotWidth * (index + 0.5f) / entries.size
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
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
        val barWidth = (slot * 0.65f).coerceAtMost(48.dp.toPx())

        gridLines.forEach { (value, label) ->
            val y = plotHeight * (1f - (value / yHigh).toFloat())
            drawLine(gridColor, Offset(leftPadding.toFloat(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height)))
        }

        entries.forEachIndexed { i, entry ->
            val barHeight = (plotHeight * (entry.value / yHigh).toFloat()).coerceAtLeast(0f)
            drawRect(
                color = if (i == selectedIndex) selectedColor else barColor,
                topLeft = Offset(barCenterX(i, size.width) - barWidth / 2f, plotHeight - barHeight),
                size = Size(barWidth, barHeight),
            )
        }

        val firstLabel = textMeasurer.measure(entries.first().label, labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPadding.toFloat(), size.height - firstLabel.size.height))
        if (entries.size > 1) {
            val lastLabel = textMeasurer.measure(entries.last().label, labelStyle)
            drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, size.height - lastLabel.size.height))
        }
    }
}
