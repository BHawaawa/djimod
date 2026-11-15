# -*- coding: utf-8 -*-
"""Procedural generator: DJI Matrice 400 (经纬 M400) -> Blockbench GeckoLib entity (bbmodel + geo + animation + texture).

Scale: 1 model px = 15 mm.  Front = north (-Z), +X = drone's right side, +Y up, origin = ground centre (skids on Y=0).
Key real dimensions used (DJI spec): unfolded 980 x 760 x 480 mm (incl. landing gear), diagonal wheelbase 1070 mm,
25-inch (635 mm) folding propellers 2510F, motors 7510, props mounted UNDER the motors (as in the reference renders).

usage: python3 gen_m400.py <out_dir>
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, obox, rrect, rrect_y, rr, norm_ang
import tb100 as TB

OUT = sys.argv[1] if len(sys.argv) > 1 else 'build_m400'
MODEL = 'm400'
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
MM = 1 / 15.0

MATS = {
    **TB.MATS,
    'shell':   ((78, 79, 83), 2.2),
    'shell2':  ((64, 65, 69), 2.0),
    'shell3':  ((88, 89, 93), 2.2),
    'carbon':  ((62, 63, 67), 0.8),
    'motor':   ((42, 42, 45), 1.0),
    'bell':    ((56, 56, 60), 1.2),
    'metal':   ((128, 130, 134), 1.0),
    'rubber':  ((30, 30, 32), 1.2),
    'glass':   ((14, 42, 40), 0.0),
    'dark':    ((24, 24, 26), 0.8),
    'clear':   (None, 0.0),
}

# ----------------------------------------------------------------------------- key dimensions (px)
Y_BOT, Y_TOR, Y_HEAD, Y_BAT = 14.7, 19.5, 29.3, 28.6       # body bottom / torso top / head top / battery top
HW_T, HW_H, HW_B = 6.0, 5.6, 5.0                          # half widths torso / head / battery
Z_HF, Z_TF, Z_HR, Z_TR, Z_BR = -17.0, -16.4, -5.0, 16.8, 17.3
Y_ARM = 20.0
DOME = (0.0, 29.6, -12.6)
# arm roots (on the body) and motor centres (x, z) for the right side; left side mirrored
ARMS = {
    'fr': dict(root=(5.0, -8.5), motor=(29.2, -19.6), spin='ccw', front=True),
    'fl': dict(root=(-5.0, -8.5), motor=(-29.2, -19.6), spin='cw', front=True),
    'rr': dict(root=(5.0, 13.6), motor=(24.8, 27.1), spin='cw', front=False),
    'rl': dict(root=(-5.0, 13.6), motor=(-24.8, 27.1), spin='ccw', front=False),
}
PROP_Y, BLADE_L, BLADE_W, HUB_R = 15.35, 20.3, 4.0, 0.9
SKID_X, SKID_Y, SKID_R, SKID_Z0, SKID_Z1, LEG_Z = 18.2, 1.05, 1.0, -10.2, 16.2, 3.0


# ============================================================================ helpers
_TXT = {}


def glyphs(txt, font):
    key = (txt, font)
    if key not in _TXT:
        fnt = ImageFont.truetype(font, 160)
        bb = fnt.getbbox(txt)
        im = Image.new('L', (bb[2] - bb[0] + 8, bb[3] - bb[1] + 8), 0)
        ImageDraw.Draw(im).text((4 - bb[0], 4 - bb[1]), txt, font=fnt, fill=255)
        _TXT[key] = np.asarray(im, float) / 255.0
    return _TXT[key]


def world_text(f, A, B, txt, ca, cb, h, color, font=FONT_B, mask=None):
    """A grows to the reader's right, B grows downwards (world coords on that face)."""
    g = glyphs(txt, font)
    gh, gw = g.shape
    sc = h / (gh - 8)
    u = (A - ca) / sc + gw / 2; v = (B - cb) / sc + gh / 2
    inside = (u >= 0) & (u < gw) & (v >= 0) & (v < gh)
    m = g[np.clip(v.astype(int), 0, gh - 1), np.clip(u.astype(int), 0, gw - 1)] * inside
    if mask is not None:
        m = m * mask
    f.paint(m, color)


def lens(f, A, B, ca, cb, r, ring=(150, 152, 156), tint=(20, 46, 44)):
    d = np.sqrt((A - ca) ** 2 + (B - cb) ** 2)
    f.paint(d < r * 1.45, (20, 20, 22))
    f.paint((d < r * 1.18) & (d > r * 1.0), ring)
    g = np.clip(1 - d / r, 0, 1)
    m = d < r
    col = np.array(tint, float)[None, None, :] * (0.6 + 0.7 * g[..., None])
    f.img[..., :3] = np.where(m[..., None], col, f.img[..., :3])
    f.paint(m & (d < r * 0.35), (6, 10, 10))
    f.paint(((A - ca + r * 0.35) ** 2 + (B - cb + r * 0.35) ** 2) < (r * 0.18) ** 2, (200, 225, 220))


def mesh(f, A, B, mask, pitch=0.22, col=(22, 22, 24)):
    f.paint(mask, (34, 34, 36))
    holes = mask & (((A / pitch) % 1) < 0.55) & (((B / pitch) % 1) < 0.55)
    f.paint(holes, col)


def slots(f, A, B, a0, a1, b0, b1, n, axis, r=0.12, col=(20, 20, 22), fill=0.55):
    """n parallel rounded slots inside [a0,a1]x[b0,b1]; axis='a' -> slots run along A."""
    if axis == 'a':
        step = (b1 - b0) / n
        for i in range(n):
            s0 = b0 + i * step + step * (1 - fill) / 2
            f.paint(rr(A, B, a0, s0, a1, s0 + step * fill, r), col)
    else:
        step = (a1 - a0) / n
        for i in range(n):
            s0 = a0 + i * step + step * (1 - fill) / 2
            f.paint(rr(A, B, s0, b0, s0 + step * fill, b1, r), col)


def screw(f, A, B, ca, cb, r=0.13):
    d2 = (A - ca) ** 2 + (B - cb) ** 2
    f.paint(d2 < r * r, (30, 30, 32))
    f.paint(d2 < (r * 0.45) ** 2, (70, 70, 74))


