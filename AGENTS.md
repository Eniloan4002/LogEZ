# LogEZ — session conventions

This project is governed by the Codex-Obsidian vault. Before making changes, read:

- `/Users/ladi/Documents/Claude/Claude-Obsidian/CLAUDE.md` — the operating manual (logging protocol, sync directive, workflow)
- `/Users/ladi/Documents/Claude/Claude-Obsidian/11-Projects/logEZ/` — this project's brief, rules, decisions, lessons, and the three append-only logs (`prompt-log.md`, `response-log.md`, `changelog.md`)

Non-negotiables that sessions keep missing without this pointer:

1. **Log every project interaction** — append the Owner's prompt verbatim to `prompt-log.md` (`[P-###]`), a response entry to `response-log.md` (minor = one line; major = detailed; uncertain → major), and a `changelog.md` entry for anything actually shipped. Append-only, never rewrite.
2. **Debug APKs are never delivered as `app-debug.apk`** — rename to `logEZ-debug<major>.<iteration>.apk` per `20-Patterns-Engineering/mobile-apk-versioned-debug-naming.md` (`<major>` = count of shipped milestones from `changelog.md`, sub-lettered milestones count separately; `<iteration>` resets to 1 each new major). Record the exact filename in `response-log.md`.
3. **"Read/check the vault" = full two-way sync** — update all three logs AND commit pending repo changes in logically-scoped commits (imperative messages explaining why; build/tests verified green before committing). Surface unpushed commits; don't push unasked.
4. Hard checkpoints: stop for Owner approval at phase/milestone boundaries unless the Owner explicitly waives it for a task.

Repo notes: `docs/` is deliberately gitignored (ADRs live there, local-only). Release signing reads gitignored `/keystore.properties`; never commit keystores.


<claude-mem-context>
# Memory Context

# [LogEZ] recent context, 2026-09-15 1:04pm GMT+8

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision 🚨security_alert 🔐security_note
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (22,148t read) | 5,301,017t work | 100% savings

