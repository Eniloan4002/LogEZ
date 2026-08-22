Confidence: high — the formula, table, clamp, zero-guard, and rounding were read verbatim from Hevy's own production web-app bundle at hevy.com (first-party code, not community reverse-engineering), and its consumption by the 1RM graph/Best-1RM PR was read from the exercise-page chunk of the same bundle.

Gaps: 1) This is the hevy.com WEB app's code; the Android/iOS apps were not decompiled, so exact mobile parity is assumed (very likely, since the module is Hevy's shared domain library and web/app show the same stats), not proven. 2) Whether Hevy's backend computes the stored `best_1rm` PR server-side with the identical table (vs. the client) was not verified — API responses weren't inspected since that needs an authenticated account. 3) The former marketing page hevyapp.com/1rm-calculator/ now returns 404, so no public Hevy calculator page could be checked for a stated formula. 4) The Zendesk help center blocks plain fetchers (403 via WebFetch, Firecrawl scrape failed); articles were retrieved through the public Zendesk JSON API in a real browser instead — content obtained in full, so no informational gap, noted only as method. 5) In-app rounding granularity on mobile (1 decimal vs whole number in some views) wasn't verified; the web chart uses 1 decimal after unit conversion.

# Hevy's exact 1RM formula — recovered from Hevy's own production code

Hevy's official docs never name the formula, but the formula ships in plain sight in the hevy.com web app's JavaScript bundle (Next.js chunk `pages/_app-7433987e0e93e13e.js`, webpack module `6752`, part of Hevy's shared domain library that also defines `setVolume`, `userExerciseSetWeight`, streaks, etc.). This is a first-party primary source.

## The formula: a percentage lookup table, NOT Epley/Brzycki

Verbatim from the bundle (source: hevy.com `_app` chunk):

```js
n.oneRepMaxPercentageMap = {
  1:1, 2:.97, 3:.94, 4:.92, 5:.89, 6:.86, 7:.83, 8:.81, 9:.78, 10:.75,
  11:.73, 12:.71, 13:.7, 14:.68, 15:.67, 16:.65, 17:.64, 18:.63, 19:.61, 20:.6,
  21:.59, 22:.58, 23:.57, 24:.56, 25:.55, 26:.54, 27:.53, 28:.52, 29:.51, 30:.5
};
let O = (e, a) => {          // e = weight, a = reps
  if (0 === a) return 0;
  let t = a > 30 ? .5 : n.oneRepMaxPercentageMap[a];
  return e / t
};
n.oneRepMax = O;
```

So: **estimated 1RM = weight / percentage[reps]**, where `percentage` is the fixed 30-entry table above.

Properties (all read directly from the code):
- **0 reps → 1RM = 0** (explicit guard).
- **No upper rep cutoff where 1RM stops being estimated.** Instead, reps > 30 clamp to the 50% factor, i.e. 1RM = weight x 2 is the ceiling multiplier. A 100 kg x 50-rep set still yields a 200 kg estimated 1RM.
- **An actual 1-rep set gets factor 1.0**, so its "estimated" 1RM equals the weight lifted exactly. There is **no special override for true singles** — Best 1RM is simply the max of `weight/factor(reps)` over all sets, so a multi-rep set can out-rank an actual heavier-than-nothing single (e.g. 100 kg x 10 → 133.3 kg beats an actual 120 kg x 1 → 120 kg).
- **Relationship to named formulas:** for 1–10 reps the table is exactly the **Brzycki** factor `(37 - reps)/36` rounded to 2 decimals (r=2: 35/36=.9722→.97; r=5: 32/36=.8889→.89; r=10: 27/36=.75). From 11 reps onward it diverges from Brzycki (r=11 Brzycki .722 vs table .73; r=15 Brzycki .611 vs table .67) and follows a flatter hand-tuned descent of 1–2 points per rep down to .50 at 30. It is NOT Epley anywhere (Epley r=5 would be .857, table has .89). So the correct description is: **"Brzycki-rounded for 1–10 reps, custom flattened table for 11–30, clamped at 50% beyond 30."**

## Rounding / display behavior

From the same module and the exercise-page chunk (`pages/exercise/[[...exerciseTemplateId]]-*.js`):
- The raw division is not rounded internally. For display, the value goes through unit conversion (kg→user's weight unit) and then `roundToOneDecimal = e => Math.round(10*e)/10` — i.e. **shown to 1 decimal place** on the web exercise charts ("Best One Rep Max" / "One Rep Max" graph). (Module also defines roundToTwoDecimal and roundToWholeNumber; the 1RM chart path uses roundToOneDecimal.)

## How the graph and PR consume it (exercise chunk, verbatim behavior)

- **"One Rep Max" graph**: sets are grouped by `workout_short_id`; within each workout, sets are sorted by `oneRepMax(userExerciseSetWeight(set), reps)` descending and the top set becomes that workout's data point (date = workout start_time). One point per workout, not per set.
- **"Best One Rep Max" (Best 1RM PR)**: max of `oneRepMax(...)` over ALL sets ever, same rounding.
- The 1RM chart is generated **only for `weight_reps` exercise type** (the `switch` on exercise type only emits the oneRepMax chart in the `"weight_reps"` case; bodyweight/assisted/duration types get other charts). This matches the official PR help article's table, which lists "Best 1RM" only for "Weight & reps" exercises (source: help.hevyapp.com article 35649367857175).
- Weight input is `userExerciseSetWeight`, which for plain weight_reps is just `weight_kg` (its bodyweight-adding branches apply to bodyweight-type exercises and are called with the include-bodyweight flag = false on the 1RM chart path).
- PR type key in code/API payloads: `best_1rm` (i18n title "Best 1RM" via `web.prTitles.best1rm`); chart labels: "One Rep Max" / "Best One Rep Max".

## What official docs say (for the clone's help text)

- Official 1RM article (help.hevyapp.com article 36954464726167, updated 2026-08-17): "1RM is a measurement of how much you could lift if you only did 1 single rep with proper form... Hevy calculates an estimate of what your 1RM would be based on what was lifted in a given set." No formula named.
- Official PR article (35649367857175): "Best 1RM: 1RM (One Rep Max) uses reps and weight from a set to estimate the max weight you could lift for a single rep. This is the highest 1RM you've ever achieved." Also: the Set Records table "doesn't show the percentage of your one-rep max (1RM). Instead, it displays the heaviest weight you've lifted for that specific number of repetitions."

## Drop-in Kotlin equivalent for the clone

```kotlin
val ONE_RM_PCT = doubleArrayOf(/*1..30*/ 1.0,0.97,0.94,0.92,0.89,0.86,0.83,0.81,0.78,0.75,
  0.73,0.71,0.70,0.68,0.67,0.65,0.64,0.63,0.61,0.60,
  0.59,0.58,0.57,0.56,0.55,0.54,0.53,0.52,0.51,0.50)
fun oneRepMax(weightKg: Double, reps: Int): Double =
  if (reps == 0) 0.0 else weightKg / (if (reps > 30) 0.50 else ONE_RM_PCT[reps - 1])
// display: round(convertToUserUnit(x) * 10) / 10
```
