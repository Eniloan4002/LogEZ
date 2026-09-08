package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup

/**
 * M20c (ADR-0009) — the 8 body regions the Statistics screen's muscle-balance radar wheel plots.
 * [MuscleGroup] has 20 raw values, four of which aren't body regions at all (see
 * [toBodyRegion]); the remaining 16 are grouped into these 8 for a readable wheel (Owner's choice,
 * 2026-09-08 structured question, over the 16-raw-groups alternative). Declared in the fixed order
 * the wheel's spokes render in.
 */
enum class BodyRegion { CHEST, BACK, SHOULDERS, ARMS, CORE, QUADS, HAMSTRINGS_GLUTES, LOWER_LEG }

/** Null for [MuscleGroup.CARDIO]/[MuscleGroup.FULL_BODY]/[MuscleGroup.OTHER]/[MuscleGroup.NECK] —
 *  none of the four are body regions, so they never appear on the wheel (they stay counted in the
 *  existing per-group distribution list; see [balanceAxes]'s KDoc for how that list and the wheel
 *  can disagree in percentage terms). */
fun MuscleGroup.toBodyRegion(): BodyRegion? = when (this) {
    MuscleGroup.CHEST -> BodyRegion.CHEST
    MuscleGroup.LATS, MuscleGroup.UPPER_BACK, MuscleGroup.TRAPS, MuscleGroup.LOWER_BACK -> BodyRegion.BACK
    MuscleGroup.SHOULDERS -> BodyRegion.SHOULDERS
    MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS -> BodyRegion.ARMS
    MuscleGroup.ABDOMINALS -> BodyRegion.CORE
    MuscleGroup.QUADRICEPS -> BodyRegion.QUADS
    MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> BodyRegion.HAMSTRINGS_GLUTES
    MuscleGroup.CALVES, MuscleGroup.ADDUCTORS, MuscleGroup.ABDUCTORS -> BodyRegion.LOWER_LEG
    MuscleGroup.CARDIO, MuscleGroup.NECK, MuscleGroup.FULL_BODY, MuscleGroup.OTHER -> null
}

data class RegionShare(val region: BodyRegion, val setCount: Int, val sharePercent: Int)

/**
 * Maps [MuscleStatsCalculator.GroupShare]s (per raw [MuscleGroup], from the existing distribution
 * list) onto all 8 [BodyRegion]s, always — every region is present, zero-filled if nothing landed
 * there, in [BodyRegion]'s declared order, so the wheel's axes never reshuffle between calls.
 * [RegionShare.sharePercent] is recomputed over the **region-only** total (the sum of every
 * [MuscleStatsCalculator.GroupShare.setCount] whose group maps to a region) — this can legitimately
 * differ from the numeric distribution list above the wheel, whose own percentages are each
 * computed over the FULL set total including Cardio/Full body/Other/Neck.
 */
fun balanceAxes(current: List<MuscleStatsCalculator.GroupShare>): List<RegionShare> {
    val setCountByRegion = current
        .mapNotNull { share -> share.group.toBodyRegion()?.let { region -> region to share.setCount } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, counts) -> counts.sum() }
    val totalInRegions = setCountByRegion.values.sum()
    return BodyRegion.entries.map { region ->
        val setCount = setCountByRegion[region] ?: 0
        val sharePercent = if (totalInRegions == 0) 0 else Math.round(setCount * 100.0 / totalInRegions).toInt()
        RegionShare(region, setCount, sharePercent)
    }
}
