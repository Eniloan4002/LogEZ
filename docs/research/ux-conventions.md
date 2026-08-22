# ux-conventions

Confidence: high — nearly every logging/settings fact came from official hevyapp.com feature pages or hevyapp.com/help mirror pages fetched directly, and the CSV schema was verified against a real export sample file; the few snippet-only facts (warm-up formula 40/60/80, first-day-of-week location, export-via-email) are from Hevy's own help-center titles/snippets.

Gaps: help.hevyapp.com (Zendesk) returns 403 to both WebFetch and Firecrawl, so several help articles were read only via search-result snippets (warm-up default formula, previous-vs-routine values setting, rest-timer defaults, export/import article, first-day-of-week) — content is consistent across sources but not read in full. Unverified: whether the in-workout number pad is a fully custom keyboard vs. system decimal keyboard with an accessory bar (only the accessory-bar RPE and Calculator buttons are documented); whether tapping the checkmark on an untouched row silently accepts the grey placeholder values (widely reported behavior but not stated in any page I read); any distance-unit (km/miles) global setting (only KG/LBS is documented); the exact current bottom-tab count/names in the 2026 build (official tutorial names Home/Workout/Profile; a Discover surface exists but its placement is unclear); whether an in-app minimized-workout bar exists when you leave the active workout screen while staying inside the app; exact TSV-vs-CSV tier split for export. The hotelgyms.com review (403) could not be used as a secondary check.

# Hevy UI/UX Research: In-Gym Logging Speed + Settings Surface

## 1. The core logging loop (what makes Hevy fast)

