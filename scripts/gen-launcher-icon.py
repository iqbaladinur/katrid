#!/usr/bin/env python3
"""Kartrid launcher icon — BMO-style Game Boy, riso DNA (flat, ink border, hard shadow).

Fix 17 Okt 2026: icon blurry karena adaptive FOREGROUND cuma 108x108 px (1x).
Launcher render 108dp -> 432 px di xxxhdpi => upscale 4x = ngeblir.
Sekarang tiap density dapat PNG seukuran aslinya (digambar ulang, supersample 4x),
dan konten adaptive dibatasi ke safe zone (72/108 dp) biar gak kepotong mask.

Output:
  res/mipmap-<dpi>/ic_launcher.png            48..192  (legacy, full-bleed)
  res/mipmap-<dpi>/ic_launcher_round.png      48..192  (legacy round)
  res/mipmap-<dpi>/ic_launcher_foreground.png 108..432 (adaptive fg, transparan)
"""
import os
from PIL import Image, ImageDraw

RES = os.path.expanduser("~/hobbies/kartrid/android/app/src/main/res")

INK = (0x16, 0x12, 0x1F, 255)
BODY = (0x6D, 0x3B, 0xE8, 255)      # variant C: violet body
FACE = (0xC3, 0xE8, 0x2F, 255)      # lime face + ctrl
BG = (0xA9, 0x8B, 0xFF, 255)        # #A98BFF (sama dgn adaptive bg xml)
SS = 4                              # supersample factor


def art(px: int) -> Image.Image:
    """Badan Game Boy digambar di kanvas px (konten 0.115..0.855)."""
    S = px * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bx0, by0, bx1, by1 = 0.115, 0.095, 0.855, 0.835
    rad = 0.17 * S
    ox = oy = 0.045 * S
    ink_w = max(1, int(round(0.030 * S)))

    # hard offset shadow, 0 blur
    d.rounded_rectangle([bx0 * S + ox, by0 * S + oy, bx1 * S + ox, by1 * S + oy], radius=rad, fill=INK)
    # body
    d.rounded_rectangle([bx0 * S, by0 * S, bx1 * S, by1 * S], radius=rad, fill=BODY, outline=INK, width=ink_w)

    # screen
    sx0, sy0, sx1, sy1 = 0.20, 0.175, 0.775, 0.525
    d.rounded_rectangle([sx0 * S, sy0 * S, sx1 * S, sy1 * S], radius=0.055 * S, fill=INK,
                        outline=INK, width=max(1, int(round(0.017 * S))))

    # face: dua mata + senyum
    ex = [0.365, 0.61]
    ey, erx, ery = 0.305, 0.042, 0.058
    for cx in ex:
        d.ellipse([(cx - erx) * S, (ey - ery) * S, (cx + erx) * S, (ey + ery) * S], fill=FACE)
    d.arc([0.425 * S, 0.30 * S, 0.55 * S, 0.44 * S], start=15, end=165, fill=FACE,
          width=max(1, int(round(0.022 * S))))

    # d-pad
    dcx, dcy, arm, aw = 0.30, 0.685, 0.072, 0.045
    d.rounded_rectangle([(dcx - arm) * S, (dcy - aw) * S, (dcx + arm) * S, (dcy + aw) * S], radius=0.018 * S, fill=FACE)
    d.rounded_rectangle([(dcx - aw) * S, (dcy - arm) * S, (dcx + aw) * S, (dcy + arm) * S], radius=0.018 * S, fill=FACE)

    # tombol A/B
    for (cx, cy, r) in [(0.615, 0.705, 0.046), (0.705, 0.615, 0.046)]:
        d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=FACE,
                  outline=INK, width=max(1, int(round(0.012 * S))))

    return img.resize((px, px), Image.LANCZOS)


def compose(canvas_px: int, frac: float, bg=None, circle=False) -> Image.Image:
    """art di-skala ke frac * canvas_px, dipaste center di kanvas kanvas_px."""
    inner = max(10, int(round(canvas_px * frac)))
    a = art(inner)
    out = Image.new("RGBA", (canvas_px, canvas_px), bg if bg else (0, 0, 0, 0))
    off = (canvas_px - inner) // 2
    out.alpha_composite(a, (off, off))
    if circle:
        m = Image.new("L", (canvas_px, canvas_px), 0)
        ImageDraw.Draw(m).ellipse([0, 0, canvas_px - 1, canvas_px - 1], fill=255)
        out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", out.size, 0), m))
    return out


# legacy icon: 48dp baseline; adaptive foreground: 108dp baseline
LEGACY = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
FG = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]

report = []
for dpi, px in LEGACY:
    d = f"{RES}/mipmap-{dpi}"
    os.makedirs(d, exist_ok=True)
    compose(px, 0.80, bg=BG).save(f"{d}/ic_launcher.png")
    compose(px, 0.72, bg=BG, circle=True).save(f"{d}/ic_launcher_round.png")
    report.append(("legacy", dpi, px))

for dpi, px in FG:
    d = f"{RES}/mipmap-{dpi}"
    os.makedirs(d, exist_ok=True)
    # 0.62 dari 108dp = 67dp -> aman di safe zone 72dp (gak kepotong mask launcher)
    compose(px, 0.62).save(f"{d}/ic_launcher_foreground.png")
    report.append(("fg", dpi, px))

for kind, dpi, px in report:
    print(f"{kind:6s} {dpi:8s} {px:4d}px")
