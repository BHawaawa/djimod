# -*- coding: utf-8 -*-
"""Applies the Redmi K90 integration edits to copies of the existing fabric_mod sources (used to build patches/).
usage: python3 k90_patch.py <root_of_copied_src_main>"""
import sys, os, json
ROOT = sys.argv[1]
J = os.path.join(ROOT, 'java/com/example/avatadrone')


def edit(path, pairs):
    raw = open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in pairs:
        assert s.count(old) == 1, (path, old[:60], s.count(old))
        s = s.replace(old, new)
    if crlf:
        s = s.replace('\n', '\r\n')
    open(path, 'w', encoding='utf-8', newline='').write(s)


edit(f'{J}/item/DeviceCharging.java', [
    ('        if (stack.getItem() instanceof Rc2Item) return "rc2";\n',
     '        if (stack.getItem() instanceof Rc2Item) return "rc2";\n'
     '        if (stack.getItem() instanceof PhoneItem) return "redmi_k90";\n'),
    ('    public static boolean isChargeable(ItemStack stack) { return batteryModel(stack) != null; }\n',
     '    public static boolean isChargeable(ItemStack stack) { return batteryModel(stack) != null; }\n\n'
     '    /** devices charged while held in the main hand (cable goes to the player): RC 2, Redmi K90. */\n'
     '    public static boolean isHandheld(ItemStack stack) {\n'
     '        return stack.getItem() instanceof Rc2Item || stack.getItem() instanceof PhoneItem;\n'
     '    }\n'),
    ('            case "rc2" -> 22.32; // gameplay battery capacity\n',
     '            case "rc2" -> 22.32; // gameplay battery capacity\n'
     '            case "redmi_k90" -> 29.26;           // 7560 mAh x 3.87 V\n'),
    ('            case "rc2" -> 9.0;\n',
     '            case "rc2" -> 9.0;\n'
     '            case "redmi_k90" -> 100.0;           // 100 W HyperCharge\n'),
])

edit(f'{J}/entity/PowerStationEntity.java', [
    ('                        && controllerStack.getItem() instanceof com.example.avatadrone.item.Rc2Item) {',
     '                        && DeviceCharging.isHandheld(controllerStack)) {'),
    ('                        if (wh > 0) DeviceCharging.setCharge(controllerStack, c + wh / cap);\n                    }\n',
     '                        if (wh > 0) DeviceCharging.setCharge(controllerStack, c + wh / cap);\n                    }\n'
     '                    if (!busy() && controllerStack.getItem() instanceof com.example.avatadrone.item.PhoneItem)\n'
     '                        com.example.avatadrone.item.PhoneItem.reportCharging(controllerStack, (int) Math.round(wh / dtH), getWorld().getTime());\n'),
    ('        if (!player.isSneaking() && held.getItem() instanceof com.example.avatadrone.item.Rc2Item) {',
     '        if (!player.isSneaking() && DeviceCharging.isHandheld(held)) {'),
    ('else player.sendMessage(Text.literal("遥控器已连接另一台移动电源"), true);',
     'else player.sendMessage(Text.literal("该设备已连接另一台移动电源"), true);'),
    ('"先 Shift+右键移动电源取线，等待抽线完成，再手持遥控器右键连接"',
     '"先 Shift+右键移动电源取线，等待抽线完成，再手持遥控器/手机右键连接"'),
    ('手持遥控器右键电源（5格内）', '手持遥控器/手机右键电源（5格内）'),
])

edit(f'{J}/client/ChargingCableRenderer.java', [
    ('            if (e.getTargetKind() == 3) {\n',
     '            if (e.getTargetKind() == 3 && target instanceof net.minecraft.entity.player.PlayerEntity holder\n'
     '                    && holder.getMainHandStack().getItem() instanceof com.example.avatadrone.item.PhoneItem) {\n'
     '                // Redmi K90 held upright in the right hand: USB-C port at the bottom edge, plug comes from below\n'
     '                normal = new Vec3d(0, -1, 0);\n'
     '                Vec3d side = rotate(new Vec3d(-0.36, 0, 0.40), -target.getYaw(delta));\n'
     '                port = target.getLerpedPos(delta).subtract(e.getPos()).add(side).add(0, target.getHeight() * 0.50, 0);\n'
     '            } else if (e.getTargetKind() == 3) {\n'),
])

edit(f'{J}/AvataDrone.java', [
    ('    /** 禁飞无人机牌子',
     '    /** Redmi K90 手机（GeckoLib 物品）：Shift+右键开关机，右键亮/熄屏；AURO 2000 取线后手持手机右键电源充电。 */\n'
     '    public static final Item REDMI_K90 = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "redmi_k90"),\n'
     '            new com.example.avatadrone.item.PhoneItem(new Item.Settings().maxCount(1)));\n\n'
     '    /** 禁飞无人机牌子'),
    ('                        entries.add(DJI_RC2);\n',
     '                        entries.add(DJI_RC2);\n'
     '                        entries.add(REDMI_K90);\n'),
    ('        com.example.avatadrone.network.ChargingNetwork.register();\n',
     '        com.example.avatadrone.network.ChargingNetwork.register();\n'
     '        com.example.avatadrone.item.PhoneApps.registerCommand();\n'),
])

edit(f'{J}/AvataDroneClient.java', [
    ('        com.example.avatadrone.client.FlightSettingsClient.register();\n',
     '        com.example.avatadrone.client.FlightSettingsClient.register();\n'
     '        com.example.avatadrone.item.PhoneItem.clientTime = () -> {\n'
     '            var w = net.minecraft.client.MinecraftClient.getInstance().world;\n'
     '            return w == null ? 0L : w.getTime();\n'
     '        };\n'),
])

LANG = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'model', 'phone', 'redmi_k90', 'lang_entries.json'), encoding='utf-8'))
for code in ('zh_cn', 'en_us'):
    p = os.path.join(ROOT, f'resources/assets/djimod/lang/{code}.json')
    raw0 = open(p, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in raw0 else '\n'
    raw = raw0.replace('\r\n', '\n').rstrip()
    assert raw.endswith('}')
    add = ',\n'.join(f'  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}' for k, v in LANG[code].items())
    tail = '\n' if raw0.endswith(('\n', '\r\n')) else ''
    open(p, 'w', encoding='utf-8', newline='').write((raw[:-1].rstrip() + ',\n' + add + '\n}' + tail).replace('\n', nl))
    json.load(open(p, encoding='utf-8'))
print('patched', ROOT)
