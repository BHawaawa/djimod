# -*- coding: utf-8 -*-
"""2D art for the Redmi K90 phone item (screen layers, boot logos, charge ring, app icons, UI mock).

All screen art is authored in *screen texels*: TPX texels per model px.  The visible screen is
SW x SH model px (see gen_redmi_k90.py), i.e. round(SW*TPX) x round(SH*TPX) texels, origin top-left
as seen by a viewer looking at the front glass.

Black-background layers (boot logos, spinner, charge ring) are premultiplied onto black and given a
binary alpha, because GeckoLib renders items with an alpha *cutout* layer (no blending) and the glass
behind them is black anyway.
"""
import math, os, json
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

TPX = 12
FONT_DIR = '/usr/share/fonts/truetype'
F_POP_M = f'{FONT_DIR}/google-fonts/Poppins-Medium.ttf'
F_POP_SB = f'{FONT_DIR}/google-fonts/Poppins-Bold.ttf'
F_POP_BI = f'{FONT_DIR}/google-fonts/Poppins-BoldItalic.ttf'
F_POP_L = f'{FONT_DIR}/google-fonts/Poppins-Light.ttf'
F_POP_R = f'{FONT_DIR}/google-fonts/Poppins-Regular.ttf'
F_DJV = f'{FONT_DIR}/dejavu/DejaVuSans.ttf'
F_DJV_B = f'{FONT_DIR}/dejavu/DejaVuSans-Bold.ttf'
F_CJK = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'


def font(path, size):
    return ImageFont.truetype(path, int(size))


# ============================================================================ helpers
def rounded_mask(w, h, r, ss=4):
    m = Image.new('L', (w * ss, h * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * ss - 1, h * ss - 1), r * ss, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def on_black(rgba, thr=10):
    """premultiply an RGBA image onto black and binarise alpha (cutout friendly)."""
    a = np.asarray(rgba.convert('RGBA')).astype(float)
    al = a[..., 3:] / 255.0
    rgb = a[..., :3] * al
    out = np.zeros_like(a)
    out[..., :3] = rgb
    out[..., 3] = np.where(a[..., 3] > thr, 255, 0)
    return out.astype(np.uint8)


def over(bg_rgb, rgba):
    """composite RGBA over an RGB background array, keep alpha binary from rgba."""
    a = np.asarray(rgba.convert('RGBA')).astype(float)
    al = a[..., 3:] / 255.0
    rgb = bg_rgb.astype(float) * (1 - al) + a[..., :3] * al
    out = np.zeros_like(a)
    out[..., :3] = rgb
    out[..., 3] = np.where(a[..., 3] > 10, 255, 0)
    return out.astype(np.uint8)


def text_img(txt, fpath, size, color=(255, 255, 255), pad=4, tracking=0.0, italic_shear=0.0):
    f = font(fpath, size)
    if tracking == 0:
        bb = f.getbbox(txt)
        w, h = bb[2] - bb[0] + 2 * pad, bb[3] - bb[1] + 2 * pad
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(im).text((pad - bb[0], pad - bb[1]), txt, font=f, fill=color + (255,))
    else:
        widths = [f.getbbox(ch)[2] - f.getbbox(ch)[0] if ch != ' ' else size * 0.3 for ch in txt]
        adv = [f.getlength(ch) for ch in txt]
        total = sum(adv) + tracking * size * (len(txt) - 1)
        bb = f.getbbox(txt)
        h = bb[3] - bb[1] + 2 * pad
        im = Image.new('RGBA', (int(total + 2 * pad), h), (0, 0, 0, 0))
        d = ImageDraw.Draw(im); x = pad
        for ch, a in zip(txt, adv):
            d.text((x, pad - bb[1]), ch, font=f, fill=color + (255,)); x += a + tracking * size
    if italic_shear:
        w, h = im.size
        im = im.transform((w + int(abs(italic_shear) * h), h), Image.AFFINE, (1, italic_shear, -abs(italic_shear) * h if italic_shear < 0 else 0, 0, 1, 0),
                          Image.BICUBIC)
    return im


def fit_center(canvas, im, cx, cy, max_w=None, max_h=None):
    w, h = im.size
    s = 1.0
    if max_w: s = min(s, max_w / w)
    if max_h: s = min(s, max_h / h)
    if s != 1.0:
        im = im.resize((max(1, int(w * s)), max(1, int(h * s))), Image.LANCZOS)
    canvas.alpha_composite(im, (int(round(cx - im.size[0] / 2)), int(round(cy - im.size[1] / 2))))
    return im.size


def _persp_coeffs(dst, src):
    """PIL PERSPECTIVE coefficients mapping output (dst) points to input (src) points."""
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float)).tolist()


