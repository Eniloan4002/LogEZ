# ADR-0003: "Neon Lab" rebrand — color, typography, and the M9 revamp scope

**Status:** Accepted and fully shipped. M9a (color+type foundation) and M9b (elevation + custom icon set) shipped 2026-08-26; the LogEzMono rollout, M9c (copy voice), and M9d (motion exception) shipped 2026-08-27, completing the milestone.

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
| `secondary` / `secondaryContainer` | SpecimenBlue | `#2E9FFF` |
| `onSecondary` / `onSecondaryContainer` | Neutral950 | `#0A0D0F` |
| `tertiary` | HazardAmber | `#F5C518` |
| `onTertiary` | Neutral950 | `#0A0D0F` |
| `onTertiaryContainer` | HazardAmber | `#F5C518` |
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
- No on-device visual verification was performed **by this session** — no `adb`/emulator access,
  same caveat as ADR-0002 — but the Owner's own on-device check of `debug13.12` is what prompted the
  Revision below, so the fonts and near-black/neon base are now confirmed reading correctly on a
  real screen; only the secondary/tertiary role assignment needed a change.

## Revision (2026-08-26, same day) — secondary/tertiary swapped

**Context:** Owner checked `debug13.12` on-device and asked to swap the blue and yellow role
assignments, otherwise approving the foundation as shipped ("i like it so far").

**Decision:** `secondary`/`secondaryContainer` (the filled-banner role) move from HazardAmber to
SpecimenBlue; `tertiary`/`tertiaryContainer` (the selection/highlight-accent role) move from
SpecimenBlue to HazardAmber — a straight swap of which named color sits in which of the two roles,
not a change to either color's own hex value or to `primary`. Updated the Decision table above in
place rather than duplicating it here, consistent with ADR-0002's own revision style.

**Rationale:** Purely an on-device visual call by the Owner ("I think it looks better that way") —
no functional or accessibility driver, and none needed: both colors already had verified WCAG
contrast in both roles' shape (a bright fill with `Neutral950` text, or bare text on `background`),
so swapping which role each occupies doesn't change any contrast number, only which UI moments each
color shows up in (filled banners now read blue, selection highlights now read amber).

**Impact:** `Theme.kt` is the only file touched — `Color.kt`'s named constants (`HazardAmber`,
`SpecimenBlue`) are unchanged, only their `darkColorScheme(...)` role assignment moved, so every
consuming screen (which reads roles via `MaterialTheme.colorScheme.*`, never the named constants
directly) picks up the swap automatically with no other code changes.

## M9b (2026-08-26, same day) — real elevation + a custom lab-signage icon set

**Context:** Owner approved the M9a foundation on-device ("i like it so far") and authorized
proceeding to the next implementations. Two of M9b's three originally-scoped pieces (real
elevation on the app's flat cards; a custom icon set for domain-meaningful moments) were sized to
ship together; the third (`LogEzMono` adopted at numeric call sites) was deliberately left for a
later pass — see Follow-up.

**Decision — elevation:** Added `LogEzCard` (`core/designsystem/LogEzCard.kt`), a thin wrapper
around Material3's `Card` with a visible shadow (`Elevation.card = 4.dp`, new in `Tokens.kt`) and an
`outlineVariant` hairline border baked in as defaults. Rolled out across every *default-styled*
`Card(...)` call site app-wide — 49 sites across 12 files — via a parallel per-file pass (one agent
per file, same replacement rule everywhere), each agent deciding per call site whether it was safe
to replace: a plain `Card(modifier = ...) { ... }` became `LogEzCard(...)`, while any call that
already customizes `colors=`/`elevation=`/`border=`/`shape=` (e.g. `WorkoutSummaryScreen.kt`'s
PR-medal card, deliberately tinted with `primaryContainer`) was left as plain `Card()` untouched.

