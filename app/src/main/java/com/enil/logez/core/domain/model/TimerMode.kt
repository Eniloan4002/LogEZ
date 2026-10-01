package com.enil.logez.core.domain.model

/**
 * How an exercise's inline set timer behaves inside one workout: a stopwatch counting up, or a
 * countdown from the set's TIME. Chosen per workout exercise (`workout_exercises.timer_mode`), so it
 * never carries to another workout that has the same exercise.
 *
 * Stored as text, `null` for a stopwatch (so every row from before the column reads back as one) and
 * `"COUNTDOWN"` for a countdown. The stored text is kept as the source of truth in the entity, the
 * backup and the UI model, and an unknown value (a newer app's mode) is never rewritten: it is
 * [of]-interpreted as a stopwatch for behaviour only.
 */
enum class TimerMode(val stored: String?) {
    STOPWATCH(null),
    COUNTDOWN("COUNTDOWN"),
    ;

    companion object {
        /** The mode a stored `timer_mode` value means; anything this app does not know behaves as a stopwatch. */
        fun of(stored: String?): TimerMode = if (stored == COUNTDOWN.stored) COUNTDOWN else STOPWATCH
    }
}
