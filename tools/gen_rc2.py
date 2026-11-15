# -*- coding: utf-8 -*-
"""Procedural generator: DJI RC 2 remote controller -> Blockbench (GeckoLib item) model + texture.
Internal coords: Blockbench units, screen faces north (-Z), X = width, Y up (0 = bottom), exported with Y offset
so the item is centred on the GeckoLib item pivot.
"""
import json, math, uuid, base64, io, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else '/sessions/confident-focused-lamport/mnt/outputs/rc2_build'
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
RNG = np.random.default_rng(2)
SS = 4
MODEL = 'rc2'
OFFSET = np.array([0.0, -11.0, 0.0])     # export offset (centres item on pivot)


def uid():
    return str(uuid.uuid4())


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def norm_ang(a):
    return round((a + 180.0) % 360.0 - 180.0, 4)


MATS = {
    'shell':   ((160, 161, 163), 2.0),
    'rubber':  ((118, 119, 121), 4.5),
    'black':   ((30, 30, 32), 1.5),
    'bezel':   ((20, 20, 22), 1.0),
    'silver':  ((176, 178, 181), 2.0),
    'cbtn':    ((176, 177, 179), 2.0),
}

BONES, ORDER, CUBES = {}, [], []


def bone(name, pivot, parent=None):
    BONES[name] = dict(name=name, pivot=list(pivot), parent=parent, cubes=[], uuid=uid())
    ORDER.append(name)
    return name


def box(b, name, frm, to, mat, D=12, origin=None, rot=None, decals=None):
    c = dict(bone=b, name=name, frm=[float(v) for v in frm], to=[float(v) for v in to], mat=mat, D=D,
             origin=[float(v) for v in (origin if origin is not None else frm)],
             rot=[float(v) for v in (rot if rot is not None else [0, 0, 0])],
             decals=decals or {}, uuid=uid())
    CUBES.append(c); BONES[b]['cubes'].append(c)
    return c


def obox(b, name, center, size, rot, mat, **kw):
    center = np.array(center, float); size = np.array(size, float)
    return box(b, name, center - size / 2, center + size / 2, mat, origin=center, rot=rot, **kw)


