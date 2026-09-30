package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * P-208 achievements, grouped for the Achievements screen's sections, in display order.
 * Plain fitness wording (Owner, 2026-09-30) -- the approved mockup's lab names ("First Specimen",
 * "Anomaly Detected") were dropped; the app's own strings had already moved off that voice.
 */
enum class AchievementCategory { WORKOUTS, CONSISTENCY, STRENGTH, DAILY_STEPS, STEP_STREAKS }

/**
 * Every achievement and the number it unlocks at. Declared in display order within each category.
 * The ids ([name]) are stable keys for the future custom-badge pass -- rename the display strings,
 * never these.
 */
enum class Achievement(val category: AchievementCategory, val target: Long) {
    FIRST_WORKOUT(AchievementCategory.WORKOUTS, 1),
    WORKOUTS_10(AchievementCategory.WORKOUTS, 10),
    WORKOUTS_50(AchievementCategory.WORKOUTS, 50),

    DAY_STREAK_7(AchievementCategory.CONSISTENCY, 7),
    DAY_STREAK_30(AchievementCategory.CONSISTENCY, 30),
    WEEK_STREAK_52(AchievementCategory.CONSISTENCY, 52),

    FIRST_PR(AchievementCategory.STRENGTH, 1),
    PRS_10(AchievementCategory.STRENGTH, 10),
    FULL_BODY_WEEK(AchievementCategory.STRENGTH, 8),

    STEPS_DAY_20K(AchievementCategory.DAILY_STEPS, 20_000),
    STEPS_DAY_30K(AchievementCategory.DAILY_STEPS, 30_000),
    STEPS_DAY_40K(AchievementCategory.DAILY_STEPS, 40_000),
    STEPS_DAY_50K(AchievementCategory.DAILY_STEPS, 50_000),
    STEPS_DAY_75K(AchievementCategory.DAILY_STEPS, 75_000),
    STEPS_DAY_100K(AchievementCategory.DAILY_STEPS, 100_000),

    STEP_STREAK_30(AchievementCategory.STEP_STREAKS, 30),
    STEP_STREAK_365(AchievementCategory.STEP_STREAKS, 365),
}

/** Where an achievement stands: [current] is the best the user has ever reached toward [Achievement.target]. */
data class AchievementProgress(val achievement: Achievement, val current: Long) {
    val target: Long get() = achievement.target
    val unlocked: Boolean get() = current >= target
}

/**
 * Everything the calculator needs, already read out of the repositories. Every list is
 * whole-history: achievements are "ever reached", so a broken streak or a later, smaller day never
 * re-locks one.
 */
data class AchievementInput(
    /** One entry per completed workout (repeats allowed -- two sessions on one day are two workouts). */
    val workoutDates: List<LocalDate>,
    /**
     * Exercises holding at least one personal record. Not raw record rows: one first-ever set
     * creates up to four (heaviest weight, 1RM, set volume, session volume), which made "10 PRs"
     * read 4 / 10 after a single set on-device. Per exercise it is monotonic too -- a beaten
     * record updates its row in place, it never disappears.
     */
    val exercisesWithRecords: Int,
    /** (day trained, region trained) for every completed set whose exercise maps to a [BodyRegion]. */
    val trainedRegions: List<Pair<LocalDate, BodyRegion>>,
    /** Daily step totals; empty when Health Connect has never supplied any. */
    val dailySteps: Map<LocalDate, Long>,
    val firstDayOfWeek: DayOfWeek,
)

/**
 * Pure and whole-history, so nothing about an unlock is stored: every value is recomputed from
 * data the app already keeps (workouts, the records table, the daily steps cache). Nothing here is
 * a "current" streak -- see [longestDailyRun].
 */
object AchievementCalculator {
    /** The daily bar for both step-streak achievements (Owner, 2026-09-30: "10k steps for 30 days"). */
    const val STEP_STREAK_DAILY_MINIMUM = 10_000L

    fun evaluate(input: AchievementInput): List<AchievementProgress> {
        val workoutCount = input.workoutDates.size.toLong()
        val trainingDays = input.workoutDates.toSet()
        val longestDayStreak = longestDailyRun(trainingDays).toLong()
        val longestWeekStreak = longestWeeklyRun(trainingDays, input.firstDayOfWeek).toLong()
        val bestRegionWeek = bestWeekRegionCount(input.trainedRegions, input.firstDayOfWeek).toLong()
        val bestStepDay = input.dailySteps.values.maxOrNull() ?: 0L
        val longestStepStreak = longestDailyRun(
            input.dailySteps.filterValues { it >= STEP_STREAK_DAILY_MINIMUM }.keys,
        ).toLong()

        return Achievement.entries.map { achievement ->
            val current = when (achievement.category) {
                AchievementCategory.WORKOUTS -> workoutCount
                AchievementCategory.CONSISTENCY ->
                    if (achievement == Achievement.WEEK_STREAK_52) longestWeekStreak else longestDayStreak
                AchievementCategory.STRENGTH ->
                    if (achievement == Achievement.FULL_BODY_WEEK) bestRegionWeek else input.exercisesWithRecords.toLong()
                AchievementCategory.DAILY_STEPS -> bestStepDay
                AchievementCategory.STEP_STREAKS -> longestStepStreak
            }
            AchievementProgress(achievement, current)
        }
    }

    /** The longest run of consecutive calendar days in [days] -- ever, not the one ending today. */
    internal fun longestDailyRun(days: Set<LocalDate>): Int = longestRun(days.sorted(), stepDays = 1)

    /** The longest run of consecutive weeks (starting on [firstDayOfWeek]) with at least one day in [days]. */
    internal fun longestWeeklyRun(days: Set<LocalDate>, firstDayOfWeek: DayOfWeek): Int =
        longestRun(days.map { StreakCalculator.weekStart(it, firstDayOfWeek) }.distinct().sorted(), stepDays = 7)

    /** The most distinct [BodyRegion]s trained inside any single week. */
    internal fun bestWeekRegionCount(trained: List<Pair<LocalDate, BodyRegion>>, firstDayOfWeek: DayOfWeek): Int =
        trained.groupBy({ StreakCalculator.weekStart(it.first, firstDayOfWeek) }, { it.second })
            .values
            .maxOfOrNull { it.toSet().size } ?: 0

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
