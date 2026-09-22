package com.enil.logez.core.domain.model

/**
 * What kind of session a workout row is — the *modality*, orthogonal to [WorkoutStructure]'s
 * question of how sets are grouped (a GPS-tracked run is REGULAR-structured; a circuit is
 * STRENGTH-kind).
 *
 * This is persisted because process-death recovery has to answer "which live surface owns this
 * IN_PROGRESS row?" from Room alone. Before this column existed the only discriminator was
 * `ActivityTrackingController.state.value.workoutId` — in-memory state that dies with the process —
 * so every recovered GPS run was routed into the strength Logger and started a second foreground
 * service alongside the still-running location one.
 *
 * Immutable after creation (like [WorkoutStructure]): a session does not change modality.
 */
enum class WorkoutKind {
    /** Typed into the Logger: straight sets, circuits, supersets. Every row created before v9. */
    STRENGTH,

    /**
     * Driven by `ActivityTrackingController` + `ActivityTrackingService`: one exercise, one set,
     * with distance and route written by GPS rather than by the user.
     */
    GPS_TRACKED,
}
