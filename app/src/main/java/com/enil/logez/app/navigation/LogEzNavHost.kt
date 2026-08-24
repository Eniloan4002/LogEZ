package com.enil.logez.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.enil.logez.feature.analytics.AnalyticsRoutes
import com.enil.logez.feature.analytics.AnalyticsScreen
import com.enil.logez.feature.analytics.AnalyticsViewModel
import com.enil.logez.feature.analytics.MonthlyReportScreen
import com.enil.logez.feature.analytics.ProfileScreen
import com.enil.logez.feature.exercises.CustomExerciseEditorScreen
import com.enil.logez.feature.exercises.ExerciseDetailScreen
import com.enil.logez.feature.exercises.ExerciseLibraryScreen
import com.enil.logez.feature.exercises.ExerciseRoutes
import com.enil.logez.feature.history.CalendarScreen
import com.enil.logez.feature.history.HistoryRoutes
import com.enil.logez.feature.history.HistoryScreen
import com.enil.logez.feature.history.WorkoutDetailScreen
import com.enil.logez.feature.routines.RoutineBuilderScreen
import com.enil.logez.feature.routines.RoutineDetailScreen
import com.enil.logez.feature.routines.RoutineRoutes
import com.enil.logez.feature.routines.WorkoutTabScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.enil.logez.feature.workout.WorkoutLoggerScreen
import com.enil.logez.feature.workout.WorkoutLoggerViewModel
import com.enil.logez.feature.workout.WorkoutRoutes
import com.enil.logez.feature.workout.finish.FinishWorkoutScreen
import com.enil.logez.feature.workout.finish.WorkoutSummaryScreen

