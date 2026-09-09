package com.enil.logez.feature.activity.location

import kotlinx.coroutines.flow.Flow

/** M21a. `FusedLocationSource` is the production binding; `FakeLocationSource` (test source set) drives controller tests. */
interface LocationSource {
    fun fixes(): Flow<LocationFix>
}
