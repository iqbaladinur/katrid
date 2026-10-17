#!/usr/bin/env python3
"""Render probe-cart3d.html -> PNG + gate (font, clip, posisi, gambar)."""
import os
from playwright.sync_api import sync_playwright
from PIL import Image

BASE = "/home/iqbaladinur/hobbies/kartrid/design"
OUT = os.path.join(BASE, "shots", "probe3d")
os.makedirs(OUT, exist_ok=True)
CH = "/home/iqbaladinur/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome"
IDS = [("p1", 640), ("p2", 640), ("p3", 640), ("spec", 1180)]

AUDIT = """() => {
  const el = document.querySelector('.screen:target');
  const pr = el.getBoundingClientRect();
  const o = {clip: [], far: []};
  el.querySelectorAll('*').forEach(e => {
    const cs = getComputedStyle(e);
    if (!e.children.length && e.textContent.trim() && e.scrollWidth - e.clientWidth > 2
        && cs.overflow === 'visible' && cs.whiteSpace !== 'nowrap')
      o.clip.push(e.className + '::' + e.textContent.trim().slice(0,26));
    const r = e.getBoundingClientRect();
    if (r.width && (r.right > pr.right + 260 || r.left < pr.left - 260 || r.bottom > pr.bottom + 60))
      o.far.push((e.className||'') + ' ' + [r.left,r.top].map(Math.round));
  });
  const f = el.querySelector('.cart.focus');
  if (f) { const r = f.getBoundingClientRect();
    o.focus = {cx: Math.round((r.left+r.right)/2 - pr.left), w: Math.round(r.width), h: Math.round(r.height),
               inside: r.left >= pr.left && r.right <= pr.right}; }
  o.imgs = {t: document.images.length, ok: [...document.images].filter(i => i.complete && i.naturalWidth > 0).length};
  o.strips = el.querySelectorAll('.strip').length;
  o.fonts = [document.fonts.check('700 16px JBMono'), document.fonts.check('400 16px ArchivoBlack')];
  return o;
}"""

with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CH, args=["--no-sandbox","--allow-file-access-from-files","--force-color-profile=srgb"])
    for i, h in IDS:
        pg = b.new_page(viewport={"width": 1000, "height": h + 40}, device_scale_factor=2)
        pg.goto(f"file://{BASE}/probe-cart3d.html#{i}")
        pg.wait_for_timeout(700)
        a = pg.evaluate(AUDIT)
        path = f"{OUT}/{i}.png"
        pg.locator(".screen:target").screenshot(path=path)
        pg.close()
        im = Image.open(path).convert("RGB")
        cols = im.getcolors(maxcolors=900000) or []
        lime = sum(c for c, col in cols if col == (212, 242, 78))
        print(f"--- {i} {im.size} colors={len(cols)} lime={lime}")
        print("   ", a)
        print("    bg px", im.getpixel((4,4)))
    b.close()
print("ok ->", OUT)
