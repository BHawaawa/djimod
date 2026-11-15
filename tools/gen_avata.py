# -*- coding: utf-8 -*-
"""Procedural generator v2: DJI Avata 360 -> Blockbench (GeckoLib entity) model + texture + glowmask.

Coordinates: Blockbench units (1/16 block), 1 px ~= 8 mm. Front of drone = north (-Z). Y up. Origin = bottom centre.

v2 changes (vs gen_avata_v1_legacy.py):
  * 4-blade scimitar propellers (wider, swept, rounded tips)
  * rounder shell: spine / shoulders / camera / sensor housings / ducts are rounded-rect prisms
  * spine is a hollow rounded tube with a rear battery bay (opening at the tail, connector at the far end)
  * the DJI Avata 360 Intelligent Flight Battery model (model/battery/avata360, 4 mm/px) is merged in as bone
    `battery` (scale 0.5, rotated 180 deg so the cap with button/LEDs is the drone's tail). It is hidden by default
    (Blockbench visibility off; the reference code hides it while the drone has no battery) and has
    insert / remove animations.

usage: python3 gen_avata.py <out_dir> [battery_model_dir]
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, obox, rrect, rr, rr_dist, norm_ang, rotmat

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else 'build_avata'
BAT_DIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'battery', 'avata360')
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
MODEL = 'avata360'

# ----------------------------------------------------------------------------- materials (colour, noise)
MATS = {
    'shell':       ((146, 148, 150), 2.2),
    'shell_dark':  ((104, 105, 107), 2.0),
    'rim':         ((152, 154, 156), 2.2),
    'bay':         ((70, 72, 75), 1.6),
    'cam':         ((36, 37, 39), 1.4),
    'cam2':        ((26, 27, 29), 1.2),
    'housing':     ((20, 20, 22), 1.0),
    'glass':       ((30, 52, 38), 0.0),
    'motor':       ((34, 34, 36), 1.2),
    'bell':        ((42, 42, 44), 1.2),
    'prop':        ((27, 27, 29), 1.0),
    'rubber':      ((58, 58, 60), 1.5),
    'led':         ((200, 236, 210), 0.0),
    'clear':       (None, 0.0),
}

# ----------------------------------------------------------------------------- key dimensions
YS, SP_W, SP_H, SP_R, SP_T = 2.65, 6.8, 4.2, 1.0, 0.41      # spine centre-line / width / height / edge radius / wall
SP_Y0, SP_Y1 = YS - SP_H / 2, YS + SP_H / 2                   # 0.55 .. 4.75
Z_SF, Z_BE, Z_R = -4.6, -2.05, 9.2                            # spine front / bay end wall / tail opening
BAY = (-(SP_W / 2 - SP_T), SP_Y0 + SP_T, SP_W / 2 - SP_T, SP_Y1 - SP_T, SP_R - SP_T)   # x0,y0,x1,y1,r of the bay
BAT_S, BAT_Y, BAT_Z = 0.5, YS, 5.15                           # battery merge transform (scale, centre-line y, z shift)
DUCT_X, DUCT_Z, R_IN, R_OUT, D_Y0, D_Y1, D_R = 9.0, 6.1, 5.1, 6.0, 1.0, 3.4, 0.38
N_SEG = 36
RC = [(-DUCT_X, -DUCT_Z, 'fl', 'cw'), (DUCT_X, -DUCT_Z, 'fr', 'ccw'),
      (-DUCT_X, DUCT_Z, 'rl', 'ccw'), (DUCT_X, DUCT_Z, 'rr', 'cw')]
BL, BW_PLATE, HUB_OFF = 4.3, 3.9, 0.45                        # blade length / blade plate width / root offset
PROP_Y = 2.9


# ============================================================================ helpers
def faces(**fns):
    """decal dispatcher keyed by the cube-local face name."""
    def fn(f):
        g = fns.get(f.face)
        if g:
            g(f)
    return fn


def chain(*fns):
    def fn(f):
        for g in fns:
            if g:
                g(f)
    return fn


_TXT = {}


def glyphs(txt, font, stretch=1.0):
    key = (txt, font, stretch)
    if key not in _TXT:
        fnt = ImageFont.truetype(font, 160)
        bb = fnt.getbbox(txt)
        im = Image.new('L', (bb[2] - bb[0] + 8, bb[3] - bb[1] + 8), 0)
        ImageDraw.Draw(im).text((4 - bb[0], 4 - bb[1]), txt, font=fnt, fill=255)
        if stretch != 1.0:
            im = im.resize((max(1, int(im.size[0] * stretch)), im.size[1]), Image.LANCZOS)
        _TXT[key] = np.asarray(im, float) / 255.0
    return _TXT[key]


def world_text(f, A, B, txt, ca, cb, h, color, font=FONT_B, stretch=1.0):
    """paint text on a face: A = world coord that grows to the reader's right, B = grows downwards."""
    g = glyphs(txt, font, stretch)
    gh, gw = g.shape
    sc = h / (gh - 8)
    u = (A - ca) / sc + gw / 2; v = (B - cb) / sc + gh / 2
    inside = (u >= 0) & (u < gw) & (v >= 0) & (v < gh)
    m = g[np.clip(v.astype(int), 0, gh - 1), np.clip(u.astype(int), 0, gw - 1)] * inside
    f.paint(m, color)


