# -*- coding: utf-8 -*-
"""GIFs / stills of the Redmi K90: screen states and charging from the AURO 2000 ELITE retractable cable.
usage: python3 make_k90_gifs.py <k90.bbmodel> <auro.bbmodel> <out_dir> <what>
  what = states | plug | still
Frames are cached in /tmp/k90_frames (resumable; bash calls are time limited)."""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw
from render import render, rotmat, view
from render_anim import Model
import k90_art as A

KP, AP, OUT, WHAT = sys.argv[1:5]
os.makedirs(OUT, exist_ok=True)
CACHE = '/tmp/k90_frames'; os.makedirs(CACHE, exist_ok=True)
N = 'animation.redmi_k90.'
ph = Model(KP)
LAY = json.load(open(os.path.join(os.path.dirname(KP), 'screen_layout.json'), encoding='utf-8'))
SW, SH = LAY['screen']['w'], LAY['screen']['h']


def screen_overlay(img, R, sc, center, pct=None, home=False, pill_pct=None, offset=np.zeros(3), M=np.eye(3), size=None):
    """paint the Java overlay (home UI or charging %) onto a rendered front view by re-rendering the wallpaper quad."""
    return img


def phone_quads(anims, M=np.eye(3), t=np.zeros(3), ui=None):
    q = ph.pose(anims)
    return [((p @ M.T) + t, uv) for p, uv in q]


# --- UI texture variants: bake the home overlay into a copy of the texture, as the game draws it on top
def ui_tex(battery, charging):
    tex = ph.tex.copy()
    m = json.load(open(KP, encoding='utf-8'))
    el = [e for e in m['elements'] if e['name'] == 'wallpaper'][0]
    u0, v0, u1, v1 = [int(round(a)) for a in el['faces']['north']['uv']]
    tex[v0:v1, u0:u1] = A.home_ui_mock(tex[v0:v1, u0:u1].copy(), SW, SH, battery=battery, charging=charging)
    return tex


def pct_tex(base, pct, region_el='wallpaper'):
    return base


def label(img, txt):
    d = ImageDraw.Draw(img)
    f = A.font(A.F_CJK, 26)
    d.rectangle((0, img.size[1] - 44, img.size[0], img.size[1]), fill=(250, 250, 250))
    d.text((img.size[0] / 2, img.size[1] - 22), txt, font=f, fill=(40, 40, 44), anchor='mm')
    return img


def save_gif(frames, path, ms):
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True)


def cached(key, fn):
    p = os.path.join(CACHE, key + '.png')
    if os.path.exists(p):
        return Image.open(p).convert('RGB')
    im = fn(); im.save(p); return im


# ============================================================================ phone screen states (front view)
if WHAT == 'states':
    R = view(180 + 14, 6); S = 520; sc = S * 0.9 / 82
    tex_home = ui_tex(0.76, False)
    tex_chg = ui_tex(0.58, True)
    seq = []
    # off (1 s) -> boot 4 s -> home 1.5 s -> plug pill 2.2 s -> power off 2 s -> off + charging ring 2 s
    for i in range(6): seq.append(('off', [(N + 'screen_off', 0)], None, '关机 · 黑屏'))
    for i in range(40): seq.append((f'boot{i}', [(N + 'boot', i * 0.1)], 'home' if i >= 36 else None, '开机：REDMI → Xiaomi HyperOS → 桌面'))
    for i in range(10): seq.append(('home', [(N + 'home', 0)], 'home', '系统桌面（应用图标可配置）'))
    for i in range(22): seq.append((f'plug{i}', [(N + 'home', 0), (N + 'charge_plug', i * 0.1)], 'chg', '插入 AURO 2000 伸缩线 · 充电提示'))
    for i in range(20): seq.append((f'poff{i}', [(N + 'power_off', i * 0.1)], None, '关机动画'))
    for i in range(20): seq.append((f'coff{i}', [(N + 'charge_off', i * 0.1)], 'pct', '关机充电动画'))
    frames = []
    for key, an, ui, txt in seq:
        def fn(an=an, ui=ui):
            tex = tex_home if ui == 'home' else tex_chg if ui == 'chg' else ph.tex
            im = render(ph.pose(an), tex, R, size=S, scale=sc, center=(0, 0, 0), bg=(236, 236, 236))
            if ui == 'pct':
                d = ImageDraw.Draw(im)
                d.text((S / 2 + 6, S / 2 - sc * (LAY['screen']['half_y'] - 46)), '58%', font=A.font(A.F_POP_L, sc * 4.2),
                       fill=(255, 255, 255), anchor='mm')
            return im
        frames.append(label(cached('st_' + key, fn).copy(), txt))
    save_gif(frames, os.path.join(OUT, 'screen_states.gif'), 100)
    print('states', len(frames))

# ============================================================================ AURO cable plugged into the phone
if WHAT in ('plug', 'still'):
    au = Model(AP)
    info = json.load(open(os.path.join(os.path.dirname(AP), 'cable_info.json')))
    tip = np.array(info['plugged_tip_px'], float)
    # phone lies screen-up on the ground in front of the station, USB-C (local -Y) facing the station (+Z),
    # 1 phone px = 2 mm = 0.5 station px
    M = 0.5 * rotmat([0, 180, 0]) @ rotmat([90, 0, 0])   # screen up, USB-C (local -Y) towards the station (+Z)
    port_local = np.array(LAY['usb_c']['center'], float)
    lift = 0.5 * 1.975 + 0.05
    t = tip + np.array([0, 0, 0.3]) - M @ port_local
    t[1] = lift
    tex_c = ui_tex(0.58, True)
    sc = 900 / 150
    cam = view(180 + 35, 28)
    C = (-6, 14, -60)

    def scene(cable, phone_an, tex):
        qa = au.pose(cable)
        qp = phone_quads(phone_an, M, t)
        a = render(qa, au.tex, cam, size=900, scale=sc, center=C, bg=(236, 236, 236))
        # z-merge: render phone separately and composite with depth by rendering both into one list is not possible
        # (different textures) -> render phone over a depth-correct pass: phone is in front of the station here.
        b = render(qp, tex, cam, size=900, scale=sc, center=C, bg=(1, 2, 3))
        ba = np.asarray(b).astype(int); aa = np.asarray(a).copy()
        m = np.any(ba != (1, 2, 3), axis=2)
        aa[m] = ba[m]
        return Image.fromarray(aa)
    AN = 'animation.auro2000elite.'
    if WHAT == 'still':
        scene([(AN + 'cable_plugged', 0)], [(N + 'home', 0)], tex_c).save(os.path.join(OUT, 'charging_home.png'))
        scene([(AN + 'cable_plugged', 0)], [(N + 'charge_off', 0.6)], ph.tex).save(os.path.join(OUT, 'charging_off.png'))
        print('stills')
    else:
        frames = []
        L = au.anims[AN + 'cable_plug']['length']
        n1 = int(L / 0.1) + 1
        for i in range(n1):
            frames.append(label(cached(f'pl_{i}', lambda i=i: scene([(AN + 'cable_plug', i * 0.1)], [(N + 'home', 0)], ui_tex(0.58, False))).copy(),
                                'AURO 2000 伸缩线插入手机 USB-C'))
        for i in range(22):
            frames.append(label(cached(f'pp_{i}', lambda i=i: scene([(AN + 'cable_plugged', 0)], [(N + 'home', 0), (N + 'charge_plug', i * 0.1)], tex_c)).copy(),
                                '手机开始充电（桌面充电提示，状态栏电池变绿）'))
        save_gif(frames, os.path.join(OUT, 'cable_plug.gif'), 100)
        print('plug', len(frames))
