# -*- coding: utf-8 -*-
"""Shared helpers for the DJI charging-hub block entities (gen_charger_avata360.py / gen_charger_mini4pro.py).

* merge_battery_slots(): merges an existing battery ITEM model (geo + texture, see model/battery/<p>) into the hub
  three times (one per slot).  The battery cubes keep their native orientation and are only scaled + translated;
  the stand-up orientation comes from the slot bone's rest rotation [90, 90, 0] (Blockbench euler):
      battery -Z (cap / collar end)  -> hub +Y (up)
      battery +Y (black label face)  -> hub +X
      battery +X (latch / push-button side) -> hub -Z (front)
  so face textures need no re-mapping.  The three copies share one atlas region per face (bbgen de-duplicates
  identical pre-baked arrays).
* slot_anims(): per-slot insert / remove / installed / empty / charging animations (Bedrock values).
"""
import json, os
import numpy as np
from PIL import Image
import bbgen as G

SLOT_ROT = [90, 90, 0]


def _load(bat_dir, name):
    geo = json.load(open(os.path.join(bat_dir, 'geo', f'{name}.geo.json'), encoding='utf-8'))['minecraft:geometry'][0]
    tex = np.asarray(Image.open(os.path.join(bat_dir, 'textures', f'{name}.png')).convert('RGBA'))
    gp = os.path.join(bat_dir, 'textures', f'{name}_glowmask.png')
    gtex = np.asarray(Image.open(gp).convert('RGBA')) if os.path.exists(gp) else np.zeros_like(tex)
    return geo, tex, gtex


def merge_battery_slots(bat_dir, name, slots, scale, parent, DS=2, keep_glow=False, bone_map=None):
    """slots: list of (slot_bone_name, prefix, centre_bb).  centre_bb = hub Blockbench point where the battery
    item's origin (its geometric centre) goes.  Returns {slot_bone: {orig_bone: new_bone}}."""
    geo, tex, gtex = _load(bat_dir, name)
    cache = {}
    out = {}
    for sb, pre_, C in slots:
        C = np.array(C, float)

        def P(p):
            return list(C + scale * np.array(p, float))
        names = {}
        for b in geo['bones']:
            if b['name'] == 'root':
                continue
            pv = b['pivot']
            if b['name'] == 'battery':
                nb = sb
                G.bone(nb, P([0, 0, 0]), parent, rot=SLOT_ROT)
            else:
                nb = f'{pre_}_{(bone_map or {}).get(b["name"], b["name"])}'
                G.bone(nb, P([-pv[0], pv[1], pv[2]]), names[b.get('parent')])
            names[b['name']] = nb
            for ci, c in enumerate(b.get('cubes', [])):
                o, sz = c['origin'], c['size']
                frm = [-(o[0] + sz[0]), o[1], o[2]]; to = [frm[i] + sz[i] for i in range(3)]
                if 'rotation' in c:
                    cp = c['pivot']; org = P([-cp[0], cp[1], cp[2]])
                    r = c['rotation']; rot = [-r[0], -r[1], r[2]]
                else:
                    org, rot = P(frm), [0, 0, 0]
                key = (b['name'], ci)
                if key not in cache:
                    pre = {}
                    for fname, fd in c['uv'].items():
                        (u, v), (w, h) = fd['uv'], fd['uv_size']
                        ua, ub = sorted([u, u + w]); va, vb = sorted([v, v + h])
                        ua, ub, va, vb = int(round(ua)), int(round(ub)), int(round(va)), int(round(vb))
                        reg, greg = tex[va:vb, ua:ub], gtex[va:vb, ua:ub]
                        if not keep_glow:
                            greg = np.zeros_like(greg)
                        nw, nh = max(1, int(round(reg.shape[1] / DS))), max(1, int(round(reg.shape[0] / DS)))
                        reg = np.asarray(Image.fromarray(np.ascontiguousarray(reg), 'RGBA').resize((nw, nh), Image.BOX))
                        greg = np.asarray(Image.fromarray(np.ascontiguousarray(greg), 'RGBA').resize((nw, nh), Image.BOX))
                        # (identity orientation: atlas pixels are reused as-is; bbgen writes the same uv convention)
                        pre[fname] = (np.ascontiguousarray(reg), np.ascontiguousarray(greg))
                    cache[key] = pre
                cb = G.box(nb, f'{pre_}_{b["name"]}_{ci}', P(frm), P(to), 'hub', origin=org, rot=rot)
                cb['pre'] = cache[key]
        out[sb] = names
    return out


