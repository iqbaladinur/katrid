#!/usr/bin/env python3
"""Bikin bitmap label kartrid: art 1:1 (nggak di-crop) + panel warna sistem di kanan."""
import os, html
from playwright.sync_api import sync_playwright

BASE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(BASE, "..", "art")
FONTS = os.path.join(BASE, "..", "fonts")
OUT = os.path.join(BASE, "labels")
os.makedirs(OUT, exist_ok=True)
CH = "/home/iqbaladinur/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome"

W, H = 1120, 720          # rasio label 1,556

SYS = {
    "GB":  dict(panel="#CFC9DD", fg="#16121F", sub="#4F4862", code="GB",  name="GAME BOY"),
    "GBC": dict(panel="#4F86AE", fg="#F6F3FF", sub="#D6E6F0", code="GBC", name="GAME BOY COLOR"),
    "GBA": dict(panel="#5A4C93", fg="#F6F3FF", sub="#D8D0F2", code="GBA", name="GAME BOY ADVANCE"),
}

LABELS = [
    ("gba_emerald", "GBA", "Pokémon Emerald Version", "USA · 12.6 MB · MAIN 17×", "gba_Pokemon_-_Emerald_Version__USA__Europe_.png"),
    ("gba_metroid", "GBA", "Metroid Fusion",           "USA · 8.4 MB · MAIN 6×",  "gba_Metroid_Fusion__USA_.png"),
    ("gba_golden",  "GBA", "Golden Sun",                "USA · 8.0 MB · MAIN 3×",  "gba_Golden_Sun__USA__Europe_.png"),
    ("gbc_crystal", "GBC", "Pokémon Crystal Version",  "USA · 2.0 MB · MAIN 24×", "gbc_Pokemon_-_Crystal_Version__USA_.png"),
    ("gbc_zelda",   "GBC", "Zelda: Oracle of Ages",    "USA · 1.0 MB · MAIN 2×",  "gbc_Legend_of_Zelda__The_-_Oracle_of_Ages__USA__Australia_.png"),
    ("gb_mario",    "GB",  "Super Mario Land",         "World · 64 KB · MAIN 31×", "gb_Super_Mario_Land__World_.png"),
    ("gb_tetris",   "GB",  "Tetris",                    "World · 32 KB · MAIN 58×", "gb_Tetris__World___Rev_1_.png"),
]

TPL = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{{font-family:Inter;font-weight:600;src:url('file://{f}/inter-600-latin.woff2') format('woff2')}}
@font-face{{font-family:ArchivoBlack;font-weight:400;src:url('file://{f}/archivo-black-400-latin.woff2') format('woff2')}}
@font-face{{font-family:JBMono;font-weight:500;src:url('file://{f}/jetbrains-mono-500-latin.woff2') format('woff2')}}
@font-face{{font-family:JBMono;font-weight:700;src:url('file://{f}/jetbrains-mono-700-latin.woff2') format('woff2')}}
*{{margin:0;padding:0;box-sizing:border-box}}
body{{width:{W}px;height:{H}px;overflow:hidden;background:{panel}}}
.label{{position:absolute;inset:0;background:{panel};display:flex}}
.art{{width:{H}px;height:{H}px;flex:0 0 auto;background:#0B0810}}
.art img{{width:100%;height:100%;object-fit:cover;display:block}}
.panel{{flex:1;padding:34px 30px 26px 34px;display:flex;flex-direction:column;color:{fg}}}
.code{{font-family:ArchivoBlack;font-size:104px;line-height:.86;letter-spacing:-1px}}
.name{{font-family:JBMono;font-weight:700;font-size:15px;letter-spacing:3.2px;color:{sub};margin-top:14px}}
.rule{{height:2px;background:{fg};opacity:.35;margin:22px 0 18px}}
.title{{font-family:JBMono;font-weight:700;font-size:30px;line-height:1.22;letter-spacing:-.2px;
  display:-webkit-box;-webkit-line-clamp:5;-webkit-box-orient:vertical;overflow:hidden}}
.meta{{margin-top:auto;font-family:JBMono;font-size:15px;letter-spacing:.6px;color:{sub}}}
</style></head><body>
<div class="label">
  <div class="art"><img src="file://{a}/{art}"></div>
  <div class="panel">
    <div class="code">{code}</div>
    <div class="name">{name}</div>
    <div class="rule"></div>
    <div class="title">{title}</div>
    <div class="meta">{meta}</div>
  </div>
</div></body></html>"""

def build():
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path=CH, args=["--no-sandbox", "--allow-file-access-from-files", "--force-color-profile=srgb"])
        pg = b.new_page(viewport={"width": W, "height": H}, device_scale_factor=1)
        tmp = os.path.join(OUT, "_tmp.html")
        for key, sys, title, meta, art in LABELS:
            s = SYS[sys]
            # CATATAN: set_content() bikin origin opaque → <img src=file://> gagal (naturalWidth 0).
            # Harus file HTML beneran + goto().
            open(tmp, "w").write(TPL.format(W=W, H=H, f=FONTS, a=ART, art=art, panel=s["panel"], fg=s["fg"],
                                            sub=s["sub"], code=s["code"], name=s["name"],
                                            title=html.escape(title), meta=html.escape(meta)))
            pg.goto("file://" + tmp)
            pg.wait_for_function("() => { const i = document.querySelector('.art img'); return i && i.complete && i.naturalWidth > 0; }", timeout=15000)
            pg.wait_for_timeout(200)
            path = os.path.join(OUT, f"{key}.png")
            pg.screenshot(path=path, clip={"x":0,"y":0,"width":W,"height":H})
            print("label", key, sys, path)
        b.close()

if __name__ == "__main__":
    build()
