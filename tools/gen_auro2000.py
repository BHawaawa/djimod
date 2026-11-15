# -*- coding: utf-8 -*-
"""Procedural generator: DJI Power AURO 2000 ELITE portable power station -> GeckoLib entity
(bbmodel + geo + animation + texture + glowmask), including the built-in 140 W retractable USB-C cable as an
animated bone chain (plug_yaw -> plug -> cab_01 .. cab_NN).

Scale: 1 model px = 4 mm.  Real size 456 x 225 x 245 mm (L x D x H) -> 114 x 56.2 x 61.25 px.
Front = north (-Z), +Y up, origin = ground centre.  Front-panel features are laid out in u = -X
(u grows to the right when looking at the front, i.e. image coordinates of the reference photos).

usage: python3 gen_auro2000.py <out_dir>
"""
import math, os, sys, json
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, obox, xbox, rrect, rrect_y, rr, norm_ang
import auro_cable as CAB

OUT = sys.argv[1] if len(sys.argv) > 1 else 'build_auro'
MODEL = 'auro2000elite'
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

SHELL = (190, 192, 195)
MATS = {
    'shell':  (SHELL, 2.0),
    'shell_d': ((172, 174, 177), 2.0),
    'band':   ((50, 52, 55), 3.0),
    'panel':  ((58, 60, 63), 3.4),
    'tray':   ((62, 64, 67), 3.4),
    'outlet': ((70, 72, 75), 3.0),
    'cover':  ((60, 62, 65), 3.0),
    'glass':  ((10, 11, 13), 0.4),
    'dark':   ((26, 27, 29), 1.0),
    'grille': ((150, 152, 155), 1.5),
    'rubber': ((34, 34, 36), 1.2),
    'cable':  ((30, 30, 32), 1.0),
    'plug':   ((36, 37, 39), 1.0),
    'metal':  ((186, 188, 192), 1.0),
    'btn':    ((205, 207, 210), 1.5),
}

# ----------------------------------------------------------------------------- key dimensions (px)
HX, HZ, H = 53.5, 28.1, 61.25          # half length (side walls) / half depth / height
EAR_X = 57.0                           # handle ears
Y_BOT, Y_TOP = 1.5, 60.5               # shell bottom (feet below) / top before the cap
Z_CORE = -27.0                         # core front (floor of the cable slot)
Z_F = -HZ                              # shell front
Z_BAND = -28.45                        # dark band front
Z_PAN = -28.8                          # panel front
BAND = (-50.0, 50.0, 30.0, 57.2)       # u0,u1,y0,y1
SLOT = (-3.8, 12.5, 47.6, 52.4)        # inner cable slot (u)
RECESS = (-4.6, 18.4, 45.9, 54.2)      # outer recess in the middle panel (u)
CAV_Y0, CAV_Y1, CAV_Z = 38.5, 55.0, 17.0   # handle cavity on both ends (measured on the hero photo)
CORE_X = 45.0


def U(u):
    return -u


# ============================================================================ text / paint helpers
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


def text_mask(A, B, txt, ca, cb, h, font=FONT_B, shear=0.0, wscale=1.0):
    g = glyphs(txt, font)
    gh, gw = g.shape
    sc = h / (gh - 8)
    v = (B - cb) / sc + gh / 2
    u = (A - ca + (B - cb) * shear) / (sc * wscale) + gw / 2
    inside = (u >= 0) & (u < gw) & (v >= 0) & (v < gh)
    return g[np.clip(v.astype(int), 0, gh - 1), np.clip(u.astype(int), 0, gw - 1)] * inside


def text(f, A, B, txt, ca, cb, h, color, font=FONT_B, glow=False, **kw):
    m = text_mask(A, B, txt, ca, cb, h, font, **kw)
    f.paint(m, color)
    if glow:
        f.emit(m > 0.35, color)


def screw(f, A, B, ca, cb, r=0.55):
    d2 = (A - ca) ** 2 + (B - cb) ** 2
    f.paint(d2 < (r * 1.25) ** 2, (34, 35, 37))
    f.paint(d2 < r * r, (120, 122, 126))
    f.paint(d2 < (r * 0.75) ** 2, (92, 94, 98))
    f.paint((d2 < (r * 0.62) ** 2) & ((np.abs(A - ca) < r * 0.13) | (np.abs(B - cb) < r * 0.13)), (40, 41, 43))   # hex-ish
    f.paint(d2 < (r * 0.25) ** 2, (50, 51, 54))


def grain(f, mask, amt=6.0, scale=9.0):
    n = G.world_noise(f.P, scale, 77)
    f.img[..., :3] += (n * amt * mask)[..., None]