def disc(b, name, center, radius, a0, a1, mat, axis='z', decals=None, D=12, sides=None):
    """regular polygon prism = union of sides/2 bars (exact polygon). a0..a1 = extent along axis."""
    if sides is None:
        sides = 12 if radius > 0.7 else 8
    seg = 2 * radius * math.tan(math.pi / sides) + 0.01
    for k in range(sides // 2):
        a = norm_ang(k * 360.0 / sides)
        e = 0.003 * k
        if axis == 'z':
            cen = [center[0], center[1], (a0 - e + a1) / 2]; size = [2 * radius, seg, a1 - a0 + e]; rot = [0, 0, a]
        elif axis == 'y':
            cen = [center[0], (a0 + a1 + e) / 2, center[2]]; size = [2 * radius, a1 - a0 + e, seg]; rot = [0, a, 0]
        else:  # x
            cen = [(a0 + a1) / 2, center[1], center[2]]; size = [a1 - a0, seg, 2 * radius]; rot = [a, 0, 0]
        obox(b, f'{name}_{k}', cen, size, rot, mat, decals=decals, D=D)


def rrect(b, name, x0, x1, y0, y1, z0, z1, radii, mat, decals=None, D=12):
    """Rounded rectangle prism along Z. radii = dict {(sx,sy): r} for corners (-1/+1 = min/max side)."""
    xm, ym = (x0 + x1) / 2, (y0 + y1) / 2
    for (sx, sy), r in radii.items():
        cx = x0 if sx < 0 else x1; cy = y0 if sy < 0 else y1
        dx, dy = -sx, -sy                 # direction toward the centre
        qn = f'{name}_{"l" if sx < 0 else "r"}{"b" if sy < 0 else "t"}'
        if r < 0.05:
            box(b, qn, (min(cx, xm), min(cy, ym), z0), (max(cx, xm), max(cy, ym), z1), mat, decals=decals, D=D)
            continue
        xa = cx + dx * r
        box(b, qn + '_a', (min(xa, xm), min(cy, ym), z0), (max(xa, xm), max(cy, ym), z1), mat, decals=decals, D=D)
        ya = cy + dy * r
        box(b, qn + '_b', (min(cx, xa), min(ya, ym), z0), (max(cx, xa), max(ya, ym), z1), mat, decals=decals, D=D)
        disc(b, qn + '_c', (xa, ya, 0), r, z0, z1, mat, axis='z', decals=decals, D=D)


# ============================================================================ painting context
class F:
    def __init__(self, img, P, S, T, fw, fh, k, face, cube):
        self.img, self.P, self.S, self.T = img, P, S, T
        self.fw, self.fh, self.k, self.face, self.c = fw, fh, k, face, cube
        self.X, self.Y, self.Z = P[..., 0], P[..., 1], P[..., 2]
        self.rotated = any(abs(a) > 1e-6 for a in cube['rot'])

    def paint(self, mask, color, a=1.0):
        m = mask[..., None].astype(float) * a
        self.img[..., :3] = self.img[..., :3] * (1 - m) + np.array(color[:3], float) * m
        self.img[..., 3:] = self.img[..., 3:] * (1 - m) + 255 * m

    def w2st(self, X, Y, Z):
        f, t = self.c['frm'], self.c['to']
        return {'north': (t[0] - X, t[1] - Y), 'south': (X - f[0], t[1] - Y), 'east': (t[2] - Z, t[1] - Y),
                'west': (Z - f[2], t[1] - Y), 'up': (X - f[0], Z - f[2]), 'down': (X - f[0], t[2] - Z)}[self.face]

    def wtext(self, txt, X, Y, Z, h, color, font=FONT_B, rot=0):
        """text centred at world point (only for unrotated cubes)."""
        if self.rotated:
            return
        s, t = self.w2st(X, Y, Z)
        H, W = self.img.shape[:2]
        px = max(6, int(h * self.k * 1.4))
        fnt = ImageFont.truetype(font, px)
        bb = fnt.getbbox(txt)
        tw, th = bb[2] - bb[0] + 4, bb[3] - bb[1] + 4
        tim = Image.new('L', (tw, th), 0)
        ImageDraw.Draw(tim).text((2 - bb[0], 2 - bb[1]), txt, font=fnt, fill=255)
        if rot:
            tim = tim.rotate(rot, expand=True, resample=Image.BICUBIC)
        sc = (h * self.k) / max(1, (th - 4))
        tim = tim.resize((max(1, int(tim.size[0] * sc)), max(1, int(tim.size[1] * sc))), Image.LANCZOS)
        layer = Image.new('L', (W, H), 0)
        layer.paste(tim, (int(s * self.k - tim.size[0] / 2), int(t * self.k - tim.size[1] / 2)))
        self.paint(np.asarray(layer, float) / 255.0, color)


def rr(X, Y, x0, y0, x1, y1, r):
    cx = np.clip(X, x0 + r, x1 - r); cy = np.clip(Y, y0 + r, y1 - r)
    return (X - cx) ** 2 + (Y - cy) ** 2 <= r * r


# ============================================================================ dimensions
HW = 12.0            # half width
H = 20.0             # body height (without antenna)
ZF, ZB = -2.55, 2.55 # front / back of lower body
ZU = 4.1             # back of upper (shoulder) block
WELL = [(7.4, 16.1), (-7.4, 16.1)]
BTN = [(2.9, 14.75, 'rth'), (-2.9, 14.75, 'power')]
SHELL = (160, 161, 163); RUB = (118, 119, 121); BLK = (30, 30, 32)


# ============================================================================ world-projected paint functions
def paint_shell(f):
    X, Y, Z = f.X, f.Y, f.Z
    if f.face == 'north':
        f.paint((np.abs(X) > 11.3) & (Y > 3.4) & (Y < 17.2), RUB)
        # LED bar: battery pill + 4 dots
        f.paint(rr(X, Y, 1.1, 17.68, 1.58, 17.92, 0.11), (92, 93, 95))
        for x in (0.44, -0.15, -0.74, -1.33):
            f.paint((X - x) ** 2 + (Y - 17.8) ** 2 < 0.12 ** 2, (92, 93, 95))
        # button / switch recesses
        for bx, by, _ in BTN:
            r = np.sqrt((X - bx) ** 2 + (Y - by) ** 2)
            f.paint((r > 0.74) & (r < 0.98), (136, 137, 139))
        f.paint(rr(X, Y, -1.5, 14.28, 1.5, 15.2, 0.45) & ~rr(X, Y, -1.27, 14.45, 1.27, 15.03, 0.27), (138, 139, 141))
        # well outer ring shading
        for wx, wy in WELL:
            r = np.sqrt((X - wx) ** 2 + (Y - wy) ** 2)
            f.paint((r > 2.0) & (r < 2.18), (140, 141, 143))
        # C N S markings
        for x, ch, col in ((0.78, 'C', (245, 245, 245)), (0.0, 'N', (240, 112, 30)), (-0.78, 'S', (245, 245, 245))):
            f.wtext(ch, x, 16.25, ZF, 0.5, col, font=FONT_R)
        for x in (0.78, 0.0, -0.78):
            f.paint((np.abs(X - x) < 0.04) & (Y > 15.4) & (Y < 15.8), (240, 240, 240))
        f.paint((np.abs(Y - 15.78) < 0.035) & (((X > 0.6) & (X < 0.82)) | ((X < -0.6) & (X > -0.82))), (240, 240, 240))
        # thin seam where the screen sits
        f.paint(rr(X, Y, -10.15, 2.25, 10.15, 13.2, 0.4) & ~rr(X, Y, -9.97, 2.43, 9.97, 13.02, 0.3), (120, 121, 123))
    elif f.face == 'south':
        center = np.abs(X) < 4.8
        # speaker grille
        g = rr(X, Y, -2.7, 10.35, 2.7, 10.95, 0.28)
        f.paint(g, (52, 53, 55))
        dots = g & (np.sin(X * 40) > 0.3) & (np.sin(Y * 40) > 0.3)
        f.paint(dots, (90, 91, 93))
        # screws
        for x in (2.1, -2.1):
            r = np.sqrt((X - x) ** 2 + (Y - 5.9) ** 2)
            f.paint(r < 0.22, (95, 96, 98)); f.paint(r < 0.1, (45, 45, 47))
        # bottom vent
        v = rr(X, Y, -3.1, 1.75, 3.1, 3.5, 0.4)
        f.paint(v, (24, 24, 26))
        sl = v & (np.abs(((X + 3.1) % 0.44) - 0.22) < 0.07) & (Y > 1.95) & (Y < 3.3)
        f.paint(sl, (52, 52, 54))
        # antenna-storage recess on the slope
        rec = rr(X, Y, -3.0, 15.35, 3.0, 16.55, 0.3)
        f.paint(rec, (128, 129, 131))
        for s in (1, -1):
            f.paint(rec & (s * X > 0.35) & (s * X < 2.3) & (Y > 15.7) & (Y < 16.15), (88, 89, 91))
            f.paint(rec & (s * X > 1.0) & (s * X < 1.5) & (Y > 15.55) & (Y < 16.35), (88, 89, 91))
        f.wtext('dji', 0.0, 18.35, ZU, 0.95, (126, 127, 129))
        f.paint((np.abs(np.abs(X) - 4.8) < 0.05) & (Y < 15.2) & (Y > 1.0), (138, 139, 141))
        f.paint(~center & (Y < 15.0), RUB)
    elif f.face in ('east', 'west'):
        f.paint((Y > 3.4) & (Y < 17.25) & (Z > -2.3) & (Z < 2.45), RUB)
        f.paint((np.abs(Y - 17.32) < 0.06) & (Z > -2.3), (128, 129, 131))
        # side seam between front shell and back cover
        f.paint((np.abs(Z - 0.4) < 0.04) & (Y >= 17.25), (128, 129, 131))
    elif f.face == 'up':
        # (( . )) wireless icons on the top
        for sx in (1, -1):
            cx, cz = sx * 4.2, 3.0
            r = np.sqrt((X - cx) ** 2 + (Z - cz) ** 2)
            ang = np.abs(np.arctan2(Z - cz, X - cx))
            side = (ang < 0.7) | (ang > math.pi - 0.7)
            f.paint(r < 0.08, (118, 119, 121))
            for r0 in (0.2, 0.36, 0.52):
                f.paint(side & (r > r0) & (r < r0 + 0.06), (118, 119, 121))
        f.paint((np.abs(Z - 0.4) < 0.04), (134, 135, 137))
    elif f.face == 'down':
        # port panel: microSD + USB-C
        f.paint(rr(X, Z, -3.3, -1.2, 3.3, 0.9, 0.3) & ~rr(X, Z, -3.2, -1.1, 3.2, 0.8, 0.25), (130, 131, 133))
        f.paint(rr(X, Z, 0.4, -0.25, 2.3, -0.05, 0.08), (25, 25, 27))
        f.paint(rr(X, Z, -2.6, -0.35, -0.9, 0.05, 0.18), (25, 25, 27))
        f.paint(rr(X, Z, -2.45, -0.22, -1.05, -0.08, 0.06), (70, 70, 72))
        for x in (9.6, -9.6):
            r = np.sqrt((X - x) ** 2 + Z ** 2)
            f.paint(r < 0.62, (120, 121, 123)); f.paint(r < 0.45, (60, 60, 62)); f.paint(r < 0.22, (165, 167, 170))
        f.paint(np.abs(Z - 0.4) < 0.04, (134, 135, 137))


def paint_rubber(f):
    pass


def paint_screen(f):
    if f.face != 'north':
        return
    X, Y = f.X, f.Y
    disp = rr(X, Y, -9.62, 2.78, 9.62, 12.67, 0.15)
    g = (Y - 2.78) / 9.9
    col = np.stack([58 + 8 * g, 59 + 8 * g, 61 + 8 * g], -1)
    m = disp[..., None].astype(float)
    f.img[..., :3] = f.img[..., :3] * (1 - m) + col * m
    refl = disp & (np.abs((X * 0.55 + Y) - 9.0) < 1.6)
    f.paint(refl, (80, 81, 84), 0.35)
    f.paint(rr(X, Y, -9.95, 2.45, 9.95, 13.0, 0.3) & ~rr(X, Y, -9.85, 2.55, 9.85, 12.9, 0.25), (52, 52, 55))


def paint_well(cx, cy):
    def fn(f):
        if f.face != 'north':
            return
        dx, dy = f.X - cx, f.Y - cy
        r = np.sqrt(dx * dx + dy * dy)
        f.paint(r < 2.0, (17, 17, 19))
        f.paint((r > 1.82) & (r < 2.0), (40, 40, 42))
        cross = ((np.abs(dx) < 0.05) | (np.abs(dy) < 0.05)) & (r > 1.15) & (r < 1.8)
        f.paint(cross, (44, 44, 46))
    return fn


def paint_gimbal(cx, cy):
    def fn(f):
        dx, dy = f.X - cx, f.Y - cy
        if f.face == 'north':
            f.paint((np.abs(dy) < 0.16) & (np.abs(dx) < 0.75), (10, 10, 11))
            f.paint((np.abs(np.abs(dx) - 0.85) < 0.05), (52, 52, 54))
            for k in range(-3, 4):
                f.paint((np.abs(dx - k * 0.2) < 0.025) & (np.abs(dy) > 0.3) & (np.abs(dy) < 0.6), (48, 48, 50))
    return fn


def paint_cap(cx, cy):
    def fn(f):
        dx, dy = f.X - cx, f.Y - cy
        r = np.sqrt(dx * dx + dy * dy); th = np.arctan2(dy, dx)
        if f.face == 'north':
            f.paint(np.ones_like(r, bool), (200, 202, 205))
            f.paint((np.sin(th * 9) > 0.2) & (r > 0.18), (120, 122, 126))
            f.paint(r < 0.16, (60, 60, 62))
        else:
            knurl = (np.sin(th * 14) > 0) ^ (np.sin(f.Z * 22) > 0)
            f.paint(knurl, (128, 130, 134))
    return fn


def paint_button(cx, cy, kind):
    def fn(f):
        if f.face != 'north':
            return
        dx, dy = f.X - cx, f.Y - cy
        r = np.sqrt(dx * dx + dy * dy)
        W = (245, 245, 245); O = (240, 112, 30)
        if kind == 'power':
            ring = (r > 0.26) & (r < 0.36) & ~((np.abs(dx) < 0.13) & (dy > 0))
            f.paint(ring, W)
            f.paint((np.abs(dx) < 0.05) & (dy > 0.02) & (dy < 0.42), W)
        else:
            # "H" (viewer's left = +X) and two orange bars
            for x in (0.47, 0.17):
                f.paint((np.abs(dx - x) < 0.045) & (np.abs(dy) < 0.24), W)
            f.paint((dx > 0.17) & (dx < 0.47) & (np.abs(dy) < 0.035), W)
            f.paint((np.abs(dx - 0.32) < 0.1) & (np.abs(dy - 0.33) < 0.03), W)
            f.paint((np.abs(dx - 0.04) < 0.02) & (np.abs(dy) < 0.26), W)
            for x in (-0.14, -0.33):
                f.paint((np.abs(dx - x) < 0.055) & (np.abs(dy) < 0.24), O)
    return fn


def paint_switch(f):
    if f.face != 'north':
        return
    X, Y = f.X, f.Y
    f.paint(np.abs(((X + 5) % 0.16) - 0.08) < 0.03, (20, 20, 22))
    f.paint((np.abs(X) < 0.24) & (np.abs(Y - 14.73) < 0.09), (240, 112, 30))


def paint_shoulder(kind, cx):
    def fn(f):
        if f.face != 'up':
            return
        dx, dz = f.X - cx, f.Z - (-0.5)
        if kind == 'rec':
            f.paint(dx ** 2 + dz ** 2 < 0.36 ** 2, (240, 112, 30))
        else:
            body = rr(dx, dz, -0.45, -0.32, 0.45, 0.32, 0.08) & ~rr(dx, dz, -0.36, -0.23, 0.36, 0.23, 0.05)
            f.paint(body, (150, 150, 152))
            f.paint((dx ** 2 + dz ** 2 < 0.17 ** 2) & (dx ** 2 + dz ** 2 > 0.1 ** 2), (150, 150, 152))
            f.paint((np.abs(dx + 0.15) < 0.12) & (np.abs(dz + 0.37) < 0.05), (150, 150, 152))
    return fn


def paint_wheel(cy, cz):
    def fn(f):
        th = np.arctan2(f.Y - cy, f.Z - cz)
        if f.face in ('east', 'west'):
            r = np.sqrt((f.Y - cy) ** 2 + (f.Z - cz) ** 2)
            f.paint(r < 0.4, (45, 45, 47))
        else:
            f.paint(np.sin(th * 16) > 0.1, (55, 55, 58))
    return fn


def paint_antenna(hx):
    def fn(f):
        X, Y = f.X, f.Y
        if f.face in ('north', 'south'):
            sgn = -np.sign(hx)
            groove = (np.abs(Y - 21.05) < 0.07) & (sgn * (X - hx) > 0.9) & (sgn * (X - hx) < 5.0)
            f.paint(groove, (14, 14, 15))
            f.paint((np.abs(Y - 21.7) < 0.03) & (sgn * (X - hx) > 0.5), (48, 48, 50))
            r = np.sqrt((X - hx) ** 2 + (Y - 21.4) ** 2)
            f.paint((r > 0.18) & (r < 0.28), (60, 60, 62))
        elif f.face == 'up':
            f.paint(np.abs(f.Z - 0.1) < 0.04, (50, 50, 52))
    return fn


def paint_cbtn(label):
    def fn(f):
        if f.face == 'south':
            x = 4.45 if label == 'C1' else -4.45
            f.wtext(label, x, 12.6, 3.0, 0.42, (90, 91, 93), font=FONT_R)
    return fn


ALL = {k: paint_shell for k in ('north', 'south', 'east', 'west', 'up', 'down')}

# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('body', (0, 10, 0), 'root')
bone('screen', (0, 7.7, -2.6), 'root')
bone('buttons', (0, 15, -2.6), 'root')

# lower body: rounded rect (bottom radius 3, top 1.6)
rrect('body', 'shell', -HW, HW, 0, H, ZF, ZB, {(-1, -1): 3.0, (1, -1): 3.0, (-1, 1): 1.6, (1, 1): 1.6}, 'shell',
      decals=ALL, D=12)
# upper back block (shoulders / wheels)
rrect('body', 'upper', -11.7, 11.7, 16.8, H, ZB, ZU, {(-1, -1): 0.0, (1, -1): 0.0, (-1, 1): 1.4, (1, 1): 1.4}, 'shell',
      decals=ALL, D=12)
# slope under the upper block and edge roundings
obox('body', 'slope', (0, 16.8, ZB), (22.4, 2.19, 2.19), [45, 0, 0], 'shell', decals=ALL, D=12)
# back grips (rubber)
for sx in (1, -1):
    x0, x1 = (4.8, HW) if sx > 0 else (-HW, -4.8)
    o, i = (1, -1) if sx > 0 else (-1, 1)
    rrect('body', f'grip_{"l" if sx > 0 else "r"}', x0, x1, 0.9, 16.3, ZB, ZB + 0.75,
          {(o, -1): 3.0, (i, -1): 1.1, (o, 1): 0.5, (i, 1): 0.7}, 'rubber', D=8)
# C1 / C2 back buttons
for sx, lab in ((1, 'C1'), (-1, 'C2')):
    x0, x1 = sorted([sx * 3.95, sx * 4.95])
    box('body', f'btn_{lab.lower()}', (x0, 11.7, ZB + 0.1), (x1, 13.5, ZB + 0.45), 'cbtn', D=24,
        decals={'south': paint_cbtn(lab)})

# screen
box('screen', 'screen', (-9.95, 2.45, -2.68), (9.95, 13.0, ZF + 0.01), 'bezel', D=16, decals={'north': paint_screen})

# joystick wells + gimbals
for i, (wx, wy) in enumerate(WELL):
    disc('body', f'well_{i}', (wx, wy, 0), 2.0, -2.64, ZF + 0.01, 'black', axis='z', decals={'north': paint_well(wx, wy)},
         D=20, sides=16)
    box('body', f'gimbal_{i}', (wx - 1.0, wy - 0.85, -2.85), (wx + 1.0, wy + 0.85, -2.6), 'black', D=24,
        decals={'north': paint_gimbal(wx, wy)})

# sticks (separate bones, pivot in the gimbal)
for name, (wx, wy) in (('stick_l', WELL[0]), ('stick_r', WELL[1])):
    bone(name, (wx, wy, -2.75), 'root')
    box(name, f'{name}_shaft', (wx - 0.17, wy - 0.17, -4.2), (wx + 0.17, wy + 0.17, -2.8), 'silver', D=16)
    disc(name, f'{name}_collar', (wx, wy, 0), 0.42, -3.05, -2.8, 'black', axis='z', D=16, sides=8)
    disc(name, f'{name}_cap', (wx, wy, 0), 0.62, -5.05, -4.1, 'silver', axis='z',
         decals={k: paint_cap(wx, wy) for k in ('north', 'east', 'west', 'up', 'down', 'south')}, D=24, sides=12)

# front buttons + mode switch
for bx, by, kind in BTN:
    disc('buttons', f'btn_{kind}', (bx, by, 0), 0.72, -2.82, ZF + 0.01, 'black', axis='z',
         decals={'north': paint_button(bx, by, kind)}, D=32, sides=12)
box('buttons', 'mode_switch', (-1.25, 14.45, -2.74), (1.25, 15.03, ZF + 0.01), 'black', D=32, decals={'north': paint_switch})

# shoulder buttons (record = +X, photo = -X) and dials
for sx, kind in ((1, 'rec'), (-1, 'photo')):
    x0, x1 = sorted([sx * 7.2, sx * 11.1])
    box('buttons', f'shoulder_{kind}', (x0, 19.65, -2.2), (x1, 20.45, 1.0), 'black', D=16)
    x0, x1 = sorted([sx * 7.3, sx * 11.0])
    cx = sx * 9.15
    box('buttons', f'shoulder_{kind}_top', (x0, 20.5, -2.0), (x1, 20.68, 0.9), 'black', D=24,
        decals={'up': paint_shoulder(kind, cx)})
    disc('buttons', f'dial_{kind}', (0, 19.85, 2.95), 0.8, sx * 7.9 if sx > 0 else -10.5, 10.5 if sx > 0 else -7.9,
         'black', axis='x', decals={k: paint_wheel(19.85, 2.95) for k in ('north', 'south', 'up', 'down', 'east', 'west')},
         D=16, sides=12)

# antennas (folded) + hinge posts
for name, sx in (('antenna_l', 1), ('antenna_r', -1)):
    hx = sx * 4.75
    bone(name, (hx, 21.4, 0.1), 'root')
    x0, x1 = sorted([sx * 5.55, sx * 0.08])
    rrect(name, f'{name}_bar', x0, x1, 20.6, 22.2, -0.85, 1.05,
          {(-1, -1): 0.35, (1, -1): 0.35, (-1, 1): 0.35, (1, 1): 0.35}, 'black',
          decals={k: paint_antenna(hx) for k in ('north', 'south', 'up')}, D=16)
    disc('body', f'{name}_post', (hx, 0, 0.1), 0.42, 19.9, 20.62, 'black', axis='y', D=16, sides=8)

print('cubes', len(CUBES))

# ============================================================================ BAKE (shared with avata pipeline)
FACES = ['north', 'east', 'south', 'west', 'up', 'down']


def face_dims(c, face):
    dx, dy, dz = [c['to'][i] - c['frm'][i] for i in range(3)]
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]