# ============================================================================ wallpaper (from the user's reference render)
def wallpaper(ref_png, W, H, r_px):
    """crop the front-view screen of reference image 8, remove the punch hole, upscale, rounded corners."""
    src = Image.open(ref_png).convert('RGB')
    # image 8 (right phone) is shot with a slight yaw: inner-bezel edges measured by line fits
    #   left x=516, right x=768, top y=12.10+0.0221x, bottom y=588.6-0.020x  -> perspective rectify
    ins = 2.0
    TL = (516 + ins, 12.10 + 0.0221 * 516 + ins); TR = (768 - ins, 12.10 + 0.0221 * 768 + ins)
    BR = (768 - ins, 588.6 - 0.020 * 768 - ins); BL = (516 + ins, 588.6 - 0.020 * 516 - ins)
    cw, ch = 252, 552
    crop = np.asarray(src.transform((cw, ch), Image.PERSPECTIVE, _persp_coeffs([(0, 0), (cw, 0), (cw, ch), (0, ch)], [TL, TR, BR, BL]),
                                    Image.BICUBIC)).astype(float)
    # inpaint the punch-hole camera (it is modelled separately): row-wise linear fill
    dk = crop[:40, 90:160].sum(2) < 300
    ys, xs = np.where(dk)
    hc = (90 + xs.mean(), ys.mean()) if len(xs) else (126.0, 18.0)
    hr = 9.5
    hh, ww = crop.shape[:2]
    for y in range(max(0, int(hc[1] - hr - 1)), min(hh, int(hc[1] + hr + 2))):
        dy = y - hc[1]
        if abs(dy) > hr: continue
        half = math.sqrt(hr * hr - dy * dy) + 1.5
        xa, xb = int(hc[0] - half) - 1, int(hc[0] + half) + 1
        xa, xb = max(0, xa), min(ww - 1, xb)
        ca, cb = crop[y, xa].copy(), crop[y, xb].copy()
        for x in range(xa + 1, xb):
            t = (x - xa) / (xb - xa)
            crop[y, x] = ca * (1 - t) + cb * t
    im =Image.fromarray(np.clip(crop, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    im = im.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
    a = np.asarray(im).astype(np.uint8)
    mask = np.asarray(rounded_mask(W, H, r_px))
    out = np.zeros((H, W, 4), np.uint8)
    out[..., :3] = a
    out[..., 3] = np.where(mask > 127, 255, 0)
    return out


# ============================================================================ boot / shutdown / charge art
def boot_redmi(W, H):
    """'REDMI' wordmark (white on black)."""
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    t = text_img('REDMI', F_POP_SB, H * 3, tracking=0.06)
    fit_center(im, t, W / 2, H / 2, max_w=W * 0.98, max_h=H * 0.92)
    return on_black(im)


def boot_android(W, H):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    t = text_img('Powered by Android', F_POP_L, H * 3, color=(200, 200, 200))
    fit_center(im, t, W / 2, H / 2, max_w=W * 0.98, max_h=H * 0.9)
    return on_black(im)


def boot_hyperos(W, H):
    """'Xiaomi HyperOS' wordmark with a soft warm->cool gradient over the letters."""
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    t = text_img('Xiaomi HyperOS', F_POP_M, H * 3)
    fit_center(im, t, W / 2, H / 2, max_w=W * 0.98, max_h=H * 0.8)
    a = np.asarray(im).astype(float)
    u = np.linspace(0, 1, W)[None, :]
    g0, g1 = np.array([255, 196, 140]), np.array([170, 205, 255])
    grad = g0[None, None, :] * (1 - u[..., None]) + g1[None, None, :] * u[..., None]
    a[..., :3] = grad * 0.35 + 255 * 0.65
    return on_black(Image.fromarray(a.astype(np.uint8), 'RGBA'))


def spinner(S):
    """shutdown spinner: grey arc fading into black (rotated by the animation)."""
    ss = 4; N = S * ss
    y, x = np.mgrid[0:N, 0:N] + 0.5
    c = N / 2; dx, dy = x - c, y - c
    r = np.hypot(dx, dy) / ss
    ang = (np.degrees(np.arctan2(dy, dx)) + 360) % 360
    ring = (r > S * 0.36) & (r < S * 0.46)
    k = ang / 360.0
    col = np.zeros((N, N, 4))
    col[..., :3] = (230 * k)[..., None]
    col[..., 3] = np.where(ring & (k > 0.08), 255, 0)
    im = Image.fromarray(col.astype(np.uint8), 'RGBA').resize((S, S), Image.LANCZOS)
    return on_black(im, thr=40)


def charge_ring(S):
    """off-mode charging ring: bright green head, tail fading to black, faint full track."""
    ss = 4; N = S * ss
    y, x = np.mgrid[0:N, 0:N] + 0.5
    c = N / 2; dx, dy = x - c, y - c
    r = np.hypot(dx, dy) / S / ss                      # 0..0.707 (fraction of the texture size)
    ang = (np.degrees(np.arctan2(dy, dx)) + 360) % 360
    k = ang / 360.0
    track = (r > 0.40) & (r < 0.47)
    glow = np.exp(-((r - 0.435) / 0.05) ** 2)
    head = np.array([96, 255, 150]); tail = np.array([20, 120, 190])
    colk = head[None, None, :] * k[..., None] + tail[None, None, :] * (1 - k[..., None])
    rgb = colk * (k[..., None] ** 1.6) * glow[..., None]
    rgb = np.maximum(rgb, (np.array([28, 34, 32]) * track[..., None]))
    alpha = np.where((r > 0.36) & (r < 0.51) & (rgb.sum(2) > 18), 255, 0)
    col = np.zeros((N, N, 4)); col[..., :3] = rgb; col[..., 3] = alpha
    im = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), 'RGBA').resize((S, S), Image.LANCZOS)
    a = np.asarray(im).copy(); a[..., 3] = np.where(a[..., 3] > 60, 255, 0)
    return a