def ring(f, A, B, ca, cb, r_in, r_out, col):
    d2 = (A - ca) ** 2 + (B - cb) ** 2
    f.paint((d2 < r_out ** 2) & (d2 >= r_in ** 2), col)


# ============================================================================ geometry helpers
def slab_holes(b, name, x0, x1, y0, y1, z0, z1, holes, mat, D=8, decals=None):
    """axis-aligned slab in X/Y with rectangular holes (x0,x1,y0,y1) cut through; merged grid decomposition."""
    xs = sorted({x0, x1, *[h[0] for h in holes], *[h[1] for h in holes]})
    ys = sorted({y0, y1, *[h[2] for h in holes], *[h[3] for h in holes]})
    xs = [x for x in xs if x0 <= x <= x1]; ys = [y for y in ys if y0 <= y <= y1]
    k = 0
    for j in range(len(ys) - 1):
        ya, yb = ys[j], ys[j + 1]
        run = None
        for i in range(len(xs) - 1):
            xa, xb = xs[i], xs[i + 1]
            cx, cy = (xa + xb) / 2, (ya + yb) / 2
            inside = any(h[0] < cx < h[1] and h[2] < cy < h[3] for h in holes)
            if not inside:
                run = (run[0], xb) if run else (xa, xb)
            if inside or i == len(xs) - 2:
                if run:
                    box(b, f'{name}_{k}', (run[0], ya, z0), (run[1], yb, z1), mat, D=D, decals=decals); k += 1
                    run = None


def uslab(b, name, u0, u1, y0, y1, z0, z1, mat, holes=(), **kw):
    """slab given in front u coordinates (u -> X = -u)."""
    slab_holes(b, name, -u1, -u0, y0, y1, z0, z1, [(-h[1], -h[0], h[2], h[3]) for h in holes], mat, **kw)


def ubox(b, name, u0, u1, y0, y1, z0, z1, mat, **kw):
    return box(b, name, (-u1, y0, z0), (-u0, y1, z1), mat, **kw)


def urrect(b, name, u0, u1, y0, y1, r, z0, z1, mat, **kw):
    rrect(b, name, -(u0 + u1) / 2, (y0 + y1) / 2, u1 - u0, y1 - y0, r, z0, z1, mat, **kw)


def rrect_x(b, name, cy, cz, h, d, r, x0, x1, mat, D=24, n=3, decals=None):
    """rounded-rectangle prism along X (profile in Y/Z)."""
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
                a = step * k if sy * sz > 0 else -step * k
                obox(b, f'{name}_c{"p" if sy > 0 else "m"}{"p" if sz > 0 else "m"}{k}', cc, (x1 - x0, seg, 2 * r),
                     [norm_ang(a), 0, 0], mat, D=D, decals=decals)


# ============================================================================ DECALS
def front_uv(f):
    return -f.X, f.Y


