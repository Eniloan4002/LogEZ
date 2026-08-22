# api-data-model

Confidence: high — the entire official OpenAPI spec was read verbatim from api.hevyapp.com itself (embedded in swagger-ui-init.js), and endpoint/auth details were independently corroborated by two community integrations that call the live API.

Gaps: Could not verify against live responses (no API key; API is Pro-only), so doc-vs-reality quirks remain unconfirmed: the `rest_seconds: string` typing in the Routine response (almost certainly an int in practice), the inconsistent exercise-template ID formats (uuid example vs 8-char hex examples vs integer id returned by POST /v1/exercise_templates), and whether `supersets_id` (plural) is really the response field name at runtime. The exact numeric values of the free-tier routine limit and custom-exercise limit are not in the spec (only the 403 errors proving they exist). Rate limits are undocumented. The interactive /docs/ page itself would not render for the fetcher (JS-only), so the spec was recovered from its init script instead — same origin, so no fidelity loss. The private/internal mobile-app API (which hevy-unofficial wraps) was not explored; findings cover only the public /v1 API, which is the appropriate inspiration surface for a Room model.

All facts below come from the official OpenAPI 3.0.0 spec embedded in https://api.hevyapp.com/docs/swagger-ui-init.js (fetched raw and read in full), unless another source is cited. Base URL is `https://api.hevyapp.com`; all paths are versioned `/v1/...`.

## Auth model (context only)
- Every endpoint requires a header literally named `api-key` (schema `type: string, format: uuid`), required on every operation. Confirmed in code by HA-hevy's api.py which sets an `"api-key"` header.
- API is Hevy Pro-only; keys issued at `https://hevy.com/settings?developer` (spec info block). Spec version is "0.0.1" and the intro warns the structure may change.
- No rate limits are documented anywhere in the spec.

## Complete verified endpoint list (24 operations)
Workouts:
- `GET /v1/workouts` — paginated; query `page` (default 1), `pageSize` (default 5, "Max 10"). 200 → `{page: int, page_count: int, workouts: Workout[]}`
- `POST /v1/workouts` — body `PostWorkoutsRequestBody`; 201 → `Workout`
- `GET /v1/workouts/count` — 200 → `{workout_count: int}`
- `GET /v1/workouts/events` — query `page`, `pageSize` (Max 10), `since` (default `"1970-01-01T00:00:00Z"`). Returns update/delete events "newest to oldest" for local-cache sync. 200 → `PaginatedWorkoutEvents`
- `GET /v1/workouts/{workoutId}` — 200 → `Workout`, 404 if not found
- `PUT /v1/workouts/{workoutId}` — body is the same `PostWorkoutsRequestBody`; 200 → `Workout`

Routines:
- `GET /v1/routines` — page/pageSize (Max 10); 200 → `{page, page_count, routines: Routine[]}`
- `POST /v1/routines` — body `PostRoutinesRequestBody`; 201 → `Routine`; **403 "Routine limit exceeded"** (free-tier routine cap exists)
- `GET /v1/routines/{routineId}` — 200 → `{routine: Routine}` (note: wrapped, unlike GET workout)
- `PUT /v1/routines/{routineId}` — body `PutRoutinesRequestBody`; 404 "Routine doesn't exist or doesn't belong to the user"

Exercise templates:
- `GET /v1/exercise_templates` — page/pageSize (**Max 100** here, unlike others); 200 → `{page, page_count, exercise_templates: ExerciseTemplate[]}`
- `POST /v1/exercise_templates` — create custom exercise, body `CreateCustomExerciseRequestBody`; 200 → `{id: integer}`; **403 example error string `"exceeds-custom-exercise-limit"`** (custom-exercise cap exists)
- `GET /v1/exercise_templates/{exerciseTemplateId}` — 200 → `ExerciseTemplate`

Routine folders:
- `GET /v1/routine_folders` — page/pageSize (Max 10); 200 → `{page, page_count, routine_folders: RoutineFolder[]}`
- `POST /v1/routine_folders` — "The folder will be created at index 0, and all other folders will have their indexes incremented." Body `PostRoutineFolderRequestBody` = `{routine_folder: {title: string}}`; 201 → `RoutineFolder`
- `GET /v1/routine_folders/{folderId}` — 200 → `RoutineFolder`

Exercise history:
- `GET /v1/exercise_history/{exerciseTemplateId}` — optional query `start_date`, `end_date` (ISO 8601 date-time); 200 → `{exercise_history: ExerciseHistoryEntry[]}`

