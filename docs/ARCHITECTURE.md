# Architecture

Kartrid is deliberately small: three systems, one emulator target, one screen. Almost all
of the complexity lives in one place — making a flat boxart bitmap look like a physical
cartridge sitting in a recessed label — and that part is a build-time render plus a
runtime warp, not a 3D engine inside the app.

```
┌─────────────────────────── Android app (Kotlin / Compose) ───────────────────────────┐
│                                                                                       │
│  MainActivity                                                                         │
│    ├─ RomScanner ──► Game list (per system, persisted play counts / favourites)       │
│    ├─ ArtCache   ──► boxart (libretro-thumbnails, or a user-picked image)             │
│    ├─ Store       ──► settings (folders, cores, auto-scan, art)                       │
│    └─ RetroArch   ──► launch intent + core selection                                  │
│                                                                                       │
│  UI (fixed 960×640 design canvas, MockCanvas overrides density so 1 dp == 1 px)       │
│    ├─ ShelfScreen   carousel + 3D cartridges + drag-to-spin                           │
│    ├─ Cartridge     draws shell frame, warps label into the frame's label quad         │
│    ├─ ListScreen    search, A–Z rail, filters                                         │
│    ├─ SettingsScreen / FolderPicker                                                  │
│    └─ Sfx           synthesised UI sounds + haptics                                   │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

## 1. The cartridge: render once, warp at runtime

A photoreal cartridge needs volume, but boxart is per-game and can change at any time.
Splitting those two facts is the whole design:

| Layer | Produced | When |
|---|---|---|
| Shell (plastic, bevels, recessed label window) | 27 PNGs, one per rotation step | build time (three.js, headless) |
| Label (boxart + system panel + text) | one bitmap per game | runtime, cached |
| Composite | label drawn into the frame's label quad, frame drawn on top | runtime, per frame |

Because the shell PNG has a **transparent hole** where the label is, drawing the label
first and the shell on top gives the recess lip, the inner shadow and the drop of the
label edge for free. No manual overlay, no per-game render.

### Shell model

Procedurally modelled, not downloaded — the open model sites all gate downloads behind an
account, and a downloaded print STL has no measured label window:

- body 31.5 × 21.5 × 3.4 mm, corner radius 1.5 mm, one clipped corner (keying notch)
- edge bevel 0.28 mm, 3 segments — the bevel carries every highlight
- label window 28.0 × 18.0 mm, inset 1.75 mm, radius 1.1 mm, cut as a **hole** in the
  extruded body so its inner walls shade per side
- label plate sits 0.30 mm below the front face (a real recess; a plate in front reads as
  a sticker)

Measured luminance across the recess edge: **231 body → 240 lip highlight → 190 inner
shadow → 209 plate**. That gradient is the entire 3D illusion; a flat plane under a
directional light renders a constant colour (measured σ = 0.0), no matter how many lights
you add.

Rendering uses a perspective camera (18° fov) at a fixed distance so the projection is
stable, `NoToneMapping` so the printed label is exactly the bitmap the app will draw, and
two `RectAreaLight`s for the gloss sweep on the plastic.

### Rotation

27 frames from −26° to +26° (2° steps), rendered by rotating the model group around its
own origin. The pivot is therefore identical in every frame — verified: maximum pivot
deviation across all 27 frames is **0.0 px**, which is why the drag does not wobble.

Each frame also records the projected positions of the four label corners:

```kotlin
val QUADS: Array<FloatArray>  // 27 × [TLx, TLy, TRx, TRy, BRx, BRy, BLx, BLy]
```

At runtime the label bitmap is mapped onto those corners with
`Matrix.setPolyToPoly(src, 0, dst, 0, 4)` and drawn through the native canvas. That is a
true perspective transform — a plain `graphicsLayer` rotation would shear a flat card and
lose the recess.

Vertical tilt (±10°) is applied with `graphicsLayer.rotationX` on top, because
pre-rendering a second axis would mean 27 × N frames. If two-axis rotation is ever needed
properly, the grid is 27 × 7 = 189 frames (~3 MB).

### Label bitmap

Drawn at runtime with `android.graphics.Canvas` at 560 × 360:

- left: the boxart, **square and uncropped** (360 × 360 = full label height); non-square
  sources are centre-cropped to keep the aspect
- right: a solid panel in the system colour carrying the system code (Archivo Black), the
  system name, the title (up to 4 wrapped lines, mono) and region/size/play count

| System | Panel | Text |
|---|---|---|
| GB | `#CFC9DD` | `#16121F` |
| GBC | `#4F86AE` | `#F6F3FF` |
| GBA | `#5A4C93` | `#F6F3FF` |

