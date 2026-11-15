# Preview renders for the DJI Power AURO 2000 ELITE entity  (usage: preview_auro.py <bbmodel> <out_dir> [names])
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from render_anim import Model, render, view, rotmat

src, out = sys.argv[1], sys.argv[2]
only = sys.argv[3].split(',') if len(sys.argv) > 3 else None
os.makedirs(out, exist_ok=True)
m = Model(src)
A = 'animation.auro2000elite.'
ST = {'stowed': [(A + 'cable_stowed', 0.0)], 'out': [(A + 'cable_out', 0.0)], 'plugged': [(A + 'cable_plugged', 0.0)]}
C = (0, 30, 0)
V = {
    'persp_front':  (view(180 + 28, 16), 6.4, C),
    'persp_rear':   (view(-32, 20), 6.4, C),
    'front':        (rotmat([0, 180, 0]), 7.2, (0, 30.6, 0)),
    'rear':         (rotmat([0, 0, 0]), 7.2, (0, 30.6, 0)),
    'side':         (rotmat([0, -90, 0]), 9.0, (0, 30.6, 0)),
    'top':          (rotmat([90, 0, 0]) @ rotmat([0, 180, 0]), 7.2, (0, 30, 0)),
    'bottom':       (rotmat([-90, 0, 0]) @ rotmat([0, 180, 0]), 7.2, (0, 30, 0)),
    'low_side':     (view(180 + 62, 6), 6.4, C),
    'closeup_panel': (view(180 + 10, 8), 15, (8, 44, -28)),
    'closeup_ac':   (view(180 + 18, 8), 19, (31, 44, -28)),
    'closeup_mid':  (view(180 + 6, 6), 30, (-7, 44, -28)),
    'closeup_screen': (view(180 - 8, 6), 30, (-34, 45, -28)),
    'closeup_handle': (view(180 + 70, 12), 13, (-50, 40, 0)),
    'cable_out':    (view(180 + 35, 22), 4.4, (0, 25, -35), 'out'),
    'cable_out_side': (view(180 + 85, 8), 4.4, (0, 25, -35), 'out'),
    'cable_plugged': (view(180 + 35, 22), 4.4, (0, 25, -40), 'plugged'),
}
for n, spec in V.items():
    if only and n not in only:
        continue
    R, s, c = spec[:3]
    st = spec[3] if len(spec) > 3 else 'stowed'
    render(m.pose(ST[st]), m.tex, R, size=900, scale=s, center=c, bg=(240, 240, 240), cull=True).save(os.path.join(out, f'{n}.png'))
names = ['persp_front', 'persp_rear', 'front', 'rear', 'side', 'top', 'low_side', 'cable_out']
if all(os.path.exists(os.path.join(out, f'{n}.png')) for n in names):
    sheet = Image.new('RGB', (2000, 1000), (240, 240, 240))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(out, f'{n}.png')).resize((500, 500), Image.LANCZOS)
        sheet.paste(im, ((i % 4) * 500, (i // 4) * 500))
        ImageDraw.Draw(sheet).text(((i % 4) * 500 + 10, (i // 4) * 500 + 10), n, fill=(70, 70, 70))
    sheet.save(os.path.join(out, 'overview.png'))
print('previews ->', out)
