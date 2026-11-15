# -*- coding: utf-8 -*-
"""Copies a gen_redmi_k90.py build into the asset package model/phone/redmi_k90 (+ app icons, manifest).
usage: python3 package_k90.py <build_dir> [package_dir]"""
import sys, os, json, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import k90_art as A

B = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
P = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', 'model', 'phone', 'redmi_k90')
M = 'redmi_k90'
for d in ('geo', 'animations', 'textures/apps', 'models/item'):
    os.makedirs(os.path.join(P, d), exist_ok=True)
shutil.copy(os.path.join(B, f'{M}.bbmodel'), P)
shutil.copy(os.path.join(B, f'{M}.geo.json'), os.path.join(P, 'geo'))
shutil.copy(os.path.join(B, f'{M}.animation.json'), os.path.join(P, 'animations'))
shutil.copy(os.path.join(B, f'{M}.png'), os.path.join(P, 'textures'))
shutil.copy(os.path.join(B, f'{M}_glowmask.png'), os.path.join(P, 'textures'))
shutil.copy(os.path.join(B, f'{M}.item.json'), os.path.join(P, 'models/item', f'{M}.json'))
shutil.copy(os.path.join(B, 'meta.json'), P)
shutil.copy(os.path.join(B, 'screen_layout.json'), P)
for a, zh, en in A.APPS:
    Image.fromarray(A.icon(a, 96), 'RGBA').save(os.path.join(P, 'textures/apps', f'{a}.png'), optimize=True)

meta = json.load(open(os.path.join(B, 'meta.json')))
lay = json.load(open(os.path.join(B, 'screen_layout.json'), encoding='utf-8'))
anims = json.load(open(os.path.join(B, f'{M}.animation.json')))['animations']
manifest = {
    'name': 'Redmi K90 Pro Max (Sound by Bose)',
    'id': M, 'kind': 'geckolib_item', 'minecraft': '1.20.1', 'loader': 'fabric (yarn)', 'geckolib': '4.4.9',
    'modid': 'djimod',
    'scale': '1 model px = 2 mm (body 163.3 x 77.8 x 7.9 mm -> 81.65 x 38.9 x 3.95 px), centred on the item pivot',
    'orientation': 'screen -Z (north), back/cameras +Z; seen from the front the keys are on the right = -X',
    'install_map': {
        f'geo/{M}.geo.json': f'assets/djimod/geo/item/{M}.geo.json',
        f'animations/{M}.animation.json': f'assets/djimod/animations/item/{M}.animation.json',
        f'textures/{M}.png': f'assets/djimod/textures/item/{M}.png',
        f'textures/{M}_glowmask.png': f'assets/djimod/textures/item/{M}_glowmask.png',
        'textures/apps/*.png': 'assets/djimod/textures/item/phone/apps/*.png',
        f'models/item/{M}.json': f'assets/djimod/models/item/{M}.json',
        'reference_code/item/PhoneItem.java': 'src/main/java/com/example/avatadrone/item/PhoneItem.java',
        'reference_code/item/PhoneApps.java': 'src/main/java/com/example/avatadrone/item/PhoneApps.java',
        'reference_code/client/PhoneItemModel.java': 'src/main/java/com/example/avatadrone/client/PhoneItemModel.java',
        'reference_code/client/PhoneItemRenderer.java': 'src/main/java/com/example/avatadrone/client/PhoneItemRenderer.java',
        'patches/redmi_k90_integration.patch': 'apply in the DJI folder root: patch -p1 < model/phone/redmi_k90/patches/redmi_k90_integration.patch',
    },
    'bones': meta['bones'],
    'screen_states': {
        'OFF': 'animation.redmi_k90.screen_off  (black glass)',
        'BOOT': 'animation.redmi_k90.boot  (REDMI -> Xiaomi HyperOS -> desktop zoom-in, 4.0 s; PhoneItem.T_BOOT = 72 ticks)',
        'HOME': 'animation.redmi_k90.home  (wallpaper + Java overlay: status bar, clock, app grid, dock)',
        'SLEEP': 'animation.redmi_k90.screen_off  (system on, screen off)',
        'SHUTDOWN': 'animation.redmi_k90.power_off  (spinner, 2.0 s)',
        'CHARGE_OFF': 'animation.redmi_k90.charge_off  (loop: green ring + bolt + live %, phone off and plugged)',
        'plug fx': 'animation.redmi_k90.charge_plug  (controller "fx": HyperOS pill over the desktop for 2.2 s after the plug arrives)',
    },
    'animations': {k: dict(loop=v.get('loop', False), length=v['animation_length'], bones=sorted(v['bones'])) for k, v in anims.items()},
    'controllers': {'screen': 'state machine from stack NBT (PhoneItem.screen)', 'fx': 'charge_plug or STOP'},
    'nbt': {'PowerOn': 'bool', 'PowerTime': 'long world time of last power toggle', 'Awake': 'bool screen on',
            'Charging': 'bool (set by PowerStationEntity each tick it feeds the phone, cleared by the phone)',
            'PlugTime': 'long', 'ChargeW': 'int', 'BatteryCharge': 'double 0..1 (DeviceCharging)',
            'Apps': 'list<string> desktop grid (max 20)', 'Dock': 'list<string> (max 4)', 'GeckoLibID': 'long'},
    'charging': {'source': 'DJI Power AURO 2000 ELITE built-in 140 W retractable USB-C cable (PowerStationEntity, target kind 3 = player)',
                 'phone_battery_wh': 29.26, 'phone_max_input_w': 100, 'usb_c_port': lay['usb_c']},
    'apps': {'icon_path': 'assets/djimod/textures/item/phone/apps/<id>.png (96x96, transparent rounded corners)',
             'label_key': 'app.djimod.phone.<id>', 'known': [a for a, _, _ in A.APPS],
             'default_grid': A.DEFAULT_GRID, 'default_dock': A.DEFAULT_DOCK,
             'command': '/redmi app add <id> [dock] | remove <id> | list | reset   (phone in main hand)'},
    'screen_layout': 'screen_layout.json',
    'texture': {'atlas': meta['atlas'], 'glowmask': True},
    'bbox_px': [meta['bbox_min'], meta['bbox_max']],
    'cubes': meta['cubes'],
}
json.dump(manifest, open(os.path.join(P, 'manifest.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('packaged ->', P)