def face_local(c, face, S, T):
    f, t = c['frm'], c['to']; one = np.ones_like(S)
    if face == 'north': X, Y, Z = t[0] - S, t[1] - T, f[2] * one
    elif face == 'south': X, Y, Z = f[0] + S, t[1] - T, t[2] * one
    elif face == 'east': X, Y, Z = t[0] * one, t[1] - T, t[2] - S
    elif face == 'west': X, Y, Z = f[0] * one, t[1] - T, f[2] + S
    elif face == 'up': X, Y, Z = f[0] + S, t[1] * one, f[2] + T
    else: X, Y, Z = f[0] + S, f[1] * one, t[2] - T
    return np.stack([X, Y, Z], -1)


def bake_face(c, face):
    fw, fh = face_dims(c, face)
    D = c['D']
    pw, ph = max(1, int(math.ceil(fw * D - 1e-6))), max(1, int(math.ceil(fh * D - 1e-6)))
    W_, H_ = pw * SS, ph * SS
    kx, ky = W_ / fw, H_ / fh
    S = (np.arange(W_) + 0.5)[None, :].repeat(H_, 0) / kx
    T = (np.arange(H_) + 0.5)[:, None].repeat(W_, 1) / ky
    Pl = face_local(c, face, S, T)
    o = np.array(c['origin']); R = rotmat(c['rot'])
    P = (Pl - o) @ R.T + o
    col, noise = MATS[c['mat']]
    img = np.zeros((H_, W_, 4), float); img[..., :3] = col; img[..., 3] = 255
    fc = F(img, P, S, T, fw, fh, (kx + ky) / 2, face, c)
    if face in c['decals']:
        c['decals'][face](fc)
    small = img.reshape(ph, SS, pw, SS, 4).mean((1, 3))
    small[..., :3] += RNG.normal(0, noise, (ph, pw, 1))
    small[..., 3] = 255
    return np.clip(small, 0, 255).astype(np.uint8)


