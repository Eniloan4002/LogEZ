package com.enil.logez.core.domain.model

/** PHASE2_PLAN.md §3.1 §3.2 — at most one [IN_PROGRESS] workout at a time (process-death recovery anchor). */
enum class WorkoutStatus {
    IN_PROGRESS,
    COMPLETED,
}
