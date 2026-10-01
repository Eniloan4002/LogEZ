package com.enil.logez.feature.workout

import com.enil.logez.core.designsystem.formatWeight
import com.enil.logez.core.designsystem.formatMmSs
import com.enil.logez.core.designsystem.formatTargetNumber
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.workout.session.TimedSetNotificationContent
import com.enil.logez.feature.workout.session.WorkoutNotificationContent
import com.enil.logez.feature.workout.session.WorkoutSessionController

/**
 * Builds and pushes the ongoing workout notification's content (§9.3) — pulled out of
 * [WorkoutLoggerViewModel] (2026-09-19 debt audit) since it's pure formatting over a snapshot of
 * exercises, with no dependency on the screen's own write-through/persist machinery.
 */
class WorkoutNotificationContentBuilder(
    private val sessionController: WorkoutSessionController,
) {
    /**
     * [timedSet] is the (exerciseId, setId) of a running set timer, if any: the notification then
     * follows that set (title, clock and Complete set), wherever it is in the workout, instead of
     * the first exercise with an open set.
     */
    fun push(exercises: List<WorkoutExerciseUiModel>, restingExerciseId: String?, weightUnit: WeightUnit, timedSet: Pair<String, String>? = null) {
        if (exercises.isEmpty()) {
            sessionController.updateNotificationContent(null)
            return
        }
        val current = exercises.find { it.id == restingExerciseId } ?: exercises.find { e -> e.sets.any { !it.isCompleted } } ?: exercises.last()
        val nextSet = current.sets.firstOrNull { !it.isCompleted }
        val completedInExercise = current.sets.count { it.isCompleted }
        val title = "${current.exerciseName} · set ${(completedInExercise + 1).coerceAtMost(current.sets.size.coerceAtLeast(1))} of ${current.sets.size}"
        val isResting = restingExerciseId != null
        val text = when {
            isResting -> "Resting…"
            nextSet != null -> "Next: ${formatSetTarget(nextSet, weightUnit)}"
            else -> "All sets complete"
        }
        sessionController.updateNotificationContent(
            WorkoutNotificationContent(
                title = title,
                text = text,
                isResting = isResting,
                actionableExerciseId = if (!isResting) current.id else null,
                actionableSetId = if (!isResting) nextSet?.id else null,
                timedSet = timedSetContent(exercises, timedSet),
            ),
        )
    }

    private fun timedSetContent(exercises: List<WorkoutExerciseUiModel>, timedSet: Pair<String, String>?): TimedSetNotificationContent? {
        val (exerciseId, setId) = timedSet ?: return null
        val exercise = exercises.find { it.id == exerciseId } ?: return null
        val index = exercise.sets.indexOfFirst { it.id == setId }
        if (index < 0) return null
        return TimedSetNotificationContent(
            exerciseId = exerciseId,
            setId = setId,
            title = "${exercise.exerciseName} · set ${index + 1} of ${exercise.sets.size}",
            label = "${exercise.exerciseName} · set ${index + 1}",
        )
    }

    private fun formatSetTarget(set: WorkoutSetUiModel, unit: WeightUnit): String {
        val parts = mutableListOf<String>()
        set.weightKg?.let { parts.add(formatWeight(it, unit)) }
        set.reps?.let { parts.add("× $it") }
        set.durationSeconds?.let { parts.add(formatMmSs(it)) }
        set.distanceMeters?.let { parts.add("${formatTargetNumber(it)}m") }
        return if (parts.isEmpty()) "—" else parts.joinToString(" ")
    }
}
