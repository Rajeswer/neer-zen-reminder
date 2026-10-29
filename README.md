# Neer Zen

A calm, offline Android app for mindful hydration: a 12-second **Mindful Sip** ritual, a **Focus Vessel** timer that ends in a hydration break, and a field guide of traditional **Herbal Infusions** with steep timers.

Built with Jetpack Compose. Every screen follows the design system in [`docs/design-system.md`](docs/design-system.md): ash-grey canvas, charcoal ink, muted sage and pale water blue; Source Serif 4 for editorial moments and Inter for interface text; hand-painted-style Enso, water and stone illustrations drawn in code.

## Build and install

Every push to `main` runs **Actions → Build Neer Zen APK**. When it finishes:

- **Releases** → open the newest `build-N` release → tap the `.apk` to download and install, or
- open the run → **Artifacts** → `Neer-Zen-APK`.

If a build fails, the run page shows the compile errors as annotations.

## Project layout

```
app/src/main/java/com/zen/neer/
  ZenTheme.kt        palette, semantic tokens, type scale, spacing, shapes
  ZenComponents.kt   ZenScaffold, ZenTopBar, buttons, surfaces, chips
  Illustrations.kt   Enso, droplet, ripples, mountains, bamboo, stones, bowl, infusion glass
  SipScreen.kt       Mindful Sip ritual and completion
  FocusScreen.kt     Focus Vessel timer and mindful break
  InfusionsScreen.kt herbal infusion list and detail
  SteepScreen.kt     steep timer
  SettingsScreen.kt  daily goal, sound, haptics, reset
  Store.kt           offline persistence and timers
app/src/main/res/font/  Source Serif 4 and Inter (SIL OFL, see licenses/)
```

Herbal preparations are traditional home recipes, not medical advice.
