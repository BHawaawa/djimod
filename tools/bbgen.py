# -*- coding: utf-8 -*-
"""Shared procedural Blockbench/GeckoLib builder used by the battery generators.

Usage (one model per process):
    import bbgen as G
    G.bone('root', (0,0,0)); G.rrect(...); G.box(...)
    G.export(out_dir, model_name, mats, anims, kind='item', offset=(0,-3.5,0), display={...})

Coordinates are Blockbench units (1 px = 1/16 block), +X east, +Y up, -Z north.
Every cube face is baked into its own atlas region by calling the cube's decal painter with the world
position of every texel (supersampled), so painting is continuous across neighbouring cubes.
"""
import json, math, uuid, base64, io, os
import numpy as np
from PIL import Image

SS = 4
FACES = ['north', 'east', 'south', 'west', 'up', 'down']
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
BONES, ORDER, CUBES = {}, [], []


def uid():
    return str(uuid.uuid4())


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def norm_ang(a):
    return round((a + 180.0) % 360.0 - 180.0, 4)


# ============================================================================ geometry registry
def bone(name, pivot, parent=None, rot=None):
    """rot = optional rest rotation of the group (Blockbench euler degrees, same convention as cube rotations)."""
    BONES[name] = dict(name=name, pivot=list(pivot), parent=parent, cubes=[], uuid=uid(),
                       rot=[float(a) for a in rot] if rot is not None else None)
    ORDER.append(name)
    return name


def box(b, name, frm, to, mat, D=24, origin=None, rot=None, decals=None):
    c = dict(bone=b, name=name, frm=[float(v) for v in frm], to=[float(v) for v in to], mat=mat, D=D,
             origin=[float(v) for v in (origin if origin is not None else frm)],
             rot=[float(v) for v in (rot if rot is not None else [0, 0, 0])],
             decals=decals, uuid=uid())
    CUBES.append(c); BONES[b]['cubes'].append(c)
    return c


def obox(b, name, center, size, rot, mat, **kw):
    center = np.array(center, float); size = np.array(size, float)
    return box(b, name, center - size / 2, center + size / 2, mat, origin=center, rot=rot, **kw)


def xbox(b, name, x0, x1, y0, y1, z0, z1, mat, **kw):
    """box from unordered coordinate pairs."""
    return box(b, name, (min(x0, x1), min(y0, y1), min(z0, z1)), (max(x0, x1), max(y0, y1), max(z0, z1)), mat, **kw)


def rrect(b, name, cx, cy, w, h, r, z0, z1, mat, D=24, n=3, decals=None):
    """rounded-rectangle prism along Z: 2 core boxes + n bevel slats per corner (slats overlap inward)."""
    hw, hh = w / 2, h / 2
    r = max(0.05, min(r, hw - 0.02, hh - 0.02))
    step = 90.0 / (n + 1)
    e = r * math.tan(math.radians(step / 2))     # core faces run to the first polygon vertex (no corner notches)
    box(b, f'{name}_h', (cx - hw, cy - hh + r - e, z0), (cx + hw, cy + hh - r + e, z1), mat, D, decals=decals)
    box(b, f'{name}_v', (cx - hw + r - e, cy - hh, z0), (cx + hw - r + e, cy + hh, z1), mat, D, decals=decals)
    seg = 2 * r * math.tan(math.radians(step / 2)) + 0.004
    for sx in (1, -1):
        for sy in (1, -1):
            cc = (cx + sx * (hw - r), cy + sy * (hh - r), (z0 + z1) / 2)
            for k in range(1, n + 1):
                a = step * k if sx * sy > 0 else -step * k
                obox(b, f'{name}_c{"p" if sx > 0 else "m"}{"p" if sy > 0 else "m"}{k}', cc, (2 * r, seg, z1 - z0),
                     [0, 0, norm_ang(a)], mat, D=D, decals=decals)


