import re
p = "/home/iqbaladinur/hobbies/kartrid/design/cart3d/render.html"
s = open(p).read()
s = s.replace(
    "renderer.toneMapping = THREE.ACESFilmicToneMapping;\nrenderer.toneMappingExposure = 1.16;",
    "// NoToneMapping: label dicetak = albedo mentah, biar bake == warp di app (nggak ada color shift)\nrenderer.toneMapping = THREE.NoToneMapping;")
s = s.replace("clearcoatRoughness: 0.35, envMapIntensity: 0.85,", "clearcoatRoughness: 0.35, envMapIntensity: 0.55,")
s = s.replace("new THREE.DirectionalLight(0xffffff, 1.1)", "new THREE.DirectionalLight(0xffffff, 0.62)")
s = s.replace("new THREE.DirectionalLight(0xD9D2FF, 0.45)", "new THREE.DirectionalLight(0xD9D2FF, 0.26)")
s = s.replace("new THREE.DirectionalLight(0xFFF3D0, 0.9)", "new THREE.DirectionalLight(0xFFF3D0, 0.5)")
s = s.replace("new THREE.RectAreaLight(0xffffff, 6.0, 26, 15)", "new THREE.RectAreaLight(0xffffff, 3.4, 26, 15)")
s = s.replace("new THREE.RectAreaLight(0xCFC4FF, 2.2, 18, 12)", "new THREE.RectAreaLight(0xCFC4FF, 1.2, 18, 12)")
s = s.replace("new THREE.AmbientLight(0xffffff, 0.12)", "new THREE.AmbientLight(0xffffff, 0.10)")
open(p, "w").write(s)
print("patched", s.count("NoToneMapping"))
