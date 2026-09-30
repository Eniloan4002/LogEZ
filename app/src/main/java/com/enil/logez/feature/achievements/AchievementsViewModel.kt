package com.enil.logez.feature.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.AchievementCalculator
import com.enil.logez.core.domain.calc.AchievementInput
import com.enil.logez.core.domain.calc.AchievementProgress
import com.enil.logez.core.domain.calc.toBodyRegion
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.canRead
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * P-208 Achievements. Nothing about an unlock is stored: each one is recomputed from data the app
 * already keeps ([AchievementCalculator]'s KDoc).
 *
 * Steps are the one input that needs care. The `daily_wellness_totals` cache only ever held the
 * days the app happened to be opened (the Workout tab and Profile upsert *today*), so on its own it
 * can't carry a 365-day streak. This screen therefore also reads [HISTORY_DAYS] of history from
 * Health Connect and writes each day it learns back into the cache -- so an unlocked step
 * achievement survives Health Connect later dropping old data, and only the Settings > Data
 * disconnect (which deletes the cache on purpose) takes it away.
 */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val wellnessRepository: WellnessRepository,
    private val healthMetricsSource: HealthMetricsSource,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _uiState.value = load() }
    }

    private suspend fun load(): AchievementsUiState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
        val settings = settingsRepository.settings.first()

        val workoutDates = workoutRepository.getCompletedWorkouts().map { it.startedAt.toLocalDate(zone) }
        // Exercises with a record, not record rows -- see AchievementInput.exercisesWithRecords.
        val exercisesWithRecords = personalRecordsRepository.getAchievedBetween(0L, Long.MAX_VALUE)
            .map { it.exerciseId }.distinct().size

        val rows = workoutRepository.getSetsWithExerciseForCompletedWorkouts()
        val muscleByExercise = rows.map { it.exerciseId }.distinct()
            .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it.primaryMuscleGroup } }
            .toMap()
        val trainedRegions = rows.mapNotNull { row ->
            muscleByExercise[row.exerciseId]?.toBodyRegion()?.let { region -> row.set.workoutStartedAt.toLocalDate(zone) to region }
        }

        val stepsConnected = healthMetricsSource.canRead(HealthDataType.STEPS)
        val dailySteps = loadDailySteps(today, stepsConnected)

        val progress = AchievementCalculator.evaluate(
            AchievementInput(
                workoutDates = workoutDates,
                exercisesWithRecords = exercisesWithRecords,
                trainedRegions = trainedRegions,
                dailySteps = dailySteps,
                firstDayOfWeek = settings.firstDayOfWeek,
            ),
        )
        return AchievementsUiState(isLoading = false, progress = progress, stepsConnected = stepsConnected)
    }

    /** Cache ∪ Health Connect, per day, keeping the larger total (a cached "today" may be a mid-day reading). */
    private suspend fun loadDailySteps(today: LocalDate, connected: Boolean): Map<LocalDate, Long> {
        val cached = wellnessRepository.getAll().associateBy { LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) }
        val merged = cached.mapValues { it.value.steps }.toMutableMap()
        if (!connected) return merged

        val now = clock.now().toEpochMilliseconds()
        // Chunked, so one failed read (the source returns an empty list on any error) costs one
        // window of days, not the whole year.
        var start = today.minusDays(HISTORY_DAYS - 1)
        while (!start.isAfter(today)) {
            val end = minOf(start.plusDays(CHUNK_DAYS - 1), today)
            for (day in healthMetricsSource.readStepsHistory(start, end)) {
                if (day.steps <= 0L) continue
                val existing = cached[day.date]
                val best = maxOf(day.steps, existing?.steps ?: 0L)
                merged[day.date] = best
                // Today's row belongs to the Workout tab/Profile refresh (it carries calories and
                // feeds the widget); only finished days are written back from here.
                if (day.date != today && (existing == null || existing.steps < best)) {
                    wellnessRepository.upsert(
                        DailyWellnessTotal(
                            date = day.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            steps = best,
                            caloriesBurned = existing?.caloriesBurned,
                            updatedAt = now,
                        ),
                    )
                }
            }
            start = end.plusDays(1)
        }
        return merged
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    internal companion object {
        /** Covers the 365-day step streak plus a margin; older days still count once cached. */
        const val HISTORY_DAYS = 400L
        const val CHUNK_DAYS = 90L
    }
}

data class AchievementsUiState(
    val isLoading: Boolean = true,
    val progress: List<AchievementProgress> = emptyList(),
    /** Whether Health Connect steps are readable right now -- gates the step sections' hint. */
    val stepsConnected: Boolean = false,
)
