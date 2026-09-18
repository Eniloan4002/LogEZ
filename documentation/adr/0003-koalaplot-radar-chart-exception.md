# ADR-0003: KoalaPlot for the muscle-balance radar wheel (scoped exception to "no chart library")

**Status:** Accepted, scoped narrowly.

## Context

`core/designsystem/BarChart.kt`, `LineChart.kt`, and `HeatmapGrid.kt` exist because of an explicit,
project-wide decision: **no third-party chart library** — every chart in the app is a
dependency-free Compose `Canvas` composable.

A "muscle balance" feature (a radar/spider plot of set-share-per-body-region on the Statistics
screen, alongside the existing numeric distribution list in
`core/domain/calc/MuscleStatsCalculator.kt`) is qualitatively different from a bar/line chart — it
needs a polar coordinate system, multi-axis gridlines, and area-fill rendering that a from-scratch
`Canvas` implementation would take real effort to get right, and to keep readable at 8 axes. No
such component exists in this codebase.

## Decision

A narrow exception to the "no chart library" rule, **for the radar plot only**, via
`io.github.koalaplot:koalaplot-core`. `BarChart.kt`, `LineChart.kt`, and `HeatmapGrid.kt` stay
exactly as they are — dependency-free Canvas composables. This ADR does not reopen that decision
for anything else.

**Version/compat note, worth reading before bumping KoalaPlot:** the pinned version is built with a
Kotlin version one minor ahead of this project's pinned Kotlin. Per Kotlin's own binary-compatibility
guidance, a library built one minor version ahead "will probably load, but is not guaranteed to." Before
adopting or bumping a library in a similar position, verify all of the following rather than just a
green compile — a clean `:app:compileDebugKotlin` alone is not sufficient:
`:app:checkDebugAarMetadata` (no compileSdk floor conflict), `:app:dependencies` (confirm the
resolved Compose Multiplatform version is inside this project's pinned Compose BOM, not ahead of
it), the full unit-test suite, the merged-manifest check (no surprise new permission), and an
install+launch smoke test.

**Axis grouping:** `MuscleGroup` has 20 raw values, four of which aren't body regions (`CARDIO`,
`FULL_BODY`, `OTHER`, `NECK`). The wheel renders **8 grouped regions** rather than all remaining raw
groups: Chest; Back; Shoulders; Arms; Core; Quads; Hamstrings + Glutes; Lower leg. The four excluded
groups stay counted in the existing distribution list above the wheel, with a footnote — nothing is
silently dropped from the numbers, only from the wheel's own axes.

## Consequences

- `core/domain/calc/MuscleBalance.kt` is the one place body-region grouping logic lives — a pure,
  tested mapping independent of the chart library itself, so a future swap (dropping KoalaPlot, or
  a from-scratch radar Canvas) only has to change the rendering half.
- No new manifest permission surface; KoalaPlot is a pure rendering library.
- This does not reopen the "no chart library" rule for anything else — a future chart-library
  request needs its own decision and its own ADR, following this one's compat-verification pattern.
