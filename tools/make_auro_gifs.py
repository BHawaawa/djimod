# -*- coding: utf-8 -*-
"""GIFs / stills of the AURO 2000 ELITE cable animations (software renderer).

usage: python3 make_auro_gifs.py <auro2000elite.bbmodel> <out_dir> <which>
  which = comma list of: extract, retract, plug, unplug, cycle, stills
A Mini 4 Pro battery (model/battery/mini4pro) is placed at the cable's device spot as the charged device.
"""
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_anim import Model, render, view, rotmat
import auro_cable as CAB

SRC, OUT = sys.argv[1], sys.argv[2]
WHICH = sys.argv[3].split(',') if len(sys.argv) > 3 else ['extract']
os.makedirs(OUT, exist_ok=True)
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
SIZE, SS, FPS = 640, 2, 20
BG = (236, 236, 236)
A = 'animation.auro2000elite.'
HERE = os.path.dirname(os.path.abspath(__file__))

m = Model(SRC)
bat = Model(os.path.join(HERE, '..', 'model', 'battery', 'mini4pro', 'mini4pro_battery.bbmodel'))
H1, W1 = m.tex.shape[:2]; H2, W2 = bat.tex.shape[:2]
Wc, Hc = max(W1, W2), H1 + H2
TEX = np.zeros((Hc, Wc, 4), np.uint8); TEX[:H1, :W1] = m.tex; TEX[H1:, :W2] = bat.tex
# battery: 1 px = 3 mm -> station px (4 mm) = 0.75; connector end (+Z) faces the station; label (+Y) up
BQ = bat.pose([])
_all = np.concatenate([q for q, _ in BQ])
bmin, bmax = _all.min(0), _all.max(0)
bc = (bmin + bmax) / 2


def battery_quads():
    out = []
    S = 0.75
    off = np.array(CAB.DEVICE) + np.array([0, -bmin[1] * S + 0.02, 0]) - np.array([bc[0], 0, bc[2]]) * S
    for q, uv in BQ:
        out.append((q * S + off, np.c_[uv[:, 0] * W2 / Wc, (H1 + uv[:, 1] * H2) / Hc]))
    return out


BAT = battery_quads()


def station(anims, device=False):
    q = [(qq, np.c_[uv[:, 0] * W1 / Wc, uv[:, 1] * H1 / Hc]) for qq, uv in m.pose(anims)]
    return q + (BAT if device else [])


def caption(img, txt):
    if not txt:
        return img
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 22)
    w = d.textlength(txt, font=f)
    d.rounded_rectangle((8, 8, 24 + w, 44), radius=8, fill=(250, 250, 250))
    d.text((16, 11), txt, font=f, fill=(80, 80, 82))
    return img


def frame(anims, R, scale, center, txt=None, device=False):
    im = render(station(anims, device), TEX, R, size=SIZE * SS, scale=scale * SS, center=center, bg=BG, cull=True)
    return caption(im.resize((SIZE, SIZE), Image.LANCZOS), txt)


def save_gif(frames, name, hold_last=0.8, hold_first=0.5):
    dur = [int(1000 / FPS)] * len(frames)
    dur[0] = int(hold_first * 1000); dur[-1] = int(hold_last * 1000)
    pal = [f.convert('P', palette=Image.ADAPTIVE, colors=255) for f in frames]
    pal[0].save(os.path.join(OUT, name), save_all=True, append_images=pal[1:], duration=dur, loop=0, optimize=False)
    print('wrote', name, len(frames), 'frames')


def anim_frames(anim, length, R, scale, center, txt, device=False):
    n = int(round(length * FPS)) + 1
    return [frame([(A + anim, i / FPS)], R, scale, center, txt, device) for i in range(n)]


V_MAIN = view(180 + 34, 20)
V_LOW = view(180 + 80, 6)


def framing(R, fill=0.9):
    """centre / scale so that the station + plugged device + laid-out cable fill the frame."""
    pts = []
    for an, dev in (('cable_plugged', True), ('cable_out', False), ('cable_stowed', False)):
        pts += [q for q, _ in station([(A + an, 0.0)], dev)]
    P = np.concatenate(pts)
    S = P @ np.asarray(R).T
    lo, hi = S.min(0), S.max(0)
    mid = (lo + hi) / 2
    c = np.asarray(R).T @ np.array([mid[0], mid[1], 0.0])
    sc = fill * SIZE / max(hi[0] - lo[0], hi[1] - lo[1])
    return tuple(c), sc


C_MAIN, S_MAIN = framing(V_MAIN)

if 'extract' in WHICH:
    save_gif(anim_frames('cable_extract', 1.6, V_MAIN, S_MAIN, C_MAIN, 'Shift+右键 · 取出 140W 伸缩线 · cable_extract'),
             '01_cable_extract.gif')
if 'plug' in WHICH:
    save_gif(anim_frames('cable_plug', 1.1, V_MAIN, S_MAIN, C_MAIN, '手持设备右键 · 插入充电 · cable_plug', True),
             '02_cable_plug.gif')
if 'unplug' in WHICH:
    save_gif(anim_frames('cable_unplug', 0.95, V_MAIN, S_MAIN, C_MAIN, '空手右键 · 拔出设备 · cable_unplug', True),
             '03_cable_unplug.gif')
if 'retract' in WHICH:
    save_gif(anim_frames('cable_retract', 1.35, V_MAIN, S_MAIN, C_MAIN, 'Shift+右键 · 收回伸缩线 · cable_retract'),
             '04_cable_retract.gif')
if 'extract_side' in WHICH:
    save_gif(anim_frames('cable_extract', 1.6, V_LOW, S_MAIN, (0, 26, -42), '取出伸缩线 · 侧视'), '05_cable_extract_side.gif')
if 'stills' in WHICH:
    os.makedirs(os.path.join(OUT, 'stills'), exist_ok=True)
    for nm, an, R, sc, c, dev in [('stowed', 'cable_stowed', V_MAIN, S_MAIN, C_MAIN, False),
                                  ('out', 'cable_out', V_MAIN, S_MAIN, C_MAIN, False),
                                  ('plugged', 'cable_plugged', V_MAIN, S_MAIN, C_MAIN, True),
                                  ('plugged_side', 'cable_plugged', V_LOW, S_MAIN, (0, 26, -42), True),
                                  ('plugged_closeup', 'cable_plugged', view(180 + 40, 25), 16, (-12, 4, -74), True)]:
        im = render(station([(A + an, 0.0)], dev), TEX, R, size=1800, scale=sc * 2, center=c, bg=BG, cull=True)
        im.resize((900, 900), Image.LANCZOS).save(os.path.join(OUT, 'stills', nm + '.png'))
    print('stills done')
