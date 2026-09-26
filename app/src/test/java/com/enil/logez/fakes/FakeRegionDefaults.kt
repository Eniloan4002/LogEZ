package com.enil.logez.fakes

import com.enil.logez.core.common.RegionDefaults
import com.enil.logez.core.common.RegionSuggestion
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2); defaults to what an en-PH phone suggests. */
class FakeRegionDefaults(
    var suggestion: RegionSuggestion = RegionSuggestion(
        weightUnit = WeightUnit.KG,
        distanceUnit = DistanceUnit.KM,
        lengthUnit = LengthUnit.CM,
        firstDayOfWeek = DayOfWeek.SUNDAY,
        weekStartClamped = false,
    ),
) : RegionDefaults {
    override fun suggest(): RegionSuggestion = suggestion
}
