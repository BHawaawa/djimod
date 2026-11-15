# -*- coding: utf-8 -*-
"""Redmi K90 (Pro Max, "Sound by Bose") phone as a GeckoLib ITEM.

usage: python3 gen_redmi_k90.py <out_dir> [reference_dir]

Scale: 1 model px = 2 mm (K = 0.5 px/mm).  Real body 163.3 x 77.8 x 7.9 mm -> 81.65 x 38.9 x 3.95 px.
Orientation (same as the RC2 / AURO models): screen faces -Z (north), back + cameras +Z, X = width.
Seen from the FRONT the phone's right edge is -X (power / volume keys); seen from the BACK the camera
plateau is top-left = -X.  Geometry is centred on the item pivot (0,0,0).

Screen layers (all children of bone `screen`):
  scr_home       wallpaper (cropped + rectified from the user's reference render 8.png), rest = visible
  boot_redmi     "REDMI" boot logo (+ child boot_android "Powered by Android")
  boot_hyperos   "Xiaomi HyperOS" boot wordmark
  shutdown_spin  shutdown spinner
  chg_ring/chg_bolt  off-mode charging ring + bolt
  chg_pop        HyperOS plug-in pill (shown over the desktop when the cable is plugged in)
  screen_ui      empty anchor bone at the glass; the Java renderer draws clock / status bar / app icons here
The overlay layers REST 2.2 px inside the body (hidden behind the glass) so the rest pose is a clean desktop;
every animation that shows one keys position [0,0,-2.2] for it.
"""
import sys, os, math, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image
import bbgen as G
import k90_art as A

OUT = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
REF = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'phone', 'redmi_k90', 'reference')
MODEL = 'redmi_k90'

K = 0.5
W, H, T = 77.8 * K, 163.3 * K, 7.9 * K
HW, HH, HT = W / 2, H / 2, T / 2               # 19.45, 40.825, 1.975
R = 10.0 * K                                     # corner radius 10 mm

# ---- screen
SX, SY = HW - 0.90, HH - 0.90                    # visible screen half extents (bezel 1.8 mm incl. glass inset)
SW, SH = 2 * SX, 2 * SY                          # 37.1 x 79.85 px
SR = R - 0.9
TPX = A.TPX
Z_GLASS = -HT                                    # front glass plane
Z_HOME = Z_GLASS - 0.010
Z_OVL = Z_GLASS - 0.022
Z_POP = Z_GLASS - 0.034
Z_PUNCH = Z_GLASS - 0.046
Z_UI = Z_GLASS - 0.058                           # Java overlay plane (bone screen_ui pivot)
HIDE = 2.2                                       # overlay rest offset into the body (+Z)
FWD = [0, 0, -HIDE]

# ---- back
PLATE = dict(x0=-16.75, x1=17.30, y0=17.875, y1=38.475, r=2.85)
PLATE['cx'] = (PLATE['x0'] + PLATE['x1']) / 2; PLATE['cy'] = (PLATE['y0'] + PLATE['y1']) / 2
PLATE['w'] = PLATE['x1'] - PLATE['x0']; PLATE['h'] = PLATE['y1'] - PLATE['y0']
Z_BACK = HT
Z_PL0, Z_PL1, Z_PL2 = Z_BACK - 0.03, Z_BACK + 0.475, Z_BACK + 0.665
Z_RING0, Z_RING1 = Z_PL2 - 0.02, Z_PL2 + 0.40
LENS_R = 4.1
LENSES = [(-11.075, 32.825, 'wide'), (-1.5, 32.825, 'tele'), (-11.075, 23.575, 'ultra'), (-1.5, 23.575, 'flash')]

mats = {
    'frame': ((200, 203, 207), 5.0),
    'glass': ((9, 9, 11), 1.0),
    'back': ((239, 239, 237), 2.0),
    'plate': ((242, 242, 240), 2.0),
    'ring': ((206, 208, 212), 5.0),
    'button': ((196, 199, 203), 4.0),
    'pre': ((0, 0, 0), 0.0),
}


