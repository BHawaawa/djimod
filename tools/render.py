# Software renderer for verifying the .bbmodel (Blockbench semantics) and geo.json (GeckoLib semantics)
import json, math, sys, base64, io
import numpy as np
from PIL import Image


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


# face -> corner order (TL, TR, BR, BL as seen from outside) in terms of (x,y,z) min/max picks
def face_quad(f, t, face):
    x0, y0, z0 = f; x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east':  [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west':  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up':    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down':  [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def load_bbmodel(path, pose=None):
    m = json.load(open(path))
    src = m['textures'][0]['source'].split(',', 1)[1]
    tex = np.asarray(Image.open(io.BytesIO(base64.b64decode(src))).convert('RGBA'))
    TW, TH = m['resolution']['width'], m['resolution']['height']
    els = {e['uuid']: e for e in m['elements']}
    quads = []
    pose = pose or {}

    def walk(node, M):
        # bone pose rotation (bedrock values -> blockbench: negate x,y)
        o = np.array(node['origin'], float)
        rest = np.array(node.get('rotation', [0, 0, 0]), float)
        if node['name'] in pose:
            r = pose[node['name']]
            rest = rest + np.array([-r[0], -r[1], r[2]], float)
        if np.any(np.abs(rest) > 1e-9):
            R = rotmat(list(rest))
            T = np.eye(4); T[:3, :3] = R; T[:3, 3] = o - R @ o
            M = M @ T
        for ch in node['children']:
            if isinstance(ch, str):
                e = els[ch]
                R = rotmat(e.get('rotation', [0, 0, 0])); oo = np.array(e['origin'], float)
                for face, fd in e['faces'].items():
                    if fd.get('texture') is None:
                        continue
                    q = np.array(face_quad(e['from'], e['to'], face), float)
                    q = (q - oo) @ R.T + oo
                    q = (np.c_[q, np.ones(4)] @ M.T)[:, :3]
                    u1, v1, u2, v2 = fd['uv']
                    uv = np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)], float) / [TW, TH]
                    quads.append((q, uv))
            else:
                walk(ch, M)
    for n in m['outliner']:
        walk(n, np.eye(4))
    return quads, tex


def load_geo(path, texpath):
    g = json.load(open(path))['minecraft:geometry'][0]
    TW, TH = g['description']['texture_width'], g['description']['texture_height']
    tex = np.asarray(Image.open(texpath).convert('RGBA'))
    quads = []
    bmap = {b['name']: b for b in g['bones']}
    mats = {}

    def bmat(name):
        if name in mats:
            return mats[name]
        b = bmap[name]
        M = bmat(b['parent']) if b.get('parent') else np.eye(4)
        r = b.get('rotation')
        if r:
            pv = np.array([-b['pivot'][0], b['pivot'][1], b['pivot'][2]], float)
            R = rotmat([-r[0], -r[1], r[2]])
            T = np.eye(4); T[:3, :3] = R; T[:3, 3] = pv - R @ pv
            M = M @ T
        mats[name] = M
        return M
    for b in g['bones']:
        BM = bmat(b['name'])
        for c in b.get('cubes', []):
            size = np.array(c['size'], float)
            o = np.array(c['origin'], float)
            frm = np.array([-(o[0] + size[0]), o[1], o[2]])   # back to blockbench space
            to = frm + size
            rot = c.get('rotation', [0, 0, 0]); rot = [-rot[0], -rot[1], rot[2]]
            pv = c.get('pivot', [0, 0, 0]); pv = np.array([-pv[0], pv[1], pv[2]], float)
            R = rotmat(rot)
            for face, fd in c['uv'].items():
                q = np.array(face_quad(frm, to, face), float)
                q = (q - pv) @ R.T + pv
                q = (np.c_[q, np.ones(4)] @ BM.T)[:, :3]
                (u, v), (w, h) = fd['uv'], fd['uv_size']
                if face in ('up', 'down'):
                    u, v, w, h = u + w, v + h, -w, -h
                uv = np.array([(u, v), (u + w, v), (u + w, v + h), (u, v + h)], float) / [TW, TH]
                quads.append((q, uv))
    return quads, tex


L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


