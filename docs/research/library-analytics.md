# library-analytics

Confidence: high — nearly every fact comes from official Hevy sources (help.hevyapp.com articles read via the Zendesk JSON API, hevyapp.com feature pages, and the official OpenAPI spec extracted from api.hevyapp.com's swagger-ui-init.js), with exact field names and enums quoted from the spec.

Gaps: 1) Exact built-in exercise count: officially only "400+"; querying GET /v1/exercise_templates for a true count requires a Pro API key. 2) The 1RM estimation formula is not disclosed anywhere official (help article only says it is estimated from a set's weight and reps; Epley/Brzycki not named). 3) Mapping between the 8 UI exercise-type names and the 8 API enum values is not 1:1 documented — the API has `reps_only` and `bodyweight_reps` while the UI lists "Bodyweight reps" and "Weighted bodyweight"; the likely mapping (reps_only = plain rep counting, bodyweight_reps = bodyweight w/ added weight, short_distance_weight = "Weight & distance") is inference, not verified. 4) Annual ($23.99) and lifetime ($74.99) prices appeared only in third-party reviews citing the App Store listing; only $2.99/month was verified on official pages. 5) help.hevyapp.com blocks normal fetching (403) — worked around via the Zendesk API, so article formatting (tables) was reconstructed from raw HTML; the Pro comparison table's column assignment was double-checked against the raw HTML. 6) Marketing-page numbers were extracted by a summarizing fetch tool, so minor phrasing may be paraphrased; all limits/enums were cross-verified against help-center or API text where possible.

# Hevy: Exercise Library, Measurement Types, Analytics, PRs, Body Measurements

## 1. Exercise library

- **Size:** "400+ high-quality exercises" (official help article 35688251991575 and marketing pages; an exact count is not published).
- **Browsing/filtering:** search bar plus filters by **equipment** and **muscle targets**. Library is reached from Profile > Exercises, or from the "+ Add Exercise" button while building a routine or logging a workout. Recently logged exercises sort first, then custom exercises (help 35382889578135).
- **Equipment categories** (official API enum `EquipmentCategory`): `none, barbell, dumbbell, kettlebell, machine, plate, resistance_band, suspension, other` (api.hevyapp.com OpenAPI spec). Marketing copy matches: "barbells, dumbbells, kettlebells, gym machines, weight plates, resistance bands, suspension kits, or no equipment".
- **Muscle groups** (official API enum `MuscleGroup`, used for both primary and secondary): `abdominals, shoulders, biceps, triceps, forearms, quadriceps, hamstrings, calves, glutes, abductors, adductors, lats, upper_back, traps, lower_back, chest, cardio, neck, full_body, other` (20 values). Each exercise has **one primary muscle group + array of secondary muscle groups** (`primary_muscle_group`, `secondary_muscle_groups` on `ExerciseTemplate`; custom-create uses `muscle_group` + `other_muscles`).
- **ExerciseTemplate fields** (API): `id` (string, e.g. "05293BCA" for built-ins, UUID for custom), `title` (e.g. "Bench Press (Barbell)" — equipment is baked into the display name), `type`, `primary_muscle_group`, `secondary_muscle_groups[]`, `equipment_category`, `is_custom` (bool).

### Exercise detail screen (help 35382889578135, marketing exercise-performance page)
Three sections/tabs: **Summary**, **History**, **How to**.
- **How to:** a demonstrational animation + step-by-step setup/execution instructions.
- **Summary:** a graph with selectable metrics and time ranges **last 3 months (free) / year / all time (Pro)**; below the graph, Personal Records; at the bottom a collapsible **Set Records** table; for 13 specific exercises a **Strength Level** section; a share button exports a performance summary image.
  - Weight-based exercises — graph metrics: **Heaviest Weight, One Rep Max, Best Set Volume, Session Volume, Total Reps**; records shown: heaviest weight, "true or projected 1RM", best set & session volume.
  - Bodyweight & assisted exercises — graph metrics: **Most Reps (Set)** and **Session Reps** (also their only PRs).
  - Duration exercises (plank etc.) — graph shows **Best Time** (only PR).
  - Cardio exercises — graph shows **Best Pace, Longest Distance, Longest Time**; PRs are Longest Distance and Longest Time.
