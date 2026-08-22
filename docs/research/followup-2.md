Confidence: high — three independent official Hevy sources read verbatim today all state RPE is unavailable in the consumer routine builder, and the API schema asymmetry is exactly explained by the Hevy Coach platform, which officially advertises per-set RPE targets.

Gaps: Could not surface specific r/Hevy Reddit threads (search returned no direct thread links; official pages answered the question anyway). The chrisdoc/hevy-mcp raw openapi.json path 404'd, but this was redundant since the official swagger spec was verified directly. No changelog evidence either way on whether a 2025–2026 consumer-app update added RPE to the routine builder — but the official feature pages as served today (Aug 2026) still state it is unavailable, so any such update would postdate their last revision. The Hevy Coach per-set RPE claim on the workout-program-builder page was read via search summary rather than a direct verbatim fetch; the workout-builder page was fetched verbatim and confirms the capability.

## Definitive answer: NO for the consumer app, YES for Hevy Coach (platform-dependent)

**The consumer Hevy app's routine builder does NOT support per-set RPE targets.** RPE in the consumer app (iOS/Android/web) is a live-workout-logging field only. The apparent contradiction was caused by a mis-summary of Source A — the actual page text says the opposite of what the summary claimed.

### 1. Source A re-read verbatim — it DENIES RPE in routines
https://www.hevyapp.com/features/exercise-programming-options/ states, verbatim: "While unavailable during routine building, you can enable RPE (Profile tab > Settings gear icon > Workouts > RPE Tracking enable via the toggle switch). This will allow you to log an RPE value for any set while doing a workout." The earlier summary listing "RPE targets" among routine programming options was wrong — the page lists rep ranges and set types (Warm Up, Normal, Failure, Drop) as routine-programmable, and explicitly excludes RPE.

### 2. Source B confirmed via Zendesk JSON API
https://help.hevyapp.com/api/v2/help_center/en-us/articles/35687721776663.json ("How to Use RPE"): "Enabling RPE in the settings generates an additional column for rep-based exercises while logging workouts in Hevy." Enable path: Profile > Settings > Workouts > RPE Tracking toggle. Input range is restricted: "you can only input a value between 6 and 10" (blank allowed per set, e.g. warm-ups). No mention of the routine builder anywhere in the article.

### 3. Third official page — the most explicit statement found
https://www.hevyapp.com/features/how-to-write-sets-and-reps/ states, verbatim: "Selecting a rate of perceived exertion (RPE) value for each set is only available while logging a live session but *not* while creating a routine." This is a direct, unambiguous denial from an official Hevy feature page, still live as of August 2026.

### 4. Source C (official OpenAPI spec) asymmetry re-verified — and it is real
From https://api.hevyapp.com/docs/swagger-ui-init.js:
- Routine RESPONSE set object: `index, type, weight_kg, reps, rep_range, distance_meters, duration_seconds, rpe, custom_metric` — **has `rpe`**.
- `PostRoutinesRequestSet` and `PutRoutinesRequestSet`: `type, weight_kg, reps, distance_meters, duration_seconds, custom_metric, rep_range` — **NO `rpe`**.
- `PostWorkoutsRequestSet`: `type, weight_kg, reps, distance_meters, duration_seconds, custom_metric, rpe` — has `rpe` (workouts, i.e. logged sessions, accept RPE on write).

### 5. The explanation for the asymmetry: Hevy Coach
https://hevycoach.com/features/workout-builder/ (the paid trainer web platform) states verbatim: "Our workout builder comes with a built-in RPE scale." and "Use it to instruct your client how hard to train on each set to further tailor each workout to their needs, allowing for more effective training and fatigue management." The Hevy Coach program/workout builder supports per-set target RPE alongside weight targets, rep or rep-range targets, rest periods, and set types (per https://hevycoach.com/features/workout-program-builder/ via search). So coach-authored routines CAN carry per-set RPE targets, which is why the routine RESPONSE schema includes `rpe` — a client's app must be able to display coach-assigned RPE targets — while the public API's routine write bodies (and the consumer routine builder UI) cannot set it.

### Implication for the clone's schema
For a private local-only Android clone of the consumer app: the routine-set table does not need `target_rpe` for feature parity — the consumer routine builder has no RPE column. However, since Hevy's own data model demonstrably stores RPE on routine sets (response schema) and workout sets accept `rpe` (6.0–10.0, nullable, blanks allowed), a defensible design is: `rpe` (nullable REAL) on the **workout/logged set** table (required for parity), and an optional nullable `target_rpe` on the routine-set table only if the clone wants the Hevy-Coach-style enhancement — Hevy's own storage layer clearly accommodates it. Strict consumer-app parity = drop the routine-side column, keep the logged-side one.
