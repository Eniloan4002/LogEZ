# social-surface

Confidence: high — every existing-feature claim traces to an official hevyapp.com feature page fetched directly, with help-centre and app-store facts cross-checked via search snippets; absence claims (no clubs/groups/challenges) rest on the official complete feature list rather than a direct "we don't have this" statement, so they are the least ironclad part.

Gaps: 1) help.hevyapp.com (Zendesk) returned 403 to WebFetch/curl and rendered blank in the browser pane, so help-centre facts (Follows notification toggle, "remove social media features" behavior, private-workout visibility) come from search-result snippets of those articles, not full reads. 2) The official OpenAPI spec behind api.hevyapp.com/docs/ could not be extracted (Swagger UI shell only; spec URL not discoverable), so the API endpoint list relies on third-party SDK/MCP READMEs. 3) Whether likes/comments trigger push or in-app notifications, and whether a dedicated notifications-inbox screen exists, is undocumented in readable sources. 4) Exact bottom-tab-bar composition (whether Exercises is a distinct tab) was only partially confirmed — the tutorial page names Home, Workout, and Profile tabs. 5) Clubs/groups/challenges absence is inferred from the official feature list, not an explicit denial.

# Hevy Social/Community Feature Surface (as of Aug 2026)

Hevy's own marketing describes the app as three pillars: "workout logging, progress tracking, and socializing" (hevyapp.com/features/). Below is the full verified social surface, per-feature app location, and what remains when social is stripped.

## 1. Social features that EXIST (verified on official pages)

### Home feed ("Following" feed) — Home tab
- The Home tab is a social content feed showing workouts posted by users you follow: session name, description, stats overview (duration, training volume, number of PRs, average heart rate if logged on a smartwatch), plus photos/videos uploaded with the workout (hevyapp.com/features/content-feed/).
- Each feed post has likes and comments beneath it; you can like the session, comment, like other users' comments, reply directly to comments, and post clickable hyperlinks in comments (content-feed page).
- An upward-arrow icon on a feed post shares the session outside Hevy (link via messaging apps etc.) (content-feed page).
- A horizontal "suggested athletes" carousel appears while scrolling the feed (follow / remove / view profile). It can be hidden: Profile tab > gear icon > Privacy & Social > Hide Suggested Users (content-feed and social-features pages).

### Discover feed — Home tab (mode toggle)
- Accessed from the Home tab by tapping the grey "Home" button top-right and switching to "Discover"; shows recent workouts from users you do NOT follow, with inline "+ Follow", and per-workout "Save as Routine" / "Copy Workout" via the three-dot menu (hevyapp.com/features/discovery-feed/).
- Per Hevy's help article "How to keep my information private" (read via search snippet — page blocks direct fetch): "the Discover feed will remain on the Home screen, and as of right now, there is no way to remove this feature."

### Following / followers
- Follow from profiles (blue "Follow" button), from the Discover feed inline, from the suggested carousel, or via search (magnifying glass) (discovery-feed, user-profiles pages).
- Profiles show workout count, follower count, following count with tappable lists (user-profiles page).
- Private profiles exist: "You will need to send a follow request and be accepted" to see anything; set via Profile tab > gear > Privacy & Social > Private Profile (user-profiles page).

### User profiles — reached from Home feed, carousel, Discover, or search
- Contents: bio + external social links, media gallery (workout photos/videos), stats, activity graph ("week-to-week activity from the last three months"), the user's saved routines (viewable and saveable by others), and recent workouts (user-profiles page).
- Three-dot profile menu: Unfollow, "Workout Notifications – get a push notification each time the user saves a workout", Report, Block (user-profiles page).
- "Compare" button: side-by-side stats vs that user — muscle splits, number of workouts, training time, volume, and an "Exercises in Common" head-to-head section; ranges 30 days / 3 months / year / all time (user-profiles, social-features pages).

### Likes & comments
- On workouts in both feeds and on profiles; includes comment replies, comment likes, and links in comments (content-feed page). Verified to exist today.

### Leaderboards — Profile tab > Statistics > Leaderboard Exercises
- Ranks heaviest weight lifted on 38 barbell/dumbbell compound movements (bench, squat, deadlift, OHP, Olympic lifts); ONLY people you follow appear ("so long as they've done the specific exercise") — there is no global/public leaderboard; no date limit on records; also reachable as a "Leaderboard" tab inside any of those 38 exercises in the exercise library; tapping a ranked person opens the workout where they set the record (hevyapp.com/features/gym-leaderboard/).

### Workout sharing (links + images)
- Share links: workouts, routines, folders, and user profiles can each generate a hyperlink that opens a public page on hevy.com (share-folders-routines page + social guide snippets). This implies public web profiles/workout pages hosted on hevy.com.
- Social Media Shareables (image generation): auto-generated graphics for PRs, training volume, muscle distribution chart, monthly report, consistency calendar, exercise frequency; light/dark/transparent backgrounds; transparent ones usable as resizable/tiltable overlay stickers on Instagram stories. Access: post-workout screen "Stories" button or "More"; or Profile tab > past workout > three dots > "Share Workout"; monthly report via Profile > Statistics > Monthly Report (hevyapp.com/features/shareable/).
- Strava integration: automatically posts your workouts to Strava (social-features page).

