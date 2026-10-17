import numpy as np
from PIL import Image

im = Image.open("/home/iqbaladinur/hobbies/kartrid/design/cart3d/bake_unlit.png").convert("RGBA")
a = np.asarray(im).astype(int)
print("plastik atas   ", a[230, 600, :3])
print("plastik kiri   ", a[500, 180, :3])
print("panel label    ", a[500, 900, :3], " (target 90,76,147)")
print("art label      ", a[500, 400, :3])
print("piksel clip255 ", int((a[:, :, :3] == 255).all(2).sum()))
print("alpha tengah   ", a[500, 600, 3])