def bolt_poly(cx, cy, s):
    """lightning bolt polygon centred at (cx,cy), height 2s."""
    pts = [(0.18, -1.0), (-0.62, 0.12), (-0.05, 0.12), (-0.22, 1.0), (0.62, -0.16), (0.05, -0.16)]
    return [(cx + px * s, cy + py * s) for px, py in pts]


def bolt(W, H, color=(96, 255, 150)):
    ss = 4
    im = Image.new('RGBA', (W * ss, H * ss), (0, 0, 0, 0))
    ImageDraw.Draw(im).polygon(bolt_poly(W * ss / 2, H * ss / 2, min(W, H * 0.62) * ss * 0.8), fill=color + (255,))
    im = im.resize((W, H), Image.LANCZOS)
    return on_black(im, thr=50)


def charge_pill(W, H, bg_rgb):
    """HyperOS plug-in pill (dark capsule, green bolt on the left; the % is drawn live by Java)."""
    ss = 4
    im = Image.new('RGBA', (W * ss, H * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W * ss - 1, H * ss - 1), H * ss / 2, fill=(22, 24, 26, 255))
    d.rounded_rectangle((3 * ss, 3 * ss, W * ss - 1 - 3 * ss, H * ss - 1 - 3 * ss), H * ss / 2 - 3 * ss, outline=(60, 64, 66, 255), width=ss)
    d.ellipse((H * ss * 0.18, H * ss * 0.18, H * ss * 0.82, H * ss * 0.82), fill=(40, 200, 110, 255))
    d.polygon(bolt_poly(H * ss / 2, H * ss / 2, H * ss * 0.24), fill=(255, 255, 255, 255))
    im = im.resize((W, H), Image.LANCZOS)
    return over(bg_rgb, im)


