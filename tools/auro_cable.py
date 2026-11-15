# -*- coding: utf-8 -*-
"""Retractable 140 W cable of the DJI Power AURO 2000 ELITE as a GeckoLib bone chain.

Chain:  root -> plug_yaw (yaw only + translation) -> plug (remaining rotation) -> cab_01 -> cab_02 -> ... -> cab_NN
Every cab_i is a rigid flat-ribbon segment of length LS whose pivot is its first joint.  The rest pose (no animation)
is the STOWED cable: plug lying in the front slot (tip -> -X), the ribbon going +X to the exit slit, turning into the
body (+Z) and winding on an internal reel (helix about X) so it is hidden inside the shell.

For any plug pose the solver builds a path  plug tail -> (Bezier + gravity sag, clamped to the ground and kept in front
of the station) -> exit slit K -> internal lead -> reel helix, walks the joints along it at exact chord length LS,
assigns minimal-twist frames (twist distributed so the ribbon passes the slit upright) and converts every joint into a
relative rotation.  Animations are sampled at 20 Hz (one key per game tick) so GeckoLib's linear interpolation between
keys stays within a fraction of a pixel.
"""
import math
import numpy as np

LS, N = 3.0, 48               # segment length (px) / number of segments  -> 144 px = 576 mm of ribbon
CW, CT = 2.4, 0.55            # ribbon width / thickness (px)  (9.6 mm x 2.2 mm)
PT_REST = (1.2, 50.0, -27.8)  # plug tail (= cable start) in the stowed pose
K = np.array([3.3, 50.0, -27.0])          # exit slit on the slot floor (cable enters +Z here)
LEAD_END = np.array([3.3, 50.0, -12.0])   # internal lead end = top of the reel
REEL_C = (40.0, -12.0)                    # reel centre (y, z), axis along X
REEL_R, REEL_PITCH = 10.0, 0.9
FRONT_Z = -29.12              # cable centre may not go behind this plane in front of the panel
GROUND_Y = CT / 2 + 0.02
HZ = 40                       # sample rate (keys per second)
DEVICE = (-14.0, 0.0, -85.5)  # where the charged device sits (model px, Blockbench coords)

TIP_REST = np.array([-1.0, 0.0, 0.0])
FN_REST = np.array([0.0, 0.0, -1.0])      # label normal of the plug (front in the slot)
PLUG_LEN = 13.2               # tail -> tip end


# ============================================================================ small math
def nrm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def rotmat(r):
    rx, ry, rz = [math.radians(a) for a in r]
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def euler_cands(R):
    """all (a,b,c) with R = Rz(c) Ry(b) Rx(a) (degrees), two branches."""
    sb = max(-1.0, min(1.0, -R[2, 0]))
    b = math.degrees(math.asin(sb))
    if abs(abs(sb) - 1) < 1e-7:
        a = 0.0
        c = math.degrees(math.atan2(-R[0, 1], R[1, 1]))
        return [(a, b, c)]
    a = math.degrees(math.atan2(R[2, 1], R[2, 2])); c = math.degrees(math.atan2(R[1, 0], R[0, 0]))
    return [(a, b, c), (a + 180.0, 180.0 - b, c + 180.0)]


def euler_bb(M):
    a, b, c = euler_cands(np.asarray(M, float))[0]
    return [round(((v + 180) % 360) - 180, 4) for v in (a, b, c)]


def nearest(c, prev):
    out = []
    for v, p in zip(c, prev):
        out.append(v + 360.0 * round((p - v) / 360.0))
    return out


def anim_euler(R, prev, rest=(0.0, 0.0, 0.0)):
    """Bedrock animation value for a bone whose total Blockbench rotation must be R.
    GeckoLib: total euler = rest + (-v0, -v1, v2)  ->  v = (-(a - r0), -(b - r1), c - r2); continuous w.r.t. prev."""
    best, bd = None, 1e18
    for a, b, c in euler_cands(R):
        v = nearest([-(a - rest[0]), -(b - rest[1]), c - rest[2]], prev)
        d = sum(abs(x - y) for x, y in zip(v, prev))
        if d < bd:
            best, bd = v, d
    # gimbal: redistribute a/c to stay close to prev
    return best


def rodrigues(a, b):
    """rotation taking unit vector a to unit vector b (minimal)."""
    v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -0.999999:
        ax = np.cross(a, [1, 0, 0])
        if np.linalg.norm(ax) < 1e-6:
            ax = np.cross(a, [0, 1, 0])
        ax = nrm(ax)
        return 2 * np.outer(ax, ax) - np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


