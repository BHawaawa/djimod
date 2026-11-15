from PIL import Image
import os
out = r'fabric_mod/src/main/resources/assets/djimod/textures/gui'
for name in ('rc2_front', 'rc2_front_shadow', 'rc2_stick', 'ui_icons'):
    im = Image.open(os.path.join(out, name + '.png')).convert('RGBA')
    bg = Image.new('RGBA', im.size, (32, 36, 42, 255))
    bg.alpha_composite(im)
    bg.convert('RGB').save(fr'preview\gui_{name}.png')
    print(name, im.size, im.getchannel('A').getextrema())