def punch_hole(S):
    ss = 4; N = S * ss
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, N - 1, N - 1), fill=(6, 6, 8, 255))
    d.ellipse((N * 0.22, N * 0.22, N * 0.78, N * 0.78), fill=(14, 14, 26, 255))
    d.ellipse((N * 0.36, N * 0.36, N * 0.64, N * 0.64), fill=(30, 26, 52, 255))
    d.ellipse((N * 0.30, N * 0.28, N * 0.42, N * 0.40), fill=(120, 110, 170, 255))
    im = im.resize((S, S), Image.LANCZOS)
    a = np.asarray(im).copy(); a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
    return a


# ============================================================================ app icons (HyperOS style, 96x96)
APPS = [  # id, zh label, en label
    ('phone', '电话', 'Phone'), ('messages', '短信', 'Messages'), ('browser', '浏览器', 'Browser'), ('camera', '相机', 'Camera'),
    ('clock', '时钟', 'Clock'), ('weather', '天气', 'Weather'), ('calendar', '日历', 'Calendar'), ('gallery', '相册', 'Gallery'),
    ('settings', '设置', 'Settings'), ('appstore', '应用商店', 'GetApps'), ('music', '音乐', 'Music'), ('files', '文件管理', 'Files'),
    ('notes', '笔记', 'Notes'), ('calculator', '计算器', 'Calculator'), ('themes', '主题', 'Themes'), ('security', '手机管家', 'Security'),
    ('mihome', '米家', 'Mi Home'), ('djifly', 'DJI Fly', 'DJI Fly'), ('compass', '指南针', 'Compass'), ('recorder', '录音机', 'Recorder'),
]
DEFAULT_DOCK = ['phone', 'messages', 'browser', 'camera']
DEFAULT_GRID = ['clock', 'weather', 'calendar', 'gallery', 'settings', 'appstore', 'music', 'files',
                'notes', 'calculator', 'themes', 'security', 'mihome', 'djifly']


def _grad(N, c0, c1, vertical=True):
    t = np.linspace(0, 1, N)
    t = t[:, None] if vertical else t[None, :]
    g = np.array(c0)[None, None, :] * (1 - t[..., None]) + np.array(c1)[None, None, :] * t[..., None]
    return np.broadcast_to(g, (N, N, 3)).copy()


