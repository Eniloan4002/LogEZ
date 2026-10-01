package com.enil.logez.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.TipId
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.history.WorkoutEditor
import com.enil.logez.feature.onboarding.TipsViewModel
import com.enil.logez.feature.workout.finish.LivePrDetector
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g) on the real logger screen: the "Logging tips" card is the list's last item,
 * so the superset auto-scroll (which passes an exercise's position in uiState.exercises straight to
 * the list) still lands on the right exercise, and adding the first exercises does not open the
 * list at its end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoggerTipsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val names = listOf("Exercise A", "Exercise B", "Exercise C", "Exercise D", "Exercise E")
    private val catalogue = names.mapIndexed { i, name ->
        Exercise(
            id = "ex-$i", name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = emptyList(), equipment = Equipment.DUMBBELL, instructions = "", mediaPath = null,
            isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
        )
    }
    private val tipsStore = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)

    /** The screen's lifecycle, so a test can leave it (as for Settings) and come back. */
    private val screen = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun setLogger(workoutRepo: FakeWorkoutRepository): WorkoutLoggerViewModel {
        val clock = FakeClock(currentMillis = 10_000L)
        val exerciseRepo = FakeExerciseRepository(catalogue)
        val settingsRepo = FakeSettingsRepository()
        val session = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), MainScope())
        val records = FakePersonalRecordsRepository()
        val vm = WorkoutLoggerViewModel(
            SavedStateHandle(mapOf("workoutId" to "w1")),
            workoutRepo, exerciseRepo, settingsRepo, session,
            SetCompletionUseCase(workoutRepo, settingsRepo, session, clock),
            LivePrDetector(workoutRepo, exerciseRepo, records, FakeMeasurementRepository(), clock),
            WorkoutEditor(workoutRepo, PersonalRecordsUpdater(workoutRepo, exerciseRepo, records, FakeMeasurementRepository(), settingsRepo), FakeTransactionRunner(), clock),
            FakeHealthMetricsSource(),
            clock,
        )
        rule.setContent {
            LogEzTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                    WorkoutLoggerScreen(
                        onExit = {}, onNavigateToFinish = {}, onDiscarded = {}, onExerciseClick = {}, onCreateExercise = {}, onSettingsClick = {},
                        viewModel = vm,
                        tipsViewModel = TipsViewModel(tipsStore, AppLogger.NoOp),
                    )
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun workout() = WorkoutEntity(
        id = "w1", routineId = null, title = "New Workout", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = 5_000L, endedAt = null, durationSeconds = 0, createdAt = 5_000L, updatedAt = 5_000L,
    )

    @Test
    fun `adding the first exercises opens the list at its top, with the tips card after them`() {
        val vm = setLogger(FakeWorkoutRepository(workouts = listOf(workout())))

        rule.runOnIdle { vm.addExercises(catalogue.take(4)) }
        rule.waitForIdle()

        rule.onNodeWithText("Exercise A").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToKey(LOGGER_TIP_KEY)
        rule.onNodeWithText("Logging tips").assertIsDisplayed()
        assertEquals(setOf(TipId.LOGGER), tipsStore.seenTips)
    }

    @Test
    fun `with the tips card showing, the superset auto-scroll lands on the other member`() {
        // Five exercises; A (position 0) and D (position 3) form a superset.
        val exercises = names.indices.map { i ->
            WorkoutExerciseEntity(
                id = "we$i", workoutId = "w1", exerciseId = "ex-$i", orderIndex = i,
                supersetGroup = if (i == 0 || i == 3) 1 else null, restTimerSeconds = null, notes = null,
            )
        }
        val sets = names.indices.map { i ->
            WorkoutSetEntity(
                id = "s$i", workoutExerciseId = "we$i", orderIndex = 0, setType = SetType.NORMAL, weightKg = 20.0, reps = 10,
                durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
            )
        }
        val vm = setLogger(FakeWorkoutRepository(workouts = listOf(workout()), exercises = exercises, sets = sets))
        rule.onNode(hasScrollAction()).performScrollToKey(LOGGER_TIP_KEY)
        rule.onNodeWithText("Logging tips").assertIsDisplayed()

        rule.runOnIdle { vm.toggleCheck("we0", "s0") }
        rule.waitForIdle()

        // D's card is scrolled to the top of the list (its name sits below the card's superset
        // chip and padding), with C above it off screen. Had the tip come first, position 3
        // would have been C.
        val listTop = rule.onNode(hasScrollAction()).getBoundsInRoot().top
        val dTop = rule.onNodeWithText("Exercise D").getBoundsInRoot().top
        assertTrue("D's name at $dTop, list top at $listTop", dTop - listTop < 100.dp)
        if (rule.onAllNodesWithText("Exercise C").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Exercise C").assertIsNotDisplayed()
        }
    }

    /** One exercise in the live workout, so the timer takes taps. */
    private fun oneExerciseRepo() = FakeWorkoutRepository(
        workouts = listOf(workout()),
        exercises = listOf(
            WorkoutExerciseEntity(id = "we0", workoutId = "w1", exerciseId = "ex-0", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
        ),
        sets = listOf(
            WorkoutSetEntity(
                id = "s0", workoutExerciseId = "we0", orderIndex = 0, setType = SetType.NORMAL, weightKg = 20.0, reps = 10,
                durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
            ),
        ),
    )

    /** The top bar's timer: one button whose text is the elapsed time ("0:00"). */
    private val timer = SemanticsMatcher("the timer button") { node ->
        node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("^\\d+:\\d{2}").containsMatchIn(it.text) }
    }

    @Test
    fun `in the real top bar, a tap 40dp above the timer's bottom edge opens the timer menu`() {
        setLogger(oneExerciseRepo())
        val bounds = rule.onNode(timer).getBoundsInRoot()
        assertTrue("the timer is ${bounds.bottom - bounds.top} tall", bounds.bottom - bounds.top < 40.dp)

        rule.onRoot().performTouchInput { click(Offset(((bounds.left + bounds.right) / 2).toPx(), (bounds.bottom - 40.dp).toPx())) }
        rule.waitForIdle()

        rule.onNodeWithText("Pause Workout Timer").assertIsDisplayed()
    }

    @Test
    fun `Show tips again in Settings brings the card back when the logger resumes`() {
        // An install that skipped setup (like the Owner's debug install): no tips at first.
        tipsStore.storedPath = FirstRunPath.EXISTING
        setLogger(oneExerciseRepo())
        rule.onNodeWithText("Exercise A").assertIsDisplayed()
        rule.onNodeWithText("Logging tips").assertDoesNotExist()

        // Settings opens over the logger, "Show tips again" runs there, then Back.
        rule.runOnIdle { screen.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle { tipsStore.reenabled = true }
        rule.runOnIdle { screen.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()

        rule.onNode(hasScrollAction()).performScrollToKey(LOGGER_TIP_KEY)
        rule.onNodeWithText("Logging tips").assertIsDisplayed()
    }
}
