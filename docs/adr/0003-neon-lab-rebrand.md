# ADR-0003: "Neon Lab" rebrand — color, typography, and the M9 revamp scope

**Status:** Accepted, foundation shipped (2026-08-26, M9a); component/icon/copy passes still pending (M9b+)

## Context

The Owner felt the app's UI/UX "feel" was unpolished despite core features working well, and asked
to go through a full branding revamp step by step in discussion rather than jump straight to
implementation. Two references were given: Hevy's level of interaction *slickness/polish* (not its
visual style — recreating another product's distinctive branded UI is out of scope regardless), and
a "mad scientist" aesthetic brief (moodboard: scalpels, biohazard signage, CRT monitor banks, glass
specimen tubes, black rubber gloves, a folder tab labeled "ETHICALLY QUESTIONABLE," neon green
called out as the trope's signature color).

Read as a real tension, not a simple brief: literal mad-scientist camp (cartoon mascots, costume
jokes) reads as *unpolished* — the opposite of the ask — while Hevy's restraint is the opposite of
camp. Resolved by targeting the aesthetic's *clinical/industrial* half (surgical steel, hazard
signage, chrome, glass tubes) rather than its cartoon half, and by carrying the "mad science" flavor
through color, data typography, iconography, and copy voice rather than illustration or mascots.