def axis_angle(ax, ang):
    ax = nrm(ax); x, y, z = ax
    c, s = math.cos(ang), math.sin(ang); C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


# ---- quaternions (w, x, y, z)
def q_from_m(M):
    t = np.trace(M)
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        q = [0.25 * s, (M[2, 1] - M[1, 2]) / s, (M[0, 2] - M[2, 0]) / s, (M[1, 0] - M[0, 1]) / s]
    elif M[0, 0] > M[1, 1] and M[0, 0] > M[2, 2]:
        s = math.sqrt(1.0 + M[0, 0] - M[1, 1] - M[2, 2]) * 2
        q = [(M[2, 1] - M[1, 2]) / s, 0.25 * s, (M[0, 1] + M[1, 0]) / s, (M[0, 2] + M[2, 0]) / s]
    elif M[1, 1] > M[2, 2]:
        s = math.sqrt(1.0 + M[1, 1] - M[0, 0] - M[2, 2]) * 2
        q = [(M[0, 2] - M[2, 0]) / s, (M[0, 1] + M[1, 0]) / s, 0.25 * s, (M[1, 2] + M[2, 1]) / s]
    else:
        s = math.sqrt(1.0 + M[2, 2] - M[0, 0] - M[1, 1]) * 2
        q = [(M[1, 0] - M[0, 1]) / s, (M[0, 2] + M[2, 0]) / s, (M[1, 2] + M[2, 1]) / s, 0.25 * s]
    return nrm(q)


def m_from_q(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def slerp(q0, q1, t):
    d = float(np.dot(q0, q1))
    if d < 0:
        q1 = -q1; d = -d
    if d > 0.9995:
        return nrm(q0 + (q1 - q0) * t)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * q0 + math.sin(t * th) * q1) / math.sin(th)


# ============================================================================ plug pose
def plug_rot(tip, fn):
    """rotation taking the rest plug frame (tip, fn, w) to the target one."""
    tip = nrm(tip)
    fn = nrm(np.asarray(fn, float) - np.dot(fn, tip) * tip)
    w = np.cross(fn, tip)
    w_r = np.cross(FN_REST, TIP_REST)
    A = np.stack([tip, fn, w], 1); B = np.stack([TIP_REST, FN_REST, w_r], 1)
    return A @ B.T


# ============================================================================ paths
def bezier(p0, p1, p2, p3, n=60):
    t = np.linspace(0, 1, n)[:, None]
    return ((1 - t) ** 3) * p0 + 3 * ((1 - t) ** 2) * t * p1 + 3 * (1 - t) * (t ** 2) * p2 + (t ** 3) * p3


def internal_path():
    pts = [K + (LEAD_END - K) * s for s in np.linspace(0, 1, 16)[1:]]
    yc, zc = REEL_C
    for ph in np.linspace(0, 2 * math.pi * 3.2, 400)[1:]:
        pts.append(np.array([LEAD_END[0] + REEL_PITCH * ph / (2 * math.pi),
                             yc + REEL_R * math.cos(ph), zc + REEL_R * math.sin(ph)]))
    return np.array(pts)


INTERNAL = internal_path()


def in_slot_zone(p):
    return -12.5 - 0.5 < p[0] < 3.8 + 0.6 and 47.0 < p[1] < 53.0


def external_path(pt, tip, sag_k):
    pt = np.asarray(pt, float); d0 = -nrm(tip)
    chord = float(np.linalg.norm(K - pt))
    a = float(np.clip(0.38 * chord, 0.7, 20.0)); b = float(np.clip(0.30 * chord, 0.7, 11.0))
    P = bezier(pt, pt + d0 * a, K - np.array([0, 0, 1.0]) * b, K, n=max(24, int(chord * 1.5)))
    u = np.linspace(0, 1, len(P))
    sag = sag_k * max(0.0, chord - 5.0)
    P[:, 1] -= sag * 4 * u * (1 - u) * (1 - u * 0.35)
    for it in range(4):
        P[:, 1] = np.maximum(P[:, 1], GROUND_Y)
        for i, p in enumerate(P):
            if abs(p[0]) < 53.9 and p[1] < 61.6 and p[2] > FRONT_Z and not in_slot_zone(p):
                if p[2] < -20:                         # in front -> push forward onto the panel plane
                    P[i, 2] = FRONT_Z
        if it < 3:
            Q = P.copy()
            Q[1:-1] = P[1:-1] * 0.5 + (P[:-2] + P[2:]) * 0.25
            Q[1:4] = P[1:4]; Q[-4:] = P[-4:]                  # keep plug & slit tangents
            P = Q
    return P


