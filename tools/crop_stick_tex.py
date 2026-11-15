from PIL import Image
im = Image.open(r'model/RC2/textures/rc2.png').convert('RGBA')
# top faces of stick cap cubes (uv coords from geo); save a few candidates
for name, box in {
    'stick_top_a': (746, 472, 776, 495),
    'stick_top_b': (808, 472, 838, 495),
    'stick_top_c': (374, 473, 404, 497),
    'stick_top_d': (932, 472, 962, 495),
}.items():
    im.crop(box).resize((300, 230), Image.NEAREST).save(fr'preview\{name}.png')
print(im.size)
