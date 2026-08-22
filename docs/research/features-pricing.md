# features-pricing

Confidence: high — the core Free-vs-Pro gates and limits come from Hevy's own help-centre Pro article (comparison table read directly from the rendered DOM), the official downgrade article, and the live hevy.com/pricing page (screenshot-verified checkmark table), and all three agree with each other.

Gaps: 1) hevy.com/plans is login-gated, so in-app/regional price variants could not be checked; the $2.99/$23.99/$74.99 figures are from the public pricing page and the Hevy Trainer FAQ. 2) api.hevyapp.com/docs is a Swagger UI SPA whose content did not render via WebFetch, so endpoint list and rate limits are unverified from the primary source; Pro-only API access is corroborated by several third-party integration docs (Serval, hevy-mcp README) but not read verbatim from a Hevy page. 3) Marketing feature pages on hevyapp.com never label tiering, so "free by absence of gating" items (plate calculator, RPE, supersets, widgets, watch apps, Strava sync, CSV export) are inferred from the absence of any Pro mention across the official Pro table, pricing table, and downgrade article — none of these appear in any Pro list, but Hevy publishes no exhaustive free-feature list. 4) One Aug-2026 Play review claims a 3-routine free cap vs the official 4; official help-centre and pricing pages (both current) say 4 — unresolved anecdote. 5) Firecrawl scraping failed repeatedly (unauthenticated), and help.hevyapp.com returns 403 to WebFetch; all help-centre content was instead read through the browser pane's rendered DOM.

# Hevy Free vs Pro — verified feature inventory (August 2026)

## 1. The authoritative Free-vs-Pro gates (official sources)

From the official help-centre article "Hevy Pro Subscription: How to get Pro and What Does It Include?" (comparison table extracted verbatim from the page DOM) — https://help.hevyapp.com/hc/en-us/articles/35119778922263:

| Feature | Free Version | Pro Version |
|---|---|---|
| Routine limit | **Limit of 4** | Unlimited |
| Data history | **3 months** | All time |
| Custom exercises | **Limit of 7** | Unlimited |
| Advanced tracking | Muscle distribution (body and chart); Main exercises; Leaderboard Exercises; Monthly Report | All stats as free **+ Set count per muscle group** |
| Warm up Calculator | **Unavailable** | Available |

Note the surprising details: **muscle distribution charts and the Monthly Report are FREE**; what Pro adds in "advanced tracking" is specifically **"Set count per muscle group"** (the sets-per-muscle-group-per-week stat). The **warm-up set calculator is Pro-only** (while the plate calculator is not gated anywhere).

From the live pricing page https://hevy.com/pricing (JS-rendered; table verified by screenshot):

| Row | Free | PRO |
|---|---|---|
| Log Unlimited Workouts | ✓ | ✓ |
| Hevy Trainer | ✗ | ✓ |
| Unlimited Routines | "4 max" | ✓ |
| Unlimited Custom Exercises | "7 max" | ✓ |
| Measurement Tracking | "Limited" | ✓ |
| Unlimited Graph History | "3 months" | ✓ |

Pricing-page FAQ: "Hevy Pro includes full access to all Pro features. Create unlimited routines, create unlimited custom exercises, get unlimited graph history, and log all body measurements."

