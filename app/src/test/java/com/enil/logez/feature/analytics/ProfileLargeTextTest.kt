package com.enil.logez.feature.analytics

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.enil.logez.core.designsystem.LogEzTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Profile pieces whose layout is decided by the font scale alone (so Robolectric can check it
 * without real glyph widths). The measured two-column / rows rule of the scorecards is checked on the
 * device instead (docs/verification/profile-2026-10-01).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp", fontScale = 2.0f)
class ProfileLargeTextTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `at 2x text the last 7 days numbers stack under the map instead of squeezing beside it`() {
        val monday = LocalDate.of(2026, 8, 17)
        val state = ProfileUiState(
            isLoading = false, workoutCount = 9,
            week = ProfileWeek(monday, 2, 4, (0L..6L).map { WeekDay(monday.plusDays(it), WeekDayMark.UPCOMING) }, 5, 0.0, 0, 0),
            last7Count = 3, last7SetCount = 12, last7RegionsTrained = 6,
            last7RegionsMissing = listOf(com.enil.logez.core.domain.calc.BodyRegion.SHOULDERS),
        )
        rule.setContent { LogEzTheme { ProfileLast7Card(state, rememberProfileStyles()) } }
        val numbers = rule.onNodeWithContentDescription("3 workouts.", substring = true).getUnclippedBoundsInRoot()
        // Side by side, the numbers would start right of the 168 dp map.
        assertTrue(numbers.left < 168.dp)
    }
}
