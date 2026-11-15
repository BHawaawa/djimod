# -*- coding: utf-8 -*-
"""Procedural generator: DJI Avata 360 -> Blockbench (GeckoLib) model + texture.
Coordinates: Blockbench units (1/16 block). Front of drone = north (-Z). Y up.
"""
import json, math, uuid, base64, io, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else '/sessions/confident-focused-lamport/mnt/outputs/build'
os.makedirs(OUT, exist_ok=True)
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
RNG = np.random.default_rng(360)
DSCALE = 1.5
SS = 4  # supersampling for texture painting
MODEL = 'avata360'


def uid():
    return str(uuid.uuid4())


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


# ----------------------------------------------------------------------------- materials
MATS = {
    'shell':       (146, 148, 150),
    'shell_in':    (118, 120, 122),
    'shell_light': (170, 171, 172),
    'shell_dark':  (100, 101, 103),
    'rim':         (156, 158, 160),
    'cam':         (36, 37, 39),
    'cam2':        (26, 27, 29),
    'housing':     (20, 20, 22),
    'glass':       (30, 52, 38),
    'motor':       (34, 34, 36),
    'bell':        (42, 42, 44),
    'prop':        (27, 27, 29),
    'rubber':      (58, 58, 60),
    'led':         (90, 235, 100),
    'clear':       None,
}

# ----------------------------------------------------------------------------- scene
BONES = {}   # name -> dict
ORDER = []


def bone(name, pivot, parent=None):
    BONES[name] = dict(name=name, pivot=list(pivot), parent=parent, cubes=[], uuid=uid())
    ORDER.append(name)
    return name


CUBES = []


def box(b, name, frm, to, mat, D=6, origin=None, rot=None, decals=None, inner_face=None, ao=True):
    c = dict(bone=b, name=name, frm=[float(v) for v in frm], to=[float(v) for v in to], mat=mat, D=D,
             origin=list(origin) if origin is not None else [float(v) for v in frm],
             rot=[float(v) for v in rot] if rot is not None else [0.0, 0.0, 0.0],
             decals=decals or {}, inner_face=inner_face, ao=ao, uuid=uid())
    CUBES.append(c)
    BONES[b]['cubes'].append(c)
    return c


def obox(b, name, center, size, rot, mat, **kw):
    center = np.array(center, float); size = np.array(size, float)
    return box(b, name, center - size / 2, center + size / 2, mat, origin=center, rot=rot, **kw)


def norm_ang(a):
    a = (a + 180.0) % 360.0 - 180.0
    return round(a, 4)


def disc(b, name, center, radius, y0, y1, mat, axis='y', n=None, decals=None, D=8, sides=None):
    """Regular polygon prism (apothem = radius) built as the union of sides/2 bars of
    size (2*apothem x side_len), each rotated by 360/sides. Exact polygon, no spikes."""
    if sides is None:
        sides = 12 if radius > 1.05 else 8
    nb = sides // 2
    seg = 2 * radius * math.tan(math.pi / sides) + 0.01
    cs = []
    for k in range(nb):
        a = norm_ang(k * 360.0 / sides)
        e = 0.004 * k   # tiny stagger to avoid coplanar z-fighting in game
        if axis == 'y':
            cen = [center[0], (y0 + y1 + e) / 2, center[2]]
            size = [2 * radius, y1 - y0 + e, seg]
            rot = [0, a, 0]
        else:
            cen = [center[0], center[1], (y0 + y1 - e) / 2]
            size = [2 * radius, seg, y1 - y0 + e]
            rot = [0, 0, a]
        cs.append(obox(b, f'{name}_{k}', cen, size, rot, mat, decals=decals, D=D, ao=False))
    return cs


# ============================================================================ DECALS
class F:
    """Face painting context. Arrays at supersampled resolution."""

    def __init__(self, img, P, S, T, L, fw, fh, k, face):
        self.img, self.P, self.S, self.T, self.L = img, P, S, T, L
        self.fw, self.fh, self.k, self.face = fw, fh, k, face

    def paint(self, mask, color, a=1.0):
        col = np.array(color[:3], float)
        alpha = color[3] / 255.0 if len(color) > 3 else 1.0
        m = mask[..., None].astype(float) * a
        self.img[..., :3] = self.img[..., :3] * (1 - m) + col * m
        self.img[..., 3:] = self.img[..., 3:] * (1 - m) + alpha * 255 * m

    def st_rect(self, s0, t0, s1, t1):
        return (self.S >= s0) & (self.S <= s1) & (self.T >= t0) & (self.T <= t1)

    def st_rrect(self, s0, t0, s1, t1, r):
        cs = np.clip(self.S, s0 + r, s1 - r); ct = np.clip(self.T, t0 + r, t1 - r)
        return (self.S - cs) ** 2 + (self.T - ct) ** 2 <= r * r

    def st_circle(self, cs, ct, r):
        return (self.S - cs) ** 2 + (self.T - ct) ** 2 <= r * r

    def text(self, txt, cs, ct, h, color, rot=0, font=FONT_B, stretch=1.0):
        H, W = self.img.shape[:2]
        px = max(4, int(h * self.k * 1.35))
        fnt = ImageFont.truetype(font, px)
        bb = fnt.getbbox(txt)
        tw, th = bb[2] - bb[0] + 4, bb[3] - bb[1] + 4
        tim = Image.new('L', (tw, th), 0)
        ImageDraw.Draw(tim).text((2 - bb[0], 2 - bb[1]), txt, font=fnt, fill=255)
        if stretch != 1.0:
            tim = tim.resize((max(1, int(tw * stretch)), th), Image.LANCZOS)
        if rot:
            tim = tim.rotate(rot, expand=True, resample=Image.BICUBIC)
        # scale so that glyph height == h units
        scale = (h * self.k) / (th - 4) if not rot or rot % 180 == 0 else (h * self.k) / (tim.size[0] - 4)
        tim = tim.resize((max(1, int(tim.size[0] * scale)), max(1, int(tim.size[1] * scale))), Image.LANCZOS)
        layer = Image.new('L', (W, H), 0)
        layer.paste(tim, (int(cs * self.k - tim.size[0] / 2), int(ct * self.k - tim.size[1] / 2)))
        self.paint(np.asarray(layer, float) / 255.0, color)


