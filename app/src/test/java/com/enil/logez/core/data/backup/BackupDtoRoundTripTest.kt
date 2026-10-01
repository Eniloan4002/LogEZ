package com.enil.logez.core.data.backup

import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every backed-up table must survive entity → DTO → JSON → DTO → entity unchanged. Anything that
 * does not is data the user silently loses on restore, which is the one operation where a silent
 * loss is unacceptable.
 *
 * Fixtures deliberately populate every nullable column and use multi-element enum lists, because
 * a round trip over all-null rows proves almost nothing.
 */
class BackupDtoRoundTripTest {
    private val write = BackupFormat.jsonWrite
    private val read = BackupFormat.jsonRead

    private inline fun <reified D> roundTrip(dto: D): D where D : Any =
        read.decodeFromString<D>(write.encodeToString(dto))

    @Test
    fun `exercise survives, including both enum lists`() {
        val entity = ExerciseEntity(
            id = "e1", name = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS,
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            equipment = Equipment.BARBELL, instructions = "1. Lie down\n2. **Brace**",
            mediaPath = "exercise_media/abc.jpg", isCustom = true,
            isBodyweightVolumeEligible = true, isDeleted = true,
            createdAt = 10, updatedAt = 20,
            muscleHeads = listOf(MuscleHead.UPPER_CHEST, MuscleHead.TRICEPS_LONG_HEAD),
            deprecatedPrimaryMuscleHead = null,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `a soft-deleted exercise keeps its flag, because restore needs the row for its foreign keys`() {
        val entity = ExerciseEntity(
            id = "e2", name = "Retired", exerciseType = ExerciseType.WEIGHT_REPS,
            primaryMuscleGroup = MuscleGroup.OTHER, secondaryMuscleGroups = emptyList(),
            equipment = Equipment.NONE, instructions = "", mediaPath = null, isCustom = false,
            isBodyweightVolumeEligible = false, isDeleted = true, createdAt = 1, updatedAt = 1,
            muscleHeads = emptyList(), deprecatedPrimaryMuscleHead = null,
        )
        assertTrue(roundTrip(entity.toDto()).toEntity().isDeleted)
    }

    @Test
    fun `routine folder survives`() {
        val entity = RoutineFolderEntity("f1", "Push", 3, 10, 20)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine survives, including its structure discriminator`() {
        val entity = RoutineEntity(
            id = "r1", folderId = "f1", name = "Push A", notes = "heavy",
            orderIndex = 2, createdAt = 10, updatedAt = 20, structure = WorkoutStructure.CIRCUIT,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine exercise survives`() {
        val entity = RoutineExerciseEntity("re1", "r1", "e1", 1, 2, 90, "pause at chest")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine set survives every target column`() {
        val entity = RoutineSetEntity(
            id = "rs1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.WARMUP,
            targetWeightKg = 60.5, targetReps = 8, targetRepRangeMin = 6, targetRepRangeMax = 10,
            targetDurationSeconds = 45, targetDistanceMeters = 1200.0,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `workout survives, including kind and structure`() {
        val entity = WorkoutEntity(
            id = "w1", routineId = "r1", title = "Push Day", notes = "felt strong, back tight",
            status = WorkoutStatus.COMPLETED, startedAt = 100, endedAt = 200, durationSeconds = 3600,
            createdAt = 100, updatedAt = 200, structure = WorkoutStructure.CIRCUIT,
            kind = WorkoutKind.GPS_TRACKED,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `workout exercise survives`() {
        val entity = WorkoutExerciseEntity("we1", "w1", "e1", 0, 1, 120, "notes")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `a countdown workout exercise survives, and a mode this app does not know is kept verbatim`() {
        val countdown = WorkoutExerciseEntity("we1", "w1", "e1", 0, 1, 120, "notes", timerMode = "COUNTDOWN")
        assertEquals(countdown, roundTrip(countdown.toDto()).toEntity())
        // A newer app's mode must come back exactly as written, never coerced to a stopwatch (null).
        val future = WorkoutExerciseEntity("we2", "w1", "e1", 1, null, null, null, timerMode = "INTERVALS")
        assertEquals(future, roundTrip(future.toDto()).toEntity())
    }

    @Test
    fun `a workout exercise from a backup made before timer modes existed restores as a stopwatch`() {
        val oldLine = """{"id":"we1","workout_id":"w1","exercise_id":"e1","order_index":0,"superset_group":1,"rest_timer_seconds":120,"notes":"n"}"""
        val restored = BackupFormat.jsonRead.decodeFromString(WorkoutExerciseDto.serializer(), oldLine).toEntity()
        assertEquals(null, restored.timerMode)
        assertEquals(120, restored.restTimerSeconds)
    }

    @Test
    fun `workout set survives every metric, including the one the CSV drops`() {
        val entity = WorkoutSetEntity(
            id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.DROPSET,
            weightKg = 102.5, reps = 5, durationSeconds = 60, distanceMeters = 5250.0,
            rpe = 8.5, customMetric = 42.0, isCompleted = true, completedAt = 300,
        )
        // customMetric has no CSV column, which is exactly why the JSON backup has to carry it.
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `activity track survives, so a GPS route is not lost on restore`() {
        val entity = ActivityTrackEntity("t1", "ws1", "_p~iF~ps|U_ulLnnqC", 412, 4.5, routeTimes = "AEE")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `an activity track's pause ranges survive`() {
        val entity = ActivityTrackEntity("t1", "ws1", "_p~iF~ps|U_ulLnnqC", 412, 4.5, routeTimes = "AEE", pauseRanges = "BCD")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `an activity track from a backup made before pauses existed restores as a run that was never paused`() {
        val oldLine = """{"id":"t1","workout_set_id":"ws1","route_polyline":"abc","point_count":3,"avg_accuracy_m":4.5,"route_times":"AEE"}"""
        val restored = BackupFormat.jsonRead.decodeFromString(ActivityTrackDto.serializer(), oldLine).toEntity()
        assertEquals(null, restored.pauseRanges)
        assertEquals("AEE", restored.routeTimes)
    }

    @Test
    fun `an activity track from a backup made before route times existed restores with none`() {
        val oldLine = """{"id":"t1","workout_set_id":"ws1","route_polyline":"abc","point_count":3,"avg_accuracy_m":4.5}"""
        val restored = BackupFormat.jsonRead.decodeFromString(ActivityTrackDto.serializer(), oldLine).toEntity()
        assertEquals(null, restored.routeTimes)
        assertEquals("abc", restored.routePolyline)
    }

    @Test
    fun `heart rate sample survives`() {
        val entity = WorkoutHeartRateSampleEntity("hr1", "w1", 150, 132L)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `body measurement survives all seventeen metrics`() {
        val entity = BodyMeasurementEntity(
            date = "2026-09-22", weightKg = 82.4, leanMassKg = 65.1, fatPercent = 18.2,
            neckCm = 38.0, shoulderCm = 120.0, chestCm = 102.0, leftBicepCm = 36.5,
            rightBicepCm = 36.8, leftForearmCm = 29.0, rightForearmCm = 29.2, abdomenCm = 84.0,
            waistCm = 80.0, hipsCm = 96.0, leftThighCm = 58.0, rightThighCm = 58.4,
            leftCalfCm = 38.0, rightCalfCm = 38.2, updatedAt = 500,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `progress photo survives with its filesDir-relative path`() {
        val entity = ProgressPhotoEntity("p1", "2026-09-22", "progress_photos/abc.jpg", 600)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `goal survives, so a restore does not silently drop the user's targets`() {
        val entity = GoalDefinitionEntity("g1", GoalMetric.WORKOUT_COUNT, GoalPeriod.WEEKLY, 4.0, 10, 20)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `daily wellness total survives`() {
        val entity = DailyWellnessTotalEntity("2026-09-22", 9312L, 512.5, 700)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `column names are the wire format, so renaming a Kotlin field cannot break old backups`() {
        val json = write.encodeToString(
            WorkoutSetEntity(
                id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL,
                weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null,
                rpe = null, customMetric = null, isCompleted = true, completedAt = 1,
            ).toDto(),
        )
        assertTrue(json, json.contains("\"workout_exercise_id\""))
        assertTrue(json, json.contains("\"weight_kg\""))
        assertTrue(json, json.contains("\"is_completed\""))
    }

    @Test
    fun `a backup missing a column this app knows about still decodes`() {
        // The forward-compatibility case: a backup written before `kind` existed.
        val old = """{"id":"w1","title":"Legacy","status":"COMPLETED","started_at":5}"""
        val entity = read.decodeFromString<WorkoutDto>(old).toEntity()
        assertEquals("w1", entity.id)
        assertEquals(WorkoutKind.STRENGTH, entity.kind)
        assertEquals(WorkoutStructure.REGULAR, entity.structure)
    }

    @Test
    fun `a backup carrying a column this app does not know about still decodes`() {
        val future = """{"id":"f1","name":"Folder","order_index":0,"created_at":1,"updated_at":2,"colour":"red"}"""
        assertEquals("Folder", read.decodeFromString<RoutineFolderDto>(future).toEntity().name)
    }

    // ---- Settings enums (first-run plan O1d, F4) ----

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `settings with every non-default enum survive, so known names round-trip`() {
        val settings = UserSettings(
            weightUnit = WeightUnit.LB,
            distanceUnit = DistanceUnit.MILES,
            lengthUnit = LengthUnit.IN,
            muscleDiagramVariant = MuscleDiagramVariant.FEMALE,
            firstDayOfWeek = DayOfWeek.SUNDAY,
            perExerciseUnitOverrides = mapOf("e1" to WeightUnit.KG, "e2" to WeightUnit.LB),
            previousValuesMode = PreviousValuesMode.SAME_ROUTINE,
            measurementsTrackingMode = MeasurementsTrackingMode.SIMPLIFIED,
            effortScale = EffortScale.RIR,
        )
        assertEquals(settings, roundTrip(settings.toDto()).toUserSettings())
    }

    @Test
    fun `the effort scale travels as effort_scale, by name`() {
        val json = write.encodeToString(UserSettings(effortScale = EffortScale.RIR).toDto())
        assertTrue(json, json.contains("\"effort_scale\":\"RIR\""))
    }

    @Test
    fun `a backup from before effort_scale existed restores as RPE`() {
        val older = """{"weight_unit":"LB","rpe_tracking_enabled":true}"""
        val settings = read.decodeFromString<SettingsDto>(older).toUserSettings()
        assertEquals(EffortScale.RPE, settings.effortScale)
        assertTrue(settings.rpeTrackingEnabled)
    }

    // ---- F9: the pound plate set ----

    @Test
    fun `custom kg and pound plate equipment both survive a backup`() {
        val settings = UserSettings(
            weightUnit = WeightUnit.LB,
            plateEquipment = PlateEquipment(
                barsKg = listOf(15.0, 20.0),
                platesKg = listOf(0.5, 25.0),
                barsLb = listOf(35.0, 45.0),
                platesLb = listOf(1.25, 2.5, 45.0),
            ),
        )
        assertEquals(settings, roundTrip(settings.toDto()).toUserSettings())
    }

    @Test
    fun `the pound set travels inside plate_equipment as barsLb and platesLb`() {
        val json = write.encodeToString(UserSettings().toDto())
        val plateEquipment = Json.parseToJsonElement(json).jsonObject.getValue("plate_equipment").jsonObject
        assertEquals("[45.0]", plateEquipment.getValue("barsLb").toString())
        assertEquals("[2.5,5.0,10.0,25.0,35.0,45.0]", plateEquipment.getValue("platesLb").toString())
    }

    @Test
    fun `a backup from before the pound set restores its kg equipment and the default pound set`() {
        val older = """{"weight_unit":"LB","plate_equipment":{"barsKg":[15.0,20.0],"platesKg":[0.5,25.0]}}"""
        val settings = read.decodeFromString<SettingsDto>(older).toUserSettings()
        assertEquals(listOf(15.0, 20.0), settings.plateEquipment.barsKg)
        assertEquals(listOf(0.5, 25.0), settings.plateEquipment.platesKg)
        assertEquals(listOf(45.0), settings.plateEquipment.barsLb)
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0), settings.plateEquipment.platesLb)
    }

    @Test
    fun `a settings enum name this build does not know is refused, never coerced`() {
        val cases = listOf(
            "weight_unit" to SettingsDto(weightUnit = "STONE"),
            "distance_unit" to SettingsDto(distanceUnit = "LEAGUES"),
            "length_unit" to SettingsDto(lengthUnit = "HANDS"),
            "muscle_diagram_variant" to SettingsDto(muscleDiagramVariant = "OTHER"),
            "first_day_of_week" to SettingsDto(firstDayOfWeek = "FUNDAY"),
            "per_exercise_unit_overrides" to SettingsDto(perExerciseUnitOverrides = mapOf("e1" to "STONE")),
            "previous_values_mode" to SettingsDto(previousValuesMode = "SAME_WEEKDAY"),
            "measurements_tracking_mode" to SettingsDto(measurementsTrackingMode = "MINIMAL"),
            "effort_scale" to SettingsDto(effortScale = "BORG"),
        )
        cases.forEach { (field, dto) ->
            val e = assertThrows(UnknownSettingValueException::class.java) { dto.toUserSettings() }
            assertEquals(field, e.field)
        }
    }

    private fun archive(settingsJson: String?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
            zip.write(write.encodeToString(BackupManifest(roomSchemaVersion = BackupWriter.ROOM_SCHEMA_VERSION)).toByteArray())
            zip.closeEntry()
            if (settingsJson != null) {
                zip.putNextEntry(ZipEntry(BackupFormat.SETTINGS_ENTRY))
                zip.write(settingsJson.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun stage(settingsJson: String?): StagedBackup =
        BackupReader().stage(ByteArrayInputStream(archive(settingsJson)), temp.newFolder())

    @Test
    fun `staging treats an unknown settings enum name as too new, before anything is restored`() {
        val staged = stage("""{"weight_unit":"STONE"}""")
        assertTrue(staged.hasSettings)
        assertTrue(staged.settingsTooNew)
        assertEquals(BackupManifest.Compatibility.TooNew, staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION))
    }

    @Test
    fun `staging accepts settings whose names this build knows`() {
        val staged = stage(write.encodeToString(UserSettings(weightUnit = WeightUnit.LB).toDto()))
        assertTrue(staged.hasSettings)
        assertFalse(staged.settingsTooNew)
        assertEquals(BackupManifest.Compatibility.Ok, staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION))
    }

    @Test
    fun `staging treats an unknown effort scale as too new, like the other enums`() {
        val json = write.encodeToString(UserSettings(effortScale = EffortScale.RIR).toDto())
            .replace("\"effort_scale\":\"RIR\"", "\"effort_scale\":\"BORG\"")
        val staged = stage(json)
        assertTrue(staged.settingsTooNew)
        assertEquals(BackupManifest.Compatibility.TooNew, staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION))
    }

    @Test
    fun `staging accepts an RIR effort scale, and it reads back as RIR`() {
        val dir = temp.newFolder()
        val json = write.encodeToString(UserSettings(effortScale = EffortScale.RIR).toDto())
        val staged = BackupReader().stage(ByteArrayInputStream(archive(json)), dir)
        assertFalse(staged.settingsTooNew)
        assertEquals(BackupManifest.Compatibility.Ok, staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION))
        assertEquals(EffortScale.RIR, BackupReader().stagedSettings(dir)?.toUserSettings()?.effortScale)
    }

    @Test
    fun `staging notes a backup without settings, which is not too new`() {
        val staged = stage(settingsJson = null)
        assertFalse(staged.hasSettings)
        assertFalse(staged.settingsTooNew)
        assertEquals(BackupManifest.Compatibility.Ok, staged.compatibility(BackupWriter.ROOM_SCHEMA_VERSION))
    }
}
