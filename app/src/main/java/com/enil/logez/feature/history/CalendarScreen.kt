package com.enil.logez.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * PHASE2_PLAN.md §5.2 "Calendar screen": month grid with workout days highlighted, unlimited
 * scroll-back, a streak banner, and the first-day-of-week picker that writes the shared setting.
 *
 * "An un-highlighted calendar is itself the honest empty state; no placeholder dots" — so there is
 * no empty-state branch here at all. A month with no workouts simply renders undecorated.
 *
 * The Year / multi-year view switcher the plan also lists is not built: month-at-a-time with
 * unbounded paging already delivers unlimited scroll-back, and a year grid is a presentation of the
 * same `countsByDate` map that earns its own design pass alongside M6's charts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onWorkoutClick: (workoutId: String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var dayWorkouts by remember { mutableStateOf<List<CalendarDayWorkout>>(emptyList()) }
    var firstDayMenuExpanded by remember { mutableStateOf(false) }

    // A workout deleted or edited elsewhere changes which days are highlighted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedDay) {
        val day = selectedDay
        dayWorkouts = if (day == null) emptyList() else viewModel.workoutsOn(day)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Owner: the header should read "CALENDAR", not the month -- the month name
                // already lives in the stepper row below (uiState.displayedMonth + Chevron
                // controls), so the header would otherwise just duplicate it.
                title = { ScreenTitle(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { firstDayMenuExpanded = true }) {
                        Icon(Icons.Filled.CalendarViewWeek, contentDescription = stringResource(R.string.calendar_first_day_of_week))
                    }
                    DropdownMenu(expanded = firstDayMenuExpanded, onDismissRequest = { firstDayMenuExpanded = false }) {
                        // §5.2 lists exactly these three — the conventional week starts, not all seven.
                        listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                            DropdownMenuItem(
                                text = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                                onClick = { firstDayMenuExpanded = false; viewModel.setFirstDayOfWeek(day) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            Text(
                if (uiState.weeklyStreak > 0) {
                    pluralStringResource(R.plurals.calendar_streak_banner, uiState.weeklyStreak, uiState.weeklyStreak)
                } else {
                    stringResource(R.string.calendar_no_streak)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::showPreviousMonth) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous_month))
                }
                Text(
                    uiState.displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::showNextMonth) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
                }
            }

            MonthGrid(
                state = uiState,
                onDayClick = { date -> if (uiState.countsByDate.containsKey(date)) selectedDay = date },
            )
        }
    }

    val day = selectedDay
    if (day != null) {
        ModalBottomSheet(onDismissRequest = { selectedDay = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                dayWorkouts.forEach { workout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDay = null; onWorkoutClick(workout.workoutId) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(workout.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            formatCalendarDuration(workout.durationSeconds),
                            style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
                TextButton(onClick = { selectedDay = null }, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onDayClick: (LocalDate) -> Unit) {
    val firstOfMonth = state.displayedMonth.atDay(1)
    // How many blank cells precede the 1st, given where the user's week starts.
    val leadingBlanks = ((firstOfMonth.dayOfWeek.value - state.firstDayOfWeek.value) + 7) % 7
    val dayLabels = (0 until 7).map { state.firstDayOfWeek.plus(it.toLong()) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(bottom = Spacing.xs),
                )
            }
        }

        val cells = leadingBlanks + state.displayedMonth.lengthOfMonth()
        val rows = (cells + 6) / 7
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth < 1 || dayOfMonth > state.displayedMonth.lengthOfMonth()) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = state.displayedMonth.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            hasWorkout = state.countsByDate.containsKey(date),
                            isToday = date == state.today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    hasWorkout: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.aspectRatio(1f).clickable(enabled = hasWorkout, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .then(if (hasWorkout) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    hasWorkout -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun formatCalendarDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