def rrect_y(b, name, cx, cz, w, d, r, y0, y1, mat, D=24, n=3, decals=None):
    """rounded-rectangle prism along Y (rounded vertical edges)."""
    hw, hd = w / 2, d / 2
    r = max(0.05, min(r, hw - 0.02, hd - 0.02))
    step = 90.0 / (n + 1)
    e = r * math.tan(math.radians(step / 2))
    box(b, f'{name}_x', (cx - hw, y0, cz - hd + r - e), (cx + hw, y1, cz + hd - r + e), mat, D, decals=decals)
    box(b, f'{name}_z', (cx - hw + r - e, y0, cz - hd), (cx + hw - r + e, y1, cz + hd), mat, D, decals=decals)
    seg = 2 * r * math.tan(math.radians(step / 2)) + 0.004
    for sx in (1, -1):
        for sz in (1, -1):
            cc = (cx + sx * (hw - r), (y0 + y1) / 2, cz + sz * (hd - r))
            for k in range(1, n + 1):
                a = -step * k if sx * sz > 0 else step * k        # rotation about Y: +a turns +X toward -Z
                obox(b, f'{name}_c{"p" if sx > 0 else "m"}{"p" if sz > 0 else "m"}{k}', cc, (2 * r, y1 - y0, seg),
                     [0, norm_ang(a), 0], mat, D=D, decals=decals)


# ============================================================================ painting helpers
class F:
    def __init__(self, img, glow, P, n, S, T, fw, fh, k, face, cube):
        self.img, self.glow, self.P, self.n, self.S, self.T = img, glow, P, n, S, T
        self.fw, self.fh, self.k, self.face, self.c = fw, fh, k, face, cube
        self.X, self.Y, self.Z = P[..., 0], P[..., 1], P[..., 2]
        self.L = None   # cube-local coords relative to the cube centre (set by bake_face)

    # face-space (S,T) helpers: S along the face width, T down from the face top
    def st_rect(self, s0, t0, s1, t1):
        return (self.S >= s0) & (self.S <= s1) & (self.T >= t0) & (self.T <= t1)

    def st_rrect(self, s0, t0, s1, t1, r):
        return rr(self.S, self.T, s0, t0, s1, t1, r)

    def st_circle(self, cs, ct, r):
        return (self.S - cs) ** 2 + (self.T - ct) ** 2 <= r * r

    def paint(self, mask, color, a=1.0):
        m = mask[..., None].astype(float) * a if mask.dtype == bool else mask[..., None] * a
        col = np.asarray(color, float)
        col = col[..., :3] if col.ndim == 3 else col[:3]
        self.img[..., :3] = self.img[..., :3] * (1 - m) + col * m

    def shade(self, mask, mul):
        m = mask.astype(float)[..., None] if mask.dtype == bool else mask[..., None]
        self.img[..., :3] *= (1 + (mul - 1) * m)

    def emit(self, mask, color):
        m = mask[..., None].astype(float)
        self.glow[..., :3] = self.glow[..., :3] * (1 - m) + np.array(color, float) * m
        self.glow[..., 3:] = np.maximum(self.glow[..., 3:], m * 255)


def rr(A, B, a0, b0, a1, b1, r):
    ca = np.clip(A, a0 + r, a1 - r); cb = np.clip(B, b0 + r, b1 - r)
    return (A - ca) ** 2 + (B - cb) ** 2 <= r * r


def rr_dist(A, B, a0, b0, a1, b1, r):
    """signed distance to a rounded rectangle (negative inside)."""
    ca, cb = (a0 + a1) / 2, (b0 + b1) / 2
    qa = np.abs(A - ca) - ((a1 - a0) / 2 - r); qb = np.abs(B - cb) - ((b1 - b0) / 2 - r)
    out = np.sqrt(np.maximum(qa, 0) ** 2 + np.maximum(qb, 0) ** 2)
    return out + np.minimum(np.maximum(qa, qb), 0) - r


def world_noise(P, scale, seed):
    """deterministic value noise from world position (identical on coplanar overlapping faces)."""
    q = np.floor(P * scale).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ seed
    h = (h ^ (h >> 13)) * 1274126177
    return ((h & 0xFFFF) / 65535.0) - 0.5