Rather than propose hex values in prose (this project's own history — ADR-0002's "too dark"
revision — shows color/feel decisions don't land well as text alone), the direction was mocked up
as an actual two-artboard design canvas (a Statistics screen + a visual-system reference sheet)
before any Kotlin was written, and iterated on with the Owner in that form. The Owner approved the
mockup with two adjustments: push the accent bolder/more maximalist (more neon, more glow, less
restraint than the first draft), and swap the tertiary accent from violet to blue.

## Decision

Replace the MP076 palette (ADR-0002) with "Neon Lab" — still dark-only, still a near-black neutral
base, but deeper (more contrast against the neon accents) and with three brand accents sharing one
saturated, glow-forward character instead of one muted primary:

| Role | Color | Hex |
|---|---|---|
| `background` | Neutral950 | `#0A0D0F` |
| `surface` | Neutral900 | `#12161A` |
| `surfaceVariant` | Neutral800 | `#1C2225` |
| `outlineVariant` | Neutral700 | `#232B30` |
| `outline` | Neutral600 | `#3A444B` |
| `onSurfaceVariant` | Neutral400 | `#7C8A82` |
| `onBackground` / `onSurface` | Neutral0 | `#EAF2E9` |
| `primary` | NeonGreen | `#39FF6E` |
| `onPrimary` | Neutral950 | `#0A0D0F` |
| `primaryContainer` / `tertiaryContainer` | Neutral900 | `#12161A` |
| `onPrimaryContainer` | NeonGreen | `#39FF6E` |
| `secondary` / `secondaryContainer` | HazardAmber | `#F5C518` |
| `onSecondary` / `onSecondaryContainer` | Neutral950 | `#0A0D0F` |
| `tertiary` | SpecimenBlue | `#2E9FFF` |
| `onTertiary` | Neutral950 | `#0A0D0F` |
| `onTertiaryContainer` | SpecimenBlue | `#2E9FFF` |
| `error` | Danger500 (semantic, brightened to match the bolder direction) | `#FF5A50` |
| `onError` | Neutral950 | `#0A0D0F` |

`Warning500` is aliased to `HazardAmber` (`#F5C518`) — the old palette had two near-identical
ambers (`#E0A63C` semantic warning, plus the brand accent); the new direction only needs one
hazard-yellow. `SupersetPalette` is untouched (see Consequences).

Typography: three fonts (all SIL OFL, bundled as `res/font/` resources — no network font fetch,
matching this app's offline-first posture), replacing the single system-default sans that M0's
`Type.kt` KDoc had explicitly flagged as a placeholder pending "a Phase 4/polish decision":

| Role | Font | Weight |
|---|---|---|
| `titleLarge` / `titleMedium` | Chakra Petch | Bold / SemiBold |
| `bodyLarge` / `bodyMedium` | IBM Plex Sans | Regular |
| `labelLarge` | IBM Plex Sans | Medium |
| `LogEzMono.data{Large,Medium,Small}` (new, not yet adopted at call sites) | IBM Plex Mono | SemiBold / Medium / Regular |

## Rationale

**Why the neutral base got deeper, not just re-tinted:** the mockup's `#0A0D0F` (vs. the old
palette's `#11161A` `Neutral950`) tests noticeably better as a backdrop for a bright, glowing accent
— more headroom between background and the neon green's own luminance means the glow effects
(box-shadows, gradient bar fills) actually read as glowing rather than just "a slightly brighter
green box." This was settled visually in the mockup, not by contrast math alone.

**Why `primaryContainer`/`tertiaryContainer` stay a plain `Neutral900` fill rather than an
accent-tinted dark (e.g. the PR toast's `#0F1611` green-black from the mockup):** same reasoning as
ADR-0002's `primaryContainer` decision — a plain surface-tier fill with the accent as the "on" color
(`onPrimaryContainer`/`onTertiaryContainer`) passes contrast comfortably and keeps one fewer named
color in the system; the mockup's accent-tinted variants are a *component-level* treatment (the PR
toast specifically) worth keeping as a one-off, not promoting to a Material3 role every card
inherits by default.

**Why the tertiary swap (violet → blue) didn't need new contrast math from scratch:** the mockup's
violet (`#C837FF`) and the replacement blue (`#2E9FFF`) were chosen to share the same saturation/
lightness character deliberately (see the mockup's "PROPOSED" swatch row) — recomputing confirmed
blue clears text-on-background at 6.98:1 and the container/fill pairings at 6.4–6.9:1, all
comfortably above WCAG AA's 4.5:1 floor for the roles that are actually painted as text (see
Consequences for the full computed table).

**Why IBM Plex Sans is wired as a single variable font, not per-weight static files:** Google Fonts
retired IBM Plex Sans's static per-weight distribution in favor of one variable font
(`IBMPlexSans[wdth,wght].ttf`); Compose's `Font(resId, weight, variationSettings =
FontVariation.Settings(FontVariation.weight(N)))` (`ExperimentalTextApi`) resolves named weight
instances from it. `android.graphics.fonts.FontVariationAxis` has existed since API 26, so this is
compatible with this app's minSdk without a fallback path. Chakra Petch and IBM Plex Mono still
ship static per-weight files and needed no such treatment.

**Why the numeric-data mono treatment (`LogEzMono`) isn't wired into any screen yet:** rolling it
out means touching every weight/rep/timer/chart-axis call site across the app (Logger set rows,
Exercise Detail Summary, Analytics bar/line charts, History cards) — a component-level pass, not a
token-level one. Shipping the fonts/colors alone already changes every screen's feel immediately
(everything sources `MaterialTheme.colorScheme`/`typography.*`), matching ADR-0002's own
"`Color.kt`/`Theme.kt` are the only files touched" precedent; bundling the mono rollout into the
same commit would blur a foundational, low-risk change together with a wide, screen-by-screen one.
Deliberately split into a later milestone (M9b) instead, following this project's own
one-milestone-at-a-time discipline (the M8a/b/c/d split).

## Consequences

- `Color.kt`, `Theme.kt`, and `Type.kt` are the only production files touched — every other
  color/type-dependent screen sources exclusively from `MaterialTheme.colorScheme.*` /
  `MaterialTheme.typography.*` and needed no changes, confirmed by grep (no direct references to
  the old `Mantis`/`FirstColorsOfSpring`/`NuitBlanche` names existed anywhere outside `Color.kt`/
  `Theme.kt` even before this change).
- Contrast for every foreground/background pairing that is actually rendered as text was computed
  with Python (WCAG relative-luminance formula), not eyeballed:

  | Pairing | Ratio |
  |---|---|
  | `onBackground` text on `background` | 17.06:1 |
  | `onSurfaceVariant` text on `surface` | 5.03:1 |
  | `onSurfaceVariant` text on `background` | 5.40:1 |
  | `onSurface` text on `surface` | 15.90:1 |
  | `onPrimary` on `primary` fill | 15.08:1 |
  | `onPrimaryContainer` (green) on `primaryContainer` | 13.74:1 |
  | `primary` green as bare text on `background` | 14.58:1 |
  | `onSecondary` on `secondary` fill | 11.26:1 |
  | `secondary` amber as bare text on `background` | 11.96:1 |
  | `onTertiary` on `tertiary` fill | 6.87:1 |
  | `tertiary` blue as bare text on `background` | 6.98:1 |
  | `onTertiaryContainer` (blue) on `tertiaryContainer` | 6.47:1 |
  | `onError` on `error` fill | 6.40:1 |
  | `error` red as bare text on `background` | 6.34:1 |

  Every one clears WCAG AA's 4.5:1 text floor, most by a wide margin — this is a high-contrast
  palette by construction (bright accents on a near-black base), not a marginal pass.
- `SupersetPalette` (the 9-color superset-badge palette) is deliberately untouched, same reasoning
  as ADR-0002: it's kept distinct from brand/semantic colors on purpose, so a superset badge never
  reads as a brand color.
- Font files (`res/font/*.ttf`, ~1.1MB total) and their SIL Open Font License texts
  (`docs/licenses/OFL_*.txt`) are bundled in the repo/APK — no runtime font download, consistent
  with this app's existing zero-network-calls posture (the same reasoning already applied to the
  embedded `MuscleBodyData.kt` anatomy tracing).
- **Follow-up milestones, not yet done:** M9b (custom lab-signage icon set replacing default
  Material icons; real elevation/shadow treatment on currently-flat cards; `LogEzMono` adopted at
  numeric call sites), M9c (a dry "lab report" copy-voice pass on empty states, toasts, and
  achievement moments), M9d (evaluating a narrow, deliberate exception to M8a's near-zero-animation
  rule for PR/finish moments specifically — not reopening animation generally).
- No on-device visual verification was performed — this session's environment has no `adb`/emulator
  access, same caveat as ADR-0002. The Owner should confirm the shipped APK's fonts/colors actually
  render and read correctly on a real screen (the IBM Plex Sans variable-font wiring in particular
  has not been visually confirmed on-device, only compiled).