def carbon(f, t, arc, period=0.42):
    """braided carbon tube: two crossing helical strand families."""
    a = (t + arc) / period; b = (t - arc) / period
    ca, cb = np.floor(a), np.floor(b)
    over = ((ca + cb) % 2) == 0
    fa, fb = a - ca, b - cb
    strand = np.where(over, np.sin(np.pi * fa), np.sin(np.pi * fb))
    shade = 0.78 + 0.32 * strand
    f.img[..., :3] *= shade[..., None]


def cyl_frame(p0, p1):
    p0 = np.array(p0, float); p1 = np.array(p1, float)
    d = p1 - p0; L = float(np.linalg.norm(d)); u = d / L
    ref = np.array([0, 1, 0]) if abs(u[1]) < 0.9 else np.array([1, 0, 0])
    v = np.cross(u, ref); v /= np.linalg.norm(v); w = np.cross(u, v)
    return p0, u, v, w, L


def cyl(b, name, p0, p1, r, mat, sides=12, D=12, decals=None, carbon_p=None):
    """regular polygon prism (apothem r) between p0 and p1 (any direction).
    decals(f, t, arc, ang) receives axial coordinate t, circumferential arc length and angle."""
    p0, u, v, w, L = cyl_frame(p0, p1)
    ctr = p0 + u * L / 2
    nb = sides // 2
    seg = 2 * r * math.tan(math.pi / sides) + 0.006
    yaw = math.degrees(math.asin(max(-1, min(1, -u[2]))))
    pz = math.degrees(math.atan2(u[1], u[0])) if abs(math.cos(math.radians(yaw))) > 1e-6 else 0.0
    vertical = abs(u[1]) > 0.999

    def dec(f):
        q = f.P - p0
        t = q @ u; a = np.arctan2(q @ w, q @ v)
        if carbon_p:
            carbon(f, t, a * r, carbon_p)
        if decals:
            decals(f, t, a * r, a)
    for k in range(nb):
        a = norm_ang(k * 360.0 / sides)
        e = 0.003 * k
        if vertical:
            obox(b, f'{name}_{k}', ctr, (2 * r, L + e, seg), [0, a, 0], mat, D=D, decals=dec)
        elif abs(u[0]) > 0.999 and abs(u[1]) < 1e-6:
            obox(b, f'{name}_{k}', ctr, (L + e, 2 * r, seg), [a, 0, 0], mat, D=D, decals=dec)
        else:
            obox(b, f'{name}_{k}', ctr, (L + e, 2 * r, seg), [a, norm_ang(yaw), norm_ang(pz)], mat, D=D, decals=dec)


def vcyl(b, name, x, z, r, y0, y1, mat, **kw):
    cyl(b, name, (x, y0, z), (x, y1, z), r, mat, **kw)


# ============================================================================ DECALS: body
def seam_diag(f, Y, Z):
    """big diagonal panel seam on both sides of the upper body (front-low -> rear-high)."""
    d = (Z + 10.8) * 0.407 - (Y - 22.6)
    f.paint(np.abs(d) < 0.05, (26, 26, 28))
    f.shade((d > 0.05) & (Y > 22.1), 0.93)


