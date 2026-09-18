# Architecture

LogEZ is a single Gradle module (`:app`) split into `core/*` (shared, feature-agnostic code) and
`feature/*` (one package per screen/flow). There's no multi-module split, so nothing at the build
level stops one feature from importing another feature's internals directly — treat the layering
below as a convention to keep to, not something the compiler enforces for you.

## Layers and data flow

```
UI (Compose)          feature/*/…Screen.kt — pure composables, no logic beyond rendering
      │  ViewModels hold screen state
ViewModel layer        feature/*/…ViewModel.kt — Hilt-injected, exposes StateFlow/State
      │  depends on interfaces in core/domain/repository
Domain layer           core/domain/repository/*.kt — contracts the UI depends on
(pure Kotlin,          core/domain/model/*.kt      — enums & data types
no Android)            core/domain/calc/*.kt       — the math: 1RM, PRs, streaks, volume,
                                                       plates, warm-ups, muscle balance, charts…
      │  implementations in core/data (bound by Hilt)
Data layer             core/data/LogEzDatabase.kt   — Room DB
                       core/data/dao/*.kt           — SQL queries
                       core/data/entity/*.kt        — table rows
                       core/data/repository/*Impl.kt — real implementations
                       DataStore                    — settings + transient session state
```

Rules that hold everywhere in the code:

- **Data flows one way:** Screen → ViewModel → repository interface → (calc engines) → Room/DataStore. ViewModels collect `Flow`s from repositories and expose `StateFlow`s the UI collects.
- **The domain layer never touches Android.** Everything in `core/domain/calc/` is pure Kotlin — that's why it's trivially unit-testable without Robolectric.
- **Repositories are behind interfaces** specifically so tests can swap in an in-memory fake (`app/src/test/java/com/enil/logez/fakes/Fake*.kt`) instead of a real Room-backed implementation.
- **Hilt wires it all:** `@HiltAndroidApp` on `LogEzApplication`, `@AndroidEntryPoint` on `MainActivity`, `@HiltViewModel` on every ViewModel, and dedicated DI modules under `core/data/di/` (`DataModule` for Room/DAOs/DataStore, `RepositoryModule` for interface→impl bindings) plus one module per feature that needs a vendor-SDK client it can't `@Inject`-construct directly (e.g. `feature/activity/di/ActivityTrackingModule.kt` for the location client).
- **Navigation:** every route lives in a per-feature `*Routes.kt` object; a single `app/navigation/LogEzNavHost.kt` wires them all together; `app/navigation/LogEzDestination.kt` defines the app's top-level tabs.

## Package map

| Package | What lives here |
|---|---|
| `core/data` | Room entities/DAOs, repository implementations, media/seed utilities |
| `core/designsystem` | Theme, typography, and shared Compose components (charts, cards, dialogs) used across features |
| `core/di` | Cross-cutting Hilt qualifiers |
| `core/domain` | Repository interfaces, domain models, and pure calculation engines |
| `feature/activity` | GPS-tracked walk/run: live tracking, the map, route storage |
| `feature/analytics` | The Statistics screen: charts, muscle-distribution/balance |
| `feature/exercises` | Exercise library: browsing, editing, custom exercises |
| `feature/history` | Past-workout history and workout detail |
| `feature/privacy` | Privacy policy screen (required by the Health Connect permission rationale) |
| `feature/routines` | Routine/folder building and the Workout tab's routine list |
| `feature/settings` | App settings (units, rest timer, sounds, plate equipment, etc.) |
| `feature/wellness` | Health Connect integration (steps, heart rate, calories) |
| `feature/workout` | The largest feature: live workout logging, finish/recap, sharing |

## Where the key business logic lives

Anything that computes a number (1RM, personal records, streaks, volume, muscle-group distribution,
plate math, warm-up sets) lives in `core/domain/calc/*.kt` as a pure function or small pure class —
start there before writing new logic in a ViewModel. If you're not sure where a calculation belongs,
check whether an existing calculator already does something close to what you need.

## Testing conventions

Tests live in `app/src/test/` (JUnit4 + Robolectric; no instrumented/`androidTest` suite exists yet).
This codebase favors **hand-written in-memory fakes** (`app/src/test/java/com/enil/logez/fakes/`)
over a mocking framework — a `FakeXRepository` implementing the same domain interface as the real
one, backed by an in-memory `MutableStateFlow`/list. Match this pattern for new repository tests
rather than introducing Mockito/MockK.

See [`documentation/adr/`](adr/) for the reasoning behind specific technology choices (chart
libraries, third-party UI components, etc.) that this overview doesn't cover in depth.