def rrect_x(b, name, cz, cy, d, h, r, x0, x1, mat, D=24, n=3, decals=None):
    """rounded rectangle in the Z-Y plane, extruded along X (pill shaped side keys)."""
    hd, hh = d / 2, h / 2
    r = max(0.05, min(r, hd - 0.01, hh - 0.01))
    step = 90.0 / (n + 1)
    e = r * math.tan(math.radians(step / 2))
    G.box(b, f'{name}_y', (x0, cy - hh + r - e, cz - hd), (x1, cy + hh - r + e, cz + hd), mat, D, decals=decals)
    if hd - r + e > 1e-3:
        G.box(b, f'{name}_z', (x0, cy - hh, cz - hd + r - e), (x1, cy + hh, cz + hd - r + e), mat, D, decals=decals)
    seg = 2 * r * math.tan(math.radians(step / 2)) + 0.004
    for sz in (1, -1):
        for sy in (1, -1):
            cc = ((x0 + x1) / 2, cy + sy * (hh - r), cz + sz * (hd - r))
            for k in range(1, n + 1):
                a = -step * k if sz * sy > 0 else step * k
                G.obox(b, f'{name}_c{"p" if sz > 0 else "m"}{"p" if sy > 0 else "m"}{k}', cc, (x1 - x0, seg, 2 * r),
                       [G.norm_ang(a), 0, 0], mat, D=D, decals=decals)


def flat(b, name, x0, x1, y0, y1, z, rgba, face='north', glow=True, glow_alpha=255):
    """thin cube carrying one pre-baked face (RGBA image as seen by the viewer of that face)."""
    rgba = np.ascontiguousarray(rgba, np.uint8)
    gl = np.zeros_like(rgba)
    if glow:
        gl[..., :3] = rgba[..., :3]; gl[..., 3] = np.where(rgba[..., 3] > 0, glow_alpha, 0)
    if face == 'north':
        c = G.box(b, name, (x0, y0, z), (x1, y1, z + 0.006), 'pre')
    else:
        c = G.box(b, name, (x0, y0, z - 0.006), (x1, y1, z), 'pre')
    c['pre'] = {f: None for f in G.FACES}
    c['pre'][face] = (rgba, gl)
    return c


def tex_size(w, h, tpx=TPX):
    return int(round(w * tpx)), int(round(h * tpx))


# ============================================================================ decals (world-space painting)
def frame_decal(f):
    X, Y, Z = f.X, f.Y, f.Z
    n = f.n
    # brushed aluminium: soft convex highlight across the band + fine horizontal brushing
    if abs(n[2]) < 0.5:
        f.img[..., :3] *= (0.94 + 0.10 * np.clip(1 - (Z / 1.9) ** 2, 0, 1))[..., None]
    side = abs(n[0]) > 0.9; topbot = abs(n[1]) > 0.9
    band = (150, 153, 158)
    if side:
        f.paint((np.abs(Y - 26.2) < 0.13) | (np.abs(Y + 30.4) < 0.13), band)
    if topbot and n[1] > 0:
        f.paint((np.abs(X - 11.7) < 0.13) | (np.abs(X + 10.5) < 0.13), band)
    if topbot and n[1] < 0:
        f.paint((np.abs(X - 12.4) < 0.13) | (np.abs(X + 14.4) < 0.13), band)
    if n[1] < -0.9:                                   # bottom: speaker | USB-C | mic | SIM tray
        d = G.rr_dist(X, Z, -2.1, -0.62, 2.1, 0.62, 0.6)
        f.paint((d < 0.07) & (d >= 0), (120, 123, 128))
        f.paint(d < 0, (16, 16, 18))
        f.paint((np.abs(X) < 1.35) & (np.abs(Z) < 0.16), (66, 68, 72))
        for i in range(6):
            x = 4.8 + i * 1.12
            f.paint((X - x) ** 2 + Z ** 2 < 0.30 ** 2, (20, 20, 22))
        f.paint((X + 3.4) ** 2 + Z ** 2 < 0.2 ** 2, (20, 20, 22))
        ds = G.rr_dist(X, Z, -13.9, -0.58, -6.9, 0.58, 0.58)
        f.paint(np.abs(ds) < 0.05, (118, 121, 126))
        f.paint((X + 13.15) ** 2 + Z ** 2 < 0.16 ** 2, (30, 30, 32))
    if n[1] > 0.9:                                    # top: mic / IR / speaker holes
        for x in (4.2, 5.0, 5.8, 6.6):
            f.paint((X - x) ** 2 + Z ** 2 < 0.2 ** 2, (24, 24, 26))
    if n[0] < -0.9:                                   # grooves around the keys
        for (y0, y1) in ((9.475, 21.825), (-0.225, 6.175)):
            d = G.rr_dist(Z, Y, -0.72, y0 - 0.1, 0.72, y1 + 0.1, 0.72)
            f.paint(np.abs(d) < 0.05, (128, 131, 136))


