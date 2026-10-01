package com.enil.logez.feature.analytics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The redesigned Profile tab's pieces (docs/mockups/profile-2026-10-01), one composable at a time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class ProfileComposablesTest {
    @get:Rule val rule = createComposeRule()

    private val monday = LocalDate.of(2026, 8, 17)

    private fun week(activeDays: Int, records: Int, sets: Int = 7, volume: Double = 2460.0) = ProfileWeek(
        start = monday,
        activeDays = activeDays,
        targetDays = 4,
        days = (0L..6L).map { WeekDay(monday.plusDays(it), if (it < activeDays) WeekDayMark.TRAINED else WeekDayMark.UPCOMING) },
        todayIndex = 5,
        volumeKgSoFar = volume,
        setsSoFar = sets,
        recordsThisWeek = records,
    )

    private fun show(content: @Composable (ProfileStyles) -> Unit) {
        rule.setContent { LogEzTheme { content(rememberProfileStyles()) } }
    }

    private fun tile(key: String, value: String, label: String, supporting: String) = ScoreTileSpec(
        key = key,
        value = AnnotatedString(value),
        label = label,
        supporting = AnnotatedString(supporting),
        progress = null,
        description = "$label, $value",
        onClick = {},
    )

    private val fourTiles = listOf(
        tile("week_streak", "13", "Week streak", "Longest 13"),
        tile("day_streak", "3", "Day streak", "Longest 9"),
        tile("workouts", "48", "Workouts", "Since 6 Jul"),
        tile("achievements", "5/17", "Achievements", "Next: 50 Workouts"),
    )

    // --- This week ---

    @Test
    fun `this week shows the day count, the target, the records and the totals so far`() {
        show { styles ->
            ThisWeekCard(week(activeDays = 3, records = 2, sets = 35, volume = 21275.0), true, WeightUnit.KG, styles, loading = false, onClick = {})
        }
        rule.onNodeWithText("This week".uppercase()).assertExists()
        rule.onNodeWithText("3").assertExists()
        rule.onNodeWithText("of 4 days").assertExists()
        rule.onNodeWithText("2 records").assertExists()
        rule.onNodeWithText("So far").assertExists()
        rule.onNodeWithText("21,275 kg").assertExists()
        rule.onNodeWithText("35 sets").assertExists()
    }

    @Test
    fun `records are singular for one and absent for none`() {
        show { styles -> ThisWeekCard(week(activeDays = 1, records = 1), true, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onNodeWithText("1 record").assertExists()
        rule.onNodeWithText("1 records").assertDoesNotExist()
    }

    @Test
    fun `no records means no records line`() {
        show { styles -> ThisWeekCard(week(activeDays = 1, records = 0), true, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onAllNodesWithText("record", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a new user sees one plain line instead of totals`() {
        show { styles -> ThisWeekCard(week(0, 0, sets = 0, volume = 0.0), false, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onNodeWithText("Finish a workout to fill in today. Your streaks, records and totals start from there.").assertExists()
        rule.onNodeWithText("So far").assertDoesNotExist()
    }

    @Test
    fun `while loading no figure is drawn as data`() {
        show { styles -> ThisWeekCard(week(3, 2), true, WeightUnit.KG, styles, loading = true, onClick = {}) }
        // The placeholder text exists only to reserve space: hidden from accessibility and drawn transparent.
        rule.onAllNodesWithText("2 records").assertCountEquals(0)
    }

    @Test
    fun `tapping this week opens its target`() {
        var clicks = 0
        show { styles -> ThisWeekCard(week(3, 0), true, WeightUnit.KG, styles, loading = false, onClick = { clicks++ }) }
        rule.onNodeWithText("of 4 days").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `reaching the target says so, and going past it says by how much`() {
        show { styles -> ThisWeekCard(week(activeDays = 4, records = 0), true, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onNodeWithText("of 4 days \u00B7 target met").assertExists()
    }

    @Test
    fun `training more days than the target never reads 5 of 4`() {
        show { styles -> ThisWeekCard(week(activeDays = 5, records = 0), true, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onNodeWithText("days \u00B7 target 4 met").assertExists()
        rule.onNodeWithText("of 4 days", substring = true).assertDoesNotExist()
    }

    @Test
    fun `this week is one button for TalkBack`() {
        show { styles -> ThisWeekCard(week(3, 0), true, WeightUnit.KG, styles, loading = false, onClick = {}) }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertExists()
    }

    @Test
    fun `a placeholder's styled figures are drawn transparent`() {
        val styled = androidx.compose.ui.text.buildAnnotatedString {
            append("00")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Red))
            append("/17")
            pop()
        }
        val hidden = styled.hiddenIf(true)
        assertEquals("00/17", hidden.text)
        assertTrue(hidden.spanStyles.isNotEmpty())
        assertTrue(hidden.spanStyles.all { it.item.color == androidx.compose.ui.graphics.Color.Transparent })
        assertEquals(styled, styled.hiddenIf(false))
    }

    // --- Scorecards ---

    @Test
    fun `the scorecards sit two to a row at the default text size`() {
        show { styles -> ScoreTileGrid(fourTiles, styles, loading = false, modifier = Modifier.width(412.dp)) }
        val weekTop = rule.onNodeWithContentDescription("Week streak, 13").getUnclippedBoundsInRoot().top
        val dayTop = rule.onNodeWithContentDescription("Day streak, 3").getUnclippedBoundsInRoot().top
        val workoutsTop = rule.onNodeWithContentDescription("Workouts, 48").getUnclippedBoundsInRoot().top
        assertEquals(weekTop, dayTop)
        assertTrue(workoutsTop > weekTop)
    }

    // Robolectric's text layout does not return real glyph widths, so the font-size-dependent decision
    // ("does every value and label word fit a column?") is checked on the device at 1.0x, 1.3x and 2.0x
    // (docs/verification/profile-2026-10-01). What is tested here is the layout each answer produces.

    @Test
    fun `when the items fit a column the grid lays them two to a row`() {
        show {
            MeasuredTwoColumnGrid(listOf("a", "b", "c"), itemKey = { it }, gap = 12.dp, fitsInColumn = { _, _, _ -> true }, modifier = Modifier.width(412.dp)) { item, single, m ->
                androidx.compose.material3.Text(item, modifier = m.testTag(item).then(Modifier.width(if (single) 400.dp else 100.dp)))
            }
        }
        val a = rule.onNodeWithTag("a").getUnclippedBoundsInRoot()
        val b = rule.onNodeWithTag("b").getUnclippedBoundsInRoot()
        val c = rule.onNodeWithTag("c").getUnclippedBoundsInRoot()
        assertEquals(a.top, b.top)
        assertTrue(b.left > a.left)
        assertTrue(c.top > a.top)
        // A trailing odd item keeps one column's width rather than stretching.
        assertTrue(c.width < 206.dp)
    }

    @Test
    fun `when any item does not fit a column the grid lays out one full-width row each`() {
        show {
            MeasuredTwoColumnGrid(listOf("a", "b", "c"), itemKey = { it }, gap = 12.dp, fitsInColumn = { item, _, _ -> item != "b" }, modifier = Modifier.width(412.dp)) { item, _, m ->
                androidx.compose.material3.Text(item, modifier = m.testTag(item))
            }
        }
        val a = rule.onNodeWithTag("a").getUnclippedBoundsInRoot()
        val b = rule.onNodeWithTag("b").getUnclippedBoundsInRoot()
        assertTrue(b.top > a.top)
        assertEquals(a.left, b.left)
        assertEquals(412.dp, a.width)
    }

    @Test
    fun `a single tile fills the width instead of leaving half a row empty`() {
        show { styles ->
            ScoreTileGrid(
                listOf(tile("achievements", "0/17", "Achievements", "Next: First Workout")), styles, loading = false,
                modifier = Modifier.width(412.dp),
            )
        }
        assertEquals(412.dp, rule.onNodeWithContentDescription("Achievements, 0/17").getUnclippedBoundsInRoot().width)
    }

    @Test
    fun `a tile is one button for TalkBack`() {
        show { styles -> ScoreTileGrid(fourTiles, styles, loading = false, modifier = Modifier.width(412.dp)) }
        rule.onNodeWithContentDescription("Week streak, 13")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `the year shows on Since only when the first workout was in an earlier year than today`() {
        // Friday 1 Jan 2027: the week started Monday 28 Dec 2026, so its start's year is last year's.
        val newYearsDay = LocalDate.of(2027, 1, 1)
        val weekFromDecember = week(1, 0).copy(start = newYearsDay.minusDays(4), todayIndex = 4)
        fun since(first: LocalDate): String {
            val state = ProfileUiState(isLoading = false, workoutCount = 5, firstWorkoutDate = first, week = weekFromDecember)
            var description = ""
            show { styles ->
                val tiles = rememberScoreTiles(state, styles, {}, {}, {})
                description = tiles.first { it.key == TILE_WORKOUTS }.description
            }
            rule.waitForIdle()
            return description
        }
        assertEquals("Workouts, 5. Since 30 Dec 2026.", since(LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun `a first workout earlier today has no year on Since`() {
        val state = ProfileUiState(
            isLoading = false, workoutCount = 1, firstWorkoutDate = LocalDate.of(2027, 1, 1),
            week = week(1, 0).copy(start = LocalDate.of(2026, 12, 28), todayIndex = 4),
        )
        var description = ""
        show { styles -> description = rememberScoreTiles(state, styles, {}, {}, {}).first { it.key == TILE_WORKOUTS }.description }
        rule.waitForIdle()
        assertEquals("Workout, 1. Since 1 Jan.", description)
    }

    @Test
    fun `scorecards invoke their own click`() {
        var clicked = ""
        val tiles = fourTiles.map { it.copy(onClick = { clicked = it.key }) }
        show { styles -> ScoreTileGrid(tiles, styles, loading = false, modifier = Modifier.width(412.dp)) }
        rule.onNodeWithContentDescription("Achievements, 5/17").performClick()
        assertEquals("achievements", clicked)
    }

    // --- Destinations ---

    @Test
    fun `the six destinations all show, with the latest weight under Measurements`() {
        show { styles ->
            val destinations = rememberDestinations(
                latestWeight = LatestWeight(78.4, LocalDate.of(2026, 9, 27)), weightUnit = WeightUnit.KG, styles = styles,
                onStatistics = {}, onAchievements = {}, onCalendar = {}, onMeasurements = {}, onExercises = {}, onSettings = {},
            )
            ProfileDestinationGrid(destinations, styles, Modifier.fillMaxWidth())
        }
        listOf("Statistics", "Achievements", "Calendar", "Measurements", "Exercises", "Settings").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        rule.onNodeWithText("78.4 kg · 27 Sep").assertExists()
    }

    @Test
    fun `a destination is one button for TalkBack`() {
        show { styles ->
            val destinations = rememberDestinations(
                latestWeight = null, weightUnit = WeightUnit.KG, styles = styles,
                onStatistics = {}, onAchievements = {}, onCalendar = {}, onMeasurements = {}, onExercises = {}, onSettings = {},
            )
            ProfileDestinationGrid(destinations, styles, Modifier.fillMaxWidth())
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasText("Statistics")).assertExists()
    }

    @Test
    fun `without a logged weight Measurements has no second line`() {
        show { styles ->
            val destinations = rememberDestinations(
                latestWeight = null, weightUnit = WeightUnit.KG, styles = styles,
                onStatistics = {}, onAchievements = {}, onCalendar = {}, onMeasurements = {}, onExercises = {}, onSettings = {},
            )
            ProfileDestinationGrid(destinations, styles, Modifier.fillMaxWidth())
        }
        rule.onAllNodesWithText("kg", substring = true).assertCountEquals(0)
    }

    // --- Chart ---

    private fun chartState(metric: TrainingMetric): ProfileUiState {
        val bars = listOf(DashboardAggregator.WeeklyBar(monday.minusWeeks(1), 3.0), DashboardAggregator.WeeklyBar(monday, 2.0))
        return ProfileUiState(isLoading = false, workoutCount = 5, week = week(2, 0), quickCharts = mapOf(metric to bars))
    }

    @Test
    fun `the chart names what the bars count and switches metric from its chips`() {
        var picked: TrainingMetric? = null
        show { styles ->
            ProfileChartCard(chartState(TrainingMetric.VOLUME), TrainingMetric.VOLUME, styles, { picked = it }, {})
        }
        rule.onNodeWithText("Volume per week".uppercase()).assertExists()
        rule.onNodeWithText("REPS").performClick()
        assertEquals(TrainingMetric.REPS, picked)
    }

    @Test
    fun `the this-week-so-far key shows only while the series ends on the current week`() {
        show { styles -> ProfileChartCard(chartState(TrainingMetric.FREQUENCY), TrainingMetric.FREQUENCY, styles, {}, {}) }
        rule.onNodeWithText("This week so far").assertExists()
    }

    @Test
    fun `an empty series says so honestly`() {
        show { styles -> ProfileChartCard(ProfileUiState(isLoading = false, workoutCount = 5, week = week(0, 0)), TrainingMetric.FREQUENCY, styles, {}, {}) }
        rule.onNodeWithText("No workouts in this period").assertExists()
        rule.onNodeWithText("This week so far").assertDoesNotExist()
    }

    @Test
    fun `the Statistics link opens the selected metric`() {
        var opened: TrainingMetric? = null
        show { styles -> ProfileChartCard(chartState(TrainingMetric.DURATION), TrainingMetric.DURATION, styles, {}, { opened = it }) }
        rule.onNodeWithText("Statistics").performClick()
        assertEquals(TrainingMetric.DURATION, opened)
    }

    // --- Health Connect, all states ---

    private fun health(
        availability: HealthConnectAvailability = HealthConnectAvailability.Available,
        granted: Set<HealthDataType> = emptySet(),
        refused: Boolean = false,
        steps: Long? = null,
        calories: Double? = null,
        onConnect: () -> Unit = {},
        onInstall: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) {
        val state = ProfileUiState(
            isLoading = false, wellnessAvailability = availability, wellnessGranted = granted, wellnessRefused = refused,
            todaySteps = steps, todayCaloriesBurned = calories,
        )
        show { styles -> HealthSection(state, styles, onConnect, onInstall, onSettings) }
    }

    private val shortConnectBody =
        "See today's steps and calories here, and your heart rate during workouts. You choose what to share, and it stays on this device."

    @Test
    fun `available with nothing allowed offers Connect with the shorter body`() {
        var connects = 0
        health(onConnect = { connects++ })
        rule.onNodeWithText("Connect Health Connect").assertExists()
        rule.onNodeWithText(shortConnectBody).assertExists()
        rule.onNodeWithText("Connect").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun `after a refusal the card opens Health Connect's settings and Connect is gone`() {
        var opened = 0
        health(refused = true, onSettings = { opened++ })
        rule.onNodeWithText("Nothing was allowed. You can allow it in Health Connect's settings.").assertExists()
        rule.onNodeWithText("Connect").assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `an unavailable Health Connect draws nothing at all`() {
        health(availability = HealthConnectAvailability.Unavailable)
        rule.onAllNodesWithText("Health Connect", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an update-required Health Connect offers Google Play`() {
        var installs = 0
        health(availability = HealthConnectAvailability.UpdateRequired, onInstall = { installs++ })
        rule.onNodeWithText("Update Health Connect").assertExists()
        rule.onNodeWithText("Open Google Play").performClick()
        assertEquals(1, installs)
    }

    @Test
    fun `steps and calories show as Today with calories burned and the heart rate note`() {
        health(granted = HealthDataType.entries.toSet(), steps = 6412, calories = 1904.0)
        rule.onNodeWithText("Today".uppercase()).assertExists()
        rule.onNodeWithText("Health Connect").assertExists()
        rule.onNodeWithText("6,412").assertExists()
        rule.onNodeWithText("Steps").assertExists()
        rule.onNodeWithText("1,904").assertExists()
        rule.onNodeWithText("Calories burned").assertExists()
        rule.onNodeWithText("Heart rate shows during workouts and walk/run tracking.").assertExists()
    }

    @Test
    fun `steps alone hide calories, and without a heart rate grant there is no heart rate note`() {
        health(granted = setOf(HealthDataType.STEPS), steps = 6412, calories = null)
        rule.onNodeWithText("6,412").assertExists()
        rule.onNodeWithText("Calories burned").assertDoesNotExist()
        rule.onNodeWithText("Heart rate shows during workouts and walk/run tracking.").assertDoesNotExist()
    }

    @Test
    fun `heart rate alone gets its own card with a way to allow the rest`() {
        var opened = 0
        health(granted = setOf(HealthDataType.HEART_RATE), onSettings = { opened++ })
        rule.onNodeWithText("Heart rate connected").assertExists()
        rule.onNodeWithText("It shows during workouts and walk/run tracking.").assertExists()
        rule.onNodeWithText("Connect").assertDoesNotExist()
        rule.onNodeWithText("Open Health Connect settings").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `heart rate and calories with no calorie figure still get the heart rate card`() {
        health(granted = setOf(HealthDataType.HEART_RATE, HealthDataType.CALORIES), steps = null, calories = null)
        rule.onNodeWithText("Heart rate connected").assertExists()
        rule.onNodeWithText("Open Health Connect settings").assertExists()
        rule.onAllNodesWithText("Today".uppercase()).assertCountEquals(0)
    }

    @Test
    fun `granted calories with no figure yet draws nothing rather than an empty Today`() {
        health(granted = setOf(HealthDataType.CALORIES), steps = null, calories = null)
        rule.onAllNodesWithText("Today".uppercase()).assertCountEquals(0)
    }

    // --- Last 7 days ---

    private fun last7State(
        workouts: Int = 3,
        sets: Int = 12,
        trained: Int = 6,
        missing: List<BodyRegion> = listOf(BodyRegion.SHOULDERS, BodyRegion.CORE),
    ) = ProfileUiState(
        isLoading = false, workoutCount = 9, week = week(2, 0),
        last7Count = workouts, last7SetCount = sets, last7RegionsTrained = trained, last7RegionsMissing = missing,
    )

    @Test
    fun `the last 7 days card shows the workouts, the regions and the ones still missing`() {
        show { styles -> ProfileLast7Card(last7State(), styles) }
        rule.onNodeWithText("Last 7 days".uppercase()).assertExists()
        rule.onNodeWithText("3", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Muscle regions trained", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("No sets yet: Shoulders, Core", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TalkBack hears the workout count along with the regions`() {
        show { styles -> ProfileLast7Card(last7State(), styles) }
        rule.onNodeWithContentDescription("3 workouts. Muscle regions trained: 6 of 8. No sets yet: Shoulders, Core").assertExists()
    }

    @Test
    fun `one workout is singular`() {
        show { styles -> ProfileLast7Card(last7State(workouts = 1), styles) }
        rule.onNodeWithText("Workout", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("1 workout.", substring = true).assertExists()
    }

    @Test
    fun `a window with no sets at all says so`() {
        show { styles -> ProfileLast7Card(last7State(workouts = 0, sets = 0, trained = 0, missing = BodyRegion.entries), styles) }
        rule.onNodeWithText("No sets in these 7 days.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `sets that map to no muscle region are not reported as no sets`() {
        // A week of cardio or full-body work: sets exist, but none belongs to one of the eight regions.
        show { styles -> ProfileLast7Card(last7State(workouts = 3, sets = 9, trained = 0, missing = BodyRegion.entries), styles) }
        rule.onNodeWithText("Nothing in these 7 days maps to a muscle region.", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("No sets in these 7 days.", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `at the default size on a 360 dp phone the map and the numbers sit side by side`() {
        show { styles -> ProfileLast7Card(last7State(), styles, Modifier.width(328.dp)) }
        // The card is 328 dp wide, so 296 dp inside: the numbers column starts right of the 168 dp map.
        val numbers = rule.onNode(hasContentDescription("3 workouts.", substring = true)).getUnclippedBoundsInRoot()
        assertTrue(numbers.left > 168.dp)
    }

    @Test
    fun `at the default size both Today figures sit side by side`() {
        health(granted = setOf(HealthDataType.STEPS, HealthDataType.CALORIES), steps = 6412, calories = 1904.0)
        val steps = rule.onNodeWithText("Steps").getUnclippedBoundsInRoot().top
        val calories = rule.onNodeWithText("Calories burned").getUnclippedBoundsInRoot().top
        assertEquals(steps, calories)
    }
}
