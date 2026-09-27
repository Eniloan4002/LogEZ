package com.enil.logez.core.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.RegionDefaults
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.data.repository.SharedPrefsFirstRunStore
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.FirstRunPath
import com.enil.logez.fakes.FakeRegionDefaults
import com.enil.logez.fakes.FakeSettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Delete all data's settings step (first-run plan, Decision 7): the units and week start take the
 * region's suggestion, everything else its default, and only the first-run keys leave
 * `logez_ui_flags`, so setup shows at the next cold start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EraserSettingsResetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uiFlags get() = context.getSharedPreferences("logez_ui_flags", Context.MODE_PRIVATE)

    private class RecordingLogger : AppLogger {
        val messages = mutableListOf<String>()
        override fun e(tag: String, message: String, throwable: Throwable?) {
            messages += message
        }
    }

    private val logger = RecordingLogger()
    private val usRegion = FakeRegionDefaults(
        RegionSuggestion(
            weightUnit = WeightUnit.LB,
            distanceUnit = DistanceUnit.MILES,
            lengthUnit = LengthUnit.IN,
            firstDayOfWeek = DayOfWeek.SUNDAY,
            weekStartClamped = false,
        ),
    )

    @Before
    fun clearFlags() {
        uiFlags.edit().clear().commit()
    }

    @Test
    fun `settings go back to their defaults with the region's units and week start`() = runTest {
        val settings = FakeSettingsRepository(
            UserSettings(defaultRestTimerSeconds = 240, keepAwake = false, weightUnit = WeightUnit.KG),
        )

        resetSettingsForFirstRun(settings, usRegion, SharedPrefsFirstRunStore(context), logger)

        assertEquals(
            UserSettings().copy(
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.MILES,
                lengthUnit = LengthUnit.IN,
                firstDayOfWeek = DayOfWeek.SUNDAY,
            ),
            settings.settings.value,
        )
        // Stored, so the setup screen that follows preselects them.
        assertEquals(
            StoredSetupValues(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY),
            settings.readStoredSetupValues(),
        )
        assertEquals(emptyList<String>(), logger.messages)
    }

    @Test
    fun `a clamped region week start is stored as Monday`() = runTest {
        val settings = FakeSettingsRepository()
        val fridayRegion = FakeRegionDefaults(
            RegionSuggestion(
                weightUnit = WeightUnit.KG,
                distanceUnit = DistanceUnit.KM,
                lengthUnit = LengthUnit.CM,
                firstDayOfWeek = DayOfWeek.MONDAY,
                weekStartClamped = true,
            ),
        )

        resetSettingsForFirstRun(settings, fridayRegion, SharedPrefsFirstRunStore(context), logger)

        assertEquals(DayOfWeek.MONDAY, settings.settings.value.firstDayOfWeek)
    }

    @Test
    fun `only the first-run keys are removed, so the tips and the notification decline stay`() = runTest {
        val store = SharedPrefsFirstRunStore(context)
        assertTrue(store.markDone(FirstRunPath.SETUP, 1_767_603_600_000L))
        uiFlags.edit()
            .putBoolean("notification_prompt_declined", true)
            .putBoolean("tip_seen_picker", true)
            .commit()

        resetSettingsForFirstRun(FakeSettingsRepository(), usRegion, store, logger)

        assertFalse(SharedPrefsFirstRunStore(context).isDone())
        assertFalse(uiFlags.contains("first_run_done_at"))
        assertFalse(uiFlags.contains("first_run_path"))
        assertTrue(uiFlags.getBoolean("notification_prompt_declined", false))
        assertTrue(uiFlags.getBoolean("tip_seen_picker", false))
    }

    @Test
    fun `a region lookup that throws still resets every setting, to the plain defaults`() = runTest {
        val store = SharedPrefsFirstRunStore(context)
        assertTrue(store.markDone(FirstRunPath.EXISTING, 1_767_603_600_000L))
        val settings = FakeSettingsRepository(
            UserSettings(weightUnit = WeightUnit.LB, distanceUnit = DistanceUnit.MILES, defaultRestTimerSeconds = 240),
        )
        val broken = object : RegionDefaults {
            override fun suggest(): RegionSuggestion = throw IllegalStateException("no locale")
        }

        resetSettingsForFirstRun(settings, broken, store, logger)

        assertEquals(listOf("Region lookup after the wipe failed; using the plain defaults"), logger.messages)
        assertEquals(UserSettings(), settings.settings.value)
        assertEquals(WeightUnit.KG, settings.settings.value.weightUnit)
        assertEquals(DayOfWeek.MONDAY, settings.settings.value.firstDayOfWeek)
        assertFalse(store.isDone())
    }
}