def walk_joints(path, start):
    """joints at exact distance LS along the polyline (first joint = start = path[0])."""
    J = [np.asarray(start, float)]
    seg_i = 0
    cur = J[0]
    while len(J) < N + 1:
        found = False
        while seg_i < len(path) - 1:
            a, b = path[seg_i], path[seg_i + 1]
            # does the sphere |x - cur| = LS intersect segment a->b beyond the current position?
            d = b - a; f = a - cur
            A = d @ d; B = 2 * f @ d; C = f @ f - LS * LS
            disc = B * B - 4 * A * C
            if A > 1e-12 and disc >= 0:
                r = math.sqrt(disc)
                for t in sorted(((-B - r) / (2 * A), (-B + r) / (2 * A))):
                    if 0 <= t <= 1 and np.dot(a + d * t - cur, d) > 0:
                        cur = a + d * t
                        J.append(cur); found = True
                        break
            if found:
                path = np.vstack([cur[None], path[seg_i + 1:]]); seg_i = 0
                break
            seg_i += 1
        if not found:
            raise RuntimeError('cable path too short')
    return np.array(J)


_PHI = [0.0]


def frames(J, w0, slit_fix=True):
    """minimal-twist frames [t, w, n] per segment; twist spread so the ribbon is upright (w = +-Y) at the slit."""
    T = [nrm(J[i + 1] - J[i]) for i in range(N)]
    W = []
    w = nrm(w0 - np.dot(w0, T[0]) * T[0])
    for i in range(N):
        if i > 0:
            w = rodrigues(T[i - 1], T[i]) @ w
            w = nrm(w - np.dot(w, T[i]) * T[i])
        W.append(w)
    if slit_fix:
        # first segment that starts at / behind the slit plane (inside the body)
        ks = next((i for i in range(N) if J[i][2] >= K[2] - 0.05 and np.linalg.norm(J[i] - K) < LS * 1.5), None)
        if ks is None:
            ks = int(np.argmin([np.linalg.norm(J[i] - K) for i in range(N)]))
        t = T[ks]
        want = np.array([0, 1.0, 0]); want = nrm(want - np.dot(want, t) * t)
        phi = math.atan2(np.dot(np.cross(W[ks], want), t), np.dot(W[ks], want))
        # upright either way (+Y or -Y): pick the branch closest to the previous sample's twist
        phi = min((phi + k * math.pi for k in range(-3, 4)), key=lambda p: abs(p - _PHI[0]))
        _PHI[0] = phi
        for i in range(N):
            f = min(1.0, i / max(ks, 1))
            W[i] = axis_angle(T[i], phi * f) @ W[i]
        # internal part: re-transport from the corrected slit frame
        for i in range(ks + 1, N):
            w = rodrigues(T[i - 1], T[i]) @ W[i - 1]
            W[i] = nrm(w - np.dot(w, T[i]) * T[i])
    return [np.stack([T[i], W[i], np.cross(T[i], W[i])], 1) for i in range(N)]


def solve(pt, tip, fn, sag_k):
    ext = external_path(pt, tip, sag_k)
    path = np.vstack([ext, INTERNAL])
    J = walk_joints(path, pt)
    Rp = plug_rot(tip, fn)
    w0 = Rp @ np.array([0, 1.0, 0])
    F = frames(J, w0)
    return J, F, Rp, ext


REST_J = REST_F = REST_E = DESIGN_J = None
# design (modelling) frame of every segment: cubes are built as a straight vertical chain (tangent +Y) so that the
# per-joint animation rotations are bends about X/Z (never near the ZYX-euler gimbal); each bone carries a rest
# rotation that folds the straight design chain into the stowed reel shown in Blockbench.
F_D = np.stack([[0, 1.0, 0], [1.0, 0, 0], [0, 0, -1.0]], 1)


def build_rest():
    global REST_J, REST_F, REST_E, DESIGN_J
    REST_J, REST_F, _, _ = solve(PT_REST, TIP_REST, FN_REST, 0.0)
    DESIGN_J = np.array([np.array(PT_REST) + np.array([0, LS * i, 0]) for i in range(N + 1)])
    REST_E = []
    Qp = np.eye(3)
    for i in range(N):
        Q = REST_F[i] @ F_D.T
        a, b, c = euler_cands(Qp.T @ Q)[0]
        REST_E.append([round(((v + 180) % 360) - 180, 4) for v in (a, b, c)])
        Qp = rotmat(REST_E[-1]) if i == 0 else Qp @ rotmat(REST_E[-1])



