package com.enil.logez.feature.widget

import com.enil.logez.core.common.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the widget reaches DI. Hilt cannot inject a `GlanceAppWidget` — it is not an Android
 * component — and `GlanceAppWidgetReceiver.glanceAppWidget` is a field initializer that runs
 * before Hilt would populate anything on the receiver, so dependencies cannot be handed to the
 * widget's constructor either. It fetches them itself instead, the same way
 * `WorkoutShareController` and the sounds settings screen already do.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetSnapshotLoader(): WidgetSnapshotLoader

    fun appLogger(): AppLogger
}
