package com.enil.logez.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.enil.logez.feature.analytics.ProfileScreen
import com.enil.logez.feature.exercises.CustomExerciseEditorScreen
import com.enil.logez.feature.exercises.ExerciseDetailScreen
import com.enil.logez.feature.exercises.ExerciseLibraryScreen
import com.enil.logez.feature.exercises.ExerciseRoutes
import com.enil.logez.feature.history.HistoryScreen
import com.enil.logez.feature.routines.RoutineBuilderScreen
import com.enil.logez.feature.routines.RoutineDetailScreen
import com.enil.logez.feature.routines.RoutineRoutes
import com.enil.logez.feature.routines.WorkoutTabScreen

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
        composable(LogEzDestination.History.route) { HistoryScreen() }
        composable(LogEzDestination.Workout.route) {
            WorkoutTabScreen(
                onRoutineClick = { id -> navController.navigate(RoutineRoutes.detail(id)) },
                onCreateRoutine = { folderId -> navController.navigate(RoutineRoutes.builder(folderId = folderId)) },
                onEditRoutine = { id -> navController.navigate(RoutineRoutes.builder(routineId = id)) },
            )
        }
        composable(LogEzDestination.Profile.route) {
            ProfileScreen(
                onExercisesClick = { navController.navigate(ExerciseRoutes.LIBRARY) },
            )
        }

        composable(ExerciseRoutes.LIBRARY) {
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
    }
}
