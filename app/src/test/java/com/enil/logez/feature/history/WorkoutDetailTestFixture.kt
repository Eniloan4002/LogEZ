package com.enil.logez.feature.history

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import com.enil.logez.feature.workout.session.WorkoutSessionController
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * A [WorkoutDetailViewModel] over fakes for workout "w1", shared by the view model tests and the
 * detail screen's tests so both build it the same way.
 */
internal fun buildWorkoutDetailViewModel(
    dispatcher: CoroutineDispatcher,
    workoutRepo: FakeWorkoutRepository,
    trackRepo: FakeActivityTrackRepository = FakeActivityTrackRepository(),
    settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(listOf()),
    sampleRepo: FakeWorkoutHeartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(),
    healthSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
): WorkoutDetailViewModel {
    val personalRecordsRepo = FakePersonalRecordsRepository()
    val personalRecordsUpdater = PersonalRecordsUpdater(
        workoutRepo, exerciseRepo, personalRecordsRepo, FakeMeasurementRepository(), settingsRepo,
    )
    return WorkoutDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WorkoutDetailViewModel.WORKOUT_ID_ARG to "w1")),
        workoutRepository = workoutRepo,
        exerciseRepository = exerciseRepo,
        routineRepository = FakeRoutineRepository(),
        personalRecordsRepository = personalRecordsRepo,
        settingsRepository = settingsRepo,
        workoutDeleter = WorkoutDeleter(workoutRepo, personalRecordsUpdater, FakeTransactionRunner()),
        workoutToRoutineConverter = WorkoutToRoutineConverter(workoutRepo, FakeRoutineRepository(), FakeClock()),
        workoutStarter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock()),
        sessionController = WorkoutSessionController(
            FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher),
        ),
        activityTrackRepository = trackRepo,
        sessionDiscarder = SessionDiscarder(
            WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock()),
            WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher)),
            ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(), CoroutineScope(dispatcher)),
        ),
        inProgressWorkoutResolver = InProgressWorkoutResolver(
            workoutRepo,
            ActivityTrackingController(
                workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(),
                CoroutineScope(dispatcher),
            ),
        ),
        heartRateSampleRepository = sampleRepo,
        heartRateBackfill = com.enil.logez.core.wellness.WorkoutHeartRateBackfill(healthSource, sampleRepo, com.enil.logez.core.common.AppLogger.NoOp),
    )
}