From the official downgrade article "What will happen to my account if I switch from Pro to the free version?" — https://help.hevyapp.com/hc/en-us/articles/38279350428695 (full text read from rendered DOM):
- **Routines**: free = up to 4; Pro = unlimited. On downgrade you KEEP all routines created above 4 and can still use them — you just can't create new ones.
- **Custom exercises**: free = up to 7; Pro = unlimited. Same downgrade behavior (keep + use, can't create new).
- **Measurements**: on free, "the only measurements that can be recorded are your **body weight and your waist circumference**. The rest of the measurements options (such as body fat%, neck circumference, bicep circumference, etc) are only available with a Pro subscription." On downgrade, historic Pro-only measurements remain viewable but no new entries.
- **Stats history**: free = "past **30 days, or 3 months**" chart ranges; Pro = "**1 year and all time**" ranges. On downgrade the year/all-time views lock, but history stays on servers and returns instantly on resubscribe.

## 2. Pro pricing (context)
From https://hevy.com/pricing (Aug 2026): **Monthly $2.99 / Yearly $23.99 / Lifetime $74.99 one-time**. The Hevy Trainer feature page FAQ repeats the same three price points ("just $2.99/month ($23.99/year or $74.99/lifetime)") — https://www.hevyapp.com/features/workout-plan-generator/. Refund window: 14 days (pricing FAQ). A recurring promo exists: "Hevy Pro Sale — 50% Off First Year" (annual plan, first-time subscribers only) — https://help.hevyapp.com/hc/en-us/articles/38223834432279. The paid plans checkout page is https://hevy.com/plans (login-gated).

## 3. Universal (non-tier) limits
- **150-set cap per workout and per routine, "even with the Pro version"** — official article "Hevy Set Limit Explained: Why Workouts and Routines Have a 150-Set Cap" — https://help.hevyapp.com/hc/en-us/articles/34896183826455. Sets reset each time a routine is started as a workout; a warning appears at the cap.
- **Exercise library: 400+ built-in exercises** (official help article title "Hevy Exercise Library: 400+ Exercises and Custom Exercises" — https://help.hevyapp.com/hc/en-us/articles/35688251991575). The Play listing says "Hundreds of exercises" with "+200 exercise videos".

## 4. Full feature catalog with tier marking

### FREE (explicitly confirmed by an official source)
- Unlimited workout logging (pricing table ✓ Free)
- Up to 4 routines + routine folders; sharing folders/routines (folder count not gated anywhere I found)
- Up to 7 custom exercises
- Body weight + waist circumference measurements
- Charts/stats at 30-day and 3-month ranges; muscle distribution (body + chart); "Main exercises"; Leaderboard exercises; Monthly Report (Pro table, Free column)
- HevyGPT (ChatGPT custom-GPT that generates programs and imports them as Hevy routines) — "You can use the ChatGPT integration for free. However, you're limited to four routines" on free (https://www.hevyapp.com/features/hevy-gpt/)
- CSV export of workout data and measurements (Profile > Settings > Export & Import Data) and CSV import (incl. Strong-app imports) — help articles 38001424401943 and 35687878672663; no Pro gating mentioned
- Social: feed, following, likes/comments, leaderboards, discovery feed, athlete profiles, workout comparison, social-media shareables, photo/video upload

### PRO (explicitly confirmed)
- Unlimited routines
- Unlimited custom exercises
- Full measurement tracking (body fat %, neck, bicep, etc.)
- 1-year and all-time graph/stats history
- Set count per muscle group (sets-per-muscle-group-per-week stat)
- Warm-up set calculator (percentage-based warm-up sets auto-inserted from a target weight)
- **Hevy Trainer** — adaptive AI programming: generates plans from level/goals/equipment/frequency, auto-adjusts working weights, progression, injury-aware exercise swaps ("No, Trainer is a Pro feature" — official feature-page FAQ)
- **Hevy public API** — API key generation at hevy.com/settings?developer requires an active Pro subscription; docs at api.hevyapp.com/docs (Swagger UI). Pro-only status confirmed via multiple third-party client READMEs (chrisdoc/hevy-mcp GitHub, Serval docs), not readable directly from the Swagger page.

### FREE by absence of gating (no Pro mention in any official source checked — high confidence, not explicitly labeled)
- Rest timers (customizable, automatic)
- Set types: Warm-up, Normal, Drop set, Failure (help article 34896293707927; Play listing lists them as base features)
- Supersets + Smart Superset Scrolling (auto-scroll to next superset exercise on set completion)
- **RPE tracking** — a free settings toggle: Profile > Settings > Workouts > RPE Tracking (feature page gives the path with no Pro mention); logged on a 6-10 scale per set; Aug 2026 update added an RPE color scale in the set row
- **Weight plate calculator** (distinct from the Pro warm-up calculator) — no gating found
- Previous workout values / rep+weight memory, exercise notes, live PR notifications, iOS Live Activity, 1RM calculation, progress photos, workout streaks, Year in Review
- Home-screen widgets (7 widget types: last-7-days data, calendar, calendar+data, day-of-week routine, weekly streak/rest days, aggregate data, routines) on iOS and Android — no gating found (https://www.hevyapp.com/features/home-screen-widgets/)
- **Wearables: Apple Watch and Wear OS apps** — Play listing details Wear OS: routines on watch, live phone sync, Hevy tile, heart-rate capture, duration timers, set-type marking, auto-save on reconnect. No Pro gating found anywhere. (Garmin is explicitly NOT supported — help article "Hevy and Garmin Integration Update: Why It's Not Possible Yet")
- Strava sync (listed under free community features in the 2025 features guide)
- Web app at hevy.com + "12 Workout Settings"; multi-device simultaneous login (pricing FAQ: subscription tied to account, 2 devices/platforms at once)
- Exercise programming in routines: weight (kg/lbs), rep targets or rep ranges (e.g. 6-8), duration, RPE targets, set types, per-exercise rest timers (https://www.hevyapp.com/features/exercise-programming-options/); "Progressive Overload" now supports reps-only exercises (Play "What's new", Aug 20 2026)

## 5. Google Play listing facts (https://play.google.com/store/apps/details?id=com.hevy, read Aug 2026)
- "Hevy - Gym Log Workout Tracker" by Hevy Gym Workout Tracker; "In-app purchases"; **4.9 stars, 255K reviews, 5M+ downloads**, rated 12+, updated Aug 20 2026; devices: Phone, Watch, Chromebook, Tablet; "No ads and free"; "No data shared with third parties", data encrypted in transit, deletion requestable.
- Caution: the listing's bullet "Create an unlimited amount of routines" is marketing copy that only holds for Pro; the real free cap is 4 (official help centre). One Aug-2026 user review claims free is "locked in with 3 routines" — contradicts all official sources, treat as anecdote.

## 6. Design implications for the clone (facts only)
The entire Pro tier is: quantity caps (routines 4→∞, custom exercises 7→∞), history caps (3 months→all time), measurement breadth (2 fields→all), one extra stat (sets per muscle group), one tool (warm-up calculator), Hevy Trainer AI, and API access. Everything else — logging, set types, supersets, RPE, plate calc, rest timers, folders, widgets, watch apps, social, CSV export — ships free. A local-only clone reproducing "free + Pro" behavior therefore mostly means: no caps, all-time charts, full measurements, sets-per-muscle-group stat, and a warm-up calculator.