**Decision — icons:** Added `LogEzIcons` (`core/designsystem/LogEzIcons.kt`), four custom line
icons replacing default Material icons at the app's domain-meaningful moments, scoped deliberately
narrow: `Workout` (a barbell, replacing every `Icons.Filled.FitnessCenter` site — the Workout nav
tab, `WorkoutTabScreen`'s empty state, `ProfileScreen`'s Exercises row, and `MuscleGroupIcon`'s
generic fallback), `History` (a lab-log clipboard, replacing `Icons.Filled.History`), `Profile` (a
specimen-ID badge, replacing `Icons.Filled.Person`), and `PersonalRecord` (a hazard-triangle
"anomaly" glyph, replacing every `Icons.Filled.EmojiEvents` site across History, Workout Detail, and
Workout Summary). Universal UI chrome — back arrows, close, check, chevrons, search, more-options,
add, expand/collapse — stays default Material.

**Rationale:** Elevation directly targets the "flat/unfinished" symptom named at the very start of
this revamp's discussion (see Context above) — the mechanical, low-risk half of M9b, safely
delegable to a parallel per-file pass since each file's transformation follows one shared rule with
no cross-file judgment required. Icon scope was deliberately bounded to domain-meaningful icons,
not "replace every Material icon in the app": universal navigation/action chrome (`ArrowBack`,
`Close`, `Check`, `MoreVert`, chevrons, `Search`, `Add`, `ExpandMore`/`Less`, ...) are conventions
users already read instantly, and redesigning them buys no brand payoff while risking recognizability
for no reason — confirmed by enumerating every distinct icon actually used in the app (`grep` across
`app/src/main/java`) before deciding what to touch, rather than guessing at scope. `PersonalRecord`
ports the hazard-triangle glyph from the Owner-approved design-canvas mockup verbatim (same path
geometry), not a new design. Per-`MuscleGroup` illustrations (§7.5, `muscleGroupIcon()`) remain the
separate, already-deferred placeholder from M2 — only its shared `else` fallback branch (which
happened to literally be `FitnessCenter`, same as the nav tab) was touched, not the
CARDIO/FULL_BODY/OTHER/ABDOMINALS-specific branches.

**Impact:** 17 files touched (15 modified via the elevation rollout + icon wiring, 2 new:
`LogEzCard.kt`, `LogEzIcons.kt`). Both new components live in `core/designsystem/`, matching the
project's existing convention (`BarChart`, `LineChart`, `HeatmapGrid`, `BodyDiagram` all live there
too). No test files needed changes (no test asserts on `Card` vs `LogEzCard` or on which `ImageVector`
a screen renders). Build and the full unit suite verified green after the rollout; the parallel
per-file agents' import cleanup (removing now-unused `Card`/icon imports, adding `LogEzCard`/
`LogEzIcons` imports) was spot-checked across several files before committing, not just trusted.

**Follow-up:** `LogEzMono` (the numeric-data monospace treatment, already built in `Type.kt` during
M9a) is still not adopted at any real call site — rolling it out to actual weight/rep/timer/chart-
axis displays across the Logger, Exercise Detail Summary, and Analytics screens remains its own
pass. M9c (copy voice) and M9d (motion exception) also remain entirely unstarted. No on-device
verification of the elevation/icon changes was performed by this session (same standing caveat as
M9a/ADR-0002) — the Owner should confirm cards actually read as "lifted" and the four new icons
render correctly on a real screen.

## Completing the milestone (2026-08-27) — LogEzMono rollout, M9c copy voice, M9d motion exception

**Context:** Owner, after seeing M9a/M9b on-device: "i really like the updates so far, can you
simply implement all of the new features now?" — authorization to finish everything still scoped
but unstarted: the `LogEzMono` rollout this ADR's own M9b section had deferred, plus M9c and M9d.