- **History tab:** every workout containing the exercise, with dates and the sets/reps/weights done; tapping an entry opens the full workout. (API mirrors this: `GET /v1/exercise_history/{exerciseTemplateId}` returns per-set entries with workout id/title/start/end + weight_kg, reps, distance_meters, duration_seconds, rpe, custom_metric, set_type.)

### Custom exercises (help 35700328894103, 35688251991575; marketing custom-exercises page)
- Fields: **Image (photo, video, or GIF), Name, Required equipment, Primary muscle target, Secondary muscle targets (multiple), Exercise type**.
- **Limit: 7 on free, unlimited on Pro** (help 38279350428695). On downgrade, existing customs above 7 are kept but no new ones can be created.
- Any library exercise can be **duplicated** into a custom exercise (three-dots > Duplicate Exercise) and then edited.
- Editing: title, asset, equipment, primary muscle, secondary muscles can be edited later (app only, not website); **the exercise type can never be changed after creation** (help 35700328894103).
- Important behavior: **custom bodyweight-type exercises never include bodyweight in volume calculations** — bodyweight only counts for library exercises flagged as 100%-bodyweight (help 38386262243223, 38387131819799).

## 2. Measurement/exercise types (set logging models)

- **UI names, 8 types with official examples** (marketing custom-exercises page): Weight & reps (bench press), Bodyweight reps (pull-ups), Weighted bodyweight (weighted dips), Assisted bodyweight (assisted pull-ups), Duration (plank), Duration & weight (weighted wall sit), Distance & duration (rowing), Weight & distance (suitcase carry).
- **API enum `CustomExerciseType`, 8 values** (official spec): `weight_reps, reps_only, bodyweight_reps, bodyweight_assisted_reps, duration, weight_duration, distance_duration, short_distance_weight`.
- **Set fields** (API `Set` / `PostWorkoutsRequestSet`): `type` ('normal' | 'warmup' | 'dropset' | 'failure'), `weight_kg`, `reps`, `distance_meters`, `duration_seconds`, `rpe` (allowed values exactly 6, 7, 7.5, 8, 8.5, 9, 9.5, 10), `custom_metric` ("Currently only used to log floors or steps for stair machine exercises"), `index`. Routine sets additionally support a nullable `rep_range` object `{start, end}` (e.g. 8–12). Exercises in a workout carry `notes`, `superset_id` (nullable), and ordered `sets`.
- **Volume math for bodyweight classes** (help 38386262243223, 34380762441111, 38387131819799):
  - Bodyweight: set volume = bodyweight x reps, but only for the library exercises that use 100% bodyweight — officially listed as **Pull up, Chin up, Dips, Handstand push ups**.
  - Assisted: effective load = bodyweight − assistance; volume = that x reps; **no volume computed at all if the user never logged bodyweight**.
  - Weighted: effective load = bodyweight + added weight (again only for the 100%-bodyweight library moves; partial-bodyweight moves like weighted push-ups count only the added weight).
  - Bodyweight can be updated from Profile > Measures or mid-workout via the exercise's three-dot menu > Update Bodyweight.

## 3. PR / records logic (help 35649367857175, 38279531346455)

Two distinct systems:
- **Personal Records (PRs)** — trigger a live banner during the workout (toggle: Profile > gear > Workouts > Live Personal Record Notification) and a medal on the set + in the saved workout. No notification the first time an exercise is ever logged (marketing live-pr page). Official PR kinds: **Heaviest Weight; Best 1RM; Best Set Volume (weight x reps); Best Session Volume; Longest Distance; Longest Time; Best Time; Best Set (most reps in a set); Most Session Reps**.
- PRs available **per exercise type** (official table): Weight & reps → Heaviest Weight, Best 1RM, Best Set Volume, Best Session Volume. Assisted → Best total reps, Most reps (set) only ("Assisted exercises do not have PRs available for weight"). Bodyweight reps → Best set, Most session reps. Weighted bodyweight → Heaviest weight, Best Set Volume. Duration → Best Time. Weight & duration → Heaviest weight, Best time. Distance & duration → Longest Distance, Longest Time.
- **Set Records** (per-rep-range records) — a separate table at the bottom of the exercise Summary showing "the heaviest weight you've lifted for that specific number of repetitions". Set records do **not** trigger live notifications or medals.
- **1RM**: "Hevy calculates an estimate of what your 1RM would be based on what was lifted in a given set" (help 36954464726167); the exact formula is not published.
- **Strength Level** (help 38001696749335): compares the user against Hevy users of the same **sex, age group, and bodyweight**, giving **Beginner / Intermediate / Advanced / Elite** plus a stronger-than percentage. Available only for 13 exercises: Behind the Back Bicep Wrist Curl (Barbell), Bench Press (Barbell), Bicep Curl (Barbell), Deadlift (Barbell), Hip Thrust (Barbell), Leg Press (Machine), Preacher Curl (Barbell), Reverse Curl (Barbell), Romanian Deadlift (Barbell), Seated Wrist Extension (Barbell), Skullcrusher (Barbell), Spider Curl (Barbell), Squat (Barbell).