### Friend discovery / contacts sync
- Hevy lets you connect your mobile contacts to see which contacts use Hevy, and invite friends via WhatsApp, Messenger, Facebook, X, contact list, or a generated invite link (official social pages, surfaced via search summary of hevyapp.com social-features/content-feed pages).

### Media upload (social-facing)
- Up to 3 photos or 2 photos + 1 video per workout, shown in the feed and on the profile media gallery; distinct from private progress photos. Visibility follows profile privacy: public profile = everyone sees media; private = followers only (social-features, user-profiles pages).

### Notifications about others' activity
- Per-user "Workout Notifications" push toggle in a profile's three-dot menu (user-profiles page).
- Account Settings > Notifications includes a "Follows" toggle (notification when someone follows you), per the help-centre article "Account Settings Preferences" (search snippet; page blocks direct fetch). Toggles for like/comment notifications are not documented in sources I could read (see gaps).

### Athlete workouts
- Browsing "other gym-goers' and athletes' workouts" for ideas via the feeds; save-as-routine / copy-workout; no verification-badge system documented (hevyapp.com/features/athlete-workouts/).

## 2. Features that do NOT exist in Hevy (verified absence, moderate confidence)
- **Clubs / groups / communities**: absent from Hevy's complete official feature list (hevyapp.com/features/ groups everything under Progress Tracking / Workout Logging / Social / Coach / Settings; no clubs/groups item) and no official page or search result describes one. "Community Updates" on hevyapp.com is a product-news blog page, not an in-app feature.
- **Challenges**: not on the official feature list.
- **Global/public leaderboards**: leaderboard is explicitly follow-scoped only (gym-leaderboard page).
- Note: the "Strength Level" feature (help article "Strength Level Feature: How Hevy Compares Your Strength by Exercise") compares you against population strength standards — statistical, not social; it can stay in a local-only clone.
- **Live PR notification is NOT social**: it is a personal in-workout banner only; no evidence followers are notified of PRs (hevyapp.com/features/live-pr/).

## 3. What remains of each screen once social is removed
- **Home tab**: Hevy's own privacy guidance states that with a private profile and no accepted followers, "this will remove all social media features from the app, and only your workouts will appear in the Home feed" (help article 34461853165079, via snippet). So the de-socialized Home tab is your own workout history feed — exactly what a local clone's home screen should be. The Discover toggle and suggested-users carousel are the residual social elements to delete (Discover cannot be disabled in real Hevy; suggested users can via Hide Suggested Users).
- **Workout tab**: routines, folders, start-empty-workout — no social content, keep as-is (hevy-tutorial page). The only social bits are "shared with me" routine links.
- **Profile tab**: personal analytics/statistics, body measurements, workout history, settings. Social elements to remove: follower/following counts + lists, Leaderboard Exercises section under Statistics, the share arrows on workouts, and Privacy & Social settings block.
- **Exercise library / exercise detail**: keep charts + records; remove the "Leaderboard" tab inside the 38 leaderboard exercises.
- **Workout detail screens**: remove like/comment rows, "Stories"/share buttons; keep stats, media (as private progress media), notes, PRs.
- **Settings**: remove Privacy & Social section, social notification toggles (Follows, per-user Workout Notifications), contact sync/invite flows, Strava integration.

## 4. Account/cloud dependency implications
- **Account required**: the app gates usage behind Log in / Sign Up (hevyapp.com/hevy-tutorial/). All social features, sync, and share links depend on the account.
- **Offline logging exists**: Hevy lists "Workout Offline" as a homepage feature, and Hevy Coach docs state clients "can log individual workouts without internet access as long as the latest version of the program is saved in the app. Once they are back online, any saved workouts will sync to their profile automatically" (hevycoach.com/features/client-app/). So Hevy is offline-tolerant but cloud-backed — a local-only clone simply makes the local store the source of truth.
- **Cloud-dependent surfaces**: feed/Discover content, profiles, likes/comments, leaderboards, compare, share links (public hevy.com pages), media hosting, cross-device sync (phone / hevy.com web app / Apple Watch / Wear OS), Strava, and the coach chat features.
- **Public API**: api.hevyapp.com, docs at api.hevyapp.com/docs/ (Swagger UI); API key is Pro-only, generated at hevy.com/settings?developer, sent in an `api-key` header; documented resources cover workouts, routines, exercise templates, routine folders, and webhook subscriptions for workout events — no social endpoints (feed/follows/likes/comments) appear in any client SDK or docs found (github.com/chrisdoc/hevy-mcp, github.com/ewright3/hevy-py READMEs via search). Irrelevant to a local clone except as schema inspiration.