def make_decal_sampler(img_arr, a0, a1, b0, b1):
    """returns sample(A, B) -> (rgb, alpha) for an RGBA art image mapped onto rectangle [a0,a1]x[b0,b1]."""
    H, W = img_arr.shape[:2]

    def sample(A, B):
        u = (A - a0) / (a1 - a0) * W; v = (B - b0) / (b1 - b0) * H
        iu = np.clip(u.astype(int), 0, W - 1); iv = np.clip(v.astype(int), 0, H - 1)
        s = img_arr[iv, iu]
        inside = (u >= 0) & (u < W) & (v >= 0) & (v < H)
        return s[..., :3], (s[..., 3] / 255.0) * inside
    return sample


# ============================================================================ bake
def face_dims(c, face):
    dx, dy, dz = [c['to'][i] - c['frm'][i] for i in range(3)]
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]


def face_local(c, face, S, T):
    f, t = c['frm'], c['to']; one = np.ones_like(S)
    if face == 'north': X, Y, Z = t[0] - S, t[1] - T, f[2] * one
    elif face == 'south': X, Y, Z = f[0] + S, t[1] - T, t[2] * one
    elif face == 'east': X, Y, Z = t[0] * one, t[1] - T, t[2] - S
    elif face == 'west': X, Y, Z = f[0] * one, t[1] - T, f[2] + S
    elif face == 'up': X, Y, Z = f[0] + S, t[1] * one, f[2] + T
    else: X, Y, Z = f[0] + S, f[1] * one, t[2] - T
    return np.stack([X, Y, Z], -1)


_BSPH = {}


def _bsph(c):
    k = c['uuid']
    if k not in _BSPH:
        R = rotmat(c['rot']); o = np.array(c['origin'])
        ctr = ((np.array(c['frm']) + np.array(c['to'])) / 2 - o) @ R.T + o
        _BSPH[k] = (ctr, 0.5 * float(np.linalg.norm(np.array(c['to']) - np.array(c['frm']))))
    return _BSPH[k]


def inside_any(pts, me):
    res = np.zeros(len(pts), bool)
    mc, mr = _bsph(me)
    for c in BONES[me['bone']]['cubes']:
        if c is me or c.get('pre') is not None:
            continue
        cc, cr = _bsph(c)
        if np.linalg.norm(cc - mc) > cr + mr + 0.02:
            continue
        R = rotmat(c['rot']); o = np.array(c['origin'])
        lp = (pts - o) @ R + o
        e = 1e-4
        res |= np.all((lp > np.array(c['frm']) + e) & (lp < np.array(c['to']) - e), axis=1)
    return res


def exposed_fraction(c, face):
    fw, fh = face_dims(c, face)
    gs = lambda L: np.r_[min(0.004, L * 0.01), np.linspace(0.05, 0.95, 14) * L, L - min(0.004, L * 0.01)]
    ss, tt = [a.ravel() for a in np.meshgrid(gs(fw), gs(fh))]
    Pl = face_local(c, face, ss, tt)
    R = rotmat(c['rot']); o = np.array(c['origin'])
    n = R @ np.array(NORMALS[face], float)
    P = (Pl - o) @ R.T + o + n * 0.01
    return 1.0 - float(inside_any(P, c).mean())


