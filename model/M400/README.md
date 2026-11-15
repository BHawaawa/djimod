# M400 — 大疆经纬 M400（DJI Matrice 400）无人机实体（v1.1：带 TB100 电池仓，可插拔电池）（供 AI / 开发者调用）

Minecraft 1.20.1，GeckoLib 4.x 实体。机器可读信息见 `manifest.json`（文件 MD5、骨骼、动画、尺寸）。

## 目录
```
m400.bbmodel                       Blockbench 工程（GeckoLib 实体格式，贴图内嵌，含 9 段动画；电池骨骼在编辑器中默认隐藏）
geo/m400.geo.json                  几何，identifier = geometry.m400，UV 空间 4096x1024
animations/m400.animation.json     动画
textures/m400.png                  贴图 4096x1024（每面独立 UV；桨叶为透明 cutout）
textures/m400_glowmask.png         发光遮罩（电机状态灯、电池电量灯、机头/机腹补光灯），配合 AutoGlowingGeoLayer
preview/                           软件渲染预览（overview.png 为 8 视角总览，另有特写；不带后缀的是装好电池，*_no_battery / battery_bay_*_empty 是空仓）
reference_code/                    Fabric 1.20.1 (Yarn) 参考实现：实体 / GeoModel / 渲染器 / 注册片段
manifest.json                      机器可读描述
```
生成脚本：`DJI/tools/gen_m400.py <输出目录>`（基于共用的 `tools/bbgen.py`，电池外形来自 `tools/tb100.py`），预览：`DJI/tools/preview_m400.py`。
插拔动画 GIF：`DJI/model/gif/M400/`（`tools/make_m400_battery_gifs.py`）。电池物品：`DJI/model/battery/M400/`。v1 备份：`DJI/backup/M400_v1/`。

## 放入模组
```
geo/m400.geo.json              -> assets/<modid>/geo/m400.geo.json
animations/m400.animation.json -> assets/<modid>/animations/m400.animation.json
textures/m400.png              -> assets/<modid>/textures/entity/m400.png
textures/m400_glowmask.png     -> assets/<modid>/textures/entity/m400_glowmask.png
```

## 比例与尺寸（按官方参数）
- 1 模型像素 = 15 mm。机头朝 -Z（north），机身右侧 = +X，原点在地面中心，滑橇底部贴 Y=0。
- 高 31.7 像素 ≈ 475 mm（官方含脚架 480 mm）；对角轴距 71.3 像素 ≈ 1070 mm；25 英寸桨直径 42.4 像素。
- 电机位置按俯视参考图：前臂更横向（X ±29.2, Z -19.6），后臂更靠后（X ±24.8, Z +27.1）。
- 建议渲染缩放 0.4（约 1.65 格宽、0.8 格高），碰撞箱 1.6 × 0.8。

## 外形还原要点
- **机头**：高的正面带切角；顶部两个"耳朵"（上视镜头）夹着墨绿色激光雷达玻璃罩；正面上角两颗前视鱼眼、前向 ToF 窗、dji 标；两侧切角是竖向网格散热口，上方有补光灯；侧面有两颗侧视鱼眼和竖向散热缝。
- **机身侧面**：一条从前下到后上的大斜分缝（与参考侧视图一致），E-Port 盖板。
- **TB100 电池**：机身后上方，顶面向后下倾斜约 34°（侧视轮廓与参考图一致）；斜面上有防滑格栅、TB100 / MATRICE 400 字样、4 颗电量灯和电源键；两侧有橙色释放卡扣。
- **机尾**：MATRICE 字样 + 散热格栅。**机腹**：两组散热格栅、下视双目 + ToF、黄色补光灯、两组 E-Port V2 触点、机头下方 DGC2.0 云台快拆口（不带云台，与参考图一致）。
- **机臂**：机身凸台 → 带橙色锁定标记的滚花旋钮锁 → 编织碳纤维臂管 → 电机端套筒；靠近电机处有带散热槽的电调盒。
- **电机**：立柱侧面有 3 颗状态灯（前绿、后橙，发光）；前臂顶部是细天线，后臂顶部是高的 RTK 天线罩；电机钟罩带绕组开窗；**桨在电机下方**（与参考渲染一致）。
- **桨叶**：2510F 两叶折叠桨，有扭转角，桨根折叠铰链，桨尖橙色。
- **起落架**：两根碳纤维斜腿（中段夹箍）→ T 型接头 → 两根纵向碳纤维滑橇，两端橡胶帽。

