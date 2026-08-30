package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.calc.VolumeCalculator
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.8(c) post-save summary: total volume, completed sets, duration, ordinal
 * workout count, weekly streak, and one medal per PR achieved. Reads the already-COMPLETED
 * workout — every number here is derived from persisted rows, never from live logger state, so
 * the screen shows exactly what was saved (the milestone's own acceptance criterion is "summary
 * matches logged data").
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState())
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState

    init {
        viewModelScope.launch {
            val workout = workoutRepository.getById(workoutId) ?: return@launch
            val settings = settingsRepository.settings.first()
            val sets = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
            val included = sets.filter { isIncluded(it.set, settings.includeWarmupsInStats) }

            val volumeKg = included.sumOf { row ->
                val exercise = exerciseRepository.getById(row.exerciseId)
                if (exercise == null) {
                    0.0
                } else {
                    VolumeCalculator.setVolume(
                        exerciseType = exercise.exerciseType,
                        isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
                        weightKg = row.set.weightKg,
                        reps = row.set.reps,
                        // Bodyweight-dependent volume needs a logged bodyweight; without one those
                        // exercise types contribute 0 by VolumeCalculator's own rules (§8.3),
                        // which is the honest answer rather than a fabricated estimate.
                        bodyweightKg = null,
                    )
                }
            }

            // §5.1.8 edge case: "Streak/PR computation uses the edited (backdated) startedAt."
            val zone = ZoneId.systemDefault()
            val workoutDates = workoutRepository.getCompletedWorkoutTimestamps()
                .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val streak = StreakCalculator.weeklyStreak(
                workoutDates = workoutDates,
                today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate(),
                firstDayOfWeek = settings.firstDayOfWeek,
            )

            // Share-card exercise lines, built the way HistoryViewModel builds its card summaries
            // (group by workoutExerciseId, order by exerciseOrderIndex, count through the same
            // `isIncluded` predicate as the Sets stat) so the card can't contradict History.
            val exerciseLines = sets.groupBy { it.workoutExerciseId }
                .entries
                .sortedBy { (_, blockRows) -> blockRows.first().exerciseOrderIndex }
                .map { (_, blockRows) ->
                    SummaryExerciseLine(
                        name = exerciseRepository.getById(blockRows.first().exerciseId)?.name.orEmpty(),
                        setCount = blockRows.count { isIncluded(it.set, settings.includeWarmupsInStats) },
                    )
                }

            val prs = personalRecordsRepository.getForWorkout(workoutId).map { pr ->
                PrMedal(
                    exerciseName = exerciseRepository.getById(pr.exerciseId)?.name.orEmpty(),
                    prType = pr.prType,
                    value = pr.value,
                )
            }

            _uiState.value = WorkoutSummaryUiState(
                isLoading = false,
                title = workout.title,
                startedAtMillis = workout.startedAt,
                durationSeconds = workout.durationSeconds,
                completedSetCount = included.size,
                totalVolumeKg = volumeKg,
                workoutOrdinal = workoutRepository.countCompletedWorkoutsUpTo(workout.startedAt, workout.id),
                weeklyStreak = streak,
                prMedals = prs,
                exerciseLines = exerciseLines,
            )
        }
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class PrMedal(val exerciseName: String, val prType: PrType, val value: Double)

/** One share-card exercise line, History's "3 × Bench Press (Barbell)" convention. */
data class SummaryExerciseLine(val name: String, val setCount: Int)

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val startedAtMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val completedSetCount: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val workoutOrdinal: Int = 0,
    val weeklyStreak: Int = 0,
    val prMedals: List<PrMedal> = emptyList(),
    val exerciseLines: List<SummaryExerciseLine> = emptyList(),
)
