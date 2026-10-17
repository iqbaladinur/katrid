import json, numpy as np
from PIL import Image, ImageFilter

M = json.load(open("meta.json"))
quad = M["frame_m14"]["quad"]
frame = Image.open("frame_m14.png").convert("RGBA")
bake = Image.open("bake_unlit.png").convert("RGBA")

def coeffs(dst, src):
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u*x, -u*y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v*x, -v*y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float))

lab = Image.open("labels/gba_emerald.png").convert("RGBA")
W, H = frame.size
src = [(0, 0), (lab.width, 0), (lab.width, lab.height), (0, lab.height)]
comp = Image.alpha_composite(lab.transform((W, H), Image.PERSPECTIVE, coeffs(quad, src), Image.BICUBIC), frame)
comp.save("warp_check.png")

xs = [p[0] for p in quad]; ys = [p[1] for p in quad]
x0, x1, y0, y1 = int(min(xs)) + 3, int(max(xs)) - 3, int(min(ys)) + 3, int(max(ys)) - 3

a = np.asarray(comp.convert("RGB"), float)[y0:y1, x0:x1]
b = np.asarray(bake.convert("RGB"), float)[y0:y1, x0:x1]
d = np.abs(a - b)
print(f"area {x1-x0}x{y1-y0}")
print(f"sharp   : mean={d.mean():.2f} p95={np.percentile(d,95):.1f} >16={100*(d.max(2)>16).mean():.1f}%")
print(f"corr    : {np.corrcoef(a.ravel(), b.ravel())[0,1]:.4f}")

bl = Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
bb = Image.fromarray(b.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
da = np.abs(np.asarray(bl, float) - np.asarray(bb, float))
print(f"blur1.2 : mean={da.mean():.2f} p95={np.percentile(da,95):.1f}")

out = Image.new("RGBA", (W, H * 2 + 6), (22, 18, 31, 255))
out.paste(comp, (0, 0)); out.paste(bake, (0, H + 6))
out.save("warp_vs_bake.png")
print("-> warp_vs_bake.png (atas warp, bawah bake)")
