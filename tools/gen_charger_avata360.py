# -*- coding: utf-8 -*-
"""Procedural generator: DJI Avata 360 Two-Way Charging Hub (3 slots) -> GeckoLib BLOCK ENTITY model.

Reference: DJI product renders (user images 1-5).  1 model px = 8 mm, i.e. the same scale as the Avata 360 drone
entity, so the three batteries are the battery item model (4 mm/px) merged at scale 0.5, exactly like in the drone.

Frame (Blockbench, GeckoLib block): origin = block bottom centre, +Y up, front = -Z (north; GeoBlockRenderer turns
it to the block's FACING).  Batteries stand cap-up, label face toward +X, latches toward the front/back walls.
Hub: 13.3 x 7.0 px footprint, rim at 15.95 px, cap tops at ~18.9 px (a little taller than one block, like the real
hub, which is ~10 cm wide / ~15 cm tall with batteries).

usage: python3 gen_charger_avata360.py <out_dir> [battery_model_dir]
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bbgen as G
from bbgen import bone, box, xbox, rrect, rrect_y, rr, rr_dist
import hub_common as H

OUT = sys.argv[1] if len(sys.argv) > 1 else 'charger_avata_build'
BAT_DIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'battery', 'avata360')
os.makedirs(OUT, exist_ok=True)
MODEL = 'avata360_charging_hub'
P = f'animation.{MODEL}.'

# ============================================================================ dimensions (model px, 8 mm/px)
W, D, RC = 13.3, 7.0, 0.42             # outer width (X) / depth (Z) / vertical corner radius
Y_F, Y_R = 4.9, 15.95                  # pocket floor / rim top (= battery cap seat)
BUMP_H = 0.6                           # rounded tongues on front & back rim at each slot
PITCH = 4.28
XS = [-PITCH, 0.0, PITCH]              # slot centres (slot 1 = -X)
T_END, T_DIV, T_FB = 0.355, 0.25, 0.5  # end wall / divider / front-back wall thickness
ZI = D / 2 - T_FB                      # inner half depth (3.0)
WIN_HW, WIN_Y0, WIN_Y1, WIN_R = 0.9, 5.2, 11.95, 0.45   # see-through windows (front and back)
BAT_S = 0.5
BAT_CY = Y_R - 8.1 * BAT_S             # battery item origin height (cap/body joint at z=-8.1 sits on the rim)
LIFT = 12.5                            # insert start height above the seat

HUB = (124, 126, 128)
MATS = {
    'hub':  (HUB, 2.0),
    'dark': ((44, 45, 47), 1.0),
    'port': ((30, 30, 32), 0.6),
    'btn':  ((118, 120, 122), 1.6),
    'led':  ((146, 148, 150), 1.2),
    'label': ((22, 22, 24), 0.6),
}

# ============================================================================ decal art
LOGO = np.asarray(H.dji_logo_mask(120), float) / 255.0          # front logo (embossed)
LOGO_C, LOGO_W = (5.92, 10.1), 1.02                              # centre (x, y) / width on the front face
LOGO_H = LOGO_W * LOGO.shape[0] / LOGO.shape[1]
LBL_W, LBL_D = 9.0, 4.9
LBL = H.hub_label(1080, int(1080 * LBL_D / LBL_W), 'AVATA 360 TWO-WAY CHARGING HUB', compat='DJI Avata 360',
                  qr_data='https://www.dji.com/avata-360', serial='')
Image.fromarray(LBL.astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'label_art.png'))
PORT_C = (5.25, 0.78)        # USB-C (x, y) on the front face (viewer's lower-left = +X)
LED_X, BTN_X = 4.12, 3.5


def sample(img, A, B, a0, a1, b0, b1):
    h, w = img.shape[:2]
    u = (A - a0) / (a1 - a0) * w; v = (B - b0) / (b1 - b0) * h
    ins = (u >= 0) & (u < w) & (v >= 0) & (v < h)
    iu = np.clip(u.astype(int), 0, w - 1); iv = np.clip(v.astype(int), 0, h - 1)
    return img[iv, iu], ins


def in_pocket(X, Y, Z, e=0.02):
    return (np.abs(Z) < ZI + e) & (np.abs(X) < W / 2 - T_END + e) & (Y > Y_F - e)


def paint_hub(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    pocket = in_pocket(X, Y, Z)
    if pocket.any():                                   # inside the battery pockets: darker the deeper it goes
        depth = np.clip((Y - Y_F) / (Y_R - Y_F), 0, 1)
        f.shade(pocket, 1.0)
        f.img[..., :3] *= np.where(pocket, 0.5 + 0.42 * depth, 1.0)[..., None]
        if ny > 0.9:                                   # pocket floor: 8-pin charging socket per slot
            for x0 in XS:
                f.paint(rr(Z, X, -1.25, x0 - 0.42, 1.25, x0 + 0.42, 0.15), (26, 26, 28))
                for k in range(8):
                    z = -0.98 + k * 0.28
                    f.paint(rr(Z, X, z - 0.07, x0 - 0.22, z + 0.07, x0 + 0.22, 0.03), (190, 160, 88))
        return
    if abs(nz) > 0.9 and np.abs(Z).max() > D / 2 - 0.01:      # outer front / back face
        front = nz < 0
        for x0 in XS:                                  # tongue scoops: soft trapezoid recess under each bump
            hw = 1.25 + 0.45 * np.clip((Y - 13.4) / (Y_R + BUMP_H - 13.4), 0, 1)
            m = (np.abs(X - x0) < hw) & (Y > 13.4)
            f.shade(m, 0.93)
            edge = (np.abs(np.abs(X - x0) - hw) < 0.05) & (Y > 13.4)
            f.paint(edge, (108, 110, 112), 0.8)
            f.paint((np.abs(np.abs(X - x0) - hw - 0.06) < 0.03) & (Y > 13.4), (150, 152, 154), 0.6)
            f.paint((np.abs(Y - 13.4) < 0.04) & (np.abs(X - x0) < 1.25), (112, 114, 116), 0.7)
        if front:
            # embossed dji logo
            a, ins = sample(LOGO, -X, -Y, -(LOGO_C[0] + LOGO_W / 2), -(LOGO_C[0] - LOGO_W / 2),
                            -(LOGO_C[1] + LOGO_H / 2), -(LOGO_C[1] - LOGO_H / 2))
            a = a * ins
            a2, ins2 = sample(LOGO, -X, -Y + 0.035, -(LOGO_C[0] + LOGO_W / 2), -(LOGO_C[0] - LOGO_W / 2),
                              -(LOGO_C[1] + LOGO_H / 2), -(LOGO_C[1] - LOGO_H / 2))
            f.paint(np.clip(a2 * ins2 - a, 0, 1), (158, 160, 162))
            f.paint(a, (112, 114, 116))
            # USB-C port, LED slit, button recess
            px, py = PORT_C
            f.paint(rr(X, Y, px - 0.62, py - 0.25, px + 0.62, py + 0.25, 0.22), (96, 98, 100))
            f.paint(rr(X, Y, px - 0.55, py - 0.19, px + 0.55, py + 0.19, 0.18), (14, 14, 15))
            f.paint(rr(X, Y, px - 0.36, py - 0.05, px + 0.36, py + 0.05, 0.04), (70, 72, 74))
            f.paint(rr(X, Y, LED_X - 0.06, py - 0.2, LED_X + 0.06, py + 0.2, 0.05), (40, 42, 44))
            f.paint(np.abs(rr_dist(X, Y, BTN_X - 0.36, py - 0.32, BTN_X + 0.36, py + 0.32, 0.12)) < 0.04, (80, 82, 84))
    elif ny > 0.9:                                     # rim tops: slightly lighter
        f.shade(Y > Y_F + 1, 1.05)
    elif ny < -0.9:
        f.shade(Y < 0.2, 0.8)
    # window reveal (inside the hole, through the front / back walls)
    if ((abs(nx) > 0.9 or abs(ny) > 0.9) and (np.abs(Z) > ZI - 0.01).all() and (np.abs(Z) < D / 2 + 0.01).all()
            and (Y > WIN_Y0 - 0.01).all() and (Y < WIN_Y1 + 0.01).all()):
        f.shade(np.ones_like(X, bool), 0.72)


def paint_label(f):
    if f.n[1] < -0.9:
        col, ins = sample(LBL, -f.X, f.Z, -LBL_W / 2, LBL_W / 2, -LBL_D / 2, LBL_D / 2)   # read from below, front edge up
        a = (col[..., 3] / 255.0) * ins
        f.paint(a, col[..., :3])


def paint_led(f):
    if abs(f.n[2]) > 0.9 or abs(f.n[0]) > 0.9 or abs(f.n[1]) > 0.9:
        r = np.sqrt(f.L[..., 0] ** 2 + f.L[..., 1] ** 2)       # cube-local: circle on the end face
        f.paint(r < 0.105, (150, 255, 170))
        f.paint((r >= 0.105) & (r < 0.13), (70, 200, 110))
        f.emit(r < 0.13, (90, 255, 130))


def paint_power_led(f):
    f.paint(np.ones_like(f.X, bool), (120, 255, 150))
    f.emit(np.ones_like(f.X, bool), (90, 255, 130))


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('hub', (0, 0, 0), 'root')
HD = 24
# ---- base (solid, rounded vertical edges) + bottom label
rrect_y('hub', 'base', 0, 0, W, D, RC, 0.02, Y_F, 'hub', D=HD, n=3, decals=paint_hub)
box('hub', 'label', (-LBL_W / 2, 0.0, -LBL_D / 2), (LBL_W / 2, 0.02, LBL_D / 2), 'label', D=72, decals=paint_label)

# ---- corner columns + end walls
for sx in (1, -1):
    for sz in (1, -1):
        rrect_y('hub', f'col_{sx}{sz}', sx * (W / 2 - RC), sz * (D / 2 - RC), 2 * RC, 2 * RC, RC - 0.01, Y_F, Y_R, 'hub',
                D=HD, n=3, decals=paint_hub)
    xbox('hub', f'end_{sx}', sx * W / 2, sx * (W / 2 - T_END), Y_F, Y_R, -(D / 2 - RC), D / 2 - RC, 'hub', D=HD,
         decals=paint_hub)

# ---- dividers (slightly below the rim)
for xd in (-PITCH / 2, PITCH / 2):
    xbox('hub', f'div_{xd:+.1f}', xd - T_DIV / 2, xd + T_DIV / 2, Y_F, Y_R - 0.25, -ZI, ZI, 'hub', D=HD, decals=paint_hub)

# ---- front / back walls with the three see-through windows
XA = W / 2 - RC
for sz, tag in ((-1, 'f'), (1, 'b')):
    z0, z1 = sz * D / 2, sz * ZI
    xbox('hub', f'w{tag}_low', -XA, XA, Y_F, WIN_Y0, z0, z1, 'hub', D=HD, decals=paint_hub)
    xbox('hub', f'w{tag}_top', -XA, XA, WIN_Y1, Y_R, z0, z1, 'hub', D=HD, decals=paint_hub)
    edges = [-XA] + [v for x0 in XS for v in (x0 - WIN_HW, x0 + WIN_HW)] + [XA]
    for k in range(0, len(edges), 2):
        xbox('hub', f'w{tag}_pill{k // 2}', edges[k], edges[k + 1], WIN_Y0, WIN_Y1, z0, z1, 'hub', D=HD, decals=paint_hub)
    # rounded window corners: 3-step fillets
    for i, x0 in enumerate(XS):
        for k in range(3):
            h0, h1 = WIN_R * k / 3, WIN_R * (k + 1) / 3
            wv = WIN_R - math.sqrt(max(0.0, WIN_R ** 2 - (WIN_R - (h0 + h1) / 2) ** 2))
            for sy, yb in ((1, WIN_Y0), (-1, WIN_Y1)):
                ya, yb2 = (yb + h0, yb + h1) if sy > 0 else (yb - h1, yb - h0)
                for sxx in (1, -1):
                    xe = x0 + sxx * WIN_HW
                    xbox('hub', f'w{tag}_fil{i}{k}{sy}{sxx}', xe, xe - sxx * wv, ya, yb2, z0, z1, 'hub', D=HD,
                         decals=paint_hub)
    # tongues on the rim (rounded trapezoid, 4 layers)
    for i, x0 in enumerate(XS):
        for k, (hw, hh) in enumerate(((1.7, 0.18), (1.6, 0.17), (1.45, 0.14), (1.2, 0.11))):
            y0 = Y_R + sum(h for _, h in ((1.7, 0.18), (1.6, 0.17), (1.45, 0.14), (1.2, 0.11))[:k])
            xbox('hub', f'w{tag}_tongue{i}{k}', x0 - hw, x0 + hw, y0, y0 + hh, z0, z1, 'hub', D=HD, decals=paint_hub)
    # vertical ribs on the inner face (beside the windows)
    for i, x0 in enumerate(XS):
        for sxx in (1, -1):
            xr = x0 + sxx * 1.3
            xbox('hub', f'w{tag}_rib{i}{sxx}', xr - 0.06, xr + 0.06, Y_F, Y_R - 0.6, z1, z1 - sz * 0.1, 'hub', D=HD,
                 decals=paint_hub)

# ---- front port details
px, py = PORT_C
box('hub', 'button', (BTN_X - 0.3, py - 0.26, -D / 2 - 0.05), (BTN_X + 0.3, py + 0.26, -D / 2 + 0.01), 'btn', D=40)
bone('power_led', (LED_X, py, -D / 2), 'hub')
box('power_led', 'power_led', (LED_X - 0.05, py - 0.17, -D / 2 - 0.012), (LED_X + 0.05, py + 0.17, -D / 2 + 0.01), 'led',
    D=40, decals=paint_power_led)

# ---- batteries (merged item model) + LED overlays on each cap
slots = [(f'slot{i + 1}', f's{i + 1}', (x0, BAT_CY, 0.0)) for i, x0 in enumerate(XS)]
H.merge_battery_slots(BAT_DIR, 'avata360_battery', slots, BAT_S, 'hub', DS=2, keep_glow=False)
BAT_LED_X = [0.55, 1.45, 2.35, 3.25]      # battery item Blockbench x of the 4 charge LEDs (cap end face, z = -14)
LEDS = {}
for i, (sb, pre_, C) in enumerate(slots):
    C = np.array(C)
    LEDS[sb] = []
    for k, lx in enumerate(BAT_LED_X):
        c = C + BAT_S * np.array([lx, 0, -14.0])
        bn = bone(f'{pre_}_led{k + 1}', list(c), sb)
        box(bn, bn, c + [-0.13, -0.13, -0.022], c + [0.13, 0.13, 0.004], 'led', D=96, decals=paint_led)
        LEDS[sb].append(bn)

# ============================================================================ ANIMATIONS
ANIMS = {f'{P}idle': dict(loop='loop', length=1.0, bones={'hub': {'position': H.kf((0.0, [0, 0, 0]))}})}
for i, (sb, pre_, C) in enumerate(slots):
    ANIMS.update(H.slot_anims(P, i + 1, sb, LEDS[sb], LIFT,
                              press_bones=[(f'{pre_}_latch_r', 1), (f'{pre_}_latch_l', -1)], press_dx=0.11))
ANIMS.update(H.demo_cycle(P, [(sb, LEDS[sb]) for sb, _, _ in slots], LIFT, ANIMS))

meta = G.export(OUT, MODEL, MATS, ANIMS, kind='block', glow=True, atw=2048, bounds=(2, 2), bounds_offset=[0, 0.6, 0])
H.write_block_assets(OUT, MODEL, display=H.BLOCK_DISPLAY)
print('done')