# ---- decal functions -------------------------------------------------------
def d_top_cover(f):
    # dji logo near rear, seam lines
    f.paint(f.st_rect(0, 0, f.fw, 0.05), (110, 112, 114))
    # subtle panel line
    f.paint(f.st_rect(0.25, 1.0, 0.3, f.fh - 1.0), (128, 130, 132))
    f.paint(f.st_rect(f.fw - 0.3, 1.0, f.fw - 0.25, f.fh - 1.0), (128, 130, 132))


def d_battery_end(f):
    # rear face: power button (rounded) + 4 battery LEDs
    f.paint(f.st_rrect(0.45, 1.25, 1.75, 2.05, 0.3), (120, 121, 123))
    f.paint(f.st_rrect(0.55, 1.35, 1.65, 1.95, 0.25), (176, 177, 178))
    for i in range(4):
        s0 = 2.15 + i * 0.42
        col = (70, 225, 85) if i < 3 else (95, 97, 99)
        f.paint(f.st_rect(s0, 1.5, s0 + 0.26, 1.8), col)
    f.paint(f.st_rect(0, 0, f.fw, 0.06), (140, 141, 142))


def d_battery_cap(f):
    # release grooves
    for i in range(5):
        t = 0.4 + i * 0.22
        f.paint(f.st_rect(0.6, t, f.fw - 0.6, t + 0.08), (150, 151, 152))


def d_bottom(f):
    # bottom shell: vents, label, oval IR sensor, screws.  s along +X, t along -Z (from rear)
    # t = 0 at z = to.z (rear) ; z = 9.6 - t
    def zt(z):
        return 9.6 - z
    for blk, (z0, z1) in enumerate([(-1.0, 2.0), (3.0, 5.0)]):
        for i in range(9):
            s = 0.55 + i * 0.35
            f.paint(f.st_rect(s, zt(z1), s + 0.18, zt(z0)), (40, 41, 43))
    # label box
    f.paint(f.st_rect(0.45, zt(-2.2), 3.75, zt(-2.25)), (125, 126, 128))
    f.paint(f.st_rect(0.45, zt(-4.0), 3.75, zt(-3.95)), (125, 126, 128))
    f.text('81dB', 1.2, zt(-3.1), 0.55, (125, 126, 128), font=FONT_C)
    f.text('CE', 2.95, zt(-3.1), 0.55, (125, 126, 128), font=FONT_C)
    # oval downward sensor
    f.paint(f.st_rrect(1.35, zt(7.2), 2.75, zt(6.4), 0.38), (22, 22, 24))
    f.paint(f.st_rect(2.03, zt(7.25), 2.07, zt(6.35)), (100, 101, 103))
    f.paint(f.st_circle(1.75, zt(6.8), 0.18), (40, 60, 48))
    # screws
    for s, z in [(0.3, -3.9), (3.9, -3.9), (0.3, 8.9), (3.9, 8.9), (0.3, 2.5), (3.9, 2.5)]:
        f.paint(f.st_circle(s, zt(z), 0.12), (45, 45, 47))


def d_spine_side(f):
    # side small vents + mic hole (visible through the gap between ducts)
    for i in range(3):
        f.paint(f.st_rrect(f.fw * 0.27 + i * 0.35, 1.2, f.fw * 0.27 + i * 0.35 + 0.15, 2.4, 0.07), (60, 61, 63))


def d_cam_side(f):
    f.text('AVATA 360', f.fw / 2, 3.6, 0.42, (120, 121, 124), font=FONT_C)
    f.paint(f.st_rect(0, 0.0, f.fw, 0.06), (55, 56, 58))
    f.paint(f.st_rect(0, 4.2, f.fw, 4.25), (20, 20, 22))


def d_cam_top(f):
    # ridged top cap + rotated label
    for i in range(9):
        t = 0.35 + i * 0.6
        f.paint(f.st_rect(0.25, t, f.fw - 0.9, t + 0.14), (22, 23, 24))
    f.text('AVATA 360', f.fw - 0.17, f.fh / 2, 0.26, (150, 151, 154), rot=-90, font=FONT_C)


