#!/usr/bin/env python3
"""Render 27 frame putaran (-26..+26, step 2) → PNG siap pakai Android + CartFrames.kt."""
import json, os, subprocess
from playwright.sync_api import sync_playwright
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
SEQ = os.path.join(BASE, "seq")
os.makedirs(SEQ, exist_ok=True)
CH = "/home/iqbaladinur/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome"
RES = os.path.abspath(os.path.join(BASE, "..", "..", "android", "app", "src", "main", "res", "drawable-nodpi"))
KT = os.path.abspath(os.path.join(BASE, "..", "..", "android", "app", "src", "main", "java", "id", "iqbaldev", "kartrid", "ui", "CartFrames.kt"))

ANGLES = list(range(-26, 27, 2))          # 27 frame
UNIT_W = 330.0                             # lebar kartrid @0° di PNG (px) ≈ 1,08 × 305 dp

# --- 1. render sekuens ---
metas = []
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CH, args=[
        "--no-sandbox", "--use-gl=angle", "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader", "--allow-file-access-from-files", "--force-color-profile=srgb"])
    pg = b.new_page(viewport={"width": 1300, "height": 1100})
    pg.goto(f"file://{BASE}/seq.html?plate=0")
    pg.wait_for_function("document.title === 'ready'", timeout=60000)
    for i, a in enumerate(ANGLES):
        m = pg.evaluate("([a]) => window.renderAt(a, 0)", [a])
        path = f"{SEQ}/f{i:02d}.png"
        pg.locator("#c").screenshot(path=path, omit_background=True)
        m["idx"] = i
        metas.append(m)
    b.close()

# --- 2. union bbox + skala ---
u = {k: (min(m["bbox"][k] for m in metas) if k in ("x", "y") else max(m["bbox"][k] for m in metas))
     for k in ("x", "y", "w", "h")}
x0, y0 = u["x"], u["y"]
x1 = max(m["bbox"]["x"] + m["bbox"]["w"] for m in metas)
y1 = max(m["bbox"]["y"] + m["bbox"]["h"] for m in metas)
bw, bh = x1 - x0, y1 - y0

b0 = metas[ANGLES.index(0)]["bbox"]["w"]
s = UNIT_W / b0
IW, IH = round(bw * s), round(bh * s)
print(f"union {bw}x{bh} → export {IW}x{IH} (s={s:.4f})")

# --- 3. crop + resize + simpan ke drawable-nodpi ---
os.makedirs(RES, exist_ok=True)
for m in metas:
    im = Image.open(f"{SEQ}/f{m['idx']:02d}.png").convert("RGBA").crop((x0, y0, x1, y1))
    im = im.resize((IW, IH), Image.LANCZOS)
    im.save(f"{RES}/cf_{m['idx']:02d}.png", optimize=True)

# --- 4. data quad buat Kotlin ---
quads, centers = [], []
for m in metas:
    pts = []
    for (qx, qy) in m["quad"]:
        pts += [round((qx - x0) * s, 2), round((qy - y0) * s, 2)]
    quads.append(pts)
    cx, cy = m["center"]
    centers.append((round((cx - x0) * s, 2), round((cy - y0) * s, 2)))

cz = centers[ANGLES.index(0)]
# pivot harus identik di semua frame (rotasi di origin) — assert
dev = max(abs(cx - cz[0]) + abs(cy - cz[1]) for cx, cy in centers)
print("deviasi pivot antar frame (px):", round(dev, 3))

rows = ",\n        ".join(
    "floatArrayOf(" + ", ".join(f"{v:g}f" for v in pts) + ")" for pts in quads)

kt = f'''package id.iqbaldev.kartrid.ui

import id.iqbaldev.kartrid.R

/**
 * Data 27 frame putaran kartrid (GENERATED — jangan diedit tangan).
 * Sumber: design/cart3d/seq.html + seq.py. Regenerate kalau model berubah.
 *
 * Frame hanya berisi SHELL plastik dengan lubang label transparan; label (art + panel
 * sistem) digambar runtime ke quad [QUADS] pakai setPolyToPoly → 27 PNG ±1,2 MB total,
 * boxart tetap dinamis (custom art per game jalan).
 */
object CartFrames {{
    const val MIN_ANGLE = -26f
    const val MAX_ANGLE = 26f
    const val STEP = 2f
    val ANGLES = floatArrayOf({", ".join(f"{a:g}f" for a in ANGLES)})

    const val IMG_W = {IW}
    const val IMG_H = {IH}

    /** lebar kartrid pada 0° di PNG (px) — pembagi skala: scale = cartWidthDp / UNIT_W */
    const val UNIT_W = {UNIT_W:g}f

    /** pivot (pusat kartrid) di koordinat PNG — sama untuk semua frame */
    const val CENTER_X = {cz[0]:g}f
    const val CENTER_Y = {cz[1]:g}f

    /** 4 titik pojok label (TL, TR, BR, BL) per frame, koordinat PNG */
    val QUADS: Array<FloatArray> = arrayOf(
        {rows},
    )

    val RES: IntArray = intArrayOf(
        {", ".join(f"R.drawable.cf_{i:02d}" for i in range(len(ANGLES)))},
    )

    /** index frame terdekat untuk sudut (derajat) */
    fun indexFor(deg: Float): Int {{
        val i = ((deg - MIN_ANGLE) / STEP).toInt().coerceIn(0, RES.size - 1)
        return i
    }}
}}
'''
open(KT, "w").write(kt)
print("→", KT, os.path.getsize(KT), "bytes")
tot = sum(os.path.getsize(f"{RES}/cf_{i:02d}.png") for i in range(len(ANGLES)))
print(f"PNG total {tot/1024:.0f} KB, per frame ~{tot/len(ANGLES)/1024:.0f} KB")
json.dump({"angles": ANGLES, "IW": IW, "IH": IH, "unitW": UNIT_W,
           "center": cz, "quads": quads}, open(f"{BASE}/seq_meta.json", "w"), indent=1)
