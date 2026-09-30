package com.enil.logez.feature.settings

import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.UserSettings

/**
 * P-211 decision 2 (Owner, 2026-09-30): the three choices of the "Effort tracking" row, over the
 * two settings they are stored in. [UserSettings.rpeTrackingEnabled] stays the on/off and
 * [UserSettings.effortScale] the scale, so an existing user with RPE tracking on reads as [RPE]
 * without any migration. [OFF] keeps the saved scale.
 */
enum class EffortTrackingOption {
    OFF,
    RPE,
    RIR,
    ;

    companion object {
        fun of(settings: UserSettings): EffortTrackingOption = when {
            !settings.rpeTrackingEnabled -> OFF
            settings.effortScale == EffortScale.RIR -> RIR
            else -> RPE
        }
    }
}
