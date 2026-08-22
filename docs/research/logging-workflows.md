# logging-workflows

Confidence: high — nearly all facts come verbatim from official Hevy Help Centre article bodies retrieved through Zendesk's public API, cross-checked against Hevy's own feature pages.

Gaps: help.hevyapp.com HTML pages 403 all fetchers (WebFetch, Firecrawl, and the article body failed to render in the browser pane), so article bodies were pulled from the Zendesk REST API instead — content is the canonical source text, but embedded screenshots were not viewable, so pixel-level layout details (exact placement of the check button, colors of the completed-set highlight, summary-screen layout) are unverified. hevyapp.com feature-page facts came through a summarizing fetch, so their short quotes are second-hand extractions rather than verbatim page text. Not found in any official source: the exact rest-timer picker increments (only the ~5s–5min range), whether an explicit in-app "Skip" button exists on the main workout screen (skip is only documented on the Live Activity widget), workout title/description fields on the Save Workout screen (photo and Visibility are documented; a title field is strongly implied by the product but not stated in the articles read), the exact 1RM formula (Hevy only says it "estimates" 1RM from set weight/reps), and behavior when discarding an in-progress workout. The free-plan 4-routine limit comes from the official Pro article and was current as of the article's last update; Hevy may have changed tier limits since.

# Hevy Core Logging Workflows — Behavioral Reference

All help.hevyapp.com article bodies were read verbatim via Zendesk's public REST API (`help.hevyapp.com/api/v2/help_center/en-us/articles/{id}.json`) because the HTML help centre blocks fetchers. hevyapp.com feature pages were read via summarizing fetches.