## v1.1 电池仓（TB100）
- **位置**：机身后上方的 34° 斜面，也就是参考图黄圈处。斜面起点在 Blockbench 坐标 Y 28.6、Z 4.5。
  - 电池盖：10 × 14.96 像素，构成整个斜面的顶面。
  - 电池本体：8.2 × 10.75 像素，沿斜面法向插入机身，盖面以下深 7.35 像素。
- **仓体**：
  - 仓壁、仓底和两侧搭边是按斜面旋转的立方体；仓内是深色，底面有和电池触点对齐的插座和两条导槽。
  - 仓体周围的机身用 0.35 像素一阶的轴对齐方块阶梯填充。阶梯的锯齿都被仓壁和搭边挡住，从外面看不到。
  - 机身侧面和底面保持原来的外观，侧面有分缝线和 E-Port 盖板。
- **电池骨骼** `battery`（子骨骼 `bat_latch_l` / `bat_latch_r`）：放在 `drone` 下面，轴心在斜面起点。
  - 电池和 `model/battery/M400` 的物品用同一套代码生成，外观一致。
  - 默认没有电池：`battery_empty` 把它缩放到 0，Blockbench 工程里这个骨骼也默认隐藏。
- **方块数**：从 531 增加到 704，贴图仍是 4096×1024。

## 骨骼
`root > drone > { body > lidar, battery > {bat_latch_l, bat_latch_r}, gear, motor_fr/fl/rr/rl > prop_xx }`
- `root` 不被动画使用，留给代码按速度前倾/侧倾（参考 `M400Model#setCustomAnimations`）。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.m400.idle | loop | 落地静止，桨不转 |
| animation.m400.props | loop | 只转桨（4 圈/秒，对角同向），可叠加使用 |
| animation.m400.fly | loop，2 s | 悬停上下浮动 + 轻微摆动 + 桨旋转 |
| animation.m400.takeoff | hold_on_last_frame，1.5 s | 桨逐渐加速，0.8 s 后机身离地上升 3 像素 |
| animation.m400.land | hold_on_last_frame，1.5 s | 机身从 3 像素高落回地面，桨逐渐停下 |
| animation.m400.battery_empty | hold_on_last_frame | 常态：无电池（battery 缩放 0） |
| animation.m400.battery_installed | hold_on_last_frame | 常态：已装电池 |
| animation.m400.battery_insert | hold_on_last_frame，1.8 s | 电池从斜面后上方沿法向压入，两侧卡扣先压下，到位后弹出 |
| animation.m400.battery_remove | hold_on_last_frame，1.5 s | 先按下卡扣，电池沿法向拔出，1.45 s 时缩放到 0 |

`battery_insert` 和 `battery_remove` 的最后一帧分别和 `battery_installed`、`battery_empty` 完全一致。参考代码里，`battery` 控制器平时按状态播放这两个常态动画，插拔时用 `triggerAnim("battery", "insert" / "remove")`，结束后能无缝接回常态。

## 参考代码交互（reference_code/entity/M400Entity.java）
- 手持 TB100 电池（`M400BatteryItem`）右键无人机：插入电池，消耗 1 个电池（创造模式不消耗）。
- 潜行加空手右键：取出电池，电池还给玩家；背包满时掉在地上。
- 没装电池时无人机受重力落地，飞行 AI 不启动；死亡时如果装着电池会掉落电池。
- 插拔状态用 DataTracker 同步，并存进 NBT（`HasBattery`）。
- 需要注册 `M400_BATTERY`，见 `model/battery/M400/reference_code/Registration.snippet.java`。

## 已知限制
- Blockbench 只能用立方体，圆柱（臂管、电机、腿）用 12–16 边形拼成，雷达罩是 8 层台阶拼的半球。
- 预览图是软件渲染，光照比游戏和 Blockbench 偏暗。
- 电池本体的深度受机身斜面下方空间限制，比实物 TB100 略扁。