## 4. Analytics / statistics (Profile > Statistics; help 35702030346903)

- **Last 7 Day Body Graph** — workout count for the last 7 days + body heat map of trained muscles.
- **Set Count Per Muscle Group** — sets per muscle group, on a graph and per-muscle list; two-level segmentation (period: last 30 days / 3 months / year / all time x bucket: week / month); muscle select/deselect on a diagram (marketing sets-per-muscle-group page).
- **Muscle Distribution (Chart)** — how training volume is split between muscles, with comparison to the equivalent previous period (blue = current, gray = prior); also shows workouts completed, total duration, total volume load (sets x reps x weight) and total sets for the period.
- **Muscle Distribution (Body)** — sets per muscle group week by week, with a muscle diagram highlighted in blue.
- **Main Exercises** — most frequently logged exercises over 30 days / 3 months / year / since start, with counts.
- **Leaderboard Exercises** — rank vs. followed users for supported (mostly barbell) exercises.
- **Monthly Report** — for the last completed month only (no historical archive): bar graph comparing months on workouts / duration / total volume / sets; totals summary; PR list; calendar view; muscle distribution vs. previous month; main muscle groups; top exercises; photos posted that month.
- **Yearly Review** (December, help 35700454899991): most productive month, most-trained body parts, most-logged exercises, biggest supporters, total volume/reps/duration, year calendar, longest streak, year PRs, shareable cards.
- **Workout calendar & streak** (marketing gym-consistency page): calendar with workout days highlighted blue; monthly (default), year, and multi-year views with unlimited scroll-back; retroactive logging on any date; **active streak = consecutive weeks with at least one logged session**; calendar views shareable; home-screen widgets.

## 5. Body measurements (help 35385479603479; API BodyMeasurement schema)

- Located at Profile > Measures; entries are per-date and can be backdated; each metric gets its own graph with a metric switcher below it and a dated value list.
- **Metrics** (API field names): `weight_kg`, `lean_mass_kg`, `fat_percent`, and 14 circumference fields: `neck_cm`, `shoulder_cm`, `chest_cm`, `left_bicep_cm`, `right_bicep_cm`, `left_forearm_cm`, `right_forearm_cm`, `abdomen`, `waist`, `hips`, `left_thigh`, `right_thigh`, `left_calf`, `right_calf`. Marketing copy: weight to 0.1 kg/lb, circumferences to 0.1 cm/inch; log as many or as few metrics per entry as desired.
- **Progress photos:** max one per day, private unless shared, can attach to a measurement entry or stand alone, in-app camera has an **Overlay** of the previous photo for consistent angles, plus compare/replace/delete.

## 6. Pro gating (official: help 38279350428695 and Pro table in 35119778922263)

| Feature | Free | Pro |
|---|---|---|
| Routines | limit of 4 | unlimited |
| Custom exercises | limit of 7 | unlimited |
| Stats/data history | last 30 days / 3 months only | + 1 year and all time |
| Body measurements | **body weight + waist circumference only** | + body fat % and all other circumferences |
| Muscle distribution (body + chart), Main exercises, Leaderboard, Monthly Report | included free | included |
| **Set count per muscle group** | not included | Pro |
| **Warm-up calculator** | unavailable | available |

- Downgrade behavior: nothing is deleted — extra routines/customs/measurement history stay visible but new creation/logging in gated areas is blocked; year/all-time ranges lock again but data is retained server-side.
- Pricing seen on official marketing pages: **$2.99/month**; plan types offered: Monthly, Annual, Lifetime (help 35119778922263).
- The public REST API (api.hevyapp.com, api-key header) exists for programmatic access; pagination default pageSize 5, max 100 on `/v1/exercise_templates`.