def d_cam_front(f):
    f.paint(f.st_rect(0, 0, f.fw, 0.08), (55, 56, 58))
    f.paint(f.st_rrect(0.25, 0.35, f.fw - 0.25, 1.05, 0.2), (20, 20, 22))   # vent slot above lens


def lens_front_world(cx, cy, r_glass, face_axis='z'):
    def fn(f):
        dx = f.P[..., 0] - cx; dy = f.P[..., 1] - cy
        r = np.sqrt(dx * dx + dy * dy)
        ang = np.arctan2(dy, dx)
        # outer knurled ring
        knurl = (np.sin(ang * 48) > 0) & (r > r_glass + 0.16) & (r < r_glass + 0.3)
        f.paint((r > r_glass + 0.08), (24, 24, 26))
        f.paint(knurl, (40, 40, 43))
        f.paint((r > r_glass + 0.3), (16, 16, 17))
        f.paint((r > r_glass) & (r <= r_glass + 0.08), (120, 122, 124))
    return fn


def glass_world(cx, cy, R, tint=(34, 62, 44), silver=False):
    def fn(f):
        dx = f.P[..., 0] - cx; dy = f.P[..., 1] - cy
        r = np.sqrt(dx * dx + dy * dy) / R
        base = np.array(tint, float)
        img = f.img
        g = np.clip(1.0 - r, 0, 1)
        col = base[None, None, :] * (0.55 + 0.6 * g[..., None])
        if silver:
            col = col * 0.5 + np.array([150, 155, 160])[None, None, :] * (0.5 * np.clip(1 - r[..., None], 0, 1) ** 0.5)
        img[..., :3] = col
        img[..., 3] = 255
        f.paint((r > 0.93), (10, 10, 11))
        f.paint((r > 0.62) & (r < 0.68), (70, 100, 80))
        f.paint((r < 0.3) & (r > 0.24), (60, 90, 70))
        # specular highlights
        f.paint(((dx + 0.35 * R) ** 2 + (dy - 0.35 * R) ** 2) < (0.16 * R) ** 2, (215, 235, 220))
        f.paint(((dx - 0.3 * R) ** 2 + (dy + 0.4 * R) ** 2) < (0.07 * R) ** 2, (150, 180, 160))
        f.paint(r < 0.12, (8, 14, 10))
    return fn


def d_sensor_front(sx):
    # front face of outer shoulder block: rectangular LiDAR window, round vision sensor, tiny dot
    def fn(f):
        X = f.P[..., 0] * sx; Y = f.P[..., 1]
        win = (X > 5.05) & (X < 6.35) & (Y > 2.15) & (Y < 2.95)
        f.paint(win, (14, 14, 16))
        f.paint(win & (X > 5.15) & (X < 5.5) & (Y > 2.6), (60, 62, 66))
        rr = (X - 5.5) ** 2 + (Y - 1.25) ** 2
        f.paint(rr < 0.5 ** 2, (20, 20, 22))
        f.paint(rr < 0.36 ** 2, (32, 58, 44))
        f.paint(((X - 5.4) ** 2 + (Y - 1.36) ** 2) < 0.1 ** 2, (190, 220, 200))
        f.paint(((X - 6.9) ** 2 + (Y - 2.5) ** 2) < 0.12 ** 2, (40, 40, 42))
        f.paint((Y > 3.32), (175, 177, 179))
    return fn


def d_sensor_down(sx):
    def fn(f):
        X = f.P[..., 0] * sx; Z = f.P[..., 2]
        rr = (X - 6.0) ** 2 + (Z + 11.55) ** 2
        f.paint(rr < 0.48 ** 2, (20, 20, 22))
        f.paint(rr < 0.33 ** 2, (40, 75, 55))
        f.paint(((X - 5.92) ** 2 + (Z + 11.47) ** 2) < 0.09 ** 2, (180, 210, 190))
    return fn


def d_mount_bottom(f):
    # motor mount: 3 screw holes, measured from bone pivot in f.P via closure set later
    pass


def mount_world(cx, cz):
    def fn(f):
        dx = f.P[..., 0] - cx; dz = f.P[..., 2] - cz
        r = np.sqrt(dx * dx + dz * dz)
        f.paint(r < 0.95, (110, 111, 113))
        for k in range(3):
            a = math.radians(90 + 120 * k)
            hx, hz = 0.48 * math.cos(a), 0.48 * math.sin(a)
            f.paint(((dx - hx) ** 2 + (dz - hz) ** 2) < 0.16 ** 2, (25, 25, 27))
        f.paint(r < 0.16, (60, 60, 62))
    return fn


def bell_side(f):
    # copper windings visible through bell slots
    for i in range(6):
        s = 0.12 + i * (f.fw - 0.24) / 6
        f.paint(f.st_rect(s, 0.3, s + 0.12, f.fh - 0.35), (150, 92, 45))
        f.paint(f.st_rect(s + 0.02, 0.3, s + 0.05, f.fh - 0.35), (200, 130, 70))
    f.paint(f.st_rect(0, 0, f.fw, 0.12), (60, 60, 62))


