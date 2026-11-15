# -*- coding: utf-8 -*-
"""Procedural generator v3: DJI Avata 360 -> Blockbench (GeckoLib entity) model.

v3 changes vs v2 (gen_avata.py):
  * 4-blade scimitar propellers (matches reference photos)
  * Smoother body profile: spine edges more rounded, shoulders wider
  * Front camera housing: larger fisheye lens ring, secondary back lens removed,
    replaced by a spherical housing closer to reference photos
  * Gimbal protection cover: separate 'gimbal_cover' bone (D-shaped shell over
    the front lens). Two animations: gimbal_cover_open (swings forward around X),
    gimbal_cover_close (returns to position)
  * Battery slides DOWN from above (top-load) instead of from the rear.
    New battery_insert animation updated accordingly.
  * More detailed bottom cooling grilles, top status LED, side obstacle sensors
  * Battery model is merged from model/battery/avata360 at 0.5 scale (same as v2)

Coordinates: Blockbench units (1/16 block), 1 px ≈ 8 mm.
Front = -Z, Up = +Y. Origin = bottom centre of drone at hover height.

Usage:
    python3 gen_avata360_v3.py <out_dir> [battery_model_dir]
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone, box, obox, rrect, rr, rr_dist, norm_ang, rotmat

HERE = os.path.dirname(os.path.abspath(__file__))
OUT    = sys.argv[1] if len(sys.argv) > 1 else 'build_avata360_v3'
BAT_DIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'battery', 'avata360')
os.makedirs(OUT, exist_ok=True)

FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_C = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
if not os.path.exists(FONT_B):
    win_fonts = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts')
    FONT_B = os.path.join(win_fonts, 'arialbd.ttf')
    FONT_C = FONT_B
MODEL  = 'avata360'

# ─────────────────────────────────────────── materials (colour, procedural noise)
MATS = {
    'shell':        ((142, 144, 147), 2.0),   # main light-grey plastic
    'shell_dark':   ((100, 101, 104), 1.8),   # slightly darker panels / arms
    'rim':          ((148, 150, 153), 2.0),   # prop-guard rim (slightly lighter)
    'bay':          ((68,  70,  73),  1.4),   # battery bay interior
    'cam':          ((34,  35,  37),  1.2),   # camera housing body (near-black)
    'cam2':         ((24,  25,  27),  0.9),   # camera accent / fins (black)
    'housing':      ((18,  18,  20),  0.8),   # lens housing (black)
    'glass':        ((28,  50,  36),  0.0),   # front fisheye glass (teal-green)
    'glass_b':      ((55,  65,  65),  0.0),   # rear fisheye glass (silver-teal)
    'motor':        ((32,  32,  34),  1.0),   # stator ring
    'bell':         ((40,  40,  42),  1.0),   # rotor bell
    'prop':         ((25,  25,  27),  0.8),   # prop hub / blade plate
    'rubber':       ((55,  55,  58),  1.2),   # rubber foot pads
    'led':          ((190, 230, 200), 0.0),   # status LED (emissive green)
    'orange':       ((220, 128,  28), 0.0),   # motor ring accent (orange)
    'cover':        ((55,  57,  60),  1.0),   # gimbal cover dark shell
    'cover_clear':  (None,            0.0),   # gimbal cover clear lens window
    'clear':        (None,            0.0),   # alpha cutout (prop blades)
}

# ─────────────────────────────────────────── key dimensions (1 px ≈ 8 mm)
# Spine / body
YS    = 2.75          # spine centre-line Y
SP_W  = 7.0           # spine outer width
SP_H  = 4.4           # spine outer height (front cross-section)
SP_R  = 1.1           # spine edge radius
SP_T  = 0.42          # spine wall thickness (hollow bay section)
SP_Y0 = YS - SP_H/2  # 0.55
SP_Y1 = YS + SP_H/2  # 4.95

Z_SF  = -5.2          # spine front face Z
Z_BE  = -1.8          # bay end-wall Z (connector end)
Z_R   =  9.6          # tail opening Z (rear face)
BAY   = (-(SP_W/2 - SP_T), SP_Y0 + SP_T,
          (SP_W/2 - SP_T), SP_Y1 - SP_T, SP_R - SP_T)

# Battery top-load: drops in from Y+ above the spine tail section
BAT_S = 0.5           # battery merge scale
BAT_Y = YS            # battery centre-line Y (after scale)
BAT_Z = 5.3           # battery Z shift (rear section of spine)

# Prop guards
DUCT_X = 9.2          # duct centre X offset
DUCT_Z = 6.4          # duct centre Z offset
R_IN   = 5.2          # duct inner radius
R_OUT  = 6.1          # duct outer radius
D_Y0   = 1.05         # duct bottom Y
D_Y1   = 3.6          # duct top Y
D_R    = 0.40         # duct cross-section edge radius
N_SEG  = 36           # ring segments
RC = [(-DUCT_X, -DUCT_Z, 'fl', 'cw'),
      ( DUCT_X, -DUCT_Z, 'fr', 'ccw'),
      (-DUCT_X,  DUCT_Z, 'rl', 'ccw'),
      ( DUCT_X,  DUCT_Z, 'rr', 'cw')]

# Props (4-blade)
N_BLADES  = 4
BL        = 4.3       # blade length
BW_PLATE  = 3.6       # blade bounding-box plate width
HUB_OFF   = 0.44      # root offset from hub centre
PROP_Y    = 3.0       # propeller Y (top of motor bell)

# Camera module
CAM_CX, CAM_CY, CAM_CZ_F, CAM_CZ_B = 0.0, 3.1, -13.1, -5.8
LY = 3.1              # lens axis Y
LENS_R_F  = 1.55      # front fisheye glass radius
LENS_R_B  = 1.05      # rear fisheye glass radius

# Gimbal cover pivot: hinges at front face of camera housing
COV_PIV_Y = LY
COV_PIV_Z = CAM_CZ_F - 0.1

# ─────────────────────────────────────────── helper functions
def faces(**fns):
    """Dispatch decal painter by face name."""
    def fn(f):
        g = fns.get(f.face)
        if g: g(f)
    return fn

def chain(*fns):
    def fn(f):
        for g in fns:
            if g: g(f)
    return fn

_TXT = {}
def glyphs(txt, font, stretch=1.0):
    key = (txt, font, stretch)
    if key not in _TXT:
        fnt = ImageFont.truetype(font, 160)
        bb = fnt.getbbox(txt)
        im = Image.new('L', (bb[2]-bb[0]+8, bb[3]-bb[1]+8), 0)
        ImageDraw.Draw(im).text((4-bb[0], 4-bb[1]), txt, font=fnt, fill=255)
        if stretch != 1.0:
            im = im.resize((max(1, int(im.size[0]*stretch)), im.size[1]), Image.LANCZOS)
        _TXT[key] = np.asarray(im, float) / 255.0
    return _TXT[key]

def world_text(f, A, B, txt, ca, cb, h, color, font=FONT_B, stretch=1.0):
    g = glyphs(txt, font, stretch)
    gh, gw = g.shape
    sc = h / (gh - 8)
    u = (A - ca) / sc + gw/2
    v = (B - cb) / sc + gh/2
    inside = (u >= 0) & (u < gw) & (v >= 0) & (v < gh)
    m = g[np.clip(v.astype(int), 0, gh-1), np.clip(u.astype(int), 0, gw-1)] * inside
    f.paint(m, color)

def rrect_x(b, name, cy, cz, h, d, r, x0, x1, mat, D=24, n=3, decals=None):
    """Rounded-rect prism along X."""
    hh, hd = h/2, d/2
    r = max(0.05, min(r, hh-0.02, hd-0.02))
    step = 90.0 / (n+1)
    e = r * math.tan(math.radians(step/2))
    box(b, f'{name}_y', (x0, cy-hh+r-e, cz-hd), (x1, cy+hh-r+e, cz+hd), mat, D, decals=decals)
    box(b, f'{name}_z', (x0, cy-hh, cz-hd+r-e), (x1, cy+hh, cz+hd-r+e), mat, D, decals=decals)
    seg = 2*r*math.tan(math.radians(step/2)) + 0.004
    for sy in (1,-1):
        for sz in (1,-1):
            cc = ((x0+x1)/2, cy+sy*(hh-r), cz+sz*(hd-r))
            for k in range(1, n+1):
                a = step*k if sy*sz > 0 else -step*k
                obox(b, f'{name}_c{"p" if sy>0 else "m"}{"p" if sz>0 else "m"}{k}', cc,
                     (x1-x0, 2*r, seg), [norm_ang(a), 0, 0], mat, D=D, decals=decals)

def rtube_z(b, name, x0, y0, x1, y1, r, t, z0, z1, mat, D=24, n=4, decals=None):
    """Hollow rounded-rect tube along Z."""
    step = 90.0 / (n+1)
    e = r * math.tan(math.radians(step/2))
    box(b, f'{name}_top', (x0+r-e, y1-t, z0), (x1-r+e, y1,   z1), mat, D, decals=decals)
    box(b, f'{name}_bot', (x0+r-e, y0,   z0), (x1-r+e, y0+t, z1), mat, D, decals=decals)
    box(b, f'{name}_l',   (x0, y0+r-e, z0), (x0+t, y1-r+e, z1),   mat, D, decals=decals)
    box(b, f'{name}_r',   (x1-t, y0+r-e, z0), (x1, y1-r+e, z1),   mat, D, decals=decals)
    seg = 2*e + 0.004
    for sx in (1,-1):
        for sy in (1,-1):
            ccx = x1-r if sx > 0 else x0+r
            ccy = y1-r if sy > 0 else y0+r
            for k in range(1, n+1):
                th = math.radians(k*step)
                ux, uy = sx*math.cos(th), sy*math.sin(th)
                rm = r - t/2
                obox(b, f'{name}_a{"p" if sx>0 else "m"}{"p" if sy>0 else "m"}{k}',
                     (ccx+ux*rm, ccy+uy*rm, (z0+z1)/2), (t, seg, z1-z0),
                     [0, 0, norm_ang(math.degrees(math.atan2(uy, ux)))], mat, D=D, decals=decals)

def disc(b, name, center, radius, y0, y1, mat, axis='y', decals=None, D=12, sides=None):
    """Regular polygon prism as overlapping bars."""
    if sides is None:
        sides = 16 if radius > 1.05 else 12
    nb = sides // 2
    seg = 2*radius*math.tan(math.pi/sides) + 0.01
    for k in range(nb):
        a = norm_ang(k * 360.0/sides)
        e = 0.004*k
        if axis == 'y':
            cen  = [center[0], (y0+y1+e)/2, center[2]]
            size = [2*radius, y1-y0+e, seg]
            rot  = [0, a, 0]
        else:
            cen  = [center[0], center[1], (y0+y1-e)/2]
            size = [2*radius, seg, y1-y0+e]
            rot  = [0, 0, a]
        obox(b, f'{name}_{k}', cen, size, rot, mat, decals=decals, D=D)

# ─────────────────────────────────────────── decal painters
def d_spine(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nz) < 0.5:
        d_in = rr_dist(X, Y, BAY[0], BAY[1], BAY[2], BAY[3], BAY[4])
        inner = (np.abs(d_in) < 0.07) & (Z > Z_BE - 0.01)
        if inner.any():
            f.paint(inner, MATS['bay'][0])
            for k in range(7):
                zr = Z_BE + 1.2 + k * 1.5
                f.paint(inner & (np.abs(Z - zr) < 0.045), (50, 52, 55))
            f.paint(inner & (Z > Z_R - 0.4), (95, 97, 100))
            f.paint(inner & (np.abs(Y-YS) < 0.55) & (np.abs(Z-(Z_R-1.1)) < 0.28) & (np.abs(X) > 2.6), (28,29,31))
    if ny > 0.95 and Y.min() > SP_Y1 - 0.02:          # top face
        world_text(f, X, Z, 'dji', 0.0, 6.5, 0.9, (200,202,205), FONT_B)
        f.paint(rr(X, Z, -0.3, -4.1, 0.3, -3.95, 0.07), (210, 245, 220))   # status LED
        f.paint(rr(X, Z, -0.38,-4.18, 0.38,-3.87, 0.12) &
               ~rr(X, Z, -0.3, -4.1,  0.3, -3.95, 0.07), (110,112,115))
        # battery top-load opening outline
        f.paint(rr(X, Z,  -3.1, 3.4,  3.1, 7.6, 0.4) &
               ~rr(X, Z,  -2.85, 3.65, 2.85, 7.35, 0.3), (100,102,104))
    if ny < -0.95 and Y.max() < SP_Y0 + 0.02:         # bottom face
        # cooling grilles in two bands
        for z0b, z1b in ((-2.0, 0.8), (1.3, 4.5)):
            for i in range(10):
                x = -2.1 + i * 0.44
                f.paint(rr(X, Z, x, z0b, x+0.22, z1b, 0.07), (38,40,43))
        # label box
        f.paint(rr(X, Z, -2.2, -4.5, 2.2, -2.3, 0.14) &
               ~rr(X, Z, -2.14,-4.44, 2.14,-2.36, 0.11), (115,117,120))
        world_text(f, -X, Z, '81', -1.0, -3.3, 0.82, (115,117,120), FONT_C)
        world_text(f, -X, Z, 'CE',  1.0, -3.3, 0.6,  (115,117,120), FONT_C)
        # downward ToF sensor window
        f.paint(rr(X, Z, -1.0, 5.6, 1.0, 6.7, 0.5), (32,64,165))
        f.paint(rr(X, Z, -0.06, 5.6, 0.06, 6.7, 0.02), (18,26,58))
        f.paint(((X+0.47)**2 + (Z-5.9)**2) < 0.09**2, (145,178,238))
        # landing leg screw circles
        for px, pz in ((-2.4,-4.2),(2.4,-4.2),(-2.4,4.9),(2.4,4.9),(-2.4,8.5),(2.4,8.5)):
            f.paint(((X-px)**2+(Z-pz)**2) < 0.10**2, (42,43,45))
    if nx > 0.95 and X.min() > SP_W/2 - 0.02:         # right side: warning sticker
        f.paint(rr(Z, Y, 4.3, 2.0, 7.9, 3.8, 0.12), (22,22,24))
        f.paint(((Z-7.1)**2+(Y-2.9)**2) < 0.37**2, (202,38,38))
        f.paint(((Z-7.1)**2+(Y-2.9)**2) < 0.21**2, (238,238,238))
        for k in range(4):
            f.paint((np.abs(Y-(2.3+k*0.32)) < 0.038) & (Z > 4.5) & (Z < 6.6), (148,148,150))
    if abs(nx) > 0.95 and abs(X).min() > SP_W/2 - 0.02:  # side mic vents
        for i in range(3):
            f.paint(rr(Z, Y, 0.85+i*0.37, 1.25, 1.02+i*0.37, 2.4, 0.055), (58,59,62))
    if nz > 0.95:
        if Z.min() > Z_R - 0.02:                       # tail rim
            f.paint(np.ones_like(X, bool), (122,124,127))
        elif abs(Z.max() - Z_BE) < 0.02:               # connector wall
            f.paint(np.ones_like(X, bool), (60,62,65))
            f.paint(rr(X, Y, -1.6, YS-0.6, 1.6, YS+0.55, 0.2), (22,22,24))
            for k in range(8):
                xc = -1.1 + k * 0.32
                f.paint(rr(X, Y, xc-0.07, YS-0.32, xc+0.07, YS+0.27, 0.03), (195,168,95))
            for sx in (1,-1):
                f.paint(((X-sx*2.0)**2+(Y-YS)**2) < 0.15**2, (148,150,153))
                f.paint(((X-sx*2.0)**2+(Y-YS)**2) < 0.065**2, (38,38,40))

def d_cam(f):
    X, Y, Z = f.X, f.Y, f.Z
    nx, ny, nz = f.n
    if abs(nx) > 0.95:
        world_text(f, -np.sign(nx)*Z, -Y, 'AVATA 360',
                   np.sign(nx)*8.4, -2.1, 0.40, (118,120,123), FONT_C)
        f.paint(np.abs(Y - 1.55) < 0.028, (18,18,20))
    if nz < -0.95 and Z.max() < CAM_CZ_F + 0.1:
        # top vent slots above front lens
        f.paint(rr(X, Y, -1.5, 4.7, 1.5, 5.3, 0.22), (18,18,20))

def d_cam_top(f):
    if f.n[1] > 0.95:
        X, Z = f.X, f.Z
        for i in range(9):
            z = -11.4 + i * 0.62
            f.paint((np.abs(Z-z-0.07) < 0.07) & (X > -1.4) & (X < 0.75), (20,21,22))
        world_text(f, Z, X, 'AVATA 360', -8.5, -1.5, 0.25, (148,150,153), FONT_C)

def lens_ring(cx, cy, r_glass):
    def fn(f):
        dx = f.X - cx; dy = f.Y - cy
        r  = np.sqrt(dx*dx + dy*dy)
        ang = np.arctan2(dy, dx)
        knurl = (np.sin(ang*52) > 0) & (r > r_glass+0.15) & (r < r_glass+0.28)
        f.paint(r > r_glass+0.07, (22,22,24))
        f.paint(knurl, (38,38,41))
        f.paint(r > r_glass+0.28, (14,14,15))
        f.paint((r > r_glass) & (r <= r_glass+0.07), (118,120,122))
    return fn

def glass(cx, cy, R, tint=(32,60,42), silver=False):
    def fn(f):
        dx = f.X - cx; dy = f.Y - cy
        r  = np.sqrt(dx*dx + dy*dy) / R
        g  = np.clip(1.0-r, 0, 1)
        col = np.array(tint, float)[None,None,:] * (0.52 + 0.62*g[...,None])
        if silver:
            col = col*0.5 + np.array([148,153,158])[None,None,:] * (0.5*np.clip(1-r[...,None],0,1)**0.5)
        f.img[..., :3] = col
        f.paint(r > 0.92, (8,8,9))
        f.paint((r > 0.60) & (r < 0.66), (68,98,78))
        f.paint((r < 0.28) & (r > 0.22), (58,88,68))
        f.paint(((dx+0.33*R)**2+(dy-0.33*R)**2) < (0.15*R)**2, (212,232,218))
        f.paint(((dx-0.28*R)**2+(dy+0.38*R)**2) < (0.065*R)**2, (145,175,158))
        f.paint(r < 0.11, (6,12,8))
    return fn

def d_sensor(sx):
    def fn(f):
        X = f.X * sx; Y, Z = f.Y, f.Z
        if f.n[2] < -0.95:
            f.paint(rr(X, Y, 5.0, 2.05, 6.5, 2.95, 0.14), (12,12,14))
            rr2 = (X-5.55)**2 + (Y-1.2)**2
            f.paint(rr2 < 0.47**2, (18,18,20))
            f.paint(rr2 < 0.33**2, (30,56,42))
            f.paint(((X-5.45)**2+(Y-1.31)**2) < 0.09**2, (188,218,198))
            f.paint(((X-6.65)**2+(Y-2.5)**2) < 0.11**2, (38,38,40))
        elif f.n[1] < -0.95:
            rr2 = (X-6.05)**2 + (Z+11.65)**2
            f.paint(rr2 < 0.44**2, (18,18,20))
            f.paint(rr2 < 0.30**2, (38,72,52))
    return fn

def d_duct(cx, cz):
    def fn(f):
        nx, ny, nz = f.n
        dx = f.X-cx; dz = f.Z-cz
        rad = np.sqrt(dx*dx+dz*dz) + 1e-6
        inward = (nx*dx+nz*dz)/rad < -0.55
        f.shade(inward, 0.80)
        if ny > 0.5:
            f.shade(np.ones_like(dx, bool), 1.05)
    return fn

def mount_dec(cx, cz):
    def fn(f):
        if f.n[1] < -0.95:
            dx = f.X-cx; dz = f.Z-cz
            r = np.sqrt(dx*dx+dz*dz)
            f.paint(r < 0.94, (108,110,112))
            for k in range(3):
                a = math.radians(90+120*k)
                f.paint(((dx-0.47*math.cos(a))**2+(dz-0.47*math.sin(a))**2) < 0.15**2, (23,23,25))
            f.paint(r < 0.14, (58,58,60))
    return fn

def bell_dec(cx, cz, orange_ccw):
    def fn(f):
        dx = f.X-cx; dz = f.Z-cz
        if abs(f.n[1]) < 0.5:
            ang = np.degrees(np.arctan2(dz, dx))
            slot = (np.abs(((ang+15) % 30)-15) < 4.5) & (f.Y > 1.6) & (f.Y < 2.4)
            f.paint(slot, (148,90,43))
            f.paint(slot & (np.abs(((ang+15)%30)-15) < 1.5), (198,128,68))
            f.paint(f.Y < 1.45, (58,58,60))
        elif f.n[1] > 0.95:
            r = np.sqrt(dx*dx+dz*dz)
            ang = np.degrees(np.arctan2(dz, dx)) % 360
            f.paint((r > 0.37) & (r < 0.60), (108,68,36))
            f.paint((r > 0.37) & (r < 0.60) & (np.abs(((ang+15)%60)-30) < 9), (28,28,30))
            ring = (r > 0.62) & (r < 0.76)
            sel = (((ang > 20)&(ang<110)) | ((ang>200)&(ang<290))) if orange_ccw else (np.abs((ang%120)-60) < 9)
            f.paint(ring & sel, (235,138,28))
    return fn

def hub_dec(cx, cz):
    def fn(f):
        if f.n[1] > 0.95:
            dx = f.X-cx; dz = f.Z-cz
            r = np.sqrt(dx*dx+dz*dz)
            f.paint(r < 0.33, (58,58,60))
            for k in range(3):
                a = math.radians(30+120*k)
                f.paint(((dx-0.29*math.cos(a))**2+(dz-0.29*math.sin(a))**2) < 0.075**2, (118,120,122))
            f.paint(r < 0.09, (28,28,28))
    return fn

def blade_dec(sweep):
    """5-blade scimitar: drawn as alpha cutout on a thin plate."""
    def fn(f):
        L = f.L
        lx, lz = L[...,0], L[...,2]
        u  = (lx + BL/2) / BL
        uc = np.clip(u, 0, 1)
        # swept camber line
        zc  = sweep * (1.6 * uc**1.75 - 0.38)
        # chord width: wider root, tapers and rounds at tip
        hw  = (0.28 + 0.98*uc**0.55) * np.sqrt(np.clip(1 - uc**4.5, 0, 1)) * (1 - 0.22*uc)
        off = lz - zc
        lim = np.where(off*sweep > 0, hw*0.82, hw*1.18)
        m   = (u >= 0) & (u <= 1) & (np.abs(off) < lim)
        f.img[...,3] = np.where(m, 255, 0)
        f.img[...,:3] = np.where(m[...,None], np.array([25,25,27], float), f.img[...,:3])
        edge = m & (np.abs(off) > lim - 0.075)
        f.paint(edge, (44,44,46))
        f.paint(m & (np.abs(off) < lim*0.22) & (uc < 0.65), (30,30,32))
    return fn

def d_cover(f):
    """Gimbal cover shell faces – dark matte with 'AVATA 360' label."""
    if f.n[2] < -0.95:   # front face of cover
        X, Y = f.X, f.Y
        world_text(f, X, -Y, 'AVATA 360', 0.0, -LY, 0.38, (135,138,142), FONT_C)
        # three horizontal ribs
        for yrib in (LY+0.5, LY, LY-0.5):
            f.paint(np.abs(Y - yrib) < 0.03, (45,46,48))
    if f.n[1] > 0.95:    # top lip
        f.paint(np.ones_like(f.X, bool), (50,51,54))

# ─────────────────────────────────────────── bone skeleton
bone('root',  (0,0,0))
bone('drone', (0,0,0), 'root')
bone('body',  (0, YS, 2), 'drone')
bone('guards',(0, 2, 0),  'drone')
bone('frame', (0, 1, 0),  'drone')
bone('camera',(CAM_CX, CAM_CY, (CAM_CZ_F+CAM_CZ_B)/2), 'drone')
bone('gimbal_cover', (CAM_CX, COV_PIV_Y, COV_PIV_Z), 'camera')

# ─────────────────────────────────────────── spine geometry
x0, x1 = -SP_W/2, SP_W/2
rrect('body', 'spine_f',   0, YS, SP_W, SP_H, SP_R, Z_SF,  Z_BE, 'shell', D=16, n=4, decals=d_spine)
rtube_z('body','spine_bay', x0, SP_Y0, x1, SP_Y1, SP_R, SP_T, Z_BE, Z_R, 'shell', D=16, n=4, decals=d_spine)
box('body','led', (-0.3, SP_Y1, -4.1), (0.3, SP_Y1+0.04, -3.95), 'led', D=24)

# ─────────────────────────────────────────── shoulders / sensor housings
for sx, side in ((1,'r'),(-1,'l')):
    rrect('body', f'shoulder_{side}', sx*3.3, 2.6, 2.5, 4.4, 0.78, -13.0, Z_SF+0.3,
          'shell', D=14, n=3, decals=d_sensor(sx))
    xa, xb = sorted([sx*4.3, sx*7.0])
    rrect_x('body', f'sensor_{side}', 2.0, -11.65, 2.95, 1.65, 0.62, xa, xb,
            'shell', D=16, n=3, decals=d_sensor(sx))
    xa, xb = sorted([sx*2.3, sx*6.9])
    box('body', f'chin_{side}', (xa, 0.28, -12.1), (xb, 0.52, -10.6), 'shell_dark', D=8)

# ─────────────────────────────────────────── camera module
rrect('camera','cam_body', 0, CAM_CY, 3.9, 5.4, 0.72, CAM_CZ_F, CAM_CZ_B, 'cam', D=16, n=3, decals=d_cam)
box('camera','cam_top', (-1.65, 5.86, CAM_CZ_F+0.4), (1.65, 6.18, CAM_CZ_B), 'cam', D=18, decals=d_cam_top)
for i, zc in enumerate([-11.5, -10.8, -10.1, -9.4, -8.7, -8.0]):
    box('camera', f'cam_fin_{i}', (-1.3, 6.18, zc-0.14), (1.3, 6.44, zc+0.14), 'cam2', D=12)
box('camera','cam_chin', (-1.55, 0.2, CAM_CZ_F+0.4), (1.55, 0.44, CAM_CZ_B), 'cam', D=8)
for sx in (1,-1):
    xa, xb = sorted([sx*1.95, sx*2.05])
    box('camera', f'cam_axle_{"r" if sx>0 else "l"}', (xa, 2.55, -9.6), (xb, 3.65, -8.3), 'housing', D=12)

# front fisheye lens
disc('camera','lens_f_housing', (CAM_CX,LY,0), 1.58, CAM_CZ_F-0.68, CAM_CZ_F,
     'housing', axis='z', decals=lens_ring(CAM_CX, LY, LENS_R_F), D=22)
disc('camera','lens_f_glass',   (CAM_CX,LY,0), LENS_R_F, CAM_CZ_F-0.90, CAM_CZ_F-0.68,
     'glass', axis='z', decals=glass(CAM_CX, LY, LENS_R_F+0.04), D=26)

# rear fisheye lens
disc('camera','lens_r_housing', (CAM_CX,LY,0), 1.1,  CAM_CZ_B, CAM_CZ_B+0.3,
     'housing', axis='z', decals=lens_ring(CAM_CX, LY, LENS_R_B), D=18)
disc('camera','lens_r_glass',   (CAM_CX,LY,0), LENS_R_B, CAM_CZ_B+0.3, CAM_CZ_B+0.5,
     'glass_b', axis='z', decals=glass(CAM_CX, LY, LENS_R_B+0.04, tint=(55,65,65), silver=True), D=20)

# ─────────────────────────────────────────── gimbal cover (D-shape, hinges forward)
# The cover wraps the front lens from below and sides; it is a rounded D profile
# sitting flush against the cam_body front face.  When opened it rotates ~100 deg
# around the X axis (top-forward).
COV_H  = 3.4   # cover height (Y span)
COV_W  = 3.9   # cover width (X span)
COV_D  = 1.0   # cover depth (Z span, sticks forward)
COV_R  = 0.55  # cover edge radius
rrect('gimbal_cover', 'cov_shell', CAM_CX, LY, COV_W, COV_H, COV_R,
      COV_PIV_Z - COV_D, COV_PIV_Z, 'cover', D=18, n=3, decals=d_cover)
# inner clear window in front face of cover
disc('gimbal_cover','cov_window', (CAM_CX, LY, 0), 1.45,
     COV_PIV_Z - COV_D - 0.04, COV_PIV_Z - COV_D,
     'cover_clear', axis='z', D=16)
# hinge clip tabs (small boxes each side)
for sx in (1,-1):
    box('gimbal_cover', f'cov_hinge_{"r" if sx>0 else "l"}',
        (sx*1.85, LY-0.22, COV_PIV_Z-0.28), (sx*2.1, LY+0.22, COV_PIV_Z), 'cam2', D=10)

# ─────────────────────────────────────────── prop guards
SOLIDS = [((-SP_W/2, Z_SF), (SP_W/2, Z_R)),
          ((2.1, -13.0), (4.5, Z_SF)),
          ((-4.5, -13.0), (-2.1, Z_SF))]

def inside_solid(pts):
    return any(all(a0 <= p[0] <= a1 and b0 <= p[1] <= b1 for p in pts)
               for (a0,b0),(a1,b1) in SOLIDS)

def ring_piece(tag, k, part, phi, q0, q1, y0, y1, tilt, cx, cz, dec):
    qc, yc = (q0+q1)/2, (y0+y1)/2
    L   = 2*(R_OUT+0.05)*math.tan(math.pi/N_SEG) + 0.03
    yaw = norm_ang(-(math.degrees(phi)+90))
    obox('guards', f'duct_{tag}_{part}_{k}',
         (cx+qc*math.cos(phi), yc, cz+qc*math.sin(phi)),
         (L, y1-y0, q1-q0), [tilt, yaw, 0], 'rim', D=10, decals=dec)

for cx, cz, tag, _ in RC:
    dec = d_duct(cx, cz)
    for k in range(N_SEG):
        phi = 2*math.pi*(k+0.5)/N_SEG
        tx, tz = -math.sin(phi), math.cos(phi)
        Lseg = 2*R_OUT*math.tan(math.pi/N_SEG)
        corners = [(cx+rr_*math.cos(phi)+a*tx*Lseg/2, cz+rr_*math.sin(phi)+a*tz*Lseg/2)
                   for a in (-1,1) for rr_ in (R_IN, R_OUT)]
        if inside_solid(corners):
            continue
        r = D_R
        ring_piece(tag, k, 'h', phi, R_IN,   R_OUT,   D_Y0+r, D_Y1-r, 0,  cx, cz, dec)
        ring_piece(tag, k, 'v', phi, R_IN+r, R_OUT-r, D_Y0,   D_Y1,   0,  cx, cz, dec)
        for sq, sy, nsl in ((1,1,2),(-1,1,2),(1,-1,1),(-1,-1,1)):
            qc = (R_OUT-r) if sq > 0 else (R_IN+r)
            yc = (D_Y1-r)  if sy > 0 else (D_Y0+r)
            step = 90.0/(nsl+1)
            seg  = 2*r*math.tan(math.radians(step/2)) + 0.004
            for j in range(1, nsl+1):
                a = step*j if (sy>0)==(-sq>0) else -step*j
                ring_piece(tag, k, f's{sq}{sy}{j}', phi,
                           qc-seg/2, qc+seg/2, yc-r, yc+r, norm_ang(a), cx, cz, dec)
    ox = (1 if cx > 0 else -1); oz = (1 if cz > 0 else -1)
    ang = math.atan2(oz*0.75, ox)
    fx, fz = cx+5.6*math.cos(ang), cz+5.6*math.sin(ang)
    obox('guards', f'foot_{tag}', (fx, 0.74, fz), (1.1, 0.52, 0.72),
         [0, norm_ang(-(math.degrees(ang)+90)), 0], 'rubber', D=8)

# waist bridges + struts
for sx in (1,-1):
    rrect('guards', f'waist_{"r" if sx>0 else "l"}', sx*DUCT_X, (D_Y0+D_Y1)/2, 1.6, D_Y1-D_Y0, D_R,
          -1.0, 1.0, 'rim', D=10, n=2)
    for sz in (1,-1):
        p0 = np.array([sx*(SP_W/2-0.2), sz*2.4])
        p1 = np.array([sx*(DUCT_X-0.65), sz*0.3])
        d  = p1-p0; Ld = float(np.linalg.norm(d))
        yaw = norm_ang(math.degrees(math.atan2(-d[1], d[0])))
        c   = (p0+p1)/2
        obox('guards', f'strut_{"r" if sx>0 else "l"}{"f" if sz<0 else "b"}',
             (c[0], 2.2, c[1]), (Ld, 1.65, 0.72), [0, yaw, 0], 'shell', D=10)

# ─────────────────────────────────────────── arms, motors, 5-blade props
for cx, cz, tag, spin in RC:
    sx, sz = (1 if cx>0 else -1), (1 if cz>0 else -1)
    B = np.array([sx*3.1, sz*3.0]); M = np.array([cx, cz])
    d = M-B; Lr = float(np.linalg.norm(d)); dn = d/Lr
    yaw = norm_ang(math.degrees(math.atan2(-dn[1], dn[0])))
    ctr = (B+M)/2
    obox('frame', f'arm_{tag}', (ctr[0], 1.18, ctr[1]), (Lr+0.65, 0.58, 0.98), [0,yaw,0], 'shell_dark', D=8)
    back = math.atan2(-dn[1], -dn[0])
    for j, off in enumerate((120,-120)):
        a = back + math.radians(off)
        e = np.array([math.cos(a), math.sin(a)])
        c2 = M + e*(0.9+R_IN+0.25)/2
        obox('frame', f'spoke_{tag}_{j}', (c2[0], 1.08, c2[1]),
             (R_IN+0.25-0.9, 0.3, 0.28),
             [0, norm_ang(math.degrees(math.atan2(-e[1], e[0]))), 0], 'shell_dark', D=6)

    mb = bone(f'motor_{tag}', (cx, 1.0, cz), 'drone')
    disc(mb, f'mount_{tag}', (cx,0,cz), 1.02, 0.9, 1.38, 'shell_dark', decals=mount_dec(cx,cz), D=14)
    disc(mb, f'bell_{tag}',  (cx,0,cz), 0.86, 1.38, 2.6,  'bell', decals=bell_dec(cx,cz, spin=='ccw'), D=16)
    disc(mb, f'bell_top_{tag}', (cx,0,cz), 0.81, 2.6, 2.78, 'motor', decals=bell_dec(cx,cz, spin=='ccw'), D=20)

    pb = bone(f'prop_{tag}', (cx, PROP_Y, cz), mb)
    disc(pb, f'hub_{tag}', (cx,0,cz), 0.62, 2.78, 3.18, 'prop', decals=hub_dec(cx,cz), D=20)

    sweep = 1.0 if spin == 'cw' else -1.0
    pitch = 15.0 * sweep
    base  = 18.0 if tag in ('fl','rr') else 54.0
    for kb in range(N_BLADES):
        yb  = norm_ang(base + (360.0/N_BLADES)*kb)
        R   = rotmat([pitch, yb, 0])
        cen = np.array([cx, PROP_Y, cz]) + R @ np.array([HUB_OFF+BL/2, 0, 0])
        obox(pb, f'blade_{tag}_{kb}', cen, (BL, 0.08, BW_PLATE), [pitch, yb, 0], 'clear', D=24,
             decals=faces(up=blade_dec(sweep), down=blade_dec(sweep)))

# ─────────────────────────────────────────── merge battery model
def merge_battery(bat_dir):
    geo_path = os.path.join(bat_dir, 'geo', 'avata360_battery.geo.json')
    if not os.path.exists(geo_path):
        print(f'[warn] battery geo not found at {geo_path}, skipping merge')
        return
    geo  = json.load(open(geo_path, encoding='utf-8'))['minecraft:geometry'][0]
    tex  = np.asarray(Image.open(os.path.join(bat_dir,'textures','avata360_battery.png')).convert('RGBA'))
    gm_path = os.path.join(bat_dir,'textures','avata360_battery_glowmask.png')
    gtex = np.asarray(Image.open(gm_path).convert('RGBA')) if os.path.exists(gm_path) else np.zeros_like(tex)
    DS = 2
    s, YB, ZB = BAT_S, BAT_Y, BAT_Z

    def P(p):
        return [-s*p[0], s*p[1]+YB, -s*p[2]+ZB]

    rename = {'battery':'battery','body':'bat_body','cap':'bat_cap',
              'button':'bat_button','latch_l':'bat_latch_r','latch_r':'bat_latch_l'}
    swap   = {'north':'south','south':'north','east':'west','west':'east','up':'up','down':'down'}
    n = 0
    for b in geo['bones']:
        if b['name'] == 'root':
            continue
        pv     = b['pivot']
        parent = 'drone' if b.get('parent') in (None,'root') else rename[b['parent']]
        bone(rename[b['name']], P([-pv[0], pv[1], pv[2]]), parent)
        for c in b.get('cubes', []):
            o, sz = c['origin'], c['size']
            frm = [-(o[0]+sz[0]), o[1], o[2]]
            to  = [frm[i]+sz[i] for i in range(3)]
            nf, nt = P(to), P(frm)
            frm2 = [min(nf[i],nt[i]) for i in range(3)]
            to2  = [max(nf[i],nt[i]) for i in range(3)]
            if 'rotation' in c:
                cp  = c['pivot']; org = P([-cp[0], cp[1], cp[2]])
                r_  = c['rotation']; rb = [-r_[0],-r_[1],r_[2]]
                rot = [-rb[0], rb[1], -rb[2]]
            else:
                org, rot = frm2, [0,0,0]
            pre = {}
            for fname, fd in c['uv'].items():
                (u, v), (w, h) = fd['uv'], fd['uv_size']
                ua,ub = sorted([u,u+w]); va,vb = sorted([v,v+h])
                ua,ub,va,vb = int(round(ua)),int(round(ub)),int(round(va)),int(round(vb))
                reg  = np.asarray(Image.fromarray( tex[va:vb,ua:ub],'RGBA').resize(
                           (max(1,int(round((ub-ua)/DS))), max(1,int(round((vb-va)/DS)))), Image.BOX))
                greg = np.asarray(Image.fromarray(gtex[va:vb,ua:ub],'RGBA').resize(
                           (reg.shape[1], reg.shape[0]), Image.BOX))
                tf = swap[fname]
                if tf in ('up','down'):
                    reg, greg = reg[::-1,::-1], greg[::-1,::-1]
                pre[tf] = (np.ascontiguousarray(reg), np.ascontiguousarray(greg))
            cb = box(rename[b['name']], 'bat_'+str(n), frm2, to2, 'shell', origin=org, rot=rot)
            cb['pre'] = pre
            n += 1
    print('merged battery cubes', n)

merge_battery(BAT_DIR)

# ─────────────────────────────────────────── animations
def spin_sign(sp):
    return -1 if sp == 'cw' else 1

# Battery top-load: starts above the drone, lowers into bay from Y+
# Out-position: Y+8 (above spine), Z at battery bay centre (Z ~5.5)
BAT_OUT_Y = [0, 7.5, 0]   # offset while outside
ANIMS = {
    'animation.avata360.idle': dict(loop='loop', length=1.0, bones={
        'drone': {'position': [(0.0, [0,0,0], 'linear')]}}),

    'animation.avata360.fly': dict(loop='loop', length=1.0, bones={
        'drone': {
            'position': [(0.0,[0,0,0],'catmullrom'),(0.25,[0,0.2,0],'catmullrom'),
                         (0.5,[0,0.42,0],'catmullrom'),(0.75,[0,0.2,0],'catmullrom'),
                         (1.0,[0,0,0],'catmullrom')],
            'rotation': [(0.0,[0,0,0],'catmullrom'),(0.25,[1.2,0,1.4],'catmullrom'),
                         (0.5,[0,0,0],'catmullrom'),(0.75,[-1.2,0,-1.4],'catmullrom'),
                         (1.0,[0,0,0],'catmullrom')]},
        **{f'prop_{t}': {'rotation': [(0.0,[0,0,0],'linear'),(1.0,[0,1440*spin_sign(s),0],'linear')]}
           for _,_,t,s in RC}}),

    'animation.avata360.mode_360': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0,[0,0,0],'catmullrom'),(0.75,[90,0,0],'catmullrom')]}}),
    'animation.avata360.mode_single': dict(loop='hold', length=0.75, bones={
        'camera': {'rotation': [(0.0,[90,0,0],'catmullrom'),(0.75,[0,0,0],'catmullrom')]}}),

    # ---- gimbal cover open: rotates ~100 deg around X axis (top pivots forward)
    'animation.avata360.gimbal_open': dict(loop='hold', length=0.6, bones={
        'gimbal_cover': {
            'rotation': [(0.0,[0,0,0],'catmullrom'),(0.6,[-100,0,0],'catmullrom')]}}),
    'animation.avata360.gimbal_close': dict(loop='hold', length=0.5, bones={
        'gimbal_cover': {
            'rotation': [(0.0,[-100,0,0],'catmullrom'),(0.5,[0,0,0],'catmullrom')]}}),

    # ---- battery top-load insert: slides down from above, latches click
    'animation.avata360.battery_insert': dict(loop='hold', length=2.2, bones={
        'battery': {
            'position': [(0.0, BAT_OUT_Y,'catmullrom'),(0.5,[0,4.0,0],'catmullrom'),
                         (0.85,[0,1.0,0],'catmullrom'),(1.1,[0,0.1,0],'catmullrom'),
                         (1.25,[0,-0.08,0],'linear'),(1.38,[0,0,0],'linear')],
            'rotation': [(0.0,[0,0,0],'linear')],
            'scale':    [(0.0,[1,1,1],'linear')]},
        'bat_latch_r': {'position': [(0.0,[0,0,0],'linear'),(0.65,[0,0,0],'linear'),
                                     (0.78,[0.1,0,0],'linear'),(1.3,[0.1,0,0],'linear'),
                                     (1.38,[0,0,0],'linear')]},
        'bat_latch_l': {'position': [(0.0,[0,0,0],'linear'),(0.65,[0,0,0],'linear'),
                                     (0.78,[-0.1,0,0],'linear'),(1.3,[-0.1,0,0],'linear'),
                                     (1.38,[0,0,0],'linear')]},
        'bat_button':  {'position': [(0.0,[0,0,0],'linear'),(1.7,[0,0,0],'linear'),
                                     (1.78,[0,0,-0.055],'linear'),(1.9,[0,0,0],'linear')]}}),

    'animation.avata360.battery_remove': dict(loop='hold', length=1.6, bones={
        'battery': {
            'position': [(0.0,[0,0,0],'linear'),(0.2,[0,0,0],'catmullrom'),
                         (0.65,[0,2.5,0],'catmullrom'),(1.0,[0,5.0,0],'catmullrom'),
                         (1.55, BAT_OUT_Y,'catmullrom')],
            'rotation': [(0.0,[0,0,0],'linear')],
            'scale':    [(0.0,[1,1,1],'linear'),(1.55,[1,1,1],'linear'),(1.6,[0,0,0],'linear')]},
        'bat_latch_r': {'position': [(0.0,[0,0,0],'linear'),(0.1,[0.1,0,0],'linear'),
                                     (0.6,[0.1,0,0],'linear'),(0.72,[0,0,0],'linear')]},
        'bat_latch_l': {'position': [(0.0,[0,0,0],'linear'),(0.1,[-0.1,0,0],'linear'),
                                     (0.6,[-0.1,0,0],'linear'),(0.72,[0,0,0],'linear')]}}),

    'animation.avata360.battery_installed': dict(loop='hold', length=0.05, bones={
        'battery': {'position':[(0.0,[0,0,0],'linear')],'rotation':[(0.0,[0,0,0],'linear')],
                    'scale':   [(0.0,[1,1,1],'linear')]}}),
    'animation.avata360.battery_empty': dict(loop='hold', length=0.05, bones={
        'battery': {'position':[(0.0, BAT_OUT_Y,'linear')],'scale':[(0.0,[0,0,0],'linear')]}}),
    'animation.avata360.gimbal_on': dict(loop='hold', length=0.05, bones={
        'gimbal_cover': {'rotation':[(0.0,[0,0,0],'linear')]}}),
    'animation.avata360.gimbal_off': dict(loop='hold', length=0.05, bones={
        'gimbal_cover': {'rotation':[(0.0,[-100,0,0],'linear')]}}),
}

# ─────────────────────────────────────────── export
meta = G.export(OUT, MODEL, MATS, ANIMS, kind='entity', atw=2048, glow=True,
                bounds=(3.2, 1.6), bounds_offset=[0, 0.25, 0],
                hidden_bones=('battery', 'bat_body', 'bat_cap', 'bat_button',
                               'bat_latch_r', 'bat_latch_l'))
json.dump({'battery_transform': {'scale':BAT_S,'rotation_y':0,'centre_line_y':BAT_Y,'z_shift':BAT_Z},
           'bay': {'x':[BAY[0],BAY[2]],'y':[BAY[1],BAY[3]],'z':[Z_BE,Z_R],'corner_r':BAY[4]},
           'gimbal_cover_pivot': {'y':COV_PIV_Y,'z':COV_PIV_Z},
           **meta},
          open(os.path.join(OUT,'meta.json'),'w'), indent=1)
print('done', OUT)
