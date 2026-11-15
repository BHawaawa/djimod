# -*- coding: utf-8 -*-
"""Shared geometry + painting for the DJI TB100 intelligent flight battery (Matrice 400).

Used by BOTH gen_battery_m400.py (GeckoLib item, k = 2 -> 7.5 mm/px) and gen_m400.py (merged into the drone,
k = 1 -> 15 mm/px, then rotated onto the 34 deg battery slope).  Because both are built and painted by the same
code, the battery in the drone and the battery item look identical.

Battery-local frame (drone px, 15 mm):  x = across (+x = drone's right),  v = up (0 = top of the cap, body below),
s = along the length (0 = vent end, which faces the drone's nose; 14.96 = LED / power-button end, drone tail).
Mapping into the model:  X = k*x,  Y = oy + k*v,  Z = oz + k*s.

Outline (from the TB100 product photos): light-grey lid / cap (10 x 14.96, 1.0 thick) with a raised vent block at
one end and 4 level LEDs + power button at the other; darker body underneath (8.2 x 10.75 x 6.35; the lid overhangs it at the LED end) with moulded
horizontal grip ribs on the sides, a label on the LED end, an air grille on the vent end and the power connector on
the bottom; two orange release latches on the cap sides near the LED end.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from bbgen import box, rrect, rr

FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf'

# dimensions (battery-local, drone px)
CAP_W, CAP_T, CAP_L = 10.0, 1.0, 14.96
BODY_W, BODY_S0, BODY_S1, BODY_V0 = 8.2, 0.85, 11.6, -7.35
RIB_V = (-2.1, -3.4, -4.7, -6.0)
VENT = (-2.7, 2.7, 0.6, 3.7, 0.35)              # x0, x1, s0, s1, height
LATCH_S, LATCH_V = (11.2, 12.6), (-0.85, -0.15)
LED_S = 13.25
DEPTH = -BODY_V0                                 # 7.35: how far the battery reaches below the cap top

MATS = {
    'bat_cap':   ((84, 85, 89), 2.0),
    'bat_body':  ((76, 77, 81), 2.0),
    'bat_vent':  ((74, 75, 79), 1.8),
    'bat_latch': ((228, 112, 30), 1.2),
}

_TXT = {}


def _glyphs(txt, font):
    key = (txt, font)
    if key not in _TXT:
        fnt = ImageFont.truetype(font, 160)
        bb = fnt.getbbox(txt)
        im = Image.new('L', (bb[2] - bb[0] + 8, bb[3] - bb[1] + 8), 0)
        ImageDraw.Draw(im).text((4 - bb[0], 4 - bb[1]), txt, font=fnt, fill=255)
        _TXT[key] = np.asarray(im, float) / 255.0
    return _TXT[key]


def text(f, A, B, txt, ca, cb, h, color, font=FONT_C):
    g = _glyphs(txt, font)
    gh, gw = g.shape
    sc = h / (gh - 8)
    u = (A - ca) / sc + gw / 2; v = (B - cb) / sc + gh / 2
    inside = (u >= 0) & (u < gw) & (v >= 0) & (v < gh)
    f.paint(g[np.clip(v.astype(int), 0, gh - 1), np.clip(u.astype(int), 0, gw - 1)] * inside, color)


def make_decal(to_local, nrm_local):
    """to_local(P) -> (x, v, s) arrays; nrm_local(n) -> (nx, nv, ns)."""
    def fn(f):
        x, v, s = to_local(f.P)
        nx, nv, ns = nrm_local(f.n)
        mat = f.c['mat']
        if mat == 'bat_latch':
            f.paint(np.abs(((s - LATCH_S[0]) / 0.2) % 1 - 0.5) < 0.12, (190, 90, 22))
            return
        if nv > 0.95:
            if v.min() > VENT[4] - 0.02:                           # top of the vent block: slats
                f.paint(rr(x, s, VENT[0] + 0.2, VENT[2] + 0.2, VENT[1] - 0.2, VENT[3] - 0.2, 0.25), (40, 41, 44))
                for i in range(11):
                    xa = VENT[0] + 0.35 + i * 0.46
                    f.paint(rr(x, s, xa, VENT[2] + 0.3, xa + 0.24, VENT[3] - 0.3, 0.08), (82, 83, 87))
            elif v.min() > -0.02:                                  # cap top
                f.paint(np.abs(x) > CAP_W / 2 - 0.55, (92, 93, 97))
                f.paint(rr(x, s, -4.1, 4.2, 4.1, 12.2, 0.6) & ~rr(x, s, -4.0, 4.3, 4.0, 12.1, 0.55), (70, 71, 75))
                text(f, x, s, 'TB100', 0.0, 8.2, 0.75, (102, 103, 107))
                d = np.sqrt((x - 2.8) ** 2 + (s - LED_S) ** 2)    # power button
                f.paint(d < 0.62, (52, 53, 56)); f.paint(d < 0.48, (96, 97, 101))
                f.paint((d > 0.26) & (d < 0.32), (60, 61, 64))
                for k in range(4):                                 # battery level LEDs
                    m = ((x - (-2.6 + k * 0.8)) ** 2 + (s - LED_S) ** 2) < 0.14 ** 2
                    f.paint(((x - (-2.6 + k * 0.8)) ** 2 + (s - LED_S) ** 2) < 0.2 ** 2, (46, 47, 50))
                    f.paint(m, (110, 235, 140)); f.emit(m, (110, 245, 150))
            return
        if nv < -0.95:
            if v.max() < BODY_V0 + 0.02:                           # bottom: power connector
                f.paint(rr(x, s, -2.4, 3.7, 2.4, 8.7, 0.4), (30, 30, 32))
                for k in range(10):
                    sa = 4.1 + k * 0.44
                    f.paint(rr(x, s, -1.9, sa, -0.4, sa + 0.2, 0.05), (196, 168, 96))
                    f.paint(rr(x, s, 0.4, sa, 1.9, sa + 0.2, 0.05), (196, 168, 96))
                for sx in (1, -1):
                    f.paint(rr(x, s, sx * 3.2 - 0.25, 1.6, sx * 3.2 + 0.25, 10.8, 0.12), (52, 53, 56))   # guide rails
                text(f, -x, s, 'dji', 0.0, 10.1, 0.6, (96, 97, 100), FONT_B)
            else:                                                  # underside of the cap overhang
                f.paint(np.ones_like(x, bool), (70, 71, 75))
            return
        if abs(nx) > 0.95:
            if v.min() > -CAP_T - 0.02:                            # cap side
                f.paint(np.abs(v + 0.5) < 0.03, (70, 71, 75))
                f.paint(rr(s, v, LATCH_S[0] - 0.12, LATCH_V[0] - 0.12, LATCH_S[1] + 0.12, LATCH_V[1] + 0.12, 0.1), (36, 36, 38))
            else:                                                  # body side: grooves between the grip ribs
                for vv in RIB_V:
                    f.paint(np.abs(v - vv) < 0.2, (64, 65, 69))
                f.paint(np.abs(v - (BODY_V0 + 0.25)) < 0.04, (50, 51, 54))
            return
        if abs(ns) > 0.95:
            front = ns < 0
            if v.min() > -CAP_T - 0.02 or (VENT[4] > 0 and v.min() > -0.02):   # cap / vent block ends
                if front:
                    f.paint(rr(x, v, -3.4, -0.75, 3.4, -0.3, 0.15), (32, 32, 34))   # exhaust slot
                    slots_x = np.abs(((x + 3.4) / 0.45) % 1 - 0.5) < 0.12
                    f.paint(rr(x, v, -3.4, -0.75, 3.4, -0.3, 0.15) & slots_x, (60, 61, 64))
                    if v.min() > -0.02:
                        f.paint(rr(x, v, -2.5, 0.06, 2.5, 0.29, 0.06), (36, 36, 38))
                return
            if front:                                              # body vent end: air grille
                for k in range(9):
                    va = -1.6 - k * 0.65
                    f.paint(rr(x, v, -3.2, va - 0.22, 3.2, va, 0.1), (36, 36, 38))
            else:                                                  # body LED end: product label
                xs = x                                             # read from behind (+s side): right = +x
                f.paint(rr(xs, v, -3.6, -6.9, 3.6, -1.6, 0.3), (48, 49, 52))
                text(f, xs, -v, 'TB100', -1.0, 3.4, 0.62, (200, 201, 204), FONT_B)
                text(f, xs, -v, 'Intelligent Flight Battery', -0.2, 4.2, 0.3, (170, 171, 174), FONT_R)
                text(f, xs, -v, 'For MATRICE 400 Series', -0.6, 4.85, 0.3, (170, 171, 174), FONT_R)
                text(f, xs, -v, 'dji', 2.4, 3.4, 0.6, (200, 201, 204), FONT_B)
                f.paint(rr(xs, v, 1.7, -6.6, 3.2, -5.5, 0.12), (120, 190, 60))      # green inspection sticker
                f.paint(rr(xs, v, -3.2, -6.5, -0.4, -6.1, 0.05), (110, 111, 114))   # barcode strip
                f.paint(rr(xs, v, -3.2, -6.5, -0.4, -6.1, 0.05) & (np.abs(((xs * 9) % 1) - 0.5) < 0.2), (40, 40, 42))
    return fn


def build(bones, k, oy, oz, to_local, nrm_local, D=16):
    """bones = dict(main=, latch_l=, latch_r=) bone names. Returns the list of created cubes."""
    import bbgen as G
    n0 = len(G.CUBES)
    X = lambda x: k * x
    Y = lambda v: oy + k * v
    Z = lambda s: oz + k * s
    dec = make_decal(to_local, nrm_local)
    b = bones['main']
    rrect(b, 'tb_cap', 0, Y(-CAP_T / 2), k * CAP_W, k * CAP_T, k * 0.35, Z(0), Z(CAP_L), 'bat_cap', D=D, n=2, decals=dec)
    rrect(b, 'tb_vent', 0, Y(VENT[4] / 2 - 0.02), k * (VENT[1] - VENT[0]), k * (VENT[4] + 0.04), k * 0.12,
          Z(VENT[2]), Z(VENT[3]), 'bat_vent', D=D + 4, n=2, decals=dec)
    rrect(b, 'tb_body', 0, Y((BODY_V0 - CAP_T) / 2 + 0.02), k * BODY_W, k * (-BODY_V0 - CAP_T + 0.04), k * 0.35,
          Z(BODY_S0), Z(BODY_S1), 'bat_body', D=D, n=2, decals=dec)
    for sx, sd in ((1, 'r'), (-1, 'l')):
        for i, vv in enumerate(RIB_V):
            xa, xb = sorted([X(sx * (BODY_W / 2 - 0.05)), X(sx * (BODY_W / 2 + 0.1))])
            box(b, f'tb_rib_{sd}{i}', (xa, Y(vv - 0.13), Z(1.6)), (xb, Y(vv + 0.13), Z(10.8)), 'bat_body', D=D, decals=dec)
        lb = bones['latch_r' if sx > 0 else 'latch_l']
        xa, xb = sorted([X(sx * (CAP_W / 2 - 0.05)), X(sx * (CAP_W / 2 + 0.12))])
        box(lb, f'tb_latch_{sd}', (xa, Y(LATCH_V[0]), Z(LATCH_S[0])), (xb, Y(LATCH_V[1]), Z(LATCH_S[1])), 'bat_latch',
            D=D + 8, decals=dec)
    return G.CUBES[n0:]
