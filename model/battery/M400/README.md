# battery/M400 — 大疆 TB100 智能飞行电池（经纬 M400 用）GeckoLib 物品

Minecraft 1.20.1，GeckoLib 4.x 物品（GeoItem）。机器可读信息见 `manifest.json`。

## 目录
```
m400_battery.bbmodel                    Blockbench 工程（GeckoLib 物品格式，贴图内嵌，含 display 设置）
geo/m400_battery.geo.json               几何，identifier = geometry.m400_battery
animations/m400_battery.animation.json  动画：idle / latch_press / showcase
textures/m400_battery.png               贴图（每面独立 UV）
textures/m400_battery_glowmask.png      电量灯发光遮罩（AutoGlowingGeoLayer）
models/item/m400_battery.json           {"parent":"builtin/entity", "display":{...}}
preview/                                预览（含 gui.png：物品栏格子里的样子）
reference_code/                         M400BatteryItem / M400BatteryItemRenderer / 注册片段
```

## 放入模组
```
geo/m400_battery.geo.json               -> assets/<modid>/geo/m400_battery.geo.json
animations/m400_battery.animation.json  -> assets/<modid>/animations/m400_battery.animation.json
textures/m400_battery.png               -> assets/<modid>/textures/item/m400_battery.png
textures/m400_battery_glowmask.png      -> assets/<modid>/textures/item/m400_battery_glowmask.png
models/item/m400_battery.json           -> assets/<modid>/models/item/m400_battery.json
```

## 外形（按 TB100 产品图）
- **浅灰电池盖**：一端是凸起的散热格栅块，另一端是 4 颗电量灯（发光）和电源键；盖面中间有压印的 TB100 字样。
- **深灰电池本体**：两侧有横向防滑肋条。电池盖在电量灯一端比本体长出一截。
  - 散热端：本体端面是进风格栅。
  - 电量灯端：本体端面贴着产品标签，有 TB100 / dji 字样、绿色检验贴和条码。
- **底面**：电源插头，带两排金色触点，两侧有导轨。
- **橙色释放卡扣**：电池盖两侧靠电量灯一端各一个，分别是骨骼 `latch_l` / `latch_r`。

## 和无人机的关系
几何和贴图都由 `tools/tb100.py` 生成，物品和无人机共用同一份代码：
- 物品：k = 2，即 7.5 mm/像素。
- 无人机：k = 1，即 15 mm/像素，再旋转到 34° 斜面。

所以装进 M400 的电池和手上拿的电池外观完全一致。无人机模型见 `DJI/model/M400/`，插拔动画 GIF 见 `DJI/model/gif/M400/`。

## 关于参考图
附件里还有两张经纬 M100 用的 TB47/TB48 电池图。那款电池是细长形状，从侧面插入，装不进 M400 机身后上方的电池位（参考图黄圈处）。所以这里按 M400 的 TB100 来做，也就是附件前 3 张图里的那款。

## 尺寸说明
- 无人机斜面下方的空间有限，所以电池本体的深度按机身能容纳的最大值来做：盖面以下 7.35 个无人机像素，约 110 mm。
- 实物 TB100 比这更厚，模型整体比实物略扁。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.m400_battery.idle | loop | 静止 |
| animation.m400_battery.latch_press | once，0.6 s | 两侧卡扣按下再弹回（参考代码中右键物品触发） |
| animation.m400_battery.showcase | loop，8 s | 绕 Y 轴旋转展示 |