def bell_top_world(cx, cz, orange_arcs):
    def fn(f):
        dx = f.P[..., 0] - cx; dz = f.P[..., 2] - cz
        r = np.sqrt(dx * dx + dz * dz)
        ang = np.degrees(np.arctan2(dz, dx)) % 360
        f.paint((r > 0.38) & (r < 0.62), (110, 70, 38))
        spokes = (np.abs(((ang + 15) % 60) - 30) < 9)
        f.paint((r > 0.38) & (r < 0.62) & spokes, (30, 30, 32))
        ring = (r > 0.64) & (r < 0.78)
        if orange_arcs:
            sel = ((ang > 20) & (ang < 110)) | ((ang > 200) & (ang < 290))
        else:
            sel = (np.abs(((ang) % 120) - 60) < 9)
        f.paint(ring & sel, (238, 140, 30))
    return fn


def hub_top_world(cx, cz):
    def fn(f):
        dx = f.P[..., 0] - cx; dz = f.P[..., 2] - cz
        r = np.sqrt(dx * dx + dz * dz)
        f.paint(r < 0.3, (70, 70, 72))
        f.paint(r < 0.18, (120, 121, 123))
        f.paint(r < 0.07, (30, 30, 30))
    return fn


def blade_shape(sweep):
    def fn(f):
        L = f.L  # cube-local coords relative to cube center
        lx, lz = L[..., 0], L[..., 2]
        u = (lx + BL / 2) / BL
        uc = np.clip(u, 0, 1)
        zc = sweep * (1.9 * uc ** 2.0 - 0.55)
        hw = (0.28 + 0.85 * uc ** 0.55) * np.sqrt(np.clip(1 - uc ** 5, 0, 1))
        m = (u >= 0) & (u <= 1) & (np.abs(lz - zc) < hw)
        # leading-edge highlight
        f.paint(m, (27, 27, 29, 255))
        edge = m & (np.abs(lz - zc) > hw - 0.09)
        f.paint(edge, (44, 44, 46, 255))
    return fn


def led_top(f):
    f.paint(f.st_rect(0.05, 0.05, f.fw - 0.05, f.fh - 0.05), (140, 255, 150))


# ============================================================================ GEOMETRY
bone('root', (0, 0, 0))
bone('drone', (0, 0, 0), 'root')
bone('body', (0, 2.5, 2), 'drone')
bone('guards', (0, 2, 0), 'drone')
bone('frame', (0, 1, 0), 'drone')
bone('camera', (0, 3.05, -8.8), 'drone')

RC = [(-8.5, -5.9, 'fl', 'cw'), (8.5, -5.9, 'fr', 'ccw'), (-8.5, 5.9, 'rl', 'ccw'), (8.5, 5.9, 'rr', 'cw')]

# ---- body
box('body', 'spine', (-2.3, 0.6, -4.6), (2.3, 4.6, 9.8), 'shell', D=8,
    decals={'east': d_spine_side, 'west': d_spine_side})
box('body', 'top_cover', (-2.0, 4.6, -4.3), (2.0, 4.95, 9.6), 'shell', D=10, decals={'up': d_top_cover})
box('body', 'top_ridge', (-1.55, 4.95, -3.9), (1.55, 5.02, 8.9), 'shell', D=10,
    decals={'up': lambda f: f.text('dji', f.fw / 2, 11.75, 1.0, (200, 202, 204))})
box('body', 'led', (-0.3, 5.02, -1.15), (0.3, 5.1, -0.8), 'led', D=10, decals={'up': led_top})
box('body', 'battery', (-2.45, 0.7, 9.8), (2.45, 4.5, 12.2), 'shell_light', D=8)
box('body', 'battery_cap', (-2.15, 4.5, 9.8), (2.15, 4.85, 11.95), 'shell_light', D=10, decals={'up': d_battery_cap})
box('body', 'battery_end', (-2.1, 1.0, 12.2), (2.1, 4.25, 12.5), 'shell_light', D=12, decals={'south': d_battery_end})
for sx in (1, -1):
    x0, x1 = sorted([sx * 2.45, sx * 2.72])
    box('body', f'battery_latch_{"r" if sx > 0 else "l"}', (x0, 1.9, 10.3), (x1, 3.5, 11.6), 'shell_dark', D=8)
box('body', 'bottom_shell', (-2.1, 0.3, -4.3), (2.1, 0.6, 9.6), 'shell_dark', D=10, decals={'down': d_bottom})

def chamfer(b, name, x, y, z0, z1, size, mat, D=6):
    obox(b, name, (x, y, (z0 + z1) / 2), (size, size, z1 - z0), [0, 0, 45], mat, D=D, ao=False)


for sx in (1, -1):
    chamfer('body', f'spine_bevel_{sx}', sx * 2.22, 4.52, -4.5, 9.75, 0.45, 'shell')
    chamfer('body', f'bat_bevel_{sx}', sx * 2.37, 4.42, 9.85, 12.15, 0.45, 'shell_light')
    chamfer('body', f'spine_bevel_lo_{sx}', sx * 2.2, 0.7, -4.5, 12.1, 0.4, 'shell_dark')
    chamfer('body', f'shoulder_bevel_{sx}', sx * 4.18, 4.58, -12.3, -4.7, 0.42, 'shell')

