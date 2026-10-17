#!/usr/bin/env python3
"""Rakit layar final P2 (rak landscape) dari hasil render 3D + label bitmap."""
import json, os
from playwright.sync_api import sync_playwright
from PIL import Image

BASE = "/home/iqbaladinur/hobbies/kartrid/design"
C3D = os.path.join(BASE, "cart3d")
OUT = os.path.join(BASE, "shots", "probe3d")
os.makedirs(OUT, exist_ok=True)
CH = "/home/iqbaladinur/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome"

M = json.load(open(os.path.join(C3D, "meta.json")))
K = 0.34                       # dp per px render → kartrid ≈ 305 dp
D = lambda v: round(v * K, 2)

def place(name, center_x, bottom_y, cart_w=305.0, rot=0.0):
    """cart_w = lebar kartrid di 0° (dp). Skala fisik konsisten per kelas."""
    m = M[name]; b = m["bbox"]; ppm = m["pxPerMm"]
    k = cart_w / (31.5 * ppm)
    left = center_x - (b["x"] + b["w"] / 2) * k
    top = bottom_y - (b["y"] + b["h"]) * k
    tr = f";transform:rotate({rot}deg);transform-origin:50% 100%" if rot else ""
    cls = "dim" if rot else "focus"
    return (f'<img class="cart {cls}" src="cart3d/{name}.png" '
            f'style="left:{left:.1f}px;top:{top:.1f}px;width:{1200*k:.1f}px{tr}">')

FOCUS_CX, BASE_Y = 480, 430
N_W, F_W = 200.0, 305.0                 # lebar tetangga / fokus
STEP1 = F_W / 2 + 46 + N_W / 2          # fokus -> tetangga
STEP2 = N_W + 44                        # tetangga -> tetangga luar
carts = "\n  ".join([
    place("cart_gb_l",   FOCUS_CX - STEP1 - STEP2, BASE_Y, 150, -1.8),
    place("cart_gbc_l",  FOCUS_CX - STEP1,         BASE_Y, N_W, -0.9),
    place("cart_gba_focus", FOCUS_CX,              BASE_Y - 12, F_W),
    place("cart_gb_r",   FOCUS_CX + STEP1,         BASE_Y, N_W,  0.9),
    place("cart_gbc_r",  FOCUS_CX + STEP1 + STEP2, BASE_Y, 150,  1.8),
])
# kartrid fokus dapat ring + lift
carts = carts.replace('class="cart focus"', 'class="cart focus"').replace(
    'src="cart3d/cart_gba_focus.png"',
    'src="cart3d/cart_gba_focus.png"')

