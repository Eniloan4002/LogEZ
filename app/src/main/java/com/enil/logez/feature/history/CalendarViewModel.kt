package com.enil.logez.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Calendar screen": unlimited scroll-back workout calendar plus streak
 * context. The date bucketing and the streak itself are [StreakCalculator]'s (§8.7) — this only
 * decides which month is on screen and resolves a tapped day to its workouts.
 *
 * Backdated workouts appear on their `startedAt` date, per §8.7, which falls out of using the same
 * timestamps everything else does rather than a separate "logged on" notion.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    // Resolved fresh at every call site, never cached — the same convention HistoryScreen and
    // WorkoutDetailScreen already use. A field here froze the zone at construction, so a ViewModel
    // that outlived a real zone change (travel, or a manual Settings change; this screen is scoped
    // to the NavBackStackEntry and can survive one) kept bucketing workouts by the old zone while
    // every other screen switched to the live one — the same workout showing two different dates
    // in the same app.
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun resolveToday(): LocalDate = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone()).toLocalDate()

    private val initialToday = resolveToday()
    private val displayedMonth = MutableStateFlow(YearMonth.from(initialToday))
    private val workoutDates = MutableStateFlow<List<LocalDate>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    // Reactive rather than a constructor-time val: this ViewModel is scoped to the NavBackStackEntry,
    // so it can stay alive across midnight if the user leaves the Calendar destination on the back
    // stack (mini-bar/tab switches, not popping it) — a frozen "today" would leave the day-cell marker
    // on the wrong day and feed StreakCalculator a stale anchor, which can assert an active streak
    // that has actually lapsed. refresh() re-derives it, same as workoutDates below.
    private val today = MutableStateFlow(initialToday)

    init {
        viewModelScope.launch {
            val z = zone()
            workoutDates.value = workoutRepository.getCompletedWorkoutTimestamps()
                .map { Instant.ofEpochMilli(it).atZone(z).toLocalDate() }
            isLoading.value = false
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        displayedMonth,
        workoutDates,
        isLoading,
        settingsRepository.settings.map { it.firstDayOfWeek },
        today,
    ) { month, dates, loading, firstDay, now ->
        CalendarUiState(
            isLoading = loading,
            displayedMonth = month,
            firstDayOfWeek = firstDay,
            countsByDate = StreakCalculator.countsByDate(dates),
            weeklyStreak = StreakCalculator.weeklyStreak(dates, now, firstDay),
            today = now,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarUiState(today = initialToday))

    fun showPreviousMonth() { displayedMonth.value = displayedMonth.value.minusMonths(1) }
    fun showNextMonth() { displayedMonth.value = displayedMonth.value.plusMonths(1) }

    /** §5.2: the first-day-of-week picker lives on this screen's top bar and writes the shared setting. */
    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setFirstDayOfWeek(day) }
    }

    /** §5.2: "tap a day -> bottom sheet listing that day's workouts". */
    suspend fun workoutsOn(date: LocalDate): List<CalendarDayWorkout> {
        // One zone for both bounds — resolving it twice could straddle a real zone change mid-call
        // and open the half-open range on inconsistent ground.
        val z = zone()
        return workoutRepository.getCompletedWorkoutsOn(
            date.atStartOfDay(z).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(),
        ).map { CalendarDayWorkout(it.id, it.title, it.durationSeconds) }
    }

    /**
     * Re-reads after a workout is added or removed elsewhere, and re-derives "today" — the screen
     * calls this on RESUME, which is exactly the moment a midnight the screen slept through would
     * otherwise go unnoticed.
     */
    fun refresh() {
        today.value = resolveToday()
        viewModelScope.launch {
            val z = zone()
            workoutDates.value = workoutRepository.getCompletedWorkoutTimestamps()
                .map { Instant.ofEpochMilli(it).atZone(z).toLocalDate() }
        }
    }

    /** §5.2's "Log a workout for this date" needs the day's local midnight as the pre-dated start. */
    suspend fun firstDayOfWeek(): DayOfWeek = settingsRepository.settings.first().firstDayOfWeek
}

data class CalendarUiState(
    val isLoading: Boolean = true,
    val displayedMonth: YearMonth = YearMonth.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val countsByDate: Map<LocalDate, Int> = emptyMap(),
    val weeklyStreak: Int = 0,
    val today: LocalDate = LocalDate.now(),
)

data class CalendarDayWorkout(val workoutId: String, val title: String, val durationSeconds: Int)