# ---- front shoulders (sensor housings)
for sx, side in ((1, 'r'), (-1, 'l')):
    x0, x1 = sorted([sx * 1.95, sx * 4.3])
    box('body', f'shoulder_{side}', (x0, 0.4, -12.4), (x1, 4.7, -4.6), 'shell', D=8)
    x0, x1 = sorted([sx * 2.15, sx * 4.1])
    box('body', f'shoulder_top_{side}', (x0, 4.7, -12.1), (x1, 4.88, -4.8), 'shell', D=6)
    x0, x1 = sorted([sx * 4.3, sx * 7.45])
    box('body', f'sensor_housing_{side}', (x0, 0.5, -12.5), (x1, 3.4, -10.7), 'shell', D=12,
        decals={'north': d_sensor_front(sx), 'down': d_sensor_down(sx)})
    # small chin under the housing
    x0, x1 = sorted([sx * 2.2, sx * 6.6])
    box('body', f'chin_{side}', (x0, 0.25, -12.1), (x1, 0.5, -10.4), 'shell_dark', D=6)

# ---- camera module (rotating 360 module)
box('camera', 'cam_body', (-1.9, 0.45, -12.0), (1.9, 5.7, -5.6), 'cam', D=10,
    decals={'east': d_cam_side, 'west': d_cam_side, 'north': d_cam_front})
box('camera', 'cam_top', (-1.6, 5.7, -11.6), (1.6, 6.05, -6.0), 'cam', D=12, decals={'up': d_cam_top})
for i, zc in enumerate([-10.6, -9.9, -9.2, -8.5, -7.8, -7.1]):
    box('camera', f'cam_fin_{i}', (-1.25, 6.05, zc - 0.14), (1.25, 6.3, zc + 0.14), 'cam2', D=8)
box('camera', 'cam_chin', (-1.6, 0.2, -11.7), (1.6, 0.45, -5.9), 'cam', D=6)
for sx in (1, -1):
    x0, x1 = sorted([sx * 1.9, sx * 2.0])
    box('camera', f'cam_axle_{"r" if sx > 0 else "l"}', (x0, 2.5, -9.4), (x1, 3.6, -8.2), 'housing', D=8)
LY = 3.05
disc('camera', 'lens_f_housing', (0, LY, 0), 1.5, -12.6, -12.0, 'housing', axis='z',
     decals={'north': lens_front_world(0, LY, 1.16)}, D=14)
disc('camera', 'lens_f_glass', (0, LY, 0), 1.16, -12.82, -12.6, 'glass', axis='z',
     decals={'north': glass_world(0, LY, 1.2)}, D=16)
disc('camera', 'lens_r_housing', (0, LY, 0), 1.3, -5.6, -5.3, 'housing', axis='z',
     decals={'south': lens_front_world(0, LY, 0.98)}, D=12)
disc('camera', 'lens_r_glass', (0, LY, 0), 0.98, -5.3, -5.12, 'glass', axis='z',
     decals={'south': glass_world(0, LY, 1.02, tint=(60, 70, 70), silver=True)}, D=14)

# ---- prop guards (ducts)
N = 24
SOLIDS = [((-2.3, -4.6), (2.3, 12.2)), ((1.95, -12.4), (4.3, -4.6)), ((-4.3, -12.4), (-1.95, -4.6))]


def inside_solid(pts):
    for (x0, z0), (x1, z1) in SOLIDS:
        if all(x0 <= p[0] <= x1 and z0 <= p[1] <= z1 for p in pts):
            return True
    return False


for cx, cz, tag, _ in RC:
    for k in range(N):
        phi = 2 * math.pi * (k + 0.5) / N
        yaw = norm_ang(-(math.degrees(phi) + 90))
        tx, tz = -math.sin(phi), math.cos(phi)
        for part, (rin, rout, y0, y1, mat_) in {
            'wall': (5.85, 6.55, 0.9, 3.0, 'shell'),
            'rim': (5.45, 6.62, 3.0, 3.45, 'rim'),
            'lip': (6.5, 6.8, 1.15, 2.25, 'shell'),
        }.items():
            L = 2 * rout * math.tan(math.pi / N) + 0.04
            rc = (rin + rout) / 2
            px, pz = cx + rc * math.cos(phi), cz + rc * math.sin(phi)
            corners = []
            for a in (-1, 1):
                for rr in (rin, rout):
                    corners.append((cx + rr * math.cos(phi) + a * tx * L / 2, cz + rr * math.sin(phi) + a * tz * L / 2))
            if inside_solid(corners):
                continue
            # local +Z faces inward (south face = inner duct surface)
            obox('guards', f'duct_{tag}_{part}_{k}', (px, (y0 + y1) / 2, pz), (L, y1 - y0, rout - rin),
                 [0, yaw, 0], mat_, D=6, inner_face='south', ao=False)
    # landing foot under the outer side of each duct
    ox, oz = (1 if cx > 0 else -1), (1 if cz > 0 else -1)
    a = math.atan2(oz * 0.75, ox)
    fx, fz = cx + 6.15 * math.cos(a), cz + 6.15 * math.sin(a)
    obox('guards', f'foot_{tag}', (fx, 0.65, fz), (1.1, 0.55, 0.7), [0, norm_ang(-(math.degrees(a) + 90)), 0], 'rubber', D=6)