def icon(app, N=96):
    ss = 4; M = N * ss
    grads = {
        'phone': ((86, 222, 120), (34, 178, 86)), 'messages': ((90, 220, 140), (30, 175, 110)),
        'browser': ((90, 170, 255), (40, 110, 240)), 'camera': ((70, 72, 78), (30, 31, 35)),
        'clock': ((40, 42, 48), (18, 18, 22)), 'weather': ((110, 190, 255), (50, 130, 240)),
        'calendar': ((255, 255, 255), (238, 238, 240)), 'gallery': ((255, 255, 255), (240, 240, 244)),
        'settings': ((150, 156, 166), (100, 106, 116)), 'appstore': ((255, 160, 70), (255, 104, 40)),
        'music': ((255, 96, 120), (230, 40, 90)), 'files': ((255, 205, 80), (250, 160, 30)),
        'notes': ((255, 214, 90), (250, 186, 40)), 'calculator': ((255, 140, 50), (240, 96, 30)),
        'themes': ((190, 120, 255), (110, 80, 240)), 'security': ((70, 210, 150), (20, 160, 110)),
        'mihome': ((255, 255, 255), (238, 240, 242)), 'djifly': ((38, 40, 44), (12, 12, 14)),
        'compass': ((60, 64, 72), (24, 26, 30)), 'recorder': ((255, 110, 90), (235, 60, 60)),
    }
    c0, c1 = grads.get(app, ((180, 180, 190), (120, 120, 130)))
    base = Image.fromarray(_grad(M, c0, c1).astype(np.uint8), 'RGB').convert('RGBA')
    d = ImageDraw.Draw(base)
    W = (255, 255, 255, 255)
    cx = cy = M / 2
    u = M / 96.0
    if app == 'phone':
        f = font(F_DJV, 58 * u); d.text((cx, cy + 2 * u), '✆', font=f, fill=W, anchor='mm')
    elif app == 'messages':
        d.rounded_rectangle((22 * u, 26 * u, 74 * u, 64 * u), 16 * u, fill=W)
        d.polygon([(32 * u, 60 * u), (28 * u, 74 * u), (46 * u, 62 * u)], fill=W)
        for i in range(3):
            d.ellipse(((36 + 12 * i) * u - 4 * u, 45 * u - 4 * u, (36 + 12 * i) * u + 4 * u, 45 * u + 4 * u), fill=(40, 180, 110, 255))
    elif app == 'browser':
        d.ellipse((22 * u, 22 * u, 74 * u, 74 * u), outline=W, width=int(5 * u))
        d.ellipse((38 * u, 22 * u, 58 * u, 74 * u), outline=W, width=int(4 * u))
        d.line((22 * u, 48 * u, 74 * u, 48 * u), fill=W, width=int(4 * u))
    elif app == 'camera':
        d.ellipse((24 * u, 24 * u, 72 * u, 72 * u), fill=(210, 212, 216, 255))
        d.ellipse((31 * u, 31 * u, 65 * u, 65 * u), fill=(16, 16, 22, 255))
        d.ellipse((39 * u, 39 * u, 57 * u, 57 * u), fill=(60, 50, 110, 255))
        d.ellipse((42 * u, 41 * u, 49 * u, 48 * u), fill=(170, 160, 230, 255))
        d.ellipse((70 * u, 18 * u, 78 * u, 26 * u), fill=(255, 120, 60, 255))
    elif app == 'clock':
        d.ellipse((18 * u, 18 * u, 78 * u, 78 * u), fill=(250, 250, 250, 255))
        for k in range(12):
            a = math.radians(k * 30); r0, r1 = 24 * u, 28 * u
            d.line((cx + r0 * math.sin(a), cy - r0 * math.cos(a), cx + r1 * math.sin(a), cy - r1 * math.cos(a)), fill=(40, 40, 44, 255), width=int(2 * u))
        d.line((cx, cy, cx + 13 * u, cy - 8 * u), fill=(30, 30, 34, 255), width=int(4 * u))
        d.line((cx, cy, cx - 2 * u, cy - 22 * u), fill=(30, 30, 34, 255), width=int(3 * u))
        d.line((cx, cy + 6 * u, cx + 4 * u, cy - 24 * u), fill=(255, 100, 40, 255), width=int(1.6 * u))
        d.ellipse((cx - 3 * u, cy - 3 * u, cx + 3 * u, cy + 3 * u), fill=(255, 100, 40, 255))
    elif app == 'weather':
        d.ellipse((46 * u, 20 * u, 76 * u, 50 * u), fill=(255, 210, 70, 255))
        for (x0, y0, x1, y1) in [(18, 44, 50, 74), (36, 34, 70, 68), (52, 46, 80, 74)]:
            d.ellipse((x0 * u, y0 * u, x1 * u, y1 * u), fill=W)
        d.rectangle((34 * u, 58 * u, 66 * u, 74 * u), fill=W)
    elif app == 'calendar':
        f = font(F_POP_R, 14 * u); d.text((cx, 24 * u), 'SUN', font=f, fill=(255, 70, 60, 255), anchor='mm')
        f = font(F_POP_M, 44 * u); d.text((cx, 56 * u), '15', font=f, fill=(30, 30, 34, 255), anchor='mm')
    elif app == 'gallery':
        petals = [(255, 120, 90), (255, 190, 60), (110, 210, 120), (70, 170, 250), (170, 120, 250), (255, 110, 170)]
        for k, col in enumerate(petals):
            a = math.radians(k * 60); px, py = cx + 13 * u * math.cos(a), cy + 13 * u * math.sin(a)
            d.ellipse((px - 13 * u, py - 13 * u, px + 13 * u, py + 13 * u), fill=col + (215,))
    elif app == 'settings':
        f = font(F_DJV, 62 * u); d.text((cx, cy + 2 * u), '⚙', font=f, fill=W, anchor='mm')
    elif app == 'appstore':
        d.rounded_rectangle((24 * u, 34 * u, 72 * u, 74 * u), 8 * u, fill=W)
        d.arc((34 * u, 18 * u, 62 * u, 48 * u), 180, 360, fill=W, width=int(5 * u))
        f = font(F_POP_SB, 22 * u); d.text((cx, 55 * u), 'mi', font=f, fill=(255, 110, 40, 255), anchor='mm')
    elif app == 'music':
        f = font(F_DJV, 60 * u); d.text((cx, cy + 3 * u), '♫', font=f, fill=W, anchor='mm')
    elif app == 'files':
        d.rounded_rectangle((20 * u, 26 * u, 50 * u, 40 * u), 5 * u, fill=(255, 245, 220, 255))
        d.rounded_rectangle((20 * u, 32 * u, 76 * u, 72 * u), 7 * u, fill=W)
    elif app == 'notes':
        d.rounded_rectangle((24 * u, 20 * u, 72 * u, 76 * u), 6 * u, fill=W)
        for k in range(4):
            d.line((32 * u, (34 + 10 * k) * u, 64 * u, (34 + 10 * k) * u), fill=(250, 186, 40, 255), width=int(3 * u))
    elif app == 'calculator':
        f = font(F_DJV_B, 26 * u)
        for (tx, ty, s) in [(33, 33, '+'), (63, 33, '−'), (33, 63, '×'), (63, 63, '=')]:
            d.text((tx * u, ty * u), s, font=f, fill=W, anchor='mm')
    elif app == 'themes':
        d.ellipse((24 * u, 24 * u, 72 * u, 72 * u), outline=W, width=int(6 * u))
        d.pieslice((30 * u, 30 * u, 66 * u, 66 * u), 200, 340, fill=W)
    elif app == 'security':
        d.polygon([(cx, 18 * u), (74 * u, 30 * u), (70 * u, 58 * u), (cx, 80 * u), (26 * u, 58 * u), (22 * u, 30 * u)], fill=W)
        d.line((36 * u, 48 * u, 45 * u, 58 * u, 62 * u, 38 * u), fill=(30, 170, 110, 255), width=int(6 * u))
    elif app == 'mihome':
        d.rounded_rectangle((26 * u, 26 * u, 70 * u, 70 * u), 14 * u, fill=(255, 105, 0, 255))
        f = font(F_CJK, 30 * u); d.text((cx, cy - 2 * u), '米', font=f, fill=W, anchor='mm')
    elif app == 'djifly':
        f = font(F_POP_SB, 30 * u); d.text((cx, 42 * u), 'DJI', font=f, fill=W, anchor='mm')
        f = font(F_POP_R, 14 * u); d.text((cx, 66 * u), 'FLY', font=f, fill=(200, 200, 200, 255), anchor='mm')
    elif app == 'compass':
        d.ellipse((20 * u, 20 * u, 76 * u, 76 * u), outline=W, width=int(3 * u))
        d.polygon([(cx, 24 * u), (cx + 7 * u, cy), (cx - 7 * u, cy)], fill=(255, 70, 60, 255))
        d.polygon([(cx, 72 * u), (cx + 7 * u, cy), (cx - 7 * u, cy)], fill=W)
    elif app == 'recorder':
        d.rounded_rectangle((38 * u, 18 * u, 58 * u, 56 * u), 10 * u, fill=W)
        d.arc((28 * u, 34 * u, 68 * u, 66 * u), 0, 180, fill=W, width=int(4 * u))
        d.line((cx, 66 * u, cx, 76 * u), fill=W, width=int(4 * u))
    base = base.resize((N, N), Image.LANCZOS)
    mask = rounded_mask(N, N, N * 0.24)
    a = np.asarray(base).copy()
    a[..., 3] = np.asarray(mask)
    if app in ('calendar', 'gallery', 'mihome'):            # light icons get a hairline edge so they read on the wallpaper
        edge = (np.asarray(mask) > 40) & (np.asarray(rounded_mask(N - 4, N - 4, (N - 4) * 0.24).resize((N, N))) < 40)
        a[edge, :3] = (a[edge, :3] * 0.85).astype(np.uint8)
    return a


