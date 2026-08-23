# ADR-0001: Toolchain versions and dependency injection

**Status:** Accepted (2026-08-23, M0)

## Context

PHASE2_PLAN.md §9.12 requires recording the exact toolchain quadruple (AGP / Kotlin / KSP /
Compose BOM) plus JDK at project creation, and treating it as a deliberately pinned set — not
independently upgraded — per the Fiterval toolchain lesson (an earlier upgrade attempt broke
`android.builtInKotlin` mode compatibility on a mismatched KSP/AGP pair).

§2.1 also flagged dependency injection (Hilt vs. a manual factory) as an Owner checkpoint
decision rather than a unilateral plan choice.

## Decision

**Toolchain — reuse Fiterval's exact proven-working set** (verified building in this same
environment, not merely "should work"):

| Component | Version |
|---|---|
| AGP | 9.2.0 |
| Kotlin | 2.3.0 |
| KSP | 2.3.9 |
| Compose BOM | 2026.06.00 |
| Gradle | 9.4.1 |
| JDK | 25 (Temurin), sourceCompatibility/targetCompatibility 11 |
| compileSdk / targetSdk | 36 (minor API level 1) |
| minSdk | 26 |

Recorded in `gradle/libs.versions.toml`. Do not bump any one of these independently; upgrades
happen only at milestone boundaries as their own commit, verified by a full build.

**Dependency injection: Hilt** (`com.google.dagger:hilt-android` 2.60.1 +
`androidx.hilt:hilt-navigation-compose` 1.3.0), chosen over a manual factory (Fiterval's
ADR-0003 precedent) because logEZ's plan calls for roughly a dozen ViewModels, 8+
repositories/DAOs, and a foreground service that all need injected dependencies — Hilt's
wiring cost stays flat as that surface grows, where a manual factory's cost grows with it.
Owner-confirmed 2026-08-23 (see the vault: `11-Projects/logEZ/decisions.md`).

## Consequences

- `LogEzApplication` is `@HiltAndroidApp`; `MainActivity` is `@AndroidEntryPoint`.
- Every repository/service/ViewModel added from M1 onward is Hilt-injected, not manually wired.
- Room's KSP annotation processor and Hilt's KSP annotation processor share one `ksp` build
  step; `room.schemaLocation` is already configured in `app/build.gradle.kts` ahead of M1.
- **Hilt version note:** Hilt's Gradle plugin only gained AGP 9 support starting at 2.59 (2.57.2
  fails outright with "Android BaseExtension not found" — AGP 9 removed the old DSL types Hilt's
  plugin looked up). 2.59 itself shipped a jetifier-interaction bug (missing `ComponentTreeDeps`
  at compile time, google/dagger#5099); the fix landed in 2.59.2. 2.60.1 (pinned here) is the
  latest stable and does not regress it — confirmed against GitHub's release notes, not assumed.
- **AGP 9 also removed the separate `org.jetbrains.kotlin.android` plugin requirement** —
  applying it alongside AGP 9's built-in Kotlin support fails the build; only
  `kotlin.plugin.compose` and `kotlin.plugin.serialization` are applied.