def glass_decal(f):
    if f.n[2] < -0.9:
        X, Y = f.X, f.Y
        top = HH - 0.30
        f.paint((np.abs(X) < 3.2) & (Y > top - 0.22) & (Y < top - 0.10), (48, 48, 52))      # earpiece slit
        # faint reflection streak so the black glass reads as glass when the screen is off
        u = (X * 0.55 + Y) / 10.0
        f.img[..., :3] += (14 * np.exp(-((u - 1.4) / 0.35) ** 2))[..., None]


def back_decal(f):
    if f.n[2] > 0.9:
        X, Y = f.X, f.Y
        u = (X * 0.8 - Y) / 30.0                       # broad soft gloss band (pearl white glass)
        f.img[..., :3] *= (1.0 + 0.025 * np.exp(-((u - 0.2) / 0.5) ** 2) - 0.03 * np.clip(-Y / 40.0, 0, 1))[..., None]


def plate_decal(f):
    if f.n[2] > 0.9:
        X, Y = f.X, f.Y
        d = G.rr_dist(X, Y, PLATE['x0'], PLATE['y0'], PLATE['x1'], PLATE['y1'], PLATE['r'])
        f.img[..., :3] *= (1.0 - 0.05 * np.clip(1 + d / 0.5, 0, 1))[..., None]     # darken towards the bevel


# ============================================================================ prebaked art
def lens_cap(kind, S):
    """top of one camera: silver rim, black glass, lens elements / flash. S texels square, viewer = back (+Z)."""
    ss = 3; N = S * ss
    y, x = (np.mgrid[0:N, 0:N] + 0.5) / N * 2 - 1           # -1..1, x right (= +X from the back), y down
    r = np.hypot(x, y)
    ang = np.arctan2(-y, x)
    img = np.zeros((N, N, 4))
    rim = (r <= 1.0) & (r > 0.78)
    light = 0.82 + 0.22 * np.cos(ang - math.radians(135))
    img[rim, :3] = (np.array([222, 224, 228])[None, :] * light[rim][:, None])
    img[(r > 0.86) & (r <= 0.885), :3] = (170, 172, 176)            # machined step on the bezel
    img[(r <= 0.78) & (r > 0.74), :3] = (34, 34, 38)
    glass = r <= 0.74
    img[glass, :3] = (10, 10, 14)
    if kind != 'flash':
        img[(r > 0.56) & (r <= 0.60), :3] = (40, 40, 52)
        el = r <= (0.42 if kind != 'tele' else 0.34)
        img[el, :3] = (20, 17, 34)
        img[(r <= 0.19), :3] = (44, 36, 76)
        hl = np.hypot(x + 0.16, y + 0.16) <= 0.11
        img[hl, :3] = (150, 126, 210)
        hl2 = np.hypot(x - 0.22, y - 0.20) <= 0.05
        img[hl2, :3] = (90, 80, 140)
        sheen = glass & (np.abs((x - y) * 0.7) < 0.07) & (r > 0.46)
        img[sheen, :3] = img[sheen, :3] * 0.5 + 30
    else:
        led = r <= 0.22
        img[led, :3] = (236, 230, 196)
        img[(r > 0.22) & (r <= 0.30), :3] = (120, 116, 100)
        img[(r > 0.30) & (r <= 0.33), :3] = (40, 40, 44)
        for (px, py, pr) in ((0.0, -0.52, 0.08), (-0.52, 0.0, 0.08), (0.52, 0.0, 0.065)):
            img[np.hypot(x - px, y - py) <= pr, :3] = (64, 64, 70)
    img[r <= 1.0, 3] = 255
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').resize((S, S), Image.LANCZOS)
    a = np.asarray(im).copy(); a[..., 3] = np.where(a[..., 3] > 110, 255, 0)
    return a


