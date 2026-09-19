package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.WeightUnit

/**
 * Screen-wide toggles [WorkoutLoggerScreen] threads unchanged through every layer down to
 * [SetRow] -- none of these vary per exercise or per set. Bundled (2026-09-19 debt audit finding
 * #22) so a new screen-wide toggle (this has already happened ~6 times across M16-M18: RPE, plate
 * calculator, warm-up calculator, weight unit, inline timer) is added here once instead of by hand
 * through [WorkoutExerciseCard] -> [SetTable] -> [SetRow] and, separately,
 * [CircuitRoundCard] -> [CircuitEntry] -> [SetRow].
 */
data class WorkoutLoggerDisplayConfig(
    val rpeTrackingEnabled: Boolean = false,
    val inlineTimerEnabled: Boolean = true,
    val isEditMode: Boolean = false,
    val plateCalculator: PlateCalculatorConfig = PlateCalculatorConfig(),
    /** M18 §5.1.6: gates the "Add warm-up sets" overflow item. Always false in circuits (M11 invariant) -- CircuitRoundCard/CircuitEntry never read this field. */
    val warmupCalculatorEnabled: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
)
