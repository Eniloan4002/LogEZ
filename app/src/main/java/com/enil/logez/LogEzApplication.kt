package com.enil.logez

import android.app.Application
import com.enil.logez.core.data.seed.SeedManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LogEzApplication : Application() {
    @Inject lateinit var seedManager: SeedManager

    /** PHASE2_PLAN.md §7.7: seeding runs from an application-scoped coroutine at startup. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seedManager.seedIfNeeded() }
    }
}
