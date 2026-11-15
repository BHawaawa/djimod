# -*- coding: utf-8 -*-
"""Render the Mini 4 Pro battery-insertion GIFs / stills from mini4pro.bbmodel (software renderer).

usage: python3 make_m4p_battery_gifs.py <mini4pro.bbmodel> <out_dir> [which]
  which = comma list of: insert_rear,insert_side,insert_bottom,remove,stills  (default: all)
Uses render_anim.Model for keyframe sampling; adds bone rest rotations (the 'drone' bone carries the landed pitch).
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_anim import Model as _Model, render, view, rotmat

SRC, OUT = sys.argv[1], sys.argv[2]
WHICH = sys.argv[3].split(',') if len(sys.argv) > 3 else ['insert_rear', 'insert_side', 'insert_bottom', 'remove', 'stills']
os.makedirs(OUT, exist_ok=True)
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
SIZE, SS, FPS = 640, 2, 20
BG = (236, 236, 236)
P = 'animation.mini4pro.'
INS, REM, IN, EMPTY = P + 'battery_insert', P + 'battery_remove', P + 'battery_installed', P + 'battery_empty'


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
                    if hide:
                        continue
                    for q, uv in self._faces[chd]:
                        quads.append(((np.c_[q, np.ones(4)] @ M.T)[:, :3], uv))
                else:
                    walk(chd, M, hide)
        for n in self.outliner:
            walk(n, np.eye(4), False)
        return quads


m = Model(SRC)


def caption(img, txt):
    if not txt:
        return img
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 22)
    w = d.textlength(txt, font=f)
    d.rounded_rectangle((8, 8, 24 + w, 44), radius=8, fill=(250, 250, 250))
    d.text((16, 11), txt, font=f, fill=(80, 80, 82))
    return img


def frame(anims, R, scale, center, txt=None):
    q = m.pose(anims)
    im = render(q, m.tex, R, size=SIZE * SS, scale=scale * SS, center=center, bg=BG)
    return caption(im.resize((SIZE, SIZE), Image.LANCZOS), txt)


def save_gif(frames, name, hold_last=1.0, hold_first=0.5):
    dur = [int(1000 / FPS)] * len(frames)
    dur[0] = int(hold_first * 1000); dur[-1] = int(hold_last * 1000)
    pal = [f.convert('P', palette=Image.ADAPTIVE, colors=255) for f in frames]
    pal[0].save(os.path.join(OUT, name), save_all=True, append_images=pal[1:], duration=dur, loop=0, optimize=False)
    print('wrote', name, len(frames), 'frames')


def anim_frames(anim, length, R, scale, center, txt):
    n = int(round(length * FPS)) + 1
    return [frame([(anim, i / FPS)], R, scale, center, txt) for i in range(n)]


# camera presets (drone body frame: nose -Z, tail +Z, landed pose)
C_TAIL = (0, 4.5, 9.0)
V_REAR = view(-28, 22)            # rear-quarter, looking at the tail from behind/right/above
V_SIDE = view(-90, 6)             # from +X
V_BOT = rotmat([0, 0, 0]) @ view(-35, -38)

if 'insert_rear' in WHICH:
    save_gif(anim_frames(INS, 1.6, V_REAR, 21, C_TAIL, '插入电池 · battery_insert'), '01_insert_rear_quarter.gif')
if 'insert_side' in WHICH:
    save_gif(anim_frames(INS, 1.6, V_SIDE, 15, (0, 9.0, 12.5), '插入电池 · 侧视'), '02_insert_side.gif')
if 'insert_bottom' in WHICH:
    save_gif(anim_frames(INS, 1.6, V_BOT, 19, (0, 3.0, 8.0), '插入电池 · 底部视角'), '03_insert_bottom.gif')
if 'remove' in WHICH:
    save_gif(anim_frames(REM, 1.4, V_REAR, 21, C_TAIL, '取出电池 · battery_remove'), '04_remove_rear_quarter.gif')
if 'stills' in WHICH:
    for nm, an, R, sc, c in [('still_empty_rear', EMPTY, V_REAR, 26, C_TAIL), ('still_installed_rear', IN, V_REAR, 26, C_TAIL),
                             ('still_empty_bottom', EMPTY, V_BOT, 22, (0, 3, 8)), ('still_installed_bottom', IN, V_BOT, 22, (0, 3, 8)),
                             ('still_empty_back', EMPTY, rotmat([0, 0, 0]) @ view(0, 4), 30, (0, 6.5, 10)),
                             ('still_installed_back', IN, rotmat([0, 0, 0]) @ view(0, 4), 30, (0, 6.5, 10)),
                             ('still_installed_overview', IN, view(180 + 38, 28), 11, (0, 6, 4))]:
        q = m.pose([(an, 0.0)])
        im = render(q, m.tex, R, size=1800, scale=sc * 2, center=c, bg=BG).resize((900, 900), Image.LANCZOS)
        im.save(os.path.join(OUT, nm + '.png'))
    print('stills done')
