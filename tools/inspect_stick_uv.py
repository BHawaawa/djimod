import json
p = r'model/RC2/geo/rc2.geo.json'
g = json.load(open(p, encoding='utf-8'))['minecraft:geometry'][0]
for b in g['bones']:
    if b['name'] not in ('stick_l', 'stick_r'):
        continue
    print('BONE', b['name'])
    for i, c in enumerate(b.get('cubes', [])):
        print(' cube', i, 'origin', c['origin'], 'size', c['size'])
        for face, uv in c['uv'].items():
            print('   ', face, uv['uv'], uv['uv_size'])
