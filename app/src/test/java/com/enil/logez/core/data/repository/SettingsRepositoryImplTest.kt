package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.SetupChoices
import com.enil.logez.core.domain.model.StoredSetupValues
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real DataStore-backed repository over a real preferences file, for the first-run setup methods. */
class SettingsRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    private val seedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(tmp.root, "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun blocking(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun storedKeyNames(): Set<String> = dataStore.data.first().asMap().keys.map { it.name }.toSet()

    @Test
    fun `nothing is reported as stored on a fresh install`() = blocking {
        assertEquals(StoredSetupValues(), repository.readStoredSetupValues())
    }

    @Test
    fun `the seed marker alone is not a stored setup value`() = blocking {
        dataStore.edit { it[seedVersionKey] = 3 }
        assertEquals(StoredSetupValues(), repository.readStoredSetupValues())
    }

    @Test
    fun `each individually set value is reported, and only those`() = blocking {
        repository.setDistanceUnit(DistanceUnit.MILES)
        assertEquals(StoredSetupValues(distanceUnit = DistanceUnit.MILES), repository.readStoredSetupValues())

        repository.setWeightUnit(WeightUnit.KG)
        repository.setFirstDayOfWeek(DayOfWeek.SATURDAY)
        assertEquals(
            StoredSetupValues(WeightUnit.KG, DistanceUnit.MILES, DayOfWeek.SATURDAY),
            repository.readStoredSetupValues(),
        )
    }

    @Test
    fun `replaceAll stores every setup value, including ones equal to the defaults`() = blocking {
        repository.replaceAll(UserSettings())
        assertEquals(
            StoredSetupValues(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.MONDAY),
            repository.readStoredSetupValues(),
        )
    }

    @Test
    fun `a stored name this build does not know is reported as not stored, and left in place`() = blocking {
        val weightKey = androidx.datastore.preferences.core.stringPreferencesKey("weightUnit")
        dataStore.edit { it[weightKey] = "STONE" }

        assertEquals(StoredSetupValues(), repository.readStoredSetupValues())
        assertEquals("STONE", dataStore.data.first()[weightKey])
    }

    @Test
    fun `applySetupChoices writes exactly the four setup keys`() = blocking {
        dataStore.edit { it[seedVersionKey] = 3 }

        repository.applySetupChoices(SetupChoices(WeightUnit.LB, DistanceUnit.MILES, DayOfWeek.SUNDAY))

        assertEquals(
            setOf("lastAppliedSeedVersion", "weightUnit", "distanceUnit", "lengthUnit", "firstDayOfWeek"),
            storedKeyNames(),
        )
        val settings = repository.settings.first()
        assertEquals(WeightUnit.LB, settings.weightUnit)
        assertEquals(DistanceUnit.MILES, settings.distanceUnit)
        assertEquals(LengthUnit.IN, settings.lengthUnit)
        assertEquals(DayOfWeek.SUNDAY, settings.firstDayOfWeek)
        assertEquals(3, dataStore.data.first()[seedVersionKey])
    }

    @Test
    fun `applySetupChoices with kg sets body measurements to cm over a stored inches`() = blocking {
        repository.setLengthUnit(LengthUnit.IN)
        repository.setDefaultRestTimerSeconds(120)

        repository.applySetupChoices(SetupChoices(WeightUnit.KG, DistanceUnit.KM, DayOfWeek.MONDAY))

        val settings = repository.settings.first()
        assertEquals(LengthUnit.CM, settings.lengthUnit)
        // Everything else is left as it was.
        assertEquals(120, settings.defaultRestTimerSeconds)
    }

    // ---- P-211 effort scale ----

    @Test
    fun `an install from before the effort scale existed reads as RPE, tracking unchanged`() = blocking {
        dataStore.edit { it[androidx.datastore.preferences.core.booleanPreferencesKey("rpeTrackingEnabled")] = true }

        val settings = repository.settings.first()
        assertEquals(EffortScale.RPE, settings.effortScale)
        assertEquals(true, settings.rpeTrackingEnabled)
    }

    @Test
    fun `the effort scale persists under its own key and leaves tracking alone`() = blocking {
        repository.setEffortScale(EffortScale.RIR)

        assertEquals("RIR", dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("effortScale")])
        val settings = repository.settings.first()
        assertEquals(EffortScale.RIR, settings.effortScale)
        assertEquals(false, settings.rpeTrackingEnabled)
    }

    @Test
    fun `replaceAll writes the effort scale, including the RPE default over a stored RIR`() = blocking {
        repository.setEffortScale(EffortScale.RIR)

        repository.replaceAll(UserSettings())

        assertEquals(EffortScale.RPE, repository.settings.first().effortScale)
    }

    @Test
    fun `an effort scale name this build does not know falls back to RPE`() = blocking {
        dataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("effortScale")] = "BORG" }

        assertEquals(EffortScale.RPE, repository.settings.first().effortScale)
    }

    // ---- F9 pound plate set ----

    @Test
    fun `stored equipment from before the pound set keeps its kg values and reads the default pound set`() = blocking {
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("plateEquipment")] =
                """{"barsKg":[15.0,20.0],"platesKg":[0.5,25.0]}"""
        }

        val equipment = repository.settings.first().plateEquipment
        assertEquals(listOf(15.0, 20.0), equipment.barsKg)
        assertEquals(listOf(0.5, 25.0), equipment.platesKg)
        assertEquals(listOf(45.0), equipment.barsLb)
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0), equipment.platesLb)
    }

    @Test
    fun `custom pound equipment persists, and replaceAll writes it`() = blocking {
        val custom = PlateEquipment(barsLb = listOf(35.0, 45.0), platesLb = listOf(1.25, 45.0))
        repository.setPlateEquipment(custom)
        assertEquals(custom, repository.settings.first().plateEquipment)

        val restored = PlateEquipment(platesKg = listOf(25.0), barsLb = listOf(15.0), platesLb = listOf(10.0))
        repository.replaceAll(UserSettings(plateEquipment = restored))
        assertEquals(restored, repository.settings.first().plateEquipment)
    }
}
