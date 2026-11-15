# -*- coding: utf-8 -*-
"""Software previews of the Redmi K90 item.
usage: python3 preview_k90.py <build_dir> <preview_dir> [view,view,...] [--ui]
  --ui : composite the Java home overlay (k90_art.home_ui_mock) into the wallpaper first, so the
         renders show what the game draws (clock, status bar, app grid, dock).
"""
import sys, os, json, io, base64, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image
from render import render, rotmat, view
from render_anim import Model
from bbgen import display_matrix

src, out = sys.argv[1], sys.argv[2]
names = sys.argv[3].split(',') if len(sys.argv) > 3 and not sys.argv[3].startswith('--') else None
UI = '--ui' in sys.argv
os.makedirs(out, exist_ok=True)
M = 'redmi_k90'
path = os.path.join(src, f'{M}.bbmodel')


def with_ui(path):
    """returns path of a temp bbmodel whose wallpaper region carries the home UI mock."""
    import k90_art as A
    lay = json.load(open(os.path.join(src, 'screen_layout.json'), encoding='utf-8'))
    m = json.load(open(path, encoding='utf-8'))
    srcb = m['textures'][0]['source'].split(',', 1)[1]
    tex = np.asarray(Image.open(io.BytesIO(base64.b64decode(srcb))).convert('RGBA')).copy()
    el = [e for e in m['elements'] if e['name'] == 'wallpaper'][0]
    u0, v0, u1, v1 = [int(round(a)) for a in el['faces']['north']['uv']]
    wall = tex[v0:v1, u0:u1].copy()
    ui = A.home_ui_mock(wall, lay['screen']['w'], lay['screen']['h'])
    tex[v0:v1, u0:u1] = ui
    buf = io.BytesIO(); Image.fromarray(tex, 'RGBA').save(buf, 'PNG')
    m['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    fd, p = tempfile.mkstemp(suffix='.bbmodel'); os.close(fd)
    json.dump(m, open(p, 'w', encoding='utf-8'))
    return p


mdl = Model(with_ui(path) if UI else path)
quads = mdl.pose([('animation.redmi_k90.home', 0)])
tex = mdl.tex
C = (0, 0, 0.5)
s = 900 * 0.86 / 82.0
LX = [(-11.075 - 1.5) / 2, 28.2, 3.0]
V = {
    'front':        (rotmat([0, 180, 0]), s, C),
    'back':         (rotmat([0, 0, 0]), s, C),
    'persp_front':  (view(180 + 24, 12), s, C),
    'persp_back':   (view(-28, 14), s, C),
    'side_keys':    (rotmat([0, 90, 0]), s, C),
    'side_left':    (rotmat([0, -90, 0]), s, C),
    'bottom':       (rotmat([-90, 0, 0]), s * 2.1, (0, -40.8, 0)),
    'top':          (rotmat([90, 0, 0]), s * 2.1, (0, 40.8, 0)),
    'closeup_cam':  (view(-20, 18), s * 2.6, (LX[0] + 4, LX[1], LX[2])),
    'closeup_bottom': (view(180 + 30, -35), s * 3.0, (0, -38, 0)),
}
order = names or list(V) + ['gui', 'overview']
items = json.load(open(os.path.join(src, f'{M}.item.json')))['display']
for n in order:
    if n == 'gui':
        R = display_matrix(items['gui'])
        img = render(quads, tex, R, size=512, scale=512 / 16.0, center=(0, 0, 0), bg=(139, 139, 139))
        a = np.asarray(img).copy(); a[:2] = 55; a[-2:] = 255; a[:, :2] = 55; a[:, -2:] = 255
        Image.fromarray(a).save(os.path.join(out, 'gui.png'))
        Image.fromarray(a).resize((64, 64), Image.LANCZOS).save(os.path.join(out, 'gui_64px.png'))
        continue
    if n == 'overview':
        ims = [Image.open(os.path.join(out, f'{k}.png')) for k in ('persp_front', 'front', 'back', 'persp_back') if os.path.exists(os.path.join(out, f'{k}.png'))]
        if ims:
            ov = Image.new('RGB', (sum(i.size[0] for i in ims), ims[0].size[1]), (236, 236, 236)); x = 0
            for i in ims: ov.paste(i, (x, 0)); x += i.size[0]
            ov.resize((ov.size[0] // 2, ov.size[1] // 2), Image.LANCZOS).save(os.path.join(out, 'overview.png'))
        continue
    R, sc, c = V[n]
    render(quads, tex, R, size=900, scale=sc, center=c, bg=(236, 236, 236)).save(os.path.join(out, f'{n}.png'))
    print(n, flush=True)
