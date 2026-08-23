# logEZ — Phase 1 Research: Hevy Feature Inventory & Proposed Clone Scope

**Date:** 2026-08-22 · **Status:** scope APPROVED by Owner 2026-08-22 — §7 answers: scope as proposed; no Hevy-CSV importer (fresh start; export stays); 150-set cap removed; widgets/Wear OS/Health Connect deferred to Phase 4. See `docs/PHASE2_PLAN.md`.
**Sources:** 9 research reports in [docs/research/](research/) — Hevy's official help centre (read via the Zendesk API), hevyapp.com feature pages, hevy.com/pricing, the official OpenAPI spec recovered from api.hevyapp.com, Hevy's production web-app JS bundle, the Google Play listing, and community API clients (HA-hevy, hevy-mcp, and live exercise-catalog dumps).

---

## 1. How Hevy is organized (orientation)

Three bottom tabs: **Home** (social feed — becomes "your workout history" when social is removed), **Workout** (routine folders, routines, Start Empty Workout), **Profile** (workout count, streak, calendar, statistics, measurements, exercise list, settings gear).

Core vocabulary: a **Routine** is a saved template; a **Workout** is a live logged session. A "Program" is just a **folder of routines**. Hierarchy: `Folder → Routine → RoutineExercise → RoutineSet` and `Workout → WorkoutExercise → WorkoutSet`, everything ordered by an `index`, exercises pointing at an **ExerciseTemplate** (the library).

---

## 2. Feature inventory — Free-tier equivalent

### Logging (all free)
- Unlimited workout logging; 3 entry points: **Start Routine**, **Start Empty Workout**, **Copy a past workout**
- Set-row table per exercise: SET # · PREVIOUS · KG/LBS · REPS (or TIME/DISTANCE) · optional RPE · **one-tap checkmark** completes the set and starts the rest timer
- **Previous-performance recall**: PREVIOUS column shows last performance ("50 kg × 10 @ 8.5"); tapping it fills the current set; setting chooses source = *any workout* vs *same routine*
- Auto-populate sets/weights/reps from last time when adding a known exercise
- **Set types (4)**: Normal, Warm-up (W), Failure (F), Drop set (D) — changed by tapping the set number. Drop-set rule: no rest timer before a drop set. Failure rule: 0 reps can't be logged
- **Supersets**: unlimited members, multiple supersets, per-superset color, giant sets/circuits; **Smart Superset Scrolling** auto-scrolls to the next superset exercise on completion
- **Rest timers**: per-exercise (off–5 min), auto-start on set completion, −15/+15/skip, 5 sounds + volume levels, **Default Rest Timer** applies to newly added exercises only
- **RPE logging** (settings toggle): live workouts only (NOT in the routine builder), scale 6–10 in half steps (6, 7, 7.5, 8, 8.5, 9, 9.5, 10), blank allowed
- **Plate calculator** (settings toggle): Calculator button above the keyboard on barbell exercises; custom bars/plates; "closest possible weight" fallback
- Mid-workout: add/remove/reorder/replace exercises, swipe-left deletes a set, pause/resume the duration stopwatch, **Inline Timer** stopwatch for duration sets, update bodyweight from the exercise menu
- **Ongoing notification / Live Activity** (Android too): current exercise, next-set target, elapsed time, rest countdown; complete-set / ±15 s / skip actions from the lock screen
- Notes: **routine notes** (persist per routine exercise, support links) vs **workout notes** (saved with the session; shown greyed next time), plus a workout description
- Finish flow: **Save Workout screen** — edit title, date/time, duration (allows backdated logging), Routine Settings ("Update Routine Values" toggle); structural changes prompt **Update Routine vs Keep Original**; then a summary with volume, sets, duration, streak, and PR medals

### Routines & organization (free, capped)
- **4 routines max** (free cap), routine folders (uncapped), drag-to-reorder folders/routines, duplicate routine, share/save-as-routine from any past workout
- Routine targets per set: weight, exact reps **or rep range** (e.g. 6–8), duration, set type, per-exercise rest timer; **routine values auto-update after each workout** unless toggled off (rep ranges never auto-update)
- Explore library of 26 pre-built programs (a "program" = folder of routines)

### Exercise library (free, custom capped)
- **~433 built-in exercises** ("400+" officially), search + filter by equipment/muscle; recently-used sort first
- Per exercise: title (equipment baked in, e.g. "Bench Press (Barbell)"), 1 primary + N secondary muscle groups, equipment category, type, how-to instructions + animation
- **Exercise detail**: Summary (metric graphs + PRs + collapsible Set Records table), History (every session containing it), How-to
- **7 custom exercises max** (free cap): image, name, equipment, primary/secondary muscles, type (type immutable after creation); duplicate-to-reset-history

