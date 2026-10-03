package com.enil.logez.feature.activity

import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.session.WorkoutSessionController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What to do with a GPS run whose process died mid-track. The route and distance are unrecoverable
 * — they lived only in [ActivityTrackingController]'s memory — so the only honest options are to
 * keep the elapsed time or throw the row away, and the user picks.
 *
 * Kept out of the dialog composable so both writes are unit-testable against the existing fakes.
 */
@Singleton
class InterruptedTrackingRecovery @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTrackingController: ActivityTrackingController,
    private val sessionController: WorkoutSessionController,
    private val clock: Clock,
) {
    /**
     * The same two writes [ActivityTrackingController.finishTracking] performs, minus the distance
     * and route this path cannot know: mark the single set completed, and freeze a truthful
     * elapsed duration onto the parent.
     *
     * Marking the set completed is not optional — `WorkoutDao.finishWorkout` purges uncompleted
     * sets before rebuilding PRs, so leaving it blank would silently delete the workout's only row
     * when the user saves.
     *
     * The elapsed time is MOVING time: the strength session clock the run shared (`accumulatedActiveSeconds`,
     * `isPaused`, `lastResumedAtMillis`, saved with every Pause and Resume) is what the mini-bar showed,
     * so a run killed while paused saves the time it had when paused, not the wall time since it began.
     * Without that saved clock (it was cleared, or belongs to another workout) it falls back to the
     * time since the run began.
     *
     * Deliberately leaves the workout IN_PROGRESS: the Save Workout screen still owns the
     * COMPLETED flip, and its editable duration field is the correction path for a run reopened
     * long after it was interrupted.
     */
    suspend fun keepElapsedTime(workoutId: String): Boolean {
        val workout = workoutRepository.getById(workoutId) ?: return false
        val now = clock.now().toEpochMilliseconds()
        sessionController.rehydrate()
        val elapsedSeconds = if (sessionController.state.value.workoutId == workoutId) {
            sessionController.elapsedSeconds(now).toInt().coerceAtLeast(0)
        } else {
            ((now - workout.startedAt) / 1000).toInt().coerceAtLeast(0)
        }

        val sets = workoutRepository.getExercisesForWorkout(workoutId)
            .flatMap { workoutRepository.getSetsForWorkoutExercise(it.id) }
        sets.forEach { workoutRepository.updateWorkoutSetCompletion(it.id, true, now) }
        workoutRepository.updateWorkout(workout.copy(durationSeconds = elapsedSeconds, updatedAt = now))

        activityTrackingController.cancelTracking()
        return true
    }

    suspend fun discard(workoutId: String) {
        workoutRepository.deleteById(workoutId)
        activityTrackingController.cancelTracking()
    }
}
