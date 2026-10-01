package com.enil.logez.feature.routines

import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import javax.inject.Inject

/**
 * What [rememberRecentStartHandler] needs from a screen's ViewModel: start a copy of a finished
 * workout, report which resume path a conflict should take, and discard-then-start. Both the
 * Recent list ([RecentWorkoutsViewModel]) and the Recent detail ([RecentWorkoutDetailViewModel])
 * offer it, so the same confirmation and conflict dialogs serve both.
 */
interface RecentStartActions {
    suspend fun start(workoutId: String): StartResult
    suspend fun discardInProgressAndStart(workoutId: String): String
    suspend fun inProgressWorkout(): InProgressWorkout?
}

/**
 * The one implementation of [RecentStartActions]: [WorkoutStarter.startFromWorkout] verbatim -- the
 * exact function History's "Copy Workout" calls -- plus the session start every start path pairs it with.
 */
class RecentWorkoutStarter @Inject constructor(
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val sessionDiscarder: SessionDiscarder,
    private val inProgressWorkoutResolver: InProgressWorkoutResolver,
) : RecentStartActions {
    override suspend fun start(workoutId: String): StartResult {
        val result = workoutStarter.startFromWorkoutOrConflict(workoutId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    /** Mirrors `WorkoutDetailViewModel.discardInProgressAndStartCopy` -- same two calls, same order. */
    override suspend fun discardInProgressAndStart(workoutId: String): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startFromWorkout(workoutId)
        sessionController.startSession(id)
        return id
    }

    override suspend fun inProgressWorkout(): InProgressWorkout? = inProgressWorkoutResolver.resolve()
}
