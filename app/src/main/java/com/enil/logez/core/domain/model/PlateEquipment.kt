package com.enil.logez.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The Plate Calculator's available equipment (PHASE2_PLAN.md §5.1.5, §5.2) — settings-layer
 * shape only; the calculator's per-side-loading algorithm itself is M7 work.
 */
@Serializable
data class PlateEquipment(
    val barsKg: List<Double> = listOf(20.0),
    val platesKg: List<Double> = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0),
)

val defaultPlateEquipment = PlateEquipment()
