# avata360_charging_hub — 大疆 Avata 360 双向充电管家（GeckoLib 方块实体）

Minecraft 1.20.1 Fabric，GeckoLib 4.x **方块实体**（GeoBlockEntity），modid 占位 `avatadrone`。机器可读信息（文件 MD5、骨骼、动画、放入模组的路径）见 `manifest.json`。
生成器：`tools/gen_charger_avata360.py`（基于 `tools/bbgen.py` + `tools/hub_common.py`），预览/GIF：`tools/preview_charger.py`，打包：`tools/package_charger.py`。

## 目录
```
avata360_charging_hub.bbmodel                          Blockbench 工程（GeckoLib block 格式，贴图内嵌，含全部动画）
geo/avata360_charging_hub.geo.json                     几何，identifier = geometry.avata360_charging_hub
animations/avata360_charging_hub.animation.json        动画（见下表）
textures/avata360_charging_hub.png                     贴图（每面独立 UV，按世界坐标投影绘制）
textures/avata360_charging_hub_glowmask.png            发光遮罩：充电 LED（AutoGlowingGeoLayer）
blockstates/avata360_charging_hub.json                 facing=north/south/east/west
models/block/avata360_charging_hub.json                仅 particle 贴图（模型由 GeckoLib 渲染）
models/item/avata360_charging_hub.json                 builtin/entity + display（方块物品用 GeoItem 渲染）
preview/                             多角度静帧、铭牌原图 label_art.png
reference_code/                      Fabric 1.20.1 (Yarn) 参考实现，两种充电管家共用一套代码
```
GIF：`model/gif/avata360/charger/`。

## 放入模组
```
geo/avata360_charging_hub.geo.json                     -> assets/avatadrone/geo/block/avata360_charging_hub.geo.json
animations/avata360_charging_hub.animation.json        -> assets/avatadrone/animations/block/avata360_charging_hub.animation.json
textures/avata360_charging_hub.png                     -> assets/avatadrone/textures/block/avata360_charging_hub.png
textures/avata360_charging_hub_glowmask.png            -> assets/avatadrone/textures/block/avata360_charging_hub_glowmask.png
blockstates/avata360_charging_hub.json                 -> assets/avatadrone/blockstates/avata360_charging_hub.json
models/block/avata360_charging_hub.json                -> assets/avatadrone/models/block/avata360_charging_hub.json
models/item/avata360_charging_hub.json                 -> assets/avatadrone/models/item/avata360_charging_hub.json
reference_code/                      -> src/main/java/com/example/avatadrone/（包名见各文件）
```
`ModChargers.init()` 放进 `onInitialize()`，`ModChargersClient.init()` 放进 `onInitializeClient()`。两种充电管家共用一个 BlockEntityType（`avatadrone:charging_hub`）。

## 模型
- 比例：1 像素 = 8 mm，与对应无人机实体一致。参考：DJI 官方渲染图（你给的图 1-5）；外形尺寸为按图比例估算。
- 尺寸：13.3 × 7.0 像素占地，槽口高 15.95，电池帽顶约 18.9 像素（比 1 格略高，与真机比例一致）。原点在方块底面中心，**正面 = -Z（north）**，GeoBlockRenderer 按 FACING 旋转；放置时正面朝向玩家。
- 外观：深灰外壳；前后壁各 3 个通透长窗（可看到电池）、槽口上方的圆角舌片及其下方的梯形凹面；正面左上 dji 浮雕标，左下 USB-C、状态灯、按键；底部黑色铭牌。电池帽朝上，4 颗电量灯朝上（每槽独立 LED 骨骼）。
- 电池：直接合并 model/battery/avata360（4 mm/px，缩放 0.5，与无人机实体内的电池相同），3 份共用同一块贴图区域。电池几何保持原方向，靠槽位骨骼 `slot1..3` 的**静态旋转 [90, 90, 0]** 立起来（尾部朝上、标签面朝 +X、卡扣/按键朝前后壁）。slot1 在 -X（从正面看在右边），slot3 在 +X。
- 方块数 1115，贴图 2048×2048。

## 骨骼
- `hub`：外壳（不动）。
- `slot1`/`slot2`/`slot3`：每槽一块电池（缩放到 0 = 空槽）；子骨骼为电池各部件，前缀 `s1_` / `s2_` / `s3_`。
- 卡扣/按键：两侧卡扣 sN_latch_l / sN_latch_r 在插入时内压、到位后松开（"咔哒"）。
- 电量灯：电池帽顶面的 4 颗电量灯（sN_led1..4，随电池一起移动），用 scale 0/1 控制亮灭。

## 动画（每个槽 N = 1/2/3 各一套，互不干扰，可用 3 个控制器同时播放）
| 动画 | 类型 | 内容 |
|---|---|---|
| `slotN_insert` | 1.6 s，停在末帧 | 电池从上方 12.5 像素处落入，卡扣/按键按下，到位轻微回弹并松开，然后 4 颗灯依次亮起 |
| `slotN_remove` | 1.3 s，停在末帧 | 灯熄灭，按下卡扣/按键，电池抽出，末帧缩放为 0 |
| `slotN_installed` | 静态 | 电池在位，4 灯常亮（满电） |
| `slotN_empty` | 静态 | 空槽（电池缩放 0，灯灭） |
| `slotN_charging` | 2 s 循环 | 电池在位，充电流水灯：1、2 常亮，第 3 颗闪烁 |
| `demo_cycle` | 12 s 循环 | 展示用：3 块电池依次插入，再倒序取出 |
| `idle` | 循环 | 空动画 |

参考代码中的控制器：`slotN` 的状态动画 = empty / installed / charging；插入、取出用 `triggerAnim("slotN", "insert" | "remove")` 从服务端触发。
**注意**：json 里 insert/remove 是 `hold_on_last_frame`（方便 Blockbench 预览），代码里必须用 `Animation.LoopType.PLAY_ONCE` 播放，否则控制器停在 PAUSED，回不到状态动画（已在 GeckoLib 4.4.9 源码中核对）。

## 参考代码要点
- 手持对应电池右键：插入点击的槽（满了就插第一个空槽）；空手右键：取出点击的槽（空的就取最后一块）。
- 电量沿用模组现有的 NBT `BatteryCharge`（0..1，与 `item/DeviceCharging` 相同），插入时读取、取出时写回，并保留电池物品的其它 NBT。
- 像真机一样顺序充电：电量最高且未满的电池先充；正在充的槽播放 `slotN_charging`。
- 方块被破坏时掉落里面的电池。
- 物品形态（GeoItem）隐藏 3 块电池和指示灯；方块渲染器每帧恢复可见（两者共享 BakedGeoModel）。
- 代码未在本环境编译（虚拟机只有 JDK 11），API 已对照 loom-cache 中的 GeckoLib 4.4.9 / Yarn 1.20.1 源码。
