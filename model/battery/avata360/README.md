# avata360_battery — 大疆 Avata 360 智能飞行电池物品（供 AI / 开发者调用）

Minecraft 1.20.1，GeckoLib 4.x **物品**（GeoItem）。之前的版本是实体，现已改为物品。机器可读信息见 `manifest.json`（文件 MD5、骨骼、动画、display 姿态）。

## 目录
```
avata360_battery.bbmodel                      Blockbench 工程（GeckoLib item 格式，贴图内嵌，含 display 姿态与 4 段动画）
geo/avata360_battery.geo.json                 几何，identifier = geometry.avata360_battery，UV 空间 2048x2048
animations/avata360_battery.animation.json    动画
textures/avata360_battery.png                 贴图 2048x2048（每面独立 UV，按世界坐标投影绘制）
textures/avata360_battery_glowmask.png        发光遮罩：4 颗绿色电量 LED（GeckoLib AutoGlowingGeoLayer）
models/item/avata360_battery.json             物品模型：parent = builtin/entity + 各视角 display 变换
preview/                                      多角度预览、gui.png（物品栏格子模拟）、标签原图 label_art.png
reference_code/                               Fabric 1.20.1 (Yarn) 参考实现：GeoItem / 渲染器（含开机发光层）/ 注册片段
```

## 放入模组
```
geo/avata360_battery.geo.json                -> assets/<modid>/geo/avata360_battery.geo.json
animations/avata360_battery.animation.json   -> assets/<modid>/animations/avata360_battery.animation.json
textures/avata360_battery.png                -> assets/<modid>/textures/item/avata360_battery.png
textures/avata360_battery_glowmask.png       -> assets/<modid>/textures/item/avata360_battery_glowmask.png
models/item/avata360_battery.json            -> assets/<modid>/models/item/avata360_battery.json
```

## 相对实体版的改动
- bbmodel 改为 `geckolib_model_type: item`，贴图目录改为 item，并加入了各视角的 display 姿态。
- 几何整体下移 3.55 像素，以物品渲染枢轴为中心。形状、方块数（314）和贴图内容与实体版相同。
- 新增 `models/item/avata360_battery.json`，内容为 `builtin/entity` 加 display。
- 参考代码由 Entity 改为 GeoItem：
  - 右键：按电源键，在物品 NBT `Powered` 中切换开关，开机时 LED 发光；
  - 潜行加右键：按压两侧卡扣；
  - 提示信息：显示 2700 mAh / 14.32 V / 38.67 Wh 和电源状态。
- 旧的实体参考代码已删除。

## 模型说明
- 平放姿态：黑色标签面朝上（+Y），电池帽（电源键加 4 颗 LED）朝 -Z，8 针触点端朝 +Z。1 像素 ≈ 4 mm，尺寸 13.7 × 8.4 × 28.1 像素。
- 顶面标签内容：dji 标志、型号 BWXVN1-2700-14.32、黄色警告条、中英日文说明、UKCA / CE / TIS 2217-2548 / 带 Pb 的垃圾桶 / 回收标志 / 二维码。
- 其他细节：
  - 底面：两条纵向凹槽和卡扣舌；
  - 两侧：黑色卡扣（拨杆加带肋握把）；
  - 端面：金色触针；
  - 电池帽：椭圆电源键。

## 骨骼
root > battery > {body, cap > button, latch_l, latch_r}。latch_l 对应 Blockbench 中的 -X 侧；manifest 中的 pivot 是 geo.json 坐标，X 与 Blockbench 显示的符号相反。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.avata360_battery.idle | loop | 静止 |
| animation.avata360_battery.latch_press | 单次 0.6 s | 两侧卡扣同时向内按下后回弹 |
| animation.avata360_battery.button_press | 单次 0.3 s | 电源键按下 |
| animation.avata360_battery.showcase | loop 8 s | 缓慢旋转并上下浮动 |

## 显示姿态（models/item/avata360_battery.json）
- 物品栏（gui）：`[30, 225, 0]`，缩放 0.5，参见 preview/gui.png。gui.png 是用渲染器按 vanilla 变换模拟的，游戏里可能有细微差异。
- 第一人称：`[10, 200, 0]`，缩放 0.42。第三人称：`[10, 180, 0]`，缩放 0.36。
- 掉落物：缩放 0.3。展示框：`[-90, 180, 0]`，标签面朝外。
- 这些数值都可以在 Blockbench 的 Display 页签里调。

## 修改
用 Blockbench 打开 bbmodel 修改后，导出 geo 和动画，display 写回物品模型 JSON，并更新 manifest 里的 MD5。
也可以改生成脚本 `DJI/tools/gen_battery_avata360.py`（共用构建库 `DJI/tools/bbgen.py`）后重新生成：
- 生成：`python3 gen_battery_avata360.py <输出目录>`
- 预览：`python3 preview_item.py <输出目录> <预览目录> avata360_battery bb|geo`

## 已知限制
- Blockbench 只能用立方体，圆角是分层拼出来的，近看能看到细小台阶。
- 实物没有公开的精确毫米尺寸，比例是按参考图估的。标签小字是仿真排版，不是逐字复刻实物。

## 2026-11-07 更新：与 Avata 360 机身无缝贴合
- 电池帽截面改成和 Avata 360 机身脊梁完全一致（13.6 × 8.4、圆角 2.0；机身是 6.8 × 4.2、圆角 1.0，比例 2:1），插入后电池帽直接接上机身，没有台阶。
- 整块电池改用和无人机外壳相同的灰色 (146,148,150) 与噪点；电源键、卡槽、卡扣面板都只保留细分模线，不再有黑色/浅色色块。
- 两侧卡扣握把改成与电池帽齐平的同色面板（只凸出 0.03 px，用来避免 Z-fighting），黑色拨杆仍在电池主体侧面（插入后藏在电池仓里）。
- 中心线高度 YC 改为 4.2（导出偏移 -4.2，模型仍以物品轴心为中心）。
- bbgen 的圆角棱柱补上了核心面到第一个倒角面之间的小缺口，圆角轮廓是闭合的多边形。