regions, shared, face_tex = [], {}, {}
for c in CUBES:
    for face in FACES:
        fw, fh = face_dims(c, face)
        key = None
        if face not in c['decals']:
            key = ('plain', c['mat'], round(fw * c['D']), round(fh * c['D']))
        if key is not None and key in shared:
            face_tex[(c['uuid'], face)] = shared[key]; continue
        regions.append(bake_face(c, face))
        face_tex[(c['uuid'], face)] = len(regions) - 1
        if key is not None:
            shared[key] = len(regions) - 1

order = sorted(range(len(regions)), key=lambda i: (-regions[i].shape[0], -regions[i].shape[1]))
ATW = 2048
pos = {}; x = y = sh = 0
for i in order:
    h, w = regions[i].shape[:2]
    if x + w > ATW:
        x = 0; y += sh + 1; sh = 0
    pos[i] = (x, y); x += w + 1; sh = max(sh, h)
need = y + sh
ATH = 256
while ATH < need:
    ATH *= 2
print('regions', len(regions), 'atlas', ATW, ATH, 'used', need)
atlas = np.zeros((ATH, ATW, 4), np.uint8)
for i, a in enumerate(regions):
    px, py = pos[i]; atlas[py:py + a.shape[0], px:px + a.shape[1]] = a
