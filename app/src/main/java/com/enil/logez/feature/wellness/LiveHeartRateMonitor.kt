package com.enil.logez.feature.wellness

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** M21f default poll cadence for [liveHeartRateFlow]. */
private const val DEFAULT_POLL_INTERVAL_MS = 10_000L

/**
 * M21f. Polls [healthMetricsSource] for the latest heart-rate sample at [pollIntervalMs] while
 * something is actively collecting -- a **cold** flow, same spine-rule shape as
 * `WorkoutSessionController`'s `elapsedSecondsFlow`/`restRemainingMillisFlow`/
 * `inlineTimerSecondsFlow` (`flow { while(true) {...; delay(...)} }`), not a stateful
 * start()/stop()-lifecycle object. That distinction is load-bearing, not stylistic: an eagerly-
 * started self-ticking coroutine (e.g. kicked off from a ViewModel's `init` block) begins the
 * instant the ViewModel is *constructed* -- including in a plain unit test with no real Compose
 * collector ever watching the value -- which is exactly the "unbounded ticker hangs the test
 * suite" bug `WorkoutLoggerViewModel`'s own doc comment warns about (and which an earlier version
 * of this exact function reproduced: `LiveHeartRateMonitor.start()` called from `init {}` hung
 * `WorkoutLoggerViewModelTest` indefinitely, since nothing in that test ever collects the flow to
 * make it stop). A cold flow sidesteps this entirely: it costs nothing until collected, and
 * cancels itself the instant the collector goes away (a screen leaving composition, or a test that
 * never subscribes at all) -- no explicit lifecycle wiring needed on the ViewModel's part.
 * Silently emits null -- never crashes, never nags -- whenever Health Connect isn't available or
 * the permission isn't granted, matching the Profile card's own graceful-degrade rule.
 *
 * Emits the whole [HeartRateSample], not just the bpm -- see [HealthMetricsSource.readLatestHeartRate]'s
 * own doc comment for why a caller showing this live needs the timestamp, not just the number.
 */
fun liveHeartRateFlow(healthMetricsSource: HealthMetricsSource, pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS): Flow<HeartRateSample?> = flow {
    while (true) {
        val sample = if (healthMetricsSource.availability() == HealthConnectAvailability.Available && healthMetricsSource.hasAllPermissions()) {
            healthMetricsSource.readLatestHeartRate()
        } else {
            null
        }
        emit(sample)
        delay(pollIntervalMs)
    }
}
