"""Bake the DJI RC 2 front-face GUI assets from model/RC2.

Outputs (assets/djimod/textures/gui):
  rc2_front.png         1440x1332 RGBA front face, sticks removed, transparent
  rc2_front_shadow.png  1440x1332 soft drop shadow
  rc2_stick.png         256x256 joystick knob
  ui_icons.png          512x512 icon atlas (8x8 cells of 64px)
"""
import copy
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, 'model', 'RC2', 'geo', 'rc2.geo.json')
TEX = os.path.join(ROOT, 'model', 'RC2', 'textures', 'rc2.png')
OUT = os.path.join(ROOT, 'fabric_mod', 'src', 'main', 'resources', 'assets', 'djimod', 'textures', 'gui')

# Front orthographic projection bounds: x in [-12, 12], y in [-11, 11.2].
W, H = 1440, 1332
SCALE = 60.0
CX, CY = 0.0, 0.1
SS = 2  # supersample factor


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def face_quad(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def load_geo(path, texpath, skip_bones=()):
    g = json.load(open(path, encoding='utf-8'))['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    tex = np.asarray(Image.open(texpath).convert('RGBA'))
    quads = []
    bmap = {b['name']: b for b in g['bones']}
    mats = {}

    def bmat(name):
        if name in mats:
            return mats[name]
        b = bmap[name]
        m = bmat(b['parent']) if b.get('parent') else np.eye(4)
        r = b.get('rotation')
        if r:
            pv = np.array([-b['pivot'][0], b['pivot'][1], b['pivot'][2]], float)
            rr = rotmat([-r[0], -r[1], r[2]])
            t = np.eye(4)
            t[:3, :3] = rr
            t[:3, 3] = pv - rr @ pv
            m = m @ t
        mats[name] = m
        return m

    for b in g['bones']:
        if b['name'] in skip_bones:
            continue
        bm = bmat(b['name'])
        for c in b.get('cubes', []):
            size = np.array(c['size'], float)
            o = np.array(c['origin'], float)
            frm = np.array([-(o[0] + size[0]), o[1], o[2]])
            to = frm + size
            rot = c.get('rotation', [0, 0, 0])
            rot = [-rot[0], -rot[1], rot[2]]
            pv = c.get('pivot', [0, 0, 0])
            pv = np.array([-pv[0], pv[1], pv[2]], float)
            rr = rotmat(rot)
            for face, fd in c['uv'].items():
                q = np.array(face_quad(frm, to, face), float)
                q = (q - pv) @ rr.T + pv
                q = (np.c_[q, np.ones(4)] @ bm.T)[:, :3]
                (u, v), (w, h) = fd['uv'], fd['uv_size']
                if face in ('up', 'down'):
                    u, v, w, h = u + w, v + h, -w, -h
                uv = np.array([(u, v), (u + w, v), (u + w, v + h), (u, v + h)], float) / [tw, th]
                quads.append((q, uv))
    return quads, tex


L0 = np.array([0.2, 1.0, -0.7])
L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7])
L1 /= np.linalg.norm(L1)