# ============================================================================ animations
def kf(*keys, ip='linear'):
    return [(float(t), [float(a) for a in v], ip) for t, v in keys]


def cat(*keys):
    return kf(*keys, ip='catmullrom')


def slot_anims(P, n, slot, leds, lift, press_bones=(), press_dx=0.11, extra_insert=None):
    """P = 'animation.<model>.', n = slot number, slot = slot bone, leds = 4 LED bone names (low -> high),
    lift = how far above its seat the battery starts (px), press_bones = [(bone, sign)] latch / push buttons that are
    squeezed while the battery slides in or out (Bedrock x offset = sign * press_dx)."""
    ON, OFF = [1, 1, 1], [0, 0, 0]
    A = {}
    # ---------------------------------------------------------------- insert (1.6 s)
    b = {slot: {
        'position': cat((0.0, [0, lift, 0]), (0.15, [0, lift * 0.92, 0]), (0.75, [0, 1.2, 0]), (0.95, [0, 0.12, 0]),
                        (1.02, [0, -0.08, 0]), (1.1, [0, 0, 0]), (1.6, [0, 0, 0])),
        'scale': kf((0.0, ON), (1.6, ON))}}
    for bn, sg in press_bones:          # squeeze before the battery enters, hold, release = "click" when seated
        b[bn] = {'position': cat((0.0, [0, 0, 0]), (0.15, [sg * press_dx, 0, 0]), (0.9, [sg * press_dx, 0, 0]),
                                 (1.02, [0, 0, 0]), (1.6, [0, 0, 0]))}
    for k, lb in enumerate(leds):       # charge LEDs sweep on once the contacts touch
        t = 1.15 + 0.1 * k
        b[lb] = {'scale': kf((0.0, OFF), (t - 0.001, OFF), (t, ON), (1.6, ON))}
    if extra_insert:
        extra_insert(b)
    A[f'{P}slot{n}_insert'] = dict(loop='hold', length=1.6, bones=b)
    # ---------------------------------------------------------------- remove (1.3 s)
    b = {slot: {
        'position': cat((0.0, [0, 0, 0]), (0.2, [0, 0, 0]), (0.3, [0, 0.35, 0]), (0.95, [0, lift * 0.85, 0]),
                        (1.2, [0, lift, 0]), (1.3, [0, lift, 0])),
        'scale': kf((0.0, ON), (1.2, ON), (1.201, OFF), (1.3, OFF))}}
    for bn, sg in press_bones:
        b[bn] = {'position': cat((0.0, [0, 0, 0]), (0.15, [sg * press_dx, 0, 0]), (0.75, [sg * press_dx, 0, 0]),
                                 (0.95, [0, 0, 0]), (1.3, [0, 0, 0]))}
    for lb in leds:
        b[lb] = {'scale': kf((0.0, ON), (0.05, OFF), (1.3, OFF))}
    A[f'{P}slot{n}_remove'] = dict(loop='hold', length=1.3, bones=b)
    # ---------------------------------------------------------------- static states
    b = {slot: {'position': kf((0.0, [0, 0, 0])), 'scale': kf((0.0, ON))}}
    for lb in leds:
        b[lb] = {'scale': kf((0.0, ON))}
    A[f'{P}slot{n}_installed'] = dict(loop='hold', length=0.05, bones=b)
    b = {slot: {'position': kf((0.0, [0, lift, 0])), 'scale': kf((0.0, OFF))}}
    for lb in leds:
        b[lb] = {'scale': kf((0.0, OFF))}
    A[f'{P}slot{n}_empty'] = dict(loop='hold', length=0.05, bones=b)
    # ---------------------------------------------------------------- charging loop (LED chase, 2 s)
    b = {slot: {'position': kf((0.0, [0, 0, 0])), 'scale': kf((0.0, ON))}}
    for k, lb in enumerate(leds):
        # LEDs below the current level stay on, the next one blinks: here level = 2 of 4 (k<2 solid, k=2 blinks)
        if k < 2:
            b[lb] = {'scale': kf((0.0, ON))}
        elif k == 2:
            b[lb] = {'scale': kf((0.0, OFF), (0.499, OFF), (0.5, ON), (1.499, ON), (1.5, OFF), (2.0, OFF))}
        else:
            b[lb] = {'scale': kf((0.0, OFF))}
    A[f'{P}slot{n}_charging'] = dict(loop='loop', length=2.0, bones=b)
    return A


