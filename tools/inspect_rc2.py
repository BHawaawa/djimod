import json
import sys

p = r'model/RC2/geo/rc2.geo.json'
g = json.load(open(p, encoding='utf-8'))['minecraft:geometry'][0]
for b in g['bones']:
    print('BONE', b['name'], 'parent', b.get('parent'), 'pivot', b.get('pivot'),
          'rot', b.get('rotation'), 'cubes', len(b.get('cubes', [])))
    for c in b.get('cubes', []):
        print('   origin', c.get('origin'), 'size', c.get('size'),
              'rot', c.get('rotation'), 'pivot', c.get('pivot'),
              'inflate', c.get('inflate'))
