# Preview renders for the battery item models (uses tools/render.py software rasterizer)
# usage: python3 preview_item.py <build_dir> <preview_dir> <model_name> [bb|geo] [view,view,...]
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image
from render import load_bbmodel, load_geo, render, rotmat, view
from bbgen import display_matrix

src, out, M = sys.argv[1], sys.argv[2], sys.argv[3]
mode = sys.argv[4] if len(sys.argv) > 4 else 'bb'
os.makedirs(out, exist_ok=True)
if mode == 'bb':
    quads, tex = load_bbmodel(os.path.join(src, f'{M}.bbmodel'))
else:
    quads, tex = load_geo(os.path.join(src, f'{M}.geo.json'), os.path.join(src, f'{M}.png'))
meta = json.load(open(os.path.join(src, 'meta.json')))
lo, hi = np.array(meta['bbox_min']), np.array(meta['bbox_max'])
C = tuple((lo + hi) / 2)
L = float(max(hi - lo))
s = 900 * 0.78 / L
zc, zf = lo[2], hi[2]
V = {
    'overview':      (view(180 + 38, 30), s, C),
    'persp_cap':     (view(180 + 140, 26), s, C),
    'persp_end':     (view(35, 24), s, C),
    'top':           (rotmat([90, 0, 0]), s, C),
    'bottom':        (rotmat([0, 0, 180]) @ rotmat([-90, 0, 0]), s, C),
    'side':          (rotmat([0, -90, 0]), s, C),
    'cap_end':       (rotmat([0, 180, 0]), s * 1.9, (C[0], C[1], zc)),
    'connector_end': (rotmat([0, 0, 0]), s * 1.9, (C[0], C[1], zf)),
    'closeup_cap':   (view(180 + 50, 22), s * 2.1, (C[0] + 2, C[1], zc + 3.5)),
    'standing':      (view(180 + 25, 10) @ rotmat([-90, 0, 0]), s, C),
}
items = json.load(open(os.path.join(src, f'{M}.item.json')))['display'] if os.path.exists(os.path.join(src, f'{M}.item.json')) else {}
names = sys.argv[5].split(',') if len(sys.argv) > 5 else list(V) + ['gui']
for n in names:
    if n == 'gui':
        # GUI slot: 16x16 px item square, viewer on +Z; model centred on the item pivot
        R = display_matrix(items['gui'])
        img = render(quads, tex, R, size=512, scale=512 / 16.0, center=(0, 0, 0), bg=(139, 139, 139))
        # inventory slot frame for reference
        a = np.asarray(img).copy(); a[:2] = 55; a[-2:] = 255; a[:, :2] = 55; a[:, -2:] = 255
        Image.fromarray(a).save(os.path.join(out, 'gui.png'))
        Image.fromarray(a).resize((64, 64), Image.LANCZOS).save(os.path.join(out, 'gui_64px.png'))
        continue
    R, sc, c = V[n]
    render(quads, tex, R, size=900, scale=sc, center=c, bg=(236, 236, 236)).save(os.path.join(out, f'{n}.png'))
print(len(quads), 'quads ->', out)
