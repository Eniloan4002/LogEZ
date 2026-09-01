package com.enil.logez.core.domain.model

/**
 * How a routine/workout's exercises and sets are organized (M11 circuit training).
 *
 * [REGULAR] is the classic straight-sets shape: one card per exercise, sets performed as a block.
 * [CIRCUIT] reinterprets the *same* rows as an ordered sequence performed once per round: row k
 * (orderIndex k) of every exercise together forms round k+1. No separate tables exist for
 * circuits — this discriminator only changes how the rows are grouped and edited, which is what
 * keeps PRs, volume, analytics, previous-recall and CSV export working on circuit data unchanged.
 *
 * Immutable after creation (like [ExerciseType] on an exercise): flipping an existing routine
 * between shapes would silently reinterpret every row index.
 */
enum class WorkoutStructure {
    REGULAR,
    CIRCUIT,
}