# ---- arms, spokes, motors, props
BL = 4.85   # blade length
for cx, cz, tag, spin in RC:
    sx, sz = (1 if cx > 0 else -1), (1 if cz > 0 else -1)
    B = np.array([sx * 2.1, sz * 2.9]); M = np.array([cx, cz])
    d = M - B; Lr = float(np.linalg.norm(d)); dn = d / Lr
    yaw = norm_ang(math.degrees(math.atan2(-dn[1], dn[0])))
    ctr = (B + M) / 2
    obox('frame', f'arm_{tag}', (ctr[0], 1.15, ctr[1]), (Lr + 0.6, 0.55, 0.95), [0, yaw, 0], 'shell_dark', D=6)
    back = math.atan2(-dn[1], -dn[0])
    for j, off in enumerate((120, -120)):
        a = back + math.radians(off)
        e = np.array([math.cos(a), math.sin(a)])
        c2 = M + e * (0.9 + 5.95) / 2
        obox('frame', f'spoke_{tag}_{j}', (c2[0], 1.05, c2[1]), (5.05, 0.32, 0.3),
             [0, norm_ang(math.degrees(math.atan2(-e[1], e[0]))), 0], 'shell_dark', D=4)
    mb = bone(f'motor_{tag}', (cx, 1.0, cz), 'drone')
    disc(mb, f'mount_{tag}', (cx, 0, cz), 1.0, 0.9, 1.35, 'shell_dark', decals={'down': mount_world(cx, cz)}, D=10)
    disc(mb, f'bell_{tag}', (cx, 0, cz), 0.85, 1.35, 2.55, 'bell', decals={'north': bell_side, 'south': bell_side,
                                                                          'east': bell_side, 'west': bell_side}, D=12)
    disc(mb, f'bell_top_{tag}', (cx, 0, cz), 0.8, 2.55, 2.72, 'motor',
         decals={'up': bell_top_world(cx, cz, spin == 'ccw')}, D=14)
    pb = bone(f'prop_{tag}', (cx, 2.9, cz), mb)
    disc(pb, f'hub_{tag}', (cx, 0, cz), 0.58, 2.72, 3.08, 'prop', decals={'up': hub_top_world(cx, cz)}, D=14)
    sweep = 1.0 if spin == 'cw' else -1.0
    pitch = 16.0 * sweep
    base = 18.0 if tag in ('fl', 'rr') else 54.0
    for kb in range(5):
        yb = norm_ang(base + 72 * kb)
        R = rotmat([pitch, yb, 0])
        cen = np.array([cx, 2.9, cz]) + R @ np.array([0.45 + BL / 2, 0, 0])
        obox(pb, f'blade_{tag}_{kb}', cen, (BL, 0.08, 4.2), [pitch, yb, 0], 'clear', D=14,
             decals={'up': blade_shape(sweep), 'down': blade_shape(sweep)}, ao=False)

print('cubes:', len(CUBES))

# ============================================================================ TEXTURE BAKING
FACES = ['north', 'east', 'south', 'west', 'up', 'down']


def face_dims(c, face):
    dx, dy, dz = [c['to'][i] - c['frm'][i] for i in range(3)]
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy),
            'up': (dx, dz), 'down': (dx, dz)}[face]


def face_local(c, face, S, T):
    f, t = c['frm'], c['to']
    one = np.ones_like(S)
    if face == 'north': X, Y, Z = t[0] - S, t[1] - T, f[2] * one
    elif face == 'south': X, Y, Z = f[0] + S, t[1] - T, t[2] * one
    elif face == 'east': X, Y, Z = t[0] * one, t[1] - T, t[2] - S
    elif face == 'west': X, Y, Z = f[0] * one, t[1] - T, f[2] + S
    elif face == 'up': X, Y, Z = f[0] + S, t[1] * one, f[2] + T
    else: X, Y, Z = f[0] + S, f[1] * one, t[2] - T
    return np.stack([X, Y, Z], -1)


def bake_face(c, face):
    fw, fh = face_dims(c, face)
    D = c['D'] * DSCALE
    pw, ph = max(1, int(math.ceil(fw * D - 1e-6))), max(1, int(math.ceil(fh * D - 1e-6)))
    W, H = pw * SS, ph * SS
    kx, ky = W / fw if fw > 0 else 1, H / fh if fh > 0 else 1
    S = (np.arange(W) + 0.5)[None, :].repeat(H, 0) / kx
    T = (np.arange(H) + 0.5)[:, None].repeat(W, 1) / ky
    Pl = face_local(c, face, S, T)
    o = np.array(c['origin']); R = rotmat(c['rot'])
    P = (Pl - o) @ R.T + o
    Lc = Pl - (np.array(c['frm']) + np.array(c['to'])) / 2
    img = np.zeros((H, W, 4), float)
    mat = MATS[c['mat']]
    if mat is not None:
        col = mat
        if c['inner_face'] == face:
            col = MATS['shell_in']
        img[..., :3] = col
        img[..., 3] = 255
    fc = F(img, P, S, T, Lc, fw, fh, (kx + ky) / 2, face)
    if face in c['decals']:
        c['decals'][face](fc)
    # downsample
    small = img.reshape(ph, SS, pw, SS, 4).mean((1, 3))
    # cutout alpha
    a = small[..., 3]
    small[..., 3] = np.where(a > 127, 255, 0)
    # edge AO + noise (only on opaque materials)
    if mat is not None:
        if c['ao'] and fw > 0.6 and fh > 0.6:
            sx = (np.arange(pw) + 0.5) * fw / pw; sy = (np.arange(ph) + 0.5) * fh / ph
            ex = np.minimum(sx, fw - sx)[None, :]; ey = np.minimum(sy, fh - sy)[:, None]
            e = np.minimum(ex, ey)
            ao = 1 - 0.10 * np.clip(1 - e / 0.22, 0, 1)
            small[..., :3] *= ao[..., None]
        n = RNG.normal(0, 2.2, (ph, pw, 1))
        small[..., :3] += n
    return np.clip(small, 0, 255).astype(np.uint8)


