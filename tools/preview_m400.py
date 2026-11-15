# Preview renders for the DJI Matrice 400 entity -> <out>/  (usage: preview_m400.py <bbmodel> <out_dir> [names])
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from render_anim import Model, render, view, rotmat

src, out = sys.argv[1], sys.argv[2]
only = sys.argv[3].split(',') if len(sys.argv) > 3 else None
os.makedirs(out, exist_ok=True)
m = Model(src)
C = (0, 15, 3)
V = {
    'persp_front':   (view(180 + 32, 22), 11, C),
    'persp_rear':    (view(-35, 24), 11, C),
    'top':           (rotmat([90, 0, 0]), 9.5, (0, 15, 3.5)),
    'bottom':        (rotmat([0, 0, 180]) @ rotmat([-90, 0, 0]), 9.5, (0, 15, 3.5)),
    'front':         (rotmat([0, 180, 0]), 11, (0, 15, 0)),
    'rear':          (rotmat([0, 0, 0]), 11, (0, 15, 0)),
    'side':          (rotmat([0, -90, 0]), 9.5, (0, 15, 3.5)),
    'low_side':      (view(180 + 62, 6), 11, (0, 14, 3)),
    'closeup_front': (view(180 + 28, 14), 34, (0, 23, -12)),
    'closeup_rear':  (view(-28, 22), 34, (0, 23, 12)),
    'closeup_motor': (view(180 + 30, 24), 30, (29.2, 20, -19.6)),
    'closeup_gear':  (view(180 + 60, 10), 22, (12, 8, 3)),
    'battery_bay_empty':     (view(-30, 40), 30, (0, 23, 9), 'empty'),
    'battery_bay_installed': (view(-30, 40), 30, (0, 23, 9)),
    'battery_bay_side_empty': (view(-90, 12), 26, (0, 22, 8), 'empty'),
    'persp_rear_no_battery': (view(-35, 24), 11, C, 'empty'),
}
ST = {'installed': [('animation.m400.battery_installed', 0.0)], 'empty': [('animation.m400.battery_empty', 0.0)]}
for n, spec in V.items():
    if only and n not in only:
        continue
    R, s, c = spec[:3]
    st = spec[3] if len(spec) > 3 else 'installed'
    render(m.pose(ST[st]), m.tex, R, size=900, scale=s, center=c, bg=(240, 240, 240), cull=True).save(os.path.join(out, f'{n}.png'))
names = ['persp_front', 'persp_rear', 'top', 'bottom', 'front', 'rear', 'side', 'low_side']
if all(os.path.exists(os.path.join(out, f'{n}.png')) for n in names):
    sheet = Image.new('RGB', (2000, 1000), (240, 240, 240))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(out, f'{n}.png')).resize((500, 500), Image.LANCZOS)
        sheet.paste(im, ((i % 4) * 500, (i // 4) * 500))
        ImageDraw.Draw(sheet).text(((i % 4) * 500 + 10, (i // 4) * 500 + 10), n, fill=(70, 70, 70))
    sheet.save(os.path.join(out, 'overview.png'))
print('previews ->', out)