# ============================================================================ animation sampling
def catmull(P, t, T):
    """Catmull-Rom position through keys P at times T."""
    if t <= T[0]:
        return P[0]
    if t >= T[-1]:
        return P[-1]
    i = max(k for k in range(len(T) - 1) if T[k] <= t)
    a = (t - T[i]) / (T[i + 1] - T[i])
    p0 = P[i - 1] if i > 0 else P[i]; p1, p2 = P[i], P[i + 1]
    p3 = P[i + 2] if i + 2 < len(P) else P[i + 1]
    a2, a3 = a * a, a * a * a
    return 0.5 * (2 * p1 + (-p0 + p2) * a + (2 * p0 - 5 * p1 + 4 * p2 - p3) * a2 + (-p0 + 3 * p1 - 3 * p2 + p3) * a3)


def smooth(a):
    return a * a * (3 - 2 * a)


def plug_pose(keys, t):
    T = [k[0] for k in keys]
    P = [np.array(k[1], float) for k in keys]
    pos = catmull(P, t, T)
    if t >= T[-1]:
        i, a = len(T) - 2, 1.0
    else:
        i = max(k for k in range(len(T) - 1) if T[k] <= t); a = smooth((t - T[i]) / (T[i + 1] - T[i]))
    q0 = q_from_m(plug_rot(keys[i][2], keys[i][3])); q1 = q_from_m(plug_rot(keys[i + 1][2], keys[i + 1][3]))
    R = m_from_q(slerp(q0, q1, a))
    sag = keys[i][4] + (keys[i + 1][4] - keys[i][4]) * a
    return pos, R, sag


def pose_values(pos, Rp, sag, prev):
    """-> dict bone -> {channel: value} (bedrock), plus joints."""
    tip = Rp @ TIP_REST; fn = Rp @ FN_REST
    J, F, _, ext = solve(pos, tip, fn, sag)
    out = {}
    th_prev = -prev.get('plug_yaw', {}).get('rotation', [0, 0, 0])[1]
    h = math.hypot(tip[0], tip[2])
    th = math.degrees(math.atan2(tip[2], -tip[0])) if h > 0.25 else th_prev
    th = th + 360.0 * round((th_prev - th) / 360.0)
    Ry = rotmat([0, th, 0])
    d = np.asarray(pos, float) - np.array(PT_REST)
    out['plug_yaw'] = {'position': [round(-d[0], 4), round(d[1], 4), round(d[2], 4)], 'rotation': [0.0, round(-th, 4), 0.0]}
    out['plug'] = {'rotation': anim_euler(Ry.T @ Rp, prev.get('plug', {}).get('rotation', [0, 0, 0]))}
    Qprev = Rp
    for i in range(N):
        Q = F[i] @ F_D.T
        R = Qprev.T @ Q
        nm = f'cab_{i + 1:02d}'
        out[nm] = {'rotation': anim_euler(R, prev.get(nm, {}).get('rotation', [0, 0, 0]), REST_E[i])}
        Qprev = Q
    for b in out.values():
        b['rotation'] = [round(v, 3) for v in b['rotation']]
    return out, J


def sample(keys, length, prev, phi0=0.0):
    _PHI[0] = phi0
    times = [round(k / HZ, 4) for k in range(int(round(length * HZ)) + 1)]
    seq = []
    for t in times:
        pos, R, sag = plug_pose(keys, t)
        vals, J = pose_values(pos, R, sag, prev)
        seq.append((t, vals, J))
        prev = vals
    return seq


def to_anim(seq, loop='hold'):
    bones = {}
    for t, vals, _ in seq:
        for bn, chans in vals.items():
            for ch, v in chans.items():
                bones.setdefault(bn, {}).setdefault(ch, []).append((t, list(v), 'linear'))
    # drop redundant keys (identical neighbours)
    for bn in bones:
        for ch in bones[bn]:
            ks = bones[bn][ch]
            keep = [ks[0]]
            for i in range(1, len(ks) - 1):
                if not (np.allclose(ks[i][1], ks[i - 1][1], atol=1e-3) and np.allclose(ks[i][1], ks[i + 1][1], atol=1e-3)):
                    keep.append(ks[i])
            if len(ks) > 1:
                keep.append(ks[-1])
            bones[bn][ch] = keep
    return dict(loop=loop, length=seq[-1][0], bones=bones)


