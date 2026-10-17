#!/usr/bin/env python3
"""Bukti jalur runtime: warp label ke quad (setPolyToPoly-equivalent) + frame di depan,
lalu bandingkan dengan render yang labelnya di-bake. Kalau diff kecil, Compose aman."""
import json, os
import numpy as np
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
M = json.load(open(os.path.join(BASE, "meta.json")))

def warp_coeffs(dst, src):
    """coeff buat Image.transform(PERSPECTIVE): output(dst) -> input(src)."""
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u*x, -u*y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v*x, -v*y]); B.append(v)
    return np.linalg.solve(np.array(A, dtype=float), np.array(B, dtype=float))

frame = Image.open(os.path.join(BASE, "frame_m14.png")).convert("RGBA")
bake = Image.open(os.path.join(BASE, "bake_unlit.png")).convert("RGBA")
label = Image.open(os.path.join(BASE, "labels/gba_emerald.png")).convert("RGBA")
W, H = frame.size

quad = M["frame_m14"]["quad"]                       # TL, TR, BR, BL
src = [(0, 0), (label.width, 0), (label.width, label.height), (0, label.height)]
co = warp_coeffs(quad, src)
warped = label.transform((W, H), Image.PERSPECTIVE, co, Image.BICUBIC)

composite = Image.alpha_composite(warped, frame)

a = np.asarray(composite.convert("RGB"), dtype=np.int16)
b = np.asarray(bake.convert("RGB"), dtype=np.int16)
# bandingkan hanya di area label (bbox quad) biar area luar (alpha 0) nggak ngotorin
xs = [p[0] for p in quad]; ys = [p[1] for p in quad]
x0, x1 = int(min(xs)), int(max(xs)); y0, y1 = int(min(ys)), int(max(ys))
d = np.abs(a[y0:y1, x0:x1] - b[y0:y1, x0:x1])
print("area label", (x1-x0, y1-y0))
print("mean abs diff = %.2f/255 · p95 = %.1f · max = %d" % (d.mean(), np.percentile(d, 95), d.max()))
print("piksel >16 selisih: %.2f%%" % (100.0 * (d.max(axis=2) > 16).mean()))

composite.save(os.path.join(BASE, "warp_check.png"))
out = os.path.join(BASE, "warp_vs_bake.png")
side = Image.new("RGBA", (W, H*2+8), (22,18,31,255))
side.paste(composite, (0, 0)); side.paste(bake, (0, H+8))
side.save(out)
print("->", out)
