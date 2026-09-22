# LogEZ — App Screenshots

Captured 2026-09-19 on the `LogEZ_Pixel7` emulator (1080×2400, density 420, Android 15).

## Folders

| Folder | Size | Use |
|---|---|---|
| `raw/` | 1080×2400 | True-to-device. Social posts, website, press. |
| `play-store/` | 1080×1920 | Google Play listing. |

**Why two sizes:** the device is 2.22:1, but Google Play caps phone screenshots at **2:1**, so
native captures would be rejected. The `play-store/` files scale the full frame to fit 9:16 and
centre it on black — nothing is cropped, and the app's own background makes the padding invisible.

## What's in the shots

Real data, not mockups: **348 seeded workouts spanning Sep 2024 → Sep 2026** (~3,248 sets),
generated with a realistic periodization shape — 3 build weeks then a deload, session days drawn
at random rather than a flat grid, which is what makes the heatmap and charts read as genuine.
Three routines (Push/Pull/Leg Day) and a weekly volume goal were added by hand. The workout in
`01`/`02` was logged live during the session.

Status bar is normalised via SystemUI demo mode (9:30, full battery, full signal, no notification
clutter) — the standard approach for store screenshots.

## Not included

- **Health Connect screens** (steps card, 7-day steps chart, live BPM) — the emulator has no
  Health Connect provider, so the app correctly hides them rather than showing zeros.
- **GPS / map screens** — `adb emu geo fix` doesn't work on this AVD, so a tracked route records
  ~0 km and the map would show an empty track.

Both need a physical device with Health Connect and real GPS to capture honestly.

## Known caveat

The seeded history only covers Push/Pull/Legs barbell work, so the muscle-balance radar (`06`)
reads 0% for Arms, Hamstrings and Lower Leg. That's accurate to the data, but if you want a
fuller-looking radar, the demo seeder needs a wider exercise spread.

## Reproducing

Screenshots came from a throwaway build in an isolated git worktree off `origin/main`, with a
debug-only hook re-wiring `DemoDataSeeder` (dormant in the real app since 2026-09-04). That hook
was never committed and is not in any shipping build.
