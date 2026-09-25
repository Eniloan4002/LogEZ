# Store assets

Google Play listing graphics for LogEZ, generated on 2026-09-25 from the same mark the app ships as
its launcher icon (`app/src/main/res/drawable/ic_launcher_*.xml`).

| File | Play Console field | Spec it meets |
|---|---|---|
| `icon-512.png` | App icon | 512 × 512, 32-bit PNG with alpha, under 1 MB. Full-bleed square; Play applies its own corner mask. |
| `feature-graphic-1024x500.png` | Feature graphic | 1024 × 500, 24-bit PNG, no alpha. No price, ranking or "free" claims. |
| `brand-mark.svg` | — | Vector source for both, on a 108-unit canvas matching the adaptive icon. |

To regenerate the icon after editing the SVG:

```bash
rsvg-convert -w 512 -h 512 store/brand-mark.svg -o store/icon-512.png
```

The feature graphic's wordmark uses Chakra Petch Bold and the tagline IBM Plex Sans, both from
`app/src/main/res/font/` (SIL Open Font License).

Phone screenshots still need recapturing from the current build (at most 8), after the UI is final.
