package com.enil.logez.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.AchievementCalculator
import com.enil.logez.core.domain.calc.AchievementInput
import com.enil.logez.core.domain.calc.AchievementProgress
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.calc.WidgetSnapshotCalculator
import com.enil.logez.core.domain.calc.toBodyRegion
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Profile tab", as redesigned 2026-10-01 (docs/mockups/profile-2026-10-01):
 * the This week card (days trained against the target, which days, records, volume and sets so
 * far), four scorecards (week streak, day streak, workouts, achievements), the weekly chart's four
 * series (3 months), the last-7-days muscle map, Today's Health Connect figures and Measurements'
 * latest weight. Fresh zone/today per refresh; single-snapshot publish.
 *
 * The day streak, its "Longest" line and the week streak are published as plain fields
 * ([ProfileUiState.streakDays], [ProfileUiState.longestDayStreak], ...) and the screen reads
 * nothing else, so the source of the day streak can change in one place here without a UI change.
 *
 * The temporary RPE toggle (2026-08-24) moved to the real Settings screen in M16. The temporary
 * DEBUG-only "Seed/Clear Demo Data" rows (2026-08-26) were removed from this screen entirely on
 * 2026-09-04 (Owner directive) — [com.enil.logez.core.data.seed.DemoDataSeeder] itself is untouched
 * and still Hilt-injectable/tested on its own, just no longer wired to any UI.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    val healthMetricsSource: HealthMetricsSource,
    private val wellnessRepository: WellnessRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val measurementRepository: MeasurementRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Connect came back with nothing granted (first-run plan, O1f, F6). Health Connect can't report a
     * refusal (nothing is granted before asking either), and after one it answers every later
     * request at once without showing anything, so Connect would look dead. This flag keeps the
     * card on its settings fallback instead. Saved state, so it outlives process death; only a
     * refresh that finds a grant clears it.
     */
    private var wellnessRefused: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_WELLNESS_REFUSED) ?: false
        set(value) {
            savedStateHandle[KEY_WELLNESS_REFUSED] = value
        }

    private var refreshJob: Job? = null

    /**
     * The screen's single load trigger — RefreshOnResume calls this on every ON_RESUME (no init load).
     * A refresh still running is cancelled first, so a slow earlier one can never publish over a newer one.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
            val workoutEntities = workoutRepository.getCompletedWorkouts()
            val workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) }
            val workoutDates = workouts.map { DashboardAggregator.localDate(it.startedAt, zone) }
            val joined = workoutRepository.getSetsWithExerciseForCompletedWorkouts()
            val exerciseById = joined.map { it.exerciseId }.distinct()
                .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }.toMap()
            val sets = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                DashboardAggregator.SetWithExercise(row.exerciseId, exercise.name, exercise.exerciseType, exercise.isBodyweightVolumeEligible, row.set)
            }

            val last7Window = today.minusDays(6)..today
            val last7Count = workouts.count { DashboardAggregator.localDate(it.startedAt, zone) in last7Window }
            val last7Muscle = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                MuscleStatsCalculator.MuscleSetInput(
                    exercise.primaryMuscleGroup,
                    DashboardAggregator.localDate(row.set.workoutStartedAt, zone),
                    row.set.setType,
                    row.set.isCompleted,
                )
            }
            val heatCounts = MuscleStatsCalculator
                .distributionInWindows(last7Muscle, last7Window, previousWindow = null, settings.includeWarmupsInStats)
                .current
            val heatMax = heatCounts.maxOfOrNull { it.setCount } ?: 0

            // This week. WidgetSnapshotCalculator counts active days (two sessions on one day are one
            // day), the same figure the home-screen widget shows against the same target.
            val snapshot = WidgetSnapshotCalculator.snapshot(
                completedWorkoutDates = workoutDates,
                today = today,
                firstDayOfWeek = settings.firstDayOfWeek,
                targetDaysThisWeek = settings.weeklyActiveDayTarget,
                steps = null,
                stepsUpdatedAt = null,
            )
            val weekStart = StreakCalculator.weekStart(today, settings.firstDayOfWeek)
            val weekWindow = weekStart..weekStart.plusDays(6)
            val weekTotals = DashboardAggregator.periodTotals(workouts, sets, weekWindow, zone, settings.includeWarmupsInStats)
            // One query for both uses: the whole-history count feeds Achievements, this week's feeds the
            // records line. Distinct exercises, not rows: one first set writes up to four rows.
            val records = personalRecordsRepository.getAchievedBetween(0L, Long.MAX_VALUE)
            val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val weekEndMillis = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val recordsThisWeek = records
                .filter { it.achievedAt in weekStartMillis until weekEndMillis }
                .map { it.exerciseId }.distinct().size
            val week = ProfileWeek(
                start = weekStart,
                activeDays = snapshot.activeDaysThisWeek,
                targetDays = snapshot.targetDaysThisWeek,
                days = snapshot.weekDayStates.mapIndexed { index, trained ->
                    WeekDay(
                        date = weekStart.plusDays(index.toLong()),
                        mark = when {
                            trained -> WeekDayMark.TRAINED
                            index == snapshot.todayIndexInWeek -> WeekDayMark.TODAY
                            index < snapshot.todayIndexInWeek -> WeekDayMark.MISSED
                            else -> WeekDayMark.UPCOMING
                        },
                    )
                },
                todayIndex = snapshot.todayIndexInWeek,
                volumeKgSoFar = weekTotals.volumeKg,
                setsSoFar = weekTotals.sets,
                recordsThisWeek = recordsThisWeek,
            )

            val regionsTrained = heatCounts.filter { it.setCount > 0 }.mapNotNull { it.group.toBodyRegion() }.toSet()
            val latestWeight = measurementRepository.getLatestWeightOnOrBefore(today.format(DateTimeFormatter.ISO_LOCAL_DATE))

            // M21e: read-only, graceful degrade -- a device with no Health Connect (or a user who
            // hasn't granted the permission yet) simply doesn't get this section, never a nag.
            // Each type is honoured on its own (2026-09-25): a user who ticked only steps on Health
            // Connect's screen used to see nothing, because this required all three grants.
            val wellnessAvailability = healthMetricsSource.availability()
            val wellnessGranted = healthMetricsSource.grantedTypes()
            if (wellnessGranted.isNotEmpty()) wellnessRefused = false
            val stepsGranted = HealthDataType.STEPS in wellnessGranted
            var todaySteps: Long? = null
            var todayCalories: Double? = null
            if (stepsGranted || HealthDataType.CALORIES in wellnessGranted) {
                val totals = healthMetricsSource.readTodayTotals()
                if (stepsGranted) todaySteps = totals.steps
                todayCalories = totals.caloriesBurned
                // The widget's steps line reads this cache; a calories-only grant has no steps to
                // cache, and writing a 0 would show the widget a step count the user never shared.
                if (stepsGranted) {
                    wellnessRepository.upsert(
                        DailyWellnessTotal(
                            date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            steps = totals.steps,
                            caloriesBurned = totals.caloriesBurned,
                            updatedAt = clock.now().toEpochMilliseconds(),
                        ),
                    )
                }
            }

            // Achievements, from the cached daily steps (not a fresh Health Connect read): cheap on every
            // resume, and the Achievements screen refreshes that cache when it is opened. Read after
            // today's upsert above, so today's steps count.
            val trainedRegions = joined.mapNotNull { row ->
                val region = exerciseById[row.exerciseId]?.primaryMuscleGroup?.toBodyRegion() ?: return@mapNotNull null
                DashboardAggregator.localDate(row.set.workoutStartedAt, zone) to region
            }
            val dailySteps = wellnessRepository.getAll().associate {
                LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) to it.steps
            }
            val achievements = AchievementCalculator.summarize(
                AchievementCalculator.evaluate(
                    AchievementInput(
                        workoutDates = workoutDates,
                        exercisesWithRecords = records.map { it.exerciseId }.distinct().size,
                        trainedRegions = trainedRegions,
                        dailySteps = dailySteps,
                        firstDayOfWeek = settings.firstDayOfWeek,
                    ),
                ),
            )

            _uiState.value = ProfileUiState(
                isLoading = false,
                workoutCount = workouts.size,
                streakWeeks = StreakCalculator.weeklyStreak(workoutDates, today, settings.firstDayOfWeek),
                streakDays = StreakCalculator.dailyStreak(workoutDates, today),
                longestWeekStreak = StreakCalculator.longestWeeklyStreak(workoutDates, settings.firstDayOfWeek),
                longestDayStreak = StreakCalculator.longestDailyStreak(workoutDates),
                firstWorkoutDate = workoutDates.minOrNull(),
                week = week,
                achievementsUnlocked = achievements.unlocked,
                achievementsTotal = achievements.total,
                nextAchievement = achievements.next,
                latestWeight = latestWeight?.let { LatestWeight(it.weightKg, LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE)) },
                last7Count = last7Count,
                last7SetCount = heatCounts.sumOf { it.setCount },
                last7RegionsTrained = regionsTrained.size,
                last7RegionsMissing = BodyRegion.entries.filterNot { it in regionsTrained },
                last7Heat = if (heatMax == 0) emptyMap() else heatCounts.associate { it.group to it.setCount.toFloat() / heatMax },
                quickCharts = TrainingMetric.entries.associateWith { metric ->
                    DashboardAggregator.weeklyTrainingSeries(
                        metric, workouts, sets, ChartRange.LAST_3_MONTHS, today, zone,
                        settings.firstDayOfWeek, settings.includeWarmupsInStats,
                    )
                },
                weightUnit = settings.weightUnit,
                muscleDiagramVariant = settings.muscleDiagramVariant,
                wellnessAvailability = wellnessAvailability,
                wellnessGranted = wellnessGranted,
                wellnessRefused = wellnessRefused,
                todaySteps = todaySteps,
                todayCaloriesBurned = todayCalories,
            )
        }
    }

    /**
     * Called after the Compose permission launcher resolves. A grant re-runs the one load path
     * rather than duplicating it. Nothing granted records the refusal and shows it at once, without
     * a load: the card then points to Health Connect's settings.
     */
    fun onWellnessPermissionResult(anyGranted: Boolean) {
        if (anyGranted) {
            healthMetricsSource.onPermissionsRegranted()
            refresh()
        } else {
            wellnessRefused = true
            _uiState.value = _uiState.value.copy(wellnessRefused = true)
        }
    }

    private companion object {
        const val KEY_WELLNESS_REFUSED = "profile_wellness_refused"
    }
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val workoutCount: Int = 0,
    val streakWeeks: Int = 0,
    /**
     * The current day streak, and [longestDayStreak] beside it. Both come from [StreakCalculator]'s
     * strict functions (a day counts only with a workout). Kept as plain fields so the source can be
     * swapped in [ProfileViewModel.refresh] without a UI change.
     */
    val streakDays: Int = 0,
    val longestWeekStreak: Int = 0,
    val longestDayStreak: Int = 0,
    /** The local date of the earliest completed workout; null until there is one. */
    val firstWorkoutDate: LocalDate? = null,
    /** Null only until the first load lands. */
    val week: ProfileWeek? = null,
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = 0,
    /** The locked achievement the user is closest to; null when all are unlocked. */
    val nextAchievement: AchievementProgress? = null,
    val latestWeight: LatestWeight? = null,
    val last7Count: Int = 0,
    /** Included sets in the last 7 days, whether or not their muscle group maps to a body region (cardio, full body, other do not). */
    val last7SetCount: Int = 0,
    val last7Heat: Map<MuscleGroup, Float> = emptyMap(),
    /** Body regions with at least one set in the last 7 days, out of [BodyRegion.entries]. */
    val last7RegionsTrained: Int = 0,
    val last7RegionsMissing: List<BodyRegion> = emptyList(),
    val quickCharts: Map<TrainingMetric, List<DashboardAggregator.WeeklyBar>> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    /** M21e: whether Health Connect is usable, needs installing/updating, or is unsupported here -- picks which card shows. */
    val wellnessAvailability: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    /** The Health Connect types the user granted. Empty shows the Connect card; each stat shows only for its own type. */
    val wellnessGranted: Set<HealthDataType> = emptySet(),
    /**
     * A Connect came back with nothing granted. While nothing is granted, the Connect card then says
     * so and offers Health Connect's settings instead of a Connect that would do nothing.
     */
    val wellnessRefused: Boolean = false,
    val todaySteps: Long? = null,
    val todayCaloriesBurned: Double? = null,
)

/** How one day of the This week strip is drawn. A later change can add a rest-day mark here. */
enum class WeekDayMark {
    /** At least one completed workout that day. */
    TRAINED,

    /** A past day with none. */
    MISSED,

    /** Today, nothing logged yet. */
    TODAY,

    /** A day still to come. */
    UPCOMING,
}

data class WeekDay(val date: LocalDate, val mark: WeekDayMark)

/** The This week card: [days] are seven entries from the user's first day of week. */
data class ProfileWeek(
    val start: LocalDate,
    val activeDays: Int,
    val targetDays: Int,
    val days: List<WeekDay>,
    val todayIndex: Int,
    /** Volume (kg) and working sets so far this week, honouring the warm-up setting like Statistics does. */
    val volumeKgSoFar: Double,
    val setsSoFar: Int,
    /** Exercises that hold a personal record set this week. */
    val recordsThisWeek: Int,
) {
    val end: LocalDate get() = start.plusDays(6)
}

/** The newest logged bodyweight (kg, as stored) and the day it was logged. */
data class LatestWeight(val kg: Double, val date: LocalDate)
