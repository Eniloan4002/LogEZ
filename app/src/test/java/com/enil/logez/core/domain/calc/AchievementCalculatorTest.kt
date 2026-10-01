package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-208. Expected values are hard-coded literals (project testing rule), never recomputed. */
class AchievementCalculatorTest {

    private fun day(d: Int, month: Int = 9) = LocalDate.of(2026, month, d)

    private fun input(
        workoutDates: List<LocalDate> = emptyList(),
        exercisesWithRecords: Int = 0,
        regions: List<Pair<LocalDate, BodyRegion>> = emptyList(),
        steps: Map<LocalDate, Long> = emptyMap(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = AchievementInput(workoutDates, exercisesWithRecords, regions, steps, firstDayOfWeek)

    private fun List<AchievementProgress>.of(a: Achievement) = single { it.achievement == a }

    @Test
    fun `nothing logged locks everything at zero`() {
        val result = AchievementCalculator.evaluate(input())
        assertEquals(17, result.size)
        assertTrue(result.none { it.unlocked })
        assertTrue(result.all { it.current == 0L })
    }

    @Test
    fun `workout counts unlock at 1 and 10, 50 shows progress`() {
        val result = AchievementCalculator.evaluate(input(workoutDates = (1..10).map { day(it) }))
        assertTrue(result.of(Achievement.FIRST_WORKOUT).unlocked)
        assertTrue(result.of(Achievement.WORKOUTS_10).unlocked)
        assertFalse(result.of(Achievement.WORKOUTS_50).unlocked)
        assertEquals(10L, result.of(Achievement.WORKOUTS_50).current)
    }

    @Test
    fun `two workouts on one day count as two workouts but one streak day`() {
        val result = AchievementCalculator.evaluate(input(workoutDates = listOf(day(1), day(1), day(2))))
        assertEquals(3L, result.of(Achievement.WORKOUTS_10).current)
        assertEquals(2L, result.of(Achievement.DAY_STREAK_7).current)
    }

    @Test
    fun `the longest day streak ever counts, even after it broke`() {
        // Sep 1-7 in a row, then a gap, then Sep 20-22.
        val dates = (1..7).map { day(it) } + listOf(day(20), day(21), day(22))
        val result = AchievementCalculator.evaluate(input(workoutDates = dates))
        assertTrue(result.of(Achievement.DAY_STREAK_7).unlocked)
        assertEquals(7L, result.of(Achievement.DAY_STREAK_30).current)
    }

    @Test
    fun `a day streak runs across a month boundary`() {
        val dates = listOf(day(29, 8), day(30, 8), day(31, 8), day(1), day(2))
        assertEquals(5, AchievementCalculator.longestDailyRun(dates.toSet()))
    }

    @Test
    fun `week streaks follow the first day of the week setting`() {
        // Sun 6 Sep and Mon 7 Sep 2026.
        val dates = setOf(day(6), day(7))
        assertEquals(2, AchievementCalculator.longestWeeklyRun(dates, DayOfWeek.MONDAY))
        assertEquals(1, AchievementCalculator.longestWeeklyRun(dates, DayOfWeek.SUNDAY))
    }

    @Test
    fun `a missed week breaks the week streak`() {
        // Mondays 7, 14 and 28 Sep: the week of 21 Sep has nothing.
        val dates = setOf(day(7), day(14), day(28))
        assertEquals(2, AchievementCalculator.longestWeeklyRun(dates, DayOfWeek.MONDAY))
    }

    @Test
    fun `full body needs all 8 regions inside one week`() {
        val allEight = BodyRegion.entries.map { day(7) to it }
        assertTrue(AchievementCalculator.evaluate(input(regions = allEight)).of(Achievement.FULL_BODY_WEEK).unlocked)

        // Seven regions in the week of 7 Sep, the eighth the following week.
        val split = BodyRegion.entries.dropLast(1).map { day(8) to it } + (day(15) to BodyRegion.LOWER_LEG)
        val result = AchievementCalculator.evaluate(input(regions = split))
        assertFalse(result.of(Achievement.FULL_BODY_WEEK).unlocked)
        assertEquals(7L, result.of(Achievement.FULL_BODY_WEEK).current)
    }

    @Test
    fun `personal records unlock at 1 and 10 exercises`() {
        val result = AchievementCalculator.evaluate(input(exercisesWithRecords = 10))
        assertTrue(result.of(Achievement.FIRST_PR).unlocked)
        assertTrue(result.of(Achievement.PRS_10).unlocked)
    }

    @Test
    fun `daily step achievements use the single best day, inclusive of the target`() {
        val result = AchievementCalculator.evaluate(input(steps = mapOf(day(1) to 12_000L, day(2) to 20_000L)))
        assertTrue(result.of(Achievement.STEPS_DAY_20K).unlocked)
        assertFalse(result.of(Achievement.STEPS_DAY_30K).unlocked)
        assertEquals(20_000L, result.of(Achievement.STEPS_DAY_30K).current)
        assertEquals(20_000L, result.of(Achievement.STEPS_DAY_100K).current)
    }

    @Test
    fun `one step short of a target stays locked`() {
        val result = AchievementCalculator.evaluate(input(steps = mapOf(day(1) to 19_999L)))
        assertFalse(result.of(Achievement.STEPS_DAY_20K).unlocked)
    }

    @Test
    fun `30 days in a row at 10,000 steps unlocks the 30-day step streak`() {
        val steps = (1..30).associate { day(it) to 10_000L }
        val result = AchievementCalculator.evaluate(input(steps = steps))
        assertTrue(result.of(Achievement.STEP_STREAK_30).unlocked)
        assertEquals(30L, result.of(Achievement.STEP_STREAK_365).current)
    }

    @Test
    fun `a day under 10,000 steps breaks the step streak`() {
        // Sep 1-14 and 16-30 hit 10,000; Sep 15 is 9,999.
        val steps = (1..30).associate { day(it) to if (it == 15) 9_999L else 12_000L }
        val result = AchievementCalculator.evaluate(input(steps = steps))
        assertFalse(result.of(Achievement.STEP_STREAK_30).unlocked)
        assertEquals(15L, result.of(Achievement.STEP_STREAK_30).current)
    }

    @Test
    fun `a day with no step data breaks the step streak too`() {
        val steps = (1..30).filter { it != 10 }.associate { day(it) to 11_000L }
        assertEquals(20L, AchievementCalculator.evaluate(input(steps = steps)).of(Achievement.STEP_STREAK_30).current)
    }

    // --- Profile's Achievements tile (2026-10-01) ---

    @Test
    fun `summarize with nothing logged counts zero and points at First Workout`() {
        val summary = AchievementCalculator.summarize(AchievementCalculator.evaluate(input()))
        assertEquals(0, summary.unlocked)
        assertEquals(17, summary.total)
        assertEquals(Achievement.FIRST_WORKOUT, summary.next?.achievement)
    }

    @Test
    fun `summarize points at the locked achievement closest to its target`() {
        // One workout today: First Workout is unlocked. 7-Day Streak is 1 of 7, 10 Workouts 1 of 10.
        val summary = AchievementCalculator.summarize(AchievementCalculator.evaluate(input(workoutDates = listOf(day(1)))))
        assertEquals(1, summary.unlocked)
        assertEquals(Achievement.DAY_STREAK_7, summary.next?.achievement)
    }

    @Test
    fun `summarize has no next once every achievement is unlocked`() {
        val all = Achievement.entries.map { AchievementProgress(it, it.target) }
        val summary = AchievementCalculator.summarize(all)
        assertEquals(17, summary.unlocked)
        assertEquals(null, summary.next)
    }
}
