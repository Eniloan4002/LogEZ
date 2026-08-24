package com.enil.logez.feature.analytics

import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `rpeTrackingEnabled reflects the settings repository's current value`() = runTest {
        val settingsRepo = FakeSettingsRepository(UserSettings(rpeTrackingEnabled = true))
        val viewModel = ProfileViewModel(settingsRepo)

        assertTrue(viewModel.rpeTrackingEnabled.value)
    }

    @Test
    fun `setRpeTrackingEnabled writes through to the settings repository`() = runTest {
        val settingsRepo = FakeSettingsRepository(UserSettings(rpeTrackingEnabled = false))
        val viewModel = ProfileViewModel(settingsRepo)

        viewModel.setRpeTrackingEnabled(true)

        assertTrue(viewModel.rpeTrackingEnabled.value)
        assertTrue(settingsRepo.settings.value.rpeTrackingEnabled)

        viewModel.setRpeTrackingEnabled(false)

        assertFalse(viewModel.rpeTrackingEnabled.value)
        assertFalse(settingsRepo.settings.value.rpeTrackingEnabled)
    }
}
