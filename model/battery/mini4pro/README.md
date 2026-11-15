# mini4pro_battery — 大疆 Mini 4 Pro 智能飞行电池物品（v2，按实拍照片重做）（供 AI / 开发者调用）

Minecraft 1.20.1，GeckoLib 4.x **物品**（GeoItem）。机器可读信息见 `manifest.json`。v1（按电商图建模的版本）备份在 `DJI/backup/mini4pro_battery_v1/`。
无人机 `model/mini4pro`（v2）会把本模型的 geo 和贴图按 0.5 倍合并到电池仓里，所以**改完本模型后要重新生成无人机**。

## 目录
```
mini4pro_battery.bbmodel                      Blockbench 工程（GeckoLib item 格式，贴图内嵌，含 display 姿态与 3 段动画）
geo/mini4pro_battery.geo.json                 几何，identifier = geometry.mini4pro_battery，UV 空间 2048x2048
animations/mini4pro_battery.animation.json    动画
textures/mini4pro_battery.png                 贴图 2048x2048
models/item/mini4pro_battery.json             物品模型：parent = builtin/entity + display
preview/                                      多角度预览、gui.png（物品栏格子模拟）、标签原图 label_art.png
reference_code/                               GeoItem / 渲染器 / 注册片段
```

## 放入模组
```
geo/mini4pro_battery.geo.json                -> assets/<modid>/geo/mini4pro_battery.geo.json
animations/mini4pro_battery.animation.json   -> assets/<modid>/animations/mini4pro_battery.animation.json
textures/mini4pro_battery.png                -> assets/<modid>/textures/item/mini4pro_battery.png
models/item/mini4pro_battery.json            -> assets/<modid>/models/item/mini4pro_battery.json
```

## 模型说明（v2）
- **尺寸**：1 像素 = 3 mm。从照片量得约 80 × 38 × 26.5 mm；尾部加宽段宽 44.5 mm，比主体深 1.5 mm。模型尺寸为 15.4 × 9.4 × 26.7 像素。共 154 个方块。
- **朝向**：黑色标签面朝上（+Y），尾部加宽段（带两侧按压钮）朝 -Z，触点端朝 +Z。插进无人机后，标签面朝上藏在机身里，尾部加宽段就是机尾外露的部分。
- **细节**（按照片还原）：
  - 顶面黑色标签，按实物复刻：
    - 抬头：dji 标志、LITHIUM ION POLYMER RECHARGEABLE BATTERY、型号 BWX162-3850-7.38；
    - 参数：8.5V / 7.38Vdc / 3850mAh / 28.41Wh；
    - 警告条款、深圳授权地址、制造商"厦门新能安"；
    - 标志：打叉垃圾桶加 Pb、R32418、Li-ion 20、CE、UKCA、PSE、回收标志、TP；
    - 白底 DataMatrix 和 "5LRPN9A / CA4014Q"；
    - 文字沿长度方向排版，dji 标志在触点一侧。
  - 前端（触点端）：标签面下方是深色触点槽和 6 根金色触针，标签面前沿也能看到触针露头；底部前缘有小倒角。
  - 侧面：凸起的成型面板（下沿靠近尾部处斜着抬高）和上沿边条；尾部加宽段前方有两道横槽，旁边是竖向弹片。
  - 尾部加宽段：两侧各一个带网点纹理的方形按压钮，四周有 L 形黑缝；顶部两侧有卡钩槽；尾面有细竖纹。
  - 底部：前 44 mm 抬高 2.5 mm（插入后正好架在机身底板上面），后段底面印着 ULTRA LIGHT | 249g。
- **配色**：浅灰 (204,206,207)，加宽段稍深，按压钮稍浅。

## 骨骼
root > battery > {body, collar, button_l, button_r}。button_l 对应 Blockbench 中的 -X 侧；合并到无人机后左右会对调，名字分别是 bat_button_r / bat_button_l。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.mini4pro_battery.idle | loop | 静止 |
| animation.mini4pro_battery.buckle_press | 单次 0.6 s | 两侧按压钮压下后回弹（参考代码中右键触发） |
| animation.mini4pro_battery.showcase | loop 8 s | 缓慢旋转 |

## 与无人机的配合
- 手持本物品右键 Mini 4 Pro 无人机即可插入电池；潜行加空手右键取出。逻辑在 `model/mini4pro/reference_code/entity/Mini4ProEntity.java`。
- 插入动画 GIF：`DJI/model/gif/avata360/batteryinmini4pro/`。

## 修改
- 生成：`python3 DJI/tools/gen_battery_mini4pro.py <out>`
- 预览：`python3 DJI/tools/preview_item.py <out> <预览目录> mini4pro_battery bb|geo`
- 改完后重新生成无人机：`python3 DJI/tools/gen_mini4pro.py <out> DJI/model/battery/mini4pro`

## 已知限制
- Blockbench 只能用立方体，圆角是分层拼出来的。
- 标签小字的排版是尽量照实物还原的，不是逐像素扫描。
- 无人机里的电池贴图降采样到约 20 texel/像素，近看会比物品版模糊一些。
