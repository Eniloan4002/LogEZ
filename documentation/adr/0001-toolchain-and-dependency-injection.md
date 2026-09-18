# ADR-0001: Toolchain versions and dependency injection

**Status:** Accepted.

## Context

The AGP/Kotlin/KSP/Compose-BOM combination an Android project builds against is sensitive to
version skew — an earlier attempt at bumping one of these independently on a related project broke
build compatibility on a mismatched KSP/AGP pair. This project treats its toolchain as a
deliberately pinned set rather than something upgraded piecemeal.

Dependency injection (Hilt vs. a manual factory) was also an early architectural decision worth
recording, since it affects how every ViewModel/repository/service gets wired up from day one.

## Decision

**Toolchain — a proven-working set, verified building in this project's own environment:**

| Component | Version |
|---|---|
| AGP | 9.2.0 |
| Kotlin | 2.3.0 |
| KSP | 2.3.9 |
| Compose BOM | 2026.06.00 |
| Gradle | 9.4.1 |
| Build JDK | 17–21 (JDK 25+ breaks Robolectric-based unit tests — see `README.md`) |
| `sourceCompatibility`/`targetCompatibility` | `JavaVersion.VERSION_17` |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

Recorded in `gradle/libs.versions.toml`. **Don't bump any one of these independently** — a toolchain
upgrade should be its own commit, verified by a full build and test run, not a side effect of
adding an unrelated dependency.

**Dependency injection: Hilt** (`com.google.dagger:hilt-android` + `androidx.hilt:hilt-navigation-compose`),
chosen over a manual factory because the app has roughly a dozen ViewModels, a similar number of
repositories/DAOs, and a foreground service, all needing injected dependencies — Hilt's wiring cost
stays flat as that surface grows, where a manual factory's cost grows with it.

## Consequences

- `LogEzApplication` is `@HiltAndroidApp`; `MainActivity` is `@AndroidEntryPoint`.
- Every repository/service/ViewModel is Hilt-injected, not manually wired — new ones should follow
  the same pattern (see `documentation/ARCHITECTURE.md`).
- Room's KSP annotation processor and Hilt's KSP annotation processor share one `ksp` build step;
  `room.schemaLocation` is already configured in `app/build.gradle.kts`.
- **Hilt version note:** Hilt's Gradle plugin only gained AGP 9 support starting at 2.59 (older
  versions fail outright — AGP 9 removed DSL types Hilt's plugin looked up). If you ever bump Hilt,
  check its release notes against the pinned AGP version before assuming it "should just work."
- **AGP 9 also removed the separate `org.jetbrains.kotlin.android` plugin requirement** — applying
  it alongside AGP 9's built-in Kotlin support fails the build; only `kotlin.plugin.compose` and
  `kotlin.plugin.serialization` are applied on top of AGP's own Kotlin support.
