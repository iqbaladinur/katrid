#!/usr/bin/env python3
"""Gen shell kartrid GBA '3D' (front view) -> PNG transparent, buat Kartrid launcher.
Bukan foto asli, tapi render plastik: gradasi vertikal + bevel + specular + grip + label inset.
Output: android/app/src/main/res/drawable-nodpi/cart_gba.png
Label rect (fraksi) dicetak biar app tau di mana overlay boxart.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 500, 620
RAD_TOP, RAD_BOT = 46, 16
OUT = "/home/iqbaladinur/hobbies/kartrid/android/app/src/main/res/drawable-nodpi/cart_gba.png"

# --- mask shell: rounded atas RAD_TOP, bawah RAD_BOT ---
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], radius=RAD_TOP, fill=255)
ycut = int(H * 0.82)
mb = Image.new("L", (W, H), 0)
ImageDraw.Draw(mb).rounded_rectangle([0, ycut, W - 1, H - 1], radius=RAD_BOT, fill=255)
mask.paste(mb.crop((0, ycut, W, H)), (0, ycut))

shell = Image.new("RGBA", (W, H), (0, 0, 0, 0))

# --- gradasi vertikal plastik ---
grad = np.zeros((H, W, 4), dtype=np.uint8)
top = np.array([240, 238, 229], float)
mid = np.array([211, 207, 197], float)
bot = np.array([176, 172, 162], float)
for y in range(H):
    t = y / H
    if t < 0.28:
        k = t / 0.28
        c = top + (mid - top) * k
    elif t < 0.78:
        k = (t - 0.28) / 0.50
        c = mid + (bot - mid) * k
    else:
        c = bot
    grad[y, :, :3] = c.astype(np.uint8)
grad[:, :, 3] = 255
# lateral: kiri sedikit lebih gelap (cahaya dari kiri-atas)
lx = np.linspace(0.965, 0.955, W)
lx[: int(W * 0.15)] = np.linspace(0.90, 0.965, int(W * 0.15))
grad[:, :, :3] = (grad[:, :, :3].astype(float) * lx[None, :, None]).astype(np.uint8)

shell = Image.fromarray(grad, "RGBA")
shell.putalpha(mask)

# --- label inset (gelap, biar boxart pop) ---
lx0, ly0, lx1, ly1 = 0.115, 0.205, 0.885, 0.855
lx0p, ly0p = int(W * lx0), int(H * ly0)
lx1p, ly1p = int(W * lx1), int(H * ly1)
lab = Image.new("RGBA", (W, H), (0, 0, 0, 0))
lg = np.zeros((ly1p - ly0p, lx1p - lx0p, 4), dtype=np.uint8)
for y in range(lg.shape[0]):
    t = y / lg.shape[0]
    c = int(26 + 14 * t)
    lg[y, :, :3] = c
lg[:, :, 3] = 255
lab.paste(Image.fromarray(lg, "RGBA"), (lx0p, ly0p))
lm = Image.new("L", (W, H), 0)
ImageDraw.Draw(lm).rounded_rectangle([lx0p, ly0p, lx1p, ly1p], radius=14, fill=255)
lab.putalpha(lm)
# border label 2px gelap pekat
bd = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(bd).rounded_rectangle([lx0p - 2, ly0p - 2, lx1p + 2, ly1p + 2], radius=16, outline=(8, 8, 10, 255), width=3)
lab = Image.alpha_composite(lab, bd)
shell = Image.alpha_composite(shell, lab)

# --- specular top + inner bevel ---
spec = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(spec).rounded_rectangle([0, 0, W - 1, int(H * 0.14)], radius=RAD_TOP, fill=(255, 255, 255, 30))
shell = Image.alpha_composite(shell, spec)
line = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(line)
d.rounded_rectangle([1, 1, W - 2, H - 2], radius=RAD_TOP, outline=(255, 255, 255, 70), width=2)
d.rounded_rectangle([2, int(H * 0.86), W - 3, H - 3], radius=RAD_BOT, outline=(0, 0, 0, 70), width=3)
shell = Image.alpha_composite(shell, line)

# --- grip atas (indent kanan-kiri ala GBA) ---
grip = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(grip)
gd.rounded_rectangle([int(W * 0.13), int(H * 0.045), int(W * 0.22), int(H * 0.062)], radius=4, fill=(0, 0, 0, 110))
gd.rounded_rectangle([int(W * 0.79), int(H * 0.045), int(W * 0.86), int(H * 0.060)], radius=4, fill=(0, 0, 0, 110))
shell = Image.alpha_composite(shell, grip)

# --- lip bawah (garis halus) ---
lip = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(lip).line([(int(W * 0.10), int(H * 0.955)), (W - int(W * 0.10), int(H * 0.955))], fill=(0, 0, 0, 60), width=2)
shell = Image.alpha_composite(shell, lip)

# --- shadow lembut di bawah ---
sh = Image.new("RGBA", (W + 60, H + 60), (0, 0, 0, 0))
ImageDraw.Draw(sh).ellipse([W * 0.06, H * 0.93, W * 0.94, H * 1.02], fill=(5, 5, 8, 130))
sh = sh.filter(ImageFilter.GaussianBlur(10))
final = Image.new("RGBA", (W + 60, H + 60), (0, 0, 0, 0))
final.alpha_composite(sh, (30, 8))
final.alpha_composite(shell, (30, 8))
final = final.crop((0, 0, W, H + 30))

final.save(OUT)
print("saved", OUT, final.size)
print("LABEL_RECT_XYWH=(%.3f, %.3f, %.3f, %.3f)" % (lx0, ly0, lx1 - lx0, ly1 - ly0))