### Analytics (free tier)
- Chart ranges: **last 30 days / 3 months only**
- Muscle distribution (body + chart), Main Exercises, Monthly Report, Last-7-Days body graph
- Workout calendar (unlimited scroll-back), **streak = consecutive weeks with ≥1 workout**, Year in Review
- PR medals + live PR banner, Set Records (heaviest weight per rep count)
- 1RM estimation on weight+reps exercises

### Measurements (free, limited)
- **Body weight + waist circumference only** on free; per-date entries (1/day), backdating, per-metric graphs
- Progress photos (1/day, private, overlay camera for consistent angles)

### Data & settings (free)
- **CSV export** of workouts + measurements (emailed), **CSV import** (Hevy + Strong format)
- Settings: units kg/lb (+ per-exercise override), theme (dark/light/system), first day of week, default rest timer, sounds (timer/set-complete/PR volumes), previous-values source, warm-up-sets-count-in-stats, keep awake, plate calculator, RPE tracking, smart superset scrolling, inline timer, live PR notification
- Home-screen widgets (7 types), Apple Watch / Wear OS apps, web app, Strava sync — all free

## 3. Feature inventory — Pro-tier equivalent

The entire Pro tier is surprisingly small. **Bold** = becomes default-unlocked in logEZ:

| Pro feature | Free limit | logEZ |
|---|---|---|
| **Unlimited routines** | 4 max | Unlimited, no cap code at all |
| **Unlimited custom exercises** | 7 max | Unlimited |
| **Full graph/stats history (1 year + all time)** | 30 days / 3 months | All ranges: 30d / 3m / 1y / all time |
| **Full body measurements** (body fat %, lean mass, neck, shoulders, chest, biceps, forearms, abdomen, hips, thighs, calves — 17 metrics) | weight + waist only | All 17 metrics |
| **Set count per muscle group** (sets/muscle/week stat) | not included | Included |
| **Warm-up set calculator** (default 40%×5, 60%×5, 80%×3 of working weight, editable, plate/dumbbell rounding) | unavailable | Included |
| Hevy Trainer (adaptive AI programming) | — | ❌ excluded (cloud AI service, not core logging) |
| Public REST API access | — | ❌ N/A (local-only app) |

Universal limits (not tier-gated): **150-set cap per workout/routine** (Hevy keeps it even on Pro — proposed: keep as a sanity guard, trivially removable).

Pricing context only: Pro is $2.99/mo, $23.99/yr, $74.99 lifetime. No pricing/paywall/license code exists in logEZ.

## 4. Social features — explicit exclusion list

Everything below exists in Hevy and will **not** exist in logEZ:

1. Home **following feed** (posted workouts, likes, comments, comment replies/likes)
2. **Discover feed** (workouts from strangers; not even disableable in real Hevy)
3. **Following/followers**, follow requests, private-profile system, suggested-athletes carousel
4. **Public user profiles** (bio, social links, media gallery, follower counts) + profile Compare tool
5. **Leaderboard Exercises** (38-exercise follow-scoped leaderboards, incl. the Leaderboard tab inside exercise details)
6. **Workout/routine/folder/profile share links** (public hevy.com pages) and social-media shareable images/story stickers
7. Workout **photo/video posting** to a feed (private progress photos stay)
8. **Friend discovery / contacts sync / invites**; block/report
9. Social notifications (follows, per-user workout alerts)
10. **Strength Level** percentile comparison vs the Hevy user population (needs their population data; borderline social — excluded)
11. Athlete workout browsing / Explore's community content
12. Also excluded (not social, but cloud/out-of-scope): accounts/login, cloud sync, web app, Hevy Trainer + HevyGPT AI, Strava sync, coach features, ads/telemetry/analytics — none of which the clone needs

**What survives the removal:** Home tab becomes *your own workout history* (Hevy itself confirms this is what the feed shows with zero followed users); Workout and Profile tabs are already ~fully personal; exercise details keep charts/records minus the leaderboard tab; workout details keep stats/notes/PRs minus like/comment/share rows.

## 5. Proposed logEZ core feature set (= Free + Pro − social − cloud)

**A. Routines & programs** — unlimited routines, folders (programs = folders), full routine builder (sets, weight, reps/rep-range, duration, set types, per-exercise rest timer, notes, supersets, reorder, duplicate), save-past-workout-as-routine, routine-values auto-update w/ toggle.

**B. Live workout logging** — the full logging loop above: previous-recall + tap-to-fill, one-tap completion, 4 set types with their timer/stat rules, supersets + smart scrolling, rest timers w/ notification actions, RPE, plate calculator, warm-up calculator (unlocked), inline timer, pause, mid-workout edits, keep-awake, ongoing-notification (foreground service), finish flow w/ update-routine prompt, backdated manual logging, edit/delete/copy past workouts.

