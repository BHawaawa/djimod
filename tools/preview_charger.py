# -*- coding: utf-8 -*-
"""Stills + GIFs for the charging-hub block entities (software renderer, Blockbench semantics incl. group rest rotation).

usage: python3 preview_charger.py <model.bbmodel> <out_dir> <job>[,<job>...]
  jobs: stills | insert1 | insert2 | insert3 | remove2 | demo | charging
GIF frames are cached per job in /tmp/charger_frames/<model>/<job> so an interrupted job can be resumed (bash calls are time-capped).
"""
import os, sys, json, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_anim import Model as _Model, render, view, rotmat

SRC, OUT = sys.argv[1], sys.argv[2]
JOBS = sys.argv[3].split(',') if len(sys.argv) > 3 else ['stills']
os.makedirs(OUT, exist_ok=True)
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
BG = (236, 236, 236)
FPS = 20


class Model(_Model):
    def pose(self, anims=(), hidden=()):
        ch = self.channels(anims)
        quads = []

        def walk(node, M, hide):
            name = node['name']
            hide = hide or name in hidden
            o = np.array(node['origin'], float)
            c = ch.get(name, {})
            pos = c.get('position', [0, 0, 0]); rot = c.get('rotation', [0, 0, 0]); scl = c.get('scale', [1, 1, 1])
            rest = np.array(node.get('rotation', [0, 0, 0]), float)
            T = np.eye(4); T[:3, 3] = [-pos[0], pos[1], pos[2]]
            R = rotmat(list(rest + np.array([-rot[0], -rot[1], rot[2]]))) @ np.diag(scl)
            Pm = np.eye(4); Pm[:3, :3] = R; Pm[:3, 3] = o - R @ o
            M = M @ T @ Pm
            if min(abs(s) for s in scl) < 1e-6:
                hide = True
            for chd in node['children']:
                if isinstance(chd, str):
                    if not hide:
                        for q, uv in self._faces[chd]:
                            quads.append(((np.c_[q, np.ones(4)] @ M.T)[:, :3], uv))
                else:
                    walk(chd, M, hide)
        for n in self.outliner:
            walk(n, np.eye(4), False)
        return quads


m = Model(SRC)
NAME = json.load(open(SRC, encoding='utf-8'))['model_identifier']
P = f'animation.{NAME}.'
# glow: brighten texels where the glowmask is lit (GeckoLib AutoGlowingGeoLayer look)
gm = os.path.join(os.path.dirname(SRC), 'textures', f'{NAME}_glowmask.png')
if not os.path.exists(gm):
    gm = os.path.join(os.path.dirname(SRC), f'{NAME}_glowmask.png')
if os.path.exists(gm):
    g = np.asarray(Image.open(gm).convert('RGBA')).astype(float)
    t = m.tex.astype(float).copy()
    a = g[..., 3:4] / 255.0
    t[..., :3] = t[..., :3] * (1 - a) + g[..., :3] * a
    m.tex = np.clip(t, 0, 255).astype(np.uint8)
TOP = max(n[1] for e in m.els.values() for n in (e['to'],))
CEN = (0, TOP * 0.5, 0)


def caption(img, txt):
    if not txt:
        return img
    d = ImageDraw.Draw(img); f = ImageFont.truetype(FONT, 22)
    w = d.textlength(txt, font=f)
    d.rounded_rectangle((8, 8, 24 + w, 44), radius=8, fill=(250, 250, 250))
    d.text((16, 11), txt, font=f, fill=(80, 80, 82))
    return img


def shot(anims, R, scale, center=None, size=640, ss=2, txt=None):
    q = m.pose(anims)
    im = render(q, m.tex, R, size=size * ss, scale=scale * ss, center=center or CEN, bg=BG)
    return caption(im.resize((size, size), Image.LANCZOS), txt)


def gif(job, anim, length, R, scale, txt, center=None, extra=()):
    fd = os.path.join('/tmp/charger_frames', NAME, job); os.makedirs(fd, exist_ok=True)
    n = int(round(length * FPS)) + 1
    for i in range(n):
        fp = os.path.join(fd, f'{i:04d}.png')
        if os.path.exists(fp):
            continue
        shot(list(extra) + [(anim, i / FPS)], R, scale, center or GC, txt=txt, size=560).save(fp)
    frames = [Image.open(os.path.join(fd, f'{i:04d}.png')) for i in range(n)]
    dur = [int(1000 / FPS)] * n; dur[0] = 500; dur[-1] = 1200
    pal = [f.convert('P', palette=Image.ADAPTIVE, colors=255) for f in frames]
    pal[0].save(os.path.join(OUT, f'{job}.gif'), save_all=True, append_images=pal[1:], duration=dur, loop=0)
    print('wrote', job, n)


