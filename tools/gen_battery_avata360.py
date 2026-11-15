# -*- coding: utf-8 -*-
"""Procedural generator: DJI Avata 360 Intelligent Flight Battery -> Blockbench GeckoLib ITEM model + texture.

Frame (Blockbench, before export offset): X = width (battery right = +X), Y = up (label face on top), Z = length,
cap end = -Z (north), connector end = +Z (south).  Bottom of the cap at Y = 0; the export shifts the model by
-YC so the battery is centred on the GeckoLib item pivot.   1 model px ~= 4 mm.
Real battery approx. 112 x 48 x 28 mm (body 46 x 26 mm), 149 g, 2700 mAh / 14.32 V / 38.67 Wh.

Outputs (in OUT): avata360_battery.bbmodel / .geo.json / .animation.json / .png / _glowmask.png / .item.json / meta.json
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, obox, rrect, rr, rr_dist

OUT = sys.argv[1] if len(sys.argv) > 1 else 'bat_build'
os.makedirs(OUT, exist_ok=True)
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf'
FONT_CB = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_ZH = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT_ZHB = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
if not os.path.exists(FONT_ZHB):
    FONT_ZHB = FONT_ZH
MODEL = 'avata360_battery'

# ============================================================================ dimensions (model px)
YC = 4.2                  # battery centre line height (cap bottom at Y = 0)
BW, BH, BR = 11.5, 6.5, 0.85          # body width / thickness / long-edge radius
# cap = the drone's tail: its cross-section is exactly the Avata 360 spine (6.8 x 4.2 px, edge r 1.0 at 8 mm/px)
# scaled to this model's 4 mm/px, so when inserted the cap continues the fuselage with no step.
CW, CH, CR = 13.6, 8.4, 2.0
Z_CAP = -14.0             # cap outer end
Z_JOIN = -8.1             # visible cap / body junction
Z_END = 14.0              # connector end
CAP_RZ, CAP_RX, CAP_RY = 1.75, 1.45, 2.1   # cap end rounding: depth / side inset / face inset
END_RZ, END_INS = 1.3, 0.75                # connector-end chamfer
LBL = (-4.72, 4.72, -7.45, 12.05)          # label rectangle on the top face (x0, x1, z0, z1)
GROOVE_X = (-2.75, 2.55)                   # grooves on the bottom face
LED_X = [0.55, 1.45, 2.35, 3.25]
BTN = (-2.35, YC, 1.95, 0.98)              # power button (x, y, w, h) on the cap end face

# base colour, noise amplitude
SHELL = (146, 148, 150)                    # same plastic as the drone shell (gen_avata.py MATS['shell'])
SEAM = (128, 130, 132)                     # hair-line parting seams, no contrasting colours
MATS = {
    'body':   (SHELL, 2.2),
    'cap':    (SHELL, 2.2),
    'black':  ((30, 31, 33), 2.0),
    'button': (SHELL, 2.2),
    'dark':   ((58, 60, 62), 2.0),
}


# ============================================================================ LABEL ART
LPX = 84   # label art pixels per model px


def build_label():
    x0, x1, z0, z1 = LBL
    W, H = int(round((x1 - x0) * LPX)), int(round((z1 - z0) * LPX))
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=int(0.55 * LPX), fill=(24, 24, 26, 255))
    T1 = (218, 219, 220); T2 = (176, 177, 179)

    def fnt(p, s):
        return ImageFont.truetype(p, s)

    m = int(0.42 * LPX)
    y = m
    # --- header: dji logo + product name + small QR
    try:
        import qrcode
        q = qrcode.QRCode(border=0, box_size=1); q.add_data('CP.FP.00000306.01 BWXVN1-2700-14.32 1SB2N7K0'); q.make()
        qm = np.array(q.get_matrix(), bool)
    except Exception:
        qm = np.random.default_rng(1).random((25, 25)) > 0.5
    qs = int(0.95 * LPX)
    qimg = Image.fromarray(np.where(qm, 0, 255).astype(np.uint8)).resize((qs, qs), Image.NEAREST)
    pad = 5
    d.rectangle((W - m - qs - pad, y - pad, W - m + pad, y + qs + pad), fill=(232, 232, 232, 255))
    im.paste(Image.merge('RGBA', [qimg] * 3 + [Image.new('L', qimg.size, 255)]), (W - m - qs, y))
    # dji wordmark (italic-ish heavy letters)
    logo = Image.new('L', (220, 120), 0)
    ImageDraw.Draw(logo).text((6, -14), 'dji', font=fnt(FONT_B, 110), fill=255)
    logo = logo.transform(logo.size, Image.AFFINE, (1, 0.18, -8, 0, 1, 0), Image.BICUBIC)
    logo = logo.crop(logo.getbbox()).resize((int(0.95 * LPX), int(0.95 * LPX * 0.62)), Image.LANCZOS)
    im.paste(Image.new('RGBA', logo.size, T1 + (255,)), (m, y + 4), logo)
    tx = m + int(1.15 * LPX)
    d.text((tx, y - 2), 'Intelligent Flight Battery', font=fnt(FONT_CB, 30), fill=T1)
    d.text((tx, y + 34), '智能飞行电池', font=fnt(FONT_ZHB, 27), fill=T1)
    y += qs + int(0.25 * LPX)
    d.line((m, y, W - m, y), fill=(120, 120, 122), width=2); y += 12
    specs = ['Model: BWXVN1-2700-14.32', 'Rated Capacity: 2700mAh   14.32V   38.67Wh',
             'Charging Voltage Limit: 17.2V    Li-ion 4S', 'Charging Temp: 5°C to 40°C (41°F to 104°F)',
             '型号: BWXVN1-2700-14.32   额定容量: 2700mAh', '标称电压: 14.32V   额定能量: 38.67Wh   充电限制电压: 17.2V']
    for s in specs:
        d.text((m, y), s, font=fnt(FONT_ZH if any(ord(ch) > 255 for ch in s) else FONT_C, 21), fill=T2)
        y += 27
    y += 8
    # --- yellow caution tag
    tw = int(2.2 * LPX)
    d.rectangle((m, y, m + tw, y + 30), fill=(244, 196, 18, 255))
    d.text((m + 8, y - 2), '⚠ 警告 WARNING', font=fnt(FONT_ZHB, 22), fill=(20, 20, 20))
    d.text((m + tw + 12, y + 2), 'Read the user guide before use.', font=fnt(FONT_C, 20), fill=T2)
    y += 42
    para_en = ('Do not disassemble, puncture, impact, crush or incinerate the battery. Do not expose the battery to '
               'temperatures above 60°C (140°F) or to liquids. Only charge with a DJI approved charger. Stop using the '
               'battery if it swells, leaks or is damaged. Keep away from children. Dispose of the battery in '
               'accordance with local regulations. Fully discharge before recycling. ')
    para_zh = ('严禁拆解、刺穿、撞击、挤压或焚烧电池。请勿将电池置于高于60°C的环境或接触任何液体。'
               '请使用DJI官方认可的充电器充电。电池出现鼓包、漏液或损坏时请立即停止使用。请将电池放置于儿童无法触及的地方。'
               '请按照当地法规处理废旧电池。')

    def wrap(text, font, width, zh=False):
        lines, cur = [], ''
        tokens = list(text) if zh else text.split(' ')
        for tk in tokens:
            nxt = (cur + tk) if zh else (cur + ' ' + tk).strip()
            if d.textlength(nxt, font=font) > width and cur:
                lines.append(cur); cur = tk
            else:
                cur = nxt
        if cur:
            lines.append(cur)
        return lines
    fz = fnt(FONT_ZH, 21); fe = fnt(FONT_C, 20)
    for ln in wrap(para_zh, fz, W - 2 * m, zh=True):
        d.text((m, y), ln, font=fz, fill=T2); y += 27
    y += 6
    for ln in wrap(para_en, fe, W - 2 * m):
        d.text((m, y), ln, font=fe, fill=T2); y += 24
    y += 8
    d.text((m, y), 'WARNING', font=fnt(FONT_CB, 24), fill=T1)
    d.text((m + 125, y + 3), '⚠ Risk of fire and burns.', font=fnt(FONT_C, 19), fill=T2); y += 32
    for ln in wrap('Contains a lithium-ion battery. Do not charge unattended. Do not short circuit the terminals. '
                   'Store at 22°C - 28°C with 40% - 60% charge for long periods.', fe, W - 2 * m):
        d.text((m, y), ln, font=fe, fill=T2); y += 24
    y += 12
    d.line((m, y, W - m, y), fill=(90, 90, 92), width=2); y += 12
    para_ja = ('分解、穴あけ、衝撃、圧壊、焼却をしないでください。60°C以上の高温や液体に電池をさらさないでください。'
               'DJI認定の充電器のみを使用して充電してください。膨張、液漏れ、破損が見られる場合は直ちに使用を中止してください。')
    for ln in wrap(para_ja, fz, W - 2 * m, zh=True):
        d.text((m, y), ln, font=fz, fill=T2); y += 27
    y += 6
    for ln in wrap('ATTENTION: Battery may explode if mistreated. Do not use non-DJI batteries. Before each flight make '
                   'sure the battery is fully charged and properly installed until both latches click into place. '
                   'Avoid flying in extremely cold or hot weather.', fe, W - 2 * m):
        d.text((m, y), ln, font=fe, fill=T2); y += 24
    y += 10
    # --- certification block (bottom part of the label)
    yb = H - m - int(3.6 * LPX)
    y = max(y, yb)
    ico = fnt(FONT_B, 46)
    # UKCA
    d.text((m, y), 'UK', font=fnt(FONT_B, 36), fill=T1); d.text((m, y + 36), 'CA', font=fnt(FONT_B, 36), fill=T1)
    # CE mark
    cx0 = m + int(0.95 * LPX); r = 30
    for k in range(2):
        cx = cx0 + k * 70
        d.arc((cx - r, y + 6, cx + r, y + 6 + 2 * r), 70, 290, fill=T1, width=9)
        if k == 1:
            d.line((cx - r + 10, y + 6 + r, cx + 4, y + 6 + r), fill=T1, width=8)
    # TIS number
    d.text((cx0 + 160, y + 4), 'TIS 2217-2548', font=fnt(FONT_CB, 30), fill=T1)
    d.text((cx0 + 160, y + 40), 'Manufactured by: SZ DJI', font=fnt(FONT_C, 17), fill=T2)
    y2 = y + int(1.05 * LPX)
    # circled marks
    for k, s in enumerate(('Li', 'e', 'G')):
        cx = m + 34 + k * 82
        d.ellipse((cx - 30, y2, cx + 30, y2 + 60), outline=T1, width=5)
        d.text((cx, y2 + 30), s, font=fnt(FONT_B, 30), fill=T1, anchor='mm')
    # data matrix
    dm = (np.random.default_rng(7).random((14, 14)) > 0.5)
    dm[:, 0] = True; dm[-1, :] = True; dm[0, ::2] = True; dm[1::2, -1] = True
    dmi = Image.fromarray(np.where(dm, 255, 0).astype(np.uint8)).resize((70, 70), Image.NEAREST)
    im.paste(Image.new('RGBA', (70, 70), T1 + (255,)), (m + 262, y2 - 4), dmi)
    # crossed-out wheelie bin + Pb
    bx = W - m - int(1.55 * LPX); by = y - 6
    d.polygon([(bx + 18, by + 26), (bx + 92, by + 26), (bx + 82, by + 112), (bx + 28, by + 112)], outline=T1, width=5)
    d.line((bx + 10, by + 22, bx + 100, by + 22), fill=T1, width=6)
    d.rectangle((bx + 42, by + 10, bx + 68, by + 20), outline=T1, width=4)
    d.ellipse((bx + 24, by + 108, bx + 40, by + 124), outline=T1, width=4)
    d.line((bx + 2, by + 4, bx + 108, by + 128), fill=T1, width=6)
    d.line((bx + 108, by + 4, bx + 2, by + 128), fill=T1, width=6)
    d.rectangle((bx + 4, by + 136, bx + 106, by + 146), fill=T1)
    d.text((bx + 55, by + 190), 'Pb', font=fnt(FONT_R, 64), fill=T1, anchor='mm')
    # recycling mark
    y3 = y2 + int(0.95 * LPX)
    rc = (m + 60, y3 + 55)
    for k in range(3):
        a = math.radians(90 + k * 120)
        p = (rc[0] + 40 * math.cos(a), rc[1] - 40 * math.sin(a))
        d.ellipse((p[0] - 16, p[1] - 16, p[0] + 16, p[1] + 16), outline=T1, width=5)
    d.text(rc, 'Li-ion', font=fnt(FONT_CB, 17), fill=T1, anchor='mm')
    # large QR code
    try:
        import qrcode
        q = qrcode.QRCode(border=0, box_size=1); q.add_data('https://www.dji.com/support BWXVN1 2700 SN 1SB2N7K0012345'); q.make()
        qm = np.array(q.get_matrix(), bool)
    except Exception:
        qm = np.random.default_rng(3).random((29, 29)) > 0.5
    qs = int(1.35 * LPX)
    qimg = Image.fromarray(np.where(qm, 255, 0).astype(np.uint8)).resize((qs, qs), Image.NEAREST)
    im.paste(Image.new('RGBA', (qs, qs), T1 + (255,)), (m + 150, y3 - 8), qimg)
    d.text((m + 160 + qs, y3 + 4), 'SN:', font=fnt(FONT_C, 18), fill=T2)
    d.text((m + 160 + qs, y3 + 28), '1SB2N7K0', font=fnt(FONT_C, 18), fill=T2)
    d.text((m + 160 + qs, y3 + 52), '012345', font=fnt(FONT_C, 18), fill=T2)
    d.text((m, H - m - 22), 'Made in China   SZ DJI TECHNOLOGY CO., LTD.', font=fnt(FONT_C, 17), fill=T2)
    return np.asarray(im, float)


LABEL = build_label()
Image.fromarray(LABEL.astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'label_art.png'))


def sample_label(X, Z):
    x0, x1, z0, z1 = LBL
    H, W = LABEL.shape[:2]
    u = (X - x0) / (x1 - x0) * W; v = (Z - z0) / (z1 - z0) * H
    iu = np.clip(u.astype(int), 0, W - 1); iv = np.clip(v.astype(int), 0, H - 1)
    s = LABEL[iv, iu]
    inside = (u >= 0) & (u < W) & (v >= 0) & (v < H)
    a = (s[..., 3] / 255.0) * inside
    return s[..., :3], a


# ============================================================================ PAINTERS (by world normal)
def paint_body(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    # soft contact shadow under the cap lip
    f.shade(np.clip(1 - (Z - Z_JOIN) / 0.6, 0, 1) * (Z > Z_JOIN - 0.4), 0.72)
    if ny > 0.95:                                   # top: label
        col, a = sample_label(X, Z)
        f.paint(a, col)
        # thin raised label edge highlight
        x0, x1, z0, z1 = LBL
        dd = rr_dist(X, Z, x0, z0, x1, z1, 0.55)
        f.paint(np.abs(dd + 0.03) < 0.03, (70, 70, 72))
        # step before the connector chamfer
        f.paint(np.abs(Z - 12.55) < 0.035, (150, 152, 153))
    elif ny < -0.95:                                # bottom: two long grooves + snap tab under the cap
        for gx in GROOVE_X:
            core = (np.abs(X - gx) < 0.07) & (Z > -7.3) & (Z < 11.9)
            f.paint(core, (118, 120, 121))
            f.paint((np.abs(X - gx - 0.12) < 0.05) & (Z > -7.3) & (Z < 11.9), (196, 198, 199))
            f.paint((np.abs(X - gx + 0.11) < 0.04) & (Z > -7.3) & (Z < 11.9), (150, 152, 153))
        f.paint(np.abs(Z - 12.55) < 0.035, (150, 152, 153))
        f.paint(rr(X, Z, -1.75, -7.95, -0.35, -7.0, 0.12), (96, 98, 99))
    elif abs(nx) > 0.95:                            # sides
        sx = 1 if nx > 0 else -1
        f.paint(np.abs(Y - (YC - 1.5)) < 0.035, (128, 130, 132))  # shell parting line
        f.paint(np.abs(Y - (YC - 1.55)) < 0.02, (170, 172, 174), 0.5)
        # latch recess around the lever
        f.paint(rr(Z, Y, Z_JOIN - 0.2, YC - 1.12, -2.85, YC + 1.12, 0.45), (52, 53, 55))
        # grip ribs near the connector end
        for k in range(7):
            z = 10.7 + k * 0.26
            f.paint((np.abs(Z - z) < 0.06) & (np.abs(Y - YC) < 1.85), (128, 130, 131))
            f.paint((np.abs(Z - z - 0.09) < 0.035) & (np.abs(Y - YC) < 1.85), (196, 198, 199))
    elif nz > 0.95:                                 # connector end
        f.paint(rr(X, Y, -3.0, YC - 1.0, 3.0, YC + 0.9, 0.42), (88, 90, 92))
        f.paint(rr(X, Y, -2.8, YC - 0.82, 2.8, YC + 0.72, 0.32), (18, 18, 20))
        for k in range(8):
            x = -2.1 + k * 0.6
            f.paint(rr(X, Y, x - 0.13, YC - 0.55, x + 0.13, YC + 0.45, 0.06), (196, 168, 96))
            f.paint(rr(X, Y, x - 0.05, YC - 0.5, x + 0.03, YC + 0.4, 0.03), (236, 214, 150))
        for sx in (1, -1):
            f.paint((X - sx * 3.75) ** 2 + (Y - YC) ** 2 < 0.28 ** 2, (40, 41, 43))
    if abs(nz) < 0.3 and Z.max() > 12.6:            # chamfer slices: slightly darker
        f.shade(Z > 12.6, 0.93)


def paint_cap(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    # lip edge line where the cap meets the body
    f.paint(np.abs(Z - Z_JOIN) < 0.04, SEAM)
    if ny > 0.9:
        for sx in (1, -1):                                  # release slots: thin seam outline only
            d = rr_dist(X, Z, sx * 3.1 - 0.5, -9.15, sx * 3.1 + 0.5, -8.7, 0.2)
            f.paint(np.abs(d) < 0.04, SEAM)
    elif abs(nx) > 0.9:                                     # latch panel outline (same plastic, flush)
        d = rr_dist(Z, Y, -10.15, YC - 1.4, Z_JOIN - 0.05, YC + 1.4, 0.45)
        f.paint(np.abs(d) < 0.045, SEAM)
    elif nz < -0.9:
        bx, by, bw, bh = BTN
        d = rr_dist(X, Y, bx - bw / 2, by - bh / 2, bx + bw / 2, by + bh / 2, bh / 2)
        f.paint((d > 0.0) & (d < 0.1), SEAM)
        for x in LED_X:
            r = np.sqrt((X - x) ** 2 + (Y - YC) ** 2)
            f.paint(r < 0.2, (128, 130, 131))
            f.paint(r < 0.15, (70, 74, 72))
            f.paint(((X - x + 0.04) ** 2 + (Y - YC - 0.05) ** 2) < 0.045 ** 2, (150, 160, 152))
            f.emit(r < 0.16, (60, 255, 110))
            f.emit((r >= 0.16) & (r < 0.22), (30, 140, 60))


def paint_grip(f):
    """flush latch panel on the cap side: shell colour, fine grip ribs as hair-lines."""
    X, Y, Z = f.X, f.Y, f.Z
    if abs(f.n[0]) > 0.9:
        for k in range(6):
            z = -9.75 + k * 0.3
            f.paint((np.abs(Z - z) < 0.035) & (np.abs(Y - YC) < 1.0), SEAM)
        d = rr_dist(Z, Y, -10.15, YC - 1.4, Z_JOIN - 0.05, YC + 1.4, 0.45)
        f.paint(np.abs(d) < 0.045, SEAM)


def paint_latch(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx = f.n[0]
    if abs(nx) > 0.9:
        f.paint(np.abs(Y - YC) > 0.75, (44, 45, 47))
        for k in range(6):
            z = -9.75 + k * 0.3
            f.paint((np.abs(Z - z) < 0.06) & (Z < Z_JOIN - 0.15), (70, 71, 74))
        f.paint(np.abs(Y - YC - 0.6) < 0.04, (80, 82, 84))


def paint_button(f):
    if f.n[2] < -0.9:
        bx, by, bw, bh = BTN
        d = rr_dist(f.X, f.Y, bx - bw / 2, by - bh / 2, bx + bw / 2, by + bh / 2, bh / 2)
        f.shade(np.clip((d + 0.25) / 0.25, 0, 1), 0.85)
        f.paint(((f.X - bx + 0.45) ** 2 + (f.Y - by - 0.18) ** 2) < 0.06 ** 2, (225, 226, 226))


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('battery', (0, YC, 0), 'root')
bone('body', (0, YC, 0), 'battery')
bone('cap', (0, YC, Z_JOIN), 'battery')
bone('latch_l', (-CW / 2, YC, -6.0), 'battery')     # Blockbench -X side
bone('latch_r', (CW / 2, YC, -6.0), 'battery')
bone('button', (BTN[0], YC, Z_CAP), 'cap')

# ---- body: main prism (runs 0.5 under the cap) + chamfered connector end
Z_T = Z_END - END_RZ
rrect('body', 'body', 0, YC, BW, BH, BR, Z_JOIN - 0.5, Z_T, 'body', D=30, n=4, decals=paint_body)
NE = 6
for k in range(NE):
    t0, t1 = k / NE, (k + 1) / NE
    tm = (t0 + t1) / 2
    ins = END_INS * (0.35 * tm + 0.65 * tm ** 2)
    rrect('body', f'end{k}', 0, YC, BW - 2 * ins, BH - 2 * ins, max(0.3, BR - ins * 0.4),
          Z_T + t0 * END_RZ, Z_T + t1 * END_RZ, 'body', D=30, n=3, decals=paint_body)

# ---- cap: main prism + rounded end
Z_CR = Z_CAP + CAP_RZ
rrect('cap', 'cap', 0, YC, CW, CH, CR, Z_CR, Z_JOIN, 'cap', D=30, n=4, decals=paint_cap)
NC = 10
for k in range(NC):
    th0, th1 = k * 90.0 / NC, (k + 1) * 90.0 / NC
    thm = math.radians((th0 + th1) / 2)
    ix, iy = CAP_RX * (1 - math.cos(thm)), CAP_RY * (1 - math.cos(thm))
    za = Z_CR - CAP_RZ * math.sin(math.radians(th1)); zb = Z_CR - CAP_RZ * math.sin(math.radians(th0))
    rrect('cap', f'capend{k}', 0, YC, CW - 2 * ix, CH - 2 * iy, max(0.35, CR - 0.5 * min(ix, iy)), za, zb, 'cap',
          D=30, n=4 if k < 5 else 3, decals=paint_cap)

# ---- latches (lever along the body side + ribbed grip on the cap side)
for sx, bn in ((1, 'latch_r'), (-1, 'latch_l')):
    x_in, x_out = sx * (BW / 2 - 0.05), sx * (BW / 2 + 0.2)
    box(bn, f'{bn}_lever', (min(x_in, x_out), YC - 0.85, Z_JOIN - 0.3), (max(x_in, x_out), YC + 0.85, -3.1), 'black',
        D=32, decals=paint_latch)
    obox(bn, f'{bn}_lever_tip', (sx * (BW / 2 + 0.07), YC, -3.1), (0.25, 1.2, 0.5), [0, 0, 0], 'black', D=32)
    # grip panel: flush with the cap side (proud by 0.03 px only to avoid z-fighting), same plastic as the cap
    x_in, x_out = sx * (CW / 2 - 0.3), sx * (CW / 2 + 0.03)
    box(bn, f'{bn}_grip', (min(x_in, x_out), YC - 1.4, -10.15), (max(x_in, x_out), YC + 1.4, Z_JOIN - 0.05), 'cap',
        D=32, decals=paint_grip)

# ---- power button (oval, proud of the cap end face)
bx, by, bw, bh = BTN
rrect('button', 'button', bx, by, bw, bh, bh / 2 - 0.02, Z_CAP - 0.14, Z_CAP + 0.25, 'button', D=40, n=3,
      decals=paint_button)

# ---- snap tab on the bottom face, just below the cap
box('body', 'tab', (-1.65, YC - BH / 2 - 0.12, -7.9), (-0.45, YC - BH / 2 + 0.05, -7.05), 'body', D=40)
box('body', 'tab_slot', (-1.45, YC - BH / 2 - 0.14, -7.75), (-0.65, YC - BH / 2 - 0.1, -7.45), 'dark', D=40)


# ============================================================================ ANIMATIONS (bedrock values)
ANIMS = {
    f'animation.{MODEL}.idle': dict(loop='loop', length=1.0, bones={'root': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    # squeeze both latches inward (release), then let go
    f'animation.{MODEL}.latch_press': dict(loop='once', length=0.6, bones={
        'latch_r': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [0.22, 0, 0], 'catmullrom'),
                                 (0.4, [0.22, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]},
        'latch_l': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [-0.22, 0, 0], 'catmullrom'),
                                 (0.4, [-0.22, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]}}),
    # power button click
    f'animation.{MODEL}.button_press': dict(loop='once', length=0.3, bones={
        'button': {'position': [(0.0, [0, 0, 0], 'linear'), (0.08, [0, 0, 0.12], 'linear'), (0.3, [0, 0, 0], 'linear')]}}),
    # display stand: slow turntable + gentle float
    f'animation.{MODEL}.showcase': dict(loop='loop', length=8.0, bones={
        'battery': {'rotation': [(0.0, [0, 0, 0], 'linear'), (8.0, [0, 360, 0], 'linear')],
                    'position': [(0.0, [0, 2, 0], 'catmullrom'), (2.0, [0, 2.6, 0], 'catmullrom'), (4.0, [0, 2, 0], 'catmullrom'),
                                 (6.0, [0, 2.6, 0], 'catmullrom'), (8.0, [0, 2, 0], 'catmullrom')]}}),
}


# item display transforms (vanilla semantics; tweak in Blockbench "Display" tab)
DISPLAY = {
    'thirdperson_righthand': {'rotation': [10, 180, 0], 'translation': [0, 2.5, 1.0], 'scale': [0.36, 0.36, 0.36]},
    'thirdperson_lefthand': {'rotation': [10, 180, 0], 'translation': [0, 2.5, 1.0], 'scale': [0.36, 0.36, 0.36]},
    'firstperson_righthand': {'rotation': [10, 200, 0], 'translation': [0, 3.5, 0], 'scale': [0.42, 0.42, 0.42]},
    'firstperson_lefthand': {'rotation': [10, 160, 0], 'translation': [0, 3.5, 0], 'scale': [0.42, 0.42, 0.42]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 14, 0], 'scale': [0.6, 0.6, 0.6]},
    'fixed': {'rotation': [-90, 180, 0], 'translation': [0, 0, 0], 'scale': [0.55, 0.55, 0.55]},
}

G.export(OUT, MODEL, MATS, ANIMS, kind='item', offset=(0, -YC, 0), display=DISPLAY, glow=True, bounds=(2, 1))
print('done')