def bake_face(c, face, mats, D=None):
    fw, fh = face_dims(c, face)
    D = D or c['D']
    pw, ph = max(1, int(math.ceil(fw * D - 1e-6))), max(1, int(math.ceil(fh * D - 1e-6)))
    W_, H_ = pw * SS, ph * SS
    kx, ky = W_ / max(fw, 1e-6), H_ / max(fh, 1e-6)
    S = (np.arange(W_) + 0.5)[None, :].repeat(H_, 0) / kx
    T = (np.arange(H_) + 0.5)[:, None].repeat(W_, 1) / ky
    Pl = face_local(c, face, S, T)
    o = np.array(c['origin']); R = rotmat(c['rot'])
    P = (Pl - o) @ R.T + o
    n = R @ np.array(NORMALS[face], float)
    col, noise = mats[c['mat']]
    img = np.zeros((H_, W_, 4), float)
    if col is not None:
        img[..., :3] = col; img[..., 3] = 255
    glow = np.zeros((H_, W_, 4), float)
    fc = F(img, glow, P, n, S, T, fw, fh, (kx + ky) / 2, face, c)
    fc.L = Pl - (np.array(c['frm']) + np.array(c['to'])) / 2
    if c['decals']:
        c['decals'](fc)
    g = world_noise(P, 40, 11) * noise + world_noise(P, 7, 5) * noise * 0.6
    img[..., :3] += g[..., None]
    small = img.reshape(ph, SS, pw, SS, 4).mean((1, 3))
    gsm = glow.reshape(ph, SS, pw, SS, 4).mean((1, 3))
    if col is None:                       # alpha cutout (e.g. propeller blades)
        small[..., 3] = np.where(small[..., 3] > 127, 255, 0)
    return np.clip(small, 0, 255).astype(np.uint8), np.clip(gsm, 0, 255).astype(np.uint8)


def r4(v):
    return [round(float(a), 4) for a in v]


