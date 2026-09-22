package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Everything the home-screen widget renders, resolved once. The widget composes from this and
 * nothing else — a Glance session times out within seconds, so it cannot observe anything live.
 */
data class WidgetSnapshot(
    /** Distinct calendar days this week with at least one completed workout. */
    val activeDaysThisWeek: Int,
    val targetDaysThisWeek: Int,
    val weekStreakWeeks: Int,
    /** Seven entries ordered from the user's first day of week; true = trained that day. */
    val weekDayStates: List<Boolean>,
    /** Index of today within [weekDayStates], for the "today" marker. */
    val todayIndexInWeek: Int,
    /** Null when nothing is cached, or when what is cached is from an earlier day. */
    val steps: Long?,
    /** When [steps] was read, for the "as of" label. Null whenever [steps] is. */
    val stepsAsOf: LocalDateTime?,
)

/**
 * Pure, so the widget's logic is testable even though its rendering is not.
 *
 * Counts active *days*, not workouts: two sessions on one Tuesday is one active day. This is also
 * why [GoalProgressCalculator]'s WORKOUT_COUNT is not reused here — it counts sessions, so a
 * two-a-day would read 3/4 on a two-day week.
 */
object WidgetSnapshotCalculator {
    fun snapshot(
        completedWorkoutDates: List<LocalDate>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        targetDaysThisWeek: Int,
        steps: Long?,
        stepsUpdatedAt: LocalDateTime?,
    ): WidgetSnapshot {
        val weekStart = StreakCalculator.weekStart(today, firstDayOfWeek)
        val trainedDates = completedWorkoutDates.toHashSet()
        val weekDayStates = (0L..6L).map { offset -> weekStart.plusDays(offset) in trainedDates }

        // A cached step count from an earlier day is not today's, and showing it under a "today"
        // framing would be wrong rather than merely stale.
        val stepsAreFromToday = stepsUpdatedAt?.toLocalDate() == today

        return WidgetSnapshot(
            activeDaysThisWeek = weekDayStates.count { it },
            targetDaysThisWeek = targetDaysThisWeek,
            weekStreakWeeks = StreakCalculator.weeklyStreak(completedWorkoutDates, today, firstDayOfWeek),
            weekDayStates = weekDayStates,
            todayIndexInWeek = (today.toEpochDay() - weekStart.toEpochDay()).toInt(),
            steps = steps.takeIf { stepsAreFromToday },
            stepsAsOf = stepsUpdatedAt.takeIf { stepsAreFromToday && steps != null },
        )
    }
}
