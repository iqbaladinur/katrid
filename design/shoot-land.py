#!/usr/bin/env python3
"""Render mockup LANDSCAPE (960x640, target KONKR Pocket Advance) -> PNG."""
import os
from playwright.sync_api import sync_playwright
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "shots")
os.makedirs(OUT, exist_ok=True)
W, H = 960, 640
PAD = 20          # body padding
DSF = 2
IDS = ["l1", "l2a", "l2b", "l3"]

AUDIT = """() => {
  const el = document.querySelector('.screen:target');
  const pr = el.getBoundingClientRect();
  const bad = [];
  el.querySelectorAll('*').forEach(e => {
    const cs = getComputedStyle(e);
    if (!e.children.length && e.textContent.trim() && e.scrollWidth - e.clientWidth > 2
        && cs.overflow === 'visible' && cs.whiteSpace !== 'nowrap')
      bad.push('CLIP ' + (e.className || e.tagName) + ' :: ' + e.textContent.trim().slice(0, 30));
    const r = e.getBoundingClientRect();
    if (r.width && (r.right > pr.right + 140 || r.bottom > pr.bottom + 140 || r.top < pr.top - 140))
      bad.push('FAR ' + (e.className || e.tagName) + ' ' + [r.left, r.top, r.right, r.bottom].map(Math.round));
  });
  return [...new Set(bad)];
}"""

with sync_playwright() as p:
    b = p.chromium.launch(args=["--allow-file-access-from-files"])
    for i in IDS:
        pg = b.new_page(viewport={"width": W + 2 * PAD, "height": H + 2 * PAD}, device_scale_factor=DSF)
        pg.goto(f"file://{BASE}/mockup-land.html#{i}")
        pg.wait_for_timeout(450)
        issues = pg.evaluate(AUDIT)
        raw = f"/tmp/_l_raw_{i}.png"
        # screenshot elemen .screen: pixel-exact 960x640 @DSF, tanpa nebak offset crop
        pg.locator(".screen:target").screenshot(path=raw)
        pg.close()
        im = Image.open(raw)
        crop = im
        dst = f"{OUT}/{i}.png"
        crop.save(dst)
        cols = crop.convert("RGB").getcolors(maxcolors=400000) or []
        info = pg.evaluate("()=>({f:document.fonts.check('600 16px Inter')&&document.fonts.check('16px ArchivoBlack'),"
                           "img:[...document.images].filter(i=>i.getBoundingClientRect().width>0).length})") if False else None
        print(f"{i}: {crop.size} colors={len(cols)} issues={issues[:5]}")
    b.close()
print("ok ->", OUT)
