import json, numpy as np
from PIL import Image

M = json.load(open("meta.json"))
quad = M["frame_m14"]["quad"]
frame = Image.open("frame_m14.png").convert("RGBA")
bake = Image.open("bake_unlit.png").convert("RGBA")
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
a = np.asarray(comp.convert("RGB"), float)
b = np.asarray(Image.open("bake_unlit.png").convert("RGB"), float)
d = np.abs(a - b).max(2)
# kuadran di dalam area label
xs = [p[0] for p in quad]; ys = [p[1] for p in quad]
x0, x1, y0, y1 = int(min(xs)) + 3, int(max(xs)) - 3, int(min(ys)) + 3, int(max(ys)) - 3
sub = d[y0:y1, x0:x1]
h, w = sub.shape
print("quadrant mean diff:")
for name, sl in {"TL": (slice(0, h//2), slice(0, w//2)), "TR": (slice(0, h//2), slice(w//2, w)),
                 "BL": (slice(h//2, h), slice(0, w//2)), "BR": (slice(h//2, h), slice(w//2, w))}.items():
    print("  ", name, round(float(sub[sl].mean()), 2))
iy, ix = np.unravel_index(np.argmax(sub), sub.shape)
print("max diff at label-rel", (ix, iy), "abs", (x0+ix, y0+iy), "val", int(sub[iy, ix]))
Image.fromarray((np.clip(sub * 3, 0, 255)).astype("uint8")).save("diff_map.png")
print("-> diff_map.png")