def d_shell(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    u = -X
    if nz < -0.95:
        if Z.max() < Z_F + 0.05 and Z.min() > Z_F - 0.05:
            text(f, u, -Y, 'AURO 2000 ELITE', 0.0, -7.2, 1.55, (118, 120, 124), FONT_R, wscale=1.05)
        # slot walls of the front layer (seen only inside the cable slot)
    elif abs(nx) > 0.95 or abs(ny) > 0.95:
        if Z.max() < Z_CORE + 0.01 and Z.min() > Z_F - 0.01:
            inslot = (u > SLOT[0] - 0.05) & (u < SLOT[1] + 0.05) & (Y > SLOT[2] - 0.05) & (Y < SLOT[3] + 0.05)
            f.paint(inslot, (28, 29, 31))
    if abs(nx) > 0.95 and Z.min() > -HZ - 0.5 and abs(X.mean()) > 47:   # end walls
        sx = np.sign(nx)
        ring(f, Z, Y, -23.6, 56.4, 0.0, 1.55, (160, 162, 166))          # threaded accessory hole (top front)
        f.paint((Z + 23.6) ** 2 + (Y - 56.4) ** 2 < 1.05 ** 2, (40, 41, 44))
        f.paint((Z + 23.6) ** 2 + (Y - 56.4) ** 2 < 0.55 ** 2, (20, 20, 22))
        for zz in (-11.5, 11.5):                                       # mounting holes for the expansion kit
            ring(f, Z, Y, zz, 18.5, 0.0, 1.45, (164, 166, 170))
            f.paint((Z - zz) ** 2 + (Y - 18.5) ** 2 < 1.0 ** 2, (52, 53, 56))
            f.paint((Z - zz) ** 2 + (Y - 18.5) ** 2 < 0.5 ** 2, (18, 18, 20))
        f.paint(np.abs(Y - (CAV_Y0 + 0.05)) < 0.07, (150, 152, 155))   # side seam
        f.paint(np.abs(Y - 3.2) < 0.06, (160, 162, 165))
    if nz > 0.95 and Z.min() > HZ - 0.05:
        d_back(f)
    if ny < -0.95 and Y.max() < Y_BOT + 0.01:
        f.paint(rr(X, Z, -20, -6, 20, 6, 1.0), (178, 180, 183))
        text(f, X, Z, 'DJI', 0.0, -2.3, 2.0, (130, 132, 136), FONT_B)
        text(f, X, Z, 'POWER AURO 2000 ELITE   DYM2000MH', 0.0, 1.0, 0.7, (130, 132, 136), FONT_C)
        text(f, X, Z, '2016Wh  LiFePO4  2000W', 0.0, 2.6, 0.7, (130, 132, 136), FONT_C)
    if ny > 0.95 and Y.min() > H - 0.02:
        f.paint(np.abs(np.abs(X) - 47.0) < 0.06, (172, 174, 177))       # ear seams on the lid


def d_back(f):
    X, Y = f.X, f.Y
    # rear: AC input + extension-battery port cover + vents + rating label (viewer right = +X)
    f.paint(rr(X, Y, -44, 38, -26, 53, 1.6), (64, 66, 69))
    f.paint(rr(X, Y, -43.4, 38.6, -26.6, 52.4, 1.3), (54, 56, 59))
    text(f, X, -Y, 'AC IN', -35.0, -51.0, 1.1, (150, 152, 156), FONT_C)
    f.paint(rr(X, Y, -38.8, 41.0, -31.2, 47.6, 1.0), (24, 24, 26))
    for xx in (-36.6, -33.4):
        f.paint(rr(X, Y, xx - 0.35, 43.6, xx + 0.35, 46.0, 0.12), (8, 8, 9))
    f.paint(rr(X, Y, -35.35, 41.8, -34.65, 43.0, 0.12), (8, 8, 9))
    f.paint(rr(X, Y, -20, 36, 20, 53.5, 1.8), (64, 66, 69))
    f.paint(rr(X, Y, -19.4, 36.6, 19.4, 52.9, 1.5), (56, 58, 61))
    text(f, X, -Y, 'EXTRA BATTERY', 0.0, -50.6, 1.2, (150, 152, 156), FONT_C)
    f.paint(rr(X, Y, -12, 39, 12, 47.6, 1.0), (30, 31, 33))
    for k in range(10):
        f.paint(rr(X, Y, -10.4 + k * 2.2, 41.2, -9.4 + k * 2.2, 45.4, 0.2), (150, 128, 70))
    f.paint(rr(X, Y, 26, 38, 44, 53, 1.6), (64, 66, 69))
    f.paint(rr(X, Y, 26.6, 38.6, 43.4, 52.4, 1.3), (54, 56, 59))
    text(f, X, -Y, 'SDC IN', 35.0, -51.0, 1.1, (150, 152, 156), FONT_C)
    for xx in (31.0, 39.0):
        f.paint(rr(X, Y, xx - 2.4, 41.5, xx + 2.4, 47.0, 0.8), (24, 24, 26))
        f.paint(rr(X, Y, xx - 1.2, 43.4, xx + 1.2, 45.2, 0.3), (60, 60, 62))
    for k in range(18):                                           # vent slots
        x0 = -38 + k * 4.3
        f.paint(rr(X, Y, x0, 21.0, x0 + 2.3, 31.0, 1.0), (84, 86, 90))
        f.paint(rr(X, Y, x0 + 0.3, 21.3, x0 + 2.0, 30.7, 0.8), (40, 41, 44))
    f.paint(rr(X, Y, -26, 5.5, 26, 15.5, 0.8), (205, 207, 210))
    text(f, X, -Y, 'DJI POWER AURO 2000 ELITE', 0.0, -14.0, 1.25, (90, 92, 96), FONT_B)
    text(f, X, -Y, 'Rated capacity 2016Wh 51.2V   AC out 220V~ 50Hz 2000W Max   Model DYM2000MH', 0.0, -11.6, 0.62,
         (100, 102, 106), FONT_C)
    text(f, X, -Y, 'USB-C 140W Max (built-in cable)   USB-C 65W x2   USB-A 12W x2   SDC 600W x2', 0.0, -10.0, 0.62,
         (100, 102, 106), FONT_C)
    text(f, X, -Y, 'Designed by DJI   Made in China', 0.0, -8.3, 0.62, (100, 102, 106), FONT_C)


def d_band(f):
    u, Y = front_uv(f)
    nx, ny, nz = f.n
    if nz < -0.95:
        grain(f, np.ones_like(u), 7.0, 14.0)
        # rounded outer corners of the band: paint the shell back in
        out = ~rr(u, Y, BAND[0], BAND[2], BAND[1], BAND[3], 2.4)
        f.paint(out, SHELL)
        f.paint(rr(u, Y, BAND[0] + 0.25, BAND[2] + 0.25, BAND[1] - 0.25, BAND[3] - 0.25, 2.2)
                & ~rr(u, Y, BAND[0] + 0.45, BAND[2] + 0.45, BAND[1] - 0.45, BAND[3] - 0.45, 2.0), (66, 68, 71))
        for uu in (-47.4, -7.4, 48.4):
            for yy in (56.0, 31.2):
                screw(f, u, Y, uu, yy)
        # thin printed labels beside the AC button
    elif abs(nx) > 0.5 or abs(ny) > 0.5:
        out = ~rr(u, Y, BAND[0], BAND[2], BAND[1], BAND[3], 2.4)
        f.paint(out & (f.Z < Z_BAND + 0.4), SHELL)


def d_tray(f):
    u, Y = front_uv(f)
    if f.n[2] < -0.95:
        grain(f, np.ones_like(u), 8.0, 16.0)
        text(f, u, -Y, '10A 250V~    2000W Max  (total)    → AC OUT', -28.5, -38.35, 0.55, (150, 152, 156), FONT_C)


OUTLETS = [(-46.8, -34.8), (-34.5, -22.5), (-21.9, -9.9)]
OUT_Y = (39.4, 50.7)


def d_outlet(f):
    u, Y = front_uv(f)
    if f.n[2] >= -0.95:
        return
    grain(f, np.ones_like(u), 9.0, 18.0)
    for u0, u1 in OUTLETS:
        w, h = u1 - u0, OUT_Y[1] - OUT_Y[0]
        s = (u - u0) / w; t = (OUT_Y[1] - Y) / h
        hole = (24, 24, 26)
        for sc in (0.33, 0.62):                                       # 2-pin
            f.paint(rr(s * w, t * h, (sc - 0.03) * w, 0.17 * h, (sc + 0.03) * w, 0.36 * h, 0.1), hole)
        f.paint(rr(s * w, t * h, 0.445 * w, 0.37 * h, 0.505 * w, 0.55 * h, 0.1), hole)   # earth (3-pin)
        for sc, sg in ((0.31, -1), (0.63, 1)):                       # slanted 3-pin
            cs, ct = sc * w, 0.76 * h
            a = math.radians(32 * sg)
            ss = (s * w - cs) * math.cos(a) + (t * h - ct) * math.sin(a)
            tt = -(s * w - cs) * math.sin(a) + (t * h - ct) * math.cos(a)
            f.paint((np.abs(ss) < 0.33) & (np.abs(tt) < 1.05), hole)


def d_acbtn(f):
    u, Y = front_uv(f)
    if f.n[2] < -0.95:
        f.paint(rr(u, Y, -31.3, 33.0, -26.7, 35.3, 0.5) & ~rr(u, Y, -31.0, 33.3, -27.0, 35.0, 0.4), (90, 92, 96))
        text(f, u, -Y, 'AC', -29.0, -34.15, 1.1, (110, 240, 150), FONT_B, glow=True)


def d_mid(f):
    u, Y = front_uv(f)
    if f.n[2] >= -0.95:
        if abs(f.n[0]) > 0.9 or abs(f.n[1]) > 0.9:
            f.paint(np.ones_like(u, bool), (40, 41, 44))
        return
    grain(f, np.ones_like(u), 8.0, 16.0)
    # recess bezel ring
    f.paint(rr(u, Y, RECESS[0] - 0.35, RECESS[2] - 0.35, RECESS[1] + 0.35, RECESS[3] + 0.35, 1.5)
            & ~rr(u, Y, RECESS[0], RECESS[2], RECESS[1], RECESS[3], 1.2), (74, 76, 80))
    f.paint(rr(u, Y, 1.0, 44.35, 4.6, 44.95, 0.3), (24, 24, 26))           # finger notch
    # USB-A x2 (orange) + USB-C x2
    for a0, a1 in ((8.8, 12.5), (14.1, 17.8)):
        f.paint(rr(u, Y, a0 - 0.2, 39.7, a1 + 0.2, 42.0, 0.3), (30, 30, 32))
        f.paint(rr(u, Y, a0, 39.9, a1, 41.8, 0.2), (238, 122, 36))
        f.paint(rr(u, Y, a0 + 0.35, 40.55, a1 - 0.35, 41.1, 0.1), (250, 160, 80))
    for a0, a1 in ((9.6, 12.8), (15.0, 18.2)):
        f.paint(rr(u, Y, a0, 36.2, a1, 37.5, 0.62), (150, 152, 156))
        f.paint(rr(u, Y, a0 + 0.2, 36.4, a1 - 0.2, 37.3, 0.45), (14, 14, 16))
        f.paint(rr(u, Y, a0 + 0.75, 36.75, a1 - 0.75, 36.95, 0.1), (110, 112, 116))
    lab = (175, 177, 181)
    text(f, u, -Y, 'MAX 12W', 13.3, -42.95, 0.62, lab, FONT_C)
    text(f, u, -Y, 'MAX 65W', 13.9, -35.15, 0.62, lab, FONT_C)
    for yy, s in ((42.95, -1), (35.15, 1)):                          # bracket marks
        for side in (-1, 1):
            uu = 13.6 + side * 4.2
            f.paint((np.abs(u - uu) < 0.06) & (np.abs(Y - (yy + s * 0.25)) < 0.45), lab)
            f.paint((np.abs(Y - (yy + s * 0.65)) < 0.06) & (np.abs(u - (uu - side * 0.35)) < 0.4), lab)


def d_slot_floor(f):
    """the band plate / floor of the cable slot."""
    u, Y = front_uv(f)
    if f.n[2] < -0.95:
        f.paint((u > -3.8) & (u < -2.6) & (np.abs(Y - 50.0) < 1.45), (8, 8, 9))   # cable exit slit
        for uu in (5.2,):                                             # retaining tabs above / below
            f.paint(rr(u, Y, uu - 0.9, 52.0, uu + 0.9, 52.4, 0.1), (66, 68, 71))
            f.paint(rr(u, Y, uu - 0.9, 47.6, uu + 0.9, 48.0, 0.1), (66, 68, 71))


def d_sdc_sq(f):
    u, Y = front_uv(f)
    if f.n[2] < -0.95:
        grain(f, np.ones_like(u), 8.0, 16.0)
        f.paint(rr(u, Y, -0.6, 38.9, 2.8, 40.7, 0.35), (34, 35, 37))
        f.paint(rr(u, Y, -0.6, 36.2, 2.8, 38.0, 0.35), (34, 35, 37))
        for yy in (39.8, 37.1):
            f.paint(rr(u, Y, -0.3, yy - 0.15, 2.5, yy + 0.15, 0.1), (16, 16, 18))
        f.paint(rr(u, Y, 5.9, 36.9, 6.8, 40.9, 0.3), (74, 76, 80))      # pull tab


def d_screen(f):
    u, Y = front_uv(f)
    if f.n[2] >= -0.95:
        return
    disp = rr(u, Y, 24.9, 43.6, 45.3, 54.0, 0.6)
    f.paint(disp, (6, 7, 9))
    W = (236, 238, 240)
    text(f, u, -Y, '100', 31.0, -49.9, 4.6, W, FONT_B, glow=True, shear=0.18)
    text(f, u, -Y, '%', 37.4, -48.1, 1.1, W, FONT_B, glow=True)
    for k, rad in enumerate((0.55, 0.95, 1.35)):                    # wifi icon
        a = np.arctan2(Y - 52.5, u - 26.4)
        d = np.hypot(Y - 52.5, u - 26.4)
        f.paint((np.abs(d - rad) < 0.12) & (np.abs(a - math.pi / 2) < 0.75), (210, 212, 216))
    f.paint((u - 26.4) ** 2 + (Y - 52.5) ** 2 < 0.13 ** 2, (210, 212, 216))
    for k in range(10):                                              # charge bar
        a0 = 25.9 + k * 1.15
        m = rr(u, Y, a0, 45.45, a0 + 0.85, 45.95, 0.1)
        f.paint(m, (246, 146, 40)); f.emit(m, (246, 146, 40))
    f.paint((np.abs(u - 38.7) < 0.06) & (Y > 44.4) & (Y < 53.4), (60, 62, 66))
    f.paint((np.abs(Y - 49.0) < 0.05) & (u > 39.3) & (u < 44.8), (60, 62, 66))
    S = (200, 202, 206)
    text(f, u, -Y, 'INPUT', 40.6, -52.9, 0.62, S, FONT_C, glow=True)
    text(f, u, -Y, 'W', 39.8, -51.9, 0.6, S, FONT_C, glow=True)
    text(f, u, -Y, '0', 44.0, -51.8, 2.0, W, FONT_B, glow=True, shear=0.18)
    text(f, u, -Y, 'OUTPUT', 40.8, -48.2, 0.62, S, FONT_C, glow=True)
    text(f, u, -Y, 'W', 39.8, -47.2, 0.6, S, FONT_C, glow=True)
    text(f, u, -Y, '0', 44.0, -47.1, 2.0, W, FONT_B, glow=True, shear=0.18)
    text(f, u, -Y, 'SOFT', 43.9, -45.0, 0.5, S, FONT_C, glow=True)
    # glass reflection
    f.img[..., :3] += (np.clip(1 - np.abs((u - 23) * 0.35 + (Y - 55) * 0.9 - 0.0) / 1.2, 0, 1) * 14)[..., None] * (~disp)[..., None]


def d_sdc_cover(cu):
    def dec(f):
        u, Y = front_uv(f)
        if f.n[2] >= -0.95:
            return
        grain(f, np.ones_like(u), 8.0, 16.0)
        # embossed arrow-shaped outline with "SDC"
        out = rr(u, Y, cu - 3.6, 35.6, cu + 4.2, 38.8, 0.6)
        inn = rr(u, Y, cu - 3.3, 35.9, cu + 3.9, 38.5, 0.45)
        tip = (u < cu - 2.4) & (np.abs(Y - 37.2) < (u - (cu - 4.6)) * 1.0)
        f.paint((out & ~inn) & (u > cu - 3.0), (80, 82, 86))
        f.paint(tip & ~((np.abs(Y - 37.2) < (u - (cu - 4.3)) * 1.0) & (u > cu - 4.2)), (80, 82, 86))
        text(f, u, -Y, 'SDC', cu + 0.5, -37.2, 1.15, (88, 90, 94), FONT_B)
    return dec


def d_power_btn(f):
    X, Y, Z = f.X, f.Y, f.Z
    if f.n[1] > 0.95:
        u = -X
        f.paint(rr(u, Z, 31.2, -25.5, 38.6, -23.7, 0.85) & ~rr(u, Z, 31.45, -25.3, 38.35, -23.9, 0.7), (176, 178, 181))
        led = (u - 32.5) ** 2 + (Z + 24.6) ** 2 < 0.38 ** 2
        f.paint((u - 32.5) ** 2 + (Z + 24.6) ** 2 < 0.62 ** 2, (150, 220, 170))
        f.paint(led, (120, 255, 150)); f.emit((u - 32.5) ** 2 + (Z + 24.6) ** 2 < 0.62 ** 2, (120, 255, 150))


def d_grille(f):
    pass


def d_plug(f):
    """140 W USB-C plug, built in the stowed pose (tip -> -X, label facing the front -Z)."""
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nz) > 0.95:
        B = -(Y - 50.0) if nz < 0 else (Y - 50.0)
        text(f, -X, B, 'MAX 140W', 5.2, 0.0, 1.05, (118, 120, 124), FONT_C)
        for k in range(4):                                     # grip ribs near the cable end
            xx = -1.05 + k * 0.42
            f.paint(np.abs(X - xx) < 0.1, (58, 59, 62))
        f.paint(np.abs(X + 9.75) < 0.06, (60, 61, 64))
    elif nx < -0.95 and X.max() < -11.9:                       # USB-C receptacle face of the tip
        f.paint(rr(Y, Z, 49.15, -28.08, 50.85, -27.52, 0.25), (30, 30, 32))
        f.paint(rr(Y, Z, 49.45, -27.86, 50.55, -27.74, 0.05), (210, 190, 120))


