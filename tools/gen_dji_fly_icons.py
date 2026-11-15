"""Builds the DJI Fly (RC 2) flight-interface icon atlases.

Every icon is drawn procedurally at 512 px (supersampled vector-style shapes traced from the
DJI "一篇看懂大疆飞行界面" reference sheets) and down-sampled into three atlases so the game can
pick the level closest to the on-screen size and sample it bilinearly (a hand-made mip chain):

    textures/gui/dji_fly_icons_128.png   8 x 8 cells of 128 px
    textures/gui/dji_fly_icons_64.png    8 x 8 cells of  64 px
    textures/gui/dji_fly_icons_32.png    8 x 8 cells of  32 px

Single-colour icons are white so Rc2FlightUi can tint them; a few (map app, attitude / assist
mini views, home marker) keep their reference colours. Cut-outs are transparent so the video
shows through exactly like the reference (play triangle, AUTO lettering, film perforations ...).

The index constants must match Rc2FlightUi.I_* .
Usage: python3 tools/gen_dji_fly_icons.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'fabric_mod', 'src', 'main', 'resources', 'assets', 'djimod', 'textures', 'gui')
S = 512  # master cell size

NARROW_BOLD = [
    '/usr/share/fonts/truetype/liberation/LiberationSansNarrow-Bold.ttf',
    '/usr/share/fonts/opentype/urw-base35/NimbusSansNarrow-Bold.otf',
    '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf',
]
BOLD = ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


class Icon:
    """White mask canvas with add / cut helpers in unit coordinates (0..1)."""

    def __init__(self):
        self.mask = Image.new('L', (S, S), 0)
        self.color = None  # optional RGBA layer for multi-colour icons

    @staticmethod
    def p(v):
        return v * S

    def _layer(self):
        return Image.new('L', (S, S), 0)

    def _apply(self, layer, cut):
        if cut:
            self.mask = ImageChops.subtract(self.mask, layer)
        else:
            self.mask = ImageChops.lighter(self.mask, layer)

    def poly(self, pts, cut=False):
        layer = self._layer()
        ImageDraw.Draw(layer).polygon([(self.p(x), self.p(y)) for x, y in pts], fill=255)
        self._apply(layer, cut)

    def rect(self, x0, y0, x1, y1, r=0.0, cut=False):
        layer = self._layer()
        ImageDraw.Draw(layer).rounded_rectangle([self.p(x0), self.p(y0), self.p(x1), self.p(y1)],
                                                radius=self.p(r), fill=255)
        self._apply(layer, cut)

    def rect_stroke(self, x0, y0, x1, y1, w, r=0.0, cut=False):
        layer = self._layer()
        ImageDraw.Draw(layer).rounded_rectangle([self.p(x0), self.p(y0), self.p(x1), self.p(y1)],
                                                radius=self.p(r), outline=255, width=max(1, int(self.p(w))))
        self._apply(layer, cut)

    def circle(self, cx, cy, r, cut=False):
        layer = self._layer()
        ImageDraw.Draw(layer).ellipse([self.p(cx - r), self.p(cy - r), self.p(cx + r), self.p(cy + r)], fill=255)
        self._apply(layer, cut)

    def ellipse(self, cx, cy, rx, ry, cut=False):
        layer = self._layer()
        ImageDraw.Draw(layer).ellipse([self.p(cx - rx), self.p(cy - ry), self.p(cx + rx), self.p(cy + ry)], fill=255)
        self._apply(layer, cut)

    def ring(self, cx, cy, r, w, cut=False):
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        d.ellipse([self.p(cx - r), self.p(cy - r), self.p(cx + r), self.p(cy + r)], fill=255)
        ri = r - w
        d.ellipse([self.p(cx - ri), self.p(cy - ri), self.p(cx + ri), self.p(cy + ri)], fill=0)
        self._apply(layer, cut)

    def ellipse_ring(self, cx, cy, rx, ry, w, cut=False):
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        d.ellipse([self.p(cx - rx), self.p(cy - ry), self.p(cx + rx), self.p(cy + ry)], fill=255)
        d.ellipse([self.p(cx - rx + w), self.p(cy - ry + w), self.p(cx + rx - w), self.p(cy + ry - w)], fill=0)
        self._apply(layer, cut)

    def arc(self, cx, cy, r, w, a0, a1, cut=False):
        """Annular sector, angles in degrees (0 = +x, clockwise because y grows down)."""
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        d.pieslice([self.p(cx - r), self.p(cy - r), self.p(cx + r), self.p(cy + r)], a0, a1, fill=255)
        ri = r - w
        d.ellipse([self.p(cx - ri), self.p(cy - ri), self.p(cx + ri), self.p(cy + ri)], fill=0)
        self._apply(layer, cut)

    def line(self, pts, w, cut=False, caps=True):
        layer = self._layer()
        d = ImageDraw.Draw(layer)
        sp = [(self.p(x), self.p(y)) for x, y in pts]
        d.line(sp, fill=255, width=max(1, int(self.p(w))), joint='curve')
        if caps:
            r = self.p(w) / 2
            for x, y in sp:
                d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        self._apply(layer, cut)

    def text(self, s, cx, cy, size, paths=NARROW_BOLD, cut=False, sx=1.0):
        layer = self._layer()
        f = font(paths, int(self.p(size)))
        tmp = Image.new('L', (S * 2, S), 0)
        d = ImageDraw.Draw(tmp)
        bbox = d.textbbox((0, 0), s, font=f)
        d.text((-bbox[0], -bbox[1]), s, font=f, fill=255)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        glyph = tmp.crop((0, 0, tw, th))
        if sx != 1.0:
            glyph = glyph.resize((max(1, int(tw * sx)), th), Image.LANCZOS)
            tw = glyph.width
        layer.paste(glyph, (int(self.p(cx) - tw / 2), int(self.p(cy) - th / 2)))
        self._apply(layer, cut)

    def image(self):
        if self.color is not None:
            return self.color
        img = Image.new('RGBA', (S, S), (255, 255, 255, 0))
        img.putalpha(self.mask)
        return img


def bezier(p0, p1, p2, p3, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t * t
        d = t ** 3
        pts.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return pts


def rot(pts, cx, cy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def nav_arrow(ic, cx, cy, s, cut=False, deg=0):
    pts = [(cx, cy - s), (cx + s * 0.78, cy + s * 0.9), (cx, cy + s * 0.45), (cx - s * 0.78, cy + s * 0.9)]
    ic.poly(rot(pts, cx, cy, deg), cut=cut)


# ------------------------------------------------------------------ icons

def i_home():
    ic = Icon()
    w = 0.075
    ic.line([(0.2, 0.47), (0.5, 0.2), (0.8, 0.47)], w)
    ic.line([(0.27, 0.42), (0.27, 0.82), (0.43, 0.82), (0.43, 0.62), (0.57, 0.62), (0.57, 0.82), (0.73, 0.82),
             (0.73, 0.42)], w)
    return ic


def i_back():
    ic = Icon()
    ic.line([(0.62, 0.2), (0.34, 0.5), (0.62, 0.8)], 0.08)
    return ic


def i_waypoint():
    ic = Icon()
    w = 0.065
    # navigation arrow bottom-left, path up to the first waypoint, S curve to the second
    nav_arrow(ic, 0.25, 0.74, 0.13)
    ic.line([(0.27, 0.58), (0.3, 0.36)], w)
    ic.ring(0.32, 0.27, 0.075, 0.05)
    pts = bezier((0.38, 0.31), (0.47, 0.42), (0.42, 0.78), (0.58, 0.78)) + \
        bezier((0.58, 0.78), (0.72, 0.78), (0.71, 0.5), (0.71, 0.36))
    ic.line(pts, w)
    ic.ring(0.71, 0.27, 0.075, 0.05)
    return ic


def i_focus():
    ic = Icon()
    w = 0.07
    ic.line([(0.34, 0.2), (0.24, 0.2), (0.22, 0.22), (0.22, 0.78), (0.24, 0.8), (0.34, 0.8)], w)
    ic.line([(0.66, 0.2), (0.76, 0.2), (0.78, 0.22), (0.78, 0.78), (0.76, 0.8), (0.66, 0.8)], w)
    ic.circle(0.5, 0.5, 0.06)
    return ic


def i_takeoff():
    ic = Icon()
    ic.ellipse_ring(0.5, 0.72, 0.37, 0.15, 0.06)
    shaft = [(0.42, 0.42), (0.58, 0.42), (0.58, 0.7), (0.42, 0.7)]
    head = [(0.5, 0.1), (0.75, 0.43), (0.25, 0.43)]
    # cut a gap around the arrow so it reads in front of the platform ring
    gap = Icon()
    gap.poly(head)
    gap.poly(shaft)
    grown = gap.mask.filter(__import__('PIL.ImageFilter', fromlist=['MaxFilter']).MaxFilter(25))
    ic.mask = ImageChops.subtract(ic.mask, grown)
    ic.poly(head)
    ic.poly(shaft)
    return ic


def i_land():
    ic = Icon()
    ic.ellipse_ring(0.5, 0.72, 0.37, 0.15, 0.06)
    shaft = [(0.42, 0.1), (0.58, 0.1), (0.58, 0.4), (0.42, 0.4)]
    head = [(0.5, 0.7), (0.75, 0.38), (0.25, 0.38)]
    gap = Icon()
    gap.poly(head)
    gap.poly(shaft)
    from PIL import ImageFilter
    ic.mask = ImageChops.subtract(ic.mask, gap.mask.filter(ImageFilter.MaxFilter(25)))
    ic.poly(head)
    ic.poly(shaft)
    return ic


def i_rth():
    from PIL import ImageFilter
    ic = Icon()
    ic.ellipse_ring(0.5, 0.74, 0.37, 0.15, 0.06)
    # H on the pad
    ic.line([(0.4, 0.67), (0.4, 0.8)], 0.05, caps=False)
    ic.line([(0.6, 0.67), (0.6, 0.8)], 0.05, caps=False)
    ic.line([(0.4, 0.735), (0.6, 0.735)], 0.05, caps=False)
    # lightning-like return arrow coming down onto the pad
    head = [(0.5, 0.64), (0.33, 0.43), (0.67, 0.43)]
    g = Icon()
    g.poly([(0.66, 0.08), (0.38, 0.32), (0.56, 0.32), (0.5, 0.46)])
    g.poly(head)
    ic.mask = ImageChops.subtract(ic.mask, g.mask.filter(ImageFilter.MaxFilter(21)))
    ic.poly([(0.68, 0.06), (0.36, 0.34), (0.56, 0.34), (0.5, 0.47), (0.58, 0.47), (0.66, 0.26), (0.5, 0.26)])
    ic.poly(head)
    return ic


def i_film():
    ic = Icon()
    ic.rect(0.08, 0.27, 0.92, 0.73, 0.02)
    n = 7
    for row_y in (0.3, 0.64):
        for i in range(n):
            x0 = 0.13 + i * (0.74 / n)
            ic.rect(x0, row_y, x0 + 0.06, row_y + 0.06, cut=True)
    ic.rect(0.08, 0.385, 0.92, 0.405, cut=True)
    ic.rect(0.08, 0.595, 0.92, 0.615, cut=True)
    return ic


def i_photo_frame():
    ic = Icon()
    ic.rect_stroke(0.1, 0.24, 0.9, 0.76, 0.06, 0.05)
    ic.rect(0.21, 0.35, 0.79, 0.65, 0.01)
    return ic


def i_play():
    ic = Icon()
    ic.rect(0.12, 0.22, 0.88, 0.78, 0.04)
    ic.poly([(0.42, 0.36), (0.42, 0.64), (0.64, 0.5)], cut=True)
    return ic


def i_map_app():
    ic = Icon()
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    m = Image.new('L', (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle([0.02 * S, 0.02 * S, 0.98 * S, 0.98 * S], radius=0.12 * S, fill=255)
    base = Image.new('RGBA', (S, S), (52, 168, 83, 255))
    d = ImageDraw.Draw(base)
    P = lambda pts: [(x * S, y * S) for x, y in pts]
    d.polygon(P([(0, 0.5), (0.42, 1.0), (0, 1.0)]), fill=(66, 133, 244, 255))
    d.polygon(P([(0.42, 0.0), (0.62, 0.0), (0.2, 0.47), (0.62, 1.0), (0.42, 1.0), (0.0, 0.47), (0.0, 0.43)]),
              fill=(255, 255, 255, 255))
    d.polygon(P([(0.0, 0.43), (0.38, 0.0), (0.42, 0.0), (0.0, 0.47)]), fill=(255, 255, 255, 255))
    # yellow pin
    cx, cy, r = 0.7, 0.4, 0.16
    d.ellipse(P([(cx - r, cy - r), (cx + r, cy + r)]), fill=(251, 188, 4, 255))
    d.polygon(P([(cx - r * 0.86, cy + r * 0.5), (cx + r * 0.86, cy + r * 0.5), (cx, cy + r * 2.0)]),
              fill=(251, 188, 4, 255))
    d.ellipse(P([(cx - r * 0.38, cy - r * 0.38), (cx + r * 0.38, cy + r * 0.38)]), fill=(52, 168, 83, 255))
    img.paste(base, (0, 0), m)
    ic.color = img
    return ic


def i_att_mini():
    ic = Icon()
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0.02 * S, 0.02 * S, 0.98 * S, 0.98 * S], radius=0.1 * S, fill=(70, 70, 72, 255))
    d.ellipse([0.12 * S, 0.12 * S, 0.88 * S, 0.88 * S], fill=(128, 128, 130, 255))
    for i in range(9):
        y = 0.16 + i * 0.08
        d.line([(0.5 * S, y * S), (0.5 * S, (y + 0.04) * S)], fill=(175, 175, 178, 255), width=4)
        d.line([(y * S, 0.5 * S), ((y + 0.04) * S, 0.5 * S)], fill=(175, 175, 178, 255), width=4)
    d.polygon([(0.36 * S, 0.3 * S), (0.5 * S, 0.44 * S), (0.64 * S, 0.3 * S), (0.5 * S, 0.4 * S)],
              fill=(46, 200, 120, 255))
    pts = [(0.5, 0.22), (0.69, 0.73), (0.5, 0.62), (0.31, 0.73)]
    d.polygon([(x * S, y * S) for x, y in pts], fill=(40, 150, 245, 255), outline=(255, 255, 255, 255))
    d.line([(x * S, y * S) for x, y in pts + [pts[0]]], fill=(255, 255, 255, 255), width=8, joint='curve')
    ic.color = img
    return ic


def i_target(ic):
    ic.ring(0.5, 0.5, 0.36, 0.07)
    ic.ring(0.5, 0.5, 0.25, 0.08)
    ic.ring(0.5, 0.5, 0.12, 0.07)
    for a in (45, 135):
        pts = rot([(0.5, 0.0), (0.5, 1.0)], 0.5, 0.5, a)
        ic.line(pts, 0.07, cut=True, caps=False)
    ic.ring(0.5, 0.5, 0.12, 0.07)


def i_assist_mini():
    ic = Icon()
    t = Icon()
    t.rect(0.02, 0.02, 0.98, 0.98, 0.1)
    inner = Icon()
    i_target(inner)
    t.mask = ImageChops.subtract(t.mask, inner.mask)
    img = Image.new('RGBA', (S, S), (70, 70, 72, 0))
    img.putalpha(t.mask)
    white = Image.new('RGBA', (S, S), (255, 255, 255, 0))
    white.putalpha(inner.mask)
    img.alpha_composite(white)
    ic.color = img
    return ic


def i_target_icon():
    ic = Icon()
    i_target(ic)
    return ic


def i_alt():
    ic = Icon()
    ic.rect(0.24, 0.17, 0.76, 0.24)
    ic.poly([(0.5, 0.28), (0.66, 0.46), (0.34, 0.46)])
    ic.rect(0.46, 0.44, 0.54, 0.66)
    ic.rect(0.2, 0.72, 0.8, 0.8)
    return ic


def i_dist():
    ic = Icon()
    ic.rect(0.16, 0.22, 0.23, 0.78)
    ic.rect(0.77, 0.22, 0.84, 0.78)
    ic.rect(0.3, 0.46, 0.56, 0.54)
    ic.poly([(0.7, 0.5), (0.52, 0.35), (0.52, 0.65)])
    return ic


def i_obst_omni():
    ic = Icon()
    ic.ring(0.5, 0.5, 0.45, 0.1)
    ic.circle(0.5, 0.5, 0.29)
    for a in (45, 135):
        ic.line(rot([(0.5, 0.2), (0.5, 0.8)], 0.5, 0.5, a), 0.09, cut=True, caps=False)
    ic.circle(0.5, 0.5, 0.09, cut=True)
    ic.circle(0.5, 0.5, 0.045)
    return ic


def i_obst_updown():
    ic = Icon()
    ic.poly([(0.5, 0.06), (0.8, 0.36), (0.6, 0.36), (0.6, 0.64), (0.8, 0.64), (0.5, 0.94), (0.2, 0.64), (0.4, 0.64),
             (0.4, 0.36), (0.2, 0.36)])
    return ic


def i_obst_fwd():
    ic = Icon()
    ic.arc(0.5, 0.62, 0.46, 0.27, 225, 315)
    ic.arc(0.5, 0.62, 0.36, 0.05, 220, 320, cut=True)
    ic.line([(0.5, 0.5), (0.27, 0.92)], 0.07)
    ic.line([(0.5, 0.5), (0.73, 0.92)], 0.07)
    ic.line([(0.38, 0.8), (0.62, 0.8)], 0.06, caps=False)
    return ic


def _satellite(ic):
    body = [(0.56, 0.1), (0.92, 0.46), (0.74, 0.64), (0.38, 0.28)]
    ic.poly(body)
    ic.line([(0.47, 0.37), (0.83, 0.73)], 0.04, cut=True, caps=False)
    ic.line([(0.65, 0.19), (0.47, 0.37)], 0.04, cut=True, caps=False)
    ic.line([(0.83, 0.37), (0.65, 0.55)], 0.04, cut=True, caps=False)
    ic.poly([(0.47, 0.37), (0.65, 0.55), (0.52, 0.68), (0.34, 0.5)])
    ic.arc(0.36, 0.66, 0.2, 0.07, 90, 180)
    ic.arc(0.36, 0.66, 0.32, 0.07, 90, 180)


def i_gnss():
    ic = Icon()
    _satellite(ic)
    return ic


def i_gnss_off():
    ic = Icon()
    _satellite(ic)
    ic.line([(0.1, 0.12), (0.9, 0.92)], 0.14, cut=True)
    ic.line([(0.12, 0.14), (0.88, 0.9)], 0.07)
    return ic


def _camera_badge(ic, label):
    ic.rect(0.04, 0.27, 0.96, 0.86, 0.06)
    ic.rect(0.36, 0.13, 0.66, 0.3, 0.03)
    ic.rect(0.1, 0.19, 0.2, 0.3, 0.01)
    ic.text(label, 0.5, 0.57, 0.36, cut=True, sx=0.85 if len(label) > 3 else 1.0)


def i_auto():
    ic = Icon()
    _camera_badge(ic, 'AUTO')
    return ic


def i_pro():
    ic = Icon()
    _camera_badge(ic, 'PRO')
    return ic


def i_eye():
    ic = Icon()
    up = bezier((0.08, 0.5), (0.28, 0.16), (0.72, 0.16), (0.92, 0.5))
    lo = bezier((0.92, 0.5), (0.72, 0.84), (0.28, 0.84), (0.08, 0.5))
    ic.poly(up + lo)
    inner_up = bezier((0.18, 0.5), (0.34, 0.27), (0.66, 0.27), (0.82, 0.5))
    inner_lo = bezier((0.82, 0.5), (0.66, 0.73), (0.34, 0.73), (0.18, 0.5))
    ic.poly(inner_up + inner_lo, cut=True)
    ic.circle(0.5, 0.5, 0.17)
    ic.circle(0.5, 0.5, 0.07, cut=True)
    return ic


def i_orbit():
    from PIL import ImageFilter
    ic = Icon()
    ic.ring(0.5, 0.42, 0.34, 0.065)
    ic.circle(0.5, 0.42, 0.07)
    g = Icon()
    nav_arrow(g, 0.5, 0.74, 0.17)
    ic.mask = ImageChops.subtract(ic.mask, g.mask.filter(ImageFilter.MaxFilter(25)))
    nav_arrow(ic, 0.5, 0.74, 0.17)
    return ic


def i_list():
    ic = Icon()
    ic.rect(0.2, 0.1, 0.8, 0.9, 0.06)
    for y in (0.3, 0.46, 0.62):
        ic.rect(0.32, y, 0.68, y + 0.07, cut=True)
    ic.rect(0.32, 0.62, 0.52, 0.69)
    ic.rect(0.32, 0.78, 0.5, 0.85, cut=True)
    return ic


def i_plus():
    ic = Icon()
    ic.line([(0.5, 0.2), (0.5, 0.8)], 0.07)
    ic.line([(0.2, 0.5), (0.8, 0.5)], 0.07)
    return ic


def i_chev(direction):
    ic = Icon()
    pts = {'up': [(0.25, 0.62), (0.5, 0.37), (0.75, 0.62)],
           'down': [(0.25, 0.38), (0.5, 0.63), (0.75, 0.38)],
           'left': [(0.62, 0.22), (0.36, 0.5), (0.62, 0.78)],
           'right': [(0.38, 0.22), (0.64, 0.5), (0.38, 0.78)]}[direction]
    ic.line(pts, 0.075)
    return ic


def i_dots():
    ic = Icon()
    for x in (0.22, 0.5, 0.78):
        ic.circle(x, 0.5, 0.08)
    return ic


def i_aeb():
    ic = Icon()
    ic.rect_stroke(0.3, 0.18, 0.88, 0.62, 0.05, 0.03)
    ic.rect(0.12, 0.34, 0.72, 0.82, 0.03)
    ic.text('AEB', 0.42, 0.58, 0.2, paths=BOLD, cut=True)
    return ic


def i_burst():
    ic = Icon()
    ic.rect_stroke(0.3, 0.16, 0.88, 0.62, 0.06, 0.04)
    ic.rect(0.08, 0.32, 0.74, 0.82, 0.04, cut=True)
    ic.rect(0.12, 0.36, 0.7, 0.8, 0.04)
    return ic


def i_timer():
    ic = Icon()
    ic.ring(0.5, 0.56, 0.33, 0.07)
    ic.line([(0.5, 0.56), (0.62, 0.68)], 0.07)
    ic.line([(0.12, 0.24), (0.26, 0.12)], 0.08)
    ic.line([(0.88, 0.24), (0.74, 0.12)], 0.08)
    return ic


def i_camera():
    ic = Icon()
    ic.rect(0.08, 0.3, 0.92, 0.82, 0.08)
    ic.rect(0.32, 0.2, 0.62, 0.34, 0.03)
    ic.circle(0.5, 0.56, 0.17, cut=True)
    ic.circle(0.5, 0.56, 0.11)
    return ic


def i_video():
    ic = Icon()
    ic.rect(0.06, 0.26, 0.68, 0.74, 0.05)
    ic.poly([(0.7, 0.44), (0.94, 0.28), (0.94, 0.72), (0.7, 0.56)])
    return ic


def i_master():
    ic = Icon()
    ic.rect(0.1, 0.36, 0.9, 0.86, 0.03)
    ic.rect(0.1, 0.14, 0.9, 0.3, 0.02)
    for i in range(4):
        x = 0.2 + i * 0.19
        ic.poly([(x, 0.14), (x + 0.08, 0.14), (x + 0.02, 0.3), (x - 0.06, 0.3)], cut=True)
    star = []
    for k in range(10):
        r = 0.16 if k % 2 == 0 else 0.07
        a = math.radians(-90 + k * 36)
        star.append((0.5 + r * math.cos(a), 0.62 + r * math.sin(a)))
    ic.poly(star, cut=True)
    return ic


def i_aperture():
    ic = Icon()
    ic.ring(0.5, 0.5, 0.38, 0.065)
    for k in range(6):
        a = math.radians(k * 60 + 15)
        x0, y0 = 0.5 + 0.34 * math.cos(a), 0.5 + 0.34 * math.sin(a)
        b = a + math.radians(110)
        x1, y1 = 0.5 + 0.3 * math.cos(b), 0.5 + 0.3 * math.sin(b)
        ic.line([(x0, y0), (x0 + (x1 - x0) * 0.85, y0 + (y1 - y0) * 0.85)], 0.055)
    return ic


def _film_frame(ic):
    ic.rect(0.08, 0.2, 0.92, 0.8, 0.03)
    n = 7
    for row_y in (0.23, 0.7):
        for i in range(n):
            x0 = 0.13 + i * (0.74 / n)
            ic.rect(x0, row_y, x0 + 0.06, row_y + 0.06, cut=True)
    ic.rect(0.08, 0.32, 0.92, 0.68, cut=True)
    ic.rect(0.12, 0.34, 0.88, 0.66)


def i_night():
    ic = Icon()
    _film_frame(ic)
    ic.circle(0.5, 0.5, 0.13, cut=True)
    ic.circle(0.56, 0.45, 0.11)
    ic.circle(0.6, 0.37, 0.02, cut=True)
    return ic


def i_slomo():
    ic = Icon()
    _film_frame(ic)
    ic.text('SLO', 0.5, 0.5, 0.22, paths=BOLD, cut=True)
    return ic


def i_locate():
    ic = Icon()
    ic.ring(0.5, 0.5, 0.4, 0.06)
    nav_arrow(ic, 0.5, 0.48, 0.2)
    return ic


def i_corner():
    ic = Icon()
    ic.line([(0.22, 0.16), (0.22, 0.8), (0.86, 0.8)], 0.1, caps=False)
    return ic


def i_expand():
    ic = Icon()
    ic.line([(0.56, 0.44), (0.82, 0.18)], 0.07)
    ic.line([(0.6, 0.18), (0.82, 0.18), (0.82, 0.4)], 0.07)
    ic.line([(0.44, 0.56), (0.18, 0.82)], 0.07)
    ic.line([(0.18, 0.6), (0.18, 0.82), (0.4, 0.82)], 0.07)
    return ic


def i_nav():
    ic = Icon()
    nav_arrow(ic, 0.5, 0.48, 0.4)
    return ic


def i_close():
    ic = Icon()
    ic.line([(0.25, 0.25), (0.75, 0.75)], 0.08)
    ic.line([(0.75, 0.25), (0.25, 0.75)], 0.08)
    return ic


def i_check():
    ic = Icon()
    ic.line([(0.2, 0.52), (0.42, 0.74), (0.82, 0.28)], 0.09)
    return ic


def i_trash():
    ic = Icon()
    ic.rect(0.16, 0.2, 0.84, 0.28, 0.02)
    ic.rect(0.38, 0.1, 0.62, 0.22, 0.02)
    ic.poly([(0.24, 0.32), (0.76, 0.32), (0.71, 0.9), (0.29, 0.9)])
    for x in (0.4, 0.6):
        ic.rect(x - 0.025, 0.42, x + 0.025, 0.8, cut=True)
    return ic


def i_warn():
    ic = Icon()
    ic.poly([(0.5, 0.08), (0.95, 0.88), (0.05, 0.88)])
    ic.rect(0.46, 0.34, 0.54, 0.64, cut=True)
    ic.circle(0.5, 0.75, 0.05, cut=True)
    return ic


def i_home_marker():
    ic = Icon()
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0.06 * S, 0.06 * S, 0.94 * S, 0.94 * S], fill=(255, 255, 255, 255))
    d.ellipse([0.13 * S, 0.13 * S, 0.87 * S, 0.87 * S], fill=(255, 196, 0, 255))
    f = font(BOLD, int(0.56 * S))
    bbox = d.textbbox((0, 0), 'H', font=f)
    d.text((0.5 * S - (bbox[0] + bbox[2]) / 2, 0.5 * S - (bbox[1] + bbox[3]) / 2), 'H', font=f, fill=(30, 30, 30, 255))
    ic.color = img
    return ic


def i_drop():
    ic = Icon()
    ic.circle(0.5, 0.6, 0.26)
    ic.poly([(0.5, 0.08), (0.74, 0.5), (0.26, 0.5)])
    return ic


def i_plane():
    ic = Icon()
    ic.poly([(0.06, 0.52), (0.94, 0.5), (0.3, 0.36), (0.42, 0.5), (0.3, 0.64)])
    return ic


def i_trees():
    ic = Icon()
    ic.circle(0.36, 0.3, 0.18)
    ic.circle(0.3, 0.48, 0.2)
    ic.circle(0.44, 0.55, 0.16)
    ic.rect(0.33, 0.6, 0.38, 0.95)
    ic.poly([(0.66, 0.1), (0.9, 0.72), (0.44, 0.72)])
    ic.rect(0.64, 0.6, 0.69, 0.95)
    return ic


def i_disc():
    ic = Icon()
    ic.circle(0.5, 0.5, 0.49)
    return ic


def i_shutter_dot():
    ic = Icon()
    ic.circle(0.5, 0.5, 0.2)
    return ic


def i_battery():
    ic = Icon()
    ic.rect_stroke(0.08, 0.28, 0.84, 0.72, 0.06, 0.06)
    ic.rect(0.86, 0.42, 0.93, 0.58, 0.02)
    ic.rect(0.16, 0.36, 0.6, 0.64, 0.02)
    return ic


def i_stop():
    ic = Icon()
    ic.ring(0.5, 0.5, 0.42, 0.08)
    ic.rect(0.34, 0.34, 0.66, 0.66, 0.04)
    return ic


def i_gallery():
    ic = Icon()
    ic.rect_stroke(0.1, 0.18, 0.9, 0.82, 0.07, 0.06)
    ic.poly([(0.2, 0.72), (0.42, 0.44), (0.58, 0.62), (0.68, 0.52), (0.82, 0.72)])
    ic.circle(0.68, 0.34, 0.07)
    return ic


ICONS = [
    i_home, i_back, i_waypoint, i_focus, i_takeoff, i_land, i_rth, i_film,               # 0-7
    i_photo_frame, i_play, i_map_app, i_att_mini, i_assist_mini, i_alt, i_dist, i_obst_omni,  # 8-15
    i_obst_updown, i_obst_fwd, i_gnss, i_gnss_off, i_auto, i_pro, i_eye, i_orbit,        # 16-23
    i_list, i_plus, lambda: i_chev('up'), i_dots, i_aeb, i_burst, i_timer, i_camera,     # 24-31
    i_video, i_master, i_aperture, i_night, i_slomo, i_locate, i_corner, i_expand,       # 32-39
    i_nav, i_close, i_check, i_trash, i_warn, lambda: i_chev('left'), lambda: i_chev('right'),
    lambda: i_chev('down'),                                                                # 40-47
    i_home_marker, i_drop, i_plane, i_trees, i_disc, i_target_icon, i_battery, i_stop,   # 48-55
    i_gallery,                                                                             # 56
]
NAMES = ['home', 'back', 'waypoint', 'focus', 'takeoff', 'land', 'rth', 'film',
         'photo_frame', 'play', 'map_app', 'att_mini', 'assist_mini', 'alt', 'dist', 'obst_omni',
         'obst_updown', 'obst_fwd', 'gnss', 'gnss_off', 'auto', 'pro', 'eye', 'orbit',
         'list', 'plus', 'chev_up', 'dots', 'aeb', 'burst', 'timer', 'camera',
         'video', 'master', 'aperture', 'night', 'slomo', 'locate', 'corner', 'expand',
         'nav', 'close', 'check', 'trash', 'warn', 'chev_left', 'chev_right', 'chev_down',
         'home_marker', 'drop', 'plane', 'trees', 'disc', 'target', 'battery', 'stop', 'gallery']


def main():
    masters = [fn().image() for fn in ICONS]
    for cell in (128, 64, 32):
        atlas = Image.new('RGBA', (cell * 8, cell * 8), (255, 255, 255, 0))
        for i, img in enumerate(masters):
            small = img.resize((cell, cell), Image.LANCZOS)
            atlas.paste(small, ((i % 8) * cell, (i // 8) * cell))
        # keep transparent texels white so bilinear sampling never darkens tinted edges
        r, g, b, a = atlas.split()
        white = Image.new('L', atlas.size, 255)
        mask = a.point(lambda v: 255 if v == 0 else 0)
        atlas = Image.merge('RGBA', (Image.composite(white, r, mask), Image.composite(white, g, mask),
                                     Image.composite(white, b, mask), a))
        atlas.save(os.path.join(OUT, f'dji_fly_icons_{cell}.png'))
    print(len(masters), 'icons')
    with open(os.path.join(ROOT, 'tools', 'dji_fly_icons_index.txt'), 'w', encoding='utf-8') as fh:
        for i, n in enumerate(NAMES):
            fh.write(f'{i} {n}\n')


if __name__ == '__main__':
    main()
