import json, numpy as np
from PIL import Image

M = json.load(open("meta.json"))
quad = M["frame_m14"]["quad"]
frame = Image.open("frame_m14.png").convert("RGBA")
lab = Image.open("labels/gba_emerald.png").convert("RGBA")

def coeffs(dst, src):
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u*x, -u*y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v*x, -v*y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float))

W, H = frame.size
src = [(0, 0), (lab.width, 0), (lab.width, lab.height), (0, lab.height)]
comp = Image.alpha_composite(lab.transform((W, H), Image.PERSPECTIVE, coeffs(quad, src), Image.BICUBIC), frame)
A = np.asarray(comp.convert("RGB")).astype(int)
B = np.asarray(Image.open("bake_unlit.png").convert("RGB")).astype(int)
panel = np.array([90, 76, 147])

def first_panel(img, y, xr=(300, 1050)):
    d = np.abs(img[y, xr[0]:xr[1]] - panel).sum(1)
    idx = np.where(d < 24)[0]
    return xr[0] + int(idx.min()) if len(idx) else -1

def first_row(img, x, yr=(150, 940)):
    d = np.abs(img[yr[0]:yr[1], x] - panel).sum(1)
    idx = np.where(d < 24)[0]
    return yr[0] + int(idx.min()) if len(idx) else -1

for y in (400, 500, 600, 700):
    print("y", y, "panel-x warp", first_panel(A, y), "bake", first_panel(B, y))
for x in (800, 900):
    print("x", x, "panel-y warp", first_row(A, x), "bake", first_row(B, x))
print("quad", [[round(a), round(b)] for a, b in quad])