Body measurements:
- `GET /v1/body_measurements` — page/pageSize (default 10, Max 10); 200 → `{page, page_count, body_measurements: BodyMeasurement[]}`
- `POST /v1/body_measurements` — one entry per date; **409 if an entry already exists for that date**
- `GET /v1/body_measurements/{date}` — date path param format `YYYY-MM-DD`
- `PUT /v1/body_measurements/{date}` — "All fields are overwritten; omitted fields are set to null."

User:
- `GET /v1/user/info` — 200 → `UserInfoResponse` = `{data: UserInfo}`; `UserInfo` = `{id: string (uuid), name: string, url: string (public profile URL, e.g. https://hevy.com/user/jhon)}`

**There are NO DELETE endpoints for any resource** — confirmed both by the spec (absent) and explicitly by the hevy-mcp README: no delete for workouts, routines, routine folders, exercise templates, or body measurements. Deletion is only observable via `/v1/workouts/events`.

## Response schemas (exact field names/types from the spec)

### Workout
```
id: string (uuid example "b459cba5-cd6d-463c-abd6-54f8eafcadcb")
title: string
routine_id: string          // the routine this workout was performed from
description: string
start_time: string (ISO 8601)
end_time: string (ISO 8601)
updated_at: string (ISO 8601)
created_at: string (ISO 8601)
exercises: [ WorkoutExercise ]
```

### WorkoutExercise (inline in Workout; standalone `Exercise` schema is identical)
```
index: number               // order of the exercise in the workout
title: string               // e.g. "Bench Press (Barbell)"
notes: string
exercise_template_id: string  // examples "05293BCA", "D04AC939" (8-char hex)
supersets_id: number | null   // NOTE: plural "supersets_id" in RESPONSES; null = not in a superset; superset members share an integer id (example 0)
sets: [ Set ]
```

### Set (workout response)
```
index: number               // order of the set within the exercise
type: string                // "one of 'normal', 'warmup', 'dropset', 'failure'"
weight_kg: number | null
reps: number | null
distance_meters: number | null
duration_seconds: number | null
rpe: number | null          // example 9.5
custom_metric: number | null  // "Currently only used to log floors or steps for stair machine exercises"
```

### PostWorkoutsRequestBody (create/update workout — request side)
```
{ workout: {
    title: string
    description: string | null
    start_time: string        // "2024-08-14T12:00:00Z"
    end_time: string
    is_private: boolean       // request-only field, not in Workout response
    exercises: [{
      exercise_template_id: string
      superset_id: integer | null   // NOTE: singular "superset_id" in REQUESTS
      notes: string | null
      sets: [{
        type: enum ["warmup","normal","failure","dropset"]
        weight_kg: number | null
        reps: integer | null
        distance_meters: integer | null
        duration_seconds: integer | null
        custom_metric: number | null
        rpe: number | null, enum [6, 7, 7.5, 8, 8.5, 9, 9.5, 10]   // exact allowed RPE values
      }]
    }]
}}
```

### Routine
```
id: string (uuid)
title: string
folder_id: number | null    // null = default "My Routines" folder
updated_at / created_at: string (ISO 8601)
exercises: [{
  index: number
  title: string
  rest_seconds: string      // spec types it "string" with example 60 — likely a doc typo; request schemas type it integer
  notes: string
  exercise_template_id: string
  supersets_id: number | null
  sets: [{
    index: number
    type: string            // 'normal' | 'warmup' | 'dropset' | 'failure'
    weight_kg: number | null
    reps: number | null
    rep_range: {start: number|null, end: number|null} | null   // routine sets ONLY — target rep range (example 8–12); workout sets have no rep_range
    distance_meters: number | null
    duration_seconds: number | null
    rpe: number | null
    custom_metric: number | null
  }]
}]
```
Request side (`PostRoutinesRequestBody` / `PutRoutinesRequestBody`): `{routine: {title, folder_id (number|null, POST only in the example; PUT also has it), notes, exercises: [{exercise_template_id, superset_id, rest_seconds (integer|null), notes, sets: [...same as workout request sets but with rep_range instead of rpe]}]}}`. Note asymmetry: routine request sets have `rep_range` but no `rpe`; workout request sets have `rpe` but no `rep_range`. Routines use `notes`, workouts use `description`.

### ExerciseTemplate
```
id: string                  // uuid in schema example, but 8-char hex ("D04AC939") everywhere else; POST custom exercise returns {id: integer} — ID formats are inconsistent in the docs
title: string               // "Bench Press (Barbell)"
type: string                // e.g. "weight_reps" (CustomExerciseType values)
primary_muscle_group: string    // e.g. "chest"
secondary_muscle_groups: string[]
equipment_category: EquipmentCategory
is_custom: boolean
```