def render(quads, tex, view_R, size=900, scale=26.0, center=(0, 2.5, 0), bg=(238, 238, 238), cull=False, height=None):
    W = size; H = height or size
    img = np.zeros((H, W, 3), float) + bg
    zb = np.full((H, W), -1e9)
    TH, TW = tex.shape[:2]
    c = np.array(center, float)
    for q, uv in quads:
        n = np.cross(q[1] - q[0], q[3] - q[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        light = min(1.0, 0.4 + 0.6 * (max(0, n @ L0) + max(0, n @ L1)))
        light_b = min(1.0, 0.4 + 0.6 * (max(0, -n @ L0) + max(0, -n @ L1)))
        p = (q - c) @ view_R.T
        sxy = np.c_[W / 2 + p[:, 0] * scale, H / 2 - p[:, 1] * scale]
        zz = p[:, 2]
        facing = (np.cross(np.r_[sxy[1] - sxy[0], 0], np.r_[sxy[3] - sxy[0], 0])[2]) < 0
        if cull and facing:   # quads are wound clockwise: "facing" here means the back side
            continue
        lt = light if facing else light_b
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = sxy[list(tri)]; Z = zz[list(tri)]; UV = uv[list(tri)]
            x0, y0 = np.floor(P.min(0)).astype(int); x1, y1 = np.ceil(P.max(0)).astype(int)
            x0, y0 = max(x0, 0), max(y0, 0); x1, y1 = min(x1, W - 1), min(y1, H - 1)
            if x1 < x0 or y1 < y0:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (P[1, 1] - P[2, 1]) * (P[0, 0] - P[2, 0]) + (P[2, 0] - P[1, 0]) * (P[0, 1] - P[2, 1])
            if abs(d) < 1e-9:
                continue
            a = ((P[1, 1] - P[2, 1]) * (xs - P[2, 0]) + (P[2, 0] - P[1, 0]) * (ys - P[2, 1])) / d
            b = ((P[2, 1] - P[0, 1]) * (xs - P[2, 0]) + (P[0, 0] - P[2, 0]) * (ys - P[2, 1])) / d
            cc = 1 - a - b
            inside = (a >= -1e-6) & (b >= -1e-6) & (cc >= -1e-6)
            if not inside.any():
                continue
            z = a * Z[0] + b * Z[1] + cc * Z[2]
            u = a * UV[0, 0] + b * UV[1, 0] + cc * UV[2, 0]
            v = a * UV[0, 1] + b * UV[1, 1] + cc * UV[2, 1]
            tx = np.clip((u * TW).astype(int), 0, TW - 1); ty = np.clip((v * TH).astype(int), 0, TH - 1)
            texel = tex[ty, tx]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (z > sub) & (texel[..., 3] > 25)
            sub[ok] = z[ok]
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok] = texel[ok][:, :3] * lt
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def view(yaw, pitch):
    """camera rotation: yaw around Y, then pitch around X. returns matrix world->view (x right,y up,z toward viewer)."""
    return rotmat([pitch, 0, 0]) @ rotmat([0, yaw, 0])


VIEWS = {
    'top': rotmat([90, 0, 0]),                      # looking down, -Z (front) at top of image
    'bottom': rotmat([0, 0, 180]) @ rotmat([-90, 0, 0]),
    'front': rotmat([0, 180, 0]),                   # viewer at -Z looking +Z
    'rear': rotmat([0, 0, 0]),
    'side': rotmat([0, -90, 0]),                    # viewer at +X
    'persp_front': view(180 + 35, 28),
    'persp_rear': view(-30, 28),
    'low_side': view(180 + 70, 8),
}

if __name__ == '__main__':
    mode = sys.argv[1]
    out = sys.argv[2]
    if mode == 'bb':
        pose = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None
        quads, tex = load_bbmodel(sys.argv[4] if len(sys.argv) > 4 else 'build/avata360.bbmodel', pose)
    else:
        quads, tex = load_geo('build/avata360.geo.json', 'build/avata360.png')
    names = sys.argv[5].split(',') if len(sys.argv) > 5 else list(VIEWS)
    for nme in names:
        render(quads, tex, VIEWS[nme]).save(f'{out}_{nme}.png')
    print(len(quads), 'quads')
