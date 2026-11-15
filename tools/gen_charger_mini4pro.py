# -*- coding: utf-8 -*-
"""Procedural generator: DJI Mini 3 Pro / Mini 4 Pro Two-Way Charging Hub (CHX162-30, 3 slots) -> GeckoLib BLOCK ENTITY.

Reference: the user's photos of the real hub (images 6-12): light-grey box, 3 pockets, batteries stand collar-up with
the push buttons facing the front / back walls (U-notches in the rim under each button), embossed dji logo and
3 x 4 charge LEDs on the front, power button + USB-C IN + USB-A OUT on the right narrow side, black rating label
on the bottom (CHX162-30, Input 5V/9V/12V 3A, compatibility Mini 3 Pro & Mini 3 & Mini 4 Pro).
1 model px = 6 mm (same scale as the Mini 4 Pro drone entity) -> battery item model (3 mm/px) merged at scale 0.5.

Frame: origin = block bottom centre, +Y up, front = -Z (north).  Footprint 15.67 x 7.67 px, rim 12.0 px,
collar tops 14.67 px.

usage: python3 gen_charger_mini4pro.py <out_dir> [battery_model_dir]
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bbgen as G
from bbgen import bone, box, xbox, rrect_y, rr, rr_dist
import hub_common as H

OUT = sys.argv[1] if len(sys.argv) > 1 else 'charger_m4p_build'
BAT_DIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'battery', 'mini4pro')
os.makedirs(OUT, exist_ok=True)
MODEL = 'mini4pro_charging_hub'
P = f'animation.{MODEL}.'

# ============================================================================ dimensions (model px, 6 mm/px)
W, D, RC = 15.67, 7.67, 0.5
Y_F, Y_R = 1.33, 12.0                   # pocket floor / rim (collar seat)
NOTCH = 0.45                            # depth of the U-notch under each push button
PITCH = 4.93
XS = [-PITCH, 0.0, PITCH]
T_END, T_DIV, T_FB = 0.55, 0.22, 0.52
ZI = D / 2 - T_FB
BAT_S = 0.5
BAT_CY = Y_R - 8.0 * BAT_S              # collar front edge (battery z = -8.0) sits on the rim
BAT_DX = -0.1                           # body is 0.2 bpx off-centre in the battery's thickness axis
LIFT = 12.0
LED_Y, LED_SP, LED_S = 0.78, 0.42, 0.2

HUB = (198, 200, 201)
MATS = {
    'hub':  (HUB, 2.0),
    'btn':  ((204, 206, 207), 1.6),
    'led':  ((120, 255, 150), 0.5),
    'label': ((22, 22, 24), 0.6),
}

LOGO = np.asarray(H.dji_logo_mask(160), float) / 255.0
LOGO_C, LOGO_W = (0.0, 6.0), 2.55
LOGO_H = LOGO_W * LOGO.shape[0] / LOGO.shape[1]
LBL_W, LBL_D = 9.1, 5.4
LBL = H.hub_label(1100, int(1100 * LBL_D / LBL_W), 'MINI 3 PRO TWO-WAY CHARGING HUB', model='CHX162-30',
                  inp='5V ⎓ 3A / 9V ⎓ 3A / 12V ⎓ 3A', kc='KC R-R-dji-CHX16230     CAN ICES-3(B)/NMB-3(B)',
                  compat='Mini 3 Pro & Mini 3 & Mini 4 Pro', serial='52WKM6101307CS', qr_data='52WKM6101307CS')
Image.fromarray(LBL.astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'label_art.png'))
# side ports on the +X end (viewer facing it: left = +Z)
BTN_Z, USBC_Z, USBA_Z, PORT_Y = 2.15, 0.25, -1.95, 1.0


def text_mask(s, h_px):
    im = Image.new('L', (400, 140), 0)
    ImageDraw.Draw(im).text((4, 0), s, font=ImageFont.truetype(H.FONT_R, 100), fill=255)
    im = im.crop(im.getbbox())
    return np.asarray(im.resize((max(1, int(h_px * im.width / im.height)), int(h_px)), Image.LANCZOS), float) / 255


TXT_IN, TXT_OUT = text_mask('IN', 60), text_mask('OUT', 60)


def sample(img, A, B, a0, a1, b0, b1):
    h, w = img.shape[:2]
    u = (A - a0) / (a1 - a0) * w; v = (B - b0) / (b1 - b0) * h
    ins = (u >= 0) & (u < w) & (v >= 0) & (v < h)
    iu = np.clip(u.astype(int), 0, w - 1); iv = np.clip(v.astype(int), 0, h - 1)
    return img[iv, iu], ins


def in_pocket(X, Y, Z, e=0.02):
    return (np.abs(Z) < ZI + e) & (np.abs(X) < W / 2 - T_END + e) & (Y > Y_F - e)


def emboss(f, img, A, B, c, w, h, dark, light, dy=0.04):
    a, ins = sample(img, A, B, c[0] - w / 2, c[0] + w / 2, c[1] - h / 2, c[1] + h / 2)
    a2, ins2 = sample(img, A, B + dy, c[0] - w / 2, c[0] + w / 2, c[1] - h / 2, c[1] + h / 2)
    f.paint(np.clip(a2 * ins2 - a * ins, 0, 1), light)
    f.paint(a * ins, dark)


def paint_hub(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    pocket = in_pocket(X, Y, Z)
    if pocket.any():
        depth = np.clip((Y - Y_F) / (Y_R - Y_F), 0, 1)
        f.img[..., :3] *= np.where(pocket, 0.55 + 0.4 * depth, 1.0)[..., None]
        if ny > 0.9:                                   # floor: 6-pin contact block near the label (+X) side
            for x0 in XS:
                f.paint(rr(Z, X, -1.15, x0 + 1.15, 1.15, x0 + 1.8, 0.12), (30, 30, 32))
                for k in range(6):
                    z = -0.85 + k * 0.34
                    f.paint(rr(Z, X, z - 0.09, x0 + 1.3, z + 0.09, x0 + 1.65, 0.03), (196, 166, 92))
        elif abs(nz) > 0.9:                            # moulded guide lines on the long inner walls
            for x0 in XS:
                for sx in (1, -1):
                    f.paint((np.abs(X - x0 - sx * 1.55) < 0.04) & (Y > Y_F + 0.5) & (Y < Y_R - 1.0), (120, 122, 124), 0.6)
        return
    if abs(nz) > 0.9 and np.abs(Z).max() > D / 2 - 0.01:      # outer front / back
        for x0 in XS:                                  # chevron grooves below each notch
            for sx in (1, -1):
                t = np.clip((Y_R - Y) / 1.9, 0, 1)
                xl = x0 + sx * (1.35 - 0.75 * t)
                m = (Y < Y_R) & (Y > Y_R - 1.9)
                f.paint(m & (np.abs(X - xl) < 0.045), (168, 170, 171), 0.9)
                f.paint(m & (np.abs(X - xl - sx * 0.07) < 0.03), (222, 223, 224), 0.8)
        if nz < 0:
            emboss(f, LOGO, -X, -Y, (-LOGO_C[0], -LOGO_C[1]), LOGO_W, LOGO_H, (186, 188, 189), (222, 223, 224))
            for x0 in XS:                              # LED holes (off state)
                for k in range(4):
                    xl = x0 + (1.5 - k) * LED_SP
                    f.paint(rr(X, Y, xl - LED_S / 2, LED_Y - LED_S / 2, xl + LED_S / 2, LED_Y + LED_S / 2, 0.03),
                            (70, 72, 74))
    elif nx > 0.9 and X.max() > W / 2 - 0.01:          # +X end: power button, USB-C IN, USB-A OUT
        f.paint(rr(Z, Y, USBC_Z - 0.72, PORT_Y - 0.24, USBC_Z + 0.72, PORT_Y + 0.24, 0.22), (150, 152, 154))
        f.paint(rr(Z, Y, USBC_Z - 0.66, PORT_Y - 0.19, USBC_Z + 0.66, PORT_Y + 0.19, 0.18), (16, 16, 17))
        f.paint(rr(Z, Y, USBC_Z - 0.42, PORT_Y - 0.05, USBC_Z + 0.42, PORT_Y + 0.05, 0.04), (80, 82, 84))
        f.paint(rr(Z, Y, USBA_Z - 1.02, PORT_Y - 0.4, USBA_Z + 1.02, PORT_Y + 0.4, 0.06), (150, 152, 154))
        f.paint(rr(Z, Y, USBA_Z - 0.95, PORT_Y - 0.34, USBA_Z + 0.95, PORT_Y + 0.34, 0.04), (16, 16, 17))
        f.paint(rr(Z, Y, USBA_Z - 0.78, PORT_Y - 0.02, USBA_Z + 0.78, PORT_Y + 0.17, 0.02), (120, 122, 124))
        f.paint(np.abs(rr_dist(Z, Y, BTN_Z - 0.6, PORT_Y - 0.42, BTN_Z + 0.6, PORT_Y + 0.42, 0.14)) < 0.045, (150, 152, 154))
        emboss(f, TXT_IN, -Z, -Y, (-USBC_Z, -(PORT_Y + 0.82)), 0.32 * TXT_IN.shape[1] / TXT_IN.shape[0], 0.32,
               (176, 178, 180), (222, 223, 224), dy=0.02)
        emboss(f, TXT_OUT, -Z, -Y, (-USBA_Z, -(PORT_Y + 0.82)), 0.32 * TXT_OUT.shape[1] / TXT_OUT.shape[0], 0.32,
               (176, 178, 180), (222, 223, 224), dy=0.02)
    elif ny > 0.9:
        f.shade(Y > Y_F + 1, 1.03)


def paint_label(f):
    if f.n[1] < -0.9:
        col, ins = sample(LBL, -f.X, f.Z, -LBL_W / 2, LBL_W / 2, -LBL_D / 2, LBL_D / 2)
        f.paint((col[..., 3] / 255.0) * ins, col[..., :3])


def paint_led(f):
    f.paint(np.ones_like(f.X, bool), (150, 255, 175))
    f.emit(np.ones_like(f.X, bool), (90, 255, 130))


def paint_btn(f):
    if f.n[0] > 0.9:
        d = rr_dist(f.Z, f.Y, BTN_Z - 0.52, PORT_Y - 0.35, BTN_Z + 0.52, PORT_Y + 0.35, 0.1)
        f.shade(np.clip((d + 0.12) / 0.12, 0, 1), 0.9)
        r = np.sqrt((f.Z - BTN_Z) ** 2 + (f.Y - PORT_Y) ** 2)       # power symbol
        ring = (np.abs(r - 0.17) < 0.025) & ~((np.abs(f.Z - BTN_Z) < 0.07) & (f.Y > PORT_Y))
        f.paint(ring | ((np.abs(f.Z - BTN_Z) < 0.022) & (f.Y > PORT_Y + 0.02) & (f.Y < PORT_Y + 0.22)), (140, 142, 144))


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('hub', (0, 0, 0), 'root')
HD = 24
rrect_y('hub', 'base', 0, 0, W, D, RC, 0.02, Y_F, 'hub', D=HD, n=3, decals=paint_hub)
box('hub', 'label', (-LBL_W / 2, 0.0, -LBL_D / 2), (LBL_W / 2, 0.02, LBL_D / 2), 'label', D=72, decals=paint_label)
for sx in (1, -1):
    for sz in (1, -1):
        rrect_y('hub', f'col_{sx}{sz}', sx * (W / 2 - RC), sz * (D / 2 - RC), 2 * RC, 2 * RC, RC - 0.01, Y_F, Y_R, 'hub',
                D=HD, n=3, decals=paint_hub)
    xbox('hub', f'end_{sx}', sx * W / 2, sx * (W / 2 - T_END), Y_F, Y_R, -(D / 2 - RC), D / 2 - RC, 'hub', D=HD,
         decals=paint_hub)
for xd in (-PITCH / 2, PITCH / 2):
    xbox('hub', f'div_{xd:+.1f}', xd - T_DIV / 2, xd + T_DIV / 2, Y_F, Y_R - 0.2, -ZI, ZI, 'hub', D=HD, decals=paint_hub)

XA = W / 2 - RC
NL = 4
for sz, tag in ((-1, 'f'), (1, 'b')):
    z0, z1 = sz * D / 2, sz * ZI
    xbox('hub', f'w{tag}', -XA, XA, Y_F, Y_R - NOTCH, z0, z1, 'hub', D=HD, decals=paint_hub)
    for k in range(NL):                               # rim layers with the rounded U-notches
        ya = Y_R - NOTCH + NOTCH * k / NL; yb = Y_R - NOTCH + NOTCH * (k + 1) / NL
        tm = (k + 0.5) / NL
        hw = 0.85 + 0.55 * tm ** 1.6               # notch half-width: rounded bottom, flares toward the top
        edges = [-XA] + [v for x0 in XS for v in (x0 - hw, x0 + hw)] + [XA]
        for j in range(0, len(edges), 2):
            xbox('hub', f'w{tag}_rim{k}{j // 2}', edges[j], edges[j + 1], ya, yb, z0, z1, 'hub', D=HD, decals=paint_hub)

# side power button (slightly proud)
box('hub', 'power_button', (W / 2 - 0.02, PORT_Y - 0.35, BTN_Z - 0.52), (W / 2 + 0.05, PORT_Y + 0.35, BTN_Z + 0.52), 'btn',
    D=48, decals=paint_btn)

# batteries
slots = [(f'slot{i + 1}', f's{i + 1}', (x0 + BAT_DX, BAT_CY, 0.0)) for i, x0 in enumerate(XS)]
H.merge_battery_slots(BAT_DIR, 'mini4pro_battery', slots, BAT_S, 'hub', DS=2)

# charge LEDs on the front (4 per slot, viewer's left -> right = level 1 -> 4)
LEDS = {}
for i, x0 in enumerate(XS):
    sb = f'slot{i + 1}'
    LEDS[sb] = []
    for k in range(4):
        xl = x0 + (1.5 - k) * LED_SP
        bn = bone(f's{i + 1}_led{k + 1}', (xl, LED_Y, -D / 2), 'hub')
        box(bn, bn, (xl - LED_S / 2 + 0.01, LED_Y - LED_S / 2 + 0.01, -D / 2 - 0.02),
            (xl + LED_S / 2 - 0.01, LED_Y + LED_S / 2 - 0.01, -D / 2 + 0.01), 'led', D=40, decals=paint_led)
        LEDS[sb].append(bn)

# ============================================================================ ANIMATIONS
ANIMS = {f'{P}idle': dict(loop='loop', length=1.0, bones={'hub': {'position': H.kf((0.0, [0, 0, 0]))}})}
for i, (sb, pre_, C) in enumerate(slots):
    ANIMS.update(H.slot_anims(P, i + 1, sb, LEDS[sb], LIFT,
                              press_bones=[(f'{pre_}_button_r', 1), (f'{pre_}_button_l', -1)], press_dx=0.11))
ANIMS.update(H.demo_cycle(P, [(sb, LEDS[sb]) for sb, _, _ in slots], LIFT, ANIMS))

G.export(OUT, MODEL, MATS, ANIMS, kind='block', glow=True, atw=2048, bounds=(2, 2), bounds_offset=[0, 0.5, 0])
H.write_block_assets(OUT, MODEL, display=H.BLOCK_DISPLAY)
print('done')