regions = []      # list of (key, array)
shared = {}
face_tex = {}     # (cube uuid, face) -> region index
for c in CUBES:
    for face in FACES:
        fw, fh = face_dims(c, face)
        if MATS[c['mat']] is None and face not in c['decals']:
            key = ('clear',)
        elif face not in c['decals']:
            key = ('plain', c['mat'], c['inner_face'] == face, round(fw * c['D'] * DSCALE), round(fh * c['D'] * DSCALE), c['ao'])
        elif c['name'].startswith('blade_'):
            key = ('blade', c['name'].split('_')[1], face, fw, fh)   # all blades on a prop share
        else:
            key = None
        if key is not None and key in shared:
            face_tex[(c['uuid'], face)] = shared[key]
            continue
        if key == ('clear',):
            arr = np.zeros((2, 2, 4), np.uint8)
        else:
            arr = bake_face(c, face)
        regions.append(arr)
        idx = len(regions) - 1
        face_tex[(c['uuid'], face)] = idx
        if key is not None:
            shared[key] = idx

# shelf packing
order = sorted(range(len(regions)), key=lambda i: (-regions[i].shape[0], -regions[i].shape[1]))
ATW = 1024
pos = {}
x = y = shelf_h = 0
PAD = 1
for i in order:
    h, w = regions[i].shape[:2]
    if x + w > ATW:
        x = 0; y += shelf_h + PAD; shelf_h = 0
    pos[i] = (x, y)
    x += w + PAD
    shelf_h = max(shelf_h, h)
need_h = y + shelf_h
ATH = 256
while ATH < need_h:
    ATH *= 2
print('regions', len(regions), 'atlas', ATW, ATH, 'used height', need_h)
atlas = np.zeros((ATH, ATW, 4), np.uint8)
for i, arr in enumerate(regions):
    px, py = pos[i]
    atlas[py:py + arr.shape[0], px:px + arr.shape[1]] = arr
tex_img = Image.fromarray(atlas, 'RGBA')
tex_img.save(os.path.join(OUT, f'{MODEL}.png'), optimize=True)


def face_uv(c, face):
    i = face_tex[(c['uuid'], face)]
    px, py = pos[i]
    h, w = regions[i].shape[:2]
    return [px, py, px + w, py + h]


# ============================================================================ ANIMATIONS
# values follow Bedrock/GeckoLib convention (same numbers stored in .bbmodel keyframes)
def spin_sign(spin):
    return -1 if spin == 'cw' else 1


ANIMS = {
    'animation.avata360.idle': dict(loop='loop', length=1.0, bones={
        'drone': {'position': [(0.0, [0, 0, 0], 'linear')]},
    }),
    'animation.avata360.fly': dict(loop='loop', length=1.0, bones={
        'drone': {
            'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.5, [0, 0.4, 0], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom')],
            'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.25, [0.6, 0, 0.9], 'catmullrom'),
                         (0.75, [-0.6, 0, -0.9], 'catmullrom'), (1.0, [0, 0, 0], 'catmullrom')],
        },
        **{f'prop_{t}': {'rotation': [(0.0, [0, 0, 0], 'linear'), (1.0, [0, 1440 * spin_sign(s), 0], 'linear')]}
           for _, _, t, s in RC},
    }),
    'animation.avata360.mode_360': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0, [0, 0, 0], 'catmullrom'), (0.75, [90, 0, 0], 'catmullrom')]},
    }),
    'animation.avata360.mode_single': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0, [90, 0, 0], 'catmullrom'), (0.75, [0, 0, 0], 'catmullrom')]},
    }),
}

# ============================================================================ EXPORT: .bbmodel
def r4(v):
    return [round(float(a), 4) for a in v]


elements = []
for c in CUBES:
    el = {
        'name': c['name'], 'box_uv': False, 'rescale': False, 'locked': False,
        'render_order': 'default', 'allow_mirror_modeling': True,
        'from': r4(c['frm']), 'to': r4(c['to']), 'autouv': 0, 'color': 0,
        'origin': r4(c['origin']),
        'faces': {f: {'uv': face_uv(c, f), 'texture': 0} for f in FACES},
        'type': 'cube', 'uuid': c['uuid'],
    }
    if any(abs(a) > 1e-6 for a in c['rot']):
        el['rotation'] = r4(c['rot'])
    elements.append(el)


