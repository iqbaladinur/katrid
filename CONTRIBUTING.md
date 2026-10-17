# Contributing to Kartrid

Thanks for taking a look. Kartrid is a small, focused project — the fastest way to get a
change merged is to keep it small and show a screenshot.

## Before you start

- **Bug reports**: include device model, Android version, screen resolution, and a
  screenshot. If it is a crash, attach the `adb logcat -s AndroidRuntime:E` output.
- **Feature requests**: describe the *problem* first (what is annoying today), then the
  proposed fix. Kartrid deliberately stays a frontend — no emulator core, no scraper
  service, no account.
- **Art / design changes**: the shelf layout is a fixed 960×640 design canvas
  (`MockCanvas`), so every number in the code equals a number in the design file. Keep
  that property; do not introduce density-dependent sizes for canvas elements.

## Development loop

```bash
git clone git@github.com:iqbaladinur/katrid.git
cd katrid/android
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # Robolectric screenshot tests → ../previews/*.png
../deploy.sh                          # build + install + launch on a connected device
```

The screenshot tests render the real Compose UI to PNG without a device (Robolectric +
Roborazzi), which makes UI review possible in a PR without an APK. **Please attach the
affected preview PNG before/after.**

## Changing the cartridge

The 3D cartridge is generated, not hand-drawn:

1. edit the scene in `design/cart3d/seq.html` (dimensions, bevel, recess depth, label inset);
2. run `python3 design/cart3d/seq.py` — it re-renders 27 angles, rewrites
   `android/app/src/main/res/drawable-nodpi/cf_*.png` **and** regenerates
   `ui/CartFrames.kt` with the new label quads.

Never edit `CartFrames.kt` by hand and never hand-place the boxart in Compose: the label
is warped into the four measured corners of the current frame, so render and runtime can
never drift apart.

## Code style

- Kotlin only (no Java sources).
- Compose first; no XML layouts.
- Comments explain *why*, not *what*. No changelog comments, no commented-out code.
- Keep the asset budget in mind: the whole cartridge animation must stay well under 1 MB.

## Pull requests

- One topic per PR.
- Describe what changed and why, with a screenshot for anything visual.
- Mention the device you tested on. If you only ran the screenshot tests, say so.

By contributing you agree your work is licensed under the [MIT License](LICENSE).