# ============================================================================ home UI mock (mirrors client/PhoneScreenRenderer.java layout)
LAYOUT = {
    # all values in screen px (model px) from the screen's top-left corner, as in PhoneScreenRenderer.java
    'status_y': 1.15, 'status_h': 1.35, 'time_x': 2.6,
    'clock_y': 6.2, 'clock_h': 6.0, 'date_y': 13.4, 'date_h': 1.25,
    'grid_top': 19.0, 'row_pitch': 10.0, 'cols': 4, 'rows': 5, 'icon': 6.4, 'label_h': 1.05, 'label_gap': 0.55,
    'dock_y': 72.6, 'dots_y': 67.4,
    'charge_ring_y': 30.0, 'charge_pct_y': 46.0, 'pill_y': 75.4,
}


INK = (32, 32, 36, 255)


def home_ui_mock(wall_rgba, SW, SH, apps=None, dock=None, time_str='10:08', date_str='11月15日 星期日', battery=0.76,
                 charging=False, labels=True):
    """Python twin of the Java home overlay, used for previews / GIFs / documentation."""
    apps = DEFAULT_GRID if apps is None else apps
    dock = DEFAULT_DOCK if dock is None else dock
    lab = {a: z for a, z, e in APPS}
    T = TPX
    im = Image.fromarray(wall_rgba.copy(), 'RGBA')
    d = ImageDraw.Draw(im)
    L = LAYOUT
    W = INK                       # HyperOS switches to dark status/labels on this light wallpaper
    f = font(F_POP_M, L['status_h'] * T * 1.1)
    d.text((L['time_x'] * T, L['status_y'] * T), time_str, font=f, fill=W)
    bx1 = (SW - 2.4) * T; by0 = (L['status_y'] + 0.1) * T; bw, bh = 2.4 * T, 1.15 * T
    d.rounded_rectangle((bx1 - bw, by0, bx1, by0 + bh), 0.3 * T, outline=W, width=max(1, int(0.13 * T)))
    fill_col = (80, 230, 120, 255) if charging else W
    d.rectangle((bx1 - bw + 0.25 * T, by0 + 0.25 * T, bx1 - bw + 0.25 * T + (bw - 0.5 * T) * battery, by0 + bh - 0.25 * T), fill=fill_col)
    d.rectangle((bx1 + 0.05 * T, by0 + 0.35 * T, bx1 + 0.25 * T, by0 + bh - 0.35 * T), fill=W)
    fp = font(F_POP_M, 1.05 * T)
    d.text((bx1 - bw - 0.45 * T, by0 + bh / 2), f'{round(battery * 100)}', font=fp, fill=W, anchor='rm')
    sx = bx1 - bw - 3.8 * T
    for k in range(4):                                         # signal bars
        h = (0.35 + 0.22 * k) * T
        d.rectangle((sx - (3 - k) * 0.42 * T - 0.28 * T, by0 + bh - h, sx - (3 - k) * 0.42 * T, by0 + bh), fill=W)
    # wifi fan
    wx, wy = sx - 2.6 * T, by0 + bh
    for k, rr in enumerate((1.25, 0.85)):
        d.arc((wx - rr * T, wy - rr * T, wx + rr * T, wy + rr * T), 225, 315, fill=W, width=max(1, int(0.22 * T)))
    d.pieslice((wx - 0.42 * T, wy - 0.42 * T, wx + 0.42 * T, wy + 0.42 * T), 225, 315, fill=W)
    # clock widget
    f = font(F_POP_L, L['clock_h'] * T * 1.25)
    d.text((SW * T / 2, (L['clock_y'] + L['clock_h'] / 2) * T), time_str, font=f, fill=W, anchor='mm')
    f = font(F_CJK, L['date_h'] * T * 1.15)
    d.text((SW * T / 2, (L['date_y'] + L['date_h'] / 2) * T), date_str, font=f, fill=W, anchor='mm')
    icons = {}
    def put_icon(app, cx, cy):
        if app not in icons:
            icons[app] = Image.fromarray(icon(app, 96), 'RGBA').resize((int(L['icon'] * T), int(L['icon'] * T)), Image.LANCZOS)
        ic = icons[app]
        im.alpha_composite(ic, (int(cx * T - ic.size[0] / 2), int(cy * T - ic.size[1] / 2)))
    fl = font(F_CJK, L['label_h'] * T * 1.1)
    for i, app in enumerate(apps[:L['cols'] * L['rows']]):
        r, c = divmod(i, L['cols'])
        cx = SW * (0.125 + 0.25 * c); cy = L['grid_top'] + r * L['row_pitch'] + L['icon'] / 2
        put_icon(app, cx, cy)
        if labels:
            d.text((cx * T, (cy + L['icon'] / 2 + L['label_gap'] + L['label_h'] / 2) * T), lab.get(app, app), font=fl, fill=W, anchor='mm')
    # page dots
    for k in range(2):
        x = SW / 2 + (k - 0.5) * 1.3
        col = W if k == 0 else (30, 30, 34, 90)
        d.ellipse(((x - 0.3) * T, (L['dots_y'] - 0.3) * T, (x + 0.3) * T, (L['dots_y'] + 0.3) * T), fill=col)
    # dock
    for i, app in enumerate(dock[:4]):
        put_icon(app, SW * (0.125 + 0.25 * i), L['dock_y'])
    a = np.asarray(im).copy(); a[..., 3] = wall_rgba[..., 3]
    return a


def charge_off_mock(SW, SH, ring_rgba, bolt_rgba, ring_c, bolt_c, pct=57):
    """off-mode charging screen mock (black + ring + bolt + live %)."""
    T = TPX
    im = Image.new('RGBA', (int(round(SW * T)), int(round(SH * T))), (0, 0, 0, 255))
    for arr, (cx, cy) in ((ring_rgba, ring_c), (bolt_rgba, bolt_c)):
        p = Image.fromarray(arr, 'RGBA')
        im.alpha_composite(p, (int(cx * T - p.size[0] / 2), int(cy * T - p.size[1] / 2)))
    d = ImageDraw.Draw(im)
    f = font(F_POP_L, 4.2 * T)
    d.text((SW * T / 2, LAYOUT['charge_pct_y'] * T), f'{pct}%', font=f, fill=(255, 255, 255, 255), anchor='mm')
    return np.asarray(im)


if __name__ == '__main__':
    import sys
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for a, z, e in APPS:
        Image.fromarray(icon(a, 96), 'RGBA').save(os.path.join(out, f'{a}.png'))
    print('icons ->', out)