def outliner_node(name):
    b = BONES[name]
    node = {'name': name, 'origin': r4(b['pivot']), 'color': 0, 'uuid': b['uuid'], 'export': True,
            'mirror_uv': False, 'isOpen': name in ('root', 'drone'), 'locked': False, 'visibility': True,
            'autouv': 0, 'children': []}
    node['children'] += [c['uuid'] for c in b['cubes']]
    for child in ORDER:
        if BONES[child]['parent'] == name:
            node['children'].append(outliner_node(child))
    return node


buf = io.BytesIO(); tex_img.save(buf, 'PNG', optimize=True)
tex_b64 = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

bb_anims = []
for aname, a in ANIMS.items():
    animators = {}
    for bname, chans in a['bones'].items():
        kfs = []
        for ch, keys in chans.items():
            for t, v, interp in keys:
                kfs.append({'channel': ch, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}],
                            'uuid': uid(), 'time': t, 'color': -1, 'interpolation': interp})
        animators[BONES[bname]['uuid']] = {'name': bname, 'type': 'bone', 'keyframes': kfs}
    bb_anims.append({'uuid': uid(), 'name': aname, 'loop': a['loop'], 'override': False, 'length': a['length'],
                     'snapping': 24, 'selected': False, 'anim_time_update': '', 'blend_weight': '',
                     'start_delay': '', 'loop_delay': '', 'animators': animators})

bbmodel = {
    'meta': {'format_version': '4.5', 'model_format': 'animated_entity_model', 'box_uv': False},
    'name': MODEL, 'model_identifier': MODEL,
    'visible_box': [3, 1, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [],
    'timeline_setups': [], 'unhandled_root_fields': {},
    'resolution': {'width': ATW, 'height': ATH},
    'elements': elements,
    'outliner': [outliner_node('root')],
    'textures': [{
        'path': '', 'name': f'{MODEL}.png', 'folder': 'entity', 'namespace': '', 'id': '0',
        'width': ATW, 'height': ATH, 'uv_width': ATW, 'uv_height': ATH, 'particle': False,
        'use_as_default': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default',
        'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '',
        'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False, 'uuid': uid(),
        'relative_path': '', 'source': tex_b64}],
    'animations': bb_anims,
}
with open(os.path.join(OUT, f'{MODEL}.bbmodel'), 'w', encoding='utf-8') as fp:
    json.dump(bbmodel, fp, separators=(',', ':'))

# ============================================================================ EXPORT: GeckoLib geo.json (Blockbench bedrock codec)
geo_bones = []
for name in ORDER:
    b = BONES[name]
    gb = {'name': name}
    if b['parent']:
        gb['parent'] = b['parent']
    gb['pivot'] = r4([-b['pivot'][0], b['pivot'][1], b['pivot'][2]])
    cubes = []
    for c in b['cubes']:
        size = [c['to'][i] - c['frm'][i] for i in range(3)]
        cube = {'origin': r4([-c['to'][0], c['frm'][1], c['frm'][2]]), 'size': r4(size)}
        if any(abs(a) > 1e-6 for a in c['rot']):
            cube['pivot'] = r4([-c['origin'][0], c['origin'][1], c['origin'][2]])
            cube['rotation'] = r4([-c['rot'][0], -c['rot'][1], c['rot'][2]])
        uv = {}
        for f in FACES:
            u = face_uv(c, f)
            w, h = u[2] - u[0], u[3] - u[1]
            if f in ('up', 'down'):
                uv[f] = {'uv': [u[0] + w, u[1] + h], 'uv_size': [-w, -h]}
            else:
                uv[f] = {'uv': [u[0], u[1]], 'uv_size': [w, h]}
        cube['uv'] = uv
        cubes.append(cube)
    if cubes:
        gb['cubes'] = cubes
    geo_bones.append(gb)

geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': f'geometry.{MODEL}', 'texture_width': ATW, 'texture_height': ATH,
                    'visible_bounds_width': 3, 'visible_bounds_height': 1.5, 'visible_bounds_offset': [0, 0.25, 0]},
    'bones': geo_bones}]}
with open(os.path.join(OUT, f'{MODEL}.geo.json'), 'w', encoding='utf-8') as fp:
    json.dump(geo, fp, indent=1)

anim_json = {'format_version': '1.8.0', 'animations': {}}
for aname, a in ANIMS.items():
    entry = {}
    if a['loop'] == 'loop':
        entry['loop'] = True
    elif a['loop'] == 'hold':
        entry['loop'] = 'hold_on_last_frame'
    entry['animation_length'] = a['length']
    bones_j = {}
    for bname, chans in a['bones'].items():
        bj = {}
        for ch, keys in chans.items():
            kd = {}
            for t, v, interp in keys:
                k = f'{t:.4f}'.rstrip('0').rstrip('.') if t else '0.0'
                if '.' not in k:
                    k += '.0'
                kd[k] = {'post': v, 'lerp_mode': interp} if interp == 'catmullrom' else v
            bj[ch] = kd
        bones_j[bname] = bj
    entry['bones'] = bones_j
    anim_json['animations'][aname] = entry
with open(os.path.join(OUT, f'{MODEL}.animation.json'), 'w', encoding='utf-8') as fp:
    json.dump(anim_json, fp, indent=1)
print('done', OUT)