def d_head(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if nz < -0.95:                                            # flat front face
        lens(f, -X, -Y, -3.0, -27.45, 0.48)
        lens(f, X, -Y, 3.0, -27.45, 0.48)
        world_text(f, -X, -Y, 'dji', 0.0, -23.0, 1.1, (160, 161, 164), FONT_B)
        for sx in (1, -1):
            screw(f, X, Y, sx * 4.2, 20.1)
            screw(f, X, Y, sx * 4.2, 28.7)
        f.paint(rr(X, Y, -1.1, 25.4, 1.1, 26.05, 0.2), (22, 22, 24))     # forward ToF window
        f.paint(rr(X, Y, -0.95, 25.5, 0.95, 25.95, 0.15), (34, 38, 46))
    elif nz < -0.5 and abs(nx) > 0.5:                          # chamfers: tall mesh vents + aux light
        mesh(f, Z, Y, (Y > 20.6) & (Y < 26.9) & (np.abs(np.abs(X) - 5.13) < 0.27))
        m = ((np.abs(X) - 5.13) ** 2 + (Y - 28.0) ** 2) < 0.26 ** 2
        f.paint(m, (232, 218, 160)); f.emit(m, (250, 240, 200))
    elif abs(nx) > 0.95:                                       # sides
        sx = np.sign(nx)
        lens(f, sx * Z, -Y, sx * -14.6, -27.5, 0.45)          # side fisheyes
        lens(f, sx * Z, -Y, sx * -11.2, -27.5, 0.45)
        slots(f, Z, Y, -15.6, -13.0, 20.8, 26.0, 5, 'b', r=0.08, fill=0.45)
        seam_diag(f, Y, Z)
        f.paint(np.abs(Z - (-9.4)) < 0.04, (40, 40, 43))
        f.paint(((Z + 16.1) ** 2 + (Y - 28.6) ** 2) < 0.12 ** 2, (230, 120, 30))
    elif ny > 0.95:                                            # top
        f.paint(np.abs(Z - (Z_HR + 0.15)) < 0.05, (34, 34, 36))
        f.paint(rr(X, Z, -1.2, -9.4, 1.2, -8.0, 0.3), (44, 44, 47))
        slots(f, X, Z, -1.0, 1.0, -9.25, -8.15, 4, 'a', r=0.05, fill=0.4)


def d_torso(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    f.paint(np.abs(Y - (Y_TOR - 0.06)) < 0.05, (30, 30, 32))      # waist seam
    if nz < -0.95:                                            # front of the waist
        for sx in (1, -1):
            screw(f, X, Y, sx * 1.4, 15.4); screw(f, X, Y, sx * 4.0, 15.4)
        f.paint(rr(X, Y, -0.45, 16.6, 0.45, 17.1, 0.15), (235, 225, 190))
        f.emit(rr(X, Y, -0.45, 16.6, 0.45, 17.1, 0.15), (245, 235, 200))
    elif nz < -0.5 and abs(nx) > 0.5:
        mesh(f, Z, Y, (Y > 15.3) & (Y < 18.9) & (np.abs(np.abs(X) - 5.13) < 0.27))
    elif abs(nx) > 0.95:
        slots(f, Z, Y, 3.8, 11.0, 15.3, 18.7, 4, 'a', r=0.18, fill=0.5)
        f.paint(rr(Z, Y, 3.6, 15.1, 11.2, 18.9, 0.3) & ~rr(Z, Y, 3.7, 15.2, 11.1, 18.8, 0.25), (48, 48, 51))
        for zz in (-12.0, 1.5, 13.0):
            screw(f, Z, Y, zz, 15.3)
    elif nz > 0.95:                                            # rear
        slots(f, X, Y, -3.6, 3.6, 15.3, 17.7, 4, 'a', r=0.13, fill=0.5)
        f.paint(rr(X, Y, -3.8, 15.1, 3.8, 17.9, 0.3) & ~rr(X, Y, -3.7, 15.2, 3.7, 17.8, 0.25), (44, 44, 47))
        world_text(f, X, -Y, 'MATRICE', 0.0, -18.65, 0.6, (165, 166, 170), FONT_C)
        for sx in (1, -1):
            screw(f, X, Y, sx * 4.6, 15.3)
    elif ny < -0.95:                                           # bottom
        for z0, z1 in ((-9.5, -3.5), (5.5, 12.0)):
            slots(f, X, Z, -3.8, 3.8, z0, z1, 7, 'a', r=0.12, fill=0.5)
            f.paint(rr(X, Z, -4.0, z0 - 0.2, 4.0, z1 + 0.2, 0.3) & ~rr(X, Z, -3.9, z0 - 0.1, 3.9, z1 + 0.1, 0.25),
                    (44, 44, 47))
        lens(f, X, Z, -2.4, -1.4, 0.5)
        lens(f, X, Z, 2.4, -1.4, 0.5)
        f.paint(rr(X, Z, -0.8, -1.9, 0.8, -0.9, 0.25), (20, 24, 40))              # downward 3D ToF
        f.paint(((X) ** 2 + (Z - 14.2) ** 2) < 0.75 ** 2, (40, 40, 42))
        f.paint(((X) ** 2 + (Z - 14.2) ** 2) < 0.55 ** 2, (250, 214, 90))         # bottom aux light
        f.emit(((X) ** 2 + (Z - 14.2) ** 2) < 0.55 ** 2, (250, 220, 120))
        for sx in (1, -1):
            for zz in (-14.5, 15.6):
                screw(f, X, Z, sx * 4.4, zz)
            f.paint(rr(X, Z, sx * 4.4 - 0.6, 0.5, sx * 4.4 + 0.6, 4.5, 0.2), (36, 36, 38))   # E-port V2 pads
            for k in range(4):
                f.paint(rr(X, Z, sx * 4.4 - 0.35, 0.9 + k * 0.9, sx * 4.4 + 0.35, 1.3 + k * 0.9, 0.08), (150, 128, 70))


def d_deck(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nx) > 0.95:
        sx = np.sign(nx)
        seam_diag(f, Y, Z)
        slots(f, Z, Y, -3.4, 0.4, 23.2, 25.4, 3, 'a', r=0.1, fill=0.45)
        f.paint(rr(Z, Y, -3.4, 20.6, 0.4, 22.4, 0.15), (38, 38, 41))               # E-port cover
        world_text(f, -sx * Z, -Y, 'E-Port', sx * 1.5, -21.5, 0.42, (128, 129, 132), FONT_C)
    elif ny > 0.95 and Y.min() > Y_BAT - 0.05:
        for i in range(10):
            for j in range(5):
                x0 = -2.9 + i * 0.6; z0 = -3.9 + j * 0.9
                f.paint(rr(X, Z, x0, z0, x0 + 0.38, z0 + 0.6, 0.1), (44, 44, 47))
        f.paint(rr(X, Z, -3.3, -4.3, 3.3, 0.55, 0.35) & ~rr(X, Z, -3.2, -4.2, 3.2, 0.45, 0.3), (52, 52, 55))
        f.paint(np.abs(Z - 4.45) < 0.05, (30, 30, 32))


BAT_PIV = (28.6, 4.5)                       # battery hinge line (y, z): flat deck ends, slope begins
BAT_ANG = 34.0                              # slope of the battery top (deg, rear end down)
BAT_L, BAT_T = 14.96, 5.6                   # length along the slope / thickness
_cb, _sb = math.cos(math.radians(BAT_ANG)), math.sin(math.radians(BAT_ANG))


def bat_local(Y, Z):
    """world (y, z) -> battery-local (v, s): s along the slope from the hinge, v = 0 on the top surface, negative below."""
    dy, dz = Y - BAT_PIV[0], Z - BAT_PIV[1]
    return _cb * dy + _sb * dz, -_sb * dy + _cb * dz


def rotate_new(start, pivot, ang):
    """rotate cubes CUBES[start:] about the X axis through pivot=(y, z) (rear end down for ang > 0)."""
    Rt = G.rotmat([ang, 0, 0]); pv = np.array([0.0, pivot[0], pivot[1]])
    for c in G.CUBES[start:]:
        R = Rt @ G.rotmat(c['rot'])
        o = np.array(c['origin']); o2 = Rt @ (o - pv) + pv
        c['frm'] = list(np.array(c['frm']) + o2 - o); c['to'] = list(np.array(c['to']) + o2 - o); c['origin'] = list(o2)
        b_ = math.degrees(math.asin(max(-1, min(1, -R[2, 0]))))
        a_ = math.degrees(math.atan2(R[2, 1], R[2, 2])); c_ = math.degrees(math.atan2(R[1, 0], R[0, 0]))
        c['rot'] = [norm_ang(a_), norm_ang(b_), norm_ang(c_)]


BAY_HW = 4.25                                   # bay inner half width (battery body is +-4.1, ribs to 4.2)
BAY_S0, BAY_S1 = TB.BODY_S0 - 0.05, TB.BODY_S1 + 0.05   # bay interior along the slope
BAY_V0 = TB.BODY_V0 - 0.05                      # bay floor (top surface)
WALL_F, WALL_B, FLOOR_T, LEDGE_T = 0.8, 0.65, 0.35, 0.35


def nrm_local(n):
    return n[0], _cb * n[1] + _sb * n[2], -_sb * n[1] + _cb * n[2]


def to_local(P):
    v, sl = bat_local(P[..., 1], P[..., 2])
    return P[..., 0], v, sl


def d_bay(f):
    """inside of the battery bay (rotated plates): dark moulding, receptacle on the floor."""
    x, v, sl = to_local(f.P)
    nx, nv, ns = nrm_local(f.n)
    f.paint(np.ones_like(x, bool), (46, 47, 50))
    if nv > 0.95 and v.max() < BAY_V0 + 0.02:                     # floor: power receptacle + guide grooves
        f.paint(rr(x, sl, -2.5, 3.6, 2.5, 8.8, 0.4), (24, 24, 26))
        for k in range(10):
            sa = 4.1 + k * 0.44
            f.paint(rr(x, sl, -1.85, sa + 0.04, -0.45, sa + 0.16, 0.04), (170, 146, 84))
            f.paint(rr(x, sl, 0.45, sa + 0.04, 1.85, sa + 0.16, 0.04), (170, 146, 84))
        for sx in (1, -1):
            f.paint(rr(x, sl, sx * 3.2 - 0.3, 1.4, sx * 3.2 + 0.3, 11.0, 0.12), (34, 34, 36))
        world_text(f, x, sl, 'TB100', 0.0, 10.2, 0.55, (70, 71, 75), FONT_C)
    elif nv > 0.95:                                               # ledges / rims the lid rests on
        f.paint(np.ones_like(x, bool), (64, 65, 69))
        f.paint(np.abs(np.abs(x) - 4.62) < 0.08, (36, 36, 38))   # rubber gasket
        f.paint((np.abs(sl - 0.4) < 0.08) | (np.abs(sl - 13.6) < 0.08), (36, 36, 38))
    elif abs(ns) > 0.95:
        f.paint(rr(x, v, -2.0, -3.6, 2.0, -2.6, 0.2), (36, 36, 38))
    f.paint(np.abs(v - (BAY_V0 + 0.05)) < 0.04, (34, 34, 36))


def d_fill(f):
    """axis-aligned fuselage fill around the rotated bay."""
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nx) > 0.95 and np.abs(X).min() > 4.9:
        seam_diag(f, Y, Z)
        if Y.max() < 19.6:
            d_torso(f)
    elif abs(nx) > 0.95:
        f.paint(np.ones_like(X, bool), (46, 47, 50))
    elif ny > 0.95 and Y.min() > Y_BAT - 0.05:
        d_deck(f)
    elif nz > 0.95 and Z.min() > Z_TR - 0.05:
        d_torso(f)


def d_ear(f):
    X, Y, Z = f.X, f.Y, f.Z
    if f.n[1] > 0.95:
        sx = 1 if X.mean() > 0 else -1
        lens(f, X, Z, sx * 4.25, -15.8, 0.45)
    elif f.n[2] < -0.95:
        sx = 1 if X.mean() > 0 else -1
        lens(f, X, -Y, sx * 4.25, -29.85, 0.3)


def d_dome(f):
    X, Y, Z = f.X, f.Y, f.Z
    cx, cy, cz = DOME
    q = np.stack([X - cx, Y - cy, Z - cz], -1)
    r = np.linalg.norm(q, axis=-1) + 1e-6
    n = q / r[..., None]
    L = np.array([-0.4, 0.8, -0.45]); L /= np.linalg.norm(L)
    spec = np.clip(n @ L, 0, 1) ** 24
    base = np.array([14, 44, 42], float)
    col = base[None, None, :] * (0.7 + 0.5 * n[..., 1:2]) + spec[..., None] * np.array([200, 225, 220])
    f.img[..., :3] = col
    f.paint(np.abs(Y - 30.15) < 0.05, (8, 20, 18))


def d_gimbal_port(f):
    if f.n[1] < -0.95:
        X, Z = f.X, f.Z
        d = np.sqrt(X ** 2 + (Z + 12.4) ** 2)
        f.paint(d < 1.55, (30, 30, 32))
        f.paint((d > 1.15) & (d < 1.3), (140, 142, 146))
        for k in range(8):
            a = k * math.pi / 4
            f.paint(((X - 0.7 * math.cos(a)) ** 2 + (Z + 12.4 - 0.7 * math.sin(a)) ** 2) < 0.08 ** 2, (190, 160, 90))
        f.paint(((X - 1.9) ** 2 + (Z + 12.4) ** 2) < 0.2 ** 2, (230, 110, 30))


# ============================================================================ DECALS: arms / motors / props
def d_socket(f, t, arc, ang):
    f.paint(np.abs(t - 0.15) < 0.04, (30, 30, 32))


def d_knurl(f, t, arc, ang):
    f.paint(((arc / 0.18) % 1) < 0.45, (28, 28, 30))
    f.paint(np.abs(t - 0.1) < 0.06, (80, 82, 86))
    f.paint((np.abs(t - 0.9) < 0.12) & (np.abs(arc) < 0.25), (235, 110, 30))     # orange lock mark


def d_sleeve(f, t, arc, ang):
    f.paint((np.abs(t - 0.2) < 0.04) | (np.abs(t - 2.95) < 0.04), (30, 30, 32))


def housing_dec(cx, cz, out, front):
    """vertical motor housing: status LEDs on the outward side."""
    def fn(f, t, arc, ang):
        dx, dz = f.X - cx, f.Z - cz
        rad = np.sqrt(dx * dx + dz * dz) + 1e-6
        facing = (dx * out[0] + dz * out[1]) / rad
        tang = (dx * -out[1] + dz * out[0]) / rad
        col = (70, 235, 110) if front else (255, 160, 40)
        for k in range(3):
            m = (facing > 0.7) & (np.abs(tang) < 0.16) & (np.abs(f.Y - (19.1 + 0.55 * k)) < 0.17)
            f.paint(m, col); f.emit(m, col)
        f.paint(np.abs(f.Y - 17.75) < 0.05, (24, 24, 26))
        f.paint(np.abs(f.Y - 22.45) < 0.05, (24, 24, 26))
    return fn


def bell_dec(cx, cz):
    def fn(f, t, arc, ang):
        dx, dz = f.X - cx, f.Z - cz
        a = np.degrees(np.arctan2(dz, dx))
        if abs(f.n[1]) < 0.5:
            slot = (np.abs(((a + 12) % 24) - 12) < 4) & (f.Y > 16.3) & (f.Y < 17.3)
            f.paint(slot, (22, 22, 24))
            f.paint(slot & (np.abs(((a + 12) % 24) - 12) < 1.6), (150, 92, 45))
            f.paint(np.abs(f.Y - 16.15) < 0.06, (90, 92, 96))
    return fn


def cap_top_dec(cx, cz, front):
    def fn(f, t, arc, ang):
        if f.n[1] > 0.95:
            d = np.sqrt((f.X - cx) ** 2 + (f.Z - cz) ** 2)
            f.paint((d > 1.2) & (d < 1.3), (34, 34, 36))
            if not front:
                world_text(f, f.X, f.Z, 'RTK', cx, cz, 0.55, (96, 97, 100), FONT_C)
    return fn


def hub_dec(cx, cz):
    def fn(f, t, arc, ang):
        if f.n[1] < -0.95:
            d = np.sqrt((f.X - cx) ** 2 + (f.Z - cz) ** 2)
            f.paint(d < 0.55, (70, 72, 76))
            f.paint(d < 0.3, (30, 30, 32))
    return fn


def blade_dec(sweep):
    """2510F folding blade on a thin plate (cube-local coords): x = span, z = chord."""
    def fn(f):
        lx, lz = f.L[..., 0], f.L[..., 2]
        u = (lx + BLADE_L / 2) / BLADE_L
        uc = np.clip(u, 0, 1)
        grow = np.sin(np.pi / 2 * np.clip(uc / 0.3, 0, 1))
        hw = 0.55 + 1.05 * grow - 0.85 * np.clip((uc - 0.3) / 0.7, 0, 1) ** 1.3
        hw = hw * np.sqrt(np.clip((1.0 - uc) / 0.035, 0, 1))          # rounded tip
        zc = sweep * (0.35 * np.sin(np.pi * uc) - 0.1)
        off = lz - zc
        lim = np.where(off * sweep > 0, hw * 0.8, hw * 1.2)
        m = (u >= 0) & (u <= 1) & (np.abs(off) < lim)
        f.img[..., 3] = np.where(m, 255, 0)
        base = np.array([40, 40, 43], float)
        f.img[..., :3] = np.where(m[..., None], base, f.img[..., :3])
        f.paint(m & (np.abs(off) > lim - 0.1), (58, 58, 62))                   # edges
        f.paint(m & (np.abs(off - lim * 0.15 * sweep) < lim * 0.18) & (uc < 0.85), (46, 46, 50))
        f.paint(m & (uc > 0.955), (240, 112, 28))                               # orange tip
        f.paint(m & (uc < 0.06), (60, 60, 64))                                  # folding root
        f.paint(m & (np.abs(uc - 0.03) < 0.012) & (np.abs(off) < 0.2), (150, 152, 156))
        if f.face == 'down':
            world_text(f, lx, lz, '2510F', -BLADE_L / 2 + 3.2, 0, 0.42, (86, 87, 90), FONT_C, mask=m)
    return fn


def faces(**fns):
    def fn(f):
        g = fns.get(f.face)
        if g:
            g(f)
    return fn


def d_skid_cap(f, t, arc, ang):
    pass


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('drone', (0, 0, 0), 'root')
bone('body', (0, Y_ARM, 0), 'drone')
bone('lidar', (DOME[0], DOME[1], DOME[2]), 'body')
bone('battery', (0, 28.6, 4.5), 'drone')
for _sx, _sd in ((1, 'r'), (-1, 'l')):
    _p = G.rotmat([BAT_ANG, 0, 0]) @ np.array([_sx * TB.CAP_W / 2, -0.5, sum(TB.LATCH_S) / 2])
    bone(f'bat_latch_{_sd}', (_p[0], BAT_PIV[0] + _p[1], BAT_PIV[1] + _p[2]), 'battery')
bone('gear', (0, Y_BOT, LEG_Z), 'drone')

# ---- lower body (waist / torso), head, rear deck (hollowed around the rotated battery bay)
Z_TA, Z_TB = 0.5, 14.6                          # torso: solid ahead of / behind the bay
E1 = 1.6 * math.tan(math.radians(22.5))         # rrect_y(n=1) core overhang
E2 = 1.0 * math.tan(math.radians(15.0))         # rrect_y(n=2) core overhang
Y_SLAB = 15.25
rrect_y('body', 'torso_slab', 0, (Z_HF + Z_TR) / 2, 2 * HW_H, Z_TR - Z_HF, 1.6, Y_BOT, Y_SLAB, 'shell', D=14, n=1, decals=d_torso)
rrect_y('body', 'torso_f', 0, (Z_HF + Z_TA) / 2, 2 * HW_H, Z_TA - Z_HF, 1.6, Y_SLAB, Y_TOR, 'shell', D=14, n=1, decals=d_torso)
rrect_y('body', 'torso_r', 0, (Z_TB + Z_TR) / 2, 2 * HW_H, Z_TR - Z_TB, 1.6, Y_SLAB, Y_TOR, 'shell', D=14, n=1, decals=d_torso)
rrect_y('body', 'head', 0, (Z_HF + Z_HR) / 2, 2 * HW_H, Z_HR - Z_HF, 1.6, Y_TOR, Y_HEAD, 'shell', D=16, n=1, decals=d_head)
rrect_y('body', 'deck', 0, (Z_HR - 0.2 + Z_TA) / 2, 2 * (HW_H - 0.2), Z_TA - Z_HR + 0.2, 1.0, Y_TOR - 0.1, Y_BAT, 'shell',
        D=14, n=2, decals=d_deck)
for sx, sd in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted([sx * 5.0, sx * HW_H])
    box('body', f'torso_side_{sd}', (xa, Y_SLAB, Z_TA - 1.6 + E1), (xb, Y_TOR, Z_TB + 1.6 - E1), 'shell', D=14, decals=d_torso)
    xa, xb = sorted([sx * 5.0, sx * (HW_H - 0.2)])
    box('body', f'deck_side_{sd}', (xa, Y_TOR - 0.1, Z_TA - 1.0 + E2), (xb, Y_BAT, BAT_PIV[1]), 'shell', D=14, decals=d_deck)
    box('body', f'deck2_side_{sd}', (xa, Y_TOR - 0.1, BAT_PIV[1]), (xb, 22.3, 9.2), 'shell', D=14, decals=d_deck)


def fill_allowed(z, y, band_a):
    v, sl = bat_local(y, z)
    ok = (y <= Y_BAT + 1e-6) & (y >= Y_SLAB - 1e-6)
    ok &= np.where(sl <= 0, True, np.where(sl <= TB.CAP_L, v <= -TB.CAP_T + 1e-6, y <= Y_TOR + 1e-6))
    ok &= ~((z > Z_TB + 1e-6) & (y < Y_TOR - 1e-6))           # rear torso block already there
    if band_a:
        ok &= ~((sl > BAY_S0 + 1e-4) & (sl < BAY_S1 - 1e-4) & (v > BAY_V0 + 1e-4) & (v < -TB.CAP_T - 1e-4))
    return ok


def staircase(tag, x0, x1, band_a):
    edges = list(np.linspace(Z_TA, Z_TB, 41)) + list(np.linspace(Z_TB, Z_TR, 7)[1:])
    ys = np.arange(Y_SLAB, Y_BAT + 1e-6, 0.01)
    cols = []
    for za, zb in zip(edges[:-1], edges[1:]):
        ok = np.ones_like(ys, bool)
        for z in np.linspace(za + 1e-3, zb - 1e-3, 5):
            ok &= fill_allowed(np.full_like(ys, z), ys, band_a)
        ivs, i = [], 0
        while i < len(ys):
            if ok[i]:
                j = i
                while j + 1 < len(ys) and ok[j + 1]:
                    j += 1
                if ys[j] - ys[i] > 0.02:
                    ivs.append((round(float(ys[i]), 3), round(float(ys[j]), 3)))
                i = j + 1
            else:
                i += 1
        cols.append((za, zb, ivs))
    k = 0
    i = 0
    while i < len(cols):                              # merge neighbouring columns with identical intervals
        za, zb, ivs = cols[i]
        j = i
        while j + 1 < len(cols) and cols[j + 1][2] == ivs:
            j += 1
        for (ya, yb) in ivs:
            box('body', f'fill_{tag}_{k}', (x0, ya, za), (x1, yb, cols[j][1]), 'shell', D=12, decals=d_fill)
            k += 1
        i = j + 1
    return k


nfill = staircase('a', -BAY_HW, BAY_HW, True)
for sx, sd in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted([sx * BAY_HW, sx * 5.0])
    nfill += staircase('b' + sd, xa, xb, False)
print('fill cubes', nfill)

# ---- battery bay walls (built in battery-local coordinates, then rotated onto the 34 deg slope)
_n0 = len(G.CUBES)
LY = lambda v: BAT_PIV[0] + v
LZ = lambda sl: BAT_PIV[1] + sl
box('body', 'bay_front', (-BAY_HW, LY(BAY_V0 - FLOOR_T), LZ(0.0)), (BAY_HW, LY(-TB.CAP_T), LZ(BAY_S0)), 'shell', D=14, decals=d_bay)
box('body', 'bay_back', (-BAY_HW, LY(BAY_V0 - FLOOR_T), LZ(BAY_S1)), (BAY_HW, LY(-TB.CAP_T), LZ(BAY_S1 + WALL_B)), 'shell', D=14,
    decals=d_bay)
box('body', 'bay_floor', (-BAY_HW, LY(BAY_V0 - FLOOR_T), LZ(0.0)), (BAY_HW, LY(BAY_V0), LZ(BAY_S1 + WALL_B)), 'shell', D=14,
    decals=d_bay)
box('body', 'bay_rim_back', (-BAY_HW, LY(-TB.CAP_T - LEDGE_T), LZ(BAY_S1)), (BAY_HW, LY(-TB.CAP_T), LZ(TB.CAP_L - 0.05)),
    'shell', D=14, decals=d_bay)
for sx, sd in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted([sx * BAY_HW, sx * TB.CAP_W / 2])
    box('body', f'bay_ledge_{sd}', (xa, LY(-TB.CAP_T - LEDGE_T), LZ(0.0)), (xb, LY(-TB.CAP_T), LZ(TB.CAP_L - 0.05)), 'shell',
        D=14, decals=d_bay)
    xa, xb = sorted([sx * (BAY_HW - 0.02), sx * (BAY_HW + 0.3)])          # inner side liners (clean flat bay walls)
    box('body', f'bay_side_{sd}', (xa, LY(BAY_V0 - FLOOR_T), LZ(0.0)), (xb, LY(-TB.CAP_T), LZ(BAY_S1 + WALL_B)), 'shell',
        D=14, decals=d_bay)
rotate_new(_n0, BAT_PIV, BAT_ANG)

# ---- TB100 battery (same builder as the battery item model/battery/M400), hidden by default
_n0 = len(G.CUBES)
TB.build(dict(main='battery', latch_l='bat_latch_l', latch_r='bat_latch_r'), 1.0, BAT_PIV[0], BAT_PIV[1], to_local, nrm_local,
         D=16)
rotate_new(_n0, BAT_PIV, BAT_ANG)

for sx, s in ((1, 'r'), (-1, 'l')):
    rrect_y('body', f'ear_{s}', sx * 4.25, -15.6, 2.4, 2.6, 0.6, Y_HEAD - 0.1, Y_HEAD + 0.75, 'shell3', D=20, n=2, decals=d_ear)
# lidar dome
vcyl('body', 'dome_base', DOME[0], DOME[2], 2.35, Y_HEAD - 0.05, DOME[1], 'shell2', sides=16, D=16)
_hs = [0.0, 0.55, 0.95, 1.25, 1.5, 1.7, 1.86, 1.98, 2.08]
for i, (h0, h1, r) in enumerate([(_hs[k], _hs[k + 1], math.sqrt(max(2.1 ** 2 - ((_hs[k] + _hs[k + 1]) / 2) ** 2, 0.04)))
                                 for k in range(len(_hs) - 1)]):
    vcyl('lidar', f'dome_{i}', DOME[0], DOME[2], r, DOME[1] + h0, DOME[1] + h1, 'glass', sides=16, D=20,
         decals=lambda f, t, a, g: d_dome(f))

# ---- gimbal quick-release port (DGC2.0) under the nose + rear E-port block
box('body', 'gport_plate', (-2.6, Y_BOT - 0.35, -14.9), (2.6, Y_BOT + 0.05, -9.9), 'shell2', D=14, decals=d_gimbal_port)
vcyl('body', 'gport', 0, -12.4, 1.6, Y_BOT - 0.95, Y_BOT - 0.3, 'dark', sides=16, D=16,
     decals=lambda f, t, a, g: d_gimbal_port(f))

# ---- arms
for tag, A in ARMS.items():
    rx, rz = A['root']; mx, mz = A['motor']
    sx = 1 if mx > 0 else -1
    d = np.array([mx - rx, mz - rz]); Lr = float(np.linalg.norm(d)); dn = d / Lr
    P = lambda s, y=Y_ARM: (rx + dn[0] * s, y, rz + dn[1] * s)
    # body socket (moulded boss) -> knurled fold lock -> carbon tube -> motor-end sleeve
    cyl('body', f'sock_{tag}', P(-2.0), P(2.2), 1.8, 'shell', sides=12, D=14, decals=d_socket)
    cyl('body', f'lock_{tag}', P(2.2), P(4.0), 1.92, 'shell2', sides=16, D=18, decals=d_knurl)
    cyl('body', f'arm_{tag}', P(3.9), P(Lr - 2.4), 1.15, 'carbon', sides=12, D=16, carbon_p=0.42)
    cyl('body', f'sleeve_{tag}', P(Lr - 4.2), P(Lr), 1.45, 'shell', sides=12, D=14, decals=d_sleeve)
    # ESC / LED box on top of the arm near the motor
    yaw = norm_ang(math.degrees(math.atan2(-dn[1], dn[0])))
    c = P(Lr - 6.8, Y_ARM + 1.0)
    obox('body', f'esc_{tag}', c, (3.4, 0.7, 1.7), [0, yaw, 0], 'shell2', D=14,
         decals=lambda f, c=c, dn=dn: slots(f, f.X * 0 + (f.P - np.array(c)) @ np.array([dn[0], 0, dn[1]]),
                                f.P[..., 1] * 0 + (f.P - np.array(c)) @ np.array([-dn[1], 0, dn[0]]),
                                -1.3, 1.3, -0.55, 0.55, 5, 'b', r=0.06, fill=0.4) if f.n[1] > 0.9 else None)
    # motor unit (non-rotating housing + bell) and prop
    mb = bone(f'motor_{tag}', (mx, Y_ARM, mz), 'drone')
    vcyl(mb, f'housing_{tag}', mx, mz, 2.2, 17.6, 22.6, 'shell', sides=16, D=16,
         decals=housing_dec(mx, mz, (dn[0], dn[1]), A['front']))
    if A['front']:
        vcyl(mb, f'cap_{tag}', mx, mz, 1.9, 22.6, 23.4, 'shell2', sides=16, D=16, decals=cap_top_dec(mx, mz, True))
        ax, az = mx + dn[0] * 1.15, mz + dn[1] * 1.15
        vcyl(mb, f'ant_{tag}', ax, az, 0.62, 22.6, 28.4, 'shell2', sides=8, D=14)
        vcyl(mb, f'ant_tip_{tag}', ax, az, 0.42, 28.4, 28.75, 'shell2', sides=8, D=14)
    else:
        vcyl(mb, f'cap_{tag}', mx, mz, 2.1, 22.6, 27.0, 'shell', sides=16, D=14,
             decals=lambda f, t, a, g: f.paint(np.abs(f.Y - 26.2) < 0.05, (30, 30, 32)))
        vcyl(mb, f'cap_top_{tag}', mx, mz, 1.6, 27.0, 27.45, 'shell3', sides=16, D=16, decals=cap_top_dec(mx, mz, False))
    vcyl(mb, f'bell_{tag}', mx, mz, 2.7, 16.0, 17.6, 'bell', sides=16, D=16, decals=bell_dec(mx, mz))
    vcyl(mb, f'base_{tag}', mx, mz, 2.0, 15.75, 16.0, 'motor', sides=16, D=16)
    pb = bone(f'prop_{tag}', (mx, PROP_Y, mz), mb)
    vcyl(pb, f'hub_{tag}', mx, mz, 1.25, 15.1, 15.75, 'motor', sides=12, D=18, decals=hub_dec(mx, mz))
    sweep = 1.0 if A['spin'] == 'cw' else -1.0
    perp = math.degrees(math.atan2(-dn[1], dn[0])) + 90.0
    base = norm_ang(perp + (-20 if A['front'] else 25) * sx)
    for kb in range(2):
        yb = norm_ang(base + 180 * kb)
        R = G.rotmat([8.0 * sweep, yb, 0])
        cen = np.array([mx, PROP_Y, mz]) + R @ np.array([HUB_R + BLADE_L / 2, 0, 0])
        obox(pb, f'blade_{tag}_{kb}', cen, (BLADE_L, 0.1, BLADE_W), [8.0 * sweep, yb, 0], 'clear', D=12,
             decals=faces(up=blade_dec(sweep), down=blade_dec(sweep)))
        hc = np.array([mx, PROP_Y, mz]) + R @ np.array([HUB_R + 0.5, 0, 0])
        obox(pb, f'clamp_{tag}_{kb}', hc, (1.6, 0.42, 1.25), [0, yb, 0], 'motor', D=16)

# ---- landing gear
for sx, s in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted([sx * 2.3, sx * 4.9])
    box('gear', f'leg_mount_{s}', (xa, Y_BOT - 0.9, LEG_Z - 1.3), (xb, Y_BOT + 0.1, LEG_Z + 1.3), 'shell2', D=14)
    top = (sx * 3.7, Y_BOT - 0.3, LEG_Z); bot = (sx * (SKID_X - 0.4), 3.6, LEG_Z)
    cyl('gear', f'leg_{s}', top, bot, 1.1, 'carbon', sides=12, D=16, carbon_p=0.4)
    mid = (np.array(top) * 0.45 + np.array(bot) * 0.55)
    cyl('gear', f'leg_clamp_{s}', mid - (np.array(bot) - np.array(top)) / np.linalg.norm(np.array(bot) - np.array(top)) * 0.6,
        mid + (np.array(bot) - np.array(top)) / np.linalg.norm(np.array(bot) - np.array(top)) * 0.6, 1.15, 'shell2', sides=12, D=14)
    vcyl('gear', f'leg_foot_{s}', sx * SKID_X, LEG_Z, 1.1, SKID_Y, 4.3, 'shell2', sides=12, D=14)
    box('gear', f'tee_{s}', (sx * SKID_X - 1.25, SKID_Y - 1.15, LEG_Z - 1.6), (sx * SKID_X + 1.25, SKID_Y + 1.25, LEG_Z + 1.6),
        'shell2', D=14)
    cyl('gear', f'skid_{s}', (sx * SKID_X, SKID_Y, SKID_Z0 + 0.6), (sx * SKID_X, SKID_Y, SKID_Z1 - 0.6), SKID_R, 'carbon',
        sides=12, D=14, carbon_p=0.4)
    for e, (z0, z1) in enumerate(((SKID_Z0, SKID_Z0 + 1.1), (SKID_Z1 - 1.1, SKID_Z1))):
        cyl('gear', f'skid_cap_{s}{e}', (sx * SKID_X, SKID_Y, z0), (sx * SKID_X, SKID_Y, z1), SKID_R + 0.12, 'rubber',
            sides=12, D=14)

# ============================================================================ ANIMATIONS (Bedrock values)
def spin_sign(spin):
    return -1 if spin == 'cw' else 1


PROPS = {f'prop_{t}': {'rotation': [(0.0, [0, 0, 0], 'linear'), (1.0, [0, 1440 * spin_sign(a['spin']), 0], 'linear')]}
         for t, a in ARMS.items()}
ANIMS = {
    'animation.m400.idle': dict(loop='loop', length=1.0, bones={'drone': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    'animation.m400.props': dict(loop='loop', length=1.0, bones=PROPS),
    'animation.m400.fly': dict(loop='loop', length=2.0, bones={
        'drone': {
            'position': [(0.0, [0, 0, 0], 'catmullrom'), (1.0, [0, 0.5, 0], 'catmullrom'), (2.0, [0, 0, 0], 'catmullrom')],
            'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0.5, 0, 0.6], 'catmullrom'),
                         (1.5, [-0.5, 0, -0.6], 'catmullrom'), (2.0, [0, 0, 0], 'catmullrom')]},
        **{k: {'rotation': [(0.0, [0, 0, 0], 'linear'), (2.0, [0, v['rotation'][1][1][1] * 2, 0], 'linear')]}
           for k, v in PROPS.items()}}),
    'animation.m400.takeoff': dict(loop='hold', length=1.5, bones={
        'drone': {'position': [(0.0, [0, 0, 0], 'linear'), (0.8, [0, 0, 0], 'catmullrom'), (1.5, [0, 3, 0], 'catmullrom')]},
        **{k: {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, v['rotation'][1][1][1] * 0.2, 0], 'catmullrom'),
                            (1.0, [0, v['rotation'][1][1][1] * 0.8, 0], 'catmullrom'),
                            (1.5, [0, v['rotation'][1][1][1] * 1.5, 0], 'linear')]}
           for k, v in PROPS.items()}}),
    'animation.m400.land': dict(loop='hold', length=1.5, bones={
        'drone': {'position': [(0.0, [0, 3, 0], 'catmullrom'), (0.9, [0, 0, 0], 'catmullrom')]},
        **{k: {'rotation': [(0.0, [0, 0, 0], 'linear'), (0.9, [0, v['rotation'][1][1][1] * 1.0, 0], 'catmullrom'),
                            (1.5, [0, v['rotation'][1][1][1] * 1.25, 0], 'catmullrom')]}
           for k, v in PROPS.items()}}),
}