def d_cable(f):
    """flat ribbon (design frame: length +Y, width X, thickness Z): centre groove + softer edges on the wide faces."""
    L = f.L
    if abs(f.n[2]) > 0.9:
        f.paint(np.abs(L[..., 0]) < 0.07, (18, 18, 20))
        f.paint(np.abs(L[..., 0]) > 0.95, (44, 44, 47))
    elif abs(f.n[0]) > 0.9:
        f.paint(np.ones_like(L[..., 0], bool), (40, 40, 43))


# ============================================================================ BUILD
root = bone('root', (0, 0, 0))
body = bone('body', (0, 0, 0), 'root')

# ---- core + front layer (with the cable slot cut through)
box(body, 'core', (-CORE_X, Y_BOT, Z_CORE), (CORE_X, Y_TOP, HZ), 'shell', D=4, decals=d_shell)
slab_holes(body, 'front', -CORE_X, CORE_X, Y_BOT, Y_TOP, Z_F, Z_CORE,
           [(-SLOT[1], -SLOT[0], SLOT[2], SLOT[3])], 'shell', D=5, decals=d_shell)
# ---- end blocks (both sides), rounded vertical corners, handle cavity under an overhanging ear
for sx in (1, -1):
    s = 'r' if sx > 0 else 'l'
    cx = sx * (41.0 + HX) / 2
    rrect_y(body, f'end_lo_{s}', cx, 0.0, HX - 41.0, 2 * HZ, 4.0, Y_BOT, CAV_Y0, 'shell', D=5, decals=d_shell)
    rrect_y(body, f'end_pf_{s}', cx, -(HZ + CAV_Z) / 2, HX - 41.0, HZ - CAV_Z, 4.0, CAV_Y0, Y_TOP, 'shell', D=5,
            decals=d_shell)
    rrect_y(body, f'end_pb_{s}', cx, (HZ + CAV_Z) / 2, HX - 41.0, HZ - CAV_Z, 4.0, CAV_Y0, Y_TOP, 'shell', D=5,
            decals=d_shell)
    xbox(body, f'end_top_{s}', sx * 41.0, sx * HX, CAV_Y1, Y_TOP, -CAV_Z, CAV_Z, 'shell', D=5, decals=d_shell)
    # cavity inner wall (darker) + louvre grille (vertical fins, as in the photo)
    xbox(body, f'cav_wall_{s}', sx * CORE_X, sx * (CORE_X + 0.3), CAV_Y0, CAV_Y1, -CAV_Z, CAV_Z, 'shell_d', D=5)
    for k in range(19):
        z = -13.5 + k * 1.5
        xbox(body, f'louvre_{s}{k}', sx * (CORE_X + 0.3), sx * (CORE_X + 1.7), CAV_Y0 + 2.4, CAV_Y1 - 1.2,
             z - 0.18, z + 0.18, 'grille', D=6)
    xbox(body, f'louvre_frame_{s}', sx * (CORE_X + 0.3), sx * (CORE_X + 1.1), CAV_Y0 + 1.6, CAV_Y0 + 2.4, -14.6, 14.6,
         'shell_d', D=6)
    # small hook tabs hanging below the cavity edge
    for sz in (1, -1):
        xbox(body, f'hook_{s}{sz}', sx * (HX - 2.2), sx * (HX + 0.25), CAV_Y0 - 3.4, CAV_Y0 + 2.6,
             sz * (CAV_Z - 2.9), sz * (CAV_Z - 1.7), 'shell', D=8)
        obox(body, f'hook_tip_{s}{sz}', (sx * (HX - 0.6), CAV_Y0 - 3.3, sz * (CAV_Z - 2.3)), (1.4, 1.0, 1.2),
             [0, 0, sx * 35.0], 'shell', D=8)
    # ear (handle grip) overhanging the cavity; its underside slopes up towards the outside
    rrect_y(body, f'ear_{s}', sx * (47.0 + EAR_X) / 2, 0.0, EAR_X - 47.0, 2 * CAV_Z + 6.0, 3.5, 57.0, H, 'shell', D=5,
            decals=d_shell)
    ang = math.degrees(math.atan2(57.0 - CAV_Y1, EAR_X - HX))
    Lw = math.hypot(57.0 - CAV_Y1, EAR_X - HX)
    nrm_out = np.array([sx * math.sin(math.radians(ang)), -math.cos(math.radians(ang))])
    mid = np.array([sx * (EAR_X + HX) / 2, (57.0 + CAV_Y1) / 2]) - nrm_out * 0.9
    obox(body, f'ear_wedge_{s}', (mid[0], mid[1], 0.0), (Lw + 0.6, 1.8, 2 * CAV_Z + 5.4), [0, 0, sx * ang], 'shell', D=5)
