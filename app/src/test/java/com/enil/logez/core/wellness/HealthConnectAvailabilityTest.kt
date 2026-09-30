package com.enil.logez.core.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** How `getSdkStatus` becomes LogEZ's availability (first-run plan, O1f). */
class HealthConnectAvailabilityTest {
    // HealthConnectClient's status codes, as literals so the test pins them.
    private val unavailable = 1
    private val updateRequired = 2
    private val available = 3

    @Test
    fun `available is available, whatever the API level`() {
        assertEquals(HealthConnectAvailability.Available, healthConnectAvailability(available, sdkInt = 28) { false })
        assertEquals(HealthConnectAvailability.Available, healthConnectAvailability(available, sdkInt = 35) { false })
    }

    @Test
    fun `below Android 14 an update required with no Health Connect app is not installed`() {
        assertEquals(HealthConnectAvailability.Unavailable, healthConnectAvailability(updateRequired, sdkInt = 28) { false })
        assertEquals(HealthConnectAvailability.Unavailable, healthConnectAvailability(updateRequired, sdkInt = 33) { false })
    }

    @Test
    fun `below Android 14 an update required with the app installed is an update`() {
        assertEquals(HealthConnectAvailability.UpdateRequired, healthConnectAvailability(updateRequired, sdkInt = 33) { true })
    }

    @Test
    fun `from Android 14 an update required stays an update without asking about the app`() {
        var asked = false
        val result = healthConnectAvailability(updateRequired, sdkInt = 34) { asked = true; false }

        assertEquals(HealthConnectAvailability.UpdateRequired, result)
        assertFalse(asked)
    }

    @Test
    fun `unavailable and unknown codes are unavailable`() {
        assertEquals(HealthConnectAvailability.Unavailable, healthConnectAvailability(unavailable, sdkInt = 26) { false })
        assertEquals(HealthConnectAvailability.Unavailable, healthConnectAvailability(42, sdkInt = 34) { true })
    }
}
