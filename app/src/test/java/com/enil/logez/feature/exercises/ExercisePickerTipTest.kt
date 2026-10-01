package com.enil.logez.feature.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.core.domain.repository.TipId
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeFirstRunStore
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.onboarding.TipsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g): the picker's one-row search tip is the list's first item on its first open
 * after setup, and not on a later open once seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExercisePickerTipTest {
    @get:Rule val rule = createComposeRule()

    private val tipText = "Search by name, or narrow the list with Equipment and Muscle."

    private val bench = Exercise(
        id = "ex-1", name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL,
        instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false,
        createdAt = 0, updatedAt = 0,
    )

    private fun setPicker(store: FakeFirstRunStore, open: () -> Boolean = { true }) {
        val tips = TipsViewModel(store, AppLogger.NoOp)
        val picker = ExercisePickerViewModel(FakeExerciseRepository(listOf(bench)), FakeWorkoutRepository())
        rule.setContent {
            LogEzTheme {
                if (open()) {
                    ExercisePickerSheet(
                        mode = ExercisePickerMode.ADD,
                        onDismiss = {},
                        onAddCommitted = {},
                        onExercisePicked = {},
                        onCreateExercise = {},
                        viewModel = picker,
                        tipsViewModel = tips,
                    )
                }
            }
        }
    }

    @Test
    fun `after setup the first open shows the tip above the list and marks it seen`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        setPicker(store)
        rule.waitForIdle()

        rule.onNodeWithText(tipText).assertIsDisplayed()
        rule.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        assertEquals(setOf(TipId.PICKER), store.seenTips)
    }

    @Test
    fun `a later open shows no tip once it has been seen`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        var open by mutableStateOf(true)
        setPicker(store) { open }
        rule.waitForIdle()
        rule.onNodeWithText(tipText).assertIsDisplayed()

        open = false
        rule.waitForIdle()
        open = true
        rule.waitForIdle()

        rule.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        rule.onNodeWithText(tipText).assertDoesNotExist()
    }

    @Test
    fun `a seen tip does not come back on the next open after the host is recreated with the picker open`() {
        // The hosts (logger, routine builder) open the picker from plain `remember` state, so after
        // the screen leaves and returns (to create an exercise, or rotation) the picker is closed,
        // but whatever it saved is still waiting for the next open.
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        val tips = TipsViewModel(store, AppLogger.NoOp)
        val picker = ExercisePickerViewModel(FakeExerciseRepository(listOf(bench)), FakeWorkoutRepository())
        val restoration = StateRestorationTester(rule)
        var openPicker: () -> Unit = {}
        restoration.setContent {
            LogEzTheme {
                var open by remember { mutableStateOf(false) }
                openPicker = { open = true }
                if (open) {
                    ExercisePickerSheet(
                        mode = ExercisePickerMode.ADD, onDismiss = {}, onAddCommitted = {}, onExercisePicked = {}, onCreateExercise = {},
                        viewModel = picker, tipsViewModel = tips,
                    )
                }
            }
        }
        rule.runOnIdle { openPicker() }
        rule.waitForIdle()
        rule.onNodeWithText(tipText).assertIsDisplayed()
        assertEquals(setOf(TipId.PICKER), store.seenTips)

        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Bench Press (Barbell)").assertDoesNotExist()
        rule.runOnIdle { openPicker() }
        rule.waitForIdle()

        rule.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        rule.onNodeWithText(tipText).assertDoesNotExist()
    }

    @Test
    fun `Got it removes the tip`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.SETUP)
        setPicker(store)
        rule.waitForIdle()
        // A semantics click: under Robolectric an injected click did not reach this button in the sheet's window.
        rule.onNodeWithText("Got it").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        rule.onNodeWithText(tipText).assertDoesNotExist()
        assertEquals(setOf(TipId.PICKER), store.seenTips)
    }

    @Test
    fun `an install that skipped setup never sees the tip`() {
        val store = FakeFirstRunStore(doneAt = 1L, storedPath = FirstRunPath.EXISTING)
        setPicker(store)
        rule.waitForIdle()

        rule.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        rule.onNodeWithText(tipText).assertDoesNotExist()
        assertEquals(emptySet<TipId>(), store.seenTips)
    }
}