def demo_cycle(P, slots, lift, insert_anims):
    """one long looping showcase: slot 1, 2, 3 inserted one after another, all hold, then removed in reverse order.
    Built by time-shifting the per-slot insert / remove keyframes."""
    L = 12.0
    bones = {}
    for i, (slot, leds) in enumerate(slots):
        ins = insert_anims[f'{P}slot{i + 1}_insert']['bones']
        rem = insert_anims[f'{P}slot{i + 1}_remove']['bones']
        t_in = 0.4 + i * 1.9
        t_out = 7.0 + (2 - i) * 1.5
        for bn in set(ins) | set(rem):
            for ch in ('position', 'scale'):
                keys = []
                if bn == slot and ch == 'scale':
                    keys = kf((0.0, [0, 0, 0]), (t_in - 0.001, [0, 0, 0]), (t_in, [1, 1, 1]),
                              (t_out + 1.2, [1, 1, 1]), (t_out + 1.201, [0, 0, 0]), (L, [0, 0, 0]))
                elif bn == slot and ch == 'position':
                    keys = [(0.0, [0, lift, 0], 'linear')]
                    keys += [(t + t_in, v, ip) for t, v, ip in ins[bn][ch]]
                    keys += [(t + t_out, v, ip) for t, v, ip in rem[bn][ch]]
                    keys += [(L, [0, lift, 0], 'linear')]
                else:
                    src_i = ins.get(bn, {}).get(ch); src_r = rem.get(bn, {}).get(ch)
                    if not src_i and not src_r:
                        continue
                    first = (src_i or src_r)[0][1]
                    keys = [(0.0, first if ch == 'position' else [0, 0, 0], 'linear')]
                    if src_i:
                        keys += [(t + t_in, v, ip) for t, v, ip in src_i]
                    if src_r:
                        keys += [(t + t_out, v, ip) for t, v, ip in src_r]
                    keys += [(L, keys[-1][1], 'linear')]
                # drop duplicate times (keep the later one)
                dd = {}
                for t, v, ip in keys:
                    dd[round(t, 4)] = (round(t, 4), v, ip)
                bones.setdefault(bn, {})[ch] = [dd[k] for k in sorted(dd)]
    return {f'{P}demo_cycle': dict(loop='loop', length=L, bones=bones)}


# ============================================================================ bottom label art
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf'
FONT_CB = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_ZH = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT_ZHB = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
if not os.path.exists(FONT_ZHB):
    FONT_ZHB = FONT_ZH


def dji_logo_mask(h_px):
    """slanted heavy 'dji' word-mark as an L mask of height h_px."""
    from PIL import ImageDraw, ImageFont
    logo = Image.new('L', (230, 130), 0)
    ImageDraw.Draw(logo).text((8, -12), 'dji', font=ImageFont.truetype(FONT_B, 110), fill=255)
    logo = logo.transform(logo.size, Image.AFFINE, (1, 0.2, -10, 0, 1, 0), Image.BICUBIC)
    logo = logo.crop(logo.getbbox())
    return logo.resize((max(1, int(h_px * logo.width / logo.height)), int(h_px)), Image.LANCZOS)