tex = Image.fromarray(atlas, 'RGBA'); tex.save(os.path.join(OUT, f'{MODEL}.png'), optimize=True)


def face_uv(c, face):
    i = face_tex[(c['uuid'], face)]; px, py = pos[i]; h, w = regions[i].shape[:2]
    return [px, py, px + w, py + h]


# ============================================================================ ANIMATIONS (bedrock values)
ANIMS = {
    'animation.rc2.idle': dict(loop='loop', length=1.0, bones={'root': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    'animation.rc2.sticks': dict(loop='once', length=2.0, bones={
        'stick_l': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [14, 0, 0], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom'),
                                 (1.5, [-14, 0, 0], 'catmullrom'), (2.0, [0, 0, 0], 'catmullrom')]},
        'stick_r': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 14, 0], 'catmullrom'), (1.0, [8, 0, 0], 'catmullrom'),
                                 (1.5, [0, -14, 0], 'catmullrom'), (2.0, [0, 0, 0], 'catmullrom')]},
    }),
    'animation.rc2.antenna_open': dict(loop='hold', length=0.5, bones={
        'antenna_l': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 0, -90], 'catmullrom')]},
        'antenna_r': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 0, 90], 'catmullrom')]},
    }),
    'animation.rc2.antenna_close': dict(loop='hold', length=0.5, bones={
        'antenna_l': {'rotation': [(0.0, [0, 0, -90], 'catmullrom'), (0.5, [0, 0, 0], 'catmullrom')]},
        'antenna_r': {'rotation': [(0.0, [0, 0, 90], 'catmullrom'), (0.5, [0, 0, 0], 'catmullrom')]},
    }),
}