# ---- battery: slides in / out perpendicular to the 34 deg slope (local +v = up & back); Bedrock values
_U = np.array([0.0, _cb, _sb])


def bpos(d, lift=0.0):
    p = _U * d + np.array([0, lift, 0])
    return [0, round(float(p[1]), 4), round(float(p[2]), 4)]


BAT_OUT = bpos(10.0, 3.0)
ANIMS.update({
    'animation.m400.battery_insert': dict(loop='hold', length=1.8, bones={
        'battery': {
            'position': [(0.0, BAT_OUT, 'catmullrom'), (0.55, bpos(10.0), 'catmullrom'), (0.85, bpos(6.0), 'catmullrom'),
                         (1.2, bpos(0.5), 'catmullrom'), (1.32, bpos(-0.08), 'linear'), (1.4, bpos(0.0), 'linear')],
            'rotation': [(0.0, [-8, 0, 0], 'catmullrom'), (0.55, [0, 0, 0], 'catmullrom')],
            'scale': [(0.0, [1, 1, 1], 'linear')]},
        'bat_latch_r': {'position': [(0.0, [0, 0, 0], 'linear'), (0.95, [0, 0, 0], 'linear'), (1.05, [0.11, 0, 0], 'linear'),
                                     (1.3, [0.11, 0, 0], 'linear'), (1.38, [0, 0, 0], 'linear')]},
        'bat_latch_l': {'position': [(0.0, [0, 0, 0], 'linear'), (0.95, [0, 0, 0], 'linear'), (1.05, [-0.11, 0, 0], 'linear'),
                                     (1.3, [-0.11, 0, 0], 'linear'), (1.38, [0, 0, 0], 'linear')]},
    }),
    'animation.m400.battery_remove': dict(loop='hold', length=1.5, bones={
        'battery': {
            'position': [(0.0, bpos(0.0), 'linear'), (0.2, bpos(0.0), 'catmullrom'), (0.45, bpos(1.0), 'catmullrom'),
                         (0.95, bpos(10.0), 'catmullrom'), (1.4, BAT_OUT, 'catmullrom')],
            'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.95, [0, 0, 0], 'catmullrom'), (1.4, [-8, 0, 0], 'catmullrom')],
            'scale': [(0.0, [1, 1, 1], 'linear'), (1.4, [1, 1, 1], 'linear'), (1.45, [0, 0, 0], 'linear')]},
        'bat_latch_r': {'position': [(0.0, [0, 0, 0], 'linear'), (0.12, [0.11, 0, 0], 'linear'), (0.45, [0.11, 0, 0], 'linear'),
                                     (0.55, [0, 0, 0], 'linear')]},
        'bat_latch_l': {'position': [(0.0, [0, 0, 0], 'linear'), (0.12, [-0.11, 0, 0], 'linear'), (0.45, [-0.11, 0, 0], 'linear'),
                                     (0.55, [0, 0, 0], 'linear')]},
    }),
    'animation.m400.battery_installed': dict(loop='hold', length=0.05, bones={
        'battery': {'position': [(0.0, [0, 0, 0], 'linear')], 'rotation': [(0.0, [0, 0, 0], 'linear')],
                    'scale': [(0.0, [1, 1, 1], 'linear')]}}),
    'animation.m400.battery_empty': dict(loop='hold', length=0.05, bones={
        'battery': {'position': [(0.0, BAT_OUT, 'linear')], 'scale': [(0.0, [0, 0, 0], 'linear')]}}),
})

meta = G.export(OUT, MODEL, MATS, ANIMS, kind='entity', atw=4096, glow=True, bounds=(5, 2.5), bounds_offset=[0, 1.0, 0],
                hidden_bones=('battery',))
print('done', OUT)
