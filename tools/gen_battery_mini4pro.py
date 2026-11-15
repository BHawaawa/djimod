# -*- coding: utf-8 -*-
"""Procedural generator: DJI Mini 4 Pro Intelligent Flight Battery (Plus, BWX162-3850-7.38) -> GeckoLib ITEM model.

v2 - rebuilt from the user's photos of the real battery:
  * black label on the top face (text runs along the length, connector pins on the front edge)
  * front (connector) end: 6 gold pins in a slot just below the label face, small chamfer at the bottom
  * rear "collar" (16 mm long) wider than the body (44.5 vs 38 mm) and 1.5 mm deeper; it forms the drone's tail
  * on both collar sides: textured square push button in an L-shaped gap, spring tab, two horizontal slots
  * raised moulded panel on the body sides, stepped bottom (front 44 mm sits 2.5 mm higher, above the drone floor)

Frame (Blockbench, before export offset): X = width, Y = up (label face on top), Z = length,
collar / rear end = -Z (north), connector end = +Z (south).  1 model px = 3 mm.
The drone generator (gen_mini4pro.py) merges this model at scale 0.5 (6 mm/px), turned 180 deg so the
connector points forward.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, xbox, obox, rrect, rr, rr_dist

OUT = sys.argv[1] if len(sys.argv) > 1 else 'm4pbat_build'
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf'
FONT_CB = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_ZH = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT_ZHB = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
if not os.path.exists(FONT_ZHB):
    FONT_ZHB = FONT_ZH
MODEL = 'mini4pro_battery'
MM = 1 / 3.0                         # model px per mm

# ============================================================================ dimensions (model px)
L = 80 * MM                          # 26.67
Z_REAR, Z_FRONT = -L / 2, L / 2      # collar end (-Z) / connector end (+Z)
def zd(d_mm):                        # distance from the connector end (mm) -> model z
    return Z_FRONT - d_mm * MM


BW, BH, BR = 38 * MM, 26.5 * MM, 0.9          # body width / height / long-edge radius
CW, CR = 44.5 * MM, 1.0                       # collar width / radius
CY0, CY1 = -1.5 * MM, 26.8 * MM               # collar bottom / top
Z_COL = zd(64)                                # collar starts (front edge of the collar)
Z_STEP = zd(44)                               # bottom step
Y_STEP = 2.5 * MM                             # front section bottom height
Z_NOSE = zd(6)                                # nose (connector end) taper start
YC = (CY0 + CY1) / 2
LBL = (-23.8 * MM / 2, 23.8 * MM / 2, zd(76.5), zd(11.4))   # x0, x1, z0, z1 (label on the top face)
BTN_Z = (zd(78.5), zd(69.5)); BTN_Y = (9.5 * MM, 20.5 * MM)  # push button on each collar side
TAB_Z = (zd(68.2), zd(66.4))
SLOT_Y = (9.6 * MM, 20.2 * MM); SLOT_Z = (zd(68), zd(56.5))

MATS = {
    'shell':  ((204, 206, 207), 2.2),
    'collar': ((200, 202, 204), 2.3),
    'button': ((212, 213, 214), 1.8),
    'dark':   ((36, 37, 39), 1.2),
}


# ============================================================================ label art (top face)
LPX = 30                                      # art px per mm


def build_label():
    Wmm, Hmm = 65.1, 23.8                     # along the length x across the width
    W, H = int(Wmm * LPX), int(Hmm * LPX)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=int(0.9 * LPX), fill=(24, 24, 26, 255))
    T1 = (232, 232, 233, 255); T2 = (205, 206, 208, 255)
    f = lambda p, s: ImageFont.truetype(p, int(s))
    mx, my = int(1.0 * LPX), int(0.9 * LPX)
    # header: dji wordmark + title + model no., underlined
    logo = Image.new('L', (220, 120), 0)
    ImageDraw.Draw(logo).text((6, -14), 'dji', font=f(FONT_B, 110), fill=255)
    logo = logo.transform(logo.size, Image.AFFINE, (1, 0.18, -8, 0, 1, 0), Image.BICUBIC)
    logo = logo.crop(logo.getbbox()).resize((int(2.6 * LPX), int(2.6 * LPX * 0.62)), Image.LANCZOS)
    im.paste(Image.new('RGBA', logo.size, T1), (mx, my), logo)
    hx = mx + int(3.2 * LPX)
    head = 'LITHIUM ION POLYMER RECHARGEABLE BATTERY/二次锂离子电池组/二次鋰離子電池組  Model No./型号/型號: BWX162-3850-7.38'
    fh = f(FONT_ZHB, 0.95 * LPX)
    while d.textlength(head, font=fh) > W - hx - mx:
        fh = f(FONT_ZHB, fh.size - 1)
    d.text((hx, my + 0.15 * LPX), head, font=fh, fill=T1)
    yl = my + int(1.9 * LPX)
    d.line((mx, yl, W - mx, yl), fill=T2, width=max(2, LPX // 12))
    # left column
    fs = f(FONT_ZH, 0.66 * LPX)
    lines = ['Max Charge Voltage/ 充电限制电压/充電限制電壓: 8.5V', 'Nominal Voltage/ 标称电压/標稱電壓: 7.38Vdc',
             'Rated Capacity/ 额定容量/額定電容量: 3850mAh', 'Rated Energy/ 额定能量: 28.41Wh']
    y = yl + int(0.35 * LPX)
    for s in lines:
        d.text((mx, y), s, font=fs, fill=T2); y += int(0.82 * LPX)
    d.text((mx, y), '⚠  WARNING 警告', font=f(FONT_ZHB, 0.8 * LPX), fill=T1); y += int(0.95 * LPX)
    warn = ['1. Only use DJI approved charging devices.', '必须使用DJI专用充电设备。',
            '2. Never disassemble, puncture, shock, crash,', 'short-circuit or put the battery into a fire.',
            '严禁拆解、刺穿、撞击、碾压、短路及燃烧电池。', '3. Do not use water-damaged batteries! 电池浸水后禁止使用!',
            '*Authorized by SZ DJI TECHNOLOGY CO., LTD.', '*本产品由深圳市大疆创新科技有限公司授权生产',
            'Lobby of T2, DJI Sky City, No. 53 Xianyuan Road, Xili', 'Community, Xili Street, Nanshan District, Shenzhen, China.',
            '深圳市南山区西丽街道西丽社区仙元路53号大疆天空之城T2大堂。', 'EU Contact Address: DJI GmbH, Industriestrasse 12,',
            '97618 Niederlauer, Germany.  服务热线: 400-700-0303',
            'Battery weight is included in the weight marked on the aircraft.',
            '电池重量包含在机身标识的重量中。 Made in China/ 中国制造/中國製造']
    fw = f(FONT_ZH, 0.6 * LPX)
    for s in warn:
        d.text((mx, y), s, font=fw, fill=T2); y += int(0.74 * LPX)
    # right column: manufacturer
    rx = int(28.5 * LPX); y = yl + int(0.35 * LPX)
    for s in ['Manufacturer/ 制造商/製造商: Xiamen Ampace Technology Limited/', '厦门新能安科技有限公司/厦門新能安科技有限公司',
              '生产厂: 厦门新能达科技有限公司', '执行标准: GB 42590-2023  2INP10/39/72']:
        d.text((rx, y), s, font=fs, fill=T2); y += int(0.82 * LPX)
    lw = max(3, int(0.17 * LPX))
    # crossed-out wheelie bin
    bx, by, bs = rx + int(0.4 * LPX), y + int(0.5 * LPX), int(8.6 * LPX)
    d.polygon([(bx + bs * .2, by + bs * .22), (bx + bs * .74, by + bs * .22), (bx + bs * .68, by + bs * .9),
               (bx + bs * .26, by + bs * .9)], outline=T1, width=lw)
    d.line((bx + bs * .14, by + bs * .17, bx + bs * .8, by + bs * .17), fill=T1, width=lw)
    d.rectangle((bx + bs * .38, by + bs * .08, bx + bs * .56, by + bs * .15), outline=T1, width=lw)
    d.line((bx + bs * .42, by + bs * .45, bx + bs * .56, by + bs * .45), fill=T1, width=lw)
    d.ellipse((bx + bs * .6, by + bs * .82, bx + bs * .74, by + bs * .96), outline=T1, width=lw)
    d.line((bx, by, bx + bs * .95, by + bs * .98), fill=T1, width=lw)
    d.line((bx + bs * .95, by, bx, by + bs * .98), fill=T1, width=lw)
    d.text((bx + bs * .22, by + bs * 1.02), 'Pb', font=f(FONT_R, 2.6 * LPX), fill=T1)
    # small marks column
    cx0 = bx + bs + int(0.8 * LPX); cy0 = by
    r = int(1.15 * LPX)
    d.ellipse((cx0, cy0, cx0 + 2 * r, cy0 + 2 * r), outline=T1, width=lw)
    d.line((cx0 + r * .3, cy0 + r, cx0 + r * 1.7, cy0 + r), fill=T1, width=lw)
    d.text((cx0 - 0.1 * LPX, cy0 + 2 * r + 0.1 * LPX), 'R32418', font=f(FONT_C, 0.62 * LPX), fill=T2)
    rcx = cx0 + int(3.6 * LPX); rcy = cy0 + r
    for k in range(3):
        a = math.radians(90 + k * 120)
        p = (rcx + 0.8 * LPX * math.cos(a), rcy - 0.8 * LPX * math.sin(a))
        d.polygon([(p[0], p[1] - 0.45 * LPX), (p[0] + 0.45 * LPX, p[1] + 0.35 * LPX), (p[0] - 0.45 * LPX, p[1] + 0.35 * LPX)],
                  outline=T1, width=max(2, lw // 2))
    d.text((rcx - 1.5 * LPX, cy0 + 2 * r + 0.05 * LPX), 'Li-ion 20', font=f(FONT_CB, 0.75 * LPX), fill=T1)
    # CE + UKCA
    ce_y = cy0 + int(3.8 * LPX); ce_r = int(1.45 * LPX)
    for k in range(2):
        ccx = cx0 + ce_r + k * int(3.2 * LPX)
        d.arc((ccx - ce_r, ce_y, ccx + ce_r, ce_y + 2 * ce_r), 60, 300, fill=T1, width=int(0.42 * LPX))
        if k:
            d.line((ccx - ce_r + 0.4 * LPX, ce_y + ce_r, ccx + 0.3 * LPX, ce_y + ce_r), fill=T1, width=int(0.36 * LPX))
    ux = cx0 + int(6.8 * LPX)
    d.text((ux, ce_y - 0.25 * LPX), 'UK', font=f(FONT_B, 1.55 * LPX), fill=T1)
    d.text((ux, ce_y + 1.45 * LPX), 'CA', font=f(FONT_B, 1.55 * LPX), fill=T1)
    # PSE, recycling, TP
    py_ = ce_y + int(3.6 * LPX)
    pr = int(0.95 * LPX)
    d.ellipse((cx0 - 2.6 * LPX, py_, cx0 - 2.6 * LPX + 2 * pr, py_ + 2 * pr), outline=T1, width=max(2, lw // 2))
    d.text((cx0 - 2.6 * LPX + pr, py_ + pr), 'PS\nE', font=f(FONT_B, 0.55 * LPX), fill=T1, anchor='mm', align='center')
    sx_ = cx0 + int(1.6 * LPX)
    d.rounded_rectangle((sx_, py_, sx_ + 1.9 * LPX, py_ + 1.9 * LPX), radius=int(0.4 * LPX), outline=T1, width=lw)
    d.line((sx_ + 0.4 * LPX, py_ + 0.4 * LPX, sx_ + 1.5 * LPX, py_ + 1.5 * LPX), fill=T1, width=lw)
    d.line((sx_ + 1.5 * LPX, py_ + 0.4 * LPX, sx_ + 0.4 * LPX, py_ + 1.5 * LPX), fill=T1, width=lw)
    tx_ = cx0 + int(5.5 * LPX)
    d.arc((tx_, py_ - 0.1 * LPX, tx_ + 2.2 * LPX, py_ + 2.1 * LPX), 40, 320, fill=T1, width=max(2, lw // 2))
    d.ellipse((tx_ + 0.75 * LPX, py_ + 0.55 * LPX, tx_ + 1.95 * LPX, py_ + 1.55 * LPX), outline=T1, width=max(2, lw // 2))
    d.text((tx_ + 1.35 * LPX, py_ + 1.05 * LPX), 'TP', font=f(FONT_B, 0.5 * LPX), fill=T1, anchor='mm')
    d.text((cx0 - 3.2 * LPX, py_ + 2.3 * LPX), 'DJI JAPAN 株式会社', font=f(FONT_ZH, 0.55 * LPX), fill=T2)
    d.text((cx0 + 1.2 * LPX, py_ + 2.25 * LPX), '廢電池請回收', font=f(FONT_ZH, 0.7 * LPX), fill=T2)
    # data matrix on a white patch
    dmx = W - mx - int(4.0 * LPX); dmy = yl + int(2.9 * LPX)
    d.rectangle((dmx, dmy, dmx + 4.0 * LPX, dmy + 6.1 * LPX), fill=(240, 240, 240, 255))
    rng = np.random.default_rng(11)
    dm = rng.random((14, 14)) > 0.5
    dm[:, 0] = True; dm[-1, :] = True; dm[0, ::2] = True; dm[1::2, -1] = True
    dms = int(3.2 * LPX)
    dmi = Image.fromarray(np.where(dm, 255, 0).astype(np.uint8)).resize((dms, dms), Image.NEAREST)
    im.paste(Image.new('RGBA', (dms, dms), (20, 20, 20, 255)), (dmx + int(0.4 * LPX), dmy + int(0.35 * LPX)), dmi)
    d.text((dmx + 2.0 * LPX, dmy + 4.25 * LPX), '5LRPN9A', font=f(FONT_C, 0.62 * LPX), fill=(30, 30, 30), anchor='mm')
    d.text((dmx + 2.0 * LPX, dmy + 5.3 * LPX), 'CA4014Q', font=f(FONT_C, 0.62 * LPX), fill=(30, 30, 30), anchor='mm')
    return np.asarray(im, float)


LABEL = build_label()
Image.fromarray(LABEL.astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'label_art.png'))
# art u runs from the connector end toward the collar (-Z), art v runs from -X to +X
_lab = G.make_decal_sampler(LABEL, -LBL[3], -LBL[2], LBL[0], LBL[1])


def sample_label(X, Z):
    return _lab(-Z, X)


def bottom_text():
    W, H = int(18 * LPX), int(4 * LPX)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    c = (150, 152, 155, 255)
    d.text((0, 0.3 * LPX), 'ULTRA', font=ImageFont.truetype(FONT_R, int(1.3 * LPX)), fill=c)
    d.text((0, 1.9 * LPX), 'LIGHT', font=ImageFont.truetype(FONT_R, int(1.3 * LPX)), fill=c)
    d.line((5.4 * LPX, 0.4 * LPX, 5.4 * LPX, 3.5 * LPX), fill=c, width=int(0.15 * LPX))
    d.text((6.0 * LPX, 0.1 * LPX), '249', font=ImageFont.truetype(FONT_R, int(3.0 * LPX)), fill=c)
    d.text((12.0 * LPX, 2.0 * LPX), 'g', font=ImageFont.truetype(FONT_R, int(1.5 * LPX)), fill=c)
    return np.asarray(im, float)


BOT = bottom_text()
# bottom marking: readable from below with the connector end (= drone nose) at the top of the view,
# so art u runs along +X and art v runs toward the collar (-Z); centred 58 mm from the connector end.
_bot = G.make_decal_sampler(BOT, -9 * MM, 9 * MM, -zd(56), -zd(60))


def sample_bottom(X, Z):
    return _bot(X, -Z)


# ============================================================================ painters
def side_sign(nx):
    return 1 if nx > 0 else -1


def paint_shell(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if ny > 0.9:                                                   # top: label + pins at the front edge
        col, a = sample_label(X, Z)
        f.paint(a, col)
        for k in range(6):
            x = -2.25 + k * 0.9
            f.paint(rr(X, Z, x - 0.16, Z_FRONT - 0.75, x + 0.16, Z_FRONT + 0.1, 0.05), (196, 166, 92))
            f.paint(rr(X, Z, x - 0.06, Z_FRONT - 0.7, x + 0.03, Z_FRONT + 0.1, 0.02), (236, 214, 150))
        f.paint(rr(X, Z, -2.8, Z_FRONT - 0.9, 2.8, Z_FRONT + 0.1, 0.15) & ~rr(X, Z, -2.6, Z_FRONT - 0.8, 2.6, Z_FRONT + 0.2, 0.1),
                (60, 61, 63))
    elif ny < -0.9:                                                # bottom: printed marking + step seam
        col, a = sample_bottom(X, Z)
        f.paint(a, col)
        f.paint(np.abs(Z - Z_STEP) < 0.05, (170, 172, 174))
    elif abs(nx) > 0.9:                                            # sides
        f.paint(np.abs(Y - 1.25) < 0.04, (176, 178, 180))         # lower parting line
        for y in SLOT_Y:                                           # two horizontal slots ahead of the button
            f.paint((np.abs(Y - y) < 0.09) & (Z > SLOT_Z[0]) & (Z < SLOT_Z[1]), (40, 41, 43))
        f.paint(np.abs(Y - 7.95) < 0.03, (186, 188, 190))         # upper edge under the top rail
    elif nz > 0.9:                                                 # connector end
        f.paint(rr(X, Y, -3.0, 5.6, 3.0, 8.15, 0.35), (70, 71, 73))
        f.paint(rr(X, Y, -2.75, 5.8, 2.75, 7.95, 0.25), (20, 20, 22))
        for k in range(6):
            x = -2.25 + k * 0.9
            f.paint(rr(X, Y, x - 0.15, 6.2, x + 0.15, 7.95, 0.05), (198, 168, 96))
            f.paint(rr(X, Y, x - 0.05, 6.3, x + 0.03, 7.9, 0.02), (238, 216, 152))
    elif nz < -0.9:
        pass


def paint_collar(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    f.paint(np.abs(Z - Z_COL) < 0.05, (168, 170, 172))            # collar front edge
    if nz < -0.9:                                                  # rear face (drone tail): fine vertical ribs
        m = (np.abs(X) < 6.3) & (Y > 0.6) & (Y < 8.2)
        ph = (X / 0.85) % 1.0
        f.paint(m & (ph < 0.08), (186, 188, 190))
        f.paint(m & (ph > 0.08) & (ph < 0.14), (214, 216, 217))
        f.paint(np.abs(rr_dist(X, Y, -6.6, 0.3, 6.6, 8.5, 0.8)) < 0.04, (182, 184, 186))
    elif ny > 0.9:                                                 # hook slots on the top rim
        for sx in (1, -1):
            f.paint(rr(sx * X, Z, CW / 2 - 1.05, zd(76), CW / 2 - 0.35, zd(70), 0.12), (30, 31, 33))
            f.paint(rr(sx * X, Z, CW / 2 - 1.35, zd(76), CW / 2 - 0.9, zd(75), 0.08), (30, 31, 33))
    elif ny < -0.9:
        col, a = sample_bottom(X, Z)
        f.paint(a, col)
    elif abs(nx) > 0.9:
        # L-shaped dark gap around the push button + gap continuing toward the slots
        d = rr_dist(Z, Y, BTN_Z[0] - 0.12, BTN_Y[0] - 0.12, BTN_Z[1] + 0.12, BTN_Y[1] + 0.12, 0.45)
        f.paint((d < 0.0) & (d > -0.16), (32, 33, 35))
        f.paint((np.abs(Y - (BTN_Y[0] - 0.3)) < 0.07) & (Z > BTN_Z[1]) & (Z < TAB_Z[1] + 0.2), (32, 33, 35))
        f.paint((np.abs(Y - 8.4) < 0.08) & (Z > zd(75.5)) & (Z < zd(72)), (36, 37, 39))   # top lip slot


def paint_button(f):
    if abs(f.n[0]) > 0.9:
        Y, Z = f.Y, f.Z
        g = ((np.abs(((Z / 0.22) % 1.0) - 0.5) < 0.18) & (np.abs(((Y / 0.22) % 1.0) - 0.5) < 0.18))
        inner = rr(Z, Y, BTN_Z[0] + 0.35, BTN_Y[0] + 0.35, BTN_Z[1] - 0.35, BTN_Y[1] - 0.35, 0.2)
        f.paint(g & inner, (188, 190, 192))
        f.paint(np.abs(rr_dist(Z, Y, BTN_Z[0] + 0.3, BTN_Y[0] + 0.3, BTN_Z[1] - 0.3, BTN_Y[1] - 0.3, 0.25)) < 0.04,
                (226, 227, 228))


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('battery', (0, YC, 0), 'root')
bone('body', (0, YC, 0), 'battery')
bone('collar', (0, YC, Z_COL), 'battery')
bone('button_l', (-CW / 2, YC, (BTN_Z[0] + BTN_Z[1]) / 2), 'battery')     # Blockbench -X side
bone('button_r', (CW / 2, YC, (BTN_Z[0] + BTN_Z[1]) / 2), 'battery')

D = 30
# rear section (full depth) and front section (bottom raised by the step)
rrect('body', 'rear', 0, BH / 2, BW, BH, BR, Z_COL - 0.4, Z_STEP, 'shell', D=D, n=3, decals=paint_shell)
rrect('body', 'front', 0, (Y_STEP + BH) / 2, BW, BH - Y_STEP, BR, Z_STEP, Z_NOSE, 'shell', D=D, n=3, decals=paint_shell)
# nose: small chamfer on the lower front edge + slight side ease-in
NN = 4
for k in range(NN):
    za = Z_NOSE + (Z_FRONT - Z_NOSE) * k / NN; zb = Z_NOSE + (Z_FRONT - Z_NOSE) * (k + 1) / NN
    t = (k + 0.5) / NN
    yb = Y_STEP + 1.1 * t ** 1.5; ins = 0.25 * t ** 2
    rrect('body', f'nose{k}', 0, (yb + BH) / 2, BW - 2 * ins, BH - yb, BR, za, zb, 'shell', D=D, n=3, decals=paint_shell)
# raised moulded side panels (lower edge rises toward the collar)
for sx in (1, -1):
    s = 'r' if sx > 0 else 'l'
    xbox('body', f'panel_{s}', sx * (BW / 2 - 0.2), sx * (BW / 2 + 0.22), 2.4, 7.0, Z_STEP - 1.6, Z_NOSE + 0.5, 'shell',
         D=D, decals=paint_shell)
    for k, (y0, z0) in enumerate(((2.75, Z_STEP - 2.3), (3.1, Z_STEP - 3.0), (3.45, Z_STEP - 3.7))):
        xbox('body', f'panel_{s}_slope{k}', sx * (BW / 2 - 0.2), sx * (BW / 2 + 0.22), y0, 7.0, z0, z0 + 0.72, 'shell',
             D=D, decals=paint_shell)
    # top rail along the upper side edge (slight overhang seen in the side photos)
    xbox('body', f'rail_{s}', sx * (BW / 2 - 0.5), sx * (BW / 2 + 0.12), 7.95, BH, Z_COL, Z_NOSE + 0.6, 'shell', D=D,
         decals=paint_shell)

# collar (rear 16 mm): wider and deeper, rounded rear face
Z_CR = Z_REAR + 0.45
rrect('collar', 'collar', 0, YC, CW, CY1 - CY0, CR, Z_CR, Z_COL, 'collar', D=D, n=3, decals=paint_collar)
for k in range(3):
    th0, th1 = k * 30.0, (k + 1) * 30.0
    thm = math.radians((th0 + th1) / 2)
    ins = 0.45 * (1 - math.cos(thm))
    za = Z_CR - 0.45 * math.sin(math.radians(th1)); zb = Z_CR - 0.45 * math.sin(math.radians(th0))
    rrect('collar', f'collar_end{k}', 0, YC, CW - 2 * ins, CY1 - CY0 - 2 * ins, CR - ins * 0.5, za, zb, 'collar', D=D, n=3,
          decals=paint_collar)
# spring tabs ahead of the buttons
for sx in (1, -1):
    xbox('collar', f'tab_{"r" if sx > 0 else "l"}', sx * (CW / 2 - 0.1), sx * (CW / 2 + 0.22), BTN_Y[0] + 0.3,
         BTN_Y[1] - 0.6, TAB_Z[0], TAB_Z[1], 'collar', D=40, decals=paint_collar)
# push buttons (ribbed/dotted square)
for sx, bn in ((1, 'button_r'), (-1, 'button_l')):
    xbox(bn, f'{bn}_cap', sx * (CW / 2 - 0.1), sx * (CW / 2 + 0.28), BTN_Y[0], BTN_Y[1], BTN_Z[0], BTN_Z[1], 'button',
         D=48, decals=paint_button)

ANIMS = {
    f'animation.{MODEL}.idle': dict(loop='loop', length=1.0, bones={'root': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    # squeeze both push buttons (release), then spring back
    f'animation.{MODEL}.buckle_press': dict(loop='once', length=0.6, bones={
        'button_r': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [0.22, 0, 0], 'catmullrom'),
                                  (0.4, [0.22, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]},
        'button_l': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [-0.22, 0, 0], 'catmullrom'),
                                  (0.4, [-0.22, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]}}),
    f'animation.{MODEL}.showcase': dict(loop='loop', length=8.0, bones={
        'battery': {'rotation': [(0.0, [0, 0, 0], 'linear'), (8.0, [0, 360, 0], 'linear')]}}),
}

DISPLAY = {
    'thirdperson_righthand': {'rotation': [10, 180, 0], 'translation': [0, 2.5, 1.0], 'scale': [0.38, 0.38, 0.38]},
    'thirdperson_lefthand': {'rotation': [10, 180, 0], 'translation': [0, 2.5, 1.0], 'scale': [0.38, 0.38, 0.38]},
    'firstperson_righthand': {'rotation': [10, 200, 0], 'translation': [0, 3.5, 0], 'scale': [0.45, 0.45, 0.45]},
    'firstperson_lefthand': {'rotation': [10, 160, 0], 'translation': [0, 3.5, 0], 'scale': [0.45, 0.45, 0.45]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.32, 0.32, 0.32]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.56, 0.56, 0.56]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 14, 0], 'scale': [0.65, 0.65, 0.65]},
    'fixed': {'rotation': [-90, 180, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
}

G.export(OUT, MODEL, MATS, ANIMS, kind='item', offset=(0, -YC, 0), display=DISPLAY, glow=False, bounds=(2, 1))
print('done')
