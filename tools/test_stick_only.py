import sys, json
sys.path.insert(0, r'tools')
import render
import numpy as np
from PIL import Image

g = json.load(open(r'model/RC2/geo/rc2.geo.json', encoding='utf-8'))['minecraft:geometry'][0]
for b in g['bones']:
    if b['name'] == 'body':
        b['cubes'] = []
    if b['name'] == 'buttons':
        b['cubes'] = []
    if b['name'] == 'screen':
        b['cubes'] = []
    if b['name'] == 'antenna_l':
        b['cubes'] = []
    if b['name'] == 'antenna_r':
        b['cubes'] = []
# write temp geo
json.dump({'format_version': '1.12.0', 'minecraft:geometry': [g]}, open(r'preview\stick_only.geo.json', 'w'), ensure_ascii=False)
quads, tex = render.load_geo(r'preview\stick_only.geo.json', r'model/RC2/textures/rc2.png')
print('quads', len(quads))
for name, view in [('top', render.view(180, 35)), ('top2', render.view(180, 55)), ('angle', render.view(160, 35))]:
    img = render.render(quads, tex, view, size=700, scale=90.0, center=(0, 5.1, -4.0), bg=(238, 238, 238))
    img.save(fr'preview\sticks_{name}.png')
print('ok')