# ============================================================================ export
def export(OUT, MODEL, mats, anims, kind='entity', offset=(0, 0, 0), display=None, atw=2048, glow=False,
           low_D=4, low_frac=0.15, bounds=(2, 1), hidden_bones=(), bounds_offset=None):
    os.makedirs(OUT, exist_ok=True)
    OFF = np.array(offset, float)
    print('cubes', len(CUBES))
    regions, glows, face_tex = [], [], {}
    pre_seen = {}                             # identical pre-baked arrays (e.g. 3 copies of one battery) share a region
    nh = 0
    for c in CUBES:
        if c.get('pre') is not None:          # pre-baked faces (merged from another model)
            for face in FACES:
                pf = c['pre'].get(face)
                if pf is None:
                    face_tex[(c['uuid'], face)] = None; nh += 1
                    continue
                key = (id(pf[0]), id(pf[1]))
                if key not in pre_seen:
                    regions.append(pf[0]); glows.append(pf[1])
                    pre_seen[key] = len(regions) - 1
                face_tex[(c['uuid'], face)] = pre_seen[key]
            continue
        for face in FACES:
            ex = exposed_fraction(c, face)
            if ex == 0.0:
                face_tex[(c['uuid'], face)] = None; nh += 1
                continue
            a, g = bake_face(c, face, mats, D=low_D if ex < low_frac else None)
            regions.append(a); glows.append(g)
            face_tex[(c['uuid'], face)] = len(regions) - 1
    print('hidden faces', nh, 'visible', len(regions))
    order = sorted(range(len(regions)), key=lambda i: (-regions[i].shape[0], -regions[i].shape[1]))
    pos = {}; x = y = sh = 0
    for i in order:
        h, w = regions[i].shape[:2]
        if x + w > atw:
            x = 0; y += sh + 1; sh = 0
        pos[i] = (x, y); x += w + 1; sh = max(sh, h)
    need = y + sh
    ath = 256
    while ath < need:
        ath *= 2
    print('atlas', atw, ath, 'used', need)
    atlas = np.zeros((ath, atw, 4), np.uint8); gatlas = np.zeros((ath, atw, 4), np.uint8)
    for i, a in enumerate(regions):
        px, py = pos[i]
        atlas[py:py + a.shape[0], px:px + a.shape[1]] = a
        gatlas[py:py + a.shape[0], px:px + a.shape[1]] = glows[i]
    tex = Image.fromarray(atlas, 'RGBA'); tex.save(os.path.join(OUT, f'{MODEL}.png'), optimize=True)
    if glow:
        Image.fromarray(gatlas, 'RGBA').save(os.path.join(OUT, f'{MODEL}_glowmask.png'), optimize=True)

    def face_uv(c, face):
        i = face_tex[(c['uuid'], face)]
        if i is None:
            return None
        px, py = pos[i]; h, w = regions[i].shape[:2]
        return [px, py, px + w, py + h]

    sh_ = lambda v: list(np.array(v, float) + OFF)

    def _hidden(bn):
        while bn is not None:
            if bn in hidden_bones:
                return True
            bn = BONES[bn]['parent']
        return False
    elements = []
    for c in CUBES:
        faces = {}
        for f in FACES:
            u = face_uv(c, f)
            faces[f] = {'uv': u, 'texture': 0} if u is not None else {'uv': [0, 0, 0, 0], 'texture': None}
        el = {'name': c['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default',
              'allow_mirror_modeling': True, 'from': r4(sh_(c['frm'])), 'to': r4(sh_(c['to'])), 'autouv': 0, 'color': 0,
              'origin': r4(sh_(c['origin'])), 'faces': faces, 'type': 'cube', 'uuid': c['uuid'],
              'visibility': not _hidden(c['bone'])}
        if any(abs(a) > 1e-6 for a in c['rot']):
            el['rotation'] = r4(c['rot'])
        elements.append(el)

    def outliner_node(name):
        b = BONES[name]
        node = {'name': name, 'origin': r4(sh_(b['pivot'])), 'color': 0, 'uuid': b['uuid'], 'export': True,
                'mirror_uv': False, 'isOpen': b['parent'] is None or BONES[b['parent']]['parent'] is None,
                'locked': False, 'visibility': not _hidden(name), 'autouv': 0, 'children': [c['uuid'] for c in b['cubes']]}
        if b.get('rot') is not None:
            node['rotation'] = r4(b['rot'])
        for ch in ORDER:
            if BONES[ch]['parent'] == name:
                node['children'].append(outliner_node(ch))
        return node

    def png_b64(img):
        buf = io.BytesIO(); img.save(buf, 'PNG', optimize=True)
        return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

    bb_anims = []
    for an, a in anims.items():
        animators = {}
        for bn, chans in a['bones'].items():
            kfs = [{'channel': ch, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': uid(), 'time': t,
                    'color': -1, 'interpolation': ip} for ch, keys in chans.items() for t, v, ip in keys]
            animators[BONES[bn]['uuid']] = {'name': bn, 'type': 'bone', 'keyframes': kfs}
        bb_anims.append({'uuid': uid(), 'name': an, 'loop': a['loop'], 'override': False, 'length': a['length'],
                         'snapping': 24, 'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '',
                         'loop_delay': '', 'animators': animators})
    folder = kind if kind in ('item', 'block') else 'entity'
    roots = [n for n in ORDER if BONES[n]['parent'] is None]
    bbmodel = {
        'meta': {'format_version': '4.5', 'model_format': 'animated_entity_model', 'box_uv': False},
        'name': MODEL, 'model_identifier': MODEL, 'geckolib_model_type': kind,
        'visible_box': [bounds[0], bounds[1], 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [],
        'timeline_setups': [], 'unhandled_root_fields': {}, 'resolution': {'width': atw, 'height': ath},
        'elements': elements, 'outliner': [outliner_node(r) for r in roots],
        'textures': [{'path': '', 'name': f'{MODEL}.png', 'folder': folder, 'namespace': '', 'id': '0', 'width': atw,
                      'height': ath, 'uv_width': atw, 'uv_height': ath, 'particle': kind == 'item', 'use_as_default': False,
                      'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default', 'render_sides': 'auto',
                      'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
                      'visible': True, 'internal': True, 'saved': False, 'uuid': uid(), 'relative_path': '',
                      'source': png_b64(tex)}],
        'animations': bb_anims,
    }
    if display:
        bbmodel['display'] = display
    json.dump(bbmodel, open(os.path.join(OUT, f'{MODEL}.bbmodel'), 'w', encoding='utf-8'), separators=(',', ':'))

    geo_bones = []
    for name in ORDER:
        b = BONES[name]; p = sh_(b['pivot'])
        gb = {'name': name}
        if b['parent']:
            gb['parent'] = b['parent']
        gb['pivot'] = r4([-p[0], p[1], p[2]])
        if b.get('rot') is not None:
            gb['rotation'] = r4([-b['rot'][0], -b['rot'][1], b['rot'][2]])
        cubes = []
        for c in b['cubes']:
            frm, to, org = sh_(c['frm']), sh_(c['to']), sh_(c['origin'])
            size = [to[i] - frm[i] for i in range(3)]
            cube = {'origin': r4([-to[0], frm[1], frm[2]]), 'size': r4(size)}
            if any(abs(a) > 1e-6 for a in c['rot']):
                cube['pivot'] = r4([-org[0], org[1], org[2]])
                cube['rotation'] = r4([-c['rot'][0], -c['rot'][1], c['rot'][2]])
            uv = {}
            for f in FACES:
                u = face_uv(c, f)
                if u is None:
                    continue
                w, h = u[2] - u[0], u[3] - u[1]
                uv[f] = ({'uv': [u[0] + w, u[1] + h], 'uv_size': [-w, -h]} if f in ('up', 'down')
                         else {'uv': [u[0], u[1]], 'uv_size': [w, h]})
            cube['uv'] = uv
            cubes.append(cube)
        if cubes:
            gb['cubes'] = cubes
        geo_bones.append(gb)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{MODEL}', 'texture_width': atw, 'texture_height': ath,
                        'visible_bounds_width': bounds[0], 'visible_bounds_height': bounds[1],
                        'visible_bounds_offset': bounds_offset or [0, 0.25 if kind != 'item' else 0, 0]},
        'bones': geo_bones}]}
    json.dump(geo, open(os.path.join(OUT, f'{MODEL}.geo.json'), 'w', encoding='utf-8'), indent=1)

    aj = {'format_version': '1.8.0', 'animations': {}}
    for an, a in anims.items():
        e = {}
        if a['loop'] == 'loop':
            e['loop'] = True
        elif a['loop'] == 'hold':
            e['loop'] = 'hold_on_last_frame'
        e['animation_length'] = a['length']
        bj = {}
        for bn, chans in a['bones'].items():
            bj[bn] = {}
            for ch, keys in chans.items():
                bj[bn][ch] = {repr(float(t)): ({'post': v, 'lerp_mode': ip} if ip == 'catmullrom' else v) for t, v, ip in keys}
        e['bones'] = bj
        aj['animations'][an] = e
    json.dump(aj, open(os.path.join(OUT, f'{MODEL}.animation.json'), 'w', encoding='utf-8'), indent=1)
    if kind == 'item':
        json.dump({'parent': 'builtin/entity', 'display': display or {}},
                  open(os.path.join(OUT, f'{MODEL}.item.json'), 'w'), indent=1)

    corners = []
    for c in CUBES:
        R = rotmat(c['rot']); o = np.array(c['origin'])
        for xx in (c['frm'][0], c['to'][0]):
            for yy in (c['frm'][1], c['to'][1]):
                for zz in (c['frm'][2], c['to'][2]):
                    corners.append((np.array([xx, yy, zz]) - o) @ R.T + o + OFF)
    corners = np.array(corners)
    meta = {'kind': kind, 'cubes': len(CUBES), 'atlas': [atw, ath], 'offset': list(OFF),
            'bones': {n: dict(parent=BONES[n]['parent'], pivot_bb=r4(sh_(BONES[n]['pivot'])), cubes=len(BONES[n]['cubes']))
                      for n in ORDER},
            'bbox_min': r4(corners.min(0)), 'bbox_max': r4(corners.max(0)),
            'animations': {k: dict(loop=v['loop'], length=v['length'], bones=list(v['bones'])) for k, v in anims.items()}}
    json.dump(meta, open(os.path.join(OUT, 'meta.json'), 'w'), indent=1)
    print('bbox', meta['bbox_min'], meta['bbox_max'])
    return meta


# ============================================================================ item display preview helpers
def display_matrix(d):
    """vanilla ModelTransformation: translate, then rotationXYZ (= Rx*Ry*Rz), then scale."""
    rx, ry, rz = [math.radians(a) for a in d.get('rotation', [0, 0, 0])]
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Rx @ Ry @ Rz * np.array(d.get('scale', [1, 1, 1]))[None, :]
