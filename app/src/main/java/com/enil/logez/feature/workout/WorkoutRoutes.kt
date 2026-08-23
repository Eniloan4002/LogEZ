package com.enil.logez.feature.workout

/** Route strings for the Live Workout Logger (PHASE2_PLAN.md §4). */
object WorkoutRoutes {
    const val LOGGER = "workout_logger/{workoutId}"

    fun logger(workoutId: String) = "workout_logger/$workoutId"
}
