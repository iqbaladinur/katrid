#!/usr/bin/env python3
"""Render kartrid Switch 3D (three.js headless) -> PNG alpha + meta JSON.

Job = (nama, spin_deg, tilt_deg, label_file|None, plate)
  plate=False -> frame doang, lubang label transparan (buat di-warp di app)
"""
import json, os
from playwright.sync_api import sync_playwright

BASE = os.path.dirname(os.path.abspath(__file__))
CH = "/home/iqbaladinur/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome"
ARGS = ["--no-sandbox", "--use-gl=angle", "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader", "--allow-file-access-from-files",
        "--force-color-profile=srgb"]

# kartrid di rak final
JOBS = [
    ("cart_gba_focus", -14, 5, "labels/gba_emerald.png", True),
    ("cart_gb_l",        0, 0, "labels/gb_tetris.png",   True),
    ("cart_gbc_l",       0, 0, "labels/gbc_crystal.png", True),
    ("cart_gb_r",        0, 0, "labels/gb_mario.png",    True),
    ("cart_gbc_r",       0, 0, "labels/gbc_zelda.png",   True),
    # aset runtime: frame berlubang + quad label, buat setPolyToPoly di Compose
    ("frame_0",          0, 0, None, False),
    ("frame_m14",      -14, 5, None, False),
    # bukti warp: label di-bake ke frame yang sama
    ("bake_m14",       -14, 5, "labels/gba_emerald.png", True),
    ("bake_unlit",     -14, 5, "labels/gba_emerald.png", True),
]

def run(jobs=JOBS):
    metas = {}
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path=CH, args=ARGS)
        for name, spin, tilt, label, plate in jobs:
            qs = f"?spin={spin}&tilt={tilt}&plate={1 if plate else 0}"
            if label:
                qs += f"&label={label}"
            if name == "bake_unlit":
                qs += "&unlit=1"
            pg = b.new_page(viewport={"width": 1400, "height": 1100})
            errs = []
            pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
            pg.on("pageerror", lambda e: errs.append(str(e)))
            pg.goto(f"file://{BASE}/render.html{qs}")
            try:
                pg.wait_for_function("document.title === 'ready'", timeout=60000)
            except Exception:
                print("FAIL", name, errs[:3]); pg.close(); continue
            meta = pg.evaluate("window.__meta")
            out = os.path.join(BASE, f"{name}.png")
            pg.locator("#c").screenshot(path=out, omit_background=True)
            meta["file"] = out
            metas[name] = meta
            print(f"{name}: out={meta['out']} bbox={meta['bbox']} pxPerMm={meta['pxPerMm']:.2f} errs={errs[:1]}")
            print("   quad", [[round(x), round(y)] for x, y in meta["quad"]])
            pg.close()
        b.close()
    with open(os.path.join(BASE, "meta.json"), "w") as f:
        json.dump(metas, f, indent=1)
    return metas

if __name__ == "__main__":
    run()
