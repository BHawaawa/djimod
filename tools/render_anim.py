# -*- coding: utf-8 -*-
"""Animated software preview for GeckoLib .bbmodel files (Blockbench semantics).

  load(path)                         -> Model
  model.pose(anims=[(name, t), ...], hidden={'battery'}) -> quads list for render.render()
  sample(anim, bone, channel, t)     -> keyframe value with linear / catmullrom interpolation (Bedrock values)

Bedrock -> Blockbench conversion used here (same as GeckoLib): position (-x, y, z), rotation (-x, -y, z).
Bone transform = translate(position) * pivot * R * S * pivot^-1, applied parent-first.
"""
import json, math, io, base64
import numpy as np
from PIL import Image
from render import rotmat, face_quad, render, view   # noqa: F401  (re-exported for callers)


class Model:
    def __init__(self, path):
        m = json.load(open(path, encoding='utf-8'))
        src = m['textures'][0]['source'].split(',', 1)[1]
        self.tex = np.asarray(Image.open(io.BytesIO(base64.b64decode(src))).convert('RGBA'))
        self.TW, self.TH = m['resolution']['width'], m['resolution']['height']
        self.els = {e['uuid']: e for e in m['elements']}
        self.outliner = m['outliner']
        self.anims = {}
        for a in m.get('animations', []):
            bones = {}
            for an in a['animators'].values():
                ch = {}
                for kf in an['keyframes']:
                    dp = kf['data_points'][0]
                    ch.setdefault(kf['channel'], []).append(
                        (float(kf['time']), [float(dp['x']), float(dp['y']), float(dp['z'])], kf.get('interpolation', 'linear')))
                for k in ch:
                    ch[k].sort(key=lambda e: e[0])
                bones[an['name']] = ch
            self.anims[a['name']] = dict(length=a['length'], loop=a['loop'], bones=bones)
        # pre-extract local face quads per element
        self._faces = {}
        for uid, e in self.els.items():
            R = rotmat(e.get('rotation', [0, 0, 0])); oo = np.array(e['origin'], float)
            lst = []
            for face, fd in e['faces'].items():
                if fd.get('texture') is None:
                    continue
                q = np.array(face_quad(e['from'], e['to'], face), float)
                q = (q - oo) @ R.T + oo
                u1, v1, u2, v2 = fd['uv']
                uv = np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)], float) / [self.TW, self.TH]
                lst.append((q, uv))
            self._faces[uid] = lst

    def channels(self, anims):
        """anims: list of (name, time). later entries override earlier ones per bone/channel."""
        out = {}
        for name, t in anims:
            a = self.anims[name]
            if a['loop'] == 'loop' and a['length'] > 0:
                t = t % a['length']
            for bn, chans in a['bones'].items():
                for ch, keys in chans.items():
                    out.setdefault(bn, {})[ch] = interp(keys, t)
        return out

    def pose(self, anims=(), hidden=(), overrides=None):
        ch = self.channels(anims)
        if overrides:
            for bn, d in overrides.items():
                ch.setdefault(bn, {}).update(d)
        quads = []

        def walk(node, M, hide):
            name = node['name']
            hide = hide or name in hidden
            o = np.array(node['origin'], float)
            c = ch.get(name, {})
            pos = c.get('position', [0, 0, 0]); rot = c.get('rotation', [0, 0, 0]); scl = c.get('scale', [1, 1, 1])
            T = np.eye(4)
            T[:3, 3] = [-pos[0], pos[1], pos[2]]
            rest = node.get('rotation', [0, 0, 0])          # bone rest rotation (GeckoLib: initial + animation, Euler add)
            R = rotmat([rest[0] - rot[0], rest[1] - rot[1], rest[2] + rot[2]]) @ np.diag(scl)
            P = np.eye(4); P[:3, :3] = R; P[:3, 3] = o - R @ o
            M = M @ T @ P
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


def interp(keys, t):
    if t <= keys[0][0]:
        return list(keys[0][1])
    if t >= keys[-1][0]:
        return list(keys[-1][1])
    for i in range(len(keys) - 1):
        t0, v0, _ = keys[i]; t1, v1, ip = keys[i + 1]
        if t0 <= t <= t1:
            a = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            v0 = np.array(v0, float); v1 = np.array(v1, float)
            if ip == 'catmullrom' or keys[i][2] == 'catmullrom':
                vp = np.array(keys[i - 1][1], float) if i > 0 else v0
                vn = np.array(keys[i + 2][1], float) if i + 2 < len(keys) else v1
                a2, a3 = a * a, a * a * a
                v = 0.5 * ((2 * v0) + (-vp + v1) * a + (2 * vp - 5 * v0 + 4 * v1 - vn) * a2 + (-vp + 3 * v0 - 3 * v1 + vn) * a3)
            else:
                v = v0 + (v1 - v0) * a
            return list(v)
    return list(keys[-1][1])