# lid cap (stepped bevel)
rrect_y(body, 'cap', 0.0, 0.0, 2 * HX - 1.0, 2 * HZ - 1.0, 3.6, Y_TOP, H, 'shell', D=4, decals=d_shell)
# feet
for sx in (1, -1):
    for sz in (1, -1):
        rrect_y(body, f'foot_{sx}{sz}', sx * 44.0, sz * 21.0, 9.0, 7.0, 2.0, 0.0, Y_BOT + 0.05, 'rubber', D=4, n=2)

# ---- dark front band (behind everything), slot cut through
uslab(body, 'band', BAND[0], BAND[1], BAND[2], BAND[3], Z_BAND, Z_F, 'band', holes=[SLOT], D=10, decals=d_band)
# slot floor detail plate (exactly on the core front, inside the slot)
ubox(body, 'slot_floor', SLOT[0], SLOT[1], SLOT[2], SLOT[3], Z_CORE - 0.02, Z_CORE, 'dark', D=16, decals=d_slot_floor)

# ---- AC area: tray + 3 outlets + AC button
urrect(body, 'ac_tray', -48.0, -9.2, 37.4, 51.7, 0.9, Z_PAN, Z_BAND, 'tray', D=10, decals=d_tray)
for i, (u0, u1) in enumerate(OUTLETS):
    urrect(body, f'outlet_{i}', u0, u1, OUT_Y[0], OUT_Y[1], 0.6, Z_PAN - 0.35, Z_PAN, 'outlet', D=12, decals=d_outlet)