## 1. Core vocabulary: Routine vs Workout
- **Routine** = a saved plan/template living in the Workout tab; **Workout** = a live logged session. ([Workouts vs Routines](https://help.hevyapp.com/hc/en-us/articles/33703513582871))
- Hard cap: **150 sets per workout AND per routine, even on Pro**. Sets "automatically reset" each time a routine is started as a workout. ([150-Set Cap](https://help.hevyapp.com/hc/en-us/articles/34896183826455))
- Free-plan limits (official Pro article): **4 routines max, 3 months of data history, 7 custom exercises**; warm-up calculator is Pro-only. ([Pro Subscription](https://help.hevyapp.com/hc/en-us/articles/35119778922263))

## 2. Creating a routine
- Workout tab → tap **"New Routine"**; if the user has 2+ folders they first pick a destination folder. Name it (e.g. "Push"), add exercises, then **Save**. ([Build a Workout Program](https://help.hevyapp.com/hc/en-us/articles/34953606698903); [gym-routines feature page](https://www.hevyapp.com/features/gym-routines/))
- **Adding exercises**: scroll to bottom, tap blue **"+ Add Exercise"**; library has 400+ exercises with search + filters by equipment and muscle. When adding an exercise done before, **previous set count, weight, and reps auto-populate** (editable). ([how-to-write-sets-and-reps](https://www.hevyapp.com/features/how-to-write-sets-and-reps/))
- **Sets**: grey **"+ Add Set"** button adds sets; remove by **swiping left** (red Delete) or tapping the set number in the SET column → Remove Set. ([how-to-write-sets-and-reps](https://www.hevyapp.com/features/how-to-write-sets-and-reps/); [Delete article](https://help.hevyapp.com/hc/en-us/articles/38030200802583))
- **Targets per set**: weight under **KG/LBS** column (unit per settings); reps under **REPS** — tapping the REPS header switches between **exact reps and rep ranges** (e.g. 6-8). Duration exercises show a **TIME** column instead. ([how-to-write-sets-and-reps](https://www.hevyapp.com/features/how-to-write-sets-and-reps/))
- **Per-exercise rest timer**: tap **"Rest Timer"** just below the exercise name → scroll picker → **"Done"**; can be set to off per exercise. ([Rest Timer article](https://help.hevyapp.com/hc/en-us/articles/35385404949143))
- **Exercise three-dots menu**: reorder exercises (drag-and-drop), **replace exercise** from library, **"+ Add to Superset"**, "Add Warm Up Sets" (if calculator enabled), remove exercise. ([exercise-programming-options](https://www.hevyapp.com/features/exercise-programming-options/))
- **Routine notes** per exercise, may include a clickable link (e.g. YouTube form video), visible only to the owner. ([Exercise Notes](https://help.hevyapp.com/hc/en-us/articles/36011779117591))
- **Routine three-dots menu**: Share / **Duplicate Routine** / Edit Routine / Delete Routine. Duplicate opens the create-routine page pre-filled with suggested title "<original> (copy)" and no history attached. ([gym-routines](https://www.hevyapp.com/features/gym-routines/); [Reset Data](https://help.hevyapp.com/hc/en-us/articles/35119576252951))
- A finished workout can also be **saved as a routine**: Profile → workout → three dots → edit → Save as reusable template. ([gym-routines](https://www.hevyapp.com/features/gym-routines/))

## 3. Folders & programs (organization above routines)
- Create folder: Workout tab → **Folder icon above "Explore"** → name it. Move routines by **tap-hold-drag** into a folder. ([Build a Workout Program](https://help.hevyapp.com/hc/en-us/articles/34953606698903))
- Folder three-dots menu: Share folder, **Reorder (drag-and-drop)**, Rename, **+ Add new routine**, Delete folder. ([gym-routines](https://www.hevyapp.com/features/gym-routines/))
- **Explore library**: 26 pre-built programs + 7 routine categories (At home, Travel, Dumbbells only, Band, Cardio & HIIT, Gym, Bodyweight), filterable by Level/Goal/Equipment. **"Save Program" creates a folder containing each of the program's routines** — i.e., a "program" is modeled as a folder of routines. Saved library routines are fully editable. ([Routine and Program Library](https://help.hevyapp.com/hc/en-us/articles/36011518408983))

## 4. Starting a workout (3+ entry points)
1. **From a routine**: tap blue **"Start Routine"** — opens live workout pre-filled from the template. ([Log a Workout](https://help.hevyapp.com/hc/en-us/articles/35361530647959))
2. **"+ Start Empty Workout"** at top of Workout tab — improvised session, add exercises as you go. (same source)
3. **Repeat a past workout**: open any past workout (yours or another user's) → three dots → **"Copy workout"** → a live workout starts from it. (same source)
4. Also from the home-screen **Quick Access widget** (+ button). ([Workouts vs Routines](https://help.hevyapp.com/hc/en-us/articles/33703513582871))

## 5. Live logging flow
- Top bar: **stopwatch (top-left) tracks duration**; next to it live counters for **completed sets and volume load**. ([track-workouts](https://www.hevyapp.com/features/track-workouts/))
- Each set row shows: SET number, **PREVIOUS** column (last performance, e.g. "50lbs x 10 @ 8.5 RPE"), weight, reps (or TIME), optional RPE column, and a **check control — tapping it marks the set complete** and triggers that exercise's rest timer. ([track-workouts](https://www.hevyapp.com/features/track-workouts/); [RPE vs RIR](https://help.hevyapp.com/hc/en-us/articles/34490600233111))
- Weight/reps/duration are freely editable mid-workout; pre-filled values come from previous performance. ([track-workouts](https://www.hevyapp.com/features/track-workouts/))
- **Add exercises mid-workout** via "+ Add Exercise" at the bottom; **remove sets by swipe-left**; exercises removed via three-dots menu. ([track-workouts](https://www.hevyapp.com/features/track-workouts/); [exercise-programming-options](https://www.hevyapp.com/features/exercise-programming-options/))
- **Pause**: tap the duration (top-left) → **"Pause Workout Timer"** / later "Resume Workout Timer" (phone app only, not watch). ([Adjust duration](https://help.hevyapp.com/hc/en-us/articles/34513981310615))
- In-workout **grey Settings button** at the bottom of the live session opens workout settings (e.g. Smart Superset Scrolling toggle). ([Supersets guide](https://help.hevyapp.com/hc/en-us/articles/35650286563095))
- **Inline Timer** setting: duration exercises get a built-in stopwatch per set (counts up). ([Workout Settings](https://help.hevyapp.com/hc/en-us/articles/33882110558743))
- **Live Activity widget** (iOS AND Android): shows current/next exercise, sets done, prescribed weight/reps, elapsed time; lets you mark sets complete and adjust/skip the rest timer from the lock screen. ([Live Activity](https://help.hevyapp.com/hc/en-us/articles/35649846517399))

## 6. PREVIOUS values — two-layer model (important for clone data model)
- **Previous Workout Values** setting has two modes: **"Any workout"** (last time exercise was done anywhere) vs **"Same Routine"** (last time within this routine). Affects ONLY the PREVIOUS column. ([Previous Workout Values](https://help.hevyapp.com/hc/en-us/articles/36011896355479); [Previous vs Routine Values](https://help.hevyapp.com/hc/en-us/articles/34105442929943))
- **Routine values** (the weight/reps target columns stored in the routine) **auto-update after every workout** unless the user toggles off **"Update Routine Values"** under **"Routine Settings" on the Save Workout screen**. Exception: **rep-range targets never auto-update**. ([Previous vs Routine Values](https://help.hevyapp.com/hc/en-us/articles/34105442929943))

## 7. Set types
- Four types: **Normal, Warm-up, Failure, Drop set** — changed by **tapping the set number**, menu labels: **"W - Warmup set"**, **"F - Failure set"**, **"D-Dropset"**, "Normal Set". Types can be mixed within one exercise. ([Set Types](https://help.hevyapp.com/hc/en-us/articles/34896293707927); [exercise-programming-options](https://www.hevyapp.com/features/exercise-programming-options/))
- **Drop set rule: the rest timer does NOT start after a set if the NEXT set is a drop set.** ([Set Types](https://help.hevyapp.com/hc/en-us/articles/34896293707927))
- **Failure set rule**: log the last completed rep, not the failed attempt; **0 reps cannot be entered** (log 1 or skip + note). (same source)
- **Warm-up sets** are excluded from stats unless the **"Warm-up Sets"** setting includes them in "Total sets, volume, and personal records". ([Workout Settings](https://help.hevyapp.com/hc/en-us/articles/33882110558743))
- **Warm-up Calculator (Pro)**: three-dots → "Add Warm Up Sets" → enter target working weight → blue **"Insert Warmup Sets"** button inserts percentage-based warm-ups at the top of the exercise; "Warmup Method" editor customizes per-set percentage/reps with Add Set/Remove Set; plate rounding and dumbbell rounding preferences. ([Warm Up Calculator](https://help.hevyapp.com/hc/en-us/articles/35650921359639))

## 8. Supersets
- Create: three dots on exercise → **"+ Add To Superset"** → tap the exercise to pair. Extend to giant sets/circuits by adding more exercises to an existing superset (tap any member exercise). Multiple separate supersets allowed; **each superset gets a distinct color** for visual grouping. Remove via **"Remove From Superset"**. **No limit** on exercises per superset or supersets per workout. ([Supersets guide](https://help.hevyapp.com/hc/en-us/articles/35650286563095); [what-are-supersets](https://www.hevyapp.com/features/what-are-supersets/))
- **Smart Superset Scrolling** (setting): on marking a set complete, auto-scrolls to the next exercise in the superset, wrapping back to the first (bench → pulldown → face pull → bench). ([Supersets guide](https://help.hevyapp.com/hc/en-us/articles/35650286563095))
- **Circuit recipe** (official): one superset containing all circuit exercises + a rest timer only on the final exercise to mark the end of a round. ([Circuits article](https://help.hevyapp.com/hc/en-us/articles/36954623739415))

## 9. Rest timer
- Auto-starts when a set of that exercise is marked complete; countdown displays near the top of the exercise, below the note area. Durations roughly **5 seconds to 5 minutes** via scroll picker; per-exercise and can be off. ([workout-rest-timer](https://www.hevyapp.com/features/workout-rest-timer/); [Rest Timer article](https://help.hevyapp.com/hc/en-us/articles/35385404949143))
- Active-timer controls: **"-15" / "+15"** buttons (15-second increments) and **skip** (skip confirmed on the Live Activity widget). ([workout-rest-timer](https://www.hevyapp.com/features/workout-rest-timer/); [Live Activity](https://help.hevyapp.com/hc/en-us/articles/35649846517399))
- On reaching zero, Hevy notifies (sound + notification). **Sounds settings**: 5 timer sounds; three independent volumes (high/normal/low/off) for **timer**, **check-set sound**, and **PR alert**. ([Rest Timer article](https://help.hevyapp.com/hc/en-us/articles/35385404949143))
- **Default Rest Timer** setting applies to all *newly added* exercises only — never retroactively to existing routine exercises. (same source)

## 10. RPE
- Off by default; toggle: Profile → Settings → Workouts → **RPE Tracking**. Adds an **RPE column** for rep-based exercises **during live logging only — not in the routine builder**. ([How to Use RPE](https://help.hevyapp.com/hc/en-us/articles/35687721776663); [how-to-write-sets-and-reps](https://www.hevyapp.com/features/how-to-write-sets-and-reps/))
- Tap RPE cell → **"Log Set RPE"** picker, scale **6–10 with half-steps** (6, 7, 7.5, 8, 8.5, 9, 9.5, 10), each with an RIR description ("RPE 8 – could have done 2 more reps") → "Done". Sets may be left blank. RIR is not a separate mode. ([RPE vs RIR](https://help.hevyapp.com/hc/en-us/articles/34490600233111))

## 11. Notes (two kinds per exercise + workout-level description)
- **Routine notes**: attached to an exercise in a routine; appear every time the routine is logged; NOT shown on the completed/shared workout. Edited only by editing the routine. ([Exercise notes](https://help.hevyapp.com/hc/en-us/articles/34463684392983))
- **Workout notes**: written on an exercise during a live session; saved with that workout (publicly visible). Next time the exercise is used, the old note shows **greyed-out and non-editable**; writing over it replaces it, otherwise the greyed note disappears after that session. (same source)

## 12. Duration tracking & finish flow
- Stopwatch runs while workout in progress; pausable. Tap **"Finish"** (top-right) → **"Save Workout" screen**: adjust **Date, time, and Duration** (scroll list) — enabling backdated manual logging; add a **photo**; set **Visibility** (Private / Everyone); **"Routine Settings"** with the "Update Routine Values" toggle; then **"Save"**. ([Adjust duration](https://help.hevyapp.com/hc/en-us/articles/34513981310615); [Log Previous Workouts](https://help.hevyapp.com/hc/en-us/articles/35687878672663); [Privacy article](https://help.hevyapp.com/hc/en-us/articles/34461853165079); [Previous vs Routine Values](https://help.hevyapp.com/hc/en-us/articles/34105442929943))
- If structural changes were made during a routine-based workout (**reorder, add/remove exercises or sets**), a prompt offers **"Update Routine"** vs **"Keep Original Routine"**. It does NOT appear for rep/weight changes (those follow the routine-values setting). ([Update Routine vs Keep Original](https://help.hevyapp.com/hc/en-us/articles/38387296276375))
- Post-save **summary** shows: volume load, sets done, duration, ordinal workout count, active streak, and a **highlight of PRs achieved**. ([track-workouts](https://www.hevyapp.com/features/track-workouts/))
- Backdated workouts sort by their set date in profile history but by log order on the Home feed. ([Log Previous Workouts](https://help.hevyapp.com/hc/en-us/articles/35687878672663))

## 13. PR system (detection on finish + live)
- **PR types**: Heaviest Weight, Best 1RM (estimated from weight×reps of a set), Best Set Volume (weight×reps), Best Session Volume, Longest Distance, Longest Time, Best Time, Best Set (most reps), Most Session Reps. ([PRs and Set Records](https://help.hevyapp.com/hc/en-us/articles/35649367857175))
- **PRs available per exercise type** (exact matrix): Weight & reps → Heaviest Weight, Best 1RM, Best Set Volume, Best Session Volume; Assisted → Best total reps, Most reps (set); Bodyweight reps → Best set, Most session reps; Weighted bodyweight → Heaviest weight, Best Set Volume; Duration → Best Time; Weighted duration → Heaviest weight, Best time; Distance & duration → Longest Distance, Longest Time. (same source)
- **Live PR notification** (toggleable): checking off a PR set shows a **banner** naming the PR; on save, a **medal** marks the set and the workout detail. ([Live PR Notifications](https://help.hevyapp.com/hc/en-us/articles/36012016405655); [Set Records vs PRs](https://help.hevyapp.com/hc/en-us/articles/38279531346455))
- **Set Records** are separate: heaviest weight ever lifted at each rep count, shown as a table at the bottom of an exercise's summary screen; they never trigger banners/medals. (same sources)

## 14. Editing / repeating past workouts
- **Edit**: completed workout → 'Workout Detail' page → three dots → **"Edit Workout"** → change anything incl. Duration → "Save". ([Adjust duration](https://help.hevyapp.com/hc/en-us/articles/34513981310615))
- **Repeat**: past workout → three dots → **"Copy workout"** starts a new live workout from it. ([Log a Workout](https://help.hevyapp.com/hc/en-us/articles/35361530647959))
- **Delete**: workout three dots → "Delete Workout" + confirm; routine three dots → "Delete Routine" + confirm; deleted workouts unrecoverable. ([Delete article](https://help.hevyapp.com/hc/en-us/articles/38030200802583))

## 15. Full workout-settings inventory (for clone settings screen)
Timer Sound (5 options) · Timer Volume (high/normal/low/off) · Default Rest Timer · First Day of the Week · Previous Workout Values (any workout / same routine) · Warm-up Calculator (Pro) · Warm-up Sets count-in-stats toggle · Keep Awake During Workout · Plate Calculator · RPE Tracking · Smart Superset Scrolling · Inline Timer · Live Personal Record Notification + Live PR Volume. ([Workout Settings Preferences](https://help.hevyapp.com/hc/en-us/articles/33882110558743))
- **Plate calculator behavior**: on a barbell exercise's weight field, a **"Calculator"** button appears above the keyboard; user picks available plates + bar; shows the plate arrangement, or the **closest achievable weight** if exact is impossible; "Manage" → "+ Custom Bar" / "+ Custom Plate". ([Plate Calculator](https://help.hevyapp.com/hc/en-us/articles/34518876511383))
- **Custom exercises**: Profile → Exercises → "Create" → set Equipment, Primary/Other muscle groups, Exercise type, optional image → Save; exercises can be duplicated to reset history. ([Build a Workout Program](https://help.hevyapp.com/hc/en-us/articles/34953606698903); [Reset Data](https://help.hevyapp.com/hc/en-us/articles/35119576252951))
