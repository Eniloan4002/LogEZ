package com.enil.logez.feature.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run plan (O1g): the tips quote real menu, column and chip labels as fixed text. These
 * checks tie each quote to the live label string, so renaming a label without updating the tip
 * that names it fails here. (The tips' whole sentences are checked against fixed text in
 * LoggerTipsTest, ExercisePickerTipTest and RoutineBuilderTipTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TipLabelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    /** "W — Warm-up Set" is shown in the set menu; the tip quotes the part after the dash. */
    private fun menuName(id: Int) = s(id).substringAfter("— ")

    private fun assertQuotes(tipId: Int, label: String) {
        val tip = s(tipId)
        assertTrue("\"$tip\" should quote \"$label\"", label.isNotBlank() && tip.contains(label))
    }

    @Test
    fun `the set type tip quotes the set menu's own items`() {
        assertQuotes(R.string.tip_logger_set_type, menuName(R.string.set_type_warmup))
        assertQuotes(R.string.tip_logger_set_type, menuName(R.string.set_type_failure))
        assertQuotes(R.string.tip_logger_set_type, menuName(R.string.set_type_dropset))
        assertQuotes(R.string.tip_logger_set_type, s(R.string.action_delete))
    }

    @Test
    fun `the superset and timer tips quote their menu items`() {
        assertQuotes(R.string.tip_logger_superset, s(R.string.routine_builder_menu_add_to_superset))
        assertQuotes(R.string.tip_logger_timer, s(R.string.workout_pause_timer))
    }

    @Test
    fun `the PREVIOUS tip starts with the column's own heading`() {
        val heading = s(R.string.workout_col_previous)
        assertTrue(s(R.string.tip_logger_previous).startsWith("$heading "))
    }

    @Test
    fun `the builder tip quotes the REPS heading and the Rest Timer label`() {
        assertQuotes(R.string.tip_builder, s(R.string.routine_builder_col_reps))
        assertQuotes(R.string.tip_builder, context.getString(R.string.routine_builder_rest_timer_label, "").substringBefore(":"))
    }

    @Test
    fun `the picker tip quotes the Equipment and Muscle chips`() {
        assertQuotes(R.string.tip_picker, s(R.string.exercise_library_filter_equipment))
        assertQuotes(R.string.tip_picker, s(R.string.exercise_library_filter_muscle))
    }
}
