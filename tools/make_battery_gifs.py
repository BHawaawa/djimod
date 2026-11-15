# -*- coding: utf-8 -*-
"""Render the Avata 360 battery-insertion GIFs / stills from the .bbmodel (software renderer, no Blockbench needed).

usage: python3 make_battery_gifs.py <avata360.bbmodel> <out_dir> [which]
  which = comma list of: insert_rear,insert_side,insert_top,turntable,stills,remove   (default: all)
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_anim import Model, render, view, rotmat

SRC, OUT = sys.argv[1], sys.argv[2]
WHICH = sys.argv[3].split(',') if len(sys.argv) > 3 else ['insert_rear', 'insert_side', 'insert_top', 'turntable',
                                                          'stills', 'remove']
os.makedirs(OUT, exist_ok=True)
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
SIZE, SS = 640, 2                      # output size, supersampling
FPS = 20
BG = (236, 236, 236)
m = Model(SRC)
INS, REM, IN, EMPTY, FLY = ('animation.avata360.battery_insert', 'animation.avata360.battery_remove',
                            'animation.avata360.battery_installed', 'animation.avata360.battery_empty',
                            'animation.avata360.fly')


def caption(img, txt):
    if not txt:
        return img
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.truetype(FONT, 22)
    except Exception:
        f = ImageFont.load_default()
    w = d.textlength(txt, font=f)
    d.rounded_rectangle((8, 8, 24 + w, 44), radius=8, fill=(250, 250, 250))
    d.text((16, 11), txt, font=f, fill=(80, 80, 82))
    return img


CACHE = os.environ.get('GIF_CACHE', '/tmp/gif_cache')
BUDGET = float(os.environ.get('GIF_BUDGET', '1e9'))      # seconds; stop early (resumable) when exceeded
import time, hashlib
_T0 = time.time()


class OutOfTime(Exception):
    pass


def frame(anims, R, scale, center, txt=None, hidden=()):
    key = hashlib.md5(repr((SRC, os.path.getmtime(SRC), anims, np.round(R, 6).tolist(), scale, center, txt,
                            sorted(hidden), SIZE, SS)).encode()).hexdigest()
    cp = os.path.join(CACHE, key + '.png')
    if os.path.exists(cp):
        return Image.open(cp).convert('RGB')
    if time.time() - _T0 > BUDGET:
        raise OutOfTime()
    os.makedirs(CACHE, exist_ok=True)
    im = _frame(anims, R, scale, center, txt, hidden)
    im.save(cp)
    return im


def _frame(anims, R, scale, center, txt=None, hidden=()):
    q = m.pose(anims, hidden=hidden)
    im = render(q, m.tex, R, size=SIZE * SS, scale=scale * SS, center=center, bg=BG, cull=True)
    im = im.resize((SIZE, SIZE), Image.LANCZOS)
    return caption(im, txt)


def save_gif(frames, path, hold_last=0):
    durs = [int(1000 / FPS)] * len(frames)
    if hold_last:
        durs[-1] = hold_last
    # one shared palette for the whole clip (no flicker)
    strip = Image.new('RGB', (SIZE, SIZE * 3))
    for i, k in enumerate((0, len(frames) // 2, len(frames) - 1)):
        strip.paste(frames[k], (0, SIZE * i))
    pal = strip.quantize(colors=255, method=Image.MEDIANCUT)
    q = [f.quantize(palette=pal, dither=Image.FLOYDSTEINBERG) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=durs, loop=0, optimize=True, disposal=2)
    print('saved', path, len(frames), 'frames')


def insert_clip(R, scale, center, name, txt, length=2.0, tail=0.5):
    n = int(round((length + tail) * FPS))
    frames = []
    for i in range(n):
        t = min(i / FPS, length)
        frames.append(frame([(INS, t)], R, scale, center, txt))
    save_gif(frames, os.path.join(OUT, name), hold_last=1500)


try:
    if 'insert_rear' in WHICH:      # rear three-quarter, like the photo of the tail
        insert_clip(view(-32, 20), 16.5, (1.0, 3.0, 4.5), '01_insert_rear_quarter.gif', 'Avata 360 · 插入电池（后侧视角）')
    if 'insert_side' in WHICH:      # low side view: shows the slide along the bay
        insert_clip(view(-78, 10), 12.5, (0, 3.0, 8.0), '02_insert_side.gif', 'Avata 360 · 插入电池（侧视）')
    if 'insert_top' in WHICH:       # top view (same framing as the top-down photo)
        insert_clip(rotmat([90, 0, 0]), 13.5, (0, 3.0, 7.5), '03_insert_top.gif', 'Avata 360 · 插入电池（俯视）')

    if 'turntable' in WHICH:        # after insertion: slow turntable with spinning props
        n = 72
        frames = []
        for i in range(n):
            yaw = 180 + 35 + i * 360.0 / n
            frames.append(frame([(IN, 0), (FLY, (i / FPS) * 0.25)], view(yaw, 24), 17, (0, 2.6, 0),
                                'Avata 360 · 已插入电池'))
        save_gif(frames, os.path.join(OUT, '04_inserted_turntable.gif'))

    if 'stills' in WHICH:           # after insertion: stills matching the reference photos
        S = [('05_inserted_top.png', rotmat([90, 0, 0]), 18, (0, 3, 0), '已插入电池 · 俯视'),
             ('06_inserted_rear_quarter.png', view(-28, 16), 19, (0, 2.6, 2), '已插入电池 · 后侧'),
             ('07_inserted_front_quarter.png', view(180 + 38, 26), 18, (0, 2.6, 0), '已插入电池 · 前侧'),
             ('08_inserted_tail_closeup.png', view(-24, 14), 34, (0, 2.6, 9.0), '已插入电池 · 电池尾部（电源键 + 电量灯）'),
             ('09_inserted_bottom.png', rotmat([0, 0, 180]) @ rotmat([-90, 0, 0]), 18, (0, 2.5, 0), '已插入电池 · 底部')]
        for fn, R, s, c, txt in S:
            frame([(IN, 0)], R, s, c, txt).save(os.path.join(OUT, fn))
            print('saved', fn)
        frame([(EMPTY, 0)], view(-24, 14), 34, (0, 2.6, 8.0), '未插电池 · 空电池仓').save(os.path.join(OUT, '11_empty_bay_before_insert.png'))

    if 'remove' in WHICH:
        n = int(round(2.0 * FPS))
        frames = [frame([(REM, min(i / FPS, 1.5))], view(-32, 20), 16.5, (1.0, 3.0, 4.5), 'Avata 360 · 取出电池')
                  for i in range(n)]
        save_gif(frames, os.path.join(OUT, '10_remove_rear_quarter.gif'), hold_last=1200)

except OutOfTime:
    print('INCOMPLETE - run again to continue')