def hub_label(Wpx, Hpx, title, model=None, inp=None, kc=None, compat='', serial='', qr_data='dji'):
    """black rating label in the style of the Mini 3 Pro Two-Way Charging Hub label (user photo)."""
    from PIL import ImageDraw, ImageFont
    im = Image.new('RGBA', (Wpx, Hpx), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = Hpx / 100.0                                       # layout unit
    d.rounded_rectangle((0, 0, Wpx - 1, Hpx - 1), radius=int(6 * u), fill=(22, 22, 24, 255))
    T1, T2 = (228, 228, 229, 255), (200, 201, 203, 255)
    f = lambda p, s: ImageFont.truetype(p, max(6, int(s)))
    mx = int(5 * u)
    lg = dji_logo_mask(6.5 * u)
    im.paste(Image.new('RGBA', lg.size, T1), (mx, int(7 * u)), lg)
    tx = mx + lg.width + int(2 * u)
    tmax = (Wpx - mx - int(30 * u) if model else Wpx - mx) - tx
    ft = f(FONT_B, 6.3 * u)
    while d.textlength(title, font=ft) > tmax and ft.size > 8:
        ft = f(FONT_B, ft.size - 1)
    d.text((tx, int(6 * u)), title, font=ft, fill=T1)
    if model:
        d.text((Wpx - mx, int(4.5 * u)), '型号/Model:', font=f(FONT_ZH, 5.2 * u), fill=T2, anchor='ra')
        d.text((Wpx - mx, int(11 * u)), model, font=f(FONT_R, 5.2 * u), fill=T2, anchor='ra')
    d.line((mx, int(17.5 * u), Wpx - mx, int(17.5 * u)), fill=(150, 150, 152), width=max(1, int(0.6 * u)))
    y = int(20 * u)
    if inp:
        fi = f(FONT_ZH, 5.6 * u); xx = mx
        for j, seg in enumerate(('Input/输入: ' + inp).split('⎓')):
            d.text((xx, y), seg, font=fi, fill=T1); xx += d.textlength(seg, font=fi)
            if j < inp.count('⎓'):                     # DC symbol (solid line over dashed line), not in the fonts
                sw, yy = 4.2 * u, y + 3.6 * u
                d.line((xx + 0.6 * u, yy, xx + 0.6 * u + sw, yy), fill=T1, width=max(1, int(0.55 * u)))
                for q in range(3):
                    x0 = xx + 0.6 * u + q * sw / 3
                    d.line((x0, yy + 1.4 * u, x0 + sw / 5, yy + 1.4 * u), fill=T1, width=max(1, int(0.55 * u)))
                xx += sw + 1.4 * u
        y += int(9 * u)
    if kc:
        d.text((mx, y), kc, font=f(FONT_R, 5.2 * u), fill=T2); y += int(9 * u)
    fs = f(FONT_C, 4.4 * u)
    for s in ['This device complies with part 15 of the FCC Rules. Operation', 'is subject to the following two conditions: 1. This device may',
              'not cause harmful interference, and 2. this device must accept', 'any interference received, including interference that may',
              'cause undesired operation.']:
        d.text((mx + int(1 * u), y), s, font=fs, fill=T2); y += int(4.9 * u)
    y += int(1.5 * u)
    for s in ['SZ DJI TECHNOLOGY CO., LTD.', 'Address: Lobby of T2, DJI Sky City, No. 53 Xianyuan Road,',
              'Xili Community, Xili Street, Nanshan District, Shenzhen, China.']:
        d.text((mx + int(1 * u), y), s, font=fs, fill=T2); y += int(4.9 * u)
    d.text((mx + int(1 * u), y), '深圳市大疆创新科技有限公司   中国制造   Made in China', font=f(FONT_ZH, 4.4 * u), fill=T2)
    y += int(7 * u)
    # compatibility box
    fcb = f(FONT_CB, 4.6 * u)
    bw = int(max(Wpx * 0.40, d.textlength(compat, font=fcb) + 5 * u)); bh = int(13 * u)
    d.rectangle((mx, y, mx + bw, y + bh), fill=(205, 206, 208, 255))
    d.text((mx + int(2 * u), y + int(1 * u)), 'COMPATIBILITY / 适配机型', font=f(FONT_ZHB, 4.4 * u), fill=(30, 30, 32))
    d.text((mx + int(2 * u), y + int(6.6 * u)), compat, font=fcb, fill=(30, 30, 32))
    # marks: 10-year EFUP, CE, UKCA, bin
    xm = mx + bw + int(4 * u); ym = y + int(1 * u); r = int(5.5 * u); lw = max(2, int(1.0 * u))
    d.ellipse((xm, ym, xm + 2 * r, ym + 2 * r), outline=T1, width=lw)
    d.text((xm + r, ym + r), '10', font=f(FONT_B, 5 * u), fill=T1, anchor='mm')
    xm += 2 * r + int(3 * u)
    for k in range(2):
        cx = xm + r + k * int(10 * u)
        d.arc((cx - r, ym, cx + r, ym + 2 * r), 60, 300, fill=T1, width=lw + 1)
        if k == 1:
            d.line((cx - r + lw, ym + r, cx + int(1 * u), ym + r), fill=T1, width=lw)
    xm += int(22 * u)
    d.text((xm, ym - int(1 * u)), 'UK', font=f(FONT_B, 6 * u), fill=T1); d.text((xm, ym + int(5 * u)), 'CA', font=f(FONT_B, 6 * u), fill=T1)
    xm += int(12 * u)
    d.polygon([(xm + 1 * u, ym), (xm + 9 * u, ym), (xm + 8 * u, ym + 10 * u), (xm + 2 * u, ym + 10 * u)], outline=T1, width=lw)
    d.line((xm - 1 * u, ym - 1 * u, xm + 11 * u, ym + 11 * u), fill=T1, width=lw)
    d.line((xm + 11 * u, ym - 1 * u, xm - 1 * u, ym + 11 * u), fill=T1, width=lw)
    d.rectangle((xm, ym + 12 * u, xm + 10 * u, ym + 13 * u), fill=T1)
    # QR + serial (top right)
    qs = int(24 * u)
    try:
        import qrcode
        q = qrcode.QRCode(border=0, box_size=1); q.add_data(qr_data); q.make()
        qm = np.array(q.get_matrix(), bool)
    except Exception:
        qm = np.random.default_rng(5).random((25, 25)) > 0.5
    qx, qy = Wpx - mx - qs - int(2 * u), int(22 * u)
    d.rectangle((qx - 2 * u, qy - 2 * u, qx + qs + 2 * u, qy + qs + 9 * u), fill=(236, 236, 236, 255))
    qimg = Image.fromarray(np.where(qm, 0, 255).astype(np.uint8)).resize((qs, qs), Image.NEAREST)
    im.paste(Image.merge('RGBA', [qimg] * 3 + [Image.new('L', qimg.size, 255)]), (int(qx), int(qy)))
    if serial:
        fsn = f(FONT_CB, 4.0 * u)
        while d.textlength(serial, font=fsn) > qs + 3 * u and fsn.size > 6:
            fsn = f(FONT_CB, fsn.size - 1)
        d.text((qx + qs / 2, qy + qs + 1 * u), serial, font=fsn, fill=(20, 20, 20), anchor='ma')
    # BC + RCM
    d.ellipse((qx - 8 * u, qy + qs + 14 * u, qx + 2 * u, qy + qs + 24 * u), outline=T1, width=lw)
    d.text((qx - 3 * u, qy + qs + 19 * u), 'BC', font=f(FONT_R, 4 * u), fill=T1, anchor='mm')
    d.polygon([(qx + 8 * u, qy + qs + 14 * u), (qx + 20 * u, qy + qs + 14 * u), (qx + 14 * u, qy + qs + 24 * u)], outline=T1, width=lw)
    return np.asarray(im, float)


def write_block_assets(OUT, MODEL, modid='djimod', display=None):
    """blockstate / block model (particle only) / item model (builtin/entity + display)."""
    os.makedirs(OUT, exist_ok=True)
    bs = {'variants': {f'facing={f}': {'model': f'{modid}:block/{MODEL}'} for f in ('north', 'south', 'east', 'west')}}
    json.dump(bs, open(os.path.join(OUT, f'{MODEL}.blockstate.json'), 'w'), indent=1)
    json.dump({'textures': {'particle': f'{modid}:block/{MODEL}'}}, open(os.path.join(OUT, f'{MODEL}.block.json'), 'w'), indent=1)
    json.dump({'parent': 'builtin/entity', 'display': display or {}}, open(os.path.join(OUT, f'{MODEL}.item.json'), 'w'), indent=1)


BLOCK_DISPLAY = {
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, -1.5, 0], 'scale': [0.55, 0.55, 0.55]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
}