**Decision — LogEzMono rollout:** Applied to every *read-only* numeric data display app-wide —
chart axis labels (`BarChart`/`LineChart`, a 1-line change each), the Logger's live elapsed-time +
set-count readout and rest-timer countdowns, the PREVIOUS column, RPE cells, Exercise Detail
Summary's metric readout and stat tiles, Analytics' training/distribution/set-count values, History
and Workout Detail's duration/volume/set figures, Calendar's per-day duration, Goals' progress text,
and the Workout Summary screen's stat cells and PR values. Explicitly did NOT touch editable input
fields (the set-logging weight/rep/duration/distance entry cells, the finish-screen duration editor)
— changing an input field's text style risks affecting cursor/IME rendering in ways this session has
no way to visually verify, and the ADR's own framing ("weights, reps, timers, chart axis labels")
reads naturally as data *readouts*, not the editing widgets themselves. Also left alone: dates
written as text, and numbers embedded in prose/plural-label sentences ("3 exercises", a fixed
"+15"/"-15" button label) rather than presented as a standalone data value.

**Decision — M9c copy voice:** Rewrote 8 strings across 6 signature moments in the dry "lab report"
tone the approved mockup established: History's empty state ("No specimens on file" / "Log your
first session to begin the record.", the exact mockup copy), Profile's empty state, Exercise Detail
Summary's two empty/no-data states, the post-workout summary title ("Experiment complete"), and the
live PR banner ("Anomaly detected: %1$s"). Explicitly did NOT rename core functional nouns
("routine", "workout", "goal", "exercise") anywhere those exact words are also the label on an
adjacent, currently-visible button or field — e.g. the Workout tab's empty state sits directly above
a "Start Empty Workout" button and a "New folder" action, so its title got a light touch ("Nothing
logged yet") but its subtitle keeps "routine"/"workout" verbatim; the Goals empty state sits next to
an "Add Goal" button and was left untouched entirely; the Exercise Library's "no results" state sits
below a "Search exercises" hint and was left untouched. The mockup's own restraint principle (flavor
through color/type/icons/copy, not illustration) extends here to *which* strings get the voice, not
a blanket rename — a screen where the flavored word contradicts a real button label right next to it
reads as broken, not on-brand.

**Decision — M9d motion exception:** M8a's rule was about interaction *latency* — a delay standing
between a tap and its result — not decoration categorically. Added exactly one animation: a slow
pulsing glow (`rememberInfiniteTransition`, alpha 0.35→1.0, 1.1s reverse loop) on the border of each
PR medal card on the post-workout summary screen, extracted into its own `PrMedalCard` composable.
It plays continuously on an already-fully-rendered card and never gates reaching the screen or
anything on it, so it doesn't reintroduce what M8a removed. Scoped to exactly this one moment — the
live in-session PR banner (a Snackbar) and every other place a PR value appears are untouched.

**Rationale:** Read "implement all of the new features now" as authorization to finish everything
already scoped in this ADR, not a license to invent new scope — every change in this section maps
to something this document had already named as a follow-up. The LogEzMono rollout's editable-vs-
read-only boundary and the copy voice's adjacent-label-collision check are both the same underlying
discipline: apply the aesthetic consistently, but verify each specific application against its real
surrounding context rather than pattern-matching a rule mechanically everywhere it could technically
apply.

**Impact:** 16 files touched across three commits (13 for the mono rollout, 1 for the copy pass,
1 — `WorkoutSummaryScreen.kt` — for the motion exception, which also carries that file's mono
changes). No test files needed changes. Build and the full unit suite (395 tests) verified green
after each commit; several of the parallel rollout's edited files were read and spot-checked before
committing, not just trusted from the agents' self-reported summaries.

**Follow-up:** None outstanding — M9a through M9d are all shipped. No on-device verification was
performed by this session (same standing caveat as every prior visual change here) — the Owner
should confirm the monospace numbers, the copy changes, and the PR-card glow all read correctly on
a real screen.

## v4.0 (2026-08-27) — mono-green palette, and the glow language the mockup actually had

