p = "/home/iqbaladinur/hobbies/kartrid/design/cart3d/render.html"
s = open(p).read()
s = s.replace(
    "tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 8;",
    "tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 8;\n    // tanpa mipmap: teks label tetap tajam & bake == warp (app gambar bitmap apa adanya)\n    tex.minFilter = THREE.LinearFilter; tex.generateMipmaps = false;")
open(p, "w").write(s)
print("patched", s.count("generateMipmaps"))
