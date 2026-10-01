package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * PHASE2_PLAN.md §8.7 — consecutive weeks with >=1 COMPLETED workout, honoring the first-day-
 * of-week setting. A still-empty current week does not break an otherwise-active streak.
 */
object StreakCalculator {
    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun weeklyStreak(workoutDates: List<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek): Int {
        val weekStarts = workoutDates.map { weekStart(it, firstDayOfWeek) }.toHashSet()
        val currentWeekStart = weekStart(today, firstDayOfWeek)
        var anchor = when {
            currentWeekStart in weekStarts -> currentWeekStart
            currentWeekStart.minusWeeks(1) in weekStarts -> currentWeekStart.minusWeeks(1)
            else -> return 0
        }
        var streak = 0
        while (anchor in weekStarts) {
            streak++
            anchor = anchor.minusWeeks(1)
        }
        return streak
    }

    /** Calendar aggregation (§8.7): completed-workout count per local date, for calendar highlighting. */
    fun countsByDate(workoutDates: List<LocalDate>): Map<LocalDate, Int> =
        workoutDates.groupingBy { it }.eachCount()

    /**
     * Consecutive calendar days with >=1 completed workout — [weeklyStreak]'s same grace rule, one
     * granularity down: a still-empty today does not break an otherwise-active streak, it just
     * hasn't extended it yet (mirrors §8.7's "still-empty current week" rule for days).
     */
    fun dailyStreak(workoutDates: List<LocalDate>, today: LocalDate): Int {
        val dateSet = workoutDates.toHashSet()
        var anchor = when {
            today in dateSet -> today
            today.minusDays(1) in dateSet -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (anchor in dateSet) {
            streak++
            anchor = anchor.minusDays(1)
        }
        return streak
    }

    /**
     * The longest run of consecutive calendar days in [days] -- ever, not the one ending today. This is
     * the strict counterpart of [dailyStreak] (it counts every day with a workout, nothing else), so
     * Profile's "Longest" line always compares like with like against its current value.
     */
    fun longestDailyStreak(days: Collection<LocalDate>): Int = longestRun(days.toSortedSet().toList(), stepDays = 1)

    /** The longest run of consecutive weeks (starting on [firstDayOfWeek]) with at least one day in [days]. */
    fun longestWeeklyStreak(days: Collection<LocalDate>, firstDayOfWeek: DayOfWeek): Int =
        longestRun(days.map { weekStart(it, firstDayOfWeek) }.distinct().sorted(), stepDays = 7)

    private fun longestRun(sorted: List<LocalDate>, stepDays: Long): Int {
        if (sorted.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == stepDays) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