Labels are cached in an `LruCache` (12 entries); shell frames in another (10 entries, ~319 KB
each).

## 2. Shelf layout

The canvas is 960 × 640 and the density is overridden so that **1 dp == 1 px**. Every
constant below is therefore both a design number and a code number.

| Constant | Value | Meaning |
|---|---|---|
| `FOCUS_W` | 305 | focused cartridge width (dp) |
| `ITEM_W` | 200 | neighbour width |
| `GAP` | 46 | gap between cartridges |
| `CART_BOTTOM` | 430 | shelf baseline |
| `CART_ASPECT` | 31.5 / 21.5 | physical cartridge aspect |
| `TITLE_Y` | 470 | title block |

`widthAt(d)` interpolates width against the fractional distance from the focused index and
`centerOffset(d)` integrates `GAP + widthAt(t)` so the carousel moves continuously instead
of snapping. Neighbours get a ±2.6° z-rotation and a 0.58 brightness filter; the focused
cartridge lifts 10 px and draws at full brightness.

A cartridge is positioned by its **centre**, not its top-left, because the shell frames
have different bounding boxes per angle (a rotated cartridge is narrower but taller).
`boxLeft = cx − CENTER_X·scale`, `boxTop = cy − CENTER_Y·scale`.

## 3. Assets

| Asset | Size | Notes |
|---|---|---|
| `drawable-nodpi/cf_00..cf_26.png` | 425 KB total | 331 × 241 px each, transparent label window |
| `cart_gba.png` | 10 KB | legacy style-D shell, unused by the shelf |

Frame images are exported at 330 px per cartridge width, i.e. ~1.08× the largest on-screen
size, so the focused cartridge is never upscaled.

## 4. Testing without a device

`PreviewTest` (Robolectric + Roborazzi, `@GraphicsMode(NATIVE)`, qualifiers
`w960dp-h640dp-land-mdpi`) renders the real Compose tree to PNG:

```bash
cd android && ./gradlew :app:testDebugUnitTest   # → ../previews/*.png
```

This catches layout regressions (cartridge centre, label alignment, panel colours) without
an APK. `ShelfScreen` takes an optional `preloadArt` lambda so previews can seed boxart
synchronously; the app itself loads art through `artOf` in a coroutine.

Numeric checks used during development (all reproducible from the PNGs):

- focused cartridge bounding box 306 × 212 px, centre x = 479.5 (canvas centre 480)
- system panels present as exact colours (≈5.3 k px each at 200 dp width)
- mid-drag bounding box 286 × 222 px → back to 306 × 212 px after release (spring)

## 5. Launch path

`A` on a cartridge → brief insert animation (34 px drop, 0.97 scale, SFX + 18 ms haptic) →
spinner over the label → `RetroArch.launch(game, core)` builds the RetroArch intent with
the ROM path and the per-system core from settings. Kartrid never runs emulation code.

## 6. Deliberate non-goals

- No scraper account, no cloud sync, no themes engine.
- No emulator cores, BIOS or ROMs — the app only launches one.
- No portrait-specific layout yet; the canvas letterboxes on other aspect ratios.