def render_front_rgba(quads, tex):
    w, h = W * SS, H * SS
    scale = SCALE * SS
    view = rotmat([0, 180, 0])
    center = np.array([CX, CY, 0.0])
    img = np.zeros((h, w, 3), np.float32)
    alpha = np.zeros((h, w), np.float32)
    zb = np.full((h, w), -1e9)
    th, tw = tex.shape[:2]
    for q, uv in quads:
        n = np.cross(q[1] - q[0], q[3] - q[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        light = min(1.0, 0.4 + 0.6 * (max(0, n @ L0) + max(0, n @ L1)))
        light_b = min(1.0, 0.4 + 0.6 * (max(0, -n @ L0) + max(0, -n @ L1)))
        p = (q - center) @ view.T
        sxy = np.c_[w / 2 + p[:, 0] * scale, h / 2 - p[:, 1] * scale]
        zz = p[:, 2]
        facing = (np.cross(np.r_[sxy[1] - sxy[0], 0], np.r_[sxy[3] - sxy[0], 0])[2]) < 0
        lt = light if facing else light_b
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = sxy[list(tri)]
            Z = zz[list(tri)]
            UV = uv[list(tri)]
            x0, y0 = np.floor(P.min(0)).astype(int)
            x1, y1 = np.ceil(P.max(0)).astype(int)
            x0, y0 = max(x0, 0), max(y0, 0)
            x1, y1 = min(x1, w - 1), min(y1, h - 1)
            if x1 < x0 or y1 < y0:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = ((P[1, 1] - P[2, 1]) * (P[0, 0] - P[2, 0])
                 + (P[2, 0] - P[1, 0]) * (P[0, 1] - P[2, 1]))
            if abs(d) < 1e-9:
                continue
            a = ((P[1, 1] - P[2, 1]) * (xs - P[2, 0])
                 + (P[2, 0] - P[1, 0]) * (ys - P[2, 1])) / d
            b = ((P[2, 1] - P[0, 1]) * (xs - P[2, 0])
                 + (P[0, 0] - P[2, 0]) * (ys - P[2, 1])) / d
            c = 1 - a - b
            inside = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
            if not inside.any():
                continue
            z = a * Z[0] + b * Z[1] + c * Z[2]
            u = a * UV[0, 0] + b * UV[1, 0] + c * UV[2, 0]
            v = a * UV[0, 1] + b * UV[1, 1] + c * UV[2, 1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            texel = tex[ty, tx]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (z > sub) & (texel[..., 3] > 25)
            sub[ok] = z[ok]
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok] = texel[ok][:, :3] * lt
            aregion = alpha[y0:y1 + 1, x0:x1 + 1]
            aregion[ok] = texel[ok][:, 3].astype(np.float32) / 255.0
    # Premultiplied downsample to avoid dark fringes.
    premul = np.dstack([img * alpha[..., None], alpha * 255.0])
    small = Image.fromarray((np.clip(premul, 0, 255)).astype(np.uint8), 'RGBA')
    small = small.resize((W, H), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float32) / 255.0
    a = arr[..., 3]
    rgb = np.zeros_like(arr[..., :3])
    nz = a > 1e-5
    rgb[nz] = arr[..., :3][nz] / a[nz][..., None]
    out = np.dstack([np.clip(rgb, 0, 1) * 255, np.clip(a, 0, 1) * 255]).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def make_shadow(front):
    """Soft transparent drop shadow texture."""
    alpha = front.getchannel('A').filter(ImageFilter.GaussianBlur(22))
    # Mildly dilate by boosting alpha around the body.
    alpha = alpha.point(lambda v: int(min(255, v * 1.05)))
    shadow = Image.new('RGBA', front.size, (0, 0, 0, 0))
    shadow.putalpha(alpha.point(lambda v: int(v * 0.62)))
    return shadow


def make_knob():
    N = 256
    S = 4
    size = N * S
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = size / 2

    def circle(r, fill=None, outline=None, width=1):
        rr = r * S
        d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=fill, outline=outline, width=width)

    # Outer rings
    circle(124, fill=(18, 21, 24, 255))
    circle(112, fill=(42, 47, 53, 255))
    circle(101, fill=(28, 32, 36, 255))

    # Checker grip on the top face, clipped to a circle.
    grip = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grip)
    cell = 14 * S
    colors = [(206, 210, 214, 255), (156, 162, 168, 255)]
    r = 96 * S
    x0, y0 = c - r, c - r
    for iy in range(int(2 * r // cell) + 2):
        for ix in range(int(2 * r // cell) + 2):
            col = colors[(ix + iy) % 2]
            px = x0 + ix * cell
            py = y0 + iy * cell
            gd.rectangle([px, py, px + cell, py + cell], fill=col)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([c - r, c - r, c + r, c + r], fill=255)
    grip.putalpha(mask)
    im.alpha_composite(grip)

    # Radial shading + rim + center
    shade = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for i in range(40):
        rr = int((96 - i * 96 / 40) * S)
        a = int(8 + i * 2.2)
        sd.ellipse([c - rr, c - rr, c + rr, c + rr], fill=(0, 0, 0, a))
    im.alpha_composite(shade)
    circle(98, outline=(70, 76, 82, 255), width=2 * S)
    circle(28, fill=(58, 63, 68, 255))
    circle(19, fill=(34, 38, 42, 255))
    # Highlight
    hr = 86 * S
    d.arc([c - hr, c - hr, c + hr, c + hr], 200, 320, fill=(255, 255, 255, 70), width=4 * S)
    return im.resize((N, N), Image.LANCZOS)


WHITE = (255, 255, 255, 255)


def icon_line(d, pts, width, fill=WHITE):
    d.line(pts, fill=fill, width=width, joint='curve')
    for p in (pts[0], pts[-1]):
        d.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=fill)


def make_icons():
    N = 128
    SSI = 4
    CELL = N * SSI
    cols = rows = 8
    atlas = Image.new('RGBA', (cols * N, rows * N), (0, 0, 0, 0))

    U = CELL // 256
    state = {'d': None}

    def L(pts, w=18, fill=WHITE):
        icon_line(state['d'], [(x * U, y * U) for x, y in pts], int(w * U), fill)

    def C(cx, cy, r, w=18, fill=WHITE):
        d = state['d']
        d.ellipse([(cx - r) * U, (cy - r) * U, (cx + r) * U, (cy + r) * U],
                  outline=fill if w else None, width=int(w * U) if w else 1,
                  fill=fill if not w else None)

    def draw_icon(index, func):
        im = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
        state['d'] = ImageDraw.Draw(im)
        func(state['d'])
        im = im.resize((N, N), Image.LANCZOS)
        x = (index % cols) * N
        y = (index // cols) * N
        atlas.alpha_composite(im, (x, y))

    def home(d):
        L([(128, 38), (42, 112), (58, 112), (58, 216), (198, 216), (198, 112), (214, 112), (128, 38)], 18)
        L([(104, 216), (104, 152), (152, 152), (152, 216)], 16)

    def more(d):
        for x in (76, 128, 180):
            d.ellipse([(x - 15) * U, (128 - 15) * U, (x + 15) * U, (128 + 15) * U], fill=WHITE)

    def satellite(d):
        # body
        d.rounded_rectangle([96 * U, 100 * U, 160 * U, 160 * U], radius=10 * U, outline=WHITE, width=12 * U)
        # panels
        L([(44, 118), (92, 118)], 14)
        L([(164, 118), (212, 118)], 14)
        L([(44, 118), (44, 148)], 14)
        L([(212, 118), (212, 148)], 14)
        # dish
        d.arc([88 * U, 34 * U, 168 * U, 112 * U], 200, 340, fill=WHITE, width=13 * U)
        L([(128, 78), (128, 100)], 13)

    def obstacle(d):
        C(128, 128, 82, 16)
        L([(70, 186), (186, 70)], 16)
        L([(128, 30), (128, 58)], 12)

    def arrow_up(d):
        L([(128, 208), (128, 48)], 20)
        L([(80, 96), (128, 48), (176, 96)], 20)

    def arrow_down(d):
        L([(128, 48), (128, 208)], 20)
        L([(80, 160), (128, 208), (176, 160)], 20)

    def camera(d):
        d.rounded_rectangle([36 * U, 82 * U, 220 * U, 198 * U], radius=18 * U, outline=WHITE, width=16 * U)
        d.ellipse([86 * U, 98 * U, 170 * U, 182 * U], outline=WHITE, width=14 * U)
        L([(78, 82), (98, 52), (158, 52), (178, 82)], 14)

    def record(d):
        d.ellipse([48 * U, 48 * U, 208 * U, 208 * U], outline=WHITE, width=16 * U)
        d.ellipse([82 * U, 82 * U, 174 * U, 174 * U], fill=WHITE)

    def playback(d):
        d.rounded_rectangle([36 * U, 48 * U, 220 * U, 208 * U], radius=16 * U, outline=WHITE, width=15 * U)
        d.polygon([(108 * U, 92 * U), (108 * U, 164 * U), (172 * U, 128 * U)], fill=WHITE)

    def focus(d):
        for (x, y, sx, sy) in [(52, 52, 1, 1), (204, 52, -1, 1), (52, 204, 1, -1), (204, 204, -1, -1)]:
            L([(x, y + sy * 44), (x, y), (x + sx * 44, y)], 16)
        d.ellipse([116 * U, 116 * U, 140 * U, 140 * U], fill=WHITE)

    def takeoff(d):
        L([(128, 196), (128, 54)], 18)
        L([(84, 98), (128, 54), (172, 98)], 18)
        L([(48, 220), (208, 220)], 18)

    def land(d):
        L([(128, 50), (128, 192)], 18)
        L([(84, 148), (128, 192), (172, 148)], 18)
        L([(48, 220), (208, 220)], 18)

    def rth(d):
        L([(128, 56), (56, 118), (70, 118), (70, 204), (186, 204), (186, 118), (200, 118), (128, 56)], 16)
        d.arc([58 * U, 62 * U, 198 * U, 202 * U], 25, 155, fill=WHITE, width=13 * U)
        d.polygon([(190 * U, 154 * U), (210 * U, 128 * U), (172 * U, 122 * U)], fill=WHITE)

    def waypoint(d):
        L([(60, 190), (104, 96), (166, 164), (206, 64)], 14)
        for x, y in [(60, 190), (104, 96), (166, 164), (206, 64)]:
            d.ellipse([(x - 12) * U, (y - 12) * U, (x + 12) * U, (y + 12) * U], fill=WHITE)

    def power(d):
        d.arc([56 * U, 60 * U, 200 * U, 204 * U], 290, 250, fill=WHITE, width=18 * U)
        L([(128, 40), (128, 128)], 18)

    def map_icon(d):
        L([(36, 64), (92, 44), (160, 66), (220, 44), (220, 192), (160, 212), (92, 190), (36, 212), (36, 64)], 14)
        L([(92, 44), (92, 190)], 10)
        L([(160, 66), (160, 212)], 10)

    def grid(d):
        for x in (48, 128, 208):
            for y in (48, 128, 208):
                d.rounded_rectangle([(x - 24) * U, (y - 24) * U, (x + 24) * U, (y + 24) * U],
                                    radius=6 * U, outline=WHITE, width=10 * U)

    def gear(d):
        import math as m
        cx = cy = 128
        for i in range(8):
            ang = i * m.pi / 4
            x = cx + m.cos(ang) * 88
            y = cy + m.sin(ang) * 88
            d.rounded_rectangle([(x - 18) * U, (y - 18) * U, (x + 18) * U, (y + 18) * U],
                                radius=6 * U, fill=WHITE)
        C(cx, cy, 66, 28)
        C(cx, cy, 22, 0, 255)

    def check(d):
        L([(54, 132), (106, 184), (202, 76)], 22)

    def close(d):
        L([(64, 64), (192, 192)], 20)
        L([(192, 64), (64, 192)], 20)

    def back(d):
        L([(170, 54), (90, 128), (170, 202)], 22)

    def sun(d):
        C(128, 128, 46, 16)
        import math as m
        for i in range(8):
            a = i * m.pi / 4
            L([(128 + m.cos(a) * 66, 128 + m.sin(a) * 66), (128 + m.cos(a) * 96, 128 + m.sin(a) * 96)], 12)

    def timer(d):
        C(128, 138, 78, 16)
        L([(128, 138), (128, 88)], 14)
        L([(128, 138), (168, 164)], 14)
        L([(96, 40), (160, 40)], 16)

    def burst(d):
        for i, off in enumerate((0, 18, 36)):
            d.rounded_rectangle([(42 + off) * U, (54 + off) * U, (178 + off) * U, (190 + off) * U],
                                radius=12 * U, outline=WHITE, width=12 * U)

    def pano(d):
        d.rounded_rectangle([30 * U, 88 * U, 226 * U, 178 * U], radius=14 * U, outline=WHITE, width=13 * U)
        d.polygon([(60 * U, 160 * U), (104 * U, 108 * U), (140 * U, 160 * U)], fill=WHITE)

    def night(d):
        d.ellipse([58 * U, 48 * U, 198 * U, 208 * U], fill=WHITE)
        d.ellipse([94 * U, 36 * U, 218 * U, 188 * U], fill=(0, 0, 0, 0))
        # Punch transparency with a mask instead of transparent fill.
        mask = Image.new('L', (CELL, CELL), 255 * 0)
        # drawn in separate pass below

    def _night(d):
        pass

    def moon(d):
        d.ellipse([56 * U, 40 * U, 200 * U, 216 * U], fill=WHITE)
        # erase using a black ellipse is not possible with alpha; use a separate mask outside.
        d.ellipse([96 * U, 22 * U, 232 * U, 196 * U], fill=0)

    def slowmo(d):
        C(128, 138, 74, 15)
        L([(128, 138), (128, 92)], 13)
        L([(128, 138), (162, 160)], 13)
        d.arc([56 * U, 52 * U, 200 * U, 196 * U], 200, 300, fill=WHITE, width=12 * U)

    def master(d):
        import math as m
        pts = []
        for i in range(10):
            ang = -m.pi / 2 + i * m.pi / 5
            r = 90 if i % 2 == 0 else 38
            pts.append((128 + m.cos(ang) * r, 128 + m.sin(ang) * r))
        d.polygon([(x * U, y * U) for x, y in pts], fill=WHITE)

    def photo(d):
        camera(d)

    def video(d):
        d.rounded_rectangle([36 * U, 84 * U, 172 * U, 190 * U], radius=16 * U, outline=WHITE, width=14 * U)
        d.polygon([(180 * U, 112 * U), (224 * U, 82 * U), (224 * U, 192 * U), (180 * U, 164 * U)], fill=WHITE)

    def compass(d):
        C(128, 128, 88, 15)
        d.polygon([(128 * U, 54 * U), (150 * U, 128 * U), (128 * U, 202 * U), (106 * U, 128 * U)], outline=WHITE, width=10 * U)
        d.polygon([(128 * U, 54 * U), (150 * U, 128 * U), (106 * U, 128 * U)], fill=WHITE)

    def warning(d):
        L([(128, 40), (224, 210), (32, 210), (128, 40)], 15)
        L([(128, 96), (128, 154)], 16)
        d.ellipse([118 * U, 174 * U, 138 * U, 194 * U], fill=WHITE)

    def info(d):
        C(128, 128, 88, 15)
        L([(128, 112), (128, 184)], 16)
        d.ellipse([118 * U, 68 * U, 138 * U, 88 * U], fill=WHITE)

    def folder(d):
        L([(36, 190), (36, 70), (96, 70), (116, 92), (220, 92), (220, 190), (36, 190)], 15)

    def play(d):
        d.polygon([(88 * U, 52 * U), (88 * U, 204 * U), (204 * U, 128 * U)], fill=WHITE)

    def plus(d):
        L([(128, 56), (128, 200)], 20)
        L([(56, 128), (200, 128)], 20)

    def minus(d):
        L([(56, 128), (200, 128)], 20)

    def lock(d):
        d.rounded_rectangle([64 * U, 112 * U, 192 * U, 216 * U], radius=16 * U, outline=WHITE, width=15 * U)
        d.arc([88 * U, 40 * U, 168 * U, 132 * U], 180, 360, fill=WHITE, width=15 * U)
        d.ellipse([118 * U, 148 * U, 138 * U, 168 * U], fill=WHITE)

    def orbit(d):
        C(128, 128, 36, 14)
        d.arc([48 * U, 48 * U, 208 * U, 208 * U], 20, 320, fill=WHITE, width=14 * U)
        d.polygon([(196 * U, 80 * U), (218 * U, 54 * U), (180 * U, 48 * U)], fill=WHITE)

    def follow(d):
        C(128, 128, 68, 15)
        L([(128, 40), (128, 92)], 14)
        L([(128, 164), (128, 216)], 14)
        L([(40, 128), (92, 128)], 14)
        L([(164, 128), (216, 128)], 14)
        d.ellipse([110 * U, 110 * U, 146 * U, 146 * U], fill=WHITE)

    def eye(d):
        d.arc([32 * U, 48 * U, 224 * U, 208 * U], 180, 360, fill=WHITE, width=15 * U)
        d.arc([32 * U, 48 * U, 224 * U, 208 * U], 0, 180, fill=WHITE, width=15 * U)
        C(128, 128, 38, 15)
        d.ellipse([116 * U, 116 * U, 140 * U, 140 * U], fill=WHITE)

    def trash(d):
        L([(56, 74), (200, 74)], 15)
        L([(96, 74), (108, 46), (148, 46), (160, 74)], 14)
        L([(72, 96), (88, 212), (168, 212), (184, 96)], 14)
        L([(112, 112), (116, 184)], 10)
        L([(144, 112), (140, 184)], 10)

    def gimbal(d):
        C(128, 128, 82, 15)
        d.arc([58 * U, 58 * U, 198 * U, 198 * U], 160, 200, fill=WHITE, width=12 * U)
        L([(128, 46), (128, 86)], 12)
        L([(128, 170), (128, 210)], 12)
        L([(46, 128), (86, 128)], 12)
        L([(170, 128), (210, 128)], 12)
        d.ellipse([112 * U, 112 * U, 144 * U, 144 * U], outline=WHITE, width=10 * U)

    def histogram(d):
        for x, h in [(56, 60), (88, 100), (120, 150), (152, 120), (184, 78)]:
            d.rectangle([x * U, (220 - h) * U, (x + 20) * U, 220 * U], fill=WHITE)

    def raw(d):
        d.rounded_rectangle([26 * U, 76 * U, 230 * U, 180 * U], radius=14 * U, outline=WHITE, width=13 * U)
        d.text((128 * U, 128 * U), 'RAW', fill=WHITE, anchor='mm')

    def sd(d):
        L([(76, 36), (180, 36), (204, 64), (204, 220), (52, 220), (52, 60), (76, 36)], 15)
        for y in (92, 128, 164):
            L([(76, y), (180, y)], 9)

    def antenna(d):
        L([(128, 210), (128, 48)], 16)
        d.arc([62 * U, 48 * U, 194 * U, 160 * U], 200, 340, fill=WHITE, width=13 * U)
        d.arc([86 * U, 74 * U, 170 * U, 146 * U], 200, 340, fill=WHITE, width=11 * U)

    def chev_down(d):
        L([(64, 96), (128, 166), (192, 96)], 20)

    def chev_right(d):
        L([(94, 60), (164, 128), (94, 196)], 20)

    def zoom(d):
        C(112, 112, 68, 16)
        L([(164, 164), (216, 216)], 22)
        L([(112, 76), (112, 148)], 12)
        L([(76, 112), (148, 112)], 12)

    def af(d):
        d.text((128 * U, 128 * U), 'AF', fill=WHITE, anchor='mm')

    def pin(d):
        d.arc([56 * U, 40 * U, 200 * U, 184 * U], 0, 360, fill=WHITE, width=15 * U)
        d.polygon([(84 * U, 150 * U), (172 * U, 150 * U), (128 * U, 222 * U)], fill=WHITE)
        d.ellipse([110 * U, 94 * U, 146 * U, 130 * U], fill=WHITE)

    def altitude(d):
        L([(128, 220), (128, 44)], 14)
        L([(94, 78), (128, 44), (162, 78)], 14)
        L([(94, 186), (128, 220), (162, 186)], 14)
        L([(32, 128), (224, 128)], 10)

    def distance(d):
        L([(36, 128), (220, 128)], 14)
        L([(72, 96), (36, 128), (72, 160)], 14)
        L([(184, 96), (220, 128), (184, 160)], 14)

    def speed(d):
        d.arc([48 * U, 56 * U, 208 * U, 216 * U], 180, 360, fill=WHITE, width=15 * U)
        L([(128, 136), (184, 92)], 16)
        d.ellipse([112 * U, 120 * U, 144 * U, 152 * U], fill=WHITE)

    def battery(d):
        d.rounded_rectangle([32 * U, 76 * U, 192 * U, 180 * U], radius=12 * U, outline=WHITE, width=13 * U)
        d.rectangle([196 * U, 108 * U, 218 * U, 148 * U], fill=WHITE)
        d.rectangle([54 * U, 98 * U, 132 * U, 158 * U], fill=WHITE)

    def remote(d):
        d.rounded_rectangle([56 * U, 38 * U, 200 * U, 218 * U], radius=18 * U, outline=WHITE, width=14 * U)
        d.ellipse([78 * U, 58 * U, 118 * U, 98 * U], outline=WHITE, width=10 * U)
        d.ellipse([138 * U, 58 * U, 178 * U, 98 * U], outline=WHITE, width=10 * U)
        d.rectangle([86 * U, 124 * U, 170 * U, 186 * U], outline=WHITE, width=10 * U)

    def link(d):
        d.arc([36 * U, 84 * U, 136 * U, 184 * U], 40, 220, fill=WHITE, width=16 * U)
        d.arc([120 * U, 72 * U, 220 * U, 172 * U], 220, 400, fill=WHITE, width=16 * U)
        L([(104, 128), (152, 128)], 14)

    def slider(d):
        for y, x in [(64, 92), (128, 164), (192, 116)]:
            L([(40, y), (216, y)], 10)
            d.ellipse([(x - 18) * U, (y - 18) * U, (x + 18) * U, (y + 18) * U], fill=WHITE)

    funcs = [
        home, more, satellite, obstacle, arrow_up, arrow_down, camera, record,
        playback, focus, takeoff, land, rth, waypoint, power, map_icon,
        grid, gear, check, close, back, sun, timer, burst,
        pano, moon, slowmo, master, photo, video, compass, warning,
        info, folder, play, plus, minus, lock, orbit, follow,
        eye, trash, gimbal, histogram, raw, sd, antenna, chev_down,
        chev_right, zoom, af, pin, altitude, distance, speed, battery,
        remote, link, slider, sun, info, moon, play,
    ]
    for i, fn in enumerate(funcs):
        draw_icon(i, fn)
    return atlas


def main():
    os.makedirs(OUT, exist_ok=True)
    quads, tex = load_geo(GEO, TEX, skip_bones=('stick_l', 'stick_r'))
    print('quads', len(quads))
    front = render_front_rgba(quads, tex)
    front.save(os.path.join(OUT, 'rc2_front.png'))
    make_shadow(front).save(os.path.join(OUT, 'rc2_front_shadow.png'))
    make_knob().save(os.path.join(OUT, 'rc2_stick.png'))
    make_icons().save(os.path.join(OUT, 'ui_icons.png'))
    print('wrote', OUT)
    print('front bbox', front.getbbox())
    print('screen rect in texture:', (123, 552, 1317, 1185))


if __name__ == '__main__':
    main()
