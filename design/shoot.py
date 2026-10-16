#!/usr/bin/env python3
"""Render tiap screen mockup KARTRID jadi PNG (playwright + chromium, dsf=2)."""
import os
from playwright.sync_api import sync_playwright
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "shots")
os.makedirs(OUT, exist_ok=True)
SCREENS = {"s1": (441, 834), "s2": (441, 834), "s3": (441, 834),
           "s4": (441, 834), "s5": (441, 834), "s6": (441, 834)}
PAD = 24          # body padding (css px)
W, H = 393, 786   # phone size (css px)
DSF = 2

AUDIT = """() => {
  const pr = document.querySelector('.phone:target').getBoundingClientRect();
  const bad = [];
  document.querySelectorAll('.phone:target *').forEach(el => {
    const cs = getComputedStyle(el);
    if (!el.children.length && el.textContent.trim() && el.scrollWidth - el.clientWidth > 2
        && cs.overflow === 'visible' && cs.whiteSpace !== 'nowrap')
      bad.push('CLIP ' + (el.className||el.tagName) + ' :: ' + el.textContent.trim().slice(0,26));
  });
  // elemen yang sepuluh kali keluar dari frame HP
  document.querySelectorAll('.phone:target *').forEach(el => {
    const r = el.getBoundingClientRect();
    if (r.width && (r.right > pr.right + 120 || r.bottom > pr.bottom + 120 || r.top < pr.top - 120))
      bad.push('FAR ' + (el.className||el.tagName) + ' ' + [r.left,r.top,r.right,r.bottom].map(Math.round));
  });
  return [...new Set(bad)];
}"""

with sync_playwright() as p:
    browser = p.chromium.launch(args=["--allow-file-access-from-files"])
    for name in SCREENS:
        pg = browser.new_page(viewport={"width": 441, "height": 834}, device_scale_factor=DSF)
        pg.goto(f"file://{BASE}/mockup.html#{name}")
        pg.wait_for_timeout(450)
        issues = pg.evaluate(AUDIT)
        raw = f"/tmp/_raw_{name}.png"
        pg.screenshot(path=raw)
        pg.close()
        im = Image.open(raw)
        crop = im.crop((PAD * DSF, PAD * DSF, (PAD + W) * DSF, (PAD + H) * DSF))
        dst = f"{OUT}/{name}.png"
        crop.save(dst)
        rgb = crop.convert("RGB")
        cols = rgb.getcolors(maxcolors=200000) or []
        ncolors = len(cols)
        bg = rgb.getpixel((4, 4))
        lime = sum(1 for c in cols if abs(c[1][0]-0xD4) < 12 and abs(c[1][1]-0xF2) < 12 and abs(c[1][2]-0x4E) < 14)
        print(f"{name}: {crop.size} colors={ncolors} bg={bg} limeblocks={lime} issues={issues[:6]}")
    browser.close()
print("done ->", OUT)