urrect(body, 'ac_btn', -31.4, -26.6, 32.9, 35.4, 0.6, Z_BAND - 0.45, Z_BAND, 'dark', D=20, decals=d_acbtn)

# ---- middle panel with the cable recess
uslab(body, 'mid', -5.2, 19.9, 32.6, 55.1, Z_PAN, Z_BAND, 'panel', holes=[RECESS], D=12, decals=d_mid)
urrect(body, 'sdc_sq', -4.7, 7.0, 34.0, 43.6, 1.0, Z_PAN - 0.4, Z_PAN, 'cover', D=12, decals=d_sdc_sq)

# ---- right panel: screen + two SDC covers
urrect(body, 'right', 21.1, 47.4, 32.6, 55.3, 1.0, Z_PAN, Z_BAND, 'panel', D=10)
urrect(body, 'screen', 22.7, 46.4, 42.7, 54.7, 0.9, Z_PAN - 0.15, Z_PAN, 'glass', D=22, decals=d_screen)
for i, (u0, u1) in enumerate(((22.7, 34.1), (35.2, 46.4))):
    urrect(body, f'sdc_cov_{i}', u0, u1, 34.2, 40.4, 1.2, Z_PAN - 0.45, Z_PAN, 'cover', D=12,
           decals=d_sdc_cover((u0 + u1) / 2))
    cu = (u0 + u1) / 2
    ubox(body, f'sdc_tab_{i}', cu - 1.3, cu + 1.3, 40.4, 41.0, Z_PAN - 0.4, Z_PAN, 'cover', D=12)

