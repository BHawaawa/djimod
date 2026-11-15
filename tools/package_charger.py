# -*- coding: utf-8 -*-
"""Lay out a generated charging-hub build into the asset convention model/charger/<product>/ and write manifest.json.

usage: python3 package_charger.py <build_dir> <dest_dir> <MODEL> <product title> [modid]
"""
import hashlib, json, os, shutil, sys

BUILD, DEST, MODEL, TITLE = sys.argv[1:5]
MODID = sys.argv[5] if len(sys.argv) > 5 else 'djimod'
lay = {
    f'{MODEL}.bbmodel': f'{MODEL}.bbmodel',
    f'{MODEL}.geo.json': f'geo/{MODEL}.geo.json',
    f'{MODEL}.animation.json': f'animations/{MODEL}.animation.json',
    f'{MODEL}.png': f'textures/{MODEL}.png',
    f'{MODEL}_glowmask.png': f'textures/{MODEL}_glowmask.png',
    f'{MODEL}.blockstate.json': f'blockstates/{MODEL}.json',
    f'{MODEL}.block.json': f'models/block/{MODEL}.json',
    f'{MODEL}.item.json': f'models/item/{MODEL}.json',
    'label_art.png': 'preview/label_art.png',
}
mod_path = {
    f'geo/{MODEL}.geo.json': f'assets/{MODID}/geo/block/{MODEL}.geo.json',
    f'animations/{MODEL}.animation.json': f'assets/{MODID}/animations/block/{MODEL}.animation.json',
    f'textures/{MODEL}.png': f'assets/{MODID}/textures/block/{MODEL}.png',
    f'textures/{MODEL}_glowmask.png': f'assets/{MODID}/textures/block/{MODEL}_glowmask.png',
    f'blockstates/{MODEL}.json': f'assets/{MODID}/blockstates/{MODEL}.json',
    f'models/block/{MODEL}.json': f'assets/{MODID}/models/block/{MODEL}.json',
    f'models/item/{MODEL}.json': f'assets/{MODID}/models/item/{MODEL}.json',
}
for src, dst in lay.items():
    os.makedirs(os.path.dirname(os.path.join(DEST, dst)) or DEST, exist_ok=True)
    shutil.copyfile(os.path.join(BUILD, src), os.path.join(DEST, dst))
meta = json.load(open(os.path.join(BUILD, 'meta.json')))
geo = json.load(open(os.path.join(BUILD, f'{MODEL}.geo.json')))['minecraft:geometry'][0]
md5 = lambda p: hashlib.md5(open(os.path.join(DEST, p), 'rb').read()).hexdigest()
bones = [dict(name=b['name'], parent=b.get('parent'), pivot_geo=b['pivot'], rest_rotation_geo=b.get('rotation'),
              cubes=len(b.get('cubes', []))) for b in geo['bones']]
man = {
    'id': MODEL, 'display_name': TITLE, 'type': 'geckolib_block_entity', 'modid': MODID,
    'minecraft': '1.20.1 Fabric', 'geckolib': '4.x (verified against 4.4.9 sources)',
    'format': 'GeckoLib 4.x block (Bedrock geometry 1.12.0)',
    'geometry_identifier': f'geometry.{MODEL}',
    'files': {p: md5(p) for p in lay.values()},
    'mod_paths': mod_path,
    'texture': {'width': meta['atlas'][0], 'height': meta['atlas'][1], 'uv_mode': 'per-face UV',
                'glowmask': f'textures/{MODEL}_glowmask.png (charge LEDs, for AutoGlowingGeoLayer)'},
    'cube_count': meta['cubes'],
    'bbox_px_rest_frame': {'min': meta['bbox_min'], 'max': meta['bbox_max'],
                           'note': 'battery cubes are listed before their slot bone rest rotation; real height see README'},
    'orientation': 'origin = block bottom centre; front = -Z (north); GeoBlockRenderer rotates by HorizontalFacingBlock.FACING',
    'slot_bones': ['slot1 (-X)', 'slot2 (centre)', 'slot3 (+X)'],
    'bones': bones,
    'animations': {k: v for k, v in meta['animations'].items()},
    'animation_usage': {
        'per_slot_controller': 'controller "slotN": state = slotN_installed / slotN_empty (or slotN_charging); '
                               'triggerable slotN_insert / slotN_remove',
        'demo': 'demo_cycle (12 s loop, all three slots, for Blockbench / showcase)',
    },
}
json.dump(man, open(os.path.join(DEST, 'manifest.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('packaged', DEST)
