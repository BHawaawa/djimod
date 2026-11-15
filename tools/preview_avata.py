# Preview renders for the Avata 360 drone (v2) -> model/avata360/preview
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from render_anim import Model, render, view, rotmat

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
m = Model(src)
IN, EMPTY, M360 = 'animation.avata360.battery_installed', 'animation.avata360.battery_empty', 'animation.avata360.mode_360'
C = (0, 2.6, 0)
V = {
    'persp_front':     ([IN], view(180 + 35, 28), 26, C),
    'persp_rear':      ([IN], view(-30, 28), 26, C),
    'top':             ([IN], rotmat([90, 0, 0]), 26, C),
    'bottom':          ([IN], rotmat([0, 0, 180]) @ rotmat([-90, 0, 0]), 26, C),
    'front':           ([IN], rotmat([0, 180, 0]), 26, C),
    'rear':            ([IN], rotmat([0, 0, 0]), 26, C),
    'side':            ([IN], rotmat([0, -90, 0]), 26, C),
    'low_side':        ([IN], view(180 + 70, 8), 26, C),
    'closeup_camera':  ([IN], view(180 + 30, 18), 60, (0, 3, -9)),
    'mode_360_camera': ([IN, M360], view(180 + 30, 18), 60, (0, 3, -9)),
    'closeup_prop':    ([IN], view(180 + 20, 55), 70, (9, 2.6, -6.1)),
    'battery_bay_empty': ([EMPTY], view(-24, 14), 50, (0, 2.6, 8)),
    'battery_inserted':  ([IN], view(-24, 14), 50, (0, 2.6, 9)),
}
for n, (anims, R, s, c) in V.items():
    a = [(x, 1.0) for x in anims]
    render(m.pose(a), m.tex, R, size=900, scale=s, center=c, bg=(238, 238, 238), cull=True).save(os.path.join(out, f'{n}.png'))
names = ['persp_front', 'persp_rear', 'top', 'bottom', 'front', 'rear', 'side', 'low_side']
sheet = Image.new('RGB', (2000, 1000), (238, 238, 238))
for i, n in enumerate(names):
    im = Image.open(os.path.join(out, f'{n}.png')).resize((500, 500), Image.LANCZOS)
    sheet.paste(im, ((i % 4) * 500, (i // 4) * 500))
    ImageDraw.Draw(sheet).text(((i % 4) * 500 + 10, (i // 4) * 500 + 10), n, fill=(70, 70, 70))
sheet.save(os.path.join(out, 'overview.png'))
print('previews ->', out)