def logo_redmi(w, h, bg=(239, 239, 237)):
    tw, th = tex_size(w, h, 64)
    im = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
    t = A.text_img('REDMI', A.F_POP_SB, th * 3, color=(132, 134, 139), tracking=0.07)
    A.fit_center(im, t, tw / 2, th / 2, max_w=tw, max_h=th)
    return A.over(np.full((th, tw, 3), bg, np.uint8), im)


def logo_bose(w, h, bg=(242, 242, 240)):
    tw, th = tex_size(w, h, 80)
    im = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
    col = (78, 78, 84)
    t1 = A.text_img('SOUND BY', A.F_POP_M, th * 1.2, color=col, tracking=0.12)
    A.fit_center(im, t1, tw * 0.56, th * 0.22, max_w=tw * 0.62, max_h=th * 0.34)
    t2 = A.text_img('BOSE', A.F_POP_BI, th * 2, color=col, tracking=0.04, italic_shear=-0.18)
    A.fit_center(im, t2, tw * 0.52, th * 0.68, max_w=tw * 0.96, max_h=th * 0.58)
    return A.over(np.full((th, tw, 3), bg, np.uint8), im)


# ============================================================================ build
G.bone('phone', (0, 0, 0))
G.bone('body', (0, 0, 0), 'phone')
G.bone('keys', (-HW, 0, 0), 'phone')
G.bone('screen', (0, 0, Z_GLASS), 'phone')
G.bone('scr_home', (0, 0, Z_HOME), 'screen')
G.bone('boot_redmi', (0, 1.0, Z_OVL + HIDE), 'screen')
G.bone('boot_android', (0, SY - (SH - 6.0), Z_OVL + HIDE), 'boot_redmi')
G.bone('boot_hyperos', (0, 0.5, Z_OVL + HIDE), 'screen')
G.bone('shutdown_spin', (0, 0, Z_OVL + HIDE), 'screen')
RING_Y = SY - A.LAYOUT['charge_ring_y']
G.bone('chg_ring', (0, RING_Y, Z_OVL + HIDE), 'screen')
G.bone('chg_bolt', (0, RING_Y, Z_OVL + HIDE - 0.006), 'screen')
POP_Y = SY - A.LAYOUT['pill_y']
G.bone('chg_pop', (0, POP_Y, Z_POP + HIDE), 'screen')
G.bone('screen_ui', (0, 0, Z_UI), 'screen')

# ---- body shell: front glass | front edge | aluminium frame | back edge | back glass
G.rrect('body', 'glass', 0, 0, W - 0.60, H - 0.60, R - 0.30, -HT, -1.80, 'glass', D=4, n=5, decals=glass_decal)
G.rrect('body', 'fedge', 0, 0, W - 0.20, H - 0.20, R - 0.10, -1.80, -1.60, 'frame', D=10, n=5, decals=frame_decal)
G.rrect('body', 'frame', 0, 0, W, H, R, -1.60, 1.60, 'frame', D=12, n=5, decals=frame_decal)
G.rrect('body', 'bedge', 0, 0, W - 0.20, H - 0.20, R - 0.10, 1.60, 1.80, 'frame', D=10, n=5, decals=frame_decal)
G.rrect('body', 'back', 0, 0, W - 0.64, H - 0.64, R - 0.32, 1.80, HT, 'back', D=8, n=5, decals=back_decal)

# ---- camera plateau + lenses
G.rrect('body', 'plate0', PLATE['cx'], PLATE['cy'], PLATE['w'], PLATE['h'], PLATE['r'], Z_PL0, Z_PL1, 'plate', D=10, n=4,
        decals=plate_decal)
G.rrect('body', 'plate1', PLATE['cx'], PLATE['cy'], PLATE['w'] - 0.44, PLATE['h'] - 0.44, PLATE['r'] - 0.22, Z_PL1, Z_PL2,
        'plate', D=10, n=4, decals=plate_decal)
for i, (lx, ly, kind) in enumerate(LENSES):
    G.rrect('body', f'lens{i}_ring', lx, ly, 2 * LENS_R, 2 * LENS_R, LENS_R - 0.01, Z_RING0, Z_RING1, 'ring', D=10, n=5)
    S = int(2 * LENS_R * 36)
    flat('body', f'lens{i}_cap', lx - LENS_R, lx + LENS_R, ly - LENS_R, ly + LENS_R, Z_RING1 + 0.004, lens_cap(kind, S),
         face='south', glow=False)

