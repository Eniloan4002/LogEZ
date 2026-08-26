package com.enil.logez.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

private const val ROWS = 7
private val CELL_SIZE = 10.dp
private val CELL_GAP = 3.dp

/** Day-counts at or above this many completed workouts render at full color intensity. */
private const val MAX_INTENSITY_COUNT = 2

/**
 * GitHub-contributions-style workout activity grid (M8c) — one column per week (oldest to
 * newest, left to right), one cell per day (top row = [firstDayOfWeek]). Cell intensity is
 * `min(count, MAX_INTENSITY_COUNT) / MAX_INTENSITY_COUNT`, the same rest-to-fill-color `lerp`
 * convention [BodyDiagram] uses, so both "progress" visualizations in the app read consistently.
 * Days after [today] (the partial trailing week) render no cell at all, never a false "0" square
 * — the same honest-empty-state rule every other screen in this app follows.
 *
 * The week count is derived from the available width, not a fixed constant: cells stay a small,
 * compact fixed size (a short, wide grid rather than a tall one), and however many whole weeks
 * fit at that size are shown — so the grid always fills its container, on any screen, with no
 * dead space and no horizontal scroll to discover.
 */
@Composable
fun HeatmapGrid(
    countsByDate: Map<LocalDate, Int>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
) {
    val restColor = MaterialTheme.colorScheme.outline
    val fillColor = MaterialTheme.colorScheme.primary
    val gridHeight = CELL_SIZE * ROWS + CELL_GAP * (ROWS - 1)

    BoxWithConstraints(modifier = modifier.height(gridHeight)) {
        val weeks = max(1, ((maxWidth + CELL_GAP) / (CELL_SIZE + CELL_GAP)).toInt())
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val firstWeekStart = currentWeekStart.minusWeeks((weeks - 1).toLong())

        Canvas(modifier = Modifier.height(gridHeight)) {
            val cell = CELL_SIZE.toPx()
            val gap = CELL_GAP.toPx()
            val corner = CornerRadius(3.dp.toPx())
            for (week in 0 until weeks) {
                val weekStart = firstWeekStart.plusWeeks(week.toLong())
                for (day in 0 until ROWS) {
                    val date = weekStart.plusDays(day.toLong())
                    if (date.isAfter(today)) continue
                    val count = countsByDate[date] ?: 0
                    val t = count.coerceAtMost(MAX_INTENSITY_COUNT).toFloat() / MAX_INTENSITY_COUNT
                    drawRoundRect(
                        color = lerp(restColor, fillColor, t),
                        topLeft = Offset(week * (cell + gap), day * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = corner,
                    )
                }
            }
        }
    }
}