# ---- power button on the lid (front right)
box(body, 'pwr_btn', (-38.7, H, -25.6), (-31.1, H + 0.3, -23.6), 'btn', D=20, decals=d_power_btn)

# ============================================================================ CABLE (bones)
CAB.build_rest()                                       # rest path / joints / frames
plug_yaw = bone('plug_yaw', CAB.PT_REST, 'root')
plug = bone('plug', CAB.PT_REST, plug_yaw)
PZ, PY = CAB.PT_REST[2], CAB.PT_REST[1]
box(plug, 'plug_tip', (-12.0, PY - 1.03, PZ - 0.31), (-10.5, PY + 1.03, PZ + 0.31), 'metal', D=24, decals=d_plug)
rrect_x(plug, 'plug_neck', PY, PZ, 2.9, 1.3, 0.5, -10.55, -9.7, 'dark', D=20, n=2)
rrect_x(plug, 'plug_body', PY, PZ, 3.5, 1.6, 0.62, -9.75, -0.5, 'plug', D=20, n=2, decals=d_plug)
rrect_x(plug, 'plug_tail', PY, PZ, 2.8, 1.0, 0.42, -0.55, CAB.PT_REST[0], 'plug', D=20, n=2)
parent = plug
for i in range(CAB.N):
    # straight vertical design chain; the bone rest rotation folds it onto the internal reel (stowed pose)
    j0 = CAB.DESIGN_J[i]
    b = bone(f'cab_{i + 1:02d}', tuple(j0), parent, rot=CAB.REST_E[i])
    box(b, f'cab_{i + 1:02d}', (j0[0] - CAB.CW / 2, j0[1] - 0.025, j0[2] - CAB.CT / 2),
        (j0[0] + CAB.CW / 2, j0[1] + CAB.LS + 0.025, j0[2] + CAB.CT / 2), 'cable', D=10, decals=d_cable)
    parent = b

# ============================================================================ ANIMATIONS
ANIMS = CAB.animations()
meta = G.export(OUT, MODEL, MATS, ANIMS, kind='entity', atw=4096, glow=True, bounds=(4, 2), bounds_offset=[0, 0.5, 0])
json.dump(CAB.info(), open(os.path.join(OUT, 'cable_info.json'), 'w'), indent=1)
print('done', OUT)
