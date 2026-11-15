# -*- coding: utf-8 -*-
"""Render the Mini 4 Pro flight / gimbal animation GIFs from mini4pro.bbmodel (software renderer).

usage: python3 make_m4p_flight_gifs.py <mini4pro.bbmodel> <out_dir> [which]
  which = comma list of: fly,takeoff,gimbal,gimbal_fwd  (default: all)
Uses render_anim.Model for keyframe sampling; adds bone rest rotations (the 'drone' bone carries the landed pitch).
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_anim import Model as _Model, render, view, rotmat

SRC, OUT = sys.argv[1], sys.argv[2]
WHICH = sys.argv[3].split(',') if len(sys.argv) > 3 else ['fly', 'takeoff', 'gimbal', 'gimbal_fwd']
os.makedirs(OUT, exist_ok=True)
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
SIZE, SS, FPS = 640, 1.5, 20
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
    im = render(q, m.tex, R, size=int(SIZE * SS), scale=scale * SS, center=center, bg=BG)
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


IN = P + 'battery_installed'
V_Q = view(180 + 35, 24)
C0 = (0, 7, 5)


def frames_multi(anims_at, n, R, scale, center, txt):
    return [frame(anims_at(i / FPS), R, scale, center, txt) for i in range(n)]


if 'fly' in WHICH:      # 1 s loop: level flight attitude, hover bob, props spinning
    save_gif(frames_multi(lambda t: [(IN, 0), (P + 'fly', t)], 20, V_Q, 10, C0, '飞行 · animation.mini4pro.fly'),
             '01_fly_loop.gif', hold_last=0.05, hold_first=0.05)
if 'takeoff' in WHICH:  # landed nose-up pose -> level (0.6 s), then fly loop
    save_gif(frames_multi(lambda t: [(IN, 0), (P + 'takeoff', t)] + ([(P + 'fly', t - 0.6)] if t > 0.6 else []), 24,
                          view(-90, 6), 11, (0, 7, 3), '起飞 · animation.mini4pro.takeoff'), '02_takeoff_side.gif')
if 'gimbal' in WHICH:   # gimbal down (0.8 s) then forward again (0.8 s)
    save_gif(frames_multi(lambda t: [(IN, 0), (P + 'gimbal_down', t)], 17, view(180 + 40, 8), 30, (0, 9, -7),
                          '云台俯拍 · gimbal_down'), '03_gimbal_down.gif')
if 'gimbal_fwd' in WHICH:
    save_gif(frames_multi(lambda t: [(IN, 0), (P + 'gimbal_down', 0.8), (P + 'gimbal_forward', t)], 17, view(180 + 40, 8), 30,
                          (0, 9, -7), '云台回正 · gimbal_forward'), '04_gimbal_forward.gif')
