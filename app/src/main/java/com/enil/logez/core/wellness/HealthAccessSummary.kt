package com.enil.logez.core.wellness

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enil.logez.R

/**
 * "LogEZ can read: Steps, Heart rate. Not allowed: Calories burned", in the app's type order
 * ([HealthDataType] entries). Shared by Settings > Export & backup's Health Connect row and first-run
 * setup's Health Connect section (first-run plan, O1f), so both describe a grant the same way.
 */
@Composable
internal fun healthAccessSummary(granted: Set<HealthDataType>): String {
    val names = HealthDataType.entries.associateWith { type ->
        stringResource(
            when (type) {
                HealthDataType.STEPS -> R.string.data_health_type_steps
                HealthDataType.CALORIES -> R.string.data_health_type_calories
                HealthDataType.HEART_RATE -> R.string.data_health_type_heart_rate
            },
        )
    }
    if (granted.isEmpty()) return stringResource(R.string.data_health_access_none)
    val reading = names.filterKeys { it in granted }.values.joinToString(", ")
    val missing = names.filterKeys { it !in granted }.values.joinToString(", ")
    return if (missing.isEmpty()) {
        stringResource(R.string.data_health_access_all, reading)
    } else {
        stringResource(R.string.data_health_access_partial, reading, missing)
    }
}
