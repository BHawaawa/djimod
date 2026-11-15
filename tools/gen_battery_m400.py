# -*- coding: utf-8 -*-
"""DJI Matrice 400 TB100 intelligent flight battery -> GeckoLib ITEM model (model/battery/M400).

Geometry/painting come from tb100.py (shared with gen_m400.py, which merges the same battery into the drone).
Item frame: 1 px = 7.5 mm (k = 2 in battery-local units), model centred on the item pivot,
cap / lid on top (+Y), vent end -Z (north), LED + power-button end +Z.
usage: python3 gen_battery_m400.py <out_dir>
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbgen as G
from bbgen import bone
import tb100 as T

OUT = sys.argv[1] if len(sys.argv) > 1 else 'build_tb100'
MODEL = 'm400_battery'
K = 2.0
VC = (T.BODY_V0 + T.VENT[4]) / 2          # vertical centre (battery-local)
SC = T.CAP_L / 2
OY, OZ = -K * VC, -K * SC

bone('root', (0, 0, 0))
bone('battery', (0, 0, 0), 'root')
bone('latch_l', (-K * T.CAP_W / 2, OY + K * -0.5, OZ + K * 11.9), 'battery')
bone('latch_r', (K * T.CAP_W / 2, OY + K * -0.5, OZ + K * 11.9), 'battery')
T.build(dict(main='battery', latch_l='latch_l', latch_r='latch_r'), K, OY, OZ,
        lambda P: (P[..., 0] / K, (P[..., 1] - OY) / K, (P[..., 2] - OZ) / K),
        lambda n: (n[0], n[1], n[2]), D=12)

ANIMS = {
    f'animation.{MODEL}.idle': dict(loop='loop', length=1.0, bones={'root': {'position': [(0.0, [0, 0, 0], 'linear')]}}),
    # squeeze both orange release latches, then spring back (Bedrock x = -Blockbench x)
    f'animation.{MODEL}.latch_press': dict(loop='once', length=0.6, bones={
        'latch_r': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [0.18, 0, 0], 'catmullrom'),
                                 (0.4, [0.18, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]},
        'latch_l': {'position': [(0.0, [0, 0, 0], 'catmullrom'), (0.15, [-0.18, 0, 0], 'catmullrom'),
                                 (0.4, [-0.18, 0, 0], 'catmullrom'), (0.6, [0, 0, 0], 'catmullrom')]}}),
    f'animation.{MODEL}.showcase': dict(loop='loop', length=8.0, bones={
        'battery': {'rotation': [(0.0, [0, 0, 0], 'linear'), (8.0, [0, 360, 0], 'linear')]}}),
}
DISPLAY = {
    'thirdperson_righthand': {'rotation': [10, 180, 0], 'translation': [0, 2.0, 1.5], 'scale': [0.32, 0.32, 0.32]},
    'thirdperson_lefthand': {'rotation': [10, 180, 0], 'translation': [0, 2.0, 1.5], 'scale': [0.32, 0.32, 0.32]},
    'firstperson_righthand': {'rotation': [10, 200, 0], 'translation': [0, 3.0, 0], 'scale': [0.38, 0.38, 0.38]},
    'firstperson_lefthand': {'rotation': [10, 160, 0], 'translation': [0, 3.0, 0], 'scale': [0.38, 0.38, 0.38]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.28, 0.28, 0.28]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.46, 0.46, 0.46]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 14, 0], 'scale': [0.55, 0.55, 0.55]},
    'fixed': {'rotation': [-90, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
}
mats = dict(T.MATS)
G.export(OUT, MODEL, mats, ANIMS, kind='item', display=DISPLAY, glow=True, atw=2048, bounds=(2, 1))
print('done', OUT)