# ---- logos
flat('body', 'logo_redmi', -4.45, 4.45, -27.97, -26.22, Z_BACK + 0.004, logo_redmi(8.9, 1.75), face='south', glow=False)
flat('body', 'logo_bose', 5.4, 13.3, 26.85, 29.25, Z_PL2 + 0.004, logo_bose(7.9, 2.4), face='south', glow=False)

# ---- side keys (-X side = right edge seen from the front)
rrect_x('keys', 'vol', 0, (9.475 + 21.825) / 2, 1.30, 21.825 - 9.475, 0.64, -HW - 0.22, -HW + 0.15, 'button', D=24, n=3)
rrect_x('keys', 'pwr', 0, (-0.225 + 6.175) / 2, 1.30, 6.175 + 0.225, 0.64, -HW - 0.22, -HW + 0.15, 'button', D=24, n=3)

# ---- screen layers
tw, th = tex_size(SW, SH)
wall = A.wallpaper(os.path.join(REF, '8.png'), tw, th, SR * TPX)
flat('scr_home', 'wallpaper', -SX, SX, -SY, SY, Z_HOME, wall)

bw, bh = 16.0, 3.4
flat('boot_redmi', 'logo', -bw / 2, bw / 2, 1.0 - bh / 2, 1.0 + bh / 2, Z_OVL + HIDE, A.boot_redmi(*tex_size(bw, bh, 24)))
ay = SY - (SH - 6.0)
flat('boot_android', 'text', -7.0, 7.0, ay - 0.7, ay + 0.7, Z_OVL + HIDE, A.boot_android(*tex_size(14.0, 1.4, 24)))
hw_, hh_ = 24.0, 3.2
flat('boot_hyperos', 'logo', -hw_ / 2, hw_ / 2, 0.5 - hh_ / 2, 0.5 + hh_ / 2, Z_OVL + HIDE, A.boot_hyperos(*tex_size(hw_, hh_, 24)))
sp = 7.0
flat('shutdown_spin', 'arc', -sp / 2, sp / 2, -sp / 2, sp / 2, Z_OVL + HIDE, A.spinner(int(sp * 24)))
rg = 20.0
flat('chg_ring', 'ring', -rg / 2, rg / 2, RING_Y - rg / 2, RING_Y + rg / 2, Z_OVL + HIDE, A.charge_ring(int(rg * 16)))
bw2, bh2 = 3.4, 5.6
flat('chg_bolt', 'bolt', -bw2 / 2, bw2 / 2, RING_Y - bh2 / 2, RING_Y + bh2 / 2, Z_OVL + HIDE - 0.006, A.bolt(*tex_size(bw2, bh2, 24)))
pw, ph = 12.0, 3.4
pt0 = int(round((A.LAYOUT['pill_y'] - ph / 2) * TPX)); pl0 = int(round((SW / 2 - pw / 2) * TPX))
ptw, pth = tex_size(pw, ph)
bg = wall[pt0:pt0 + pth, pl0:pl0 + ptw, :3]
flat('chg_pop', 'pill', -pw / 2, pw / 2, POP_Y - ph / 2, POP_Y + ph / 2, Z_POP + HIDE, A.charge_pill(ptw, pth, bg))

flat('body', 'punch', -0.85, 0.85, 37.3 - 0.85, 37.3 + 0.85, Z_PUNCH, A.punch_hole(int(1.7 * 48)), glow=False)


# ============================================================================ animations
def K_(t, v, ip='linear'):
    return (round(t, 4), list(v) if isinstance(v, (list, tuple)) else [v, v, v], ip)


def sc(*pairs):
    return [K_(t, [s, s, s]) for t, s in pairs]


OVL = ['boot_redmi', 'boot_hyperos', 'shutdown_spin', 'chg_ring', 'chg_bolt']
anims = {}
anims['screen_off'] = dict(loop='hold', length=0.1, bones={
    'scr_home': {'scale': sc((0, 0))}, 'screen_ui': {'scale': sc((0, 0))}})
