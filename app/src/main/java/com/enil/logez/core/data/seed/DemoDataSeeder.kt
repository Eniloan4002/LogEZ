package com.enil.logez.core.data.seed

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.history.WorkoutDeleter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Temporary dev/demo tool (Owner request 2026-08-26) — populates 12 weeks of realistic COMPLETED
 * workout history against real seeded exercises, so the Analytics dashboard, Statistics, the
 * Workout-tab heatmap, and Goals progress all have real content to show rather than an empty
 * install. Not wired into app-start seeding anywhere; only the temporary "Seed Demo Data" row on
 * the Profile tab calls [seed]. Every generated workout's `notes` field is tagged [DEMO_MARKER] so
 * [clear] can find and remove exactly what this seeded — via the same [WorkoutDeleter] a real
 * "Delete Workout" uses, so PRs get rebuilt correctly on clear too — and never touch a real logged
 * workout, which would never carry that marker.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val workoutDeleter: WorkoutDeleter,
    private val clock: Clock,
) {
    suspend fun seed() {
        val exercisesByName = exerciseRepository.getAllActive().associateBy { it.name }
        // The seed library ships names in a fixed, hand-verified set (docs/PHASE2_PLAN.md's 400)
        // -- if any of these is missing (seed not applied yet, or a future rename), skip it rather
        // than build a workout with a hole in it.
        val push = PUSH_NAMES.mapNotNull { exercisesByName[it] }
        val pull = PULL_NAMES.mapNotNull { exercisesByName[it] }
        val legs = LEGS_NAMES.mapNotNull { exercisesByName[it] }
        if (push.isEmpty() && pull.isEmpty() && legs.isEmpty()) return // seed library not loaded yet

        val includeWarmups = personalRecordsUpdater.includeWarmupsInStats()
        val nowMillis = clock.now().toEpochMilliseconds()
        val touchedExerciseIds = mutableSetOf<String>()
        val totalSessions = WEEKS * SESSIONS_PER_WEEK

        for (i in 0 until totalSessions) {
            // i=0 is the oldest session (~12 weeks back); the most recent lands a couple of days
            // ago, never "today", so today's own cell in the heatmap stays honestly empty until a
            // real workout is logged.
            val daysAgo = ((totalSessions - 1 - i) * 7) / SESSIONS_PER_WEEK + 2
            val startedAt = nowMillis - daysAgo * DAY_MILLIS - MORNING_OFFSET_MILLIS
            val durationSeconds = 2_700 + (i % 5) * 300 // 45-65 min
            val progression = i.toDouble() / (totalSessions - 1).coerceAtLeast(1) // 0.0 (oldest) .. 1.0 (newest): light progressive overload

            val todays = when (i % 3) { 0 -> push; 1 -> pull; else -> legs }
            if (todays.isEmpty()) continue

            val workoutId = UUID.randomUUID().toString()
            val workoutExercises = mutableListOf<WorkoutExerciseEntity>()
            val workoutSets = mutableListOf<WorkoutSetEntity>()

            todays.forEachIndexed { exIndex, exercise ->
                val weId = UUID.randomUUID().toString()
                workoutExercises += WorkoutExerciseEntity(
                    id = weId, workoutId = workoutId, exerciseId = exercise.id, orderIndex = exIndex,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                )
                touchedExerciseIds += exercise.id

                for (setIndex in 0 until SETS_PER_EXERCISE) {
                    val isWarmup = setIndex == 0 && exercise.exerciseType == ExerciseType.WEIGHT_REPS
                    val values = setValues(exercise, setIndex, progression, isWarmup)
                    workoutSets += WorkoutSetEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseId = weId,
                        orderIndex = setIndex,
                        setType = if (isWarmup) SetType.WARMUP else SetType.NORMAL,
                        weightKg = values.weightKg,
                        reps = values.reps,
                        durationSeconds = values.durationSeconds,
                        distanceMeters = null,
                        rpe = null,
                        customMetric = null,
                        isCompleted = true,
                        completedAt = startedAt + setIndex * 90_000L,
                    )
                }
            }

            workoutRepository.insertFullWorkout(
                WorkoutEntity(
                    id = workoutId, routineId = null, title = DEMO_TITLE, notes = DEMO_MARKER,
                    status = WorkoutStatus.COMPLETED, startedAt = startedAt,
                    endedAt = startedAt + durationSeconds * 1_000L, durationSeconds = durationSeconds,
                    createdAt = startedAt, updatedAt = startedAt,
                ),
                workoutExercises,
                workoutSets,
            )
        }

        if (touchedExerciseIds.isNotEmpty()) {
            personalRecordsUpdater.rebuildForExercises(touchedExerciseIds, workoutId = "", includeWarmupsInStats = includeWarmups)
        }
    }

    /** Removes exactly what [seed] created (matched by [DEMO_MARKER]), never a real logged workout. */
    suspend fun clear() {
        workoutRepository.getCompletedWorkouts()
            .filter { it.notes == DEMO_MARKER }
            .forEach { workoutDeleter.delete(it.id) }
    }

    private data class SetValues(val weightKg: Double?, val reps: Int?, val durationSeconds: Int?)

    private fun setValues(exercise: Exercise, setIndex: Int, progression: Double, isWarmup: Boolean): SetValues =
        when (exercise.exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                val working = roundToNearestPlate(BASELINE_KG.getValue(exercise.name) * (1.0 + progression * 0.15))
                SetValues(weightKg = if (isWarmup) roundToNearestPlate(working * 0.5) else working, reps = 6 + (setIndex % 3), durationSeconds = null)
            }
            ExerciseType.REPS_ONLY -> SetValues(weightKg = null, reps = 6 + (progression * 4).roundToInt() + (setIndex % 2), durationSeconds = null)
            ExerciseType.DURATION -> SetValues(weightKg = null, reps = null, durationSeconds = 30 + (progression * 30).roundToInt() + setIndex * 5)
            else -> SetValues(null, null, null)
        }

    private fun roundToNearestPlate(kg: Double): Double = (kg / 2.5).roundToInt() * 2.5

    companion object {
        const val DEMO_MARKER = "__logEZ_demo_seed__"
        private const val DEMO_TITLE = "Workout"
        private const val WEEKS = 12
        private const val SESSIONS_PER_WEEK = 3
        private const val SETS_PER_EXERCISE = 4
        private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        private const val MORNING_OFFSET_MILLIS = 15 * 60 * 60 * 1_000L // "today minus N days" lands ~9am

        private val PUSH_NAMES = listOf("Bench Press (Barbell)", "Overhead Press (Barbell)", "Plank")
        private val PULL_NAMES = listOf("Bent Over Row (Barbell)", "Pull Up")
        private val LEGS_NAMES = listOf("Back Squat (Barbell)", "Deadlift (Barbell)")

        private val BASELINE_KG = mapOf(
            "Bench Press (Barbell)" to 60.0,
            "Overhead Press (Barbell)" to 40.0,
            "Bent Over Row (Barbell)" to 50.0,
            "Back Squat (Barbell)" to 80.0,
            "Deadlift (Barbell)" to 100.0,
        )
    }
}