@Composable
fun LogEzNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LogEzDestination.History.route,
        modifier = modifier,
    ) {
        composable(LogEzDestination.History.route) {
            HistoryScreen(
                onWorkoutClick = { id -> navController.navigate(HistoryRoutes.detail(id)) },
                onStartWorkout = {
                    navController.navigate(LogEzDestination.Workout.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(
            route = HistoryRoutes.DETAIL,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            WorkoutDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(WorkoutRoutes.edit(id)) },
                onSavedAsRoutine = { routineId -> navController.navigate(RoutineRoutes.builder(routineId = routineId)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onRoutineClick = { id -> navController.navigate(RoutineRoutes.detail(id)) },
            )
        }
        composable(HistoryRoutes.CALENDAR) {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onWorkoutClick = { id -> navController.navigate(HistoryRoutes.detail(id)) },
            )
        }
        composable(LogEzDestination.Workout.route) {
            WorkoutTabScreen(
                onRoutineClick = { id -> navController.navigate(RoutineRoutes.detail(id)) },
                onCreateRoutine = { folderId -> navController.navigate(RoutineRoutes.builder(folderId = folderId)) },
                onEditRoutine = { id -> navController.navigate(RoutineRoutes.builder(routineId = id)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
            )
        }
        composable(LogEzDestination.Profile.route) {
            ProfileScreen(
                onExercisesClick = { navController.navigate(ExerciseRoutes.library()) },
                onCalendarClick = { navController.navigate(HistoryRoutes.CALENDAR) },
                onStatisticsClick = { metric -> navController.navigate(AnalyticsRoutes.dashboard(focus = metric?.name)) },
            )
        }
        composable(
            route = AnalyticsRoutes.DASHBOARD,
            arguments = listOf(navArgument(AnalyticsViewModel.FOCUS_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onMuscleClick = { muscle -> navController.navigate(ExerciseRoutes.library(muscle = muscle.name)) },
                onMonthlyReportClick = { navController.navigate(AnalyticsRoutes.MONTHLY_REPORT) },
            )
        }
        composable(AnalyticsRoutes.MONTHLY_REPORT) {
            MonthlyReportScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = ExerciseRoutes.LIBRARY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            ExerciseLibraryScreen(
                onBack = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
                onEditExercise = { id -> navController.navigate(ExerciseRoutes.editor(exerciseId = id)) },
            )
        }
        composable(
            route = ExerciseRoutes.DETAIL,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) {
            ExerciseDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ExerciseRoutes.editor(exerciseId = id)) },
                onDeleted = { navController.popBackStack() },
                onDuplicated = { newId ->
                    navController.popBackStack()
                    navController.navigate(ExerciseRoutes.detail(newId))
                },
            )
        }
        composable(
            route = ExerciseRoutes.EDITOR,
            arguments = listOf(
                navArgument("exerciseId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("prefillName") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            CustomExerciseEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = RoutineRoutes.DETAIL,
            arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
        ) {
            RoutineDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(RoutineRoutes.builder(routineId = id)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
            )
        }
        composable(
            route = RoutineRoutes.BUILDER,
            arguments = listOf(
                navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("folderId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            RoutineBuilderScreen(
                onBack = { navController.popBackStack() },
                onSaved = { routineId ->
                    navController.popBackStack()
                    if (navController.currentDestination?.route != RoutineRoutes.DETAIL) {
                        navController.navigate(RoutineRoutes.detail(routineId))
                    }
                },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
            )
        }

        composable(
            route = WorkoutRoutes.LOGGER,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            val workoutId = it.arguments?.getString("workoutId").orEmpty()
            WorkoutLoggerScreen(
                // M4b: the Logger is now reachable from any tab (mini-bar tap-to-expand) or
                // directly from a cold start (§9.5 recovery) -- a target-route pop assuming
                // "Workout" is always an ancestor in the back stack silently no-ops (and strands
                // the user on the finished/discarded Logger) whenever it isn't. A plain pop
                // always returns to whatever was actually beneath this destination.
                onExit = { navController.popBackStack() },
                onNavigateToFinish = {
                    navController.navigate(WorkoutRoutes.finish(workoutId)) { launchSingleTop = true }
                },
                onDiscarded = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
            )
        }

        composable(
            route = WorkoutRoutes.EDIT,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType },
                // The one thing that distinguishes this destination from LOGGER. Declared as a
                // defaulted nav argument rather than a route segment so the flag reaches
                // SavedStateHandle without putting "true" in the URL; LOGGER omits it entirely and
                // the ViewModel reads absent-as-false.
                navArgument(WorkoutLoggerViewModel.EDIT_MODE_ARG) { type = NavType.BoolType; defaultValue = true },
            ),
        ) {
            WorkoutLoggerScreen(
                // §5.1.10: Save and Cancel both return to Workout Detail, which re-reads on resume.
                onExit = { navController.popBackStack() },
                onNavigateToFinish = {}, // edit mode saves in place — there is no finish hand-off
                onDiscarded = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
            )
        }

        composable(
            route = WorkoutRoutes.FINISH,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            FinishWorkoutScreen(
                // Back returns to the still-IN_PROGRESS Logger — nothing was saved yet (§5.1.8).
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    // Drop the Logger and this screen: the workout is COMPLETED, so neither is a
                    // sane back destination from the summary.
                    navController.navigate(WorkoutRoutes.summary(savedId)) {
                        popUpTo(WorkoutRoutes.LOGGER) { inclusive = true }
                    }
                },
                onDiscardInstead = {
                    // The workout has been deleted by now, so the Logger behind this screen is
                    // dead too — pop through it. A target pop at "workout" silently no-opped
                    // whenever the Logger was reached from the mini-bar or a cold start, which
                    // left the user sitting on the Save screen with nothing having happened.
                    if (!navController.popBackStack(WorkoutRoutes.LOGGER, inclusive = true)) {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = WorkoutRoutes.SUMMARY,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            WorkoutSummaryScreen(
                // §5.1.8(c): "Done -> History tab".
                onDone = {
                    navController.navigate(LogEzDestination.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