anims['home'] = dict(loop='hold', length=0.1, bones={
    'scr_home': {'scale': sc((0, 1))}, 'screen_ui': {'scale': sc((0, 1))}})
anims['boot'] = dict(loop='hold', length=4.0, bones={
    'scr_home': {'scale': sc((0, 0), (3.0, 0), (3.01, 0.86), (3.25, 0.95), (3.6, 1.0))},
    'screen_ui': {'scale': sc((0, 0), (3.55, 0), (3.56, 1))},
    'boot_redmi': {'position': [K_(0, FWD)], 'scale': sc((0, 0), (0.3, 0), (0.31, 0.92), (1.7, 1.0), (1.72, 0))},
    'boot_hyperos': {'position': [K_(0, FWD)], 'scale': sc((0, 0), (1.85, 0), (1.86, 0.7), (2.4, 0.98), (2.95, 1.02), (3.0, 0))},
})
spin = [K_(0.3 + 0.35 * k, [0, 0, -90 * k]) for k in range(5)]
anims['power_off'] = dict(loop='hold', length=2.0, bones={
    'scr_home': {'scale': sc((0, 1), (0.3, 0))},
    'screen_ui': {'scale': sc((0, 0))},
    'shutdown_spin': {'position': [K_(0, FWD)], 'rotation': spin, 'scale': sc((0, 0), (0.3, 0), (0.31, 1), (1.7, 1), (1.71, 0))},
})
anims['charge_off'] = dict(loop='loop', length=2.0, bones={
    'scr_home': {'scale': sc((0, 0))},
    'screen_ui': {'scale': sc((0, 1))},
    'chg_ring': {'position': [K_(0, FWD)], 'rotation': [K_(0.5 * k, [0, 0, -90 * k]) for k in range(5)]},
    'chg_bolt': {'position': [K_(0, FWD)], 'scale': sc((0, 1), (1.0, 1.12), (2.0, 1))},
})
anims['charge_plug'] = dict(loop='hold', length=2.2, bones={
    'chg_pop': {'position': [K_(0, FWD)], 'scale': sc((0, 0), (0.22, 1.12), (0.36, 1), (1.8, 1), (2.1, 0))},
})

display = {
    'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 4.0, 1.2], 'scale': [0.20, 0.20, 0.20]},
    'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 4.0, 1.2], 'scale': [0.20, 0.20, 0.20]},
    'firstperson_righthand': {'rotation': [-8, 180, 0], 'translation': [-1.5, 4.5, -2.0], 'scale': [0.16, 0.16, 0.16]},
    'firstperson_lefthand': {'rotation': [-8, 180, 0], 'translation': [-1.5, 4.5, -2.0], 'scale': [0.16, 0.16, 0.16]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3.0, 0], 'scale': [0.12, 0.12, 0.12]},
    'gui': {'rotation': [8, 160, 0], 'translation': [0, 0, 0], 'scale': [0.172, 0.172, 0.172]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, -6], 'scale': [0.3, 0.3, 0.3]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.18, 0.18, 0.18]},
}

anims = {f'animation.{MODEL}.{k}': v for k, v in anims.items()}
meta = G.export(OUT, MODEL, mats, anims, kind='item', offset=(0, 0, 0), display=display, atw=2048, glow=True,
                bounds=(6, 6), bounds_offset=[0, 0, 0])

layout = dict(px_per_mm=K, body_mm=[77.8, 163.3, 7.9], screen=dict(half_x=SX, half_y=SY, w=SW, h=SH, corner_r=SR,
              z_glass=Z_GLASS, z_ui=Z_UI, note='screen px (sx,sy) from top-left seen from the front -> model X = half_x - sx, Y = half_y - sy'),
              overlay_rest_offset_z=HIDE, ui=A.LAYOUT, ring_center_y=RING_Y, pill_center_y=POP_Y,
              usb_c=dict(center=[0, -HH, 0], normal=[0, -1, 0], size=[4.2, 1.24]),
              lenses=[dict(x=x, y=y, kind=k, r=LENS_R) for x, y, k in LENSES], plate=PLATE,
              default_grid=A.DEFAULT_GRID, default_dock=A.DEFAULT_DOCK)
json.dump(layout, open(os.path.join(OUT, 'screen_layout.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('done', meta['bbox_min'], meta['bbox_max'])
