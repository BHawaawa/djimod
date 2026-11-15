"""Generate the cuboid RC 2 item model for inventory, dropped and third-person views."""
import json
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / 'fabric_mod/src/main/resources/assets/djimod'
elements = []
def box(start, end, texture):
    elements.append({'from': start, 'to': end,
        'faces': {side: {'texture': '#' + texture, 'uv': [0, 0, 16, 16]}
                  for side in ('north', 'south', 'east', 'west', 'up', 'down')}})
box([1, 3, 6], [15, 10, 9], 'body')
box([0, 2, 6], [3, 9, 9.5], 'grip')
box([13, 2, 6], [16, 9, 9.5], 'grip')
box([4, 3.5, 5.6], [12, 8, 6], 'black')
box([4.4, 3.9, 5.5], [11.6, 7.6, 5.6], 'screen')
for x in (2.4, 12.4):
    box([x, 8, 5.3], [x+1.2, 9.2, 6], 'black')
    box([x+.35, 8.35, 4.5], [x+.85, 8.85, 5.3], 'grip')
for x in (2, 13):
    box([x, 10, 7], [x+1, 14, 8], 'grip')
model = {'textures': {'body': 'minecraft:block/light_gray_concrete',
    'grip': 'minecraft:block/gray_concrete', 'black': 'minecraft:block/black_concrete',
    'screen': 'minecraft:block/black_stained_glass', 'particle': 'minecraft:block/light_gray_concrete'},
    'elements': elements, 'display': {
        'gui': {'rotation': [15, 195, 0], 'translation': [0, 0, 0], 'scale': [.9, .9, .9]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
        'thirdperson_righthand': {'rotation': [70, 180, 0], 'translation': [0, 2, 1], 'scale': [.65, .65, .65]},
        'thirdperson_lefthand': {'rotation': [70, 180, 0], 'translation': [0, 2, 1], 'scale': [.65, .65, .65]},
        'firstperson_righthand': {'rotation': [0, 180, 0], 'translation': [-7, 0, 0], 'scale': [1, 1, 1]},
        'firstperson_lefthand': {'rotation': [0, 180, 0], 'translation': [-7, 0, 0], 'scale': [1, 1, 1]}}}
(assets / 'models/item/dji_rc2.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
translations = {
    'zh_cn': ['DJI RC 2 遥控器', 'RC 2 已连接无人机', 'RC 2 已解除配对',
              '手持 RC 2 右键无人机配对', '潜行右键空气解除配对', '信号丢失 · 请靠近无人机', '图传异常 · 请重新手持遥控器'],
    'en_us': ['DJI RC 2 Controller', 'RC 2 paired with drone', 'RC 2 unpaired',
              'Use RC 2 on a drone to pair', 'Sneak + use in air to unpair', 'Signal lost - move closer', 'Video error - re-equip controller']}
keys = ['item.djimod.dji_rc2'] + ['message.djimod.rc2.' + key for key in
        ('paired', 'unpaired', 'hint', 'clear_hint', 'no_signal', 'video_error')]
for language, values in translations.items():
    path = assets / 'lang' / (language + '.json')
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update(zip(keys, values))
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