HTML = f"""<!doctype html><html lang="id"><head><meta charset="utf-8"><title>KARTRID — P2 final</title>
<style>
@font-face{{font-family:Inter;font-weight:500;src:url('fonts/inter-500-latin.woff2') format('woff2')}}
@font-face{{font-family:ArchivoBlack;font-weight:400;src:url('fonts/archivo-black-400-latin.woff2') format('woff2')}}
@font-face{{font-family:JBMono;font-weight:500;src:url('fonts/jetbrains-mono-500-latin.woff2') format('woff2')}}
@font-face{{font-family:JBMono;font-weight:700;src:url('fonts/jetbrains-mono-700-latin.woff2') format('woff2')}}
:root{{--bg:#16121F;--surface:#201A2C;--fg:#EAE6F2;--muted:#2A2338;--muted_fg:#B0A8C2;
 --hairline:#362E47;--shadow:#050309;--violet:#A98BFF;--lime:#D4F24E}}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:#0b0910;font-family:Inter}}
.screen{{width:960px;height:640px;background:var(--bg);color:var(--fg);position:relative;overflow:hidden}}
.top{{height:84px;display:flex;align-items:center;gap:22px;padding:0 22px}}
.tab{{display:flex;align-items:center;gap:8px;font-family:JBMono;font-size:15px;font-weight:700;color:rgba(234,230,242,.75)}}
.tab.on{{color:var(--lime)}}
.tab .dot{{width:10px;height:10px}}
.tab .cnt{{font-size:11px;color:var(--muted_fg);font-weight:500}}
.tab.on .cnt{{color:var(--lime)}}
.gear{{margin-left:auto;font-size:17px;color:var(--muted_fg)}}
.cart{{position:absolute;filter:drop-shadow(0 10px 14px rgba(0,0,0,.55))}}
.cart.dim{{filter:brightness(.58) saturate(.92) drop-shadow(0 7px 10px rgba(0,0,0,.5))}}
.cart.focus{{filter:drop-shadow(0 16px 22px rgba(0,0,0,.65))}}
.ring{{position:absolute;border:2px solid rgba(212,242,78,.5)}}
.titleblock{{position:absolute;left:0;right:0;top:470px;text-align:center}}
.titleblock h2{{font-family:ArchivoBlack;font-size:32px;line-height:1.05}}
.titleblock p{{font-family:JBMono;font-size:13px;color:var(--muted_fg);margin-top:7px;letter-spacing:.8px}}
.hint{{position:absolute;left:0;right:0;top:596px;display:flex;justify-content:center;gap:18px;
 font-family:JBMono;font-size:10.5px;color:var(--muted_fg)}}
.hint b{{color:var(--fg);border:1.2px solid var(--hairline);background:var(--surface);padding:3px 6px;
 margin-right:6px;font-weight:500;box-shadow:2px 2px 0 var(--shadow)}}
.rot{{position:absolute;left:0;right:0;top:438px;text-align:center;font-family:JBMono;font-size:9.5px;
 letter-spacing:1.4px;color:var(--lime);opacity:.85}}
</style></head><body>
<div class="screen">
  <div class="top">
    <div class="tab"><i class="dot" style="background:#BFBCCB"></i>GB<span class="cnt">128</span></div>
    <div class="tab"><i class="dot" style="background:#6E9FC7"></i>GBC<span class="cnt">96</span></div>
    <div class="tab on"><i class="dot" style="background:#5A4C93"></i>GBA<span class="cnt">214</span></div>
    <div class="gear">⚙</div>
  </div>
  {carts}
  <div class="rot">◀ GESER UNTUK PUTAR · SNAP BALIK KE TENGAH ▶</div>
  <div class="titleblock"><h2>Pokémon Emerald Version</h2><p>USA · 12.6 MB · main 17×</p></div>
  <div class="hint"><span><b>◀ ▶</b>move</span><span><b>drag</b>puter</span><span><b>A</b>insert</span>
    <span><b>X</b>fav</span><span><b>Y</b>cari</span><span><b>B</b>setting</span></div>
</div>
</body></html>"""

path = os.path.join(BASE, "probe-p2.html")
open(path, "w").write(HTML)

AUDIT = """() => {
  const s = document.querySelector('.screen'), sr = s.getBoundingClientRect();
  const out = {bad: [], imgs: document.images.length};
  s.querySelectorAll('*').forEach(e => {
    const r = e.getBoundingClientRect();
    if (r.width && (r.right > sr.right + 200 || r.left < sr.left - 200 || r.bottom > sr.bottom + 40))
      out.bad.push([e.className, Math.round(r.left), Math.round(r.right)]);
  });
  const f = s.querySelector('.cart.focus'); const fr = f.getBoundingClientRect();
  out.focus = {cx: Math.round((fr.left + fr.right) / 2), top: Math.round(fr.top - sr.top),
               bottom: Math.round(fr.bottom - sr.top), w: Math.round(fr.width)};
  out.ok = [...document.images].filter(i => i.complete && i.naturalWidth > 0).length;
  return out;
}"""

with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CH, args=["--no-sandbox", "--allow-file-access-from-files", "--force-color-profile=srgb"])
    pg = b.new_page(viewport={"width": 1000, "height": 700}, device_scale_factor=2)
    pg.goto("file://" + path)
    pg.wait_for_timeout(700)
    a = pg.evaluate(AUDIT)
    shot = f"{OUT}/p2_final.png"
    pg.locator(".screen").screenshot(path=shot)
    print(a)
    b.close()

im = Image.open(shot).convert("RGB")
print("png", im.size, "colors", len(im.getcolors(maxcolors=900000) or []))