# ---- key poses: (time, plug-tail position, tip direction, label normal, sag factor)
REST = (PT_REST, TIP_REST, FN_REST)
OUT_P = ((-8.0, 0.8, -58.0), (0, 0, -1), (0, 1, 0))
PLUGGED_P = ((DEVICE[0], 3.0, -63.4), (0, 0, -1), (0, 1, 0))
PRE_P = ((DEVICE[0], 3.0, -58.4), (0, 0, -1), (0, 1, 0))

EXTRACT = [
    (0.00, *REST, 0.0),
    (0.18, (1.2, 50.0, -31.2), TIP_REST, FN_REST, 0.0),
    (0.45, (-0.5, 53.0, -40.0), (-0.55, 0.15, -1), (0, 0.55, -0.85), 0.12),
    (0.85, (-5.0, 33.0, -53.0), (-0.1, -0.45, -1), (0, 1, -0.4), 0.3),
    (1.22, (-7.6, 3.0, -58.6), (0, -0.06, -1), (0, 1, 0), 0.45),
    (1.42, *OUT_P, 0.5),
    (1.60, *OUT_P, 0.5),
]
RETRACT = [
    (0.00, *OUT_P, 0.5),
    (0.40, (-6.0, 0.8, -42.0), (0, 0, -1), (0, 1, 0), 0.5),
    (0.75, (-2.0, 31.0, -37.0), (-0.45, 0.25, -1), (0, 0.75, -0.65), 0.25),
    (1.05, (1.2, 50.0, -31.4), TIP_REST, FN_REST, 0.0),
    (1.25, *REST, 0.0),
    (1.35, *REST, 0.0),
]
PLUG = [
    (0.00, *OUT_P, 0.5),
    (0.35, (-11.0, 6.5, -56.0), (0, -0.2, -1), (0, 1, 0), 0.5),
    (0.75, *PRE_P, 0.5),
    (1.00, *PLUGGED_P, 0.5),
    (1.10, *PLUGGED_P, 0.5),
]
UNPLUG = [
    (0.00, *PLUGGED_P, 0.5),
    (0.25, *PRE_P, 0.5),
    (0.55, (-11.0, 5.0, -57.0), (0, -0.15, -1), (0, 1, 0), 0.5),
    (0.85, *OUT_P, 0.5),
    (0.95, *OUT_P, 0.5),
]

_CACHE = {}


def animations():
    zero = {}
    ext = sample(EXTRACT, 1.60, zero)
    out_vals = ext[-1][1]
    ret = sample(RETRACT, 1.35, out_vals)
    plg = sample(PLUG, 1.10, out_vals)
    plugged_vals = plg[-1][1]
    unp = sample(UNPLUG, 0.95, plugged_vals)
    # shift + right click while charging: unplug and retract in one go (continuous euler branches)
    ret2 = sample(RETRACT, 1.35, unp[-1][1])
    unp_ret = unp + [(round(t + 0.95, 4), v, J) for t, v, J in ret2[1:]]
    rest_vals, _ = pose_values(np.array(PT_REST), plug_rot(TIP_REST, FN_REST), 0.0, zero)
    _CACHE.update(extract=ext, retract=ret, plug=plg, unplug=unp, unplug_retract=unp_ret)
    A = {
        'animation.auro2000elite.cable_stowed': to_anim([(0.0, rest_vals, None), (0.05, rest_vals, None)]),
        'animation.auro2000elite.cable_out': to_anim([(0.0, out_vals, None), (0.05, out_vals, None)]),
        'animation.auro2000elite.cable_plugged': to_anim([(0.0, plugged_vals, None), (0.05, plugged_vals, None)]),
        'animation.auro2000elite.cable_extract': to_anim(ext),
        'animation.auro2000elite.cable_retract': to_anim(ret),
        'animation.auro2000elite.cable_plug': to_anim(plg),
        'animation.auro2000elite.cable_unplug': to_anim(unp),
        'animation.auro2000elite.cable_unplug_retract': to_anim(unp_ret),
    }
    return A


def info():
    ext_len = {}
    for k, seq in _CACHE.items():
        ext_len[k] = max(int(np.sum([1 for j in s[2] if j[2] < K[2] - 0.05])) for s in seq)
    return dict(segments=N, segment_len_px=LS, ribbon_px=[CW, CT], plug_tail_rest=list(PT_REST), exit_slit=list(K),
                device_px=list(DEVICE), plugged_tip_px=list(np.array(PLUGGED_P[0]) + np.array([0, 0, -PLUG_LEN])),
                max_segments_outside=ext_len, sample_hz=HZ)