### Sep 12, 2026
S721 logEZ Terms and Conditions creation plus compliance checklist and missing features analysis — then pivoted back to UI fixes (background color and Privacy Notice header) (Sep 12 at 5:26 PM)
S723 Add day streak and week streak tracking to logEZ for any logged workout — planning, implementation, and on-device verification complete (Sep 12 at 5:56 PM)
S722 Add day streak and week streak tracking for logging any workout in logEZ (Sep 12 at 6:14 PM)
S725 Galaxy Watch 6 health metrics mismatch with LogEZ — investigate Health Connect integration and wearable data pipeline (Sep 12 at 6:15 PM)
S724 LogEZ APK 30.13 staged after pace/HR-zone feature added and on-device verified via ADB automation (Sep 12 at 10:22 PM)
S726 Galaxy Watch 6 health metrics mismatch with LogEZ — root cause diagnosed as Samsung Health → Health Connect sync lag; proposed fixes awaiting user confirmation (Sep 12 at 10:23 PM)
S728 LogEZ Android — Heart rate freshness window widened + "as of HH:MM" timestamp label added (APK v30.14) (Sep 12 at 11:16 PM)
S730 User asked to see the ActivityTrackingScreen showing pace, live BPM, and heart rate zones — confirmed feature implementation and investigated emulator Health Connect support (Sep 12 at 11:23 PM)
S727 LogEZ Android app: Fix heart rate display going blank + add "as of HH:MM" timestamp label on Activity Tracking screen (Sep 12 at 11:23 PM)
S729 Profile screen "Today" score-card height non-uniformity fix (S26 Ultra) — shrink font size / prevent text wrapping; also verified Activity Tracking features from prior session (Sep 12 at 11:45 PM)
### Sep 13, 2026
4910 7:29a 🔵 Standalone hc-test-writer app scaffolded in scratchpad to probe Health Connect write path
4911 7:30a 🔵 hc-test-writer standalone app fully scaffolded: manifest + MainActivity.kt with HC write logic
4912 " 🔵 AGP 9.x breaking change: org.jetbrains.kotlin.android plugin must NOT be declared with AGP ≥ 9.0
4913 " 🔵 Second AGP 9.x breaking change: kotlinOptions DSL is unavailable without org.jetbrains.kotlin.android plugin
4914 7:33a 🔵 Health Connect insertRecords() requires explicit List&lt;Record&gt; type argument — Kotlin infers invisible IntervalRecord ancestor
4916 " 🔵 LogEZ app/build.gradle.kts uses compileOptions only — no kotlinOptions or jvmToolchain block
4923 7:39a 🔵 ADB Permission UI Automation: "Allow all" Toggle Not Found in UI Hierarchy
4924 7:40a 🔵 HCTestWriter app UI element bounds confirmed via uiautomator dump
4927 7:43a 🔵 LogEZ ADB Navigation Map — Profile Tab and Settings Row Coordinates
4928 " 🔵 LogEZ Blank Screen Fixed by APK Reinstall
4929 7:45a 🔵 LogEZ App Renders Correctly After APK Reinstall — Blank Screen Confirmed as Stale Install Issue
4930 12:23p 🔵 LogEZ Android App – ADB Automation: Location Permission + Walking Workout Tracking Flow
4931 12:26p 🔵 LogEZ Location Permission Dialog Automation Debug Session
4932 12:27p 🔵 LogEZ Walking Workout Screen — Post-Tap UI State Confirmed
4933 12:28p 🔵 LogEZ GPS Movement Simulation via ADB Emulator
4934 " 🔵 LogEZ GPS Tracking UI — Repeated Simulation Attempts
4935 12:31p 🔵 HCTestWriter App Used to Inject Health Data on Emulator
4936 " 🔵 LogEZ GPS Test: HCTestWriter Data Injection + LogEZ Screenshot Captured
### Sep 15, 2026
4938 12:21p 🟣 Active Workout Screen: Session Timer Logic Overhaul + UI Cleanup
4939 " 🔵 logEZ Vault Path Corrected + Full Project State Loaded
4940 12:22p 🔵 Active Workout Screen Architecture — Header Stats + Session Timer Code Paths
4941 12:23p 🔵 Session Timer Start Call Sites — All Callers Fire Before Logger Navigates
4942 12:24p 🔵 Finish Flow Duration Guard + Test Harness Shape for Session Timer Changes
4943 " 🔵 Session Timer Reset Mechanism — startSession() Re-call Resets the Clock to Now
4945 " 🟣 Session Timer + Header UI Overhaul Applied via Patch
4944 12:25p 🔵 WorkoutLoggerUiState Has No Timer-Gate Field — exercises List Already Surfaced
4946 12:27p 🔴 Patch Applied Clean — Two Residual Issues Found and Fixed
4948 12:30p 🟣 New Unit Tests Added — Controller and ViewModel Grace-Period Coverage
4950 12:32p 🔴 Fourth Test Run GREEN (677 total tests); Vault State + Git Log Read for Log Update
4951 " 🟣 Dead String Resource Removed from strings.xml
4952 " 🔴 assembleDebug — BUILD SUCCESSFUL in 22s, exit code 0
4955 12:35p 🔵 Bug: Exercise blocks in empty-start workouts pre-populate with stale set rows
4956 " 🔵 Bug Identified: Exercise Blocks Retain Stale Set Rows on New Workouts
4959 12:53p 🔴 Empty-Start Workouts No Longer Pre-fill Historical Set Rows on Exercise Add
4957 " 🟣 Empty-Workout Session Timer Delayed Until First Exercise Added
4958 " 🔴 Empty-Start Workouts No Longer Pre-Populate Historical Set Rows
4960 12:54p 🔴 Unit Test Suite Passes After Empty-Workout Set-Row Fix
4961 " ✅ Debug APK Built Successfully with Empty-Workout Set-Row Fix
4963 " 🔴 Committed: "Start empty workout exercises with one set" (f27d8d4)
4962 " ✅ logEZ-debug30.16.apk Shipped — 678 Tests Green, Two Files Modified
4964 12:55p 🔵 logEZ main Branch 5 Commits Ahead of origin; Obsidian Vault logEZ Directory Missing
4965 1:02p ⚖️ User Wants Persistent Claude Access to Claude-Obsidian Vault Without Explicit Path Every Session
4966 " 🔵 Codex Global Config Structure Mapped — AGENTS.md Locations and config.toml Found
4967 " 🔵 Codex Instruction Hierarchy Confirmed — AGENTS.md and config.toml Content Inspected for Vault Alias Setup
4968 1:03p 🔵 Codex Sandbox Has No Outbound Network Access — fetch-codex-manual.mjs Fails with DNS Error
4969 " 🔵 Claude-Obsidian Vault Structure Confirmed — Has CLAUDE.md, Not AGENTS.md
4970 " 🟣 Claude-Obsidian Vault Alias Registered in Global Codex AGENTS.md
4972 " 🔴 LogEZ AGENTS.md Vault Path Corrected — Codex-Obsidian → Claude-Obsidian
4971 " ✅ Global Codex AGENTS.md Updated With Claude-Obsidian Vault Alias
4973 1:04p 🔵 Claude-Obsidian Vault Successfully Read — logEZ Logs Current Through P-155, Vault Accessible from Codex

Access 5301k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>