**Context:** Owner reviewed the shipped M9 build on-device and asked for four things at once:
drop blue and amber entirely in favour of **black, white and green**, with *"different hues or
opacities of the green… differentiate hierarchy by how dark to light it is, the more vibrant green
it is the more primary it is"*; implement the glow effects that were in the approved mockup but
never made it into the app; and bring the Statistics screen closer to what that mockup showed.

**Decision — the palette narrows to one hue.** Hierarchy moves from *hue* to *vibrancy*:

| Role | Color | Hex | On `background` |
|---|---|---|---|
| `primary` — hero CTAs, active states, chart fills | NeonGreen | `#39FF6E` | 14.58:1 |
| `tertiary` — selection / highlight | SpringGreen | `#B8FF5C` | 16.24:1 |
| `secondary` — filled banners (recedes on purpose) | DeepGreen | `#12A65A` | 6.15:1 |

`onSecondary` is the near-black neutral, not the light one: light-on-DeepGreen measures **2.77:1**
(fails AA) against **6.15:1** for dark-on — caught by computing it rather than assuming the
light-text-on-fill convention the other two roles use. `Warning500` (warm-up badges) drops its
amber for a deliberately *muted* green `#5E8C6A` — a warm-up is a lesser set, so under the new
rule it should read as a lesser green. `Danger500` stays red: "delete is red" is a safety
convention, not branding.

**Decision — `SupersetPalette` goes green-only too, but is optimized, not hand-picked.** These nine
colors have a hard functional job (superset group A must be tellable from group B at badge size),
which a single-hue ramp threatens. A naive lightness-only green ramp scored a worst-pair CIE
**ΔE 12.9** — two of the nine would have looked identical. Instead the ramp was derived by
optimizing for *maximum minimum pairwise ΔE* across a green-only hue band, subject to every step
clearing 4.5:1 on the background. A first run over a wide band (90°–175°) scored ΔE 35.3 but drifted
into colors that read as cyan/teal — outside what the Owner asked for — so the band was tightened to
**100°–158°** (yellow-green through spring-green, deliberately stopping short of cyan), landing at
worst-pair **ΔE 30.4** with everything unmistakably green. Ordered lightest-first, so the low group
indices a real routine actually uses are the most vibrant ones.

**Decision — the glow language becomes real code** (`core/designsystem/Glow.kt`). The mockup's look
leaned on CSS `box-shadow`/`text-shadow` bloom that no earlier pass translated, which is the concrete
reason the shipped app read flatter than the mockup. Compose has no blur-shadow primitive across this
app's whole API range, so this provides two complementary approximations: `Modifier.neonGlow` (a
*colored* elevation shadow via `shadow(spotColor=, ambientColor=)`, honored from API 28 — on 26–27 it
degrades to an ordinary dark shadow, flatter but never broken) and `Modifier.glowFalloff` /
`glowBarBrush` / `DrawScope.drawBarGlow`, which paint gradient bloom explicitly and therefore work on
every supported API.

**Rationale:** the Owner's "more vibrant = more primary" instinct is sound and is what makes a
single-hue palette workable at all — it gives the system an ordering axis that hue was previously
doing. The one place it genuinely strains is `SupersetPalette`, where nine peers need mutual
distinction rather than a rank ordering; that is why that specific ramp is the only part of the
palette derived numerically instead of chosen.

**Impact:** `Color.kt` rewritten, `Theme.kt` roles remapped, `Glow.kt` added; no other file
references the removed `SpecimenBlue`/`HazardAmber` constants (verified by grep) and no test asserts
on any color value, so the palette swap is contained to those three files. The glow primitives are
then applied at the chart/card level and across the Statistics restyle.

**Follow-up:** The superset ramp's worst pair (ΔE 30.4) is comfortable but measurably tighter than
the old multi-hue palette's; if a future routine regularly runs 6+ simultaneous supersets and they
start reading alike, that ramp — not the brand roles — is the thing to revisit. As always, none of
this was verified on a real screen by the implementing session.
