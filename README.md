# Neer Zen

A calm, offline Android app for mindful hydration: a 12-second **Mindful Sip** ritual, a **Focus Vessel** timer that ends in a hydration break, and a field guide of traditional **Herbal Infusions** with steep timers.

Built with Jetpack Compose. Every screen follows the design system in [`docs/design-system.md`](docs/design-system.md): ash-grey canvas, charcoal ink, muted sage and pale water blue; Source Serif 4 for editorial moments and Inter for interface text; ink-wash artwork painted by `tools/inkpaint.py` and animated in Compose.

## Build and install

Every push to `main` runs **Actions → Build Neer Zen APK**. When it finishes:

- **Releases** → open the newest `build-N` release → tap the `.apk` to download and install, or
- open the run → **Artifacts** → `Neer-Zen-APK`.

The build runs the unit tests first; if anything fails, the run page s## Project layout

```
app/src/main/java/com/zen/neer/
  MainActivity.kt          entry point, tabs, splash, app-level timers and the sip session
  app/Feedback.kt          chime and haptics
  data/Store.kt            offline persistence (SharedPreferences), settings, focus and steep timers
  domain/SipSession.kt     Mindful Sip state machine — no Android code, unit-tested
  design/theme/            ZenTheme (colour, type, spacing, shapes) and Motion (durations, easing)
  design/components/       top bar, buttons, surfaces, chips
  design/illustration/     painted artwork loaders and drawing helpers (Enso, fountain, basin, ripples)
  feature/mindfulsip/      SipScreen, SipSessionController (Compose state + frame loop), SipChoreography
  feature/focus/           Focus Vessel timer and mindful break
  feature/infusions/       infusion guide and steep timer
  feature/settings/        daily goal, sound, haptics, reset
app/src/test/              unit tests for the session and choreography
app/src/main/res/drawable-nodpi/  painted WebP artwork (regenerate: python3 tools/inkpaint.py <dir>)
app/src/main/res/font/     Source Serif 4 and Inter (SIL OFL, see licenses/)
tools/capture-screens.sh   emulator walkthrough used by CI
```

## Mindful Sip timing

`Get ready` 3 s → `Breathe in` 4 s → `Sip` 4 s → `Breathe out` 4 s → settle 2 s → Well done.
One droplet falls from the bamboo spout in each 4-second beat; each landing ripples the basin
and advances the Enso. The sip is recorded once, when the 12 seconds complete. Leaving the
app pauses the session; Back or Cancel ends it without recording. Durations live in
`SipTiming` (session) and `Motion` (animation).

 goal, sound, haptics, reset
  Store.kt           offline persistence and timers
app/src/main/res/font/  Source Serif 4 and Inter (SIL OFL, see licenses/)
```

Herbal preparations are traditional home recipes, not medical advice.