**Set-row table layout.** Each exercise is a card containing a table of set rows. Columns: SET number, PREVIOUS, weight (KG/LBS), REPS, an optional RPE column (when RPE tracking is enabled), and a checkmark column ([track-workouts](https://www.hevyapp.com/features/track-workouts/), [workout-settings](https://www.hevyapp.com/features/workout-settings/)).

**Previous-performance recall.** When you log an exercise you've done before, its last performance appears on the left of each set row under a column labelled **PREVIOUS** — "track your previous performance at a glance during a live session instead of guessing what you did last time" ([track-workouts](https://www.hevyapp.com/features/track-workouts/)). Tapping a PREVIOUS value **instantly fills it into the current set** instead of typing weight/reps/duration ([help article 36011896355479, via search snippet](https://help.hevyapp.com/hc/en-us/articles/36011896355479)). A setting (Profile > gear > Workout > Previous Workout Values) chooses whether PREVIOUS shows the last time the exercise was done in *any* workout or the last time *within the same routine* ([help article 34105442929943, snippet](https://help.hevyapp.com/hc/en-us/articles/34105442929943)).

**Auto-fill from previous sets.** Adding an exercise you've done before automatically pre-populates the number of sets, weights, and reps from last time, all editable ([track-workouts](https://www.hevyapp.com/features/track-workouts/)). A grey **"+ Add Set"** button appends rows ([how-to-write-sets-and-reps, snippet](https://www.hevyapp.com/features/how-to-write-sets-and-reps/)).

**One-tap set completion.** You "tap the checkmark next to each set to mark it as complete and trigger the rest timer" — completion and rest-timer start are one gesture ([hevy-tutorial](https://www.hevyapp.com/hevy-tutorial/)). A "set completion sound" exists under the Sounds setting ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).

**Set types.** Tapping the set *number* marks the set as warm-up, drop set, failure, or normal ([track-workouts](https://www.hevyapp.com/features/track-workouts/), [hevy-tutorial](https://www.hevyapp.com/hevy-tutorial/)).

**Keyboard accessory bar.** The numeric entry keyboard carries extra controls: an **RPE button "on the top of the keyboard"** to pick RPE for the focused set, and a **Calculator button "on the bottom left, just above the keyboard"** that opens the plate calculator ([exercise-programming-options snippet + weight-plate-calculator](https://www.hevyapp.com/features/weight-plate-calculator/)). (Whether the keypad itself is fully custom vs. the system decimal pad was not confirmed by official text — see gaps.)

**No +/- weight steppers on phone set rows** were found in any official documentation; the ±15 s steppers belong to the rest timer, and the Apple Watch UI is where last-session weight/reps are "adjusted in a second" via watch controls ([App Store listing](https://apps.apple.com/us/app/hevy-workout-tracker-gym-log/id1458862350)). Do not clone a stepper UI on faith — Hevy's phone speed comes from tap-to-fill placeholders, not steppers.

## 2. Rest timer UX

- Triggered automatically when a set's checkmark is tapped; countdown "appears near the top of each movement, just below the custom note section"; when it hits zero "Hevy notifies you it's time for the next set" ([workout-rest-timer](https://www.hevyapp.com/features/workout-rest-timer/)).
- Duration range **5 seconds to 5 minutes**; while running, **-15 / +15 buttons** subtract/add 15 s; per-exercise durations supported (long rest on compounds, short on isolation); an "off" position disables it per exercise ([workout-rest-timer](https://www.hevyapp.com/features/workout-rest-timer/)).
- **Default Rest Timer** setting applies to every newly added exercise: Profile > gear > Workouts > Default Rest Timer ([rest-timer help article 35385404949143, snippet](https://help.hevyapp.com/hc/en-us/articles/35385404949143)).
- Timer volume: high / normal / low / off, under Sounds ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).

## 3. Backgrounded-app persistence (Live Activity — exists on Android too)

Hevy's "Live Activity" is an ongoing lock-screen/notification surface on **both iOS and Android** ([live-activity](https://www.hevyapp.com/features/live-activity/)):
- Shows: current exercise + completed set count, prescribed weight/reps for the next set, previous-performance reference, elapsed workout time, and the rest-timer countdown.
- **Actionable from the notification**: "mark set as completed without unlocking your phone or opening the app", adjust the rest timer "in 15-second increments or skip it altogether".
- Android implementation is a notification (lock screen + badge + pop-up permissions must be enabled); iOS uses Live Activities. Free-tier feature.
- For a local-only Android clone this maps to a **foreground service with a media-style/custom ongoing notification** carrying complete-set, +15 s / -15 s, and skip-rest actions plus a countdown.

**Keep Awake During Workout** setting prevents screen lock during a session ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).

## 4. Plate calculator

- Enable: Profile > gear > Workouts > Plate Calculator toggle. Open in-workout via the **Calculator button above the keyboard** ([weight-plate-calculator](https://www.hevyapp.com/features/weight-plate-calculator/)).
- User selects available plates and bar weight; a **Manage** control next to "Available Equipment" adds custom bars and custom plate loads (home-gym use case). Works for barbell, short bar, EZ bar — explicitly **not dumbbells** ([weight-plate-calculator](https://www.hevyapp.com/features/weight-plate-calculator/), [workout-settings](https://www.hevyapp.com/features/workout-settings/)).
- If the target is unreachable with the chosen plates: "Closest possible weight is 135kg"-style message ([weight-plate-calculator](https://www.hevyapp.com/features/weight-plate-calculator/)).

## 5. Warm-up set calculator

- Invoked from an exercise's three-dot menu > **Add Warm Up Sets**, in routines or live workouts ([warm-up-set-calculator](https://www.hevyapp.com/features/warm-up-set-calculator/)).
- **Default formula: 40% x 5, 60% x 5, 80% x 3** (percent of working weight); percentages, reps, and number of sets all editable; "Reset to Default" restores the formula ([help article 35650921359639, snippet](https://help.hevyapp.com/hc/en-us/articles/35650921359639)).
- Settings: Profile > gear > Workouts > Warm-up Calculator (toggle + "Warmup Method"), with **plate-increment rounding** for barbell exercises and **dumbbell rounding** (e.g., 2.5 kg increments) ([warm-up-set-calculator](https://www.hevyapp.com/features/warm-up-set-calculator/)).
- Separate **"Warm Up Sets"** setting decides whether warm-up sets count toward total volume and PR statistics ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).

## 6. Exercise management mid-workout

- Reorder / replace / remove exercises during a live session ([track-workouts](https://www.hevyapp.com/features/track-workouts/)); reorder is three-dot menu > **Reorder Exercise**, then drag-and-drop ([gym-routines, snippet](https://www.hevyapp.com/features/gym-routines/)).
- Supersets are supported; **Smart Superset Scrolling** auto-scrolls to the superset's next exercise when a set is completed ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).
- **Inline Timer** setting adds start/pause stopwatch buttons for duration-based movements (planks) ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).
- **Live Personal Record Notification** announces PRs mid-workout, with volume control ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).
- Workout settings are reachable *during* a live session via a bottom settings button, and via Profile > Settings > Workouts ([workout-settings](https://www.hevyapp.com/features/workout-settings/)).

## 7. Finish flow and workout summary

- **Finish** button top-right ends the session and opens a save screen: rename session, view/edit duration, see volume and set count ([workout-log, snippet](https://www.hevyapp.com/features/workout-log/)).
- After saving: a celebratory screen with illustrations showing 7-day training consistency, the workout just completed, and details; PR callouts appear on the summary ([workout-log + exercise-performance snippets](https://www.hevyapp.com/features/workout-log/)).
- Auto-generated **shareable images**: workout summary (duration, weight lifted, sets), exercise list, "weight lifted compared to a real-world object", and PR cards; share to Stories or Save Image ([shareable](https://www.hevyapp.com/features/shareable/)).

## 8. Settings surface (with local-clone relevance)

| Setting | Details | Matters locally? |
|---|---|---|
| Units | Global Settings > Units (KG/LBS); per-exercise override by tapping the KG/LBS column label ([help mirror](https://www.hevyapp.com/help/change-units-per-exercise/)) | Yes — per-exercise override is a distinctive touch |
| First day of week | Chosen via icon at top-right of the **calendar page**, not a global row ([calendar help, snippet](https://help.hevyapp.com/hc/en-us/articles/35380117933207)) | Yes |
| Theme | Profile > gear > Preferences > Theme: **Dark / Light / OS settings** ([help mirror](https://www.hevyapp.com/help/change-the-theme-android-ios/)) | Yes |
| Default Rest Timer | Applies to newly added exercises; can be off | Yes |
| Sounds | Timer volume (off/low/normal/high), set-completion sound, PR-notification volume | Yes |
| Previous Workout Values | any-workout vs same-routine source | Yes |
| Warm-up Calculator / Warm Up Sets | formula + whether warm-ups count in stats | Yes |
| Plate Calculator | toggle + equipment management | Yes |
| RPE Tracking | adds RPE column per set | Yes |
| Smart Superset Scrolling, Inline Timer, Keep Awake, Live PR Notification | toggles | Yes |
| Export Data | Settings > account section > **Export Workout Data** (or measurements data); file **emailed** to account address; **CSV or TSV depending on plan tier** ([export help, snippet](https://help.hevyapp.com/hc/en-us/articles/38001424401943), [arvo.guru](https://arvo.guru/blog/hevy-to-arvo-csv-import)) | Clone should export a local file/SAF share instead of email |
| Import | Settings > Export & Import Data > **Import Strong CSV** (Strong-app format) plus a "log previous workouts / import CSV" flow ([export help, snippet](https://help.hevyapp.com/hc/en-us/articles/38001424401943)) | Optional; supporting Hevy's own CSV enables migration |
| Account/backup | Cloud account; "What happens if I switch devices" help article exists — data lives server-side ([help index](https://www.hevyapp.com/help/)) | N/A locally — replace with local DB + file export/backup |

**Actual Hevy workout CSV schema** (verified against a real export sample, [github.com/matanabudy/workout-data-sync](https://github.com/matanabudy/workout-data-sync/blob/main/examples/hevy_export_sample.csv)) — one row per set:
`"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"`
Dates formatted `22 Dec 2025, 08:00`; `set_index` is 0-based; `set_type` values include `"normal"`; weight always in kg regardless of display units. Adopting this exact schema makes the clone round-trippable with real Hevy exports.

## 9. Widgets, watch apps, navigation (context)

- **Home-screen widgets** (iOS + Android): weekly volume/activity widget (deep-links to Profile), routines widget (tap a routine starts it as a live workout), aggregate data widget (configurable metric + date range), routine-of-the-day widget (schedule per weekday); all auto-update after workouts ([home-screen-widgets](https://www.hevyapp.com/features/home-screen-widgets/)).
- **Watch**: Apple Watch app (watchOS 8+, pre-populates last weight/reps for quick adjust, tracks heart rate, rest timer) and a **Wear OS app** exist; official positioning is "leave the phone in the locker" ([App Store](https://apps.apple.com/us/app/hevy-workout-tracker-gym-log/id1458862350), [hevycoach client-app](https://hevycoach.com/features/client-app/)). Context only for the clone.
- **Navigation**: official tutorial describes bottom tabs **Home** (social feed of followed users — irrelevant for a local clone), **Workout** (routine folders, routine library, "start empty workout" button), and **Profile** (workout count/streak, calendar, statistics graphs — duration/volume/reps, body measurements, progress photos, Exercises list button, and the Settings gear top-right) ([hevy-tutorial](https://www.hevyapp.com/hevy-tutorial/)). A Discover/search surface exists for finding users/routines. Exercise library (400+ exercises, custom exercises with type: weight+reps, reps only, duration, distance) is reached from workout building and from Profile ([features index](https://www.hevyapp.com/features/)).
