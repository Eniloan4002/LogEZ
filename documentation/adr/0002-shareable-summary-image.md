# ADR-0002: Shareable workout-summary image (local PNG export via the OS share sheet)

**Status:** Accepted and implemented.

## Context

This project has a standing "no share buttons, no social surface" rule, since one of its core
principles is that training data stays local and never depends on an account or a cloud service.
A request to let users share their post-workout summary to other apps (Instagram, messaging, etc.)
looked at first like it conflicted with that rule.

It doesn't, once the two things are separated: what the standing rule actually excludes is *social
platform plumbing* — public share links, story stickers generated against a cloud service, feeds
and profiles for shared content to live on. A **locally rendered PNG handed to the Android share
sheet** is a different thing entirely: the app draws an image on-device and passes a file to
whatever app the user picks. No links, no accounts, no server, and no change to the app's
zero-network-by-default posture.

## Decision

A narrow, explicit exception to the "no share surface" rule — **local image export only**. Public
share links, feeds, and any cloud-backed share surface remain excluded.

Scope of the feature:

- **Where:** the post-workout summary screen only.
- **What:** a dedicated, fixed-size "share card" composable — not a screenshot of the live screen —
  rendered in the app's own design system.
- **Formats:** Square 1080×1080 and Story 1080×1920. The card is laid out at a fixed design size
  under a forced density so the export size is exact; the in-sheet preview is the *same
  composition* scaled down, so preview and export can't drift apart.
- **Mechanics:** Compose `GraphicsLayer` capture → PNG encoded off the main thread → app cache
  directory (stale exports cleaned up on a grace window) → a non-exported `FileProvider` scoped to
  that cache subdirectory only → `ACTION_SEND` chooser with a read-URI grant.
- **Explicitly rejected:** platform-specific share intents (e.g. Instagram's `ADD_TO_STORY`) that
  require registering an app ID with an external platform — the generic share sheet reaches the
  same destinations without that dependency.
- Side effects (bitmap encode, file IO, intent dispatch) live in an injected controller, never
  directly in a ViewModel — see `WorkoutShareController` for the pattern to follow if you add
  another export surface.

## Consequences

- The Data Safety story is unaffected: nothing is collected or transmitted by the app itself; a
  share is a user-initiated handoff of a user-visible image to an app the user chooses.
- New manifest surface: one non-exported `FileProvider`, no new permissions.
- If you're adding a new export/share surface, follow this same shape (fixed-size composable → PNG
  → cache dir → scoped `FileProvider` → share sheet) rather than inventing a new mechanism.
