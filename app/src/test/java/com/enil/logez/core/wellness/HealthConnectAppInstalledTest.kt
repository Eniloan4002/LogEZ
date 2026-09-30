package com.enil.logez.core.wellness

import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Whether the Health Connect app is installed, which decides "install" over "update" below Android 14 (O1f). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectAppInstalledTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `without the Health Connect package it is not installed`() {
        assertFalse(isHealthConnectAppInstalled(context))
    }

    @Test
    fun `with the Health Connect package it is installed`() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.google.android.apps.healthdata" })

        assertTrue(isHealthConnectAppInstalled(context))
    }
}
