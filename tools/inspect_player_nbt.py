import nbtlib
import os

base = r'fabric_mod/run/saves/RC2 Mouse QA/playerdata'
for fn in sorted(os.listdir(base)):
    if fn.endswith('.dat'):
        p = os.path.join(base, fn)
        try:
            nbt = nbtlib.load(p)
        except Exception as e:
            print(fn, 'ERR', e)
            continue
        print('===', fn)
        inv = nbt.get('Inventory')
        if inv:
            for item in inv:
                if 'dji_rc2' in str(item.get('id', '')):
                    print('RC2:', item)
        print('SelectedItemSlot', nbt.get('SelectedItemSlot'), 'Dimension', nbt.get('Dimension'))