### CreateCustomExerciseRequestBody
`{exercise: {title, exercise_type: CustomExerciseType, equipment_category: EquipmentCategory, muscle_group: MuscleGroup, other_muscles: MuscleGroup[]}}` — note request uses `exercise_type`/`muscle_group`/`other_muscles` vs response's `type`/`primary_muscle_group`/`secondary_muscle_groups`.

### Enums (exact, from spec)
- Set `type`: `warmup`, `normal`, `failure`, `dropset` (4 values only — no "left/right", no "timed" variants in the public API)
- `rpe`: `6, 7, 7.5, 8, 8.5, 9, 9.5, 10`
- `CustomExerciseType` (exercise measurement style, 8 values): `weight_reps`, `reps_only`, `bodyweight_reps`, `bodyweight_assisted_reps`, `duration`, `weight_duration`, `distance_duration`, `short_distance_weight`
- `MuscleGroup` (20 values): `abdominals`, `shoulders`, `biceps`, `triceps`, `forearms`, `quadriceps`, `hamstrings`, `calves`, `glutes`, `abductors`, `adductors`, `lats`, `upper_back`, `traps`, `lower_back`, `chest`, `cardio`, `neck`, `full_body`, `other`
- `EquipmentCategory` (9 values): `none`, `barbell`, `dumbbell`, `kettlebell`, `machine`, `plate`, `resistance_band`, `suspension`, `other`

### RoutineFolder
```
id: number (example 42)     // numeric, unlike uuid routine/workout ids
index: number               // order of the folder in the list
title: string               // "Push Pull 🏋️‍♂️"
updated_at / created_at: string (ISO 8601)
```

### ExerciseHistoryEntry (flattened set-level history rows)
```
workout_id: string, workout_title: string,
workout_start_time / workout_end_time: string (ISO 8601),
exercise_template_id: string,
weight_kg: number|null, reps: integer|null, distance_meters: integer|null,
duration_seconds: integer|null, rpe: number|null, custom_metric: number|null,
set_type: string            // "(warmup, normal, failure, dropset)" — named set_type here, not type
```

### Workout sync events (`PaginatedWorkoutEvents`)
```
{page: int, page_count: int, events: [ UpdatedWorkout | DeletedWorkout ]}
UpdatedWorkout: {type: "updated", workout: Workout}
DeletedWorkout: {type: "deleted", id: string, deleted_at: string}
```
This updated/deleted event stream (with `since` cursor + `updated_at` timestamps on every entity) is Hevy's sync design — useful pattern even for a local-only Room model (soft-delete + updated_at columns).

### BodyMeasurement (required: `date`; all else `number | null`)
`date` (YYYY-MM-DD, unique key — POST returns 409 on duplicate date), `weight_kg`, `lean_mass_kg`, `fat_percent`, `neck_cm`, `shoulder_cm`, `chest_cm`, `left_bicep_cm`, `right_bicep_cm`, `left_forearm_cm`, `right_forearm_cm`, `abdomen`, `waist`, `hips`, `left_thigh`, `right_thigh`, `left_calf`, `right_calf`. (Note the spec itself drops the `_cm` suffix on the last 8 fields.)

## Room-model-relevant structural takeaways (all derived from the verified spec)
- Hierarchy: Workout → exercises[] (ordered by `index`, each pointing at an `exercise_template_id`, optional shared superset id) → sets[] (ordered by `index`, typed warmup/normal/dropset/failure). Routines mirror workouts exactly, plus `rest_seconds` per exercise and `rep_range` per set, minus start/end times.
- All weights stored canonically in kg (`weight_kg`); distance in meters; duration in seconds — unit conversion is a display concern.
- A set carries every possible metric column as nullable (`weight_kg`, `reps`, `distance_meters`, `duration_seconds`, `custom_metric`, `rpe`); which ones are populated is determined by the exercise template's `type` (the 8 CustomExerciseType values).
- Workout keeps a `routine_id` back-reference to the routine it was started from.
- Folder → Routine is a nullable FK (`folder_id`); null means the default "My Routines" bucket; folders and their routines are user-ordered via `index` (new folders inserted at index 0).

Sources: [Hevy OpenAPI spec (swagger-ui-init.js)](https://api.hevyapp.com/docs/swagger-ui-init.js), [Hevy API Docs UI](https://api.hevyapp.com/docs/), [HA-hevy api.py](https://raw.githubusercontent.com/hudsonbrendon/HA-hevy/main/custom_components/hevy/api.py), [HA-hevy README](https://github.com/hudsonbrendon/HA-hevy), [hevy-mcp](https://github.com/chrisdoc/hevy-mcp), [hevy-api-client](https://github.com/chrisdoc/hevy-api-client)
