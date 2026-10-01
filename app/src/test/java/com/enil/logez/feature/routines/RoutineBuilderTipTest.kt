package com.enil.logez.feature.routines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.TipId
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.feature.onboarding.TipsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First-run plan (O1g): the routine builder's tip follows the exercise cards once there is one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineBuilderTipTest {
    @get:Rule val rule = createComposeRule()

    private val tipText = "Tap REPS to switch between a rep target and a rep range. Rest Timer sets the rest for this exercise only."

    private val bench = Exercise(
        id = "ex-1", name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL,
        instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false,
        createdAt = 0, updatedAt = 0,
    )

    private val plank = bench.copy(id = "ex-2", name = "Plank", exerciseType = ExerciseType.DURATION, equipment = Equipment.NONE)

    private fun setBuilder(store: FakeFirstRunStore): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            SavedStateHandle(),
            FakeRoutineRepository(),
            FakeExerciseRepository(listOf(bench, plank)),
            FakeSettingsRepository(),
            FakeClock(currentMillis = 5_000L),
        )
        rule.setContent {
            LogEzTheme {
                RoutineBuilderScreen(
                    onBack = {}, onSaved = {}, onExerciseClick = {}, onCreateExercise = {},
                    viewModel = vm,
                    tipsViewModel = TipsViewModel(store, AppLogger.NoOp),
                )
            }
        }
        return vm
    }

    @Test
    fun `the tip waits for the first exercise, then follows the cards and is marked seen`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val vm = setBuilder(store)
        rule.waitForIdle()
        rule.onNodeWithText(tipText).assertDoesNotExist()
        assertEquals(emptySet<TipId>(), store.seenTips)

        rule.runOnIdle { vm.addExercises(listOf(bench)) }
        rule.waitForIdle()
        // A sliver of the tip is not "seen" (OneTimeTipTest), so scroll it fully into view.
        rule.onNode(hasScrollAction()).performScrollToKey("tip_builder")

        rule.onNodeWithText(tipText).assertIsDisplayed()
        assertEquals(setOf(TipId.BUILDER), store.seenTips)
    }

    @Test
    fun `Got it removes the builder tip`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val vm = setBuilder(store)
        rule.runOnIdle { vm.addExercises(listOf(bench)) }
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToKey("tip_builder")

        rule.onNodeWithText("Got it").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(tipText).assertDoesNotExist()
    }

    @Test
    fun `a timed first exercise has no REPS heading, so the tip waits for one that has`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val vm = setBuilder(store)
        rule.runOnIdle { vm.addExercises(listOf(plank)) }
        rule.waitForIdle()

        rule.onNodeWithText("Plank").assertIsDisplayed()
        // Not merely off screen: the list has no tip item to scroll to.
        val scrolledToTip = runCatching { rule.onNode(hasScrollAction()).performScrollToKey("tip_builder") }.isSuccess
        assertFalse(scrolledToTip)
        rule.onNodeWithText(tipText).assertDoesNotExist()
        assertEquals(emptySet<TipId>(), store.seenTips)

        rule.runOnIdle { vm.addExercises(listOf(bench)) }
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToKey("tip_builder")

        rule.onNodeWithText(tipText).assertIsDisplayed()
        assertEquals(setOf(TipId.BUILDER), store.seenTips)
    }

    @Test
    fun `an install that skipped setup gets no builder tip`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        val vm = setBuilder(store)
        rule.runOnIdle { vm.addExercises(listOf(bench)) }
        rule.waitForIdle()

        rule.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        rule.onNodeWithText(tipText).assertDoesNotExist()
    }
}