def rrect_x(b, name, cy, cz, h, d, r, x0, x1, mat, D=24, n=3, decals=None):
    """rounded-rectangle prism along X (rounded edges parallel to X)."""
    hh, hd = h / 2, d / 2
    r = max(0.05, min(r, hh - 0.02, hd - 0.02))
    step = 90.0 / (n + 1)
    e = r * math.tan(math.radians(step / 2))
    box(b, f'{name}_y', (x0, cy - hh + r - e, cz - hd), (x1, cy + hh - r + e, cz + hd), mat, D, decals=decals)
    box(b, f'{name}_z', (x0, cy - hh, cz - hd + r - e), (x1, cy + hh, cz + hd - r + e), mat, D, decals=decals)
    seg = 2 * r * math.tan(math.radians(step / 2)) + 0.004
    for sy in (1, -1):
        for sz in (1, -1):
            cc = ((x0 + x1) / 2, cy + sy * (hh - r), cz + sz * (hd - r))
            for k in range(1, n + 1):
                a = step * k if sy * sz > 0 else -step * k        # Rx(a): +Y turns toward +Z
                obox(b, f'{name}_c{"p" if sy > 0 else "m"}{"p" if sz > 0 else "m"}{k}', cc, (x1 - x0, 2 * r, seg),
                     [norm_ang(a), 0, 0], mat, D=D, decals=decals)


def rtube_z(b, name, x0, y0, x1, y1, r, t, z0, z1, mat, D=24, n=4, decals=None):
    """hollow rounded-rectangle tube along Z (outer rect x0..x1 / y0..y1, edge radius r, wall thickness t).
    Outer outline is the exact same polygon as bbgen.rrect(..., n) so a solid rrect with the same section
    (e.g. the battery cap) continues it without any step."""
    step = 90.0 / (n + 1)
    e = r * math.tan(math.radians(step / 2))
    box(b, f'{name}_top', (x0 + r - e, y1 - t, z0), (x1 - r + e, y1, z1), mat, D, decals=decals)
    box(b, f'{name}_bot', (x0 + r - e, y0, z0), (x1 - r + e, y0 + t, z1), mat, D, decals=decals)
    box(b, f'{name}_l', (x0, y0 + r - e, z0), (x0 + t, y1 - r + e, z1), mat, D, decals=decals)
    box(b, f'{name}_r', (x1 - t, y0 + r - e, z0), (x1, y1 - r + e, z1), mat, D, decals=decals)
    seg = 2 * e + 0.004
    for sx in (1, -1):
        for sy in (1, -1):
            ccx = x1 - r if sx > 0 else x0 + r
            ccy = y1 - r if sy > 0 else y0 + r
            for k in range(1, n + 1):
                th = math.radians(k * step)
                ux, uy = sx * math.cos(th), sy * math.sin(th)
                rm = r - t / 2
                obox(b, f'{name}_a{"p" if sx > 0 else "m"}{"p" if sy > 0 else "m"}{k}',
                     (ccx + ux * rm, ccy + uy * rm, (z0 + z1) / 2), (t, seg, z1 - z0),
                     [0, 0, norm_ang(math.degrees(math.atan2(uy, ux)))], mat, D=D, decals=decals)


def disc(b, name, center, radius, y0, y1, mat, axis='y', decals=None, D=12, sides=None):
    """regular polygon prism (apothem = radius) as the union of sides/2 rotated bars."""
    if sides is None:
        sides = 16 if radius > 1.05 else 12
    nb = sides // 2
    seg = 2 * radius * math.tan(math.pi / sides) + 0.01
    for k in range(nb):
        a = norm_ang(k * 360.0 / sides)
        e = 0.004 * k
        if axis == 'y':
            cen, size, rot = [center[0], (y0 + y1 + e) / 2, center[2]], [2 * radius, y1 - y0 + e, seg], [0, a, 0]
        else:
            cen, size, rot = [center[0], center[1], (y0 + y1 - e) / 2], [2 * radius, seg, y1 - y0 + e], [0, 0, a]
        obox(b, f'{name}_{k}', cen, size, rot, mat, decals=decals, D=D)


