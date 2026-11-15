# -*- coding: utf-8 -*-
"""Procedural generator: DJI Mini 4 Pro -> Blockbench (GeckoLib entity) model + texture.
Body frame: X width (drone right = +X), Y up (body bottom = 0), Z length (nose = -Z).
Export: everything shifted so the front landing feet touch Y = 0, and the 'drone' bone carries
a rest pitch (nose-up landed pose) pivoting on the front feet so the rear feet also touch the ground.
1 unit ~= 6 mm.

v2: rear/bottom-open battery bay; the Mini 4 Pro battery item model (model/battery/mini4pro, 3 mm/px) is merged
at scale 0.5 as bone 'battery' (pre-baked faces, hidden by default) with insert / remove animations.
usage: python3 gen_mini4pro.py <out_dir> [battery_model_dir]
"""
import json, math, uuid, base64, io, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else 'm4p_build'
BAT_DIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'model', 'battery', 'mini4pro')
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf'
RNG = np.random.default_rng(4)
SS = 4
MODEL = 'mini4pro'


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


# base colour, noise amplitude
MATS = {
    'shell':  ((200, 202, 204), 1.6),
    'shell2': ((186, 188, 190), 1.6),
    'grille': ((112, 114, 117), 1.5),
    'recess': ((70, 72, 75), 1.5),
    'black':  ((28, 28, 30), 1.2),
    'cam':    ((22, 22, 24), 1.2),
    'bell':   ((74, 76, 80), 1.5),
    'rubber': ((150, 152, 155), 2.5),
    'clear':  (None, 0),
}

BONES, ORDER, CUBES = {}, [], []


def bone(name, pivot, parent=None, rot=None):
    BONES[name] = dict(name=name, pivot=list(pivot), parent=parent, cubes=[], uuid=uid(), rot=rot or [0, 0, 0])
    ORDER.append(name)
    return name


def box(b, name, frm, to, mat, D=10, origin=None, rot=None, decals=None, hidden=(), ao=False):
    c = dict(bone=b, name=name, frm=[float(v) for v in frm], to=[float(v) for v in to], mat=mat, D=D,
             origin=[float(v) for v in (origin if origin is not None else frm)],
             rot=[float(v) for v in (rot if rot is not None else [0, 0, 0])],
             decals=decals or {}, hidden=set(hidden), ao=ao, uuid=uid())
    CUBES.append(c); BONES[b]['cubes'].append(c)
    return c


def obox(b, name, center, size, rot, mat, **kw):
    center = np.array(center, float); size = np.array(size, float)
    return box(b, name, center - size / 2, center + size / 2, mat, origin=center, rot=rot, **kw)


