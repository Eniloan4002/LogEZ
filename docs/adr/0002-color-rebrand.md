# ADR-0002: Color rebrand — the MP076 palette

**Status:** Accepted (2026-08-25, M8b)

## Context

The launch palette (`Color.kt`, Owner-directed 2026-08-23) was a near-black neutral base with a
single restrained violet accent — never formally recorded in an ADR, only in that file's own
KDoc. The Owner supplied a full replacement: a 6-color brand board ("MP076", credited Alex
Cristache @AlexCristache) — Praxeti White `#F6F7ED`, First Colors of Spring `#DBE64C`, Midnight
Mirage `#001F3F`, Mantis `#74C365`, Picture Book Green `#00804C`, Nuit Blanche `#1E488F` — with
the instruction to "utilize all these colors."

Research into the existing theme (M8 planning phase) found `Theme.kt`'s `darkColorScheme(...)`
call sets only 12 of Material3's 44 available roles; 32 fall through to Material3's baseline dark
tokens. Two of those unset roles are actually rendered live: `tertiary` (6 call sites — chart
"selected" highlights, the BodyDiagram selection outline, analytics readout labels) and
`secondaryContainer` (2 call sites — reorder-mode banners), both showing Material3's baseline
pink/lavender by accident rather than any app-authored color.

## Decision

Stay dark-only (no light theme). Map the 6 brand colors onto Material3 roles as follows, computed
— not eyeballed — via a fixed-hue HSL ramp and verified against WCAG contrast at every
foreground/background pairing that is actually rendered as text or a stroke:

| Role | Color | Hex |
|---|---|---|
| `background` | Midnight Mirage | `#001F3F` |
| `surface` | (derived, 3.5% lighter) | `#06284B` |
| `surfaceVariant` | (derived) | `#103358` |
| `outlineVariant` | (derived — new, chart gridlines) | `#324C67` |
| `outline` | (derived) | `#4D6B89` |
| `onSurfaceVariant` | (derived) | `#A0ADBA` |
| `onBackground` / `onSurface` | Praxeti White | `#F6F7ED` |
| `primary` | Mantis | `#74C365` |
| `onPrimary` | (background-tier navy, for dark-on-green text) | `#001F3F` |
| `primaryContainer` / `tertiaryContainer` | (surface-tier navy — see Consequences) | `#06284B` |
| `onPrimaryContainer` | Mantis | `#74C365` |
| `secondary` / `secondaryContainer` | Nuit Blanche | `#1E488F` |
| `onSecondary` / `onSecondaryContainer` | Praxeti White | `#F6F7ED` |
| `tertiary` — new, was unset | First Colors of Spring | `#DBE64C` |
| `onTertiary` | (background-tier navy) | `#001F3F` |
| `onTertiaryContainer` | First Colors of Spring | `#DBE64C` |
| `error` | Danger500 (unchanged, semantic) | `#D8564B` |

Picture Book Green (`#00804C`) is appended as `SupersetPalette`'s 9th entry rather than mapped to
a Material3 role — see Consequences.

## Rationale

**Why not the "obvious" first mapping (Nuit Blanche → `tertiary`):** the initial candidate
mapping put Nuit Blanche on `tertiary` since it's a strong, distinct accent. Computing its
contrast against the background (`#001F3F`) first gave **1.88:1** — `tertiary` is painted as bare
foreground text/stroke in the 6 live call sites above, and WCAG AA text requires 4.5:1. Nuit
Blanche is a good *container fill* color (dark, saturated, pairs well with light text on top —
verified at 8.17:1 for Praxeti White on Nuit Blanche) but a bad *foreground text* color against
this background. Swapped it onto `secondary`/`secondaryContainer` (exactly what the 2 live
`secondaryContainer` call sites need — a filled banner) and put First Colors of Spring on
`tertiary` instead, since at `12.17:1` against the background it's the highest-contrast of the
remaining brand colors and reads as clearly distinct from Mantis (`primary`).

**Why `primaryContainer`/`tertiaryContainer` are a plain navy surface tone, not a darkened
Mantis/Spring:** the natural instinct — a "container" role should be a muted variant of its base
color — breaks down under contrast math here. Darkening Mantis or First Colors of Spring by any
single fixed factor either stays too light for light text or gets dark enough to also fail
against dark text, because both source colors sit at a moderate-to-high native lightness; there
is no factor that pushes them into "dark enough for light text, distinct enough from background"
territory without a full 13-stop tonal-palette generator, which this project doesn't have. Since
neither role is actually consumed anywhere in the app today (confirmed by grep — same status the
*original* `primaryContainer = Accent600` had before this rebrand), the pragmatic and verifiably
correct choice is a `surface`-tier navy fill with the accent itself as the "on" text
(`onPrimaryContainer = Mantis` at 6.89:1, `onTertiaryContainer = First Colors of Spring` at
10.92:1 against that fill) — both comfortably pass, and the container still visibly carries its
accent's color via the text/icon drawn on it.

**Why Picture Book Green isn't a Material3 role:** every brand color needed a role that's either
consumed as foreground text (must clear 4.5:1) or a filled container (needs a working "on" pair).
Picture Book Green's contrast against the background is 3.31:1 — usable as a decorative fill, not
as text. `SupersetPalette` entries are exactly that: decorative fills identified by adjacency/shape,
never text-on-top. Appended as index 8; every existing entry (0–7) is read via `% SupersetPalette.size`
at all 3 call sites, never a hardcoded `% 8`, so the append is additive — no existing superset's
color shifts, confirmed by reading all 3 consuming files line-by-line before this change.

## Consequences

- `Color.kt` and `Theme.kt` are the only two files touched — every other color-dependent site
  (`BarChart.kt`, `LineChart.kt`, `BodyDiagram.kt`, `MonthlyReportScreen.kt`, `AnalyticsScreen.kt`,
  `WorkoutLoggerScreen.kt`, `RoutineBuilderScreen.kt`) sources exclusively from
  `MaterialTheme.colorScheme.*` roles and needed no changes.
- `Danger500`/`Warning500` are untouched — they're semantic (failure/warmup badges), not brand
  identity, and weren't part of the Owner's palette.
- `Accent400`/`Accent500`/`Accent600` (the old violet) are deleted, including the already-dead
  `Accent500` (defined but never referenced even before this rebrand).
- Contrast values were computed with Python's `colorsys` (WCAG relative-luminance formula), not
  estimated — see the M8b implementation session for the exact script. Any future palette
  adjustment should re-run the same check rather than eyeballing a new hex value, especially for
  any role consumed as literal on-background text (`primary`, `secondary`, `tertiary`,
  `onBackground`, `onSurfaceVariant`).
- No on-device visual verification was performed for this change — this session's environment has
  no `adb`/emulator access. The Owner should confirm the shipped APK actually reads correctly on
  a real screen; the contrast math is necessary but not sufficient for "looks right."