V_FQ = view(180 + 35, 24)          # front-right quarter, from above  (front = -Z)
V_FQL = view(180 - 35, 24)
V_FRONT = view(180, 6)
V_TOP = view(180, 72)
V_SIDE = view(-90, 8)
V_BACK = view(0, 10)
V_BOT = view(180 + 20, -70)
ALL_IN = [(f'{P}slot{k}_installed', 0) for k in (1, 2, 3)]
ALL_EMPTY = [(f'{P}slot{k}_empty', 0) for k in (1, 2, 3)]
EMPTY_EXCEPT = lambda s: [(f'{P}slot{k}_empty', 0) for k in (1, 2, 3) if k != s]
INSTALLED_EXCEPT = lambda s: [(f'{P}slot{k}_installed', 0) for k in (1, 2, 3) if k != s]
S = 640 / (TOP * 1.2)
SPAN = TOP + 14.0                 # hub + battery lifted for insertion
GC = (0, SPAN / 2 - 0.5, 0)
SG = 560 * 0.78 / SPAN

for job in JOBS:
    if job == 'stills':
        for nm, an, R, sc in [('overview_installed', ALL_IN, V_FQ, S), ('overview_empty', ALL_EMPTY, V_FQ, S),
                              ('front_installed', ALL_IN, V_FRONT, S * 1.05), ('front_empty', ALL_EMPTY, V_FRONT, S * 1.05),
                              ('top_installed', ALL_IN, V_TOP, S * 1.1), ('top_empty', ALL_EMPTY, V_TOP, S * 1.1),
                              ('side_installed', ALL_IN, V_SIDE, S * 1.05), ('back_installed', ALL_IN, V_BACK, S * 1.05),
                              ('left_quarter_empty', ALL_EMPTY, V_FQL, S), ('bottom_label', ALL_IN, V_BOT, S * 1.05)]:
            shot(an, R, sc, size=900).save(os.path.join(OUT, nm + '.png'))
        print('stills done')
    elif job.startswith('insert') or job.startswith('remove') or job == 'demo':
        pass
    if job.startswith('insert'):
        k = int(job[-1])
        gif(f'0{k}_slot{k}_insert', f'{P}slot{k}_insert', 1.6, V_FQ, SG, f'槽位{k} 插入电池 · slot{k}_insert',
            extra=EMPTY_EXCEPT(k) if k == 1 else ([(f'{P}slot{j}_installed', 0) for j in range(1, k)] +
                                                  [(f'{P}slot{j}_empty', 0) for j in range(k + 1, 4)]))
    elif job.startswith('remove'):
        k = int(job[-1])
        gif(f'04_slot{k}_remove', f'{P}slot{k}_remove', 1.3, V_FQ, SG, f'槽位{k} 取出电池 · slot{k}_remove',
            extra=INSTALLED_EXCEPT(k))
    elif job == 'charging':
        if 'mini' in NAME:      # LEDs on the hub front, bottom edge
            cv, cc, cs = view(180 + 14, 22), (0, 6.5, -1.0), S * 0.62
        else:                   # LEDs on the battery caps
            cv, cc, cs = view(180 + 25, 48), (0, TOP * 0.95, 0), S * 1.5
        gif('05_charging', f'{P}slot2_charging', 2.0, cv, cs, '充电中 LED 流水 · slotN_charging',
            extra=[(f'{P}slot1_installed', 0), (f'{P}slot3_installed', 0)], center=cc)
    elif job == 'demo':
        fd = os.path.join('/tmp/charger_frames', NAME, 'demo')
        FPS_D = 8
        os.makedirs(fd, exist_ok=True)
        n = int(12.0 * FPS_D)
        for i in range(n):
            fp = os.path.join(fd, f'{i:04d}.png')
            if not os.path.exists(fp):
                shot([(f'{P}demo_cycle', i / FPS_D)], V_FQ, SG * 520 / 560, txt='演示循环 · demo_cycle', size=520, center=GC).save(fp)
        frames = [Image.open(os.path.join(fd, f'{i:04d}.png')) for i in range(n)]
        pal = [f.convert('P', palette=Image.ADAPTIVE, colors=255) for f in frames]
        pal[0].save(os.path.join(OUT, '06_demo_cycle.gif'), save_all=True, append_images=pal[1:], duration=100, loop=0)
        print('wrote demo', n)