def disc(b, name, center, radius, a0, a1, mat, axis='y', decals=None, D=12, sides=None, hidden=()):
    if sides is None:
        sides = 12 if radius > 0.8 else 8
    seg = 2 * radius * math.tan(math.pi / sides) + 0.01
    for k in range(sides // 2):
        a = norm_ang(k * 360.0 / sides)
        e = 0.003 * k
        if axis == 'z':
            cen = [center[0], center[1], (a0 - e + a1) / 2]; size = [2 * radius, seg, a1 - a0 + e]; rot = [0, 0, a]
        elif axis == 'y':
            cen = [center[0], (a0 + a1 + e) / 2, center[2]]; size = [2 * radius, a1 - a0 + e, seg]; rot = [0, a, 0]
        else:
            cen = [(a0 + a1) / 2, center[1], center[2]]; size = [a1 - a0, seg, 2 * radius]; rot = [a, 0, 0]
        obox(b, f'{name}_{k}', cen, size, rot, mat, decals=decals, D=D, hidden=hidden)


def interp(z, pts):
    zs, vs = zip(*pts)
    return float(np.interp(z, zs, vs))


# ============================================================================ painting context
class F:
    def __init__(self, img, P, L, S, T, fw, fh, k, face, cube):
        self.img, self.P, self.L, self.S, self.T = img, P, L, S, T
        self.fw, self.fh, self.k, self.face, self.c = fw, fh, k, face, cube
        self.X, self.Y, self.Z = P[..., 0], P[..., 1], P[..., 2]

    def paint(self, mask, color, a=1.0):
        m = mask[..., None].astype(float) * a
        col = np.asarray(color, float)
        col = col[..., :3] if col.ndim == 3 else col[:3]
        self.img[..., :3] = self.img[..., :3] * (1 - m) + col * m
        alpha = color[3] if (np.ndim(color) == 1 and len(color) > 3) else 255
        self.img[..., 3:] = self.img[..., 3:] * (1 - m) + alpha * m

    def text(self, txt, cs, ct, h, color, font=FONT_B, rot=0):
        H, W = self.img.shape[:2]
        px = max(6, int(h * self.k * 1.4))
        fnt = ImageFont.truetype(font, px)
        bb = fnt.getbbox(txt)
        tw, th = bb[2] - bb[0] + 4, bb[3] - bb[1] + 4
        tim = Image.new('L', (tw, th), 0)
        ImageDraw.Draw(tim).text((2 - bb[0], 2 - bb[1]), txt, font=fnt, fill=255)
        sc = (h * self.k) / max(1, th - 4)
        tim = tim.resize((max(1, int(tw * sc)), max(1, int(th * sc))), Image.LANCZOS)
        if rot:
            tim = tim.rotate(rot, expand=True, resample=Image.BICUBIC)
        layer = Image.new('L', (W, H), 0)
        layer.paste(tim, (int(cs * self.k - tim.size[0] / 2), int(ct * self.k - tim.size[1] / 2)))
        self.paint(np.asarray(layer, float) / 255.0, color)

    def wtext(self, txt, X, Y, Z, h, color, font=FONT_B, rot=0):
        if any(abs(a) > 1e-6 for a in self.c['rot']):
            return
        f, t = self.c['frm'], self.c['to']
        s, tt = {'north': (t[0] - X, t[1] - Y), 'south': (X - f[0], t[1] - Y), 'east': (t[2] - Z, t[1] - Y),
                 'west': (Z - f[2], t[1] - Y), 'up': (X - f[0], Z - f[2]), 'down': (X - f[0], t[2] - Z)}[self.face]
        self.text(txt, s, tt, h, color, font, rot)


def rr(A, B, a0, b0, a1, b1, r):
    ca = np.clip(A, a0 + r, a1 - r); cb = np.clip(B, b0 + r, b1 - r)
    return (A - ca) ** 2 + (B - cb) ** 2 <= r * r


def lens(f, A, B, ca, cb, R, ring=(150, 152, 155), glass=(18, 20, 24), tint=(70, 50, 110)):
    """generic round glass lens with ring and highlight in the (A,B) plane."""
    r = np.sqrt((A - ca) ** 2 + (B - cb) ** 2)
    f.paint(r < R, ring)
    f.paint(r < R * 0.8, (12, 12, 14))
    g = np.clip(1 - r / (R * 0.7), 0, 1)
    m = (r < R * 0.7)[..., None].astype(float)
    col = np.array(glass, float)[None, None, :] * (1 - g[..., None] * 0.3) + np.array(tint, float)[None, None, :] * (g[..., None] ** 2) * 0.6
    f.img[..., :3] = f.img[..., :3] * (1 - m) + col * m
    f.paint(((A - ca + 0.25 * R) ** 2 + (B - cb - 0.25 * R) ** 2) < (0.16 * R) ** 2, (235, 238, 245))
    f.paint(((A - ca - 0.2 * R) ** 2 + (B - cb + 0.3 * R) ** 2) < (0.07 * R) ** 2, (160, 160, 175))


# ============================================================================ BODY PROFILE (lofted slices)
Z0, Z1, DZ = -6.0, 10.6, 0.5
WL = [(-6, 5.0), (-2.5, 5.4), (0, 4.5), (2, 4.1), (4.5, 4.5), (6.0, 5.6), (6.6, 6.1), (9.4, 6.1), (10.6, 5.5)]
WU = [(-6, 5.1), (-2.5, 5.2), (0.5, 4.2), (3, 3.9), (6, 4.3), (10.6, 4.7)]
YT = [(-6, 7.6), (-1, 7.6), (4, 7.3), (8, 6.7), (10.6, 6.2)]
YB = [(-6, 0.7), (-4, 0.2), (-3, 0.0), (10.6, 0.0)]
YM = 3.9
RS = 1.15                      # radius of the rounded top shoulders (smooth skin, see TOP SKIN)

# ---- battery bay (battery slides in from the tail; bay is open at the rear and at the rear part of the bottom)
BAT_S = 0.5                    # battery item model (3 mm/px) -> drone (6 mm/px)
BAT_Y, BAT_Z = 0.25, 4.0833    # battery item origin in drone coords (item is turned 180 deg about Y)
Z_BAY0 = -2.59                 # bay front wall (battery connector end)
Z_FLOOR = 4.75                 # floor ends here; behind it the bay is open at the bottom
Z_COLLAR = 8.08                # battery collar (wider rear part) starts
Y_CEIL = 4.8                   # bay ceiling
WIN_Z, WIN_Y = (8.9, 10.6), (1.65, 3.85)   # side windows for the battery push buttons


def bay_inner(z):
    return 3.25 if z < Z_COLLAR else 3.95


def layers_at(z):
    """(name, y0, y1, halfwidth, inner_halfwidth or None for a full slab)"""
    wl, wu, y1, y0 = interp(z, WL), interp(z, WU), interp(z, YT), interp(z, YB)
    if z < Z_BAY0:
        return [('bot', y0, y0 + 0.6, min(wl, wu) - 0.7, None), ('low', y0 + 0.6, YM, wl, None),
                ('up', YM, y1 - RS, wu, None)]
    ih = bay_inner(z)
    out = [('bot', y0, y0 + 0.6, min(wl, wu) - 0.7, None if z < Z_FLOOR else ih)]
    if WIN_Z[0] <= z <= WIN_Z[1]:
        out += [('low', y0 + 0.6, WIN_Y[0], wl, ih), ('uplo', WIN_Y[1], Y_CEIL, wu, ih)]
    else:
        out += [('low', y0 + 0.6, YM, wl, ih), ('uplo', YM, Y_CEIL, wu, ih)]
    out += [('up', Y_CEIL, y1 - RS, wu, None)]
    return out


# ---- world-projected body paint ------------------------------------------------
def paint_bay(f):
    """bay interior (walls, ceiling, floor top, front wall with connector). returns True if handled."""
    X, Y, Z = f.X, f.Y, f.Z
    ih = np.where(Z < Z_COLLAR, 3.25, 3.95)
    m = (np.abs(X) < ih + 0.06) & (Z > Z_BAY0 - 0.06) & (Y > 0.55) & (Y < Y_CEIL + 0.05) & (Z < 10.62)
    if not m.any():
        return False
    depth = np.clip((10.6 - Z) / 13.0, 0, 1)
    col = np.stack([78 - 34 * depth, 80 - 34 * depth, 84 - 34 * depth], -1)
    f.paint(m, col)
    if f.n[2] > 0.9:                                   # front wall: connector block + yellow laser label
        fw = m & (np.abs(Z - Z_BAY0) < 0.08)
        f.paint(fw & rr(X, Y, -1.5, 3.55, 1.5, 4.5, 0.15), (24, 24, 26))
        for k in range(6):
            x = -1.1 + k * 0.44
            f.paint(fw & rr(X, Y, x - 0.08, 3.7, x + 0.08, 4.35, 0.03), (205, 176, 104))
        f.paint(fw & rr(X, Y, -2.0, 1.4, 2.0, 3.1, 0.12), (232, 214, 40))
        f.paint(fw & rr(X, Y, -1.85, 1.55, 1.85, 2.95, 0.1) & ~rr(X, Y, -1.7, 1.7, 1.7, 2.8, 0.08), (30, 30, 30))
        f.paint(fw & (np.abs(Y - 2.45) < 0.05) & (np.abs(X) < 1.2), (40, 40, 35))
        f.paint(fw & (np.abs(Y - 2.15) < 0.04) & (np.abs(X) < 1.4), (60, 60, 50))
        for sx in (1, -1):
            f.paint(fw & ((X - sx * 1.85) ** 2 + (Y - 4.0) ** 2 < 0.2 ** 2), (150, 152, 156))
    elif abs(f.n[0]) > 0.9:                            # side walls: guide rails
        for y in (1.2, 4.3):
            f.paint(m & (np.abs(Y - y) < 0.08), (95, 97, 101))
    return True


def paint_rear_ports(f):
    X, Y, Z = f.X, f.Y, f.Z
    if not (f.n[2] > 0.9 and Z.min() > 10.4):
        return
    f.paint(rr(X, Y, -2.5, 5.0, -0.85, 5.62, 0.28), (150, 152, 155))
    f.paint(rr(X, Y, -2.38, 5.1, -0.97, 5.52, 0.2), (22, 22, 24))
    f.paint(rr(X, Y, -2.1, 5.27, -1.25, 5.35, 0.03), (120, 122, 126))
    f.paint(rr(X, Y, 0.35, 5.18, 2.3, 5.42, 0.08), (24, 24, 26))
    f.paint(rr(X, Y, -0.6, 5.22, -0.25, 5.38, 0.05), (130, 132, 135))      # USB icon
    f.paint(rr(X, Y, 1.9, 5.5, 2.2, 5.75, 0.03), (130, 132, 135))         # SD icon
    f.paint(np.abs(Y - Y_CEIL) < 0.03, (176, 178, 180))


def paint_body(f):
    X, Y, Z = f.X, f.Y, f.Z
    face = f.face
    paint_bay(f)
    paint_rear_ports(f)
    if face == 'up':
        # dji logo + rear vents + battery LEDs / button + top vision sensors
        f.paint(dji_logo(X, Z), (238, 240, 242))
        for sx in (1, -1):
            v = rr(sx * X, Z, 1.3, 4.6, 3.4, 8.7, 0.35)
            f.paint(v, (35, 36, 38))
            f.paint(v & (np.abs(((Z - 4.6) % 0.55) - 0.27) < 0.07), (70, 72, 75))
        f.paint(rr(X, Z, -1.05, 9.15, 1.05, 10.05, 0.25), (180, 182, 184))
        f.paint(rr(X, Z, -0.95, 9.25, 0.95, 9.95, 0.2), (208, 210, 212))
        for i, x in enumerate((-0.6, -0.2, 0.2, 0.6)):
            f.paint((X - x) ** 2 + (Z - 8.6) ** 2 < 0.09 ** 2, (120, 122, 125))
        # battery seam
        f.paint(np.abs(Z - 10.3) < 0.04, (160, 162, 165))
    elif face == 'down':
        for sx in (1, -1):
            r = np.sqrt((X - sx * 3.2) ** 2 + (Z + 4.6) ** 2)
            f.paint(r < 0.95, (60, 62, 65)); f.paint((r < 0.95) & (np.sin(np.arctan2(Z + 4.6, X - sx * 3.2) * 10) > 0.4), (90, 92, 96))
            f.paint(r < 0.55, (16, 16, 18)); f.paint((X - sx * 3.2 - 0.15) ** 2 + (Z + 4.75) ** 2 < 0.13 ** 2, (180, 180, 200))
            for zz in (0.4, 2.0):
                f.paint(rr(sx * X, Z, 0.45, zz, 2.3, zz + 1.1, 0.2), (150, 152, 155))
                f.paint(rr(sx * X, Z, 0.6, zz + 0.15, 2.15, zz + 0.95, 0.15), (60, 62, 65))
        f.paint(rr(X, Z, -1.2, -3.6, 1.2, -2.6, 0.45), (25, 25, 27))
        f.paint(rr(X, Z, -0.9, -3.4, -0.1, -2.8, 0.25), (45, 48, 55))
        f.paint(X ** 2 + (Z + 1.6) ** 2 < 0.3 ** 2, (210, 205, 120)); f.paint(X ** 2 + (Z + 1.6) ** 2 < 0.16 ** 2, (240, 235, 60))
        f.paint(np.abs(Z - Z_FLOOR) < 0.04, (150, 152, 155))
    elif face in ('east', 'west'):
        sx = 1 if face == 'east' else -1
        # slanted side vents near the rear top
        u = Z - (Y - 4.8) * 0.6
        f.paint((u > 6.0) & (u < 8.6) & (Y > 4.5) & (Y < 6.3), (32, 33, 35))
        f.paint((u > 6.0) & (u < 8.6) & (Y > 4.5) & (Y < 6.3) & (np.abs(((Y - 4.5) % 0.45) - 0.22) < 0.06), (75, 77, 80))
        f.paint(np.abs(Y - YM) < 0.035, (168, 170, 172))
    elif face == 'north':
        f.paint((np.abs(X) < 2.3) & (Y > 2.0) & (Y < 7.3) & (Z < -5.9), (66, 68, 71))



_LOGO = None


def dji_logo(X, Z, cx=0.0, cz=6.0, h=0.95):
    """'dji' wordmark mask on the top face (reads with the nose up), sampled in world X/Z."""
    global _LOGO
    if _LOGO is None:
        fnt = ImageFont.truetype(FONT_B, 200)
        bb = fnt.getbbox('dji')
        im = Image.new('L', (bb[2] - bb[0] + 20, bb[3] - bb[1] + 20), 0)
        ImageDraw.Draw(im).text((10 - bb[0], 10 - bb[1]), 'dji', font=fnt, fill=255)
        _LOGO = np.asarray(im, float) / 255.0
    Hh, Ww = _LOGO.shape
    w = h * Ww / Hh
    u = (X - (cx - w / 2)) / w * Ww; v = (Z - (cz - h / 2)) / h * Hh
    ok = (u >= 0) & (u < Ww) & (v >= 0) & (v < Hh)
    return _LOGO[np.clip(v.astype(int), 0, Hh - 1), np.clip(u.astype(int), 0, Ww - 1)] * ok


def euler_zyx(M):
    """decompose M = Rz(c) Ry(b) Rx(a) -> [a, b, c] degrees (Blockbench order)."""
    b = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    a = math.atan2(M[2, 1], M[2, 2]); c = math.atan2(M[1, 0], M[0, 0])
    return [round(math.degrees(v), 4) for v in (a, b, c)]


def lens_axis(f, C, d, R, ring=(176, 178, 181)):
    """lens painted around axis (C, d) on faces whose normal is d."""
    up = np.array([0.0, 1.0, 0.0]); u = np.cross(up, d); u /= np.linalg.norm(u); v = np.cross(d, u)
    rel = f.P - C
    A = rel @ u; B = rel @ v
    lens(f, A, B, 0.0, 0.0, R, ring=ring)


def bulge(b, name, lens_center, yaw_out, pitch_up, facing, profile, lens_r, lens_slices, sides=12, D=16):
    """Lofted round bump whose cap (lens) faces direction d; slices recede along -d into the shell."""
    th = math.radians(pitch_up)
    if facing == 'front':
        psi = -math.radians(yaw_out)            # outward toward +X when yaw_out > 0
        d = np.array([-math.sin(psi) * math.cos(th), math.sin(th), -math.cos(psi) * math.cos(th)])
    else:
        d = np.array([math.sin(math.radians(yaw_out)) * math.cos(th), math.sin(th),
                      math.cos(math.radians(yaw_out)) * math.cos(th)])
    d /= np.linalg.norm(d)
    psi = math.atan2(-d[0], -d[2]); th2 = math.asin(d[1])
    Rbase = rotmat([0, math.degrees(psi), 0]) @ rotmat([math.degrees(th2), 0, 0])   # maps (0,0,-1) -> d
    C = np.array(lens_center, float)

    def paint(f):
        if f.face == 'north':
            lens_axis(f, C, d, lens_r)
    for k in range(len(profile) - 1):
        (sa, ra), (sb, rb) = profile[k], profile[k + 1]
        r = (ra + rb) / 2
        seg = 2 * r * math.tan(math.pi / sides) + 0.01
        for j in range(sides // 2):
            e = 0.003 * j
            M = Rbase @ rotmat([0, 0, j * 360.0 / sides])
            cen = C - d * ((sa + sb) / 2 + e / 2)
            obox(b, f'{name}_{k}_{j}', cen, (2 * r, seg, sb - sa + e), euler_zyx(M), 'shell', D=D,
                 decals={'north': paint} if k < lens_slices else None)

# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('drone', (0, 0, 0), 'root')          # pivot / rest pitch set after geometry is known
bone('body', (0, 4, 2), 'drone')
bone('frame', (0, 4, 2), 'drone')
GP = (0, 4.3, -7.75)                      # gimbal tilt axis
bone('gimbal', GP, 'drone')

# ---- body: per layer, polygonal outline built from yawed side plates (smooth taper, no staircase)
NODES = sorted(set([z for z, _ in WL] + [z for z, _ in WU] + [z for z, _ in YT] +
                   [-4.0, -3.0, 1.0, 7.3, 8.6, Z_BAY0, Z_FLOOR, Z_COLLAR, WIN_Z[0]]))
BODY_DEC = {k: paint_body for k in ('north', 'south', 'east', 'west', 'up', 'down')}


def layer_hw(nm, z):
    return {n: hw for n, y0, y1, hw, ih in layers_at(z)}.get(nm, None)


def layer_w(nm, z, zm):
    """outline half-width of layer nm at z (falls back to the layer list at zm for split layers)."""
    v = layer_hw(nm, z)
    if v is None:
        wl, wu = interp(z, WL), interp(z, WU)
        v = {'low': wl, 'uplo': wu, 'up': wu}.get(nm, wu)
    return v


for i in range(len(NODES) - 1):
    za, zb = NODES[i], NODES[i + 1]
    zm = (za + zb) / 2
    for nm, y0, y1, _, ih in layers_at(zm):
        ha, hb = layer_w(nm, za, zm), layer_w(nm, zb, zm)
        T = max(ha, hb) + 0.25 if ih is None else min(ha, hb) - ih
        if T < 0.12:
            continue
        for sx in (1, -1):
            ex, ez = sx * (hb - ha), zb - za
            L = math.hypot(ex, ez); ex, ez = ex / L, ez / L
            n1 = np.array([ez, -ex]); n = n1 if np.sign(n1[0]) == -sx or abs(n1[0]) < 1e-9 and sx < 0 else -n1
            mid = np.array([sx * (ha + hb) / 2, zm])
            c = mid + n * (T / 2)
            yaw = norm_ang(math.degrees(math.atan2(-ez, ex)))
            ext = 0.12 if 0 < i < len(NODES) - 2 else 0.0
            obox('body', f'body_{nm}_{i}_{"r" if sx > 0 else "l"}', (c[0], (y0 + y1) / 2, c[1]),
                 (L + 2 * ext, y1 - y0, T), [0, yaw, 0], 'shell', D=12, decals=BODY_DEC)

# ---- TOP SKIN: smooth rounded top shell lofted along Z (replaces the old terraced bev1/bev2 layers)
def skin_plate(b, name, a0, a1, b0, b1, out_hint, t, D=12):
    """thin box spanning the quad a0-a1 (at za) / b0-b1 (at zb), outer face = local +Y (face 'up')."""
    a0, a1, b0, b1 = [np.array(v, float) for v in (a0, a1, b0, b1)]
    ma, mb = (a0 + a1) / 2, (b0 + b1) / 2
    el = mb - ma; L = np.linalg.norm(el); el /= L
    ew = (a1 - a0) + (b1 - b0); ew -= el * (ew @ el); W = np.linalg.norm(ew) / 2 * 1.0; ew /= np.linalg.norm(ew)
    W = (np.linalg.norm(a1 - a0) + np.linalg.norm(b1 - b0)) / 2
    en = np.cross(el, ew)
    if en @ np.array(out_hint, float) < 0:
        ew = -ew; en = np.cross(el, ew)
    R = np.stack([ew, en, el], 1)
    cen = (ma + mb) / 2 - en * t / 2
    obox(b, name, cen, (W + 0.05, t, L + 0.08), euler_zyx(R), 'shell', D=D, decals=BODY_DEC)


def skin_section(z):
    """right-half cross-section polyline of the top shell at z: centre -> flat top -> quarter round shoulder."""
    hw, y1 = interp(z, WU), interp(z, YT)
    pts = [(0.0, y1), (hw - RS, y1)]
    for th in (22.5, 45.0, 67.5, 90.0):
        a = math.radians(th)
        pts.append((hw - RS + RS * math.sin(a), y1 - RS + RS * math.cos(a)))
    return pts, (0.0, y1 - RS)


SKIN_Z = sorted(set(np.round(np.r_[np.arange(-6.0, 10.6, 0.55), 10.6, [z for z, _ in WU], [z for z, _ in YT]], 4)))
SKIN_Z = [z for z in SKIN_Z if -6.0 <= z <= 10.6]
for i in range(len(SKIN_Z) - 1):
    za, zb = SKIN_Z[i], SKIN_Z[i + 1]
    pa, ca = skin_section(za); pb, cb = skin_section(zb)
    for sx in (1, -1):
        sd = 'r' if sx > 0 else 'l'
        for k in range(len(pa) - 1):
            if k == 0:                     # flat top: one plate across the full width (no centre seam)
                if sx > 0:
                    skin_plate('body', f'skin_{i}_top', (-pa[1][0], pa[1][1], za), (pa[1][0], pa[1][1], za),
                               (-pb[1][0], pb[1][1], zb), (pb[1][0], pb[1][1], zb), (0, 1, 0), RS, D=14)
                continue
            A0 = (sx * pa[k][0], pa[k][1], za); A1 = (sx * pa[k + 1][0], pa[k + 1][1], za)
            B0 = (sx * pb[k][0], pb[k][1], zb); B1 = (sx * pb[k + 1][0], pb[k + 1][1], zb)
            mid = ((pa[k][0] + pa[k + 1][0]) / 2, (pa[k][1] + pa[k + 1][1]) / 2)
            out = (sx * (mid[0] - ca[0]), mid[1] - ca[1], 0.0) if k else (0.0, 1.0, 0.0)
            skin_plate('body', f'skin_{i}_{sd}{k}', A0, A1, B0, B1, out, RS, D=14 if k == 0 else 12)

# ---- front: ears (forward vision sensors), hood, grille slats, gimbal pods
for sx in (1, -1):
    s = 'r' if sx > 0 else 'l'
    # front fisheye: lens faces forward, tilted outward and upward
    bulge('body', f'fisheye_front_{s}', (sx * 3.8, 7.2, -9.0), yaw_out=24 * sx, pitch_up=18, facing='front',
          profile=[(0, 0.95), (0.15, 1.2), (0.35, 1.38), (0.6, 1.5), (1.0, 1.58), (1.7, 1.6), (2.6, 1.5),
                   (3.6, 1.3), (4.6, 1.05), (5.6, 0.8)], lens_r=0.82, lens_slices=4)
    # rear fisheye: bump just behind the front one, lens faces backward, outward and upward
    bulge('body', f'fisheye_rear_{s}', (sx * 3.75, 7.7, -1.7), yaw_out=40 * sx, pitch_up=30, facing='back',
          profile=[(0, 0.72), (0.12, 0.92), (0.3, 1.06), (0.6, 1.15), (1.2, 1.1), (2.0, 0.9), (3.0, 0.62)],
          lens_r=0.64, lens_slices=3)
    # gimbal side pods (front-arm hinge housings)
    x0, x1 = sorted([sx * 2.3, sx * 5.5])
    disc('body', f'pod_{s}', (0, 3.75, -6.0), 2.45, x0, x1, 'shell', axis='x', D=10)
    # small downward antennas ("teeth") under the nose
    obox('body', f'tooth_{s}', (sx * 2.7, -0.3, -4.4), (0.7, 1.4, 1.1), [0, 0, 0], 'shell', D=10)
    obox('body', f'tooth_{s}_tip', (sx * 2.7, -1.05, -4.4), (0.4, 0.35, 0.7), [0, 0, 0], 'shell', D=10)
box('body', 'hood', (-2.45, 7.0, -7.6), (2.45, 7.85, -5.9), 'shell', D=12)
box('body', 'hood_lip', (-2.3, 6.8, -7.85), (2.3, 7.2, -7.5), 'shell2', D=12)
for k in range(7):
    x = -1.8 + k * 0.6
    h = 7.0 - 0.18 * abs(k - 3) ** 1.5
    box('body', f'grille_{k}', (x - 0.13, 3.0, -6.9), (x + 0.13, h, -5.95), 'grille', D=12)
# gimbal yoke + roll motor (stay with body)
box('body', 'yoke_bar', (-3.05, 1.15, -8.55), (3.05, 1.8, -6.95), 'black', D=12)
for sx in (1, -1):
    x0, x1 = sorted([sx * 2.45, sx * 3.05])
    box('body', f'yoke_arm_{sx}', (x0, 1.15, -8.55), (x1, 4.3, -6.95), 'black', D=12)
    x0, x1 = sorted([sx * 2.05, sx * 2.95])
    disc('body', f'tilt_motor_{sx}', (0, 4.3, -7.75), 1.12, x0, x1, 'black', axis='x', D=12)
disc('body', 'roll_motor', (0, 0.95, 0), 0.95, -8.3, -5.6, 'black', axis='z', D=12)


# ---- camera (gimbal bone)
def cam_front(f):
    if f.face != 'north':
        return
    X, Y = f.X, f.Y
    cy = 4.35
    for i, (hw, col) in enumerate([(1.55, (36, 36, 38)), (1.42, (20, 20, 22)), (1.3, (40, 40, 43)), (1.18, (18, 18, 20)),
                                    (1.06, (34, 34, 36)), (0.95, (14, 14, 16))]):
        f.paint(rr(X, Y, -hw - 0.2, cy - hw, hw - 0.2, cy + hw, 0.45), col)
    lens(f, X, Y, -0.2, cy, 0.62, ring=(30, 30, 33), glass=(16, 14, 22), tint=(120, 80, 170))
    f.wtext('E24', 1.62, 4.75, -9.6, 0.32, (220, 220, 222), font=FONT_R)
    f.wtext('E1.7', 1.62, 4.3, -9.6, 0.32, (220, 220, 222), font=FONT_R)


def cam_top(f):
    if f.face == 'up':
        for i in range(8):
            z = -9.35 + i * 0.35
            f.paint((np.abs(f.Z - z) < 0.07) & (np.abs(f.X) < 1.5), (50, 50, 53))


box('gimbal', 'camera', (-2.0, 2.45, -9.6), (2.0, 6.25, -6.45), 'cam', D=16, decals={'north': cam_front, 'up': cam_top})
box('gimbal', 'camera_back', (-1.7, 2.8, -6.45), (1.7, 5.9, -5.95), 'cam', D=10)

# ---- arms, legs, motors, props
FM = [(-18.2, -6.7, 'fl', 'cw'), (18.2, -6.7, 'fr', 'ccw')]
RM = [(-15.68, 19.3, 'rl', 'ccw'), (15.68, 19.3, 'rr', 'cw')]
PROP_R = 12.7
BL = PROP_R - 0.8


def arm_paint(text):
    def fn(f):
        # soft vertical shading to suggest a rounded section
        if f.face in ('north', 'south'):
            g = (f.T / max(f.fh, 1e-6))[..., None]
            f.img[..., :3] *= (1.03 - 0.12 * g)
            if text and f.face == 'north':
                f.text('MINI 4 PRO', f.fw * 0.45, f.fh * 0.52, 0.62, (95, 97, 100), font=FONT_R)
        elif f.face == 'up':
            f.img[..., :3] *= 1.03
    return fn


def arm(name, a, b, y, size_y, size_z, text=False, D=10):
    a = np.array(a, float); b = np.array(b, float)
    if b[0] < a[0]:
        a, b = b, a                           # local +X always toward +world X (north face = front)
    d = b - a; L = float(np.linalg.norm(d)); dn = d / L
    yaw = norm_ang(math.degrees(math.atan2(-dn[1], dn[0])))
    c = (a + b) / 2
    obox('frame', name, (c[0], y, c[1]), (L, size_y, size_z), [0, yaw, 0], 'shell', D=D,
         decals={k: arm_paint(text) for k in ('north', 'south', 'up')})


def blade_shape(sweep):
    def fn(f):
        lx, lz = f.L[..., 0], f.L[..., 2]
        u = (lx + BL / 2) / BL
        uc = np.clip(u, 0, 1)
        hw = np.interp(uc, [0, 0.1, 0.28, 0.6, 0.85, 0.95, 0.985, 1.0], [0.42, 0.95, 1.12, 0.92, 0.68, 0.52, 0.35, 0.0])
        zc = sweep * (-0.22 + 0.6 * uc - 0.5 * uc ** 2)
        m = (u >= 0) & (u <= 1) & (np.abs(lz - zc) < hw)
        f.paint(m, (30, 30, 32, 255))
        lead = m & ((lz - zc) * sweep > hw - 0.1)
        f.paint(lead, (52, 52, 55, 255))
        f.paint(m & (u > 0.955), (242, 120, 38, 255))
        if f.face == 'down':
            f.paint(m & (u < 0.955), (40, 40, 43, 255))
    return fn


def bell_paint(cx, cz):
    def fn(f):
        dx, dz = f.X - cx, f.Z - cz
        r = np.sqrt(dx * dx + dz * dz); th = np.arctan2(dz, dx)
        if f.face == 'up':
            f.paint((r > 0.5) & (r < 0.98), (176, 116, 62))
            f.paint((r > 0.5) & (r < 0.98) & (np.abs(((th * 6 / math.pi) % 1.0) - 0.5) < 0.18), (60, 60, 64))
            f.paint(r > 1.0, (90, 92, 96))
        elif f.face != 'down':
            f.paint((np.sin(th * 12) > 0.55) & (f.Y > f.c['frm'][1] + 0.25) & (f.Y < f.c['to'][1] - 0.2), (160, 105, 55))
    return fn


def hub_paint(cx, cz):
    def fn(f):
        if f.face == 'up':
            dx, dz = f.X - cx, f.Z - cz
            for s in (1, -1):
                f.paint((dx - s * 0.28) ** 2 + dz ** 2 < 0.14 ** 2, (170, 172, 176))
                f.paint((dx - s * 0.28) ** 2 + dz ** 2 < 0.06 ** 2, (60, 60, 62))
            f.paint(dx ** 2 + dz ** 2 < 0.1 ** 2, (90, 90, 94))
    return fn


def motor_set(cx, cz, tag, spin, ybase, front):
    mb = bone(f'motor_{tag}', (cx, ybase, cz), 'drone')
    # arm-end motor mount (light grey cylinder)
    if front:
        disc(mb, f'mount_{tag}', (cx, 0, cz), 1.5, ybase - 1.3, ybase + 0.45, 'shell', axis='y', D=10)
    else:
        disc(mb, f'mount_{tag}', (cx, 0, cz), 1.55, ybase - 2.25, ybase + 0.45, 'shell', axis='y', D=10)
        disc(mb, f'foot_{tag}', (cx, 0, cz + 0.2), 1.25, ybase - 2.45, ybase - 2.2, 'rubber', axis='y', D=10)
    disc(mb, f'bell_{tag}', (cx, 0, cz), 1.15, ybase + 0.45, ybase + 1.55, 'bell', axis='y',
         decals={k: bell_paint(cx, cz) for k in ('up', 'north', 'south', 'east', 'west')}, D=16)
    pb = bone(f'prop_{tag}', (cx, ybase + 1.75, cz), mb)
    disc(pb, f'hub_{tag}', (cx, 0, cz), 0.62, ybase + 1.55, ybase + 2.0, 'black', axis='y',
         decals={'up': hub_paint(cx, cz)}, D=16, sides=8)
    sweep = 1.0 if spin == 'cw' else -1.0
    pitch = 9.0 * sweep
    base = {'fl': 68, 'fr': -68, 'rl': -39, 'rr': 39}[tag]
    for kb in range(2):
        yb = norm_ang(base + 180 * kb)
        R = rotmat([pitch, yb, 0])
        cen = np.array([cx, ybase + 1.78, cz]) + R @ np.array([0.8 + BL / 2, 0, 0])
        obox(pb, f'blade_{tag}_{kb}', cen, (BL, 0.07, 2.9), [pitch, yb, 0], 'clear', D=12,
             decals={'up': blade_shape(sweep), 'down': blade_shape(sweep)})


FRONT_Y = 4.65
for cx, cz, tag, spin in FM:
    sx = 1 if cx > 0 else -1
    arm(f'arm_{tag}', (sx * 5.0, -5.6), (cx, cz), FRONT_Y, 1.45, 1.7, text=(tag == 'fl'))
    # landing leg: flat plate under the arm end, splayed outward
    top = np.array([sx * 16.5, FRONT_Y - 0.4, cz])
    ang = sx * 8.0
    R = rotmat([0, 0, ang])
    Lg = 8.6
    obox('frame', f'leg_{tag}', top + R @ np.array([0, -Lg * 0.27, 0]), (2.3, Lg * 0.54, 1.3), [0, 0, ang], 'shell', D=10,
         decals={'north': arm_paint(False)})
    obox('frame', f'leg_{tag}_lower', top + R @ np.array([sx * 0.15, -Lg * 0.75, 0]), (1.7, Lg * 0.5, 1.1), [0, 0, ang],
         'shell', D=10, decals={'north': arm_paint(False)})
    obox('frame', f'leg_{tag}_foot', top + R @ np.array([sx * 0.15, -Lg - 0.2, 0]), (1.9, 0.45, 1.3), [0, 0, ang], 'rubber', D=10)
    motor_set(cx, cz, tag, spin, FRONT_Y + 0.85, True)

REAR_Y = 1.75
for cx, cz, tag, spin in RM:
    sx = 1 if cx > 0 else -1
    # hinge housing on the body flank
    x0, x1 = sorted([sx * 5.6, sx * 6.6])
    box('body', f'rear_hinge_{tag}', (x0, 0.4, 6.2), (x1, 3.2, 8.75), 'shell', D=10)
    arm(f'arm_{tag}', (sx * 6.2, 8.0), (cx, cz), REAR_Y, 1.35, 1.55)
    motor_set(cx, cz, tag, spin, REAR_Y + 0.75, False)



# ============================================================================ MERGE: Mini 4 Pro battery item model
def merge_battery(bat_dir):
    """copy the battery item geometry (geo.json) + baked face textures into bone 'battery' (scale 0.5, turned 180 deg)."""
    geo = json.load(open(os.path.join(bat_dir, 'geo', 'mini4pro_battery.geo.json'), encoding='utf-8'))['minecraft:geometry'][0]
    btex = np.asarray(Image.open(os.path.join(bat_dir, 'textures', 'mini4pro_battery.png')).convert('RGBA'))
    yoff = -min(c['origin'][1] for b in geo['bones'] for c in b.get('cubes', []))   # item is exported centred
    DS = 3                     # 30 texels / item px -> 60 / drone px before, 20 / drone px after downsampling
    s = BAT_S

    def P(p):                  # battery Blockbench point -> drone Blockbench point (180 deg about Y)
        return [-s * p[0], s * (p[1] + yoff) + BAT_Y, -s * p[2] + BAT_Z]
    rename = {'battery': 'battery', 'body': 'bat_body', 'collar': 'bat_collar',
              'button_l': 'bat_button_r', 'button_r': 'bat_button_l'}    # the 180 deg turn swaps the sides
    swap = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'up', 'down': 'down'}
    n = 0
    for b in geo['bones']:
        if b['name'] == 'root':
            continue
        pv = b['pivot']
        parent = 'drone' if b.get('parent') in (None, 'root') else rename[b['parent']]
        bone(rename[b['name']], P([-pv[0], pv[1], pv[2]]), parent)
        for c in b.get('cubes', []):
            o, sz = c['origin'], c['size']
            frm = [-(o[0] + sz[0]), o[1], o[2]]; to = [frm[i] + sz[i] for i in range(3)]
            nf, nt = P(to), P(frm)
            frm2 = [min(nf[i], nt[i]) for i in range(3)]; to2 = [max(nf[i], nt[i]) for i in range(3)]
            if 'rotation' in c:
                cp = c['pivot']; org = P([-cp[0], cp[1], cp[2]])
                r = c['rotation']; rb = [-r[0], -r[1], r[2]]
                rot = [-rb[0], rb[1], -rb[2]]
            else:
                org, rot = frm2, [0, 0, 0]
            pre = {}
            for fname, fd in c['uv'].items():
                (u, v), (w, h) = fd['uv'], fd['uv_size']
                ua, ub = sorted([u, u + w]); va, vb = sorted([v, v + h])
                reg = btex[int(round(va)):int(round(vb)), int(round(ua)):int(round(ub))]
                nw, nh = max(1, int(round(reg.shape[1] / DS))), max(1, int(round(reg.shape[0] / DS)))
                reg = np.asarray(Image.fromarray(reg, 'RGBA').resize((nw, nh), Image.BOX))
                tf = swap[fname]
                if tf in ('up', 'down'):
                    reg = reg[::-1, ::-1]
                pre[tf] = np.ascontiguousarray(reg)
            cb = box(rename[b['name']], 'bat_%d' % n, frm2, to2, 'shell', origin=org, rot=rot)
            cb['pre'] = pre
            n += 1
    print('merged battery cubes', n)


merge_battery(BAT_DIR)
BAT_BONES = {n for n in ORDER if n == 'battery' or n.startswith('bat_')}
print('cubes', len(CUBES))

# ============================================================================ ground contact + rest pitch
def world_corners(c):
    f, t = np.array(c['frm']), np.array(c['to'])
    pts = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
    R = rotmat(c['rot']); o = np.array(c['origin'])
    return (pts - o) @ R.T + o


front_bottom = min(world_corners(c)[:, 1].min() for c in CUBES if c['name'].startswith('leg_f') and 'foot' in c['name'])
rear_bottom = min(world_corners(c)[:, 1].min() for c in CUBES if c['name'].startswith('foot_r'))
ZF, ZR = FM[0][1], RM[0][1]
PHI = math.degrees(math.atan2(rear_bottom - front_bottom, ZR - ZF))
_piv = np.array([0.0, front_bottom, ZF]); _R = rotmat([PHI, 0, 0])
_drone_bones = {n for n in ORDER if n != 'root' and n not in BAT_BONES}
_ys = [((world_corners(c) - _piv) @ _R.T + _piv)[:, 1].min() for c in CUBES if c['bone'] in _drone_bones]
SHIFT = np.array([0.0, -min(_ys), 0.0])
BONES['drone']['pivot'] = [0.0, front_bottom, ZF]       # (pre-shift coords)
BONES['drone']['rot'] = [round(PHI, 3), 0, 0]           # Blockbench convention: +X tilts the nose up
print('front_bottom %.3f rear_bottom %.3f pitch %.2f deg' % (front_bottom, rear_bottom, PHI))

# ============================================================================ BAKE
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
    kx, ky = W_ / max(fw, 1e-6), H_ / max(fh, 1e-6)
    S = (np.arange(W_) + 0.5)[None, :].repeat(H_, 0) / kx
    T = (np.arange(H_) + 0.5)[:, None].repeat(W_, 1) / ky
    Pl = face_local(c, face, S, T)
    o = np.array(c['origin']); R = rotmat(c['rot'])
    P = (Pl - o) @ R.T + o
    Lc = Pl - (np.array(c['frm']) + np.array(c['to'])) / 2
    col, noise = MATS[c['mat']]
    img = np.zeros((H_, W_, 4), float)
    if col is not None:
        img[..., :3] = col; img[..., 3] = 255
    fc = F(img, P, Lc, S, T, fw, fh, (kx + ky) / 2, face, c)
    fc.n = R @ np.array({'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0),
                         'up': (0, 1, 0), 'down': (0, -1, 0)}[face], float)
    if face in c['decals']:
        c['decals'][face](fc)
    small = img.reshape(ph, SS, pw, SS, 4).mean((1, 3))
    small[..., 3] = np.where(small[..., 3] > 127, 255, 0)
    if col is not None:
        small[..., :3] += RNG.normal(0, noise, (ph, pw, 1))
    return np.clip(small, 0, 255).astype(np.uint8)


regions, shared, face_tex = [], {}, {}
for c in CUBES:
    if c.get('pre') is not None:              # pre-baked faces merged from the battery item model
        for face in FACES:
            pf = c['pre'].get(face)
            if pf is None:
                face_tex[(c['uuid'], face)] = None
                continue
            regions.append(pf)
            face_tex[(c['uuid'], face)] = len(regions) - 1
        continue
    for face in FACES:
        if face in c['hidden']:
            face_tex[(c['uuid'], face)] = None
            continue
        fw, fh = face_dims(c, face)
        key = None
        if MATS[c['mat']][0] is None and face not in c['decals']:
            key = ('clear',)
        elif face not in c['decals']:
            key = ('plain', c['mat'], round(fw * c['D']), round(fh * c['D']))
        elif c['name'].startswith('blade_'):
            key = ('blade', c['name'].split('_')[1], face)
        if key is not None and key in shared:
            face_tex[(c['uuid'], face)] = shared[key]; continue
        arr = np.zeros((2, 2, 4), np.uint8) if key == ('clear',) else bake_face(c, face)
        regions.append(arr)
        face_tex[(c['uuid'], face)] = len(regions) - 1
        if key is not None:
            shared[key] = len(regions) - 1

order = sorted(range(len(regions)), key=lambda i: (-regions[i].shape[0], -regions[i].shape[1]))
ATW = int(os.environ.get('ATW', 2048))
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
    i = face_tex[(c['uuid'], face)]
    if i is None:
        return None
    px, py = pos[i]; h, w = regions[i].shape[:2]
    return [px, py, px + w, py + h]


# ============================================================================ ANIMATIONS (bedrock values; added on top of rest pose)
LEVEL = round(PHI, 3)     # bedrock rest rotation of 'drone' is -PHI, +PHI levels it
SPIN = {'fl': -1, 'fr': 1, 'rl': 1, 'rr': -1}
BAT_OUT = [0, 1.6, 15.0]     # battery start/end offset behind & slightly above the tail (bedrock values, model px)
ANIMS = {
    'animation.mini4pro.idle': dict(loop='loop', length=1.0, bones={'root': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    'animation.mini4pro.fly': dict(loop='loop', length=1.0, bones={
        'drone': {
            'rotation': [(0.0, [LEVEL, 0, 0], 'catmullrom'), (0.25, [LEVEL + 0.6, 0, 0.8], 'catmullrom'),
                         (0.75, [LEVEL - 0.6, 0, -0.8], 'catmullrom'), (1.0, [LEVEL, 0, 0], 'catmullrom')],
            'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 0.45, 0], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom')],
        },
        **{f'prop_{t}': {'rotation': [(0.0, [0, 0, 0], 'linear'), (1.0, [0, 1800 * s, 0], 'linear')]} for t, s in SPIN.items()},
    }),
    'animation.mini4pro.takeoff': dict(loop='hold', length=0.6, bones={
        'drone': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.6, [LEVEL, 0, 0], 'catmullrom')]},
    }),
    'animation.mini4pro.gimbal_down': dict(loop='hold', length=0.8, bones={
        'gimbal': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.8, [90, 0, 0], 'catmullrom')]}}),
    'animation.mini4pro.gimbal_forward': dict(loop='hold', length=0.8, bones={
        'gimbal': {'rotation': [(0.0, [90, 0, 0], 'catmullrom'), (0.8, [0, 0, 0], 'catmullrom')]}}),
    # ---- battery (bone 'battery' is hidden by default; these animations drive position / scale)
    'animation.mini4pro.battery_empty': dict(loop='hold', length=0.05, bones={
        'battery': {'scale': [(0.0, [0, 0, 0], 'linear')], 'position': [(0.0, BAT_OUT, 'linear')]}}),
    'animation.mini4pro.battery_installed': dict(loop='hold', length=0.05, bones={
        'battery': {'scale': [(0.0, [1, 1, 1], 'linear')], 'position': [(0.0, [0, 0, 0], 'linear')]}}),
    'animation.mini4pro.battery_insert': dict(loop='hold', length=1.6, bones={
        'battery': {'scale': [(0.0, [1, 1, 1], 'linear')],
                    'position': [(0.0, BAT_OUT, 'catmullrom'), (0.45, [0, 0.9, 9.0], 'catmullrom'),
                                 (0.85, [0, 0.05, 2.2], 'catmullrom'), (1.15, [0, 0, 0.5], 'catmullrom'),
                                 (1.3, [0, 0, -0.06], 'catmullrom'), (1.4, [0, 0, 0], 'catmullrom')]},
        # both push buttons are squeezed while the battery is pushed home, then click back out
        'bat_button_r': {'position': [(0.0, [0, 0, 0], 'linear'), (1.05, [0, 0, 0], 'linear'), (1.18, [0.16, 0, 0], 'linear'),
                                      (1.3, [0.16, 0, 0], 'linear'), (1.38, [0, 0, 0], 'linear')]},
        'bat_button_l': {'position': [(0.0, [0, 0, 0], 'linear'), (1.05, [0, 0, 0], 'linear'), (1.18, [-0.16, 0, 0], 'linear'),
                                      (1.3, [-0.16, 0, 0], 'linear'), (1.38, [0, 0, 0], 'linear')]},
    }),
    'animation.mini4pro.battery_remove': dict(loop='hold', length=1.4, bones={
        'battery': {'scale': [(0.0, [1, 1, 1], 'linear'), (1.3, [1, 1, 1], 'linear'), (1.32, [0, 0, 0], 'linear')],
                    'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.25, [0, 0, 0], 'catmullrom'),
                                 (0.55, [0, 0, 1.6], 'catmullrom'), (0.95, [0, 0.4, 8.0], 'catmullrom'),
                                 (1.3, BAT_OUT, 'catmullrom')]},
        'bat_button_r': {'position': [(0.0, [0, 0, 0], 'linear'), (0.12, [0.16, 0, 0], 'linear'), (0.5, [0.16, 0, 0], 'linear'),
                                      (0.6, [0, 0, 0], 'linear')]},
        'bat_button_l': {'position': [(0.0, [0, 0, 0], 'linear'), (0.12, [-0.16, 0, 0], 'linear'), (0.5, [-0.16, 0, 0], 'linear'),
                                      (0.6, [0, 0, 0], 'linear')]},
    }),
}