**C. Exercise library** — ~400 seeded exercises (original descriptions, placeholder media; **no Hevy-copyrighted assets**) using Hevy's exact taxonomy: 20 muscle groups, 9 equipment categories, 8 exercise types (`weight_reps`, `reps_only` = bodyweight reps, `bodyweight_weighted`, `bodyweight_assisted`, `duration`, `weight_duration`, `distance_duration`, `short_distance_weight`); unlimited custom exercises; exercise detail w/ Summary/History/How-to.

**D. Analytics & records** — all chart ranges incl. 1-year/all-time; volume/reps/duration/frequency stats; muscle distribution (body + chart); set count per muscle group; Main Exercises; monthly report; calendar + weekly streak; full PR system (9 PR kinds w/ the per-type matrix), Set Records table, live PR banners; Hevy's exact 1RM lookup-table formula (recovered from their production code — see §6).

**E. Body measurements** — all 17 metrics unlocked, per-date entries, graphs, progress photos with overlay camera.

**F. Data ownership** — offline-first Room DB (single source of truth), **CSV export in Hevy's exact export schema** (round-trippable), **CSV import of your real Hevy export** so your existing history migrates in, JSON full backup/restore.

**G. Settings** — the full workout-settings inventory in §2 (units w/ per-exercise override, theme, first-day-of-week, sounds, etc.).

**Deferred (Phase 4 candidates, not in initial scope):** home-screen widgets, Wear OS companion, Health Connect, program Explore library content.

## 6. Verified technical reference (anchors Phase 2 — do not re-derive)

- **Entities & fields**: full OpenAPI schemas for Workout, WorkoutExercise, Set, Routine (adds `rest_seconds`/exercise + `rep_range`/set), ExerciseTemplate, RoutineFolder, BodyMeasurement (17 fields), ExerciseHistory — in [research/api-data-model.md](research/api-data-model.md). Canonical units: kg / meters / seconds; display conversion only.
- **Enums**: set type `{warmup, normal, failure, dropset}`; RPE `{6,7,7.5,8,8.5,9,9.5,10}`; 20 muscle groups; 9 equipment categories; 8 exercise types + 2 built-in-only stair-machine types (`floors_duration`, `steps_duration` via `custom_metric`). UI↔API type mapping table (incl. the `reps_only` vs `bodyweight_weighted` trap): [research/followup-1.md](research/followup-1.md).
- **1RM formula**: NOT Epley — a fixed 30-entry percentage table (`1RM = weight / pct[reps]`, 0 reps → 0, >30 reps clamps to 0.50), Brzycki-rounded for 1–10 reps, custom-flattened 11–30; display rounds to 1 decimal; per-workout top set makes the chart point. Kotlin drop-in: [research/followup-0.md](research/followup-0.md).
- **RPE asymmetry**: logged sets carry RPE; the consumer routine builder cannot set target RPE (that's Hevy Coach-only). logEZ: `rpe` on logged sets; optional `targetRpe` column decision deferred to Phase 2. [research/followup-2.md](research/followup-2.md)
- **Bodyweight volume rules**: bodyweight counts toward volume only for 100 %-bodyweight built-ins (Pull up, Chin up, Dips, Handstand push-up); assisted = bodyweight − assistance; weighted = bodyweight + added load; `weight_kg` stores only the added/assistance amount; custom bodyweight exercises never add bodyweight to volume.
- **PR matrix** (per exercise type): weight+reps → Heaviest Weight, Best 1RM, Best Set Volume, Best Session Volume · bodyweight → Best Set (reps), Most Session Reps · assisted → Most Reps (set), Best Total Reps · weighted bodyweight → Heaviest Weight, Best Set Volume · duration → Best Time · weight+duration → Heaviest Weight, Best Time · distance+duration → Longest Distance, Longest Time. Warm-up sets excluded from stats/PRs unless the setting includes them.
- **CSV export schema** (verified against a real export): `title, start_time, end_time, description, exercise_title, superset_id, exercise_notes, set_index, set_type, weight_kg, reps, distance_km, duration_seconds, rpe` — one row per set, dates like `22 Dec 2025, 08:00`, weights always kg.
- **Sync-friendly pattern** Hevy itself uses: `updated_at` on every entity + soft-delete events — worth mirroring in Room for future-proofing backup/restore.

## 7. Open questions before Phase 2

1. **Scope confirmation** — is §5 (Free + Pro − social − cloud/AI) the right feature set? Anything to add/cut?
2. **Hevy data migration** — you use Hevy already; should the Phase-2 plan include an importer for your real Hevy CSV export (recommended — the schema is verified) so logEZ starts with your full history?
3. **150-set cap** — keep Hevy's universal sanity cap, or remove entirely?
4. **Deferred list** — agree that widgets / Wear OS / Health Connect sit in Phase 4, or promote any of them?
