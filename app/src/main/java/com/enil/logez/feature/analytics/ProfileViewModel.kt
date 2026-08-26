package com.enil.logez.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.seed.DemoDataSeeder
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Profile tab" — headline stats (lifetime workout count + weekly streak),
 * the last-7-days strip with its mini muscle heat-map, and the quick chart card's four weekly
 * series (default 3m, §5.2 region 4). Fresh zone/today per refresh; single-snapshot publish.
 *
 * Also still hosts the temporary RPE toggle (see [[decisions]] 2026-08-24 — no Settings screen
 * exists until M7; remove the toggle when the real Settings tree lands) and, as of 2026-08-26, a
 * temporary "Seed/Clear Demo Data" row for the same reason — both belong on a real Settings/dev
 * tools screen once one exists, not permanently on Profile.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val demoDataSeeder: DemoDataSeeder,
    private val clock: Clock,
) : ViewModel() {
    val rpeTrackingEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.rpeTrackingEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setRpeTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRpeTrackingEnabled(enabled) }
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isSeedingDemoData = MutableStateFlow(false)
    val isSeedingDemoData: StateFlow<Boolean> = _isSeedingDemoData.asStateFlow()

    /** Temporary dev tool (Owner request 2026-08-26) — see [DemoDataSeeder]. */
    fun seedDemoData() {
        if (_isSeedingDemoData.value) return
        viewModelScope.launch {
            _isSeedingDemoData.value = true
            demoDataSeeder.seed()
            refresh()
            _isSeedingDemoData.value = false
        }
    }

    fun clearDemoData() {
        if (_isSeedingDemoData.value) return
        viewModelScope.launch {
            _isSeedingDemoData.value = true
            demoDataSeeder.clear()
            refresh()
            _isSeedingDemoData.value = false
        }
    }

    /** The screen's single load trigger — RefreshOnResume calls this on every ON_RESUME (no init load). */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
            val workoutEntities = workoutRepository.getCompletedWorkouts()
            val workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) }
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

            _uiState.value = ProfileUiState(
                isLoading = false,
                workoutCount = workouts.size,
                streakWeeks = StreakCalculator.weeklyStreak(
                    workouts.map { DashboardAggregator.localDate(it.startedAt, zone) },
                    today,
                    settings.firstDayOfWeek,
                ),
                last7Count = last7Count,
                last7Heat = if (heatMax == 0) emptyMap() else heatCounts.associate { it.group to it.setCount.toFloat() / heatMax },
                quickCharts = TrainingMetric.entries.associateWith { metric ->
                    DashboardAggregator.weeklyTrainingSeries(
                        metric, workouts, sets, ChartRange.LAST_3_MONTHS, today, zone,
                        settings.firstDayOfWeek, settings.includeWarmupsInStats,
                    )
                },
                weightUnit = settings.weightUnit,
            )
        }
    }

}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val workoutCount: Int = 0,
    val streakWeeks: Int = 0,
    val last7Count: Int = 0,
    val last7Heat: Map<MuscleGroup, Float> = emptyMap(),
    val quickCharts: Map<TrainingMetric, List<DashboardAggregator.WeeklyBar>> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)