# ============================================================================ EXPORT
def r4(v):
    return [round(float(a), 4) for a in v]


def sh(v):
    return list(np.array(v, float) + SHIFT)


elements = []
for c in CUBES:
    faces = {}
    for f in FACES:
        u = face_uv(c, f)
        faces[f] = {'uv': u, 'texture': 0} if u is not None else {'uv': [0, 0, 0, 0], 'texture': None}
    el = {'name': c['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default',
          'allow_mirror_modeling': True, 'from': r4(sh(c['frm'])), 'to': r4(sh(c['to'])), 'autouv': 0, 'color': 0,
          'origin': r4(sh(c['origin'])), 'faces': faces, 'type': 'cube', 'uuid': c['uuid'],
          'visibility': c['bone'] not in BAT_BONES}
    if any(abs(a) > 1e-6 for a in c['rot']):
        el['rotation'] = r4(c['rot'])
    elements.append(el)


def outliner_node(name):
    b = BONES[name]
    node = {'name': name, 'origin': r4(sh(b['pivot'])), 'color': 0, 'uuid': b['uuid'], 'export': True,
            'mirror_uv': False, 'isOpen': name in ('root', 'drone'), 'locked': False, 'visibility': name not in BAT_BONES,
            'autouv': 0,
            'children': [c['uuid'] for c in b['cubes']]}
    if any(abs(a) > 1e-6 for a in b['rot']):
        node['rotation'] = r4(b['rot'])
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
    'name': MODEL, 'model_identifier': MODEL, 'geckolib_model_type': 'entity',
    'visible_box': [4, 2, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [],
    'unhandled_root_fields': {}, 'resolution': {'width': ATW, 'height': ATH},
    'elements': elements, 'outliner': [outliner_node('root')],
    'textures': [{'path': '', 'name': f'{MODEL}.png', 'folder': 'entity', 'namespace': '', 'id': '0', 'width': ATW,
                  'height': ATH, 'uv_width': ATW, 'uv_height': ATH, 'particle': False, 'use_as_default': False,
                  'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto',
                  'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
                  'visible': True, 'internal': True, 'saved': False, 'uuid': uid(), 'relative_path': '',
                  'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
    'animations': bb_anims,
}
json.dump(bbmodel, open(os.path.join(OUT, f'{MODEL}.bbmodel'), 'w', encoding='utf-8'), separators=(',', ':'))

geo_bones = []
for name in ORDER:
    b = BONES[name]; p = sh(b['pivot'])
    gb = {'name': name}
    if b['parent']:
        gb['parent'] = b['parent']
    gb['pivot'] = r4([-p[0], p[1], p[2]])
    if any(abs(a) > 1e-6 for a in b['rot']):
        gb['rotation'] = r4([-b['rot'][0], -b['rot'][1], b['rot'][2]])
    cubes = []
    for c in b['cubes']:
        frm, to, org = sh(c['frm']), sh(c['to']), sh(c['origin'])
        size = [to[i] - frm[i] for i in range(3)]
        cube = {'origin': r4([-to[0], frm[1], frm[2]]), 'size': r4(size)}
        if any(abs(a) > 1e-6 for a in c['rot']):
            cube['pivot'] = r4([-org[0], org[1], org[2]])
            cube['rotation'] = r4([-c['rot'][0], -c['rot'][1], c['rot'][2]])
        uv = {}
        for f in FACES:
            u = face_uv(c, f)
            if u is None:
                continue
            w, h = u[2] - u[0], u[3] - u[1]
            uv[f] = ({'uv': [u[0] + w, u[1] + h], 'uv_size': [-w, -h]} if f in ('up', 'down')
                     else {'uv': [u[0], u[1]], 'uv_size': [w, h]})
        cube['uv'] = uv
        cubes.append(cube)
    if cubes:
        gb['cubes'] = cubes
    geo_bones.append(gb)
geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': f'geometry.{MODEL}', 'texture_width': ATW, 'texture_height': ATH,
                    'visible_bounds_width': 5, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, 0.5, 0]},
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
            bj[bn][ch] = {repr(float(t)): ({'post': v, 'lerp_mode': ip} if ip == 'catmullrom' else v) for t, v, ip in keys}
    e['bones'] = bj
    aj['animations'][an] = e
json.dump(aj, open(os.path.join(OUT, f'{MODEL}.animation.json'), 'w', encoding='utf-8'), indent=1)
json.dump({'pitch_deg': PHI, 'shift_y': float(SHIFT[1]), 'cubes': len(CUBES), 'atlas': [ATW, ATH]},
          open(os.path.join(OUT, 'meta.json'), 'w'))
print('done')