# item display transforms (vanilla semantics; tweak in Blockbench "Display" tab)
DISPLAY = {
    'thirdperson_righthand': {'rotation': [10, 180, 0], 'translation': [0, 3, 1.5], 'scale': [0.38, 0.38, 0.38]},
    'thirdperson_lefthand': {'rotation': [10, 180, 0], 'translation': [0, 3, 1.5], 'scale': [0.38, 0.38, 0.38]},
    'firstperson_righthand': {'rotation': [-20, 180, 0], 'translation': [-3, 3, -2], 'scale': [0.45, 0.45, 0.45]},
    'firstperson_lefthand': {'rotation': [-20, 180, 0], 'translation': [-3, 3, -2], 'scale': [0.45, 0.45, 0.45]},
    'ground': {'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
    'gui': {'rotation': [15, 200, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, -6], 'scale': [0.6, 0.6, 0.6]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
}


# ============================================================================ EXPORT
def r4(v):
    return [round(float(a), 4) for a in v]


def off(v):
    return list(np.array(v, float) + OFFSET)


elements = []
for c in CUBES:
    el = {'name': c['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default',
          'allow_mirror_modeling': True, 'from': r4(off(c['frm'])), 'to': r4(off(c['to'])), 'autouv': 0, 'color': 0,
          'origin': r4(off(c['origin'])), 'faces': {f: {'uv': face_uv(c, f), 'texture': 0} for f in FACES},
          'type': 'cube', 'uuid': c['uuid']}
    if any(abs(a) > 1e-6 for a in c['rot']):
        el['rotation'] = r4(c['rot'])
    elements.append(el)


def outliner_node(name):
    b = BONES[name]
    node = {'name': name, 'origin': r4(off(b['pivot'])), 'color': 0, 'uuid': b['uuid'], 'export': True,
            'mirror_uv': False, 'isOpen': name == 'root', 'locked': False, 'visibility': True, 'autouv': 0,
            'children': [c['uuid'] for c in b['cubes']]}
    for ch in ORDER:
        if BONES[ch]['parent'] == name:
            node['children'].append(outliner_node(ch))
    return node


buf = io.BytesIO(); tex.save(buf, 'PNG', optimize=True)
bb_anims = []
for an, a in ANIMS.items():
    animators = {}
    for bn, chans in a['bones'].items():
        kfs = [{'channel': ch, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': uid(), 'time': t,
                'color': -1, 'interpolation': ip} for ch, keys in chans.items() for t, v, ip in keys]
        animators[BONES[bn]['uuid']] = {'name': bn, 'type': 'bone', 'keyframes': kfs}
    bb_anims.append({'uuid': uid(), 'name': an, 'loop': a['loop'], 'override': False, 'length': a['length'], 'snapping': 24,
                     'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
                     'animators': animators})

bbmodel = {
    'meta': {'format_version': '4.5', 'model_format': 'animated_entity_model', 'box_uv': False},
    'name': MODEL, 'model_identifier': MODEL, 'geckolib_model_type': 'item',
    'visible_box': [2, 2, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [],
    'unhandled_root_fields': {}, 'resolution': {'width': ATW, 'height': ATH},
    'elements': elements, 'outliner': [outliner_node('root')],
    'textures': [{'path': '', 'name': f'{MODEL}.png', 'folder': 'item', 'namespace': '', 'id': '0', 'width': ATW,
                  'height': ATH, 'uv_width': ATW, 'uv_height': ATH, 'particle': True, 'use_as_default': False,
                  'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto',
                  'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
                  'visible': True, 'internal': True, 'saved': False, 'uuid': uid(), 'relative_path': '',
                  'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
    'display': DISPLAY,
    'animations': bb_anims,
}
json.dump(bbmodel, open(os.path.join(OUT, f'{MODEL}.bbmodel'), 'w', encoding='utf-8'), separators=(',', ':'))

geo_bones = []
for name in ORDER:
    b = BONES[name]; p = off(b['pivot'])
    gb = {'name': name}
    if b['parent']:
        gb['parent'] = b['parent']
    gb['pivot'] = r4([-p[0], p[1], p[2]])
    cubes = []
    for c in b['cubes']:
        frm, to, org = off(c['frm']), off(c['to']), off(c['origin'])
        size = [to[i] - frm[i] for i in range(3)]
        cube = {'origin': r4([-to[0], frm[1], frm[2]]), 'size': r4(size)}
        if any(abs(a) > 1e-6 for a in c['rot']):
            cube['pivot'] = r4([-org[0], org[1], org[2]])
            cube['rotation'] = r4([-c['rot'][0], -c['rot'][1], c['rot'][2]])
        uv = {}
        for f in FACES:
            u = face_uv(c, f); w, h = u[2] - u[0], u[3] - u[1]
            uv[f] = ({'uv': [u[0] + w, u[1] + h], 'uv_size': [-w, -h]} if f in ('up', 'down')
                     else {'uv': [u[0], u[1]], 'uv_size': [w, h]})
        cube['uv'] = uv
        cubes.append(cube)
    if cubes:
        gb['cubes'] = cubes
    geo_bones.append(gb)
geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': f'geometry.{MODEL}', 'texture_width': ATW, 'texture_height': ATH,
                    'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 0, 0]},
    'bones': geo_bones}]}
json.dump(geo, open(os.path.join(OUT, f'{MODEL}.geo.json'), 'w', encoding='utf-8'), indent=1)

aj = {'format_version': '1.8.0', 'animations': {}}
for an, a in ANIMS.items():
    e = {}
    if a['loop'] == 'loop':
        e['loop'] = True
    elif a['loop'] == 'hold':
        e['loop'] = 'hold_on_last_frame'
    e['animation_length'] = a['length']
    bj = {}
    for bn, chans in a['bones'].items():
        bj[bn] = {}
        for ch, keys in chans.items():
            kd = {}
            for t, v, ip in keys:
                k = repr(float(t))
                kd[k] = {'post': v, 'lerp_mode': ip} if ip == 'catmullrom' else v
            bj[bn][ch] = kd
    e['bones'] = bj
    aj['animations'][an] = e
json.dump(aj, open(os.path.join(OUT, f'{MODEL}.animation.json'), 'w', encoding='utf-8'), indent=1)

json.dump({'parent': 'builtin/entity', 'display': DISPLAY}, open(os.path.join(OUT, f'{MODEL}.item.json'), 'w'), indent=1)
print('done')