# ============================================================================ DECALS (world-space)
def d_spine(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    # bay interior (faces lying on the bay's inner rounded-rect surface)
    if abs(nz) < 0.5:
        d_in = rr_dist(X, Y, BAY[0], BAY[1], BAY[2], BAY[3], BAY[4])
        inner = (np.abs(d_in) < 0.07) & (Z > Z_BE - 0.01)
        if inner.any():
            f.paint(inner, MATS['bay'][0])
            # moulded guide ribs along the bay
            for k in range(6):
                zr = Z_BE + 1.4 + k * 1.6
                f.paint(inner & (np.abs(Z - zr) < 0.05), (52, 54, 57))
            f.paint(inner & (Z > Z_R - 0.35), (96, 98, 101))      # lighter lip just inside the opening
            # latch notches (battery latches click into these)
            f.paint(inner & (np.abs(Y - YS) < 0.6) & (np.abs(Z - (Z_R - 1.1)) < 0.3) & (np.abs(X) > 2.5), (30, 31, 33))
    if ny > 0.95 and Y.min() > SP_Y1 - 0.02:                       # top shell
        world_text(f, X, Z, 'dji', 0.0, 7.0, 0.95, (200, 202, 204), FONT_B)
        f.paint(rr(X, Z, -0.28, -3.95, 0.28, -3.8, 0.07), (225, 240, 228))       # status LED slit
        f.paint(rr(X, Z, -0.36, -4.03, 0.36, -3.72, 0.12) & ~rr(X, Z, -0.28, -3.95, 0.28, -3.8, 0.07), (112, 114, 116))
        f.paint(np.abs(Z - (Z_R - 0.08)) < 0.04, (120, 122, 124))
    if ny < -0.95 and Y.max() < SP_Y0 + 0.02:                      # bottom shell
        for z0, z1 in ((-1.8, 1.0), (1.5, 4.3)):
            for i in range(10):
                x = -2.0 + i * 0.42
                f.paint(rr(X, Z, x, z0, x + 0.2, z1, 0.08), (40, 41, 43))
        f.paint(rr(X, Z, -2.1, -4.3, 2.1, -2.2, 0.15) & ~rr(X, Z, -2.04, -4.24, 2.04, -2.26, 0.12), (118, 119, 121))
        world_text(f, -X, Z, '81', -0.95, -3.25, 0.85, (118, 119, 121), FONT_C)      # read from below: right = -X
        world_text(f, -X, Z, 'CE', 0.95, -3.25, 0.6, (118, 119, 121), FONT_C)
        f.paint(rr(X, Z, -0.95, 5.5, 0.95, 6.6, 0.5), (34, 66, 168))              # downward ToF window (blue)
        f.paint(rr(X, Z, -0.05, 5.5, 0.05, 6.6, 0.02), (20, 28, 60))
        f.paint(((X + 0.45) ** 2 + (Z - 5.85) ** 2) < 0.1 ** 2, (150, 180, 240))
        for x, z in ((-2.3, -4.1), (2.3, -4.1), (-2.3, 4.8), (2.3, 4.8), (-2.3, 8.4), (2.3, 8.4)):
            f.paint(((X - x) ** 2 + (Z - z) ** 2) < 0.11 ** 2, (45, 45, 47))
    if nx > 0.95 and X.min() > SP_W / 2 - 0.02:                   # right side: warning sticker
        f.paint(rr(Z, Y, 4.4, 2.0, 7.7, 3.7, 0.12), (24, 24, 26))
        f.paint(((Z - 7.05) ** 2 + (Y - 2.85) ** 2) < 0.38 ** 2, (205, 40, 40))
        f.paint(((Z - 7.05) ** 2 + (Y - 2.85) ** 2) < 0.22 ** 2, (240, 240, 240))
        for k in range(4):
            f.paint((np.abs(Y - (2.35 + k * 0.33)) < 0.04) & (Z > 4.6) & (Z < 6.5), (150, 150, 152))
    if abs(nx) > 0.95 and abs(X).min() > SP_W / 2 - 0.02:          # side vents (mic) between the ducts
        sx = 1 if nx > 0 else -1
        for i in range(3):
            f.paint(rr(Z, Y, 0.9 + i * 0.36, 1.3, 1.05 + i * 0.36, 2.3, 0.06), (60, 61, 63))
    if nz > 0.95:
        if Z.min() > Z_R - 0.02:                                  # tail rim around the opening
            f.paint(np.ones_like(X, bool), (126, 128, 130))
        elif abs(Z.max() - Z_BE) < 0.02:                          # bay end wall: power/data connector
            f.paint(np.ones_like(X, bool), (62, 64, 67))
            f.paint(rr(X, Y, -1.55, YS - 0.55, 1.55, YS + 0.5, 0.2), (24, 24, 26))
            for k in range(8):
                x = -1.05 + k * 0.3
                f.paint(rr(X, Y, x - 0.07, YS - 0.3, x + 0.07, YS + 0.25, 0.03), (198, 170, 98))
            for sx in (1, -1):
                f.paint(((X - sx * 1.95) ** 2 + (Y - YS) ** 2) < 0.16 ** 2, (150, 152, 155))
                f.paint(((X - sx * 1.95) ** 2 + (Y - YS) ** 2) < 0.07 ** 2, (40, 40, 42))


def d_cam(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nx) > 0.95:
        world_text(f, -np.sign(nx) * Z, -Y, 'AVATA 360', np.sign(nx) * 8.8, -2.05, 0.42, (120, 121, 124), FONT_C)
        f.paint(np.abs(Y - 1.5) < 0.03, (20, 20, 22))
    if nz < -0.95 and Z.max() < -11.98:
        f.paint(rr(X, Y, -1.45, 4.6, 1.45, 5.25, 0.22), (20, 20, 22))          # vent slot above the lens


def d_cam_top(f):
    if f.n[1] > 0.95:
        X, Z = f.X, f.Z
        for i in range(9):
            z = -11.25 + i * 0.6
            f.paint((np.abs(Z - z - 0.07) < 0.07) & (X > -1.35) & (X < 0.7), (22, 23, 24))
        world_text(f, Z, X, 'AVATA 360', -8.8, -1.43, 0.26, (150, 151, 154), FONT_C)


def lens_ring(cx, cy, r_glass):
    def fn(f):
        dx = f.X - cx; dy = f.Y - cy
        r = np.sqrt(dx * dx + dy * dy); ang = np.arctan2(dy, dx)
        knurl = (np.sin(ang * 48) > 0) & (r > r_glass + 0.16) & (r < r_glass + 0.3)
        f.paint((r > r_glass + 0.08), (24, 24, 26))
        f.paint(knurl, (40, 40, 43))
        f.paint((r > r_glass + 0.3), (16, 16, 17))
        f.paint((r > r_glass) & (r <= r_glass + 0.08), (120, 122, 124))
    return fn


def glass(cx, cy, R, tint=(34, 62, 44), silver=False):
    def fn(f):
        dx = f.X - cx; dy = f.Y - cy
        r = np.sqrt(dx * dx + dy * dy) / R
        g = np.clip(1.0 - r, 0, 1)
        col = np.array(tint, float)[None, None, :] * (0.55 + 0.6 * g[..., None])
        if silver:
            col = col * 0.5 + np.array([150, 155, 160])[None, None, :] * (0.5 * np.clip(1 - r[..., None], 0, 1) ** 0.5)
        f.img[..., :3] = col
        f.paint((r > 0.93), (10, 10, 11))
        f.paint((r > 0.62) & (r < 0.68), (70, 100, 80))
        f.paint((r < 0.3) & (r > 0.24), (60, 90, 70))
        f.paint(((dx + 0.35 * R) ** 2 + (dy - 0.35 * R) ** 2) < (0.16 * R) ** 2, (215, 235, 220))
        f.paint(((dx - 0.3 * R) ** 2 + (dy + 0.4 * R) ** 2) < (0.07 * R) ** 2, (150, 180, 160))
        f.paint(r < 0.12, (8, 14, 10))
    return fn


def d_sensor(sx):
    def fn(f):
        X = f.X * sx; Y, Z = f.Y, f.Z
        if f.n[2] < -0.95:
            win = (X > 5.05) & (X < 6.45) & (Y > 2.1) & (Y < 2.9)
            f.paint(rr(X, Y, 5.05, 2.1, 6.45, 2.9, 0.15), (14, 14, 16))
            f.paint(win & (X > 5.15) & (X < 5.5) & (Y > 2.55), (60, 62, 66))
            rr2 = (X - 5.6) ** 2 + (Y - 1.25) ** 2
            f.paint(rr2 < 0.48 ** 2, (20, 20, 22))
            f.paint(rr2 < 0.34 ** 2, (32, 58, 44))
            f.paint(((X - 5.5) ** 2 + (Y - 1.36) ** 2) < 0.1 ** 2, (190, 220, 200))
            f.paint(((X - 6.6) ** 2 + (Y - 2.5) ** 2) < 0.12 ** 2, (40, 40, 42))
        elif f.n[1] < -0.95:
            rr2 = (X - 6.0) ** 2 + (Z + 11.55) ** 2
            f.paint(rr2 < 0.45 ** 2, (20, 20, 22))
            f.paint(rr2 < 0.31 ** 2, (40, 75, 55))
    return fn


def d_duct(cx, cz):
    def fn(f):
        nx, ny, nz = f.n
        dx = f.X - cx; dz = f.Z - cz
        rad = np.sqrt(dx * dx + dz * dz) + 1e-6
        inward = (nx * dx + nz * dz) / rad < -0.55           # faces looking at the duct axis
        f.shade(inward, 0.82)
        if ny > 0.5:
            f.shade(np.ones_like(dx, bool), 1.04)
    return fn


def mount_dec(cx, cz):
    def fn(f):
        if f.n[1] < -0.95:
            dx = f.X - cx; dz = f.Z - cz
            r = np.sqrt(dx * dx + dz * dz)
            f.paint(r < 0.95, (110, 111, 113))
            for k in range(3):
                a = math.radians(90 + 120 * k)
                f.paint(((dx - 0.48 * math.cos(a)) ** 2 + (dz - 0.48 * math.sin(a)) ** 2) < 0.16 ** 2, (25, 25, 27))
            f.paint(r < 0.16, (60, 60, 62))
    return fn


def bell_dec(cx, cz, orange):
    def fn(f):
        dx = f.X - cx; dz = f.Z - cz
        if abs(f.n[1]) < 0.5:                       # windings visible through the bell slots
            ang = np.degrees(np.arctan2(dz, dx))
            slot = (np.abs(((ang + 15) % 30) - 15) < 4.5) & (f.Y > 1.65) & (f.Y < 2.35)
            f.paint(slot, (150, 92, 45))
            f.paint(slot & (np.abs(((ang + 15) % 30) - 15) < 1.5), (200, 130, 70))
            f.paint(f.Y < 1.47, (60, 60, 62))
        elif f.n[1] > 0.95:
            r = np.sqrt(dx * dx + dz * dz); ang = np.degrees(np.arctan2(dz, dx)) % 360
            f.paint((r > 0.38) & (r < 0.62), (110, 70, 38))
            f.paint((r > 0.38) & (r < 0.62) & (np.abs(((ang + 15) % 60) - 30) < 9), (30, 30, 32))
            ring = (r > 0.64) & (r < 0.78)
            sel = (((ang > 20) & (ang < 110)) | ((ang > 200) & (ang < 290))) if orange else (np.abs((ang % 120) - 60) < 9)
            f.paint(ring & sel, (238, 140, 30))
    return fn


def hub_dec(cx, cz):
    def fn(f):
        if f.n[1] > 0.95:
            dx = f.X - cx; dz = f.Z - cz
            r = np.sqrt(dx * dx + dz * dz)
            f.paint(r < 0.34, (60, 60, 62))
            for k in range(3):                       # 3 prop screws
                a = math.radians(30 + 120 * k)
                f.paint(((dx - 0.3 * math.cos(a)) ** 2 + (dz - 0.3 * math.sin(a)) ** 2) < 0.08 ** 2, (120, 121, 123))
            f.paint(r < 0.1, (30, 30, 30))
    return fn


def blade_dec(sweep):
    """4-blade scimitar blade drawn as alpha cutout on a thin plate (cube-local coords)."""
    def fn(f):
        L = f.L
        lx, lz = L[..., 0], L[..., 2]
        u = (lx + BL / 2) / BL
        uc = np.clip(u, 0, 1)
        zc = sweep * (1.75 * uc ** 1.8 - 0.42)                   # swept centre line
        hw = (0.32 + 1.05 * uc ** 0.6) * np.sqrt(np.clip(1 - uc ** 4, 0, 1)) * (1 - 0.25 * uc)
        # asymmetric chord: leading edge fuller than trailing edge
        off = lz - zc
        lim = np.where(off * sweep > 0, hw * 0.85, hw * 1.15)
        m = (u >= 0) & (u <= 1) & (np.abs(off) < lim)
        f.img[..., 3] = np.where(m, 255, 0)
        f.img[..., :3] = np.where(m[..., None], np.array([27, 27, 29], float), f.img[..., :3])
        edge = m & (np.abs(off) > lim - 0.08)
        f.paint(edge, (46, 46, 48))
        f.paint(m & (np.abs(off) < lim * 0.25) & (uc < 0.7), (32, 32, 34))     # spar
    return fn


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('drone', (0, 0, 0), 'root')
bone('body', (0, YS, 2), 'drone')
bone('guards', (0, 2, 0), 'drone')
bone('frame', (0, 1, 0), 'drone')
bone('camera', (0, 3.05, -8.8), 'drone')

# ---- spine: solid front block + hollow tube with the battery bay
x0, x1 = -SP_W / 2, SP_W / 2
rrect('body', 'spine_f', 0, YS, SP_W, SP_H, SP_R, Z_SF, Z_BE, 'shell', D=16, n=4, decals=d_spine)
rtube_z('body', 'spine_bay', x0, SP_Y0, x1, SP_Y1, SP_R, SP_T, Z_BE, Z_R, 'shell', D=16, n=4, decals=d_spine)
box('body', 'led', (-0.28, SP_Y1, -3.95), (0.28, SP_Y1 + 0.04, -3.8), 'led', D=24)

# ---- front shoulders (sensor housings that hold the camera cradle)
for sx, side in ((1, 'r'), (-1, 'l')):
    rrect('body', f'shoulder_{side}', sx * 3.2, 2.55, 2.4, 4.3, 0.75, -12.4, Z_SF + 0.3, 'shell', D=14, n=3,
          decals=d_sensor(sx))
    xa, xb = sorted([sx * 4.2, sx * 6.9])
    rrect_x('body', f'sensor_{side}', 1.95, -11.55, 2.9, 1.6, 0.6, xa, xb, 'shell', D=16, n=3, decals=d_sensor(sx))
    xa, xb = sorted([sx * 2.2, sx * 6.8])
    box('body', f'chin_{side}', (xa, 0.3, -12.0), (xb, 0.55, -10.5), 'shell_dark', D=8)

# ---- camera module (rotating 360 module)
rrect('camera', 'cam_body', 0, 3.075, 3.8, 5.25, 0.7, -12.0, -5.6, 'cam', D=16, n=3, decals=d_cam)
box('camera', 'cam_top', (-1.6, 5.69, -11.6), (1.6, 6.02, -6.0), 'cam', D=18, decals=d_cam_top)
for i, zc in enumerate([-10.6, -9.9, -9.2, -8.5, -7.8, -7.1]):
    box('camera', f'cam_fin_{i}', (-1.25, 6.02, zc - 0.14), (1.25, 6.27, zc + 0.14), 'cam2', D=12)
box('camera', 'cam_chin', (-1.5, 0.22, -11.6), (1.5, 0.46, -6.0), 'cam', D=8)
for sx in (1, -1):
    xa, xb = sorted([sx * 1.9, sx * 2.0])
    box('camera', f'cam_axle_{"r" if sx > 0 else "l"}', (xa, 2.5, -9.4), (xb, 3.6, -8.2), 'housing', D=12)
LY = 3.05
disc('camera', 'lens_f_housing', (0, LY, 0), 1.5, -12.6, -12.0, 'housing', axis='z', decals=lens_ring(0, LY, 1.16), D=20)
disc('camera', 'lens_f_glass', (0, LY, 0), 1.16, -12.82, -12.6, 'glass', axis='z', decals=glass(0, LY, 1.2), D=24)
disc('camera', 'lens_r_housing', (0, LY, 0), 1.3, -5.6, -5.3, 'housing', axis='z', decals=lens_ring(0, LY, 0.98), D=18)
disc('camera', 'lens_r_glass', (0, LY, 0), 0.98, -5.3, -5.12, 'glass', axis='z',
     decals=glass(0, LY, 1.02, tint=(60, 70, 70), silver=True), D=20)

# ---- prop guards: rounded-section rings (2 core bars + 2 slats per upper corner + 1 per lower corner)
SOLIDS = [((-SP_W / 2, Z_SF), (SP_W / 2, Z_R)), ((2.0, -12.4), (4.4, Z_SF)), ((-4.4, -12.4), (-2.0, Z_SF))]


def inside_solid(pts):
    return any(all(a0 <= p[0] <= a1 and b0 <= p[1] <= b1 for p in pts) for (a0, b0), (a1, b1) in SOLIDS)


def ring_piece(tag, k, part, phi, q0, q1, y0, y1, tilt, cx, cz, dec):
    """one ring segment. q = radial coordinate. tilt = rotation about the tangent axis."""
    qc, yc = (q0 + q1) / 2, (y0 + y1) / 2
    L = 2 * (R_OUT + 0.05) * math.tan(math.pi / N_SEG) + 0.03
    yaw = norm_ang(-(math.degrees(phi) + 90))
    obox('guards', f'duct_{tag}_{part}_{k}', (cx + qc * math.cos(phi), yc, cz + qc * math.sin(phi)),
         (L, y1 - y0, q1 - q0), [tilt, yaw, 0], 'rim', D=10, decals=dec)


for cx, cz, tag, _ in RC:
    dec = d_duct(cx, cz)
    for k in range(N_SEG):
        phi = 2 * math.pi * (k + 0.5) / N_SEG
        tx, tz = -math.sin(phi), math.cos(phi)
        L = 2 * R_OUT * math.tan(math.pi / N_SEG)
        corners = [(cx + rr_ * math.cos(phi) + a * tx * L / 2, cz + rr_ * math.sin(phi) + a * tz * L / 2)
                   for a in (-1, 1) for rr_ in (R_IN, R_OUT)]
        if inside_solid(corners):
            continue
        r = D_R
        ring_piece(tag, k, 'h', phi, R_IN, R_OUT, D_Y0 + r, D_Y1 - r, 0, cx, cz, dec)
        ring_piece(tag, k, 'v', phi, R_IN + r, R_OUT - r, D_Y0, D_Y1, 0, cx, cz, dec)
        # corner slats; local +Z points inward (toward the duct axis), Rx(a) turns +Y toward +Z
        for sq, sy, nsl in ((1, 1, 2), (-1, 1, 2), (1, -1, 1), (-1, -1, 1)):
            qc = (R_OUT - r) if sq > 0 else (R_IN + r)
            yc = (D_Y1 - r) if sy > 0 else (D_Y0 + r)
            step = 90.0 / (nsl + 1)
            seg = 2 * r * math.tan(math.radians(step / 2)) + 0.004
            for j in range(1, nsl + 1):
                a = step * j if (sy > 0) == (-sq > 0) else -step * j
                ring_piece(tag, k, f's{sq}{sy}{j}', phi, qc - seg / 2, qc + seg / 2, yc - r, yc + r, norm_ang(a),
                           cx, cz, dec)
    # landing pad under the outer side of each duct
    ox, oz = (1 if cx > 0 else -1), (1 if cz > 0 else -1)
    a = math.atan2(oz * 0.75, ox)
    fx, fz = cx + 5.55 * math.cos(a), cz + 5.55 * math.sin(a)
    obox('guards', f'foot_{tag}', (fx, 0.75, fz), (1.1, 0.55, 0.75), [0, norm_ang(-(math.degrees(a) + 90)), 0], 'rubber', D=8)

# waist bridges where the front and rear rings meet + triangular struts back to the spine
for sx in (1, -1):
    rrect('guards', f'waist_{"r" if sx > 0 else "l"}', sx * DUCT_X, (D_Y0 + D_Y1) / 2, 1.5, D_Y1 - D_Y0, D_R,
          -0.9, 0.9, 'rim', D=10, n=2)
    for sz in (1, -1):
        p0 = np.array([sx * (SP_W / 2 - 0.2), sz * 2.3]); p1 = np.array([sx * (DUCT_X - 0.6), sz * 0.25])
        d = p1 - p0; Ld = float(np.linalg.norm(d))
        yaw = norm_ang(math.degrees(math.atan2(-d[1], d[0])))
        c = (p0 + p1) / 2
        obox('guards', f'strut_{"r" if sx > 0 else "l"}{"f" if sz < 0 else "b"}', (c[0], 2.15, c[1]),
             (Ld, 1.6, 0.7), [0, yaw, 0], 'shell', D=10)

# ---- arms, spokes, motors, 4-blade props
for cx, cz, tag, spin in RC:
    sx, sz = (1 if cx > 0 else -1), (1 if cz > 0 else -1)
    B = np.array([sx * 3.0, sz * 2.9]); M = np.array([cx, cz])
    d = M - B; Lr = float(np.linalg.norm(d)); dn = d / Lr
    yaw = norm_ang(math.degrees(math.atan2(-dn[1], dn[0])))
    ctr = (B + M) / 2
    obox('frame', f'arm_{tag}', (ctr[0], 1.15, ctr[1]), (Lr + 0.6, 0.55, 0.95), [0, yaw, 0], 'shell_dark', D=8)
    back = math.atan2(-dn[1], -dn[0])
    for j, off in enumerate((120, -120)):
        a = back + math.radians(off)
        e = np.array([math.cos(a), math.sin(a)])
        c2 = M + e * (0.9 + R_IN + 0.25) / 2
        obox('frame', f'spoke_{tag}_{j}', (c2[0], 1.05, c2[1]), (R_IN + 0.25 - 0.9, 0.32, 0.3),
             [0, norm_ang(math.degrees(math.atan2(-e[1], e[0]))), 0], 'shell_dark', D=6)
    mb = bone(f'motor_{tag}', (cx, 1.0, cz), 'drone')
    disc(mb, f'mount_{tag}', (cx, 0, cz), 1.0, 0.9, 1.35, 'shell_dark', decals=mount_dec(cx, cz), D=14)
    disc(mb, f'bell_{tag}', (cx, 0, cz), 0.85, 1.35, 2.55, 'bell', decals=bell_dec(cx, cz, spin == 'ccw'), D=16)
    disc(mb, f'bell_top_{tag}', (cx, 0, cz), 0.8, 2.55, 2.72, 'motor', decals=bell_dec(cx, cz, spin == 'ccw'), D=20)
    pb = bone(f'prop_{tag}', (cx, PROP_Y, cz), mb)
    disc(pb, f'hub_{tag}', (cx, 0, cz), 0.6, 2.72, 3.1, 'prop', decals=hub_dec(cx, cz), D=20)
    sweep = 1.0 if spin == 'cw' else -1.0
    pitch = 15.0 * sweep
    base = 20.0 if tag in ('fl', 'rr') else 65.0
    for kb in range(4):
        yb = norm_ang(base + 90 * kb)
        R = rotmat([pitch, yb, 0])
        cen = np.array([cx, PROP_Y, cz]) + R @ np.array([HUB_OFF + BL / 2, 0, 0])
        obox(pb, f'blade_{tag}_{kb}', cen, (BL, 0.08, BW_PLATE), [pitch, yb, 0], 'clear', D=24,
             decals=faces(up=blade_dec(sweep), down=blade_dec(sweep)))

# ============================================================================ MERGE: battery model
def merge_battery(bat_dir):
    geo = json.load(open(os.path.join(bat_dir, 'geo', 'avata360_battery.geo.json'), encoding='utf-8'))['minecraft:geometry'][0]
    tex = np.asarray(Image.open(os.path.join(bat_dir, 'textures', 'avata360_battery.png')).convert('RGBA'))
    gm_path = os.path.join(bat_dir, 'textures', 'avata360_battery_glowmask.png')
    gtex = np.asarray(Image.open(gm_path).convert('RGBA')) if os.path.exists(gm_path) else np.zeros_like(tex)
    DS = 2   # battery texel density 30 px / battery-px  -> after 0.5 scale and /2 downsample: 30 px per drone px
    s, YB, ZB = BAT_S, BAT_Y, BAT_Z

    def P(p):          # Blockbench point of the battery -> drone Blockbench point
        return [-s * p[0], s * p[1] + YB, -s * p[2] + ZB]
    rename = {'battery': 'battery', 'body': 'bat_body', 'cap': 'bat_cap', 'button': 'bat_button',
              'latch_l': 'bat_latch_r', 'latch_r': 'bat_latch_l'}     # 180 deg turn swaps the battery's sides
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
                r = c['rotation']; rb = [-r[0], -r[1], r[2]]            # geo -> Blockbench euler
                rot = [-rb[0], rb[1], -rb[2]]                           # conjugated by the 180 deg Y turn
            else:
                org, rot = frm2, [0, 0, 0]
            pre = {}
            for fname, fd in c['uv'].items():
                (u, v), (w, h) = fd['uv'], fd['uv_size']
                ua, ub = sorted([u, u + w]); va, vb = sorted([v, v + h])
                ua, ub, va, vb = int(round(ua)), int(round(ub)), int(round(va)), int(round(vb))
                reg, greg = tex[va:vb, ua:ub], gtex[va:vb, ua:ub]
                nw, nh = max(1, int(round(reg.shape[1] / DS))), max(1, int(round(reg.shape[0] / DS)))
                reg = np.asarray(Image.fromarray(reg, 'RGBA').resize((nw, nh), Image.BOX))
                greg = np.asarray(Image.fromarray(greg, 'RGBA').resize((nw, nh), Image.BOX))
                tf = swap[fname]
                if tf in ('up', 'down'):                                # u and v both reverse
                    reg, greg = reg[::-1, ::-1], greg[::-1, ::-1]
                pre[tf] = (np.ascontiguousarray(reg), np.ascontiguousarray(greg))
            cb = box(rename[b['name']], 'bat_' + str(n), frm2, to2, 'shell', origin=org, rot=rot)
            cb['pre'] = pre
            n += 1
    print('merged battery cubes', n)


merge_battery(BAT_DIR)

# ============================================================================ ANIMATIONS (Bedrock / GeckoLib values)
def spin_sign(spin):
    return -1 if spin == 'cw' else 1


BAT_OUT = [0, 2.6, 15.5]        # battery start/end offset behind & above the tail (model px)
ANIMS = {
    'animation.avata360.idle': dict(loop='loop', length=1.0, bones={
        'drone': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    'animation.avata360.fly': dict(loop='loop', length=1.0, bones={
        'drone': {
            'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 0.4, 0], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom')],
            'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.25, [0.6, 0, 0.9], 'catmullrom'),
                         (0.75, [-0.6, 0, -0.9], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom')]},
        **{f'prop_{t}': {'rotation': [(0.0, [0, 0, 0], 'linear'), (1.0, [0, 1440 * spin_sign(s), 0], 'linear')]}
           for _, _, t, s in RC}}),
    'animation.avata360.mode_360': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.75, [90, 0, 0], 'catmullrom')]}}),
    'animation.avata360.mode_single': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0, [90, 0, 0], 'catmullrom'), (0.75, [0, 0, 0], 'catmullrom')]}}),
    # ---- battery: slide in from behind the tail, latches squeeze while sliding and click at the end,
    #      then the power button is pressed once.
    'animation.avata360.battery_insert': dict(loop='hold', length=2.0, bones={
        'battery': {
            'position': [(0.0, BAT_OUT, 'catmullrom'), (0.45, [0, 0.7, 12.6], 'catmullrom'),
                         (0.7, [0, 0, 11.3], 'catmullrom'), (1.15, [0, 0, 1.2], 'catmullrom'),
                         (1.3, [0, 0, -0.12], 'linear'), (1.38, [0, 0, 0], 'linear')],
            'rotation': [(0.0, [12, 0, 0], 'catmullrom'), (0.45, [3, 0, 0], 'catmullrom'), (0.7, [0, 0, 0], 'catmullrom')],
            'scale': [(0.0, [1, 1, 1], 'linear')]},
        'bat_latch_r': {'position': [(0.0, [0, 0, 0], 'linear'), (0.6, [0, 0, 0], 'linear'), (0.72, [0.11, 0, 0], 'linear'),
                                     (1.28, [0.11, 0, 0], 'linear'), (1.36, [0, 0, 0], 'linear')]},
        'bat_latch_l': {'position': [(0.0, [0, 0, 0], 'linear'), (0.6, [0, 0, 0], 'linear'), (0.72, [-0.11, 0, 0], 'linear'),
                                     (1.28, [-0.11, 0, 0], 'linear'), (1.36, [0, 0, 0], 'linear')]},
        'bat_button': {'position': [(0.0, [0, 0, 0], 'linear'), (1.6, [0, 0, 0], 'linear'), (1.68, [0, 0, -0.06], 'linear'),
                                    (1.8, [0, 0, 0], 'linear')]},
    }),
    'animation.avata360.battery_remove': dict(loop='hold', length=1.5, bones={
        'battery': {
            'position': [(0.0, [0, 0, 0], 'linear'), (0.2, [0, 0, 0], 'catmullrom'), (0.75, [0, 0, 11.3], 'catmullrom'),
                         (1.0, [0, 0.7, 12.6], 'catmullrom'), (1.45, BAT_OUT, 'catmullrom')],
            'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.75, [0, 0, 0], 'catmullrom'), (1.0, [3, 0, 0], 'catmullrom'),
                         (1.45, [12, 0, 0], 'catmullrom')],
            'scale': [(0.0, [1, 1, 1], 'linear'), (1.45, [1, 1, 1], 'linear'), (1.5, [0, 0, 0], 'linear')]},
        'bat_latch_r': {'position': [(0.0, [0, 0, 0], 'linear'), (0.1, [0.11, 0, 0], 'linear'), (0.75, [0.11, 0, 0], 'linear'),
                                     (0.85, [0, 0, 0], 'linear')]},
        'bat_latch_l': {'position': [(0.0, [0, 0, 0], 'linear'), (0.1, [-0.11, 0, 0], 'linear'), (0.75, [-0.11, 0, 0], 'linear'),
                                     (0.85, [0, 0, 0], 'linear')]},
    }),
    # static states (useful for a state-driven controller / Blockbench preview)
    'animation.avata360.battery_installed': dict(loop='hold', length=0.05, bones={
        'battery': {'position': [(0.0, [0, 0, 0], 'linear')], 'rotation': [(0.0, [0, 0, 0], 'linear')],
                    'scale': [(0.0, [1, 1, 1], 'linear')]}}),
    'animation.avata360.battery_empty': dict(loop='hold', length=0.05, bones={
        'battery': {'position': [(0.0, BAT_OUT, 'linear')], 'scale': [(0.0, [0, 0, 0], 'linear')]}}),
}

meta = G.export(OUT, MODEL, MATS, ANIMS, kind='entity', atw=2048, glow=True, bounds=(3, 1.5),
                bounds_offset=[0, 0.25, 0], hidden_bones=('battery',))
json.dump({'battery_transform': {'scale': BAT_S, 'rotation_y': 180, 'centre_line_y': BAT_Y, 'z_shift': BAT_Z},
           'bay': {'x': [BAY[0], BAY[2]], 'y': [BAY[1], BAY[3]], 'z': [Z_BE, Z_R], 'corner_r': BAY[4]},
           **meta}, open(os.path.join(OUT, 'meta.json'), 'w'), indent=1)
print('done', OUT)